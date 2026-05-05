package com.paybook.payment.service;

import com.paybook.core.entity.PaymentEntity;
import com.paybook.core.entity.PaymentMethod;
import com.paybook.core.repository.PaymentRepository;
import com.paybook.payment.client.PgClient;
import com.paybook.payment.dto.RefundRequest;
import com.paybook.payment.exception.PaymentException;
import com.paybook.payment.service.refund.RefundStrategyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;
    private final RefundStrategyResolver refundStrategyResolver;

    @Transactional
    public PaymentEntity createPendingPayment(String orderId, int pgPaymentAmount, PaymentMethod paymentMethod) {
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        PaymentEntity payment = new PaymentEntity(paymentId, orderId, pgPaymentAmount, paymentMethod);
        return paymentRepository.save(payment);
    }

    @Transactional
    public void markSuccess(String paymentId, String pgTransactionId) {
        PaymentEntity payment = findByPaymentIdOrThrow(paymentId);
        payment.markSuccess(pgTransactionId);
    }

    @Transactional
    public void markFailed(String paymentId) {
        PaymentEntity payment = findByPaymentIdOrThrow(paymentId);
        payment.markFailed();
    }

    /**
     * 전체 환불 — 단일 트랜잭션으로 비관적 락을 PG 호출 동안 유지.
     * 결제수단별 환불 전략을 사용한다.
     */
    @Transactional
    public PaymentEntity refundPayment(String orderId) {
        PaymentEntity payment = findPaymentForRefund(orderId);

        if (payment.isCancellableBeforeDeposit()) {
            handleVirtualAccountCancel(payment);
            return payment;
        }

        int refundAmount = payment.getRefundableAmount();
        executeRefund(payment, refundAmount);
        payment.markRefunded();
        return payment;
    }

    /**
     * 부분 환불 — 비관적 락으로 이중 환불 방지.
     */
    @Transactional
    public PaymentEntity partialRefundPayment(String orderId, int refundAmount) {
        PaymentEntity payment = findPaymentForRefund(orderId);
        validatePartialRefundAmount(payment, refundAmount);
        executeRefund(payment, refundAmount);
        payment.applyPartialRefund(refundAmount);
        return payment;
    }

    /**
     * 부분 환불 (환불계좌 포함) — 계좌이체/가상계좌용.
     */
    @Transactional
    public PaymentEntity partialRefundPayment(String orderId, RefundRequest request) {
        PaymentEntity payment = findPaymentForRefund(orderId);
        validatePartialRefundAmount(payment, request.refundAmount());

        if (payment.requiresRefundAccount()) {
            payment.setRefundAccountInfo(
                    request.refundBankCode(), request.refundAccountNumber(), request.refundAccountHolder());
        }

        executeRefund(payment, request.refundAmount());
        payment.applyPartialRefund(request.refundAmount());
        return payment;
    }

    @Transactional(readOnly = true)
    public PaymentEntity findByPaymentIdOrThrow(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> PaymentException.paymentNotFound(paymentId));
    }

    // ── 내부 메서드 ──

    private PaymentEntity findPaymentForRefund(String orderId) {
        PaymentEntity payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> PaymentException.paymentNotFound(orderId));

        if (!payment.isRefundable() && !payment.isCancellableBeforeDeposit()) {
            throw PaymentException.notRefundable(payment.getStatus().name());
        }
        return payment;
    }

    private void executeRefund(PaymentEntity payment, int refundAmount) {
        PgClient.PgRefundResult result = refundStrategyResolver
                .resolve(payment.getPaymentMethod())
                .refund(payment, refundAmount, pgClient);

        if (!result.success()) {
            throw PaymentException.pgRefundFailed(result.failureReason());
        }
    }

    private void handleVirtualAccountCancel(PaymentEntity payment) {
        PgClient.PgRefundResult result = pgClient.cancelVirtualAccount(payment.getPgTransactionId());
        if (!result.success()) {
            throw PaymentException.pgRefundFailed(result.failureReason());
        }
        payment.cancelBeforeDeposit();
    }

    private void validatePartialRefundAmount(PaymentEntity payment, int refundAmount) {
        if (refundAmount <= 0) {
            throw new IllegalArgumentException("환불 금액은 양수여야 합니다");
        }
        if (refundAmount > payment.getRefundableAmount()) {
            throw PaymentException.notRefundable("환불 가능 금액 초과: " + payment.getRefundableAmount());
        }
    }
}
