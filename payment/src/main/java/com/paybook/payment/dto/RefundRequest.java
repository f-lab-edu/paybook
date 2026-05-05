package com.paybook.payment.dto;

public record RefundRequest(
        int refundAmount,
        String refundBankCode,
        String refundAccountNumber,
        String refundAccountHolder
) {}
