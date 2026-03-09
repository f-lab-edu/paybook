package com.paybook.payment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrderServiceClient {

    private final RestClient restClient;

    public OrderServiceClient(@Value("${payment.order-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public void confirmOrder(String orderId) {
        restClient.patch()
                .uri("/api/orders/{orderId}/confirm", orderId)
                .retrieve()
                .toBodilessEntity();
    }

    public void markPaymentFailed(String orderId) {
        restClient.patch()
                .uri("/api/orders/{orderId}/payment-failed", orderId)
                .retrieve()
                .toBodilessEntity();
    }
}
