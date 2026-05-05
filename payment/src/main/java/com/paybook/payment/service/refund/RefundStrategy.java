package com.paybook.payment.service.refund;

import com.paybook.core.entity.PaymentEntity;
import com.paybook.core.entity.PaymentMethod;
import com.paybook.payment.client.PgClient;

public interface RefundStrategy {

    boolean supports(PaymentMethod method);

    PgClient.PgRefundResult refund(PaymentEntity payment, int refundAmount, PgClient pgClient);
}
