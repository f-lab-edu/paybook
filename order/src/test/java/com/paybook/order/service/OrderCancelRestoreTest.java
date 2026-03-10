package com.paybook.order.service;

import com.paybook.core.entity.CouponEntity;
import com.paybook.core.entity.CouponStatus;
import com.paybook.core.entity.ProductEntity;
import com.paybook.core.entity.UserPointEntity;
import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.CreateOrderRequest.OrderItemRequest;
import com.paybook.order.dto.OrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 취소 시 리소스(재고, 쿠폰, 포인트)가 올바르게 복원되는지 테스트한다.
 *
 * 검증 목적:
 * - 취소 시 재고가 주문 수량만큼 복원되는지
 * - 취소 시 사용된 쿠폰의 상태가 USED → ACTIVE로 복원되는지
 * - 취소 시 차감된 포인트가 원래 잔액으로 복원되는지
 * - 쿠폰+포인트+재고를 모두 사용한 주문 취소 시 세 리소스가 동시에 복원되는지
 * - PG 결제가 있는 주문 취소 시 상태만 변경되는지 (PG 환불은 결제 서비스 책임)
 * - 쿠폰/포인트 미사용 주문 취소 시 복원 로직을 건너뛰고 재고만 복원되는지
 */
@DisplayName("취소 + 복원")
class OrderCancelRestoreTest extends OrderServiceTestBase {

    @Test
    @DisplayName("주문 취소 → 재고가 주문 수량만큼 복원된다")
    void cancel_restoresStock() {
        OrderResponse created = orderService.createOrder(basicRequest());
        assertThat(productRepository.findByProductId("PROD-001").orElseThrow().getStockQuantity()).isEqualTo(98);

        orderService.cancelOrder(created.orderId());

        ProductEntity product = productRepository.findByProductId("PROD-001").orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("주문 취소 → 사용된 쿠폰 상태가 USED에서 ACTIVE로 복원된다")
    void cancel_restoresCoupon() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, "COUPON-ACTIVE", null
        );

        OrderResponse created = orderService.createOrder(request);
        assertThat(couponRepository.findByCouponId("COUPON-ACTIVE").orElseThrow().getStatus())
                .isEqualTo(CouponStatus.USED);

        orderService.cancelOrder(created.orderId());

        CouponEntity coupon = couponRepository.findByCouponId("COUPON-ACTIVE").orElseThrow();
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("주문 취소 → 차감된 포인트가 원래 잔액으로 복원된다")
    void cancel_restoresPoints() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, 2000
        );

        OrderResponse created = orderService.createOrder(request);
        assertThat(userPointRepository.findByUserId("USER-001").orElseThrow().getBalance()).isEqualTo(3000);

        orderService.cancelOrder(created.orderId());

        UserPointEntity userPoint = userPointRepository.findByUserId("USER-001").orElseThrow();
        assertThat(userPoint.getBalance()).isEqualTo(5000);
    }

    @Test
    @DisplayName("쿠폰+포인트+재고 모두 사용한 주문 취소 → 세 리소스가 동시에 복원된다")
    void cancel_restoresAll() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 2)),
                null, "COUPON-ACTIVE", 1000
        );

        OrderResponse created = orderService.createOrder(request);
        orderService.cancelOrder(created.orderId());

        assertThat(productRepository.findByProductId("PROD-001").orElseThrow().getStockQuantity()).isEqualTo(100);
        assertThat(couponRepository.findByCouponId("COUPON-ACTIVE").orElseThrow().getStatus()).isEqualTo(CouponStatus.ACTIVE);
        assertThat(userPointRepository.findByUserId("USER-001").orElseThrow().getBalance()).isEqualTo(5000);
    }

    @Test
    @DisplayName("PG 결제가 포함된 주문 취소 → 상태만 CANCELLED로 변경된다 (PG 환불은 결제 서비스 책임)")
    void cancelWithPgPayment_statusOnly() {
        OrderResponse created = orderService.createOrder(basicRequest());
        assertThat(created.pgPaymentAmount()).isGreaterThan(0);

        OrderResponse cancelled = orderService.cancelOrder(created.orderId());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("쿠폰/포인트 미사용 주문 취소 → 복원 로직을 건너뛰고 재고만 복원된다")
    void cancelWithoutDiscounts_statusAndStockOnly() {
        OrderResponse created = orderService.createOrder(basicRequest());

        OrderResponse cancelled = orderService.cancelOrder(created.orderId());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");

        ProductEntity product = productRepository.findByProductId("PROD-001").orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(100);
    }
}
