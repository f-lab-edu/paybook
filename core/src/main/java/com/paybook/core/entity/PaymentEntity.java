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

    @Column(nullable = false, unique = true)
    private String orderId;

    private int pgPaymentAmount;

    private String pgTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    private int refundedAmount;

    private String refundBankCode;

    private String refundAccountNumber;

    private String refundAccountHolder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public PaymentEntity(String paymentId, String orderId, int pgPaymentAmount, PaymentMethod paymentMethod) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.pgPaymentAmount = pgPaymentAmount;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.PENDING;
        this.refundedAmount = 0;
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
        return this.status == PaymentStatus.SUCCESS
                || this.status == PaymentStatus.PARTIALLY_REFUNDED;
    }

    public boolean isPartialRefundable() {
        return isRefundable();
    }

    public int getRefundableAmount() {
        return this.pgPaymentAmount - this.refundedAmount;
    }

    public void applyPartialRefund(int refundAmount) {
        if (refundAmount <= 0) {
            throw new IllegalArgumentException("환불 금액은 양수여야 합니다");
        }
        if (this.refundedAmount + refundAmount > this.pgPaymentAmount) {
            throw new IllegalStateException("누적 환불 금액이 결제 금액을 초과합니다");
        }
        this.refundedAmount += refundAmount;
        this.status = (this.refundedAmount == this.pgPaymentAmount)
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
    }

    public void markRefunded() {
        this.refundedAmount = this.pgPaymentAmount;
        this.status = PaymentStatus.REFUNDED;
    }

    public boolean requiresRefundAccount() {
        return this.paymentMethod == PaymentMethod.BANK_TRANSFER
                || (this.paymentMethod == PaymentMethod.VIRTUAL_ACCOUNT
                    && this.status != PaymentStatus.PENDING);
    }

    public boolean isCancellableBeforeDeposit() {
        return this.paymentMethod == PaymentMethod.VIRTUAL_ACCOUNT
                && this.status == PaymentStatus.PENDING;
    }

    public void cancelBeforeDeposit() {
        if (!isCancellableBeforeDeposit()) {
            throw new IllegalStateException("입금 전 취소가 불가능한 상태입니다");
        }
        this.status = PaymentStatus.CANCELLED;
    }

    public void setRefundAccountInfo(String bankCode, String accountNumber, String holder) {
        this.refundBankCode = bankCode;
        this.refundAccountNumber = accountNumber;
        this.refundAccountHolder = holder;
    }
}
