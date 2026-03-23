package com.paybook.settlement.dto;

import java.util.List;

public record SettlementResponse(
        String settlementId,
        String orderId,
        String sellerId,
        int orderAmount,
        int commissionRate,
        int commissionAmount,
        int platformCouponAmount,
        int sellerCouponAmount,
        int pointDiscountAmount,
        int deliveryFee,
        int settlementAmount,
        String status,
        String createdAt,
        String confirmedAt,
        String paidAt
) {

    public record SettlementSummaryResponse(
            String sellerId,
            int totalOrderAmount,
            int totalCommissionAmount,
            int totalSettlementAmount,
            int count,
            List<SettlementResponse> settlements
    ) {}
}
