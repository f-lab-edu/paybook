package com.paybook.order.service;

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
 * 주문 생성 시 포인트 검증을 테스트한다.
 *
 * 검증 목적:
 * - 포인트 잔액이 충분하면 차감이 성공하고 DB 잔액이 정확히 감소하는지
 * - 포인트 잔액이 부족하면 POINTS_UNAVAILABLE 예외가 발생하는지
 * - 경계값(잔액 = 사용액)에서 정상 동작하고 잔액이 0이 되는지
 * - pointAmountToUse가 null이면 포인트 검증을 건너뛰고 DB 잔액이 변동 없는지
 * - 유저의 포인트 레코드 자체가 없으면 POINTS_UNAVAILABLE 예외가 발생하는지
 */
@DisplayName("포인트 검증")
class OrderPointsValidationTest extends OrderServiceTestBase {

    @Test
    @DisplayName("포인트 잔액 충분 → 차감 성공 후 DB 잔액이 사용액만큼 감소한다")
    void sufficientBalance_deducts() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 2)),
                null, null, 2000
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.pointDiscountAmount()).isEqualTo(2000);
        assertThat(response.pgPaymentAmount()).isEqualTo(18000);

        UserPointEntity userPoint = userPointRepository.findByUserId("USER-001").orElseThrow();
        assertThat(userPoint.getBalance()).isEqualTo(3000);
    }

    @Test
    @DisplayName("포인트 잔액(5,000)보다 많은 금액(10,000) 사용 시 POINTS_UNAVAILABLE 예외가 발생한다")
    void insufficientBalance_throws() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, 10000
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("POINTS_UNAVAILABLE"));
    }

    @Test
    @DisplayName("포인트 잔액과 사용액이 정확히 같으면(5,000) 성공하고 DB 잔액이 0이 된다")
    void exactBalance_succeeds() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 2)),
                null, null, 5000
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.pointDiscountAmount()).isEqualTo(5000);

        UserPointEntity userPoint = userPointRepository.findByUserId("USER-001").orElseThrow();
        assertThat(userPoint.getBalance()).isEqualTo(0);
    }

    @Test
    @DisplayName("pointAmountToUse가 null이면 포인트 검증을 건너뛰고 DB 잔액이 변동 없다")
    void nullOrZeroPoints_skips() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.pointDiscountAmount()).isEqualTo(0);

        UserPointEntity userPoint = userPointRepository.findByUserId("USER-001").orElseThrow();
        assertThat(userPoint.getBalance()).isEqualTo(5000);
    }

    @Test
    @DisplayName("포인트 레코드가 없는 유저가 포인트를 사용하면 POINTS_UNAVAILABLE 예외가 발생한다")
    void noPointRecord_throws() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-NO-POINTS",
                List.of(new OrderItemRequest("PROD-001", 1)),
                null, null, 1000
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("POINTS_UNAVAILABLE"));
    }
}
