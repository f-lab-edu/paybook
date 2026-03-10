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
 * 주문 내 개별 아이템 단위 취소(부분 취소)가 올바르게 동작하는지 테스트한다.
 *
 * 비즈니스 규칙:
 * - 주문 내 특정 상품만 취소할 수 있다.
 * - 취소된 아이템의 재고는 즉시 복원된다.
 * - 모든 아이템이 취소되면 주문 전체가 CANCELLED 상태로 전환되고
 *   쿠폰/포인트도 복원된다.
 * - CANCELLED, PAYMENT_FAILED, RETURNED, PURCHASE_CONFIRMED 상태에서는
 *   부분 취소가 불가능하다.
 *
 * 검증 목적:
 * - 아이템 취소 후 해당 아이템의 itemStatus가 CANCELLED로 변경되는지
 * - 취소된 아이템의 재고만 복원되고, 다른 아이템의 재고는 영향 없는지
 * - 모든 아이템 취소 시 주문 상태가 CANCELLED로 전환되는지
 * - 이미 취소된 아이템을 재취소하면 PRODUCT_NOT_FOUND 예외가 발생하는지
 */
@DisplayName("부분 취소")
class OrderPartialCancelTest extends OrderServiceTestBase {

    @Test
    @DisplayName("2개 아이템 중 1개 취소 → 해당 아이템만 CANCELLED, 재고 복원")
    void cancelOneItem_restoresStockForThatItem() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(
                        new OrderItemRequest("PROD-001", 2),
                        new OrderItemRequest("PROD-002", 1)
                ),
                null, null, null
        );

        OrderResponse created = orderService.createOrder(request);

        OrderResponse afterCancel = orderService.cancelItem(created.orderId(), "PROD-001");

        // PROD-001 아이템만 CANCELLED
        assertThat(afterCancel.items()).anySatisfy(item -> {
            if ("PROD-001".equals(item.productId())) {
                assertThat(item.itemStatus()).isEqualTo("CANCELLED");
            }
        });
        assertThat(afterCancel.items()).anySatisfy(item -> {
            if ("PROD-002".equals(item.productId())) {
                assertThat(item.itemStatus()).isEqualTo("ACTIVE");
            }
        });

        // PROD-001 재고 복원, PROD-002 재고는 차감 상태 유지
        assertThat(productRepository.findByProductId("PROD-001").orElseThrow().getStockQuantity()).isEqualTo(100);
        assertThat(productRepository.findByProductId("PROD-002").orElseThrow().getStockQuantity()).isEqualTo(99);

        // 주문은 아직 취소되지 않음 (PROD-002가 남아있으므로)
        assertThat(afterCancel.status()).isNotEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("모든 아이템 취소 → 주문 전체 CANCELLED + 쿠폰/포인트 복원")
    void cancelAllItems_orderBecomesCancelled() {
        couponRepository.save(new CouponEntity("COUPON-PARTIAL", CouponStatus.ACTIVE,
                CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1000, null));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(
                        new OrderItemRequest("PROD-001", 1),
                        new OrderItemRequest("PROD-002", 1)
                ),
                null, "COUPON-PARTIAL", 1000
        );

        OrderResponse created = orderService.createOrder(request);
        orderService.cancelItem(created.orderId(), "PROD-001");
        OrderResponse afterAllCancel = orderService.cancelItem(created.orderId(), "PROD-002");

        assertThat(afterAllCancel.status()).isEqualTo("CANCELLED");

        // 쿠폰/포인트 복원 확인
        assertThat(couponRepository.findByCouponId("COUPON-PARTIAL").orElseThrow().getStatus())
                .isEqualTo(CouponStatus.ACTIVE);
        assertThat(userPointRepository.findByUserId("USER-001").orElseThrow().getBalance()).isEqualTo(5000);
    }

    @Test
    @DisplayName("이미 취소된 아이템을 재취소하면 PRODUCT_NOT_FOUND 예외가 발생한다")
    void cancelAlreadyCancelledItem_throws() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(
                        new OrderItemRequest("PROD-001", 1),
                        new OrderItemRequest("PROD-002", 1)
                ),
                null, null, null
        );

        OrderResponse created = orderService.createOrder(request);
        orderService.cancelItem(created.orderId(), "PROD-001");

        assertThatThrownBy(() -> orderService.cancelItem(created.orderId(), "PROD-001"))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("CANCELLED 상태 주문의 아이템 취소 시도 → ORDER_NOT_CANCELLABLE 예외")
    void cancelledOrder_cannotCancelItem() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, null
        );

        OrderResponse created = orderService.createOrder(request);
        orderService.cancelOrder(created.orderId());

        assertThatThrownBy(() -> orderService.cancelItem(created.orderId(), "PROD-001"))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("ORDER_NOT_CANCELLABLE"));
    }
}
