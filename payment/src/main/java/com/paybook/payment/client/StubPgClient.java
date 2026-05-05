package com.paybook.payment.client;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("!prod")
public class StubPgClient implements PgClient {

    @Override
    public PgPaymentResult requestPayment(String paymentId, int amount) {
        if (amount > 0) {
            return new PgPaymentResult(true, "PG-TXN-" + UUID.randomUUID(), null);
        }
        return new PgPaymentResult(false, null, "Invalid amount");
    }

    @Override
    public PgRefundResult requestRefund(String pgTransactionId, int amount) {
        if (pgTransactionId != null && amount > 0) {
            return new PgRefundResult(true, "PG-REFUND-" + UUID.randomUUID(), null);
        }
        return new PgRefundResult(false, null, "Invalid refund request");
    }

    @Override
    public PgRefundResult requestRefundWithAccount(String pgTransactionId, int amount,
                                                   String bankCode, String accountNumber, String accountHolder) {
        if (pgTransactionId != null && amount > 0 && bankCode != null) {
            return new PgRefundResult(true, "PG-REFUND-ACCT-" + UUID.randomUUID(), null);
        }
        return new PgRefundResult(false, null, "Invalid refund with account request");
    }

    @Override
    public PgRefundResult cancelVirtualAccount(String pgTransactionId) {
        if (pgTransactionId != null) {
            return new PgRefundResult(true, "PG-CANCEL-VA-" + UUID.randomUUID(), null);
        }
        return new PgRefundResult(false, null, "Invalid virtual account cancel request");
    }
}
