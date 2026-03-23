package com.paybook.settlement.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SettlementException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    private SettlementException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static SettlementException settlementNotFound(String id) {
        return new SettlementException("SETTLEMENT_NOT_FOUND",
                "정산 내역을 찾을 수 없습니다: " + id, HttpStatus.NOT_FOUND);
    }

    public static SettlementException alreadySettled(String orderId) {
        return new SettlementException("ALREADY_SETTLED",
                "이미 정산된 주문입니다: " + orderId, HttpStatus.CONFLICT);
    }

    public static SettlementException alreadyPaid(String settlementId) {
        return new SettlementException("ALREADY_PAID",
                "이미 지급 완료된 정산입니다: " + settlementId, HttpStatus.CONFLICT);
    }

    public static SettlementException alreadyCancelled(String settlementId) {
        return new SettlementException("ALREADY_CANCELLED",
                "이미 취소된 정산입니다: " + settlementId, HttpStatus.CONFLICT);
    }

    public static SettlementException invalidStatusTransition(String settlementId, String from, String to) {
        return new SettlementException("INVALID_STATUS_TRANSITION",
                "정산 상태를 변경할 수 없습니다: " + settlementId + " (" + from + " → " + to + ")",
                HttpStatus.CONFLICT);
    }

    public static SettlementException orderNotPurchaseConfirmed(String orderId) {
        return new SettlementException("ORDER_NOT_PURCHASE_CONFIRMED",
                "구매확정되지 않은 주문입니다: " + orderId, HttpStatus.CONFLICT);
    }

    public static SettlementException orderFetchFailed(String orderId) {
        return new SettlementException("ORDER_FETCH_FAILED",
                "주문 정보 조회에 실패했습니다: " + orderId, HttpStatus.BAD_GATEWAY);
    }
}
