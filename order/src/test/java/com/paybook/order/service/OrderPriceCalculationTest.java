package com.paybook.order.service;

import com.paybook.core.entity.CouponEntity;
import com.paybook.core.entity.CouponStatus;
import com.paybook.core.entity.CouponType;
import com.paybook.core.entity.DiscountType;
import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.CreateOrderRequest.OrderItemRequest;
import com.paybook.order.dto.OrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문의 가격 계산과 결제 금액 분배가 정확한지 테스트한다.
 *
 * 검증 목적:
 * - 단일 아이템의 totalAmount = price × quantity 계산이 정확한지
 * - 다수 아이템 합산이 정확한지
 * - 쿠폰/포인트/PG 결제 금액의 분배가 정확한지
 *   (pgPaymentAmount = totalAmount - couponDiscount - pointDiscount)
 * - 할인 없이 전액 PG 결제 시 pgPaymentAmount = totalAmount인지
 */
@DisplayName("가격 계산 + 결제 내역")
class OrderPriceCalculationTest extends OrderServiceTestBase {

    @Test
    @DisplayName("단일 아이템 — 10,000원 × 2개 = totalAmount 20,000원")
    void singleItem_correctTotal() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 2)),
                null, null, null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.totalAmount()).isEqualTo(20000);
    }

    @Test
    @DisplayName("다수 아이템 합산 — PROD-001(10,000)×2 + PROD-002(20,000)×1 = totalAmount 40,000원")
    void multipleItems_correctTotal() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(
                        new OrderItemRequest("PROD-001", 2),
                        new OrderItemRequest("PROD-002", 1)
                ),
                null, null, null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.totalAmount()).isEqualTo(40000);
    }

    @Test
    @DisplayName("할인 분배 — total 10,000 - coupon 2,000 - point 1,000 = PG 7,000원")
    void discountBreakdown() {
        couponRepository.save(new CouponEntity("COUPON-DIST", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 2000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-DIST", 1000
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.totalAmount()).isEqualTo(10000);
        assertThat(response.couponDiscountAmount()).isEqualTo(2000);
        assertThat(response.pointDiscountAmount()).isEqualTo(1000);
        assertThat(response.pgPaymentAmount()).isEqualTo(7000);
    }

    @Test
    @DisplayName("할인 없이 전액 PG 결제 — coupon=null, point=null → pgPaymentAmount = totalAmount")
    void noDiscount_fullPg() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.couponDiscountAmount()).isEqualTo(0);
        assertThat(response.pointDiscountAmount()).isEqualTo(0);
        assertThat(response.pgPaymentAmount()).isEqualTo(response.totalAmount());
    }
}
