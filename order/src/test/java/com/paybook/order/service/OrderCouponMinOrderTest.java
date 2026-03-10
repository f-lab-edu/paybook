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
 * 쿠폰의 최소 주문금액(minOrderAmount) 조건이 올바르게 동작하는지 테스트한다.
 *
 * 비즈니스 규칙:
 * - 쿠폰에 minOrderAmount가 설정되어 있으면,
 *   주문 총액(totalAmount)이 해당 금액 이상일 때만 쿠폰을 사용할 수 있다.
 * - minOrderAmount가 null이면 최소 주문금액 제한 없이 사용 가능하다.
 *
 * 검증 목적:
 * - 총액이 최소 주문금액 이상이면 쿠폰이 정상 적용되는지
 * - 총액이 최소 주문금액 미만이면 COUPON_MIN_ORDER_NOT_MET 예외가 발생하는지
 * - 총액이 최소 주문금액과 정확히 같으면(경계값) 성공하는지
 * - minOrderAmount가 null인 쿠폰은 금액 제한 없이 사용 가능한지
 */
@DisplayName("쿠폰 최소 주문금액 검증")
class OrderCouponMinOrderTest extends OrderServiceTestBase {

    @Test
    @DisplayName("총액 20,000원 ≥ 최소주문금액 15,000원 → 쿠폰 적용 성공")
    void totalAboveMinOrder_succeeds() {
        couponRepository.save(new CouponEntity("COUPON-MIN15K", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 2000, null, 15000));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 2)),
                null, "COUPON-MIN15K", null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.couponDiscountAmount()).isEqualTo(2000);
        assertThat(response.pgPaymentAmount()).isEqualTo(18000);
    }

    @Test
    @DisplayName("총액 10,000원 < 최소주문금액 30,000원 → COUPON_MIN_ORDER_NOT_MET 예외 발생")
    void totalBelowMinOrder_throws() {
        couponRepository.save(new CouponEntity("COUPON-MIN30K", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 2000, null, 30000));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-MIN30K", null
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("COUPON_MIN_ORDER_NOT_MET"));
    }

    @Test
    @DisplayName("총액 10,000원 = 최소주문금액 10,000원 → 경계값 성공")
    void totalExactlyAtMinOrder_succeeds() {
        couponRepository.save(new CouponEntity("COUPON-MIN10K", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1000, null, 10000));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-MIN10K", null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.couponDiscountAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("minOrderAmount가 null인 쿠폰 → 금액 제한 없이 사용 가능")
    void nullMinOrder_alwaysApplicable() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-ACTIVE", null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.couponDiscountAmount()).isEqualTo(1000);
    }
}
