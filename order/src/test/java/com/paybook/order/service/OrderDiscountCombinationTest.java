package com.paybook.order.service;

import com.paybook.core.entity.*;
import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.CreateOrderRequest.OrderItemRequest;
import com.paybook.order.dto.OrderResponse;
import com.paybook.order.exception.OrderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 쿠폰 할인 유형(정액/정률)과 포인트의 다양한 조합에서
 * 할인 한도(30%) 정책이 올바르게 적용되는지 테스트한다.
 *
 * 검증 목적:
 * - 정률 쿠폰 + 포인트 조합이 한도 이내이면 양쪽 할인이 모두 적용되는지
 * - 정률 쿠폰만 사용 시 한도 이내이면 정상 처리되는지
 * - 포인트만 사용 시 한도 이내/초과 경계에서 올바르게 동작하는지
 * - 정액 쿠폰 + 포인트 조합이 한도를 초과하면 DISCOUNT_LIMIT_EXCEEDED가 발생하는지
 * - 정률 쿠폰의 maxDiscountAmount 캡 적용 후 한도를 계산하는지
 *   (예: 50% 쿠폰이지만 max=2,000으로 캡되어 실제 20% → 한도 이내)
 */
@DisplayName("할인 한도 + 쿠폰 조합")
class OrderDiscountCombinationTest extends OrderServiceTestBase {

    @Test
    @DisplayName("정률 10% 쿠폰 + 포인트 2,000 → 총액 100,000 기준 할인 12,000(12%) → 한도 이내 성공")
    void percentageCouponWithPoints_withinLimit() {
        productRepository.save(new ProductEntity("PROD-HI", "고가상품", 100000, 100));
        couponRepository.save(new CouponEntity("COUPON-PCT10", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.PERCENTAGE, 10, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-HI", 1)),
                null, "COUPON-PCT10", 2000
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.couponDiscountAmount()).isEqualTo(10000);
        assertThat(response.pointDiscountAmount()).isEqualTo(2000);
        assertThat(response.pgPaymentAmount()).isEqualTo(88000);
    }

    @Test
    @DisplayName("정률 20% 쿠폰만 → 총액 10,000 기준 할인 2,000(20%) → 한도 30% 이내 성공")
    void percentageCouponOnly_withinLimit() {
        couponRepository.save(new CouponEntity("COUPON-PCT20", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.PERCENTAGE, 20, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-PCT20", null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.couponDiscountAmount()).isEqualTo(2000);
        assertThat(response.pgPaymentAmount()).isEqualTo(8000);
    }

    @Test
    @DisplayName("포인트 3,000만 → 총액 10,000 기준 30% → 한도와 같으므로 성공")
    void pointsOnly_withinLimit() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, 3000
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.pointDiscountAmount()).isEqualTo(3000);
        assertThat(response.pgPaymentAmount()).isEqualTo(7000);
    }

    @Test
    @DisplayName("포인트 5,000만 → 총액 10,000 기준 50% → 한도 초과로 DISCOUNT_LIMIT_EXCEEDED 발생")
    void pointsOnly_exceedsLimit() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, 5000
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("DISCOUNT_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("정액 쿠폰 2,000 + 포인트 2,000 = 4,000(40%) → 한도 초과로 DISCOUNT_LIMIT_EXCEEDED 발생")
    void fixedCouponWithPoints_exceedsLimit() {
        couponRepository.save(new CouponEntity("COUPON-2K", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 2000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-2K", 2000
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("DISCOUNT_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("정률 50% 쿠폰(max=2,000) + 포인트 1,000 → 캡 적용 후 할인 3,000(30%) → 한도 이내 성공")
    void percentageCouponWithMaxDiscount_limitCheck() {
        couponRepository.save(new CouponEntity("COUPON-PCT50-MAX2K", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.PERCENTAGE, 50, 2000));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-PCT50-MAX2K", 1000
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.couponDiscountAmount()).isEqualTo(2000);
        assertThat(response.pointDiscountAmount()).isEqualTo(1000);
        assertThat(response.pgPaymentAmount()).isEqualTo(7000);
    }
}
