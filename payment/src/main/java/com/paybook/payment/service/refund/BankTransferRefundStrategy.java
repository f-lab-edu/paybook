package com.paybook.payment.service.refund;

import com.paybook.core.entity.PaymentEntity;
import com.paybook.core.entity.PaymentMethod;
import com.paybook.payment.client.PgClient;
import org.springframework.stereotype.Component;

@Component
public class BankTransferRefundStrategy implements RefundStrategy {

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.BANK_TRANSFER;
    }

    @Override
    public PgClient.PgRefundResult refund(PaymentEntity payment, int refundAmount, PgClient pgClient) {
        validateRefundAccount(payment);
        return pgClient.requestRefundWithAccount(
                payment.getPgTransactionId(), refundAmount,
                payment.getRefundBankCode(), payment.getRefundAccountNumber(),
                payment.getRefundAccountHolder());
    }

    private void validateRefundAccount(PaymentEntity payment) {
        if (payment.getRefundBankCode() == null || payment.getRefundAccountNumber() == null) {
            throw new IllegalStateException("계좌이체 환불에는 환불 계좌 정보가 필요합니다");
        }
    }
}
