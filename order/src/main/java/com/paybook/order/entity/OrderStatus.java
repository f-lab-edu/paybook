package com.paybook.order.entity;

public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    SHIPPING,
    DELIVERED,
    PURCHASE_CONFIRMED,
    RETURN_REQUESTED,
    RETURNED,
    CANCELLED,
    PAYMENT_FAILED
}
