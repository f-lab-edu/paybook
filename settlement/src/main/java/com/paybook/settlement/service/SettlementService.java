package com.paybook.settlement.service;

import com.paybook.core.entity.CouponEntity;
import com.paybook.core.entity.CouponType;
import com.paybook.core.repository.CouponRepository;
import com.paybook.settlement.client.OrderServiceClient;
import com.paybook.settlement.client.OrderServiceClient.OrderData;
import com.paybook.settlement.config.SettlementPolicyConfig;
import com.paybook.settlement.dto.SettlementResponse;
import com.paybook.settlement.dto.SettlementResponse.SettlementSummaryResponse;
import com.paybook.settlement.entity.SettlementEntity;
import com.paybook.settlement.entity.SettlementStatus;
import com.paybook.settlement.exception.SettlementException;
import com.paybook.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final String PURCHASE_CONFIRMED = "PURCHASE_CONFIRMED";
    private static final String DEFAULT_SELLER_ID = "SELLER-DEFAULT";
    private static final int BATCH_SIZE = 500;

    private final SettlementRepository settlementRepository;
    private final CouponRepository couponRepository;
    private final OrderServiceClient orderServiceClient;
    private final SettlementPolicyConfig policyConfig;

    @Lazy
    @Autowired
    private SettlementService self;

    /**
     * 정산 생성 — 외부 HTTP 조회를 트랜잭션 밖에서 수행.
     *
     * 1) Order 서비스에서 주문 정보 조회 (트랜잭션 밖 — 네트워크 지연이 DB에 영향 없음)
     * 2) 정산 엔티티 생성 및 저장 (트랜잭션 안 — DB 작업만)
     */
    public SettlementResponse createSettlement(String orderId) {
        validateNotAlreadySettled(orderId);

        OrderData orderData = orderServiceClient.getOrder(orderId);
        validatePurchaseConfirmed(orderData);

        return self.saveSettlement(orderId, orderData);
    }

    @Transactional
    public SettlementResponse saveSettlement(String orderId, OrderData orderData) {
        int couponDiscount = orderData.couponDiscountAmount();
        CouponAmounts couponAmounts = splitCouponAmounts(orderData.couponId(), couponDiscount);

        String settlementId = generateSettlementId();
        SettlementEntity settlement = SettlementEntity.create(
                settlementId,
                orderId,
                resolveSellerId(orderData),
                orderData.totalAmount(),
                policyConfig.defaultCommissionRate(),
                couponAmounts.platformAmount(),
                couponAmounts.sellerAmount(),
                orderData.pointDiscountAmount(),
                orderData.deliveryFee());

        try {
            settlementRepository.save(settlement);
        } catch (DataIntegrityViolationException e) {
            throw SettlementException.alreadySettled(orderId);
        }

        return toResponse(settlement);
    }

    @Transactional(readOnly = true)
    public SettlementResponse getSettlement(String settlementId) {
        SettlementEntity settlement = findSettlementOrThrow(settlementId);
        return toResponse(settlement);
    }

    @Transactional(readOnly = true)
    public SettlementResponse getSettlementByOrderId(String orderId) {
        SettlementEntity settlement = settlementRepository.findByOrderId(orderId)
                .orElseThrow(() -> SettlementException.settlementNotFound(orderId));
        return toResponse(settlement);
    }

    @Transactional(readOnly = true)
    public Page<SettlementResponse> getSettlementsBySellerId(String sellerId, SettlementStatus status,
                                                              Pageable pageable) {
        Page<SettlementEntity> settlements = (status != null)
                ? settlementRepository.findBySellerIdAndStatusOrderByCreatedAtDesc(sellerId, status, pageable)
                : settlementRepository.findBySellerIdOrderByCreatedAtDesc(sellerId, pageable);
        return settlements.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SettlementSummaryResponse getSellerSummary(String sellerId, SettlementStatus status) {
        SettlementRepository.SettlementSummaryProjection summary = (status != null)
                ? settlementRepository.getSellerSummaryByStatus(sellerId, status)
                : settlementRepository.getSellerSummary(sellerId);

        Page<SettlementEntity> page = (status != null)
                ? settlementRepository.findBySellerIdAndStatusOrderByCreatedAtDesc(
                        sellerId, status, PageRequest.of(0, 100))
                : settlementRepository.findBySellerIdOrderByCreatedAtDesc(
                        sellerId, PageRequest.of(0, 100));

        return new SettlementSummaryResponse(
                sellerId,
                summary != null ? summary.getTotalOrderAmount() : 0,
                summary != null ? summary.getTotalCommissionAmount() : 0,
                summary != null ? summary.getTotalSettlementAmount() : 0,
                summary != null ? summary.getCount().intValue() : 0,
                page.getContent().stream().map(this::toResponse).toList());
    }

    @Transactional
    public SettlementResponse confirmSettlement(String settlementId) {
        SettlementEntity settlement = findSettlementOrThrow(settlementId);
        settlement.confirm();
        return toResponse(settlement);
    }

    @Transactional
    public List<SettlementResponse> confirmAllPending() {
        List<SettlementResponse> results = new ArrayList<>();
        Page<SettlementEntity> page;

        do {
            page = settlementRepository.findByStatusForUpdate(
                    SettlementStatus.PENDING, PageRequest.of(0, BATCH_SIZE));

            for (SettlementEntity settlement : page.getContent()) {
                settlement.confirm();
                results.add(toResponse(settlement));
            }
        } while (page.hasNext());

        return results;
    }

    @Transactional
    public SettlementResponse markPaid(String settlementId) {
        SettlementEntity settlement = findSettlementOrThrow(settlementId);
        settlement.markPaid();
        return toResponse(settlement);
    }

    @Transactional
    public List<SettlementResponse> payAllConfirmed() {
        List<SettlementResponse> results = new ArrayList<>();
        Page<SettlementEntity> page;

        do {
            page = settlementRepository.findByStatusForUpdate(
                    SettlementStatus.CONFIRMED, PageRequest.of(0, BATCH_SIZE));

            for (SettlementEntity settlement : page.getContent()) {
                settlement.markPaid();
                results.add(toResponse(settlement));
            }
        } while (page.hasNext());

        return results;
    }

    @Transactional
    public SettlementResponse cancelSettlement(String settlementId) {
        SettlementEntity settlement = findSettlementOrThrow(settlementId);
        settlement.cancel();
        return toResponse(settlement);
    }

    @Transactional
    public SettlementResponse cancelSettlementByOrderId(String orderId) {
        SettlementEntity settlement = settlementRepository.findByOrderId(orderId)
                .orElseThrow(() -> SettlementException.settlementNotFound(orderId));
        settlement.cancel();
        return toResponse(settlement);
    }

    // ── 내부 메서드 ──

    private void validateNotAlreadySettled(String orderId) {
        settlementRepository.findByOrderId(orderId)
                .ifPresent(s -> {
                    throw SettlementException.alreadySettled(orderId);
                });
    }

    private void validatePurchaseConfirmed(OrderData orderData) {
        if (!PURCHASE_CONFIRMED.equals(orderData.status())) {
            throw SettlementException.orderNotPurchaseConfirmed(orderData.orderId());
        }
    }

    private CouponAmounts splitCouponAmounts(String couponId, int totalCouponDiscount) {
        if (totalCouponDiscount <= 0 || couponId == null) {
            return new CouponAmounts(0, 0);
        }

        CouponType couponType = couponRepository.findByCouponId(couponId)
                .map(CouponEntity::getCouponType)
                .orElse(CouponType.PLATFORM);

        return (couponType == CouponType.SELLER)
                ? new CouponAmounts(0, totalCouponDiscount)
                : new CouponAmounts(totalCouponDiscount, 0);
    }

    private String resolveSellerId(OrderData orderData) {
        return orderData.items().stream()
                .findFirst()
                .map(item -> DEFAULT_SELLER_ID)
                .orElse(DEFAULT_SELLER_ID);
    }

    private String generateSettlementId() {
        return "STL-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private SettlementEntity findSettlementOrThrow(String settlementId) {
        return settlementRepository.findBySettlementId(settlementId)
                .orElseThrow(() -> SettlementException.settlementNotFound(settlementId));
    }

    private SettlementResponse toResponse(SettlementEntity settlement) {
        return new SettlementResponse(
                settlement.getSettlementId(),
                settlement.getOrderId(),
                settlement.getSellerId(),
                settlement.getOrderAmount(),
                settlement.getCommissionRate(),
                settlement.getCommissionAmount(),
                settlement.getPlatformCouponAmount(),
                settlement.getSellerCouponAmount(),
                settlement.getPointDiscountAmount(),
                settlement.getDeliveryFee(),
                settlement.getSettlementAmount(),
                settlement.getStatus().name(),
                settlement.getCreatedAt().toString(),
                settlement.getConfirmedAt() != null ? settlement.getConfirmedAt().toString() : null,
                settlement.getPaidAt() != null ? settlement.getPaidAt().toString() : null);
    }

    private record CouponAmounts(int platformAmount, int sellerAmount) {}
}
