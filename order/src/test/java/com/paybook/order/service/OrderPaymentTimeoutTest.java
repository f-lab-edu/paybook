package com.paybook.order.service;

import com.paybook.core.entity.ProductEntity;
import com.paybook.order.config.DeliveryFeeConfig;
import com.paybook.order.config.DiscountPolicyConfig;
import com.paybook.order.config.PaymentTimeoutConfig;
import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.CreateOrderRequest.OrderItemRequest;
import com.paybook.order.dto.OrderResponse;
import com.paybook.order.entity.OrderEntity;
import com.paybook.order.entity.OrderStatus;
import com.paybook.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import com.paybook.core.repository.CouponRepository;
import com.paybook.core.repository.ProductRepository;
import com.paybook.core.repository.UserPointRepository;
import com.paybook.core.entity.UserPointEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 타임아웃(PENDING_PAYMENT 상태에서 일정 시간 초과 시 자동 취소)을
 * 테스트한다.
 *
 * 비즈니스 규칙:
 * - PENDING_PAYMENT 상태에서 설정된 시간(timeoutMinutes) 이상 경과하면
 *   자동으로 주문을 취소하고 리소스(재고/쿠폰/포인트)를 복원한다.
 * - CONFIRMED 등 다른 상태의 주문은 타임아웃 대상이 아니다.
 *
 * 검증 목적:
 * - 타임아웃 기준 시간 이전에 생성된 PENDING_PAYMENT 주문이 취소되는지
 * - 기준 시간 이후에 생성된 주문은 취소되지 않는지
 * - CONFIRMED 상태 주문은 타임아웃 대상에서 제외되는지
 * - 취소된 주문의 재고가 복원되는지
 */
@DataJpaTest
@Import({OrderService.class, PaymentTimeoutScheduler.class})
@EnableConfigurationProperties({DiscountPolicyConfig.class, DeliveryFeeConfig.class, PaymentTimeoutConfig.class})
@EntityScan("com.paybook")
@EnableJpaRepositories("com.paybook")
@TestPropertySource(properties = {
        "order.discount.max-discount-percent=30",
        "order.discount.min-pg-payment-percent=50",
        "order.delivery.fee=3000",
        "order.delivery.free-threshold=30000",
        "order.payment.timeout-minutes=30"
})
@DisplayName("결제 타임아웃")
class OrderPaymentTimeoutTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentTimeoutScheduler paymentTimeoutScheduler;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserPointRepository userPointRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        couponRepository.deleteAll();
        userPointRepository.deleteAll();

        productRepository.save(new ProductEntity("PROD-001", "상품1", 10000, 100));
        userPointRepository.save(new UserPointEntity("USER-001", 5000));
    }

    @Test
    @DisplayName("30분 초과된 PENDING_PAYMENT 주문 → 자동 취소 + 재고 복원")
    void timedOutOrder_isCancelled() {
        OrderResponse created = orderService.createOrder(new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 2)),
                null, null, null
        ));

        // createdAt을 40분 전으로 강제 변경
        OrderEntity order = orderRepository.findByOrderId(created.orderId()).orElseThrow();
        setCreatedAt(order, LocalDateTime.now().minusMinutes(40));

        int cancelled = paymentTimeoutScheduler.cancelTimedOutOrdersManually(
                LocalDateTime.now().minusMinutes(30));

        assertThat(cancelled).isEqualTo(1);

        OrderEntity cancelledOrder = orderRepository.findByOrderId(created.orderId()).orElseThrow();
        assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        assertThat(productRepository.findByProductId("PROD-001").orElseThrow().getStockQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("30분 이내 PENDING_PAYMENT 주문 → 취소되지 않음")
    void recentOrder_notCancelled() {
        OrderResponse created = orderService.createOrder(new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, null
        ));

        int cancelled = paymentTimeoutScheduler.cancelTimedOutOrdersManually(
                LocalDateTime.now().minusMinutes(30));

        assertThat(cancelled).isEqualTo(0);

        OrderEntity order = orderRepository.findByOrderId(created.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("CONFIRMED 주문은 타임아웃 대상에서 제외된다")
    void confirmedOrder_notAffected() {
        OrderResponse created = orderService.createOrder(new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, null
        ));
        orderService.confirmOrder(created.orderId());

        OrderEntity order = orderRepository.findByOrderId(created.orderId()).orElseThrow();
        setCreatedAt(order, LocalDateTime.now().minusMinutes(40));

        int cancelled = paymentTimeoutScheduler.cancelTimedOutOrdersManually(
                LocalDateTime.now().minusMinutes(30));

        assertThat(cancelled).isEqualTo(0);
    }

    private void setCreatedAt(OrderEntity order, LocalDateTime createdAt) {
        // 테스트 목적으로 리플렉션을 사용하여 createdAt 변경
        try {
            var field = OrderEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(order, createdAt);
            orderRepository.save(order);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
