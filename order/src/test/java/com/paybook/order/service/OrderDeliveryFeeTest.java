package com.paybook.order.service;

import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.CreateOrderRequest.OrderItemRequest;
import com.paybook.order.dto.OrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배송비 정책이 올바르게 적용되는지 테스트한다.
 *
 * 비즈니스 규칙:
 * - 주문 총액(totalAmount)이 무료배송 기준(freeThreshold) 이상이면 배송비 0원
 * - 기준 미만이면 배송비(fee)가 부과되어 pgPaymentAmount에 포함된다.
 *
 * 테스트 환경: fee=3,000원, freeThreshold=30,000원
 *
 * 검증 목적:
 * - 기준 미만 주문 시 배송비가 부과되고 pgPaymentAmount에 합산되는지
 * - 기준 이상 주문 시 배송비가 0원인지
 * - 기준과 정확히 같은 금액일 때(경계값) 무료배송인지
 * - 배송비는 할인 대상이 아닌지 (할인 후 pgPaymentAmount에 별도 합산)
 */
@TestPropertySource(properties = {
        "order.delivery.fee=3000",
        "order.delivery.free-threshold=30000"
})
@DisplayName("배송비 정책")
class OrderDeliveryFeeTest extends OrderServiceTestBase {

    @Test
    @DisplayName("총액 10,000원 < 기준 30,000원 → 배송비 3,000원 부과, PG에 합산")
    void belowThreshold_chargesDeliveryFee() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.deliveryFee()).isEqualTo(3000);
        assertThat(response.pgPaymentAmount()).isEqualTo(10000 + 3000);
    }

    @Test
    @DisplayName("총액 40,000원 ≥ 기준 30,000원 → 배송비 0원, 무료배송")
    void aboveThreshold_freeDelivery() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-002", 2)),
                null, null, null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.deliveryFee()).isEqualTo(0);
        assertThat(response.pgPaymentAmount()).isEqualTo(40000);
    }

    @Test
    @DisplayName("총액 30,000원 = 기준 30,000원 → 경계값 무료배송")
    void exactlyAtThreshold_freeDelivery() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(
                        new OrderItemRequest("PROD-001", 1),
                        new OrderItemRequest("PROD-002", 1)
                ),
                null, null, null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.totalAmount()).isEqualTo(30000);
        assertThat(response.deliveryFee()).isEqualTo(0);
    }

    @Test
    @DisplayName("배송비는 할인 대상이 아님 — 쿠폰 할인 후에도 배송비는 별도로 PG에 합산된다")
    void deliveryFeeNotDiscounted() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-ACTIVE", null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.totalAmount()).isEqualTo(10000);
        assertThat(response.couponDiscountAmount()).isEqualTo(1000);
        assertThat(response.deliveryFee()).isEqualTo(3000);
        // PG = 상품금액 - 할인 + 배송비 = 10000 - 1000 + 3000
        assertThat(response.pgPaymentAmount()).isEqualTo(12000);
    }
}
