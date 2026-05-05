package com.paybook.payment.service;

import com.paybook.core.entity.PaymentEntity;
import com.paybook.core.entity.PaymentMethod;
import com.paybook.payment.client.OrderServiceClient;
import com.paybook.payment.client.PgClient;
import com.paybook.payment.client.PgClient.PgPaymentResult;
import com.paybook.payment.dto.RefundRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransactionService txService;
    private final PgClient pgClient;
    private final OrderServiceClient orderServiceClient;

    /**
     * 결제 처리 — 트랜잭션을 분리하여 외부 호출 시 DB 커넥션을 점유하지 않음.
     */
    public PaymentEntity processPayment(String orderId, int pgPaymentAmount, PaymentMethod paymentMethod) {
        PaymentEntity payment = txService.createPendingPayment(orderId, pgPaymentAmount, paymentMethod);
        String paymentId = payment.getPaymentId();

        PgPaymentResult result = pgClient.requestPayment(paymentId, pgPaymentAmount);

        if (result.success()) {
            txService.markSuccess(paymentId, result.pgTransactionId());
            orderServiceClient.confirmOrder(orderId);
        } else {
            txService.markFailed(paymentId);
            orderServiceClient.markPaymentFailed(orderId);
        }

        return txService.findByPaymentIdOrThrow(paymentId);
    }

    /**
     * 전체 환불 — 단일 트랜잭션으로 비관적 락 유지.
     */
    public PaymentEntity refundPayment(String orderId) {
        return txService.refundPayment(orderId);
    }

    /**
     * 부분 환불 — 부분 취소 시 사용.
     */
    public PaymentEntity partialRefundPayment(String orderId, int refundAmount) {
        return txService.partialRefundPayment(orderId, refundAmount);
    }

    /**
     * 부분 환불 (환불계좌 포함) — 계좌이체/가상계좌용.
     */
    public PaymentEntity partialRefundPayment(String orderId, RefundRequest request) {
        return txService.partialRefundPayment(orderId, request);
    }
}
