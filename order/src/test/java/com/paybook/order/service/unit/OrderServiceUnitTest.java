package com.paybook.order.service.unit;

import com.paybook.core.entity.*;
import com.paybook.core.repository.CouponRepository;
import com.paybook.core.repository.ProductRepository;
import com.paybook.core.repository.UserPointRepository;
import com.paybook.order.config.DeliveryFeeConfig;
import com.paybook.order.config.DiscountPolicyConfig;
import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.CreateOrderRequest.OrderItemRequest;
import com.paybook.order.dto.OrderResponse;
import com.paybook.order.entity.OrderEntity;
import com.paybook.order.entity.OrderItemEntity;
import com.paybook.order.entity.OrderStatus;
import com.paybook.order.exception.OrderException;
import com.paybook.order.repository.OrderRepository;
import com.paybook.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * OrderService 순수 유닛 테스트 (런던 학파).
 *
 * Spring 컨텍스트 없이, 모든 의존성을 Mock으로 대체하여
 * OrderService의 모든 분기 로직을 검증한다.
 *
 * 원칙: 실패 원인 1개 = 테스트 1개.
 * 실패 시 어떤 분기에서 문제가 생겼는지 즉시 특정할 수 있다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 유닛 테스트")
class OrderServiceUnitTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserPointRepository userPointRepository;

    @Mock
    private DiscountPolicyConfig discountPolicyConfig;

    @Mock
    private DeliveryFeeConfig deliveryFeeConfig;

    // ════════════════════════════════════════
    // createOrder — 재고 검증 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("createOrder: 재고 검증")
    class CreateOrder_StockValidation {

        @Test
        @DisplayName("상품 없음 → PRODUCT_NOT_FOUND, 쿠폰·포인트 검증까지 가지 않는다")
        void productNotFound_stopsEarly() {
            given(productRepository.findByProductId("PROD-NONE")).willReturn(Optional.empty());

            assertException(request("PROD-NONE", 1), "PRODUCT_NOT_FOUND");

            verify(couponRepository, never()).findByCouponId(anyString());
            verify(userPointRepository, never()).findByUserId(anyString());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 부족 → OUT_OF_STOCK, 쿠폰·포인트 검증까지 가지 않는다")
        void outOfStock_stopsEarly() {
            givenProduct("PROD-001", 10000, 5);

            assertException(request("PROD-001", 10), "OUT_OF_STOCK");

            verify(couponRepository, never()).findByCouponId(anyString());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("여러 상품 중 두 번째 재고 부족 → OUT_OF_STOCK, 주문 저장 안 됨")
        void secondItemOutOfStock_stopsEarly() {
            givenProduct("PROD-001", 10000, 100);
            givenProduct("PROD-002", 20000, 1);

            CreateOrderRequest request = new CreateOrderRequest("USER-001",
                    List.of(new OrderItemRequest("PROD-001", 2), new OrderItemRequest("PROD-002", 5)),
                    "서울시", null, null);

            assertException(request, "OUT_OF_STOCK");
            verify(orderRepository, never()).save(any());
        }
    }

    // ════════════════════════════════════════
    // createOrder — 쿠폰 검증 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("createOrder: 쿠폰 검증")
    class CreateOrder_CouponValidation {

        @Test
        @DisplayName("쿠폰 null → 쿠폰 조회 자체를 하지 않는다")
        void nullCoupon_skipsCouponLookup() {
            givenProduct("PROD-001", 10000, 100);
            givenDefaultPolicy();
            givenOrderSave();

            orderService.createOrder(request("PROD-001", 1));

            verify(couponRepository, never()).findByCouponId(anyString());
        }

        @Test
        @DisplayName("쿠폰 없음 → COUPON_NOT_FOUND, 포인트 검증까지 가지 않는다")
        void couponNotFound_stopsBeforePoints() {
            givenProduct("PROD-001", 10000, 100);
            given(couponRepository.findByCouponId("COUPON-NONE")).willReturn(Optional.empty());

            assertException(requestWithCoupon("COUPON-NONE"), "COUPON_NOT_FOUND");

            verify(userPointRepository, never()).findByUserId(anyString());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("사용된 쿠폰 → COUPON_ALREADY_USED, 포인트 검증까지 가지 않는다")
        void couponUsed_stopsBeforePoints() {
            givenProduct("PROD-001", 10000, 100);
            givenCoupon("COUPON-USED", CouponStatus.USED, 1000);

            assertException(requestWithCoupon("COUPON-USED"), "COUPON_ALREADY_USED");

            verify(userPointRepository, never()).findByUserId(anyString());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("만료된 쿠폰 → COUPON_EXPIRED, 포인트 검증까지 가지 않는다")
        void couponExpired_stopsBeforePoints() {
            givenProduct("PROD-001", 10000, 100);
            givenCoupon("COUPON-EXP", CouponStatus.EXPIRED, 1000);

            assertException(requestWithCoupon("COUPON-EXP"), "COUPON_EXPIRED");

            verify(userPointRepository, never()).findByUserId(anyString());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("최소 주문금액 미달 → COUPON_MIN_ORDER_NOT_MET")
        void couponMinOrderNotMet() {
            givenProduct("PROD-001", 5000, 100);
            given(couponRepository.findByCouponId("COUPON-MIN")).willReturn(Optional.of(
                    new CouponEntity("COUPON-MIN", CouponStatus.ACTIVE, CouponType.PLATFORM,
                            DiscountType.FIXED_AMOUNT, 1000, null, 10000)));

            // 총액 5,000 < 최소주문 10,000
            assertException(requestWithCoupon("COUPON-MIN"), "COUPON_MIN_ORDER_NOT_MET");
            verify(orderRepository, never()).save(any());
        }
    }

    // ════════════════════════════════════════
    // createOrder — 포인트 검증 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("createOrder: 포인트 검증")
    class CreateOrder_PointsValidation {

        @Test
        @DisplayName("포인트 null → 포인트 조회 자체를 하지 않는다")
        void nullPoints_skipsPointLookup() {
            givenProduct("PROD-001", 10000, 100);
            givenDefaultPolicy();
            givenOrderSave();

            orderService.createOrder(request("PROD-001", 1));

            verify(userPointRepository, never()).findByUserId(anyString());
        }

        @Test
        @DisplayName("포인트 0 → 포인트 조회 자체를 하지 않는다")
        void zeroPoints_skipsPointLookup() {
            givenProduct("PROD-001", 10000, 100);
            givenDefaultPolicy();
            givenOrderSave();

            CreateOrderRequest request = new CreateOrderRequest(
                    "USER-001", List.of(new OrderItemRequest("PROD-001", 1)),
                    "서울시", null, 0);
            orderService.createOrder(request);

            verify(userPointRepository, never()).findByUserId(anyString());
        }

        @Test
        @DisplayName("포인트 계정 없음 → POINTS_UNAVAILABLE, 할인 계산까지 가지 않는다")
        void pointAccountNotFound_stopsBeforeDiscountCalc() {
            givenProduct("PROD-001", 10000, 100);
            given(userPointRepository.findByUserId("USER-001")).willReturn(Optional.empty());

            assertException(requestWithPoints(1000), "POINTS_UNAVAILABLE");

            verify(discountPolicyConfig, never()).maxDiscountPercent();
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("포인트 잔액 부족 → POINTS_UNAVAILABLE, 할인 계산까지 가지 않는다")
        void insufficientPoints_stopsBeforeDiscountCalc() {
            givenProduct("PROD-001", 10000, 100);
            given(userPointRepository.findByUserId("USER-001"))
                    .willReturn(Optional.of(new UserPointEntity("USER-001", 500)));

            assertException(requestWithPoints(1000), "POINTS_UNAVAILABLE");

            verify(discountPolicyConfig, never()).maxDiscountPercent();
            verify(orderRepository, never()).save(any());
        }
    }

    // ════════════════════════════════════════
    // createOrder — 할인 한도 검증 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("createOrder: 할인 한도 검증")
    class CreateOrder_DiscountLimit {

        @Test
        @DisplayName("할인 초과 → DISCOUNT_LIMIT_EXCEEDED, 배송비 계산까지 가지 않는다")
        void discountExceeded_stopsBeforeDeliveryFee() {
            givenProduct("PROD-001", 10000, 100);
            givenCoupon("COUPON-BIG", CouponStatus.ACTIVE, 2000);
            given(userPointRepository.findByUserId("USER-001"))
                    .willReturn(Optional.of(new UserPointEntity("USER-001", 5000)));
            given(discountPolicyConfig.maxDiscountPercent()).willReturn(30);

            // 총액 10,000 → 한도 3,000 / 쿠폰 2,000 + 포인트 2,000 = 4,000 → 초과
            CreateOrderRequest request = new CreateOrderRequest("USER-001",
                    List.of(new OrderItemRequest("PROD-001", 1)),
                    "서울시", "COUPON-BIG", 2000);

            assertException(request, "DISCOUNT_LIMIT_EXCEEDED");

            verify(deliveryFeeConfig, never()).freeThreshold();
            verify(orderRepository, never()).save(any());
        }
    }

    // ════════════════════════════════════════
    // createOrder — 배송비 계산 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("createOrder: 배송비 계산")
    class CreateOrder_DeliveryFee {

        @Test
        @DisplayName("총액 >= 무료배송 기준 → 배송비 0")
        void aboveThreshold_freeDelivery() {
            givenProduct("PROD-001", 50000, 100);
            given(discountPolicyConfig.maxDiscountPercent()).willReturn(30);
            given(discountPolicyConfig.minPgPaymentPercent()).willReturn(50);
            given(deliveryFeeConfig.freeThreshold()).willReturn(30000);
            givenOrderSave();

            OrderResponse response = orderService.createOrder(request("PROD-001", 1));

            assertThat(response.deliveryFee()).isZero();
            verify(deliveryFeeConfig, never()).fee();
        }

        @Test
        @DisplayName("총액 < 무료배송 기준 → 배송비 부과")
        void belowThreshold_chargesDeliveryFee() {
            givenProduct("PROD-001", 10000, 100);
            given(discountPolicyConfig.maxDiscountPercent()).willReturn(30);
            given(discountPolicyConfig.minPgPaymentPercent()).willReturn(50);
            given(deliveryFeeConfig.freeThreshold()).willReturn(30000);
            given(deliveryFeeConfig.fee()).willReturn(3000);
            givenOrderSave();

            OrderResponse response = orderService.createOrder(request("PROD-001", 1));

            assertThat(response.deliveryFee()).isEqualTo(3000);
        }
    }

    // ════════════════════════════════════════
    // createOrder — PG 최소 결제 검증 분기
    // ════════════════════════════════════════

    @Nested
    @DisplayName("createOrder: PG 최소 결제 검증")
    class CreateOrder_MinPgPayment {

        @Test
        @DisplayName("PG 결제 미달 → PG_PAYMENT_BELOW_MINIMUM, 주문 저장까지 가지 않는다")
        void pgBelowMinimum_stopsBeforeSave() {
            givenProduct("PROD-001", 10000, 100);
            givenCoupon("COUPON-OK", CouponStatus.ACTIVE, 1000);
            given(userPointRepository.findByUserId("USER-001"))
                    .willReturn(Optional.of(new UserPointEntity("USER-001", 5000)));
            given(discountPolicyConfig.maxDiscountPercent()).willReturn(30);
            given(discountPolicyConfig.minPgPaymentPercent()).willReturn(80);
            given(deliveryFeeConfig.freeThreshold()).willReturn(0);

            // 총액 10,000 → 할인 3,000 → pg 7,000 / 최소 8,000 → 위반
            CreateOrderRequest request = new CreateOrderRequest("USER-001",
                    List.of(new OrderItemRequest("PROD-001", 1)),
                    "서울시", "COUPON-OK", 2000);

            assertException(request, "PG_PAYMENT_BELOW_MINIMUM");
            verify(orderRepository, never()).save(any());
        }
    }

    // ════════════════════════════════════════
    // createOrder — 성공 시 부수효과 (각각 개별 검증)
    // ════════════════════════════════════════

    @Nested
    @DisplayName("createOrder: 성공 시 부수효과")
    class CreateOrder_Success {

        @Test
        @DisplayName("주문 저장이 호출된다")
        void savesOrder() {
            givenProduct("PROD-001", 10000, 100);
            givenDefaultPolicy();
            givenOrderSave();

            orderService.createOrder(request("PROD-001", 2));

            verify(orderRepository).save(any(OrderEntity.class));
        }

        @Test
        @DisplayName("상품 재고가 주문 수량만큼 차감된다")
        void deductsStock() {
            ProductEntity product = product("PROD-001", 10000, 100);
            given(productRepository.findByProductId("PROD-001")).willReturn(Optional.of(product));
            givenDefaultPolicy();
            givenOrderSave();

            orderService.createOrder(request("PROD-001", 3));

            assertThat(product.getStockQuantity()).isEqualTo(97);
        }

        @Test
        @DisplayName("쿠폰 사용 시 → 쿠폰 상태가 USED로 변경된다")
        void marksCouponUsed() {
            givenProduct("PROD-001", 10000, 100);
            CouponEntity coupon = activeCoupon("COUPON-OK", 1000);
            given(couponRepository.findByCouponId("COUPON-OK")).willReturn(Optional.of(coupon));
            givenDefaultPolicy();
            givenOrderSave();

            orderService.createOrder(requestWithCoupon("COUPON-OK"));

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @Test
        @DisplayName("쿠폰 미사용 시 → 쿠폰 조회/변경 없음")
        void noCoupon_noMarkUsed() {
            givenProduct("PROD-001", 10000, 100);
            givenDefaultPolicy();
            givenOrderSave();

            orderService.createOrder(request("PROD-001", 1));

            verify(couponRepository, never()).findByCouponId(anyString());
        }

        @Test
        @DisplayName("포인트 사용 시 → 포인트가 차감된다")
        void deductsPoints() {
            givenProduct("PROD-001", 10000, 100);
            UserPointEntity userPoint = new UserPointEntity("USER-001", 5000);
            given(userPointRepository.findByUserId("USER-001")).willReturn(Optional.of(userPoint));
            givenDefaultPolicy();
            givenOrderSave();

            orderService.createOrder(requestWithPoints(1000));

            assertThat(userPoint.getBalance()).isEqualTo(4000);
        }

        @Test
        @DisplayName("포인트 미사용 시 → 포인트 조회/차감 없음")
        void noPoints_noDeduction() {
            givenProduct("PROD-001", 10000, 100);
            givenDefaultPolicy();
            givenOrderSave();

            orderService.createOrder(request("PROD-001", 1));

            verify(userPointRepository, never()).findByUserId(anyString());
        }

        @Test
        @DisplayName("응답 상태가 PENDING_PAYMENT이다")
        void responseStatusIsPending() {
            givenProduct("PROD-001", 10000, 100);
            givenDefaultPolicy();
            givenOrderSave();

            OrderResponse response = orderService.createOrder(request("PROD-001", 1));

            assertThat(response.status()).isEqualTo("PENDING_PAYMENT");
        }
    }

    // ════════════════════════════════════════
    // getOrder
    // ════════════════════════════════════════

    @Nested
    @DisplayName("getOrder")
    class GetOrder {

        @Test
        @DisplayName("주문 없음 → ORDER_NOT_FOUND")
        void orderNotFound() {
            given(orderRepository.findByOrderId("ORD-NONE")).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrder("ORD-NONE"))
                    .isInstanceOf(OrderException.class)
                    .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                            .isEqualTo("ORDER_NOT_FOUND"));
        }

        @Test
        @DisplayName("주문 있음 → 응답 반환")
        void orderFound_returnsResponse() {
            given(orderRepository.findByOrderId("ORD-001"))
                    .willReturn(Optional.of(orderWithItem("ORD-001")));

            OrderResponse response = orderService.getOrder("ORD-001");

            assertThat(response.orderId()).isEqualTo("ORD-001");
        }
    }

    // ════════════════════════════════════════
    // getOrdersByUserId
    // ════════════════════════════════════════

    @Nested
    @DisplayName("getOrdersByUserId")
    class GetOrdersByUserId {

        private final Pageable pageable = PageRequest.of(0, 10);

        @Test
        @DisplayName("status null → 전체 조회 쿼리 호출")
        void statusNull_callsFindByUserId() {
            given(orderRepository.findByUserIdOrderByCreatedAtDesc("USER-001", pageable))
                    .willReturn(new PageImpl<>(List.of(orderWithItem("ORD-001"))));

            Page<OrderResponse> result = orderService.getOrdersByUserId("USER-001", null, pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(orderRepository).findByUserIdOrderByCreatedAtDesc("USER-001", pageable);
            verify(orderRepository, never()).findByUserIdAndStatusOrderByCreatedAtDesc(
                    anyString(), any(OrderStatus.class), any(Pageable.class));
        }

        @Test
        @DisplayName("status 지정 → 상태 필터 쿼리 호출")
        void statusGiven_callsFindByUserIdAndStatus() {
            given(orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                    "USER-001", OrderStatus.CONFIRMED, pageable))
                    .willReturn(new PageImpl<>(List.of()));

            Page<OrderResponse> result = orderService.getOrdersByUserId(
                    "USER-001", OrderStatus.CONFIRMED, pageable);

            assertThat(result.getContent()).isEmpty();
            verify(orderRepository).findByUserIdAndStatusOrderByCreatedAtDesc(
                    "USER-001", OrderStatus.CONFIRMED, pageable);
            verify(orderRepository, never()).findByUserIdOrderByCreatedAtDesc(
                    anyString(), any(Pageable.class));
        }
    }

    // ════════════════════════════════════════
    // 상태 전이: confirmOrder
    // ════════════════════════════════════════

    @Nested
    @DisplayName("confirmOrder")
    class ConfirmOrder {

        @Test
        @DisplayName("주문 없음 → ORDER_NOT_FOUND")
        void orderNotFound() {
            givenOrderNotFound("ORD-NONE");
            assertThrowsOrderNotFound(() -> orderService.confirmOrder("ORD-NONE"));
        }

        @Test
        @DisplayName("성공 → CONFIRMED 상태")
        void success() {
            OrderEntity order = orderWithItem("ORD-001");
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));

            OrderResponse response = orderService.confirmOrder("ORD-001");

            assertThat(response.status()).isEqualTo("CONFIRMED");
        }
    }

    // ════════════════════════════════════════
    // 상태 전이: markPaymentFailed
    // ════════════════════════════════════════

    @Nested
    @DisplayName("markPaymentFailed")
    class MarkPaymentFailed {

        @Test
        @DisplayName("주문 없음 → ORDER_NOT_FOUND")
        void orderNotFound() {
            givenOrderNotFound("ORD-NONE");
            assertThrowsOrderNotFound(() -> orderService.markPaymentFailed("ORD-NONE"));
        }

        @Test
        @DisplayName("성공 → PAYMENT_FAILED 상태 + 리소스 복원 호출")
        void success_restoresResources() {
            OrderEntity order = orderWithItemAndResources("ORD-001", "COUPON-OK", 1000);
            ProductEntity product = product("PROD-001", 10000, 90);
            CouponEntity coupon = activeCoupon("COUPON-OK", 1000);
            coupon.markUsed();
            UserPointEntity userPoint = new UserPointEntity("USER-001", 4000);

            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));
            given(productRepository.findByProductId("PROD-001")).willReturn(Optional.of(product));
            given(couponRepository.findByCouponId("COUPON-OK")).willReturn(Optional.of(coupon));
            given(userPointRepository.findByUserId("USER-001")).willReturn(Optional.of(userPoint));

            OrderResponse response = orderService.markPaymentFailed("ORD-001");

            assertThat(response.status()).isEqualTo("PAYMENT_FAILED");
            assertThat(product.getStockQuantity()).isEqualTo(92);
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
            assertThat(userPoint.getBalance()).isEqualTo(5000);
        }
    }

    // ════════════════════════════════════════
    // 상태 전이: startShipping
    // ════════════════════════════════════════

    @Nested
    @DisplayName("startShipping")
    class StartShipping {

        @Test
        @DisplayName("주문 없음 → ORDER_NOT_FOUND")
        void orderNotFound() {
            givenOrderNotFound("ORD-NONE");
            assertThrowsOrderNotFound(() -> orderService.startShipping("ORD-NONE"));
        }

        @Test
        @DisplayName("PENDING → SHIPPING 시도 → INVALID_STATUS_TRANSITION")
        void invalidFromPending() {
            given(orderRepository.findByOrderId("ORD-001"))
                    .willReturn(Optional.of(orderWithItem("ORD-001")));

            assertThatThrownBy(() -> orderService.startShipping("ORD-001"))
                    .isInstanceOf(OrderException.class)
                    .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                            .isEqualTo("INVALID_STATUS_TRANSITION"));
        }

        @Test
        @DisplayName("CONFIRMED → SHIPPING 성공")
        void validFromConfirmed() {
            OrderEntity order = orderWithItem("ORD-001");
            order.confirm();
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));

            OrderResponse response = orderService.startShipping("ORD-001");

            assertThat(response.status()).isEqualTo("SHIPPING");
        }
    }

    // ════════════════════════════════════════
    // 상태 전이: markDelivered
    // ════════════════════════════════════════

    @Nested
    @DisplayName("markDelivered")
    class MarkDelivered {

        @Test
        @DisplayName("주문 없음 → ORDER_NOT_FOUND")
        void orderNotFound() {
            givenOrderNotFound("ORD-NONE");
            assertThrowsOrderNotFound(() -> orderService.markDelivered("ORD-NONE"));
        }

        @Test
        @DisplayName("CONFIRMED → DELIVERED 시도 → INVALID_STATUS_TRANSITION")
        void invalidFromConfirmed() {
            OrderEntity order = orderWithItem("ORD-001");
            order.confirm();
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.markDelivered("ORD-001"))
                    .isInstanceOf(OrderException.class)
                    .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                            .isEqualTo("INVALID_STATUS_TRANSITION"));
        }

        @Test
        @DisplayName("SHIPPING → DELIVERED 성공")
        void validFromShipping() {
            OrderEntity order = orderWithItem("ORD-001");
            order.confirm();
            order.startShipping();
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));

            OrderResponse response = orderService.markDelivered("ORD-001");

            assertThat(response.status()).isEqualTo("DELIVERED");
        }
    }

    // ════════════════════════════════════════
    // 상태 전이: confirmPurchase
    // ════════════════════════════════════════

    @Nested
    @DisplayName("confirmPurchase")
    class ConfirmPurchase {

        @Test
        @DisplayName("주문 없음 → ORDER_NOT_FOUND")
        void orderNotFound() {
            givenOrderNotFound("ORD-NONE");
            assertThrowsOrderNotFound(() -> orderService.confirmPurchase("ORD-NONE"));
        }

        @Test
        @DisplayName("SHIPPING → PURCHASE_CONFIRMED 시도 → INVALID_STATUS_TRANSITION")
        void invalidFromShipping() {
            OrderEntity order = orderWithItem("ORD-001");
            order.confirm();
            order.startShipping();
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.confirmPurchase("ORD-001"))
                    .isInstanceOf(OrderException.class)
                    .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                            .isEqualTo("INVALID_STATUS_TRANSITION"));
        }

        @Test
        @DisplayName("DELIVERED → PURCHASE_CONFIRMED 성공")
        void validFromDelivered() {
            OrderEntity order = deliveredOrder("ORD-001");
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));

            OrderResponse response = orderService.confirmPurchase("ORD-001");

            assertThat(response.status()).isEqualTo("PURCHASE_CONFIRMED");
        }
    }

    // ════════════════════════════════════════
    // 상태 전이: requestReturn
    // ════════════════════════════════════════

    @Nested
    @DisplayName("requestReturn")
    class RequestReturn {

        @Test
        @DisplayName("주문 없음 → ORDER_NOT_FOUND")
        void orderNotFound() {
            givenOrderNotFound("ORD-NONE");
            assertThrowsOrderNotFound(() -> orderService.requestReturn("ORD-NONE"));
        }

        @Test
        @DisplayName("CONFIRMED → RETURN_REQUESTED 시도 → INVALID_STATUS_TRANSITION")
        void invalidFromConfirmed() {
            OrderEntity order = orderWithItem("ORD-001");
            order.confirm();
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.requestReturn("ORD-001"))
                    .isInstanceOf(OrderException.class)
                    .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                            .isEqualTo("INVALID_STATUS_TRANSITION"));
        }

        @Test
        @DisplayName("DELIVERED → RETURN_REQUESTED 성공")
        void validFromDelivered() {
            OrderEntity order = deliveredOrder("ORD-001");
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));

            OrderResponse response = orderService.requestReturn("ORD-001");

            assertThat(response.status()).isEqualTo("RETURN_REQUESTED");
        }
    }

    // ════════════════════════════════════════
    // 상태 전이: completeReturn
    // ════════════════════════════════════════

    @Nested
    @DisplayName("completeReturn")
    class CompleteReturn {

        @Test
        @DisplayName("주문 없음 → ORDER_NOT_FOUND")
        void orderNotFound() {
            givenOrderNotFound("ORD-NONE");
            assertThrowsOrderNotFound(() -> orderService.completeReturn("ORD-NONE"));
        }

        @Test
        @DisplayName("DELIVERED → RETURNED 시도 → INVALID_STATUS_TRANSITION")
        void invalidFromDelivered() {
            OrderEntity order = deliveredOrder("ORD-001");
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.completeReturn("ORD-001"))
                    .isInstanceOf(OrderException.class)
                    .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                            .isEqualTo("INVALID_STATUS_TRANSITION"));
        }

        @Test
        @DisplayName("RETURN_REQUESTED → RETURNED 성공 + 리소스 복원")
        void validFromReturnRequested_restoresResources() {
            OrderEntity order = deliveredOrder("ORD-001");
            order.requestReturn();
            // 쿠폰/포인트 없는 주문
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));
            ProductEntity product = product("PROD-001", 10000, 90);
            given(productRepository.findByProductId("PROD-001")).willReturn(Optional.of(product));

            OrderResponse response = orderService.completeReturn("ORD-001");

            assertThat(response.status()).isEqualTo("RETURNED");
            assertThat(product.getStockQuantity()).isEqualTo(92);
        }
    }

    // ════════════════════════════════════════
    // cancelOrder
    // ════════════════════════════════════════

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("주문 없음 → ORDER_NOT_FOUND")
        void orderNotFound() {
            givenOrderNotFound("ORD-NONE");
            assertThrowsOrderNotFound(() -> orderService.cancelOrder("ORD-NONE"));
        }

        @Test
        @DisplayName("이미 취소됨 → ORDER_ALREADY_CANCELLED")
        void alreadyCancelled() {
            OrderEntity order = orderWithItem("ORD-001");
            order.cancel();
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));
            given(productRepository.findByProductId("PROD-001"))
                    .willReturn(Optional.of(product("PROD-001", 10000, 100)));

            assertThatThrownBy(() -> orderService.cancelOrder("ORD-001"))
                    .isInstanceOf(OrderException.class)
                    .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                            .isEqualTo("ORDER_ALREADY_CANCELLED"));
        }

        @Test
        @DisplayName("취소 불가 상태(PURCHASE_CONFIRMED) → ORDER_NOT_CANCELLABLE")
        void notCancellable() {
            OrderEntity order = deliveredOrder("ORD-001");
            order.confirmPurchase();
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));
            given(productRepository.findByProductId("PROD-001"))
                    .willReturn(Optional.of(product("PROD-001", 10000, 100)));

            assertThatThrownBy(() -> orderService.cancelOrder("ORD-001"))
                    .isInstanceOf(OrderException.class)
                    .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                            .isEqualTo("ORDER_NOT_CANCELLABLE"));
        }

        @Test
        @DisplayName("취소 성공 → 재고 복원")
        void success_restoresStock() {
            OrderEntity order = orderWithItem("ORD-001");
            ProductEntity product = product("PROD-001", 10000, 90);
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));
            given(productRepository.findByProductId("PROD-001")).willReturn(Optional.of(product));

            orderService.cancelOrder("ORD-001");

            assertThat(product.getStockQuantity()).isEqualTo(92);
        }

        @Test
        @DisplayName("쿠폰 있는 주문 취소 → 쿠폰 ACTIVE로 복원")
        void success_restoresCoupon() {
            OrderEntity order = orderWithItemAndResources("ORD-001", "COUPON-OK", null);
            CouponEntity coupon = activeCoupon("COUPON-OK", 1000);
            coupon.markUsed();
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));
            given(productRepository.findByProductId("PROD-001"))
                    .willReturn(Optional.of(product("PROD-001", 10000, 90)));
            given(couponRepository.findByCouponId("COUPON-OK")).willReturn(Optional.of(coupon));

            orderService.cancelOrder("ORD-001");

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
        }

        @Test
        @DisplayName("포인트 있는 주문 취소 → 포인트 복원")
        void success_restoresPoints() {
            OrderEntity order = orderWithItemAndResources("ORD-001", null, 1000);
            UserPointEntity userPoint = new UserPointEntity("USER-001", 4000);
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));
            given(productRepository.findByProductId("PROD-001"))
                    .willReturn(Optional.of(product("PROD-001", 10000, 90)));
            given(userPointRepository.findByUserId("USER-001")).willReturn(Optional.of(userPoint));

            orderService.cancelOrder("ORD-001");

            assertThat(userPoint.getBalance()).isEqualTo(5000);
        }

        @Test
        @DisplayName("쿠폰·포인트 없는 주문 취소 → 쿠폰/포인트 조회 안 함")
        void noCouponNoPoints_skipsRestore() {
            OrderEntity order = orderWithItem("ORD-001");
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));
            given(productRepository.findByProductId("PROD-001"))
                    .willReturn(Optional.of(product("PROD-001", 10000, 90)));

            orderService.cancelOrder("ORD-001");

            verify(couponRepository, never()).findByCouponId(anyString());
            verify(userPointRepository, never()).findByUserId(anyString());
        }
    }

    // ════════════════════════════════════════
    // cancelItem
    // ════════════════════════════════════════

    @Nested
    @DisplayName("cancelItem")
    class CancelItem {

        @Test
        @DisplayName("주문 없음 → ORDER_NOT_FOUND")
        void orderNotFound() {
            givenOrderNotFound("ORD-NONE");
            assertThatThrownBy(() -> orderService.cancelItem("ORD-NONE", "PROD-001"))
                    .isInstanceOf(OrderException.class)
                    .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                            .isEqualTo("ORDER_NOT_FOUND"));
        }

        @Test
        @DisplayName("취소 불가 상태 → ORDER_NOT_CANCELLABLE")
        void notCancellable() {
            OrderEntity order = deliveredOrder("ORD-001");
            order.confirmPurchase();
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelItem("ORD-001", "PROD-001"))
                    .isInstanceOf(OrderException.class)
                    .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                            .isEqualTo("ORDER_NOT_CANCELLABLE"));

            verify(productRepository, never()).findByProductId(anyString());
        }

        @Test
        @DisplayName("해당 상품이 주문에 없음 → PRODUCT_NOT_FOUND")
        void itemNotFound() {
            OrderEntity order = orderWithItem("ORD-001");
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelItem("ORD-001", "PROD-NONE"))
                    .isInstanceOf(OrderException.class)
                    .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                            .isEqualTo("PRODUCT_NOT_FOUND"));
        }

        @Test
        @DisplayName("개별 상품 취소 성공 → 해당 상품 재고만 복원, 주문은 유지")
        void partialCancel_restoresStockOnly() {
            OrderEntity order = orderWithTwoItems("ORD-001");
            ProductEntity product1 = product("PROD-001", 10000, 90);
            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));
            given(productRepository.findByProductId("PROD-001")).willReturn(Optional.of(product1));

            OrderResponse response = orderService.cancelItem("ORD-001", "PROD-001");

            assertThat(product1.getStockQuantity()).isEqualTo(92);
            assertThat(response.status()).isNotEqualTo("CANCELLED");
            verify(couponRepository, never()).findByCouponId(anyString());
        }

        @Test
        @DisplayName("마지막 상품 취소 → 전체 주문 취소 + 쿠폰/포인트 복원")
        void lastItemCancel_cancelsOrder() {
            OrderEntity order = orderWithItemAndResources("ORD-001", "COUPON-OK", 1000);
            ProductEntity product = product("PROD-001", 10000, 90);
            CouponEntity coupon = activeCoupon("COUPON-OK", 1000);
            coupon.markUsed();
            UserPointEntity userPoint = new UserPointEntity("USER-001", 4000);

            given(orderRepository.findByOrderId("ORD-001")).willReturn(Optional.of(order));
            given(productRepository.findByProductId("PROD-001")).willReturn(Optional.of(product));
            given(couponRepository.findByCouponId("COUPON-OK")).willReturn(Optional.of(coupon));
            given(userPointRepository.findByUserId("USER-001")).willReturn(Optional.of(userPoint));

            OrderResponse response = orderService.cancelItem("ORD-001", "PROD-001");

            assertThat(response.status()).isEqualTo("CANCELLED");
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
            assertThat(userPoint.getBalance()).isEqualTo(5000);
        }
    }

    // ════════════════════════════════════════
    // 헬퍼: 요청 생성
    // ════════════════════════════════════════

    private static CreateOrderRequest request(String productId, int quantity) {
        return new CreateOrderRequest("USER-001",
                List.of(new OrderItemRequest(productId, quantity)),
                "서울시", null, null);
    }

    private static CreateOrderRequest requestWithCoupon(String couponId) {
        return new CreateOrderRequest("USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                "서울시", couponId, null);
    }

    private static CreateOrderRequest requestWithPoints(int points) {
        return new CreateOrderRequest("USER-001",
                List.of(new OrderItemRequest("PROD-001", 1)),
                "서울시", null, points);
    }

    // ════════════════════════════════════════
    // 헬퍼: 엔티티 생성
    // ════════════════════════════════════════

    private static ProductEntity product(String productId, int price, int stock) {
        return new ProductEntity(productId, "상품", price, stock);
    }

    private static CouponEntity activeCoupon(String couponId, int discountValue) {
        return new CouponEntity(couponId, CouponStatus.ACTIVE, CouponType.PLATFORM,
                DiscountType.FIXED_AMOUNT, discountValue, null);
    }

    private static OrderEntity orderWithItem(String orderId) {
        OrderEntity order = OrderEntity.builder()
                .orderId(orderId).userId("USER-001").totalAmount(20000).build();
        order.addItem(new OrderItemEntity("PROD-001", 2, 10000));
        return order;
    }

    private static OrderEntity orderWithTwoItems(String orderId) {
        OrderEntity order = OrderEntity.builder()
                .orderId(orderId).userId("USER-001").totalAmount(30000).build();
        order.addItem(new OrderItemEntity("PROD-001", 2, 10000));
        order.addItem(new OrderItemEntity("PROD-002", 1, 10000));
        return order;
    }

    private static OrderEntity orderWithItemAndResources(String orderId, String couponId, Integer points) {
        OrderEntity order = OrderEntity.builder()
                .orderId(orderId).userId("USER-001").totalAmount(20000)
                .couponId(couponId).pointAmountToUse(points).build();
        order.addItem(new OrderItemEntity("PROD-001", 2, 10000));
        return order;
    }

    private static OrderEntity deliveredOrder(String orderId) {
        OrderEntity order = orderWithItem(orderId);
        order.confirm();
        order.startShipping();
        order.markDelivered();
        return order;
    }

    // ════════════════════════════════════════
    // 헬퍼: Mock 세팅
    // ════════════════════════════════════════

    private void givenProduct(String productId, int price, int stock) {
        given(productRepository.findByProductId(productId))
                .willReturn(Optional.of(product(productId, price, stock)));
    }

    private void givenCoupon(String couponId, CouponStatus status, int discountValue) {
        given(couponRepository.findByCouponId(couponId)).willReturn(Optional.of(
                new CouponEntity(couponId, status, CouponType.PLATFORM,
                        DiscountType.FIXED_AMOUNT, discountValue, null)));
    }

    private void givenDefaultPolicy() {
        given(discountPolicyConfig.maxDiscountPercent()).willReturn(30);
        given(discountPolicyConfig.minPgPaymentPercent()).willReturn(50);
        given(deliveryFeeConfig.freeThreshold()).willReturn(0);
    }

    private void givenOrderSave() {
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private void givenOrderNotFound(String orderId) {
        given(orderRepository.findByOrderId(orderId)).willReturn(Optional.empty());
    }

    // ════════════════════════════════════════
    // 헬퍼: 공통 검증
    // ════════════════════════════════════════

    private void assertException(CreateOrderRequest request, String expectedCode) {
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                        .isEqualTo(expectedCode));
    }

    private void assertThrowsOrderNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(OrderException.class)
                .satisfies(ex -> assertThat(((OrderException) ex).getCode())
                        .isEqualTo("ORDER_NOT_FOUND"));
    }
}
