package com.paybook.settlement.service.unit;

import com.paybook.core.entity.*;
import com.paybook.core.repository.CouponRepository;
import com.paybook.settlement.client.OrderServiceClient;
import com.paybook.settlement.client.OrderServiceClient.OrderData;
import com.paybook.settlement.client.OrderServiceClient.OrderItemData;
import com.paybook.settlement.config.SettlementPolicyConfig;
import com.paybook.settlement.dto.SettlementResponse;
import com.paybook.settlement.dto.SettlementResponse.SettlementSummaryResponse;
import com.paybook.settlement.entity.SettlementEntity;
import com.paybook.settlement.entity.SettlementStatus;
import com.paybook.settlement.exception.SettlementException;
import com.paybook.settlement.repository.SettlementRepository;
import com.paybook.settlement.repository.SettlementRepository.SettlementSummaryProjection;
import com.paybook.settlement.service.SettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * SettlementService 순수 유닛 테스트 (런던 학파).
 *
 * Spring 컨텍스트 없이, 모든 의존성을 Mock으로 대체하여
 * SettlementService의 모든 분기 로직을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementService 유닛 테스트")
class SettlementServiceUnitTest {

    @InjectMocks
    private SettlementService settlementService;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private SettlementPolicyConfig policyConfig;

    // ════════════════════════════════════════
    // createSettlement — 검증 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("createSettlement: 검증")
    class CreateSettlement_Validation {

        @Test
        @DisplayName("이미 정산된 주문 → ALREADY_SETTLED, 주문 조회까지 가지 않는다")
        void alreadySettled_stopsEarly() {
            given(settlementRepository.findByOrderId("ORD-001"))
                    .willReturn(Optional.of(settlement("ORD-001")));

            assertException("ORD-001", "ALREADY_SETTLED");

            verify(orderServiceClient, never()).getOrder(anyString());
            verify(settlementRepository, never()).save(any());
        }

        @Test
        @DisplayName("구매확정 아닌 주문 → ORDER_NOT_PURCHASE_CONFIRMED, 저장까지 가지 않는다")
        void notPurchaseConfirmed_stopsBeforeSave() {
            given(settlementRepository.findByOrderId("ORD-001")).willReturn(Optional.empty());
            given(orderServiceClient.getOrder("ORD-001"))
                    .willReturn(orderData("ORD-001", "CONFIRMED"));

            assertException("ORD-001", "ORDER_NOT_PURCHASE_CONFIRMED");

            verify(settlementRepository, never()).save(any());
        }

        @Test
        @DisplayName("DB unique 제약 위반 → ALREADY_SETTLED (동시성 안전망)")
        void uniqueViolation_throwsAlreadySettled() {
            given(settlementRepository.findByOrderId("ORD-001")).willReturn(Optional.empty());
            given(orderServiceClient.getOrder("ORD-001"))
                    .willReturn(orderData("ORD-001", "PURCHASE_CONFIRMED"));
            givenDefaultPolicy();
            given(settlementRepository.save(any()))
                    .willThrow(new DataIntegrityViolationException("unique violation"));

            assertException("ORD-001", "ALREADY_SETTLED");
        }
    }

    // ════════════════════════════════════════
    // createSettlement — 쿠폰 분배 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("createSettlement: 쿠폰 부담금 분배")
    class CreateSettlement_CouponSplit {

        @Test
        @DisplayName("쿠폰 null → 쿠폰 조회 자체를 하지 않고 부담금 0")
        void nullCoupon_skipsCouponLookup() {
            givenCreateSettlementSetup("ORD-001", 50000, 0, 0, null);

            SettlementResponse response = settlementService.createSettlement("ORD-001");

            assertThat(response.platformCouponAmount()).isZero();
            assertThat(response.sellerCouponAmount()).isZero();
            verify(couponRepository, never()).findByCouponId(anyString());
        }

