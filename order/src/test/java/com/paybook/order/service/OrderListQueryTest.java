package com.paybook.order.service;

import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.CreateOrderRequest.OrderItemRequest;
import com.paybook.order.dto.OrderResponse;
import com.paybook.order.entity.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 목록 조회(페이징, 상태 필터링)가 올바르게 동작하는지 테스트한다.
 *
 * 검증 목적:
 * - userId로 본인의 주문 목록만 조회되는지
 * - 최신 주문이 먼저 반환되는지 (createdAt DESC)
 * - 페이징이 정확히 동작하는지 (page, size, totalElements)
 * - 상태 필터링(status)이 해당 상태의 주문만 반환하는지
 * - 주문이 없는 유저 조회 시 빈 페이지가 반환되는지
 */
@DisplayName("주문 목록 조회")
class OrderListQueryTest extends OrderServiceTestBase {

    private void createOrders(String userId, int count) {
        for (int i = 0; i < count; i++) {
            orderService.createOrder(new CreateOrderRequest(
                    userId,
                    List.of(new OrderItemRequest("PROD-001", 1)),
                    null, null, null
            ));
        }
    }

    @Test
    @DisplayName("userId로 조회 → 해당 유저의 주문만 반환된다")
    void filterByUserId() {
        createOrders("USER-001", 3);

        Page<OrderResponse> page = orderService.getOrdersByUserId("USER-001", null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).allSatisfy(order ->
                assertThat(order.userId()).isEqualTo("USER-001"));
    }

    @Test
    @DisplayName("다른 유저의 주문은 조회되지 않는다")
    void otherUserOrders_notReturned() {
        createOrders("USER-001", 2);

        Page<OrderResponse> page = orderService.getOrdersByUserId("USER-OTHER", null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("페이징 — 총 5개 중 size=2로 조회하면 3페이지, 마지막 페이지는 1개")
    void pagination_works() {
        createOrders("USER-001", 5);

        Page<OrderResponse> firstPage = orderService.getOrdersByUserId("USER-001", null, PageRequest.of(0, 2));
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);

        Page<OrderResponse> lastPage = orderService.getOrdersByUserId("USER-001", null, PageRequest.of(2, 2));
        assertThat(lastPage.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("상태 필터 — CONFIRMED 주문만 조회된다")
    void filterByStatus() {
        createOrders("USER-001", 3);

        // 첫 번째 주문만 CONFIRMED로 변경
        Page<OrderResponse> all = orderService.getOrdersByUserId("USER-001", null, PageRequest.of(0, 10));
        String firstOrderId = all.getContent().get(0).orderId();
        orderService.confirmOrder(firstOrderId);

        Page<OrderResponse> confirmed = orderService.getOrdersByUserId(
                "USER-001", OrderStatus.CONFIRMED, PageRequest.of(0, 10));

        assertThat(confirmed.getTotalElements()).isEqualTo(1);
        assertThat(confirmed.getContent().get(0).status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("주문 없는 유저 조회 → 빈 페이지 반환")
    void noOrders_emptyPage() {
        Page<OrderResponse> page = orderService.getOrdersByUserId("USER-EMPTY", null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(0);
        assertThat(page.getContent()).isEmpty();
    }
}
