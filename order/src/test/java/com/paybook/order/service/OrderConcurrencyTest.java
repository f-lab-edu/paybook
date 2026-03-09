package com.paybook.order.service;

import com.paybook.core.entity.ProductEntity;
import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.CreateOrderRequest.OrderItemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import com.paybook.core.repository.ProductRepository;
import com.paybook.core.repository.CouponRepository;
import com.paybook.core.repository.UserPointRepository;
import com.paybook.core.entity.UserPointEntity;
import com.paybook.order.config.DiscountPolicyConfig;
import com.paybook.order.repository.OrderRepository;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시 주문 시 재고 초과 차감이 발생하지 않는지 테스트한다.
 *
 * 비즈니스 규칙:
 * - 재고가 5개인 상품에 10명이 동시에 1개씩 주문하면,
 *   정확히 5명만 성공하고 나머지 5명은 실패해야 한다.
 * - @Version 낙관적 락을 통해 동시 재고 차감의 정합성을 보장한다.
 *
 * 검증 목적:
 * - 동시 요청 시 재고가 음수로 빠지지 않는지
 * - 성공한 주문 수 + 남은 재고 = 초기 재고 관계가 성립하는지
 */
@SpringBootTest
@EntityScan("com.paybook")
@EnableJpaRepositories("com.paybook")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:concurrencytest",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.main.allow-bean-definition-overriding=true",
        "order.discount.max-discount-percent=30",
        "order.discount.min-pg-payment-percent=50",
        "order.delivery.fee=0",
        "order.delivery.free-threshold=0",
        "order.payment.timeout-minutes=30"
})
@DisplayName("동시성 제어 — 재고 낙관적 락")
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserPointRepository userPointRepository;

    @Test
    @DisplayName("재고 5개 상품에 10명 동시 주문 → 성공 수 + 남은 재고 = 5, 재고 음수 불가")
    void concurrentOrders_noOverselling() throws InterruptedException {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        couponRepository.deleteAll();
        userPointRepository.deleteAll();

        int initialStock = 5;
        productRepository.save(new ProductEntity("PROD-CONCURRENT", "동시성상품", 10000, initialStock));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String userId = "USER-CONC-" + i;
            userPointRepository.save(new UserPointEntity(userId, 0));

            executor.submit(() -> {
                try {
                    CreateOrderRequest request = new CreateOrderRequest(
                            userId,
                            List.of(new OrderItemRequest("PROD-CONCURRENT", 1)),
                            null, null, null
                    );
                    orderService.createOrder(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        ProductEntity product = productRepository.findByProductId("PROD-CONCURRENT").orElseThrow();

        assertThat(product.getStockQuantity()).isGreaterThanOrEqualTo(0);
        assertThat(successCount.get() + product.getStockQuantity()).isEqualTo(initialStock);
        assertThat(successCount.get()).isLessThanOrEqualTo(initialStock);
    }
}