        @Test
        @DisplayName("쿠폰 할인 0 → 쿠폰 조회 자체를 하지 않는다")
        void zeroCouponDiscount_skipsCouponLookup() {
            givenCreateSettlementSetup("ORD-001", 50000, 0, 0, "COUPON-001");

            settlementService.createSettlement("ORD-001");

            verify(couponRepository, never()).findByCouponId(anyString());
        }

        @Test
        @DisplayName("플랫폼 쿠폰 → platformCouponAmount에 할당, sellerCouponAmount은 0")
        void platformCoupon_assignsToPlatform() {
            givenCreateSettlementSetup("ORD-001", 50000, 1000, 0, "COUPON-P");
            given(couponRepository.findByCouponId("COUPON-P"))
                    .willReturn(Optional.of(coupon("COUPON-P", CouponType.PLATFORM)));

            SettlementResponse response = settlementService.createSettlement("ORD-001");

            assertThat(response.platformCouponAmount()).isEqualTo(1000);
            assertThat(response.sellerCouponAmount()).isZero();
        }

        @Test
        @DisplayName("셀러 쿠폰 → sellerCouponAmount에 할당, platformCouponAmount은 0")
        void sellerCoupon_assignsToSeller() {
            givenCreateSettlementSetup("ORD-001", 50000, 2000, 0, "COUPON-S");
            given(couponRepository.findByCouponId("COUPON-S"))
                    .willReturn(Optional.of(coupon("COUPON-S", CouponType.SELLER)));

            SettlementResponse response = settlementService.createSettlement("ORD-001");

            assertThat(response.platformCouponAmount()).isZero();
            assertThat(response.sellerCouponAmount()).isEqualTo(2000);
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰 → 기본값 PLATFORM으로 처리")
        void unknownCoupon_defaultsToPlatform() {
            givenCreateSettlementSetup("ORD-001", 50000, 1000, 0, "COUPON-GONE");
            given(couponRepository.findByCouponId("COUPON-GONE")).willReturn(Optional.empty());

            SettlementResponse response = settlementService.createSettlement("ORD-001");

            assertThat(response.platformCouponAmount()).isEqualTo(1000);
            assertThat(response.sellerCouponAmount()).isZero();
        }
    }

    // ════════════════════════════════════════
    // createSettlement — 성공 시 계산 검증
    // ════════════════════════════════════════

    @Nested
    @DisplayName("createSettlement: 정산금 계산")
    class CreateSettlement_Calculation {

        @Test
        @DisplayName("수수료 = 주문금액 × 수수료율, 정산금 = 주문금액 - 수수료 + 배송비")
        void calculatesAmountsCorrectly() {
            givenCreateSettlementSetup("ORD-001", 50000, 0, 0, null);

            SettlementResponse response = settlementService.createSettlement("ORD-001");

            assertThat(response.commissionRate()).isEqualTo(30);
            assertThat(response.commissionAmount()).isEqualTo(15000);
            assertThat(response.settlementAmount()).isEqualTo(35000);
        }

        @Test
        @DisplayName("셀러 쿠폰이 있으면 정산금에서 차감된다")
        void sellerCoupon_deductsFromSettlement() {
            givenCreateSettlementSetup("ORD-001", 50000, 2000, 0, "COUPON-S");
            given(couponRepository.findByCouponId("COUPON-S"))
                    .willReturn(Optional.of(coupon("COUPON-S", CouponType.SELLER)));

            SettlementResponse response = settlementService.createSettlement("ORD-001");

            // 50000 - 15000(수수료) - 2000(셀러쿠폰) = 33000
            assertThat(response.settlementAmount()).isEqualTo(33000);
        }

        @Test
        @DisplayName("저장이 호출된다")
        void savesSettlement() {
            givenCreateSettlementSetup("ORD-001", 50000, 0, 0, null);

            settlementService.createSettlement("ORD-001");

            verify(settlementRepository).save(any(SettlementEntity.class));
        }

        @Test
        @DisplayName("상태가 PENDING이다")
        void statusIsPending() {
            givenCreateSettlementSetup("ORD-001", 50000, 0, 0, null);

            SettlementResponse response = settlementService.createSettlement("ORD-001");

            assertThat(response.status()).isEqualTo("PENDING");
        }
    }

