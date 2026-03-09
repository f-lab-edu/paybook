package com.paybook.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PaymentException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    private PaymentException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static PaymentException paymentNotFound(String orderId) {
        return new PaymentException("PAYMENT_NOT_FOUND",
                "결제 내역을 찾을 수 없습니다: " + orderId, HttpStatus.NOT_FOUND);
    }

    public static PaymentException notRefundable(String status) {
        return new PaymentException("NOT_REFUNDABLE",
                "환불 가능한 상태가 아닙니다: " + status, HttpStatus.CONFLICT);
    }

    public static PaymentException pgRefundFailed(String reason) {
        return new PaymentException("PG_REFUND_FAILED",
                "PG 환불 요청 실패: " + reason, HttpStatus.BAD_GATEWAY);
    }
}
