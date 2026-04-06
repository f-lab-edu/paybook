package com.paybook.order.dto;

import java.util.List;

public record OrderResponse(
        String orderId,
        String userId,
        List<OrderItemResponse> items,
        int totalAmount,
        int couponDiscountAmount,
        int pointDiscountAmount,
        int pgPaymentAmount,
        int deliveryFee,
        String status,
        String couponId,
        String createdAt
) {
    public record OrderItemResponse(
            String productId,
            int quantity,
            int price,
            String itemStatus
    ) {}
}
