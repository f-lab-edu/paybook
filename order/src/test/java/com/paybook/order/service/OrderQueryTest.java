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
 * 주문 조회(getOrder) API가 올바른 데이터를 반환하는지 테스트한다.
 *
 * 검증 목적:
 * - 생성된 주문을 orderId로 조회하면 모든 필드(orderId, userId, totalAmount,
 *   couponDiscountAmount, pointDiscountAmount, pgPaymentAmount, status, items)가
 *   생성 시점과 일치하는지
 * - 존재하지 않는 orderId로 조회하면 ORDER_NOT_FOUND 예외가 발생하는지
 */
@DisplayName("주문 조회")
class OrderQueryTest extends OrderServiceTestBase {

    @Test
    @DisplayName("존재하는 주문 조회 → 생성 시 입력한 모든 결제 내역 필드가 정확히 반환된다")
    void existingOrder_returnsCorrectResponse() {
        couponRepository.save(new CouponEntity("COUPON-GET", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 2000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-GET", 1000
        );

        OrderResponse created = orderService.createOrder(request);
        OrderResponse found = orderService.getOrder(created.orderId());

        assertThat(found.orderId()).isEqualTo(created.orderId());
        assertThat(found.userId()).isEqualTo("USER-001");
        assertThat(found.totalAmount()).isEqualTo(10000);
        assertThat(found.couponDiscountAmount()).isEqualTo(2000);
        assertThat(found.pointDiscountAmount()).isEqualTo(1000);
        assertThat(found.pgPaymentAmount()).isEqualTo(7000);
        assertThat(found.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(found.items()).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 orderId로 조회하면 ORDER_NOT_FOUND 예외가 발생한다")
    void nonExistentOrder_throws() {
        assertThatThrownBy(() -> orderService.getOrder("ORD-999999"))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("ORDER_NOT_FOUND"));
    }
}
