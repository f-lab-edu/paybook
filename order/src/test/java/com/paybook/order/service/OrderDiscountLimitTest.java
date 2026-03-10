package com.paybook.order.service;

import com.paybook.core.entity.CouponEntity;
import com.paybook.core.entity.CouponStatus;
import com.paybook.core.entity.CouponType;
import com.paybook.core.entity.DiscountType;
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
 * 할인 한도 정책(총액의 30%)이 올바르게 적용되는지 테스트한다.
 *
 * 검증 목적:
 * - 쿠폰 + 포인트 할인 합계가 한도(30%) 이내이면 주문이 성공하는지
 * - 할인 합계가 한도와 정확히 같으면(경계값) 주문이 성공하는지
 * - 할인 합계가 한도를 초과하면 DISCOUNT_LIMIT_EXCEEDED 예외가 발생하는지
 * - 쿠폰만으로도 한도를 초과할 수 있는지
 * - pgPaymentAmount = totalAmount - couponDiscount - pointDiscount 공식이 정확한지
 */
@DisplayName("할인 한도 검증")
class OrderDiscountLimitTest extends OrderServiceTestBase {

    @Test
    @DisplayName("쿠폰 2,000 + 포인트 1,000 = 3,000(30%) → 한도 이내이므로 주문이 성공한다")
    void withinLimit_succeeds() {
        couponRepository.save(new CouponEntity("COUPON-2000", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 2000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-2000", 1000
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.couponDiscountAmount()).isEqualTo(2000);
        assertThat(response.pointDiscountAmount()).isEqualTo(1000);
        assertThat(response.pgPaymentAmount()).isEqualTo(7000);
    }

    @Test
    @DisplayName("쿠폰 1,500 + 포인트 1,500 = 3,000 → 한도(30%)와 정확히 같으면 성공한다")
    void exactlyAtLimit_succeeds() {
        couponRepository.save(new CouponEntity("COUPON-1500", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1500, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-1500", 1500
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.couponDiscountAmount() + response.pointDiscountAmount()).isEqualTo(3000);
        assertThat(response.pgPaymentAmount()).isEqualTo(7000);
    }

    @Test
    @DisplayName("쿠폰 2,000 + 포인트 2,000 = 4,000(40%) → 한도 초과로 DISCOUNT_LIMIT_EXCEEDED 예외가 발생한다")
    void exceedsLimit_throws() {
        couponRepository.save(new CouponEntity("COUPON-2000B", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 2000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-2000B", 2000
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("DISCOUNT_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("쿠폰 5,000원만 사용해도(50%) 한도를 초과하면 DISCOUNT_LIMIT_EXCEEDED 예외가 발생한다")
    void couponAloneExceedsLimit_throws() {
        couponRepository.save(new CouponEntity("COUPON-5000", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 5000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-5000", null
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("DISCOUNT_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("pgPaymentAmount가 totalAmount - couponDiscount - pointDiscount 공식과 일치한다")
    void pgPaymentAmount_isCorrect() {
        couponRepository.save(new CouponEntity("COUPON-PG", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 2000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-PG", 1000
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.totalAmount()).isEqualTo(10000);
        assertThat(response.pgPaymentAmount())
                .isEqualTo(response.totalAmount() - response.couponDiscountAmount() - response.pointDiscountAmount());
    }
}
