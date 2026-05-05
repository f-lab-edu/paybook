package com.paybook.payment.client;

public interface PgClient {

    PgPaymentResult requestPayment(String paymentId, int amount);

    PgRefundResult requestRefund(String pgTransactionId, int amount);

    PgRefundResult requestRefundWithAccount(String pgTransactionId, int amount,
                                            String bankCode, String accountNumber, String accountHolder);

    PgRefundResult cancelVirtualAccount(String pgTransactionId);

    record PgPaymentResult(boolean success, String pgTransactionId, String failureReason) {}

    record PgRefundResult(boolean success, String pgRefundId, String failureReason) {}
}
