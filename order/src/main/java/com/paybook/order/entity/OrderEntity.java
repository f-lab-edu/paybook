package com.paybook.order.entity;

import com.paybook.order.exception.OrderException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEntity {

    private static final Set<OrderStatus> NON_CANCELLABLE_STATUSES = EnumSet.of(
            OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED,
            OrderStatus.RETURNED, OrderStatus.PURCHASE_CONFIRMED
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private String userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> items = new ArrayList<>();

    private int totalAmount;

    private int couponDiscountAmount;

    private int pointDiscountAmount;

    private int pgPaymentAmount;

    private int deliveryFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private String deliveryAddress;

    private String couponId;

    private Integer pointAmountToUse;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public OrderEntity(String orderId, String userId, int totalAmount,
                       int couponDiscountAmount, int pointDiscountAmount, int pgPaymentAmount,
                       int deliveryFee, String deliveryAddress, String couponId,
                       Integer pointAmountToUse) {
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.couponDiscountAmount = couponDiscountAmount;
        this.pointDiscountAmount = pointDiscountAmount;
        this.pgPaymentAmount = pgPaymentAmount;
        this.deliveryFee = deliveryFee;
        this.deliveryAddress = deliveryAddress;
        this.couponId = couponId;
        this.pointAmountToUse = pointAmountToUse;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.createdAt = LocalDateTime.now();
    }

    public void addItem(OrderItemEntity item) {
        items.add(item);
        item.setOrder(this);
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }

    public void startShipping() {
        validateTransition(OrderStatus.CONFIRMED, OrderStatus.SHIPPING);
        this.status = OrderStatus.SHIPPING;
    }

    public void markDelivered() {
        validateTransition(OrderStatus.SHIPPING, OrderStatus.DELIVERED);
        this.status = OrderStatus.DELIVERED;
    }

    public void confirmPurchase() {
        validateTransition(OrderStatus.DELIVERED, OrderStatus.PURCHASE_CONFIRMED);
        this.status = OrderStatus.PURCHASE_CONFIRMED;
    }

    public void requestReturn() {
        validateTransition(OrderStatus.DELIVERED, OrderStatus.RETURN_REQUESTED);
        this.status = OrderStatus.RETURN_REQUESTED;
    }

    public void completeReturn() {
        validateTransition(OrderStatus.RETURN_REQUESTED, OrderStatus.RETURNED);
        this.status = OrderStatus.RETURNED;
    }

    public void markPaymentFailed() {
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw OrderException.orderAlreadyCancelled(orderId);
        }
        if (NON_CANCELLABLE_STATUSES.contains(this.status)) {
            throw OrderException.orderNotCancellable(orderId);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public boolean isCancellable() {
        return !NON_CANCELLABLE_STATUSES.contains(this.status);
    }

    private void validateTransition(OrderStatus expectedCurrent, OrderStatus target) {
        if (this.status != expectedCurrent) {
            throw OrderException.invalidStatusTransition(orderId, this.status.name(), target.name());
        }
    }
}
