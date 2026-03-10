package com.paybook.payment.client;

public interface PgClient {

    PgPaymentResult requestPayment(String paymentId, int amount);

    PgRefundResult requestRefund(String pgTransactionId, int amount);

    record PgPaymentResult(boolean success, String pgTransactionId, String failureReason) {}

    record PgRefundResult(boolean success, String pgRefundId, String failureReason) {}
}
