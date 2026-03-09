package com.paybook.payment.controller;

import com.paybook.payment.exception.PaymentException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<Map<String, String>> handlePaymentException(PaymentException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(Map.of("code", ex.getCode(), "message", ex.getMessage()));
    }
}
