package com.paybook.payment.controller;

import com.paybook.core.entity.PaymentEntity;
import com.paybook.core.entity.PaymentMethod;
import com.paybook.payment.dto.RefundRequest;
import com.paybook.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final String ORDER_ID = "orderId";

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> request) {
        String orderId = (String) request.get(ORDER_ID);
        int pgPaymentAmount = (int) request.get("pgPaymentAmount");
        PaymentMethod paymentMethod = PaymentMethod.valueOf((String) request.get("paymentMethod"));

        PaymentEntity payment = paymentService.processPayment(orderId, pgPaymentAmount, paymentMethod);

        return ResponseEntity.status(HttpStatus.OK).body(Map.of(
                "paymentId", payment.getPaymentId(),
                ORDER_ID, payment.getOrderId(),
                "status", payment.getStatus().name(),
                "pgTransactionId", payment.getPgTransactionId() != null ? payment.getPgTransactionId() : ""
        ));
    }

    @PostMapping("/{orderId}/refund")
    public ResponseEntity<Map<String, Object>> refundPayment(@PathVariable String orderId) {
        PaymentEntity payment = paymentService.refundPayment(orderId);

        return ResponseEntity.ok(Map.of(
                "paymentId", payment.getPaymentId(),
                ORDER_ID, payment.getOrderId(),
                "status", payment.getStatus().name()
        ));
    }

    @PostMapping("/{orderId}/partial-refund")
    public ResponseEntity<Map<String, Object>> partialRefundPayment(
            @PathVariable String orderId, @RequestBody RefundRequest request) {
        PaymentEntity payment = paymentService.partialRefundPayment(orderId, request);

        return ResponseEntity.ok(Map.of(
                "paymentId", payment.getPaymentId(),
                ORDER_ID, payment.getOrderId(),
                "status", payment.getStatus().name(),
                "refundedAmount", payment.getRefundedAmount(),
                "refundableAmount", payment.getRefundableAmount()
        ));
    }
}
