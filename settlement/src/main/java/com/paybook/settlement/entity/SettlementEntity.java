package com.paybook.settlement.entity;

import com.paybook.settlement.exception.SettlementException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SettlementEntity {

    private static final int PERCENT_DIVISOR = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String settlementId;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private String sellerId;

    private int orderAmount;

    private int commissionRate;

    private int commissionAmount;

    private int platformCouponAmount;

    private int sellerCouponAmount;

    private int pointDiscountAmount;

    private int deliveryFee;

    private int settlementAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime confirmedAt;

    private LocalDateTime paidAt;

    @Version
    private Long version;

    public static SettlementEntity create(String settlementId, String orderId, String sellerId,
                                          int orderAmount, int commissionRate,
                                          int platformCouponAmount, int sellerCouponAmount,
                                          int pointDiscountAmount, int deliveryFee) {
        int commissionAmount = orderAmount * commissionRate / PERCENT_DIVISOR;
        int settlementAmount = orderAmount - commissionAmount - sellerCouponAmount + deliveryFee;

        return SettlementEntity.builder()
                .settlementId(settlementId)
                .orderId(orderId)
                .sellerId(sellerId)
                .orderAmount(orderAmount)
                .commissionRate(commissionRate)
                .commissionAmount(commissionAmount)
                .platformCouponAmount(platformCouponAmount)
                .sellerCouponAmount(sellerCouponAmount)
                .pointDiscountAmount(pointDiscountAmount)
                .deliveryFee(deliveryFee)
                .settlementAmount(settlementAmount)
                .build();
    }

    public void confirm() {
        if (this.status != SettlementStatus.PENDING) {
            throw SettlementException.invalidStatusTransition(
                    settlementId, status.name(), SettlementStatus.CONFIRMED.name());
        }
        this.status = SettlementStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void markPaid() {
        if (this.status != SettlementStatus.CONFIRMED) {
            throw SettlementException.invalidStatusTransition(
                    settlementId, status.name(), SettlementStatus.PAID.name());
        }
        this.status = SettlementStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == SettlementStatus.PAID) {
            throw SettlementException.alreadyPaid(settlementId);
        }
        if (this.status == SettlementStatus.CANCELLED) {
            throw SettlementException.alreadyCancelled(settlementId);
        }
        this.status = SettlementStatus.CANCELLED;
    }
}
