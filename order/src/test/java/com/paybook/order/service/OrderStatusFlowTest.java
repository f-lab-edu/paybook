package com.paybook.order.service;

import com.paybook.core.entity.CouponStatus;
import com.paybook.core.entity.ProductEntity;
import com.paybook.core.entity.UserPointEntity;
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
 * 주문의 상태 전이(State Machine)가 올바르게 동작하는지 테스트한다.
 *
 * 상태 흐름:
 *   PENDING_PAYMENT → CONFIRMED → CANCELLED
 *   PENDING_PAYMENT → PAYMENT_FAILED (취소 불가)
 *   CANCELLED → 재취소 불가
 *
 * 검증 목적:
 * - 주문 생성 시 초기 상태가 PENDING_PAYMENT인지
 * - confirmOrder 호출 시 CONFIRMED로 전이되는지
 * - markPaymentFailed 호출 시 PAYMENT_FAILED로 전이되고 리소스가 복원되는지
 * - CONFIRMED 주문을 취소하면 CANCELLED로 전이되고 재고가 복원되는지
 * - PAYMENT_FAILED 상태에서 취소하면 ORDER_NOT_CANCELLABLE 예외가 발생하는지
 *   (이미 리소스가 복원된 상태이므로 중복 복원 방지)
 * - CANCELLED 상태에서 재취소하면 ORDER_ALREADY_CANCELLED 예외가 발생하는지
 */
@DisplayName("주문 상태 흐름")
class OrderStatusFlowTest extends OrderServiceTestBase {

    @Test
    @DisplayName("주문 생성 직후 상태는 PENDING_PAYMENT이다")
    void created_isPendingPayment() {
        OrderResponse response = orderService.createOrder(basicRequest());
        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("confirmOrder 호출 → 상태가 CONFIRMED로 전이된다")
    void confirm_becomesConfirmed() {
        OrderResponse created = orderService.createOrder(basicRequest());

        OrderResponse confirmed = orderService.confirmOrder(created.orderId());
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("markPaymentFailed 호출 → PAYMENT_FAILED로 전이되고 재고/쿠폰/포인트가 모두 복원된다")
    void paymentFailed_restoresResources() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 2)),
                null, "COUPON-ACTIVE", 1000
        );

        OrderResponse created = orderService.createOrder(request);

        // 주문 생성 직후: 재고 차감, 쿠폰 사용, 포인트 차감 확인
        assertThat(productRepository.findByProductId("PROD-001").orElseThrow().getStockQuantity()).isEqualTo(98);
        assertThat(couponRepository.findByCouponId("COUPON-ACTIVE").orElseThrow().getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(userPointRepository.findByUserId("USER-001").orElseThrow().getBalance()).isEqualTo(4000);

        OrderResponse failed = orderService.markPaymentFailed(created.orderId());
        assertThat(failed.status()).isEqualTo("PAYMENT_FAILED");

        // 결제 실패 후: 재고/쿠폰/포인트가 원래 값으로 복원되었는지 확인
        assertThat(productRepository.findByProductId("PROD-001").orElseThrow().getStockQuantity()).isEqualTo(100);
        assertThat(couponRepository.findByCouponId("COUPON-ACTIVE").orElseThrow().getStatus()).isEqualTo(CouponStatus.ACTIVE);
        assertThat(userPointRepository.findByUserId("USER-001").orElseThrow().getBalance()).isEqualTo(5000);
    }

    @Test
    @DisplayName("CONFIRMED 주문을 취소하면 CANCELLED로 전이되고 재고가 복원된다")
    void cancelConfirmed_succeeds() {
        OrderResponse created = orderService.createOrder(basicRequest());
        orderService.confirmOrder(created.orderId());

        OrderResponse cancelled = orderService.cancelOrder(created.orderId());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");

        ProductEntity product = productRepository.findByProductId("PROD-001").orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("PAYMENT_FAILED 상태의 주문을 취소하면 ORDER_NOT_CANCELLABLE 예외가 발생한다")
    void cancelPaymentFailed_throws() {
        OrderResponse created = orderService.createOrder(basicRequest());
        orderService.markPaymentFailed(created.orderId());

        assertThatThrownBy(() -> orderService.cancelOrder(created.orderId()))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("ORDER_NOT_CANCELLABLE"));
    }

    @Test
    @DisplayName("이미 취소된 주문을 재취소하면 ORDER_ALREADY_CANCELLED 예외가 발생한다")
    void cancelCancelled_throws() {
        OrderResponse created = orderService.createOrder(basicRequest());
        orderService.cancelOrder(created.orderId());

        assertThatThrownBy(() -> orderService.cancelOrder(created.orderId()))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("ORDER_ALREADY_CANCELLED"));
    }
}