    // ════════════════════════════════════════
    // getSettlement
    // ════════════════════════════════════════

    @Nested
    @DisplayName("getSettlement")
    class GetSettlement {

        @Test
        @DisplayName("정산 없음 → SETTLEMENT_NOT_FOUND")
        void notFound() {
            given(settlementRepository.findBySettlementId("STL-NONE")).willReturn(Optional.empty());

            assertThatThrownBy(() -> settlementService.getSettlement("STL-NONE"))
                    .isInstanceOf(SettlementException.class)
                    .satisfies(ex -> assertCode(ex, "SETTLEMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("정산 있음 → 응답 반환")
        void found_returnsResponse() {
            given(settlementRepository.findBySettlementId("STL-001"))
                    .willReturn(Optional.of(settlement("ORD-001")));

            SettlementResponse response = settlementService.getSettlement("STL-001");

            assertThat(response.orderId()).isEqualTo("ORD-001");
        }
    }

    // ════════════════════════════════════════
    // getSettlementByOrderId
    // ════════════════════════════════════════

    @Nested
    @DisplayName("getSettlementByOrderId")
    class GetSettlementByOrderId {

        @Test
        @DisplayName("주문 없음 → SETTLEMENT_NOT_FOUND")
        void notFound() {
            given(settlementRepository.findByOrderId("ORD-NONE")).willReturn(Optional.empty());

            assertThatThrownBy(() -> settlementService.getSettlementByOrderId("ORD-NONE"))
                    .isInstanceOf(SettlementException.class)
                    .satisfies(ex -> assertCode(ex, "SETTLEMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("주문 있음 → 응답 반환")
        void found() {
            given(settlementRepository.findByOrderId("ORD-001"))
                    .willReturn(Optional.of(settlement("ORD-001")));

            SettlementResponse response = settlementService.getSettlementByOrderId("ORD-001");

            assertThat(response.orderId()).isEqualTo("ORD-001");
        }
    }

    // ════════════════════════════════════════
    // getSettlementsBySellerId — 쿼리 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("getSettlementsBySellerId")
    class GetSettlementsBySellerId {

        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("status null → 전체 조회 쿼리 호출, 상태 필터 쿼리 미호출")
        void statusNull_callsFindBySellerId() {
            given(settlementRepository.findBySellerIdOrderByCreatedAtDesc("SELLER-001", pageable))
                    .willReturn(new PageImpl<>(List.of()));

            settlementService.getSettlementsBySellerId("SELLER-001", null, pageable);

            verify(settlementRepository).findBySellerIdOrderByCreatedAtDesc("SELLER-001", pageable);
            verify(settlementRepository, never()).findBySellerIdAndStatusOrderByCreatedAtDesc(
                    anyString(), any(SettlementStatus.class), any(Pageable.class));
        }

        @Test
        @DisplayName("status 지정 → 상태 필터 쿼리 호출, 전체 쿼리 미호출")
        void statusGiven_callsFindBySellerIdAndStatus() {
            given(settlementRepository.findBySellerIdAndStatusOrderByCreatedAtDesc(
                    "SELLER-001", SettlementStatus.PENDING, pageable))
                    .willReturn(new PageImpl<>(List.of()));

            settlementService.getSettlementsBySellerId("SELLER-001", SettlementStatus.PENDING, pageable);

            verify(settlementRepository).findBySellerIdAndStatusOrderByCreatedAtDesc(
                    "SELLER-001", SettlementStatus.PENDING, pageable);
            verify(settlementRepository, never()).findBySellerIdOrderByCreatedAtDesc(
                    anyString(), any(Pageable.class));
        }
    }

    // ════════════════════════════════════════
    // getSellerSummary — null 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("getSellerSummary")
    class GetSellerSummary {

        @Test
        @DisplayName("summary null → 모든 집계값 0 반환")
        void summaryNull_returnsZeros() {
            given(settlementRepository.getSellerSummary("SELLER-001")).willReturn(null);
            given(settlementRepository.findBySellerIdOrderByCreatedAtDesc(eq("SELLER-001"), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            SettlementSummaryResponse result = settlementService.getSellerSummary("SELLER-001", null);

            assertThat(result.totalOrderAmount()).isZero();
            assertThat(result.totalCommissionAmount()).isZero();
            assertThat(result.totalSettlementAmount()).isZero();
            assertThat(result.count()).isZero();
        }

        @Test
        @DisplayName("status 지정 → 상태별 집계 쿼리 호출")
        void statusGiven_callsStatusQuery() {
            SettlementSummaryProjection projection = mock(SettlementSummaryProjection.class);
            given(projection.getTotalOrderAmount()).willReturn(100000);
            given(projection.getTotalCommissionAmount()).willReturn(30000);
            given(projection.getTotalSettlementAmount()).willReturn(70000);
            given(projection.getCount()).willReturn(2L);

            given(settlementRepository.getSellerSummaryByStatus("SELLER-001", SettlementStatus.CONFIRMED))
                    .willReturn(projection);
            given(settlementRepository.findBySellerIdAndStatusOrderByCreatedAtDesc(
                    eq("SELLER-001"), eq(SettlementStatus.CONFIRMED), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            SettlementSummaryResponse result = settlementService.getSellerSummary(
                    "SELLER-001", SettlementStatus.CONFIRMED);

            assertThat(result.totalOrderAmount()).isEqualTo(100000);
            assertThat(result.count()).isEqualTo(2);
            verify(settlementRepository, never()).getSellerSummary(anyString());
        }
    }

    // ════════════════════════════════════════
    // confirmSettlement
    // ════════════════════════════════════════

    @Nested
    @DisplayName("confirmSettlement")
    class ConfirmSettlement {

        @Test
        @DisplayName("정산 없음 → SETTLEMENT_NOT_FOUND")
        void notFound() {
            given(settlementRepository.findBySettlementId("STL-NONE")).willReturn(Optional.empty());

            assertThatThrownBy(() -> settlementService.confirmSettlement("STL-NONE"))
                    .isInstanceOf(SettlementException.class)
                    .satisfies(ex -> assertCode(ex, "SETTLEMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("PENDING → CONFIRMED 성공")
        void success() {
            SettlementEntity entity = settlement("ORD-001");
            given(settlementRepository.findBySettlementId("STL-001")).willReturn(Optional.of(entity));

            SettlementResponse response = settlementService.confirmSettlement("STL-001");

            assertThat(response.status()).isEqualTo("CONFIRMED");
        }
    }

    // ════════════════════════════════════════
    // confirmAllPending — 배치 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("confirmAllPending")
    class ConfirmAllPending {

        @Test
        @DisplayName("PENDING 건이 없으면 빈 리스트 반환")
        void noPending_returnsEmpty() {
            given(settlementRepository.findByStatusOrderByCreatedAtAsc(
                    eq(SettlementStatus.PENDING), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            List<SettlementResponse> results = settlementService.confirmAllPending();

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("OptimisticLockException 발생 시 해당 건만 건너뛰고 계속 처리")
        void optimisticLock_skipsAndContinues() {
            SettlementEntity entity1 = mock(SettlementEntity.class);
            SettlementEntity entity2 = settlement("ORD-002");

            doThrow(new ObjectOptimisticLockingFailureException(SettlementEntity.class, 1L))
                    .when(entity1).confirm();

            given(settlementRepository.findByStatusOrderByCreatedAtAsc(
                    eq(SettlementStatus.PENDING), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(entity1, entity2)));

            List<SettlementResponse> results = settlementService.confirmAllPending();

            assertThat(results).hasSize(1);
        }
    }

    // ════════════════════════════════════════
    // markPaid
    // ════════════════════════════════════════

    @Nested
    @DisplayName("markPaid")
    class MarkPaid {

        @Test
        @DisplayName("정산 없음 → SETTLEMENT_NOT_FOUND")
        void notFound() {
            given(settlementRepository.findBySettlementId("STL-NONE")).willReturn(Optional.empty());

            assertThatThrownBy(() -> settlementService.markPaid("STL-NONE"))
                    .isInstanceOf(SettlementException.class)
                    .satisfies(ex -> assertCode(ex, "SETTLEMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("PENDING → PAID 시도 → INVALID_STATUS_TRANSITION")
        void invalidFromPending() {
            SettlementEntity entity = settlement("ORD-001");
            given(settlementRepository.findBySettlementId("STL-001")).willReturn(Optional.of(entity));

            assertThatThrownBy(() -> settlementService.markPaid("STL-001"))
                    .isInstanceOf(SettlementException.class)
                    .satisfies(ex -> assertCode(ex, "INVALID_STATUS_TRANSITION"));
        }

        @Test
        @DisplayName("CONFIRMED → PAID 성공")
        void success() {
            SettlementEntity entity = settlement("ORD-001");
            entity.confirm();
            given(settlementRepository.findBySettlementId("STL-001")).willReturn(Optional.of(entity));

            SettlementResponse response = settlementService.markPaid("STL-001");

            assertThat(response.status()).isEqualTo("PAID");
        }
    }

    // ════════════════════════════════════════
    // payAllConfirmed — 배치 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("payAllConfirmed")
    class PayAllConfirmed {

        @Test
        @DisplayName("CONFIRMED 건이 없으면 빈 리스트 반환")
        void noConfirmed_returnsEmpty() {
            given(settlementRepository.findByStatusOrderByCreatedAtAsc(
                    eq(SettlementStatus.CONFIRMED), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            List<SettlementResponse> results = settlementService.payAllConfirmed();

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("OptimisticLockException 발생 시 해당 건만 건너뛰고 계속 처리")
        void optimisticLock_skipsAndContinues() {
            SettlementEntity entity1 = mock(SettlementEntity.class);
            SettlementEntity entity2 = settlement("ORD-002");
            entity2.confirm();

            doThrow(new ObjectOptimisticLockingFailureException(SettlementEntity.class, 1L))
                    .when(entity1).markPaid();

            given(settlementRepository.findByStatusOrderByCreatedAtAsc(
                    eq(SettlementStatus.CONFIRMED), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(entity1, entity2)));

            List<SettlementResponse> results = settlementService.payAllConfirmed();

            assertThat(results).hasSize(1);
        }
    }

    // ════════════════════════════════════════
    // cancelSettlement
    // ════════════════════════════════════════

    @Nested
    @DisplayName("cancelSettlement")
    class CancelSettlement {

        @Test
        @DisplayName("정산 없음 → SETTLEMENT_NOT_FOUND")
        void notFound() {
            given(settlementRepository.findBySettlementId("STL-NONE")).willReturn(Optional.empty());

            assertThatThrownBy(() -> settlementService.cancelSettlement("STL-NONE"))
                    .isInstanceOf(SettlementException.class)
                    .satisfies(ex -> assertCode(ex, "SETTLEMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("PENDING → CANCELLED 성공")
        void cancelFromPending() {
            SettlementEntity entity = settlement("ORD-001");
            given(settlementRepository.findBySettlementId("STL-001")).willReturn(Optional.of(entity));

            SettlementResponse response = settlementService.cancelSettlement("STL-001");

            assertThat(response.status()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("CONFIRMED → CANCELLED 성공")
        void cancelFromConfirmed() {
            SettlementEntity entity = settlement("ORD-001");
            entity.confirm();
            given(settlementRepository.findBySettlementId("STL-001")).willReturn(Optional.of(entity));

            SettlementResponse response = settlementService.cancelSettlement("STL-001");

            assertThat(response.status()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("PAID → 취소 시도 → ALREADY_PAID")
        void cancelFromPaid_throws() {
            SettlementEntity entity = settlement("ORD-001");
            entity.confirm();
            entity.markPaid();
            given(settlementRepository.findBySettlementId("STL-001")).willReturn(Optional.of(entity));

            assertThatThrownBy(() -> settlementService.cancelSettlement("STL-001"))
                    .isInstanceOf(SettlementException.class)
                    .satisfies(ex -> assertCode(ex, "ALREADY_PAID"));
        }

        @Test
        @DisplayName("CANCELLED → 중복 취소 → ALREADY_CANCELLED")
        void cancelFromCancelled_throws() {
            SettlementEntity entity = settlement("ORD-001");
            entity.cancel();
            given(settlementRepository.findBySettlementId("STL-001")).willReturn(Optional.of(entity));

            assertThatThrownBy(() -> settlementService.cancelSettlement("STL-001"))
                    .isInstanceOf(SettlementException.class)
                    .satisfies(ex -> assertCode(ex, "ALREADY_CANCELLED"));
        }
    }

    // ════════════════════════════════════════
    // cancelSettlementByOrderId
    // ════════════════════════════════════════

    @Nested
    @DisplayName("cancelSettlementByOrderId")
    class CancelSettlementByOrderId {

        @Test
        @DisplayName("주문 없음 → SETTLEMENT_NOT_FOUND")
        void notFound() {
            given(settlementRepository.findByOrderId("ORD-NONE")).willReturn(Optional.empty());

            assertThatThrownBy(() -> settlementService.cancelSettlementByOrderId("ORD-NONE"))
                    .isInstanceOf(SettlementException.class)
                    .satisfies(ex -> assertCode(ex, "SETTLEMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("성공 → CANCELLED")
        void success() {
            given(settlementRepository.findByOrderId("ORD-001"))
                    .willReturn(Optional.of(settlement("ORD-001")));

            SettlementResponse response = settlementService.cancelSettlementByOrderId("ORD-001");

            assertThat(response.status()).isEqualTo("CANCELLED");
        }
    }

    // ════════════════════════════════════════
    // 헬퍼: 엔티티 생성
    // ════════════════════════════════════════

    private static SettlementEntity settlement(String orderId) {
        return SettlementEntity.create("STL-001", orderId, "SELLER-DEFAULT",
                50000, 30, 0, 0, 0, 0);
    }

    private static CouponEntity coupon(String couponId, CouponType type) {
        return new CouponEntity(couponId, CouponStatus.USED, type,
                DiscountType.FIXED_AMOUNT, 1000, null);
    }

    private static OrderData orderData(String orderId, String status) {
        return new OrderData(orderId, "USER-001",
                List.of(new OrderItemData("PROD-001", 2, 25000, "ACTIVE")),
                50000, 0, 0, 50000, 0, status, null);
    }

    // ════════════════════════════════════════
    // 헬퍼: Mock 세팅
    // ════════════════════════════════════════

    private void givenDefaultPolicy() {
        given(policyConfig.defaultCommissionRate()).willReturn(30);
    }

    private void givenCreateSettlementSetup(String orderId, int totalAmount,
                                             int couponDiscount, int pointDiscount,
                                             String couponId) {
        given(settlementRepository.findByOrderId(orderId)).willReturn(Optional.empty());
        int deliveryFee = (totalAmount >= 30000) ? 0 : 3000;
        int pgPayment = totalAmount - couponDiscount - pointDiscount + deliveryFee;
        given(orderServiceClient.getOrder(orderId)).willReturn(new OrderData(
                orderId, "USER-001",
                List.of(new OrderItemData("PROD-001", 2, totalAmount / 2, "ACTIVE")),
                totalAmount, couponDiscount, pointDiscount, pgPayment,
                deliveryFee, "PURCHASE_CONFIRMED", couponId));
        givenDefaultPolicy();
        given(settlementRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    // ════════════════════════════════════════
    // 헬퍼: 공통 검증
    // ════════════════════════════════════════

    private void assertException(String orderId, String expectedCode) {
        assertThatThrownBy(() -> settlementService.createSettlement(orderId))
                .isInstanceOf(SettlementException.class)
                .satisfies(ex -> assertCode(ex, expectedCode));
    }

    private static void assertCode(Throwable ex, String expectedCode) {
        assertThat(((SettlementException) ex).getCode()).isEqualTo(expectedCode);
    }
}
