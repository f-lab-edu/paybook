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
 * 주문 생성 시 쿠폰 검증을 테스트한다.
 *
 * 검증 목적:
 * - ACTIVE 상태의 정액/정률 쿠폰이 올바르게 할인을 계산하는지
 * - 정률 쿠폰에 maxDiscountAmount가 설정되면 캡이 적용되는지
 * - USED/EXPIRED 쿠폰으로 주문하면 각각 적절한 예외가 발생하는지
 * - 존재하지 않는 쿠폰 ID로 주문하면 COUPON_NOT_FOUND 예외가 발생하는지
 * - 쿠폰을 사용하면 DB에서 상태가 USED로 변경되는지
 * - couponId가 null이면 쿠폰 검증을 건너뛰고 할인 0원으로 처리하는지
 */
@DisplayName("쿠폰 검증")
class OrderCouponValidationTest extends OrderServiceTestBase {

    @Test
    @DisplayName("ACTIVE 정액 쿠폰 → 할인 금액이 정확히 적용되고 DB에서 쿠폰 상태가 USED로 변경된다")
    void activeFixedCoupon_succeeds_statusBecomesUsed() {
        couponRepository.save(new CouponEntity("COUPON-FIXED-3000", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 3000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 2)),
                null, "COUPON-FIXED-3000", null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.couponDiscountAmount()).isEqualTo(3000);
        assertThat(response.pgPaymentAmount()).isEqualTo(17000);

        CouponEntity coupon = couponRepository.findByCouponId("COUPON-FIXED-3000").orElseThrow();
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
    }

    @Test
    @DisplayName("ACTIVE 정률 10% 쿠폰 → 총액 30,000원의 10%인 3,000원이 할인된다")
    void activePercentageCoupon_succeeds() {
        couponRepository.save(new CouponEntity("COUPON-PCT-10", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.PERCENTAGE, 10, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1), new OrderItemRequest("PROD-002", 1)),
                null, "COUPON-PCT-10", null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.totalAmount()).isEqualTo(30000);
        assertThat(response.couponDiscountAmount()).isEqualTo(3000);
        assertThat(response.pgPaymentAmount()).isEqualTo(27000);
    }

    @Test
    @DisplayName("정률 10% 쿠폰에 maxDiscountAmount=1,000 설정 → 캡이 적용되어 1,000원만 할인된다")
    void percentageCouponWithMaxDiscount() {
        couponRepository.save(new CouponEntity("COUPON-PCT-MAX", CouponStatus.ACTIVE,
                CouponType.SELLER, DiscountType.PERCENTAGE, 10, 1000));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1), new OrderItemRequest("PROD-002", 1)),
                null, "COUPON-PCT-MAX", null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.couponDiscountAmount()).isEqualTo(1000);
        assertThat(response.pgPaymentAmount()).isEqualTo(29000);
    }

    @Test
    @DisplayName("이미 사용된(USED) 쿠폰으로 주문하면 COUPON_ALREADY_USED 409 예외가 발생한다")
    void usedCoupon_throws() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-USED", null
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> {
                    OrderException oe = (OrderException) ex;
                    assertThat(oe.getCode()).isEqualTo("COUPON_ALREADY_USED");
                    assertThat(oe.getHttpStatus().value()).isEqualTo(409);
                });
    }

    @Test
    @DisplayName("만료된(EXPIRED) 쿠폰으로 주문하면 COUPON_EXPIRED 예외가 발생한다")
    void expiredCoupon_throws() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-EXPIRED", null
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("COUPON_EXPIRED"));
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 ID로 주문하면 COUPON_NOT_FOUND 예외가 발생한다")
    void nonExistentCoupon_throws() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-NONEXISTENT", null
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("COUPON_NOT_FOUND"));
    }

    @Test
    @DisplayName("couponId가 null이면 쿠폰 검증을 건너뛰고 할인 0원으로 전액 PG 결제된다")
    void nullCoupon_skips() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.couponDiscountAmount()).isEqualTo(0);
        assertThat(response.pgPaymentAmount()).isEqualTo(10000);
    }
}
