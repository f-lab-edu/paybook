package com.paybook.payment.service;

import com.paybook.core.entity.PaymentEntity;
import com.paybook.core.repository.PaymentRepository;
import com.paybook.payment.client.OrderServiceClient;
import com.paybook.payment.client.PgClient;
import com.paybook.payment.client.PgClient.PgPaymentResult;
import com.paybook.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int PAYMENT_ID_PAD_LENGTH = 6;

    private final AtomicLong sequence = new AtomicLong(1);

    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;
    private final OrderServiceClient orderServiceClient;

    @Transactional
    public PaymentEntity processPayment(String orderId, int pgPaymentAmount) {
        String paymentId = "PAY-" + String.format("%0" + PAYMENT_ID_PAD_LENGTH + "d", sequence.getAndIncrement());

        PaymentEntity payment = new PaymentEntity(paymentId, orderId, pgPaymentAmount);
        paymentRepository.save(payment);

        PgPaymentResult result = pgClient.requestPayment(paymentId, pgPaymentAmount);

        if (result.success()) {
            payment.markSuccess(result.pgTransactionId());
            orderServiceClient.confirmOrder(orderId);
        } else {
            payment.markFailed();
            orderServiceClient.markPaymentFailed(orderId);
        }

        return payment;
    }

    @Transactional
    public PaymentEntity refundPayment(String orderId) {
        PaymentEntity payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> PaymentException.paymentNotFound(orderId));

        if (!payment.isRefundable()) {
            throw PaymentException.notRefundable(payment.getStatus().name());
        }

        PgClient.PgRefundResult result = pgClient.requestRefund(
                payment.getPgTransactionId(), payment.getPgPaymentAmount());

        if (result.success()) {
            payment.markRefunded();
        } else {
            throw PaymentException.pgRefundFailed(result.failureReason());
        }

        return payment;
    }
}
