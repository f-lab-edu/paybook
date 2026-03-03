package com.paybook.order.dto;

import java.util.List;

/**
 * 주문 응답 DTO
 */
public record OrderResponse(
        String orderId,
        String userId,
        List<OrderItemResponse> items,
        int totalAmount,
        String status,
        String createdAt
) {
    public record OrderItemResponse(
            String productId,
            int quantity,
            int price
    ) {}
}
