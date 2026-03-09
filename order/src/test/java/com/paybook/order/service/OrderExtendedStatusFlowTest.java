package com.paybook.order.service;

import com.paybook.order.dto.OrderResponse;
import com.paybook.order.exception.OrderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 확장된 주문 상태 전이가 올바르게 동작하는지 테스트한다.
 *
 * 전체 상태 흐름:
 *   PENDING_PAYMENT → CONFIRMED → SHIPPING → DELIVERED → PURCHASE_CONFIRMED
 *                                                       → RETURN_REQUESTED → RETURNED
 *
 * 검증 목적:
 * - 정상 흐름(결제→확인→배송→배달→구매확정)이 순서대로 동작하는지
 * - 반품 흐름(배달→반품요청→반품완료)이 동작하고 리소스가 복원되는지
 * - 잘못된 상태에서 전이 시도 시 INVALID_STATUS_TRANSITION 예외가 발생하는지
 * - SHIPPING 상태에서 취소가 가능한지
 * - PURCHASE_CONFIRMED/RETURNED 상태에서 취소가 불가능한지
 */
@DisplayName("확장 주문 상태 흐름")
class OrderExtendedStatusFlowTest extends OrderServiceTestBase {

    private OrderResponse createAndConfirm() {
        OrderResponse created = orderService.createOrder(basicRequest());
        return orderService.confirmOrder(created.orderId());
    }

    @Test
    @DisplayName("정상 흐름 — CONFIRMED → SHIPPING → DELIVERED → PURCHASE_CONFIRMED")
    void happyPath_fullFlow() {
        OrderResponse confirmed = createAndConfirm();

        OrderResponse shipping = orderService.startShipping(confirmed.orderId());
        assertThat(shipping.status()).isEqualTo("SHIPPING");

        OrderResponse delivered = orderService.markDelivered(confirmed.orderId());
        assertThat(delivered.status()).isEqualTo("DELIVERED");

        OrderResponse purchaseConfirmed = orderService.confirmPurchase(confirmed.orderId());
        assertThat(purchaseConfirmed.status()).isEqualTo("PURCHASE_CONFIRMED");
    }

    @Test
    @DisplayName("반품 흐름 — DELIVERED → RETURN_REQUESTED → RETURNED + 리소스 복원")
    void returnFlow_restoresResources() {
        OrderResponse confirmed = createAndConfirm();
        orderService.startShipping(confirmed.orderId());
        orderService.markDelivered(confirmed.orderId());

        OrderResponse returnRequested = orderService.requestReturn(confirmed.orderId());
        assertThat(returnRequested.status()).isEqualTo("RETURN_REQUESTED");

        OrderResponse returned = orderService.completeReturn(confirmed.orderId());
        assertThat(returned.status()).isEqualTo("RETURNED");

        // 반품 완료 시 재고 복원 확인
        assertThat(productRepository.findByProductId("PROD-001").orElseThrow().getStockQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("PENDING_PAYMENT에서 배송 시작 시도 → INVALID_STATUS_TRANSITION 예외")
    void pendingPayment_cannotShip() {
        OrderResponse created = orderService.createOrder(basicRequest());

        assertThatThrownBy(() -> orderService.startShipping(created.orderId()))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("CONFIRMED에서 배달 완료 시도 → INVALID_STATUS_TRANSITION 예외 (배송 건너뛸 수 없음)")
    void confirmed_cannotSkipToDelivered() {
        OrderResponse confirmed = createAndConfirm();

        assertThatThrownBy(() -> orderService.markDelivered(confirmed.orderId()))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("SHIPPING에서 반품 요청 시도 → INVALID_STATUS_TRANSITION 예외")
    void shipping_cannotRequestReturn() {
        OrderResponse confirmed = createAndConfirm();
        orderService.startShipping(confirmed.orderId());

        assertThatThrownBy(() -> orderService.requestReturn(confirmed.orderId()))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("SHIPPING 상태에서 취소 가능")
    void shipping_canCancel() {
        OrderResponse confirmed = createAndConfirm();
        orderService.startShipping(confirmed.orderId());

        OrderResponse cancelled = orderService.cancelOrder(confirmed.orderId());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("PURCHASE_CONFIRMED 상태에서 취소 불가 → ORDER_NOT_CANCELLABLE 예외")
    void purchaseConfirmed_cannotCancel() {
        OrderResponse confirmed = createAndConfirm();
        orderService.startShipping(confirmed.orderId());
        orderService.markDelivered(confirmed.orderId());
        orderService.confirmPurchase(confirmed.orderId());

        assertThatThrownBy(() -> orderService.cancelOrder(confirmed.orderId()))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("ORDER_NOT_CANCELLABLE"));
    }

    @Test
    @DisplayName("RETURNED 상태에서 취소 불가 → ORDER_NOT_CANCELLABLE 예외")
    void returned_cannotCancel() {
        OrderResponse confirmed = createAndConfirm();
        orderService.startShipping(confirmed.orderId());
        orderService.markDelivered(confirmed.orderId());
        orderService.requestReturn(confirmed.orderId());
        orderService.completeReturn(confirmed.orderId());

        assertThatThrownBy(() -> orderService.cancelOrder(confirmed.orderId()))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("ORDER_NOT_CANCELLABLE"));
    }
}
