package com.paybook.order.controller;

import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.ErrorResponse;
import com.paybook.order.dto.OrderResponse;
import com.paybook.order.dto.OrderResponse.OrderItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 주문 API 스텁 컨트롤러
 *
 * 실제 서비스 로직 없이 API 계약(Contract)만 검증하기 위한 더미 컨트롤러.
 * 요청/응답 형식(스키마)과 Bean Validation만 동작하며,
 * 비즈니스 에러 응답은 매직 값으로 시뮬레이션한다.
 *
 * 매직 값 규칙:
 * - quantity 999999        → OUT_OF_STOCK (409)
 * - couponId "USED"        → COUPON_ALREADY_USED (409)
 * - couponId "EXPIRED"     → COUPON_EXPIRED (409)
 * - couponId "INVALID"     → COUPON_NOT_FOUND (404)
 * - pointAmountToUse 999999 → POINTS_UNAVAILABLE (409)
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final Map<String, OrderResponse> stubStore = new ConcurrentHashMap<>();
    private long sequence = 1;

    private static final int STUB_PRICE = 10000;

    /**
     * 주문 생성 API
     * POST /api/orders
     */
    @PostMapping
    public ResponseEntity<?> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        // ── 비즈니스 에러 시뮬레이션 (매직 값 기반) ──

        // 재고 부족
        boolean outOfStock = request.items().stream()
                .anyMatch(item -> item.quantity() >= 999999);
        if (outOfStock) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("OUT_OF_STOCK", "재고가 부족합니다"));
        }

        // 쿠폰 검증
        if (request.couponId() != null) {
            switch (request.couponId()) {
                case "USED" -> {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(new ErrorResponse("COUPON_ALREADY_USED", "이미 사용된 쿠폰입니다"));
                }
                case "EXPIRED" -> {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(new ErrorResponse("COUPON_EXPIRED", "만료된 쿠폰입니다"));
                }
                case "INVALID" -> {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ErrorResponse("COUPON_NOT_FOUND", "존재하지 않는 쿠폰입니다"));
                }
                default -> { /* 유효한 쿠폰 → 정상 진행 */ }
            }
        }

        // 포인트 부족
        if (request.pointAmountToUse() != null && request.pointAmountToUse() >= 999999) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("POINTS_UNAVAILABLE", "포인트가 부족합니다"));
        }

        // ── 정상 주문 생성 ──
        String orderId = "ORD-" + String.format("%06d", sequence++);

        var responseItems = request.items().stream()
                .map(item -> new OrderItemResponse(
                        item.productId(),
                        item.quantity(),
                        STUB_PRICE
                ))
                .toList();

        int totalAmount = responseItems.stream()
                .mapToInt(item -> item.price() * item.quantity())
                .sum();

        OrderResponse response = new OrderResponse(
                orderId,
                request.userId(),
                responseItems,
                totalAmount,
                "PENDING",
                LocalDateTime.now().toString()
        );

        stubStore.put(orderId, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 주문 단건 조회 API
     * GET /api/orders/{orderId}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable String orderId) {

        OrderResponse order = stubStore.get(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(
                            "ORDER_NOT_FOUND",
                            "주문을 찾을 수 없습니다: " + orderId
                    ));
        }
        return ResponseEntity.ok(order);
    }

    /**
     * 주문 취소 API
     * PATCH /api/orders/{orderId}/cancel
     */
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable String orderId) {

        OrderResponse order = stubStore.get(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(
                            "ORDER_NOT_FOUND",
                            "주문을 찾을 수 없습니다: " + orderId
                    ));
        }

        if ("CANCELLED".equals(order.status())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "ORDER_ALREADY_CANCELLED",
                            "이미 취소된 주문입니다: " + orderId
                    ));
        }

        OrderResponse cancelled = new OrderResponse(
                order.orderId(),
                order.userId(),
                order.items(),
                order.totalAmount(),
                "CANCELLED",
                order.createdAt()
        );
        stubStore.put(orderId, cancelled);

        return ResponseEntity.ok(cancelled);
    }
}
