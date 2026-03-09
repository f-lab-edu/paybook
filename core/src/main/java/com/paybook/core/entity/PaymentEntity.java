package com.paybook.core.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String paymentId;

    @Column(nullable = false)
    private String orderId;

    private int pgPaymentAmount;

    private String pgTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public PaymentEntity(String paymentId, String orderId, int pgPaymentAmount) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.pgPaymentAmount = pgPaymentAmount;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markSuccess(String pgTransactionId) {
        this.status = PaymentStatus.SUCCESS;
        this.pgTransactionId = pgTransactionId;
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public boolean isRefundable() {
        return this.status == PaymentStatus.SUCCESS;
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }
}
