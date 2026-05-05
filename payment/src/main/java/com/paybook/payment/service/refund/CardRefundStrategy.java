package com.paybook.payment.service.refund;

import com.paybook.core.entity.PaymentEntity;
import com.paybook.core.entity.PaymentMethod;
import com.paybook.payment.client.PgClient;
import org.springframework.stereotype.Component;

@Component
public class CardRefundStrategy implements RefundStrategy {

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CARD;
    }

    @Override
    public PgClient.PgRefundResult refund(PaymentEntity payment, int refundAmount, PgClient pgClient) {
        return pgClient.requestRefund(payment.getPgTransactionId(), refundAmount);
    }
}
