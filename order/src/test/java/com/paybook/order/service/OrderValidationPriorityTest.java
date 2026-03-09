package com.paybook.order.service;

import com.paybook.core.entity.ProductEntity;
import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.CreateOrderRequest.OrderItemRequest;
import com.paybook.order.exception.OrderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주문 생성 시 여러 검증이 동시에 실패할 경우,
 * 어떤 에러가 먼저 반환되는지(검증 우선순위)를 테스트한다.
 *
 * 검증 순서 계약: 상품 존재 → 재고 → 쿠폰 → 포인트 → 할인 한도
 *
 * 검증 목적:
 * - 클라이언트가 에러 메시지를 보고 순서대로 수정할 수 있도록
 *   검증 순서가 일관되게 유지되는지 보장한다.
 * - 상품 미존재와 쿠폰 미존재가 동시이면 PRODUCT_NOT_FOUND가 먼저 발생하는지
 * - 재고 부족과 쿠폰 USED가 동시이면 OUT_OF_STOCK이 먼저 발생하는지
 * - 쿠폰 USED와 포인트 부족이 동시이면 COUPON_ALREADY_USED가 먼저 발생하는지
 */
@DisplayName("검증 순서")
class OrderValidationPriorityTest extends OrderServiceTestBase {

    @Test
    @DisplayName("상품 미존재 + 쿠폰 미존재 → 상품 검증이 먼저이므로 PRODUCT_NOT_FOUND가 발생한다")
    void productNotFound_beforeCouponNotFound() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-NONEXISTENT", 1)),
                null, "COUPON-NONEXISTENT", null
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("재고 부족 + 쿠폰 USED → 재고 검증이 먼저이므로 OUT_OF_STOCK이 발생한다")
    void outOfStock_beforeCouponUsed() {
        productRepository.save(new ProductEntity("PROD-EMPTY", "빈재고", 10000, 0));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-EMPTY", 1)),
                null, "COUPON-USED", null
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("OUT_OF_STOCK"));
    }

    @Test
    @DisplayName("쿠폰 USED + 포인트 부족 → 쿠폰 검증이 먼저이므로 COUPON_ALREADY_USED가 발생한다")
    void couponUsed_beforePointsUnavailable() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-USED", 999999
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("COUPON_ALREADY_USED"));
    }
}
