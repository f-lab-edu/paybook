package com.paybook.order.service;

import com.paybook.core.entity.*;
import com.paybook.core.repository.CouponRepository;
import com.paybook.core.repository.ProductRepository;
import com.paybook.core.repository.UserPointRepository;
import com.paybook.order.config.DeliveryFeeConfig;
import com.paybook.order.config.DiscountPolicyConfig;
import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.CreateOrderRequest.OrderItemRequest;
import com.paybook.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

/**
 * OrderService 테스트의 공통 베이스 클래스.
 *
 * 모든 OrderService 테스트가 동일한 JPA 슬라이스 환경(H2)에서 실행되도록
 * 어노테이션과 공통 픽스처를 제공한다.
 *
 * 픽스처 기본 데이터:
 * - 상품: PROD-001(10,000원, 재고100), PROD-002(20,000원, 재고100)
 * - 쿠폰: COUPON-ACTIVE/USED/EXPIRED (정액 1,000원)
 * - 포인트: USER-001에 5,000 포인트
 * - 할인 한도: 총액의 30%
 */
@DataJpaTest
@Import(OrderService.class)
@EnableConfigurationProperties({DiscountPolicyConfig.class, DeliveryFeeConfig.class})
@EntityScan("com.paybook")
@EnableJpaRepositories("com.paybook")
@TestPropertySource(properties = {
        "order.discount.max-discount-percent=30",
        "order.discount.min-pg-payment-percent=50",
        "order.delivery.fee=3000",
        "order.delivery.free-threshold=0"
})
abstract class OrderServiceTestBase {

    @Autowired
    protected OrderService orderService;

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected CouponRepository couponRepository;

    @Autowired
    protected UserPointRepository userPointRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        couponRepository.deleteAll();
        userPointRepository.deleteAll();

        productRepository.save(new ProductEntity("PROD-001", "상품1", 10000, 100));
        productRepository.save(new ProductEntity("PROD-002", "상품2", 20000, 100));

        couponRepository.save(new CouponEntity("COUPON-ACTIVE", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1000, null));
        couponRepository.save(new CouponEntity("COUPON-USED", CouponStatus.USED,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1000, null));
        couponRepository.save(new CouponEntity("COUPON-EXPIRED", CouponStatus.EXPIRED,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1000, null));

        userPointRepository.save(new UserPointEntity("USER-001", 5000));
    }

    protected CreateOrderRequest basicRequest() {
        return new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 2)),
                "서울시 강남구",
                null,
                null
        );
    }
}
