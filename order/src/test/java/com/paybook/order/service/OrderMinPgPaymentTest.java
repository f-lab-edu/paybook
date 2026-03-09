package com.paybook.order.service;

import com.paybook.core.entity.*;
import com.paybook.order.config.DeliveryFeeConfig;
import com.paybook.order.config.DiscountPolicyConfig;
import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.CreateOrderRequest.OrderItemRequest;
import com.paybook.order.dto.OrderResponse;
import com.paybook.order.exception.OrderException;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PG 결제 최소 비율(minPgPaymentPercent) 정책이 올바르게 적용되는지 테스트한다.
 *
 * 비즈니스 규칙:
 * - 쿠폰/포인트만으로 전액 결제하는 것을 방지하기 위해,
 *   pgPaymentAmount가 totalAmount의 일정 비율(설정값) 이상이어야 한다.
 * - 이 테스트에서는 minPgPaymentPercent=50%로 설정한다.
 *
 * 검증 목적:
 * - 할인 합계가 50% 미만이면(PG > 50%) 정상 처리되는지
 * - 할인 합계가 정확히 50%이면(PG = 50%, 경계값) 정상 처리되는지
 * - 할인 합계가 50% 초과이면(PG < 50%) PG_PAYMENT_BELOW_MINIMUM 예외가 발생하는지
 * - 쿠폰만, 포인트만, 쿠폰+포인트 조합 각각에서 동작하는지
 *
 * 참고: maxDiscountPercent=80%로 설정하여 할인 한도 정책이 먼저 차단하지 않도록 한다.
 * 이를 통해 minPgPaymentPercent 경계를 독립적으로 검증할 수 있다.
 */
@DataJpaTest
@Import(OrderService.class)
@EnableConfigurationProperties({DiscountPolicyConfig.class, DeliveryFeeConfig.class})
@EntityScan("com.paybook")
@EnableJpaRepositories("com.paybook")
@TestPropertySource(properties = {
        "order.discount.max-discount-percent=80",
        "order.discount.min-pg-payment-percent=50",
        "order.delivery.fee=0",
        "order.delivery.free-threshold=0"
})
@DisplayName("PG 결제 최소 비율 검증")
class OrderMinPgPaymentTest {

    @Autowired
    private OrderService orderService;

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
        userPointRepository.save(new UserPointEntity("USER-001", 50000));
    }

    @Test
    @DisplayName("할인 40%(PG 60%) → 최소 PG 50% 이상이므로 주문이 성공한다")
    void pgAboveMinimum_succeeds() {
        couponRepository.save(new CouponEntity("COUPON-4K", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 4000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-4K", null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.pgPaymentAmount()).isEqualTo(6000);
    }

    @Test
    @DisplayName("할인 정확히 50%(PG 50%) → 경계값이므로 주문이 성공한다")
    void pgExactlyAtMinimum_succeeds() {
        couponRepository.save(new CouponEntity("COUPON-3K", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 3000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-3K", 2000
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.pgPaymentAmount()).isEqualTo(5000);
        assertThat(response.totalAmount()).isEqualTo(10000);
    }

    @Test
    @DisplayName("할인 60%(PG 40%) → 최소 PG 50% 미만이므로 PG_PAYMENT_BELOW_MINIMUM 예외가 발생한다")
    void pgBelowMinimum_throws() {
        couponRepository.save(new CouponEntity("COUPON-6K", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 6000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-6K", null
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("PG_PAYMENT_BELOW_MINIMUM"));
    }

    @Test
    @DisplayName("포인트만으로 60% 할인(PG 40%) → PG_PAYMENT_BELOW_MINIMUM 예외가 발생한다")
    void pointsAlone_belowMinimum_throws() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, 6000
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("PG_PAYMENT_BELOW_MINIMUM"));
    }

    @Test
    @DisplayName("쿠폰 30% + 포인트 30% = 60% 할인(PG 40%) → PG_PAYMENT_BELOW_MINIMUM 예외가 발생한다")
    void couponAndPoints_combined_belowMinimum_throws() {
        couponRepository.save(new CouponEntity("COUPON-3KB", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 3000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-3KB", 3000
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("PG_PAYMENT_BELOW_MINIMUM"));
    }

    @Test
    @DisplayName("할인 없이 전액 PG 결제 → 최소 PG 조건을 당연히 충족한다")
    void noDiscount_alwaysPassesMinimum() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.pgPaymentAmount()).isEqualTo(10000);
    }
}
