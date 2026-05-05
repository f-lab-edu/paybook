package com.paybook.order.controller;

import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.ExchangeItemRequest;
import com.paybook.order.dto.OrderResponse;
import com.paybook.order.entity.OrderStatus;
import com.paybook.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @RequestParam String userId,
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrdersByUserId(userId, status, pageable));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId));
    }

    @PatchMapping("/{orderId}/confirm")
    public ResponseEntity<OrderResponse> confirmOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.confirmOrder(orderId));
    }

    @PatchMapping("/{orderId}/payment-failed")
    public ResponseEntity<OrderResponse> markPaymentFailed(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.markPaymentFailed(orderId));
    }

    @PatchMapping("/{orderId}/ship")
    public ResponseEntity<OrderResponse> startShipping(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.startShipping(orderId));
    }

    @PatchMapping("/{orderId}/deliver")
    public ResponseEntity<OrderResponse> markDelivered(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.markDelivered(orderId));
    }

    @PatchMapping("/{orderId}/confirm-purchase")
    public ResponseEntity<OrderResponse> confirmPurchase(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.confirmPurchase(orderId));
    }

    @PatchMapping("/{orderId}/return-request")
    public ResponseEntity<OrderResponse> requestReturn(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.requestReturn(orderId));
    }

    @PatchMapping("/{orderId}/return-complete")
    public ResponseEntity<OrderResponse> completeReturn(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.completeReturn(orderId));
    }

    @PatchMapping("/{orderId}/items/{productId}/cancel")
    public ResponseEntity<OrderResponse> cancelItem(
            @PathVariable String orderId, @PathVariable String productId) {
        return ResponseEntity.ok(orderService.cancelItem(orderId, productId));
    }

    @PostMapping("/{orderId}/exchange")
    public ResponseEntity<OrderResponse> requestExchange(
            @PathVariable String orderId,
            @Valid @RequestBody ExchangeItemRequest request) {
        return ResponseEntity.ok(orderService.requestExchange(request));
    }

    @PatchMapping("/{orderId}/items/{productId}/exchange-complete")
    public ResponseEntity<OrderResponse> completeExchange(
            @PathVariable String orderId, @PathVariable String productId) {
        return ResponseEntity.ok(orderService.completeExchange(orderId, productId));
    }
}
