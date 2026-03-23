package com.paybook.settlement.client;

import com.paybook.settlement.exception.SettlementException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class OrderServiceClient {

    private final RestClient restClient;

    public OrderServiceClient(@Value("${settlement.order-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public OrderData getOrder(String orderId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/orders/{orderId}", orderId)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw SettlementException.orderFetchFailed(orderId);
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            List<OrderItemData> orderItems = items.stream()
                    .map(item -> new OrderItemData(
                            (String) item.get("productId"),
                            ((Number) item.get("quantity")).intValue(),
                            ((Number) item.get("price")).intValue(),
                            (String) item.get("itemStatus")))
                    .toList();

            return new OrderData(
                    (String) response.get("orderId"),
                    (String) response.get("userId"),
                    orderItems,
                    ((Number) response.get("totalAmount")).intValue(),
                    ((Number) response.get("couponDiscountAmount")).intValue(),
                    ((Number) response.get("pointDiscountAmount")).intValue(),
                    ((Number) response.get("pgPaymentAmount")).intValue(),
                    ((Number) response.get("deliveryFee")).intValue(),
                    (String) response.get("status"),
                    (String) response.get("couponId"));
        } catch (SettlementException e) {
            throw e;
        } catch (Exception e) {
            throw SettlementException.orderFetchFailed(orderId);
        }
    }

    public record OrderData(
            String orderId,
            String userId,
            List<OrderItemData> items,
            int totalAmount,
            int couponDiscountAmount,
            int pointDiscountAmount,
            int pgPaymentAmount,
            int deliveryFee,
            String status,
            String couponId
    ) {}

    public record OrderItemData(
            String productId,
            int quantity,
            int price,
            String itemStatus
    ) {}
}
