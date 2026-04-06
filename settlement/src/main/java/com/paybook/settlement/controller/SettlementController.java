package com.paybook.settlement.controller;

import com.paybook.settlement.dto.SettlementResponse;
import com.paybook.settlement.dto.SettlementResponse.SettlementSummaryResponse;
import com.paybook.settlement.entity.SettlementStatus;
import com.paybook.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping
    public ResponseEntity<SettlementResponse> createSettlement(@RequestBody Map<String, String> request) {
        String orderId = request.get("orderId");
        SettlementResponse response = settlementService.createSettlement(orderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{settlementId}")
    public ResponseEntity<SettlementResponse> getSettlement(@PathVariable String settlementId) {
        return ResponseEntity.ok(settlementService.getSettlement(settlementId));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<SettlementResponse> getSettlementByOrderId(@PathVariable String orderId) {
        return ResponseEntity.ok(settlementService.getSettlementByOrderId(orderId));
    }

    @GetMapping
    public ResponseEntity<Page<SettlementResponse>> getSettlementsBySellerId(
            @RequestParam String sellerId,
            @RequestParam(required = false) SettlementStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(settlementService.getSettlementsBySellerId(sellerId, status, pageable));
    }

    @GetMapping("/seller/{sellerId}/summary")
    public ResponseEntity<SettlementSummaryResponse> getSellerSummary(
            @PathVariable String sellerId,
            @RequestParam(required = false) SettlementStatus status) {
        return ResponseEntity.ok(settlementService.getSellerSummary(sellerId, status));
    }

    @PatchMapping("/{settlementId}/confirm")
    public ResponseEntity<SettlementResponse> confirmSettlement(@PathVariable String settlementId) {
        return ResponseEntity.ok(settlementService.confirmSettlement(settlementId));
    }

    @PatchMapping("/confirm-all")
    public ResponseEntity<List<SettlementResponse>> confirmAllPending() {
        return ResponseEntity.ok(settlementService.confirmAllPending());
    }

    @PatchMapping("/{settlementId}/pay")
    public ResponseEntity<SettlementResponse> markPaid(@PathVariable String settlementId) {
        return ResponseEntity.ok(settlementService.markPaid(settlementId));
    }

    @PatchMapping("/pay-all")
    public ResponseEntity<List<SettlementResponse>> payAllConfirmed() {
        return ResponseEntity.ok(settlementService.payAllConfirmed());
    }

    @PatchMapping("/{settlementId}/cancel")
    public ResponseEntity<SettlementResponse> cancelSettlement(@PathVariable String settlementId) {
        return ResponseEntity.ok(settlementService.cancelSettlement(settlementId));
    }

    @PatchMapping("/order/{orderId}/cancel")
    public ResponseEntity<SettlementResponse> cancelSettlementByOrderId(@PathVariable String orderId) {
        return ResponseEntity.ok(settlementService.cancelSettlementByOrderId(orderId));
    }
}
