package com.paybook.payment.service.refund;

import com.paybook.core.entity.PaymentEntity;
import com.paybook.core.entity.PaymentMethod;
import com.paybook.core.entity.PaymentStatus;
import com.paybook.payment.client.PgClient;
import org.springframework.stereotype.Component;

@Component
public class VirtualAccountRefundStrategy implements RefundStrategy {

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.VIRTUAL_ACCOUNT;
    }

    @Override
    public PgClient.PgRefundResult refund(PaymentEntity payment, int refundAmount, PgClient pgClient) {
        if (payment.getStatus() == PaymentStatus.PENDING) {
            return pgClient.cancelVirtualAccount(payment.getPgTransactionId());
        }

        validateRefundAccount(payment);
        return pgClient.requestRefundWithAccount(
                payment.getPgTransactionId(), refundAmount,
                payment.getRefundBankCode(), payment.getRefundAccountNumber(),
                payment.getRefundAccountHolder());
    }

    private void validateRefundAccount(PaymentEntity payment) {
        if (payment.getRefundBankCode() == null || payment.getRefundAccountNumber() == null) {
            throw new IllegalStateException("가상계좌 입금 후 환불에는 환불 계좌 정보가 필요합니다");
        }
    }
}
