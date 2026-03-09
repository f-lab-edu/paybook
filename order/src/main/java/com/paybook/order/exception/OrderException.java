package com.paybook.order.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OrderException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    private OrderException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static OrderException outOfStock() {
        return new OrderException("OUT_OF_STOCK", "재고가 부족합니다", HttpStatus.CONFLICT);
    }

    public static OrderException couponAlreadyUsed() {
        return new OrderException("COUPON_ALREADY_USED", "이미 사용된 쿠폰입니다", HttpStatus.CONFLICT);
    }

    public static OrderException couponExpired() {
        return new OrderException("COUPON_EXPIRED", "만료된 쿠폰입니다", HttpStatus.CONFLICT);
    }

    public static OrderException couponNotFound() {
        return new OrderException("COUPON_NOT_FOUND", "존재하지 않는 쿠폰입니다", HttpStatus.NOT_FOUND);
    }

    public static OrderException pointsUnavailable() {
        return new OrderException("POINTS_UNAVAILABLE", "포인트가 부족합니다", HttpStatus.CONFLICT);
    }

    public static OrderException productNotFound(String productId) {
        return new OrderException("PRODUCT_NOT_FOUND", "존재하지 않는 상품입니다: " + productId, HttpStatus.NOT_FOUND);
    }

    public static OrderException orderNotFound(String orderId) {
        return new OrderException("ORDER_NOT_FOUND", "주문을 찾을 수 없습니다: " + orderId, HttpStatus.NOT_FOUND);
    }

    public static OrderException orderAlreadyCancelled(String orderId) {
        return new OrderException("ORDER_ALREADY_CANCELLED", "이미 취소된 주문입니다: " + orderId, HttpStatus.CONFLICT);
    }

    public static OrderException discountLimitExceeded() {
        return new OrderException("DISCOUNT_LIMIT_EXCEEDED", "할인 한도를 초과했습니다", HttpStatus.CONFLICT);
    }

    public static OrderException orderNotCancellable(String orderId) {
        return new OrderException("ORDER_NOT_CANCELLABLE", "취소할 수 없는 주문 상태입니다: " + orderId, HttpStatus.CONFLICT);
    }

    public static OrderException pgPaymentBelowMinimum() {
        return new OrderException("PG_PAYMENT_BELOW_MINIMUM", "PG 결제 금액이 최소 비율 미만입니다", HttpStatus.CONFLICT);
    }

    public static OrderException couponMinOrderAmountNotMet(int minOrderAmount) {
        return new OrderException("COUPON_MIN_ORDER_NOT_MET",
                "쿠폰 최소 주문금액(" + minOrderAmount + "원)을 충족하지 못합니다", HttpStatus.CONFLICT);
    }

    public static OrderException invalidStatusTransition(String orderId, String from, String to) {
        return new OrderException("INVALID_STATUS_TRANSITION",
                "주문 상태를 변경할 수 없습니다: " + orderId + " (" + from + " → " + to + ")", HttpStatus.CONFLICT);
    }
}
