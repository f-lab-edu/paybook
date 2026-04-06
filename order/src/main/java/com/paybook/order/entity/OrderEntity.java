package com.paybook.order.entity;

import com.paybook.order.exception.OrderException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
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
    @Builder.Default
    private List<OrderItemEntity> items = new ArrayList<>();

    private int totalAmount;

    private int couponDiscountAmount;

    private int pointDiscountAmount;

    private int pgPaymentAmount;

    private int deliveryFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    private String deliveryAddress;

    private String couponId;

    private Integer pointAmountToUse;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Version
    private Long version;

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

    public boolean isExchangeable() {
        return this.status == OrderStatus.DELIVERED;
    }

    // ── 금액 재계산 ──

    public int calculateActiveItemsTotal() {
        return items.stream()
                .filter(OrderItemEntity::isActive)
                .mapToInt(OrderItemEntity::getItemTotal)
                .sum();
    }

    public double calculateCancelledItemRatio(OrderItemEntity cancelledItem) {
        return (double) cancelledItem.getItemTotal() / this.totalAmount;
    }

    public int calculateProportionalPointRestore(OrderItemEntity cancelledItem) {
        if (this.pointAmountToUse == null || this.pointAmountToUse <= 0) {
            return 0;
        }
        double ratio = calculateCancelledItemRatio(cancelledItem);
        return (int) Math.floor(this.pointAmountToUse * ratio);
    }

    public void recalculateAmountsAfterPartialCancel(int newDeliveryFee, int restoredPoints) {
        this.totalAmount = calculateActiveItemsTotal();
        this.pointDiscountAmount -= restoredPoints;
        this.deliveryFee = newDeliveryFee;
        this.pgPaymentAmount = this.totalAmount - this.couponDiscountAmount
                - this.pointDiscountAmount + this.deliveryFee;
    }

    // ── 정합성 검증 ──

    public void validateTotalAmountConsistency() {
        int expectedTotal = calculateActiveItemsTotal();
        if (this.totalAmount != expectedTotal) {
            throw OrderException.amountInconsistency(
                    this.orderId, "totalAmount", expectedTotal, this.totalAmount);
        }
    }

    public void validatePgPaymentConsistency() {
        int expectedPgPayment = this.totalAmount - this.couponDiscountAmount
                - this.pointDiscountAmount + this.deliveryFee;
        if (this.pgPaymentAmount != expectedPgPayment) {
            throw OrderException.amountInconsistency(
                    this.orderId, "pgPaymentAmount", expectedPgPayment, this.pgPaymentAmount);
        }
    }

    public void validateAmountConsistency() {
        validateTotalAmountConsistency();
        validatePgPaymentConsistency();
    }

    private void validateTransition(OrderStatus expectedCurrent, OrderStatus target) {
        if (this.status != expectedCurrent) {
            throw OrderException.invalidStatusTransition(orderId, this.status.name(), target.name());
        }
    }
}
