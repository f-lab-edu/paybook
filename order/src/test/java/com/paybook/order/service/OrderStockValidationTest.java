package com.paybook.order.service;

import com.paybook.core.entity.ProductEntity;
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
 * 주문 생성 시 상품 재고 검증을 테스트한다.
 *
 * 검증 목적:
 * - 재고가 충분하면 주문이 성공하고 DB에서 재고가 차감되는지
 * - 재고가 부족하면 OUT_OF_STOCK 예외가 발생하고 DB 재고가 변동 없는지
 * - 경계값(재고 = 주문 수량)에서 정상 동작하는지
 * - 존재하지 않는 상품에 대해 PRODUCT_NOT_FOUND 예외가 발생하는지
 * - 여러 아이템 중 하나라도 재고 부족이면 전체 주문이 실패하고
 *   다른 상품의 재고도 차감되지 않는지 (트랜잭션 원자성)
 */
@DisplayName("재고 검증")
class OrderStockValidationTest extends OrderServiceTestBase {

    @Test
    @DisplayName("재고 충분 → 주문 성공 후 DB 재고가 주문 수량만큼 차감된다")
    void sufficientStock_succeeds_andDeductsInDb() {
        OrderResponse response = orderService.createOrder(basicRequest());

        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");

        ProductEntity product = productRepository.findByProductId("PROD-001").orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(98);
    }

    @Test
    @DisplayName("재고 부족 → OUT_OF_STOCK 예외가 발생하고 DB 재고는 변동 없다")
    void insufficientStock_throwsOutOfStock() {
        productRepository.save(new ProductEntity("PROD-LOW", "재고부족", 10000, 5));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-LOW", 10)),
                null, null, null
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("OUT_OF_STOCK"));

        ProductEntity product = productRepository.findByProductId("PROD-LOW").orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("재고와 주문 수량이 정확히 같으면 성공하고 DB 재고가 0이 된다")
    void exactStock_succeeds_stockBecomesZero() {
        productRepository.save(new ProductEntity("PROD-EXACT", "정확소진", 10000, 5));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-EXACT", 5)),
                null, null, null
        );

        OrderResponse response = orderService.createOrder(request);
        assertThat(response.status()).isEqualTo("PENDING_PAYMENT");

        ProductEntity product = productRepository.findByProductId("PROD-EXACT").orElseThrow();
        assertThat(product.getStockQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("존재하지 않는 상품 ID로 주문하면 PRODUCT_NOT_FOUND 예외가 발생한다")
    void productNotFound_throws() {
        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(new OrderItemRequest("PROD-NONEXISTENT", 1)),
                null, null, null
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("여러 아이템 중 하나라도 재고 부족이면 전체 주문이 실패하고 모든 재고가 변동 없다")
    void oneItemOutOfStock_nothingDeducted() {
        productRepository.save(new ProductEntity("PROD-FEW", "재고적음", 10000, 1));

        CreateOrderRequest request = new CreateOrderRequest(
                "USER-001",
                List.of(
                        new OrderItemRequest("PROD-001", 2),
                        new OrderItemRequest("PROD-FEW", 5)
                ),
                null, null, null
        );

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode()).isEqualTo("OUT_OF_STOCK"));

        ProductEntity prod001 = productRepository.findByProductId("PROD-001").orElseThrow();
        assertThat(prod001.getStockQuantity()).isEqualTo(100);
    }
}
