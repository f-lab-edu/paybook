package com.paybook.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class PaymentServiceClient {

    private final RestClient restClient;

    public PaymentServiceClient(@Value("${order.payment-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public void requestFullRefund(String orderId) {
        restClient.post()
                .uri("/api/payments/{orderId}/refund", orderId)
                .retrieve()
                .toBodilessEntity();
    }

    public void requestPartialRefund(String orderId, int refundAmount) {
        restClient.post()
                .uri("/api/payments/{orderId}/partial-refund", orderId)
                .body(Map.of("refundAmount", refundAmount))
                .retrieve()
                .toBodilessEntity();
    }
}
