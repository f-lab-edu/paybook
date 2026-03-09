package com.paybook.order.service;

import com.paybook.core.entity.*;
import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.CreateOrderRequest.OrderItemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 유형(PLATFORM/SELLER)별로 올바르게 동작하는지 테스트한다.
 *
 * 검증 목적:
 * - 정산 서비스에서 쿠폰 유형에 따라 비용 부담 주체가 달라지므로,
 *   주문 시 사용된 쿠폰의 couponType이 DB에 정확히 보존되는지 확인한다.
 * - CouponEntity.calculateDiscount()가 할인 금액이 주문 금액을 초과하면
 *   주문 금액으로 캡(cap)하는지 확인한다.
 */
@DisplayName("쿠폰 유형별 동작")
class OrderCouponTypeTest extends OrderServiceTestBase {

    @Test
    @DisplayName("PLATFORM 쿠폰 사용 → DB에 couponType=PLATFORM으로 보존되고 상태가 USED로 변경된다")
    void platformCoupon_isSaved() {
        couponRepository.save(new CouponEntity("COUPON-PLATFORM", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-PLATFORM", null
        );

        orderService.createOrder(request);

        CouponEntity coupon = couponRepository.findByCouponId("COUPON-PLATFORM").orElseThrow();
        assertThat(coupon.getCouponType()).isEqualTo(CouponType.PLATFORM);
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
    }

    @Test
    @DisplayName("SELLER 쿠폰 사용 → DB에 couponType=SELLER로 보존되고 상태가 USED로 변경된다")
    void sellerCoupon_isSaved() {
        couponRepository.save(new CouponEntity("COUPON-SELLER", CouponStatus.ACTIVE,
                CouponType.SELLER, DiscountType.FIXED_AMOUNT, 1000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-SELLER", null
        );

        orderService.createOrder(request);

        CouponEntity coupon = couponRepository.findByCouponId("COUPON-SELLER").orElseThrow();
        assertThat(coupon.getCouponType()).isEqualTo(CouponType.SELLER);
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
    }

    @Test
    @DisplayName("정액 쿠폰 5,000원 + 주문 금액 3,000원 → calculateDiscount가 주문 금액(3,000)으로 캡한다")
    void discountCappedAtOrderAmount() {
        CouponEntity coupon = new CouponEntity("COUPON-CAP", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 5000, null);
        assertThat(coupon.calculateDiscount(3000)).isEqualTo(3000);
    }
}
