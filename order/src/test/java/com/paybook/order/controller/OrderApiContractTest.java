package com.paybook.order.controller;

import com.paybook.core.entity.*;
import com.paybook.core.repository.CouponRepository;
import com.paybook.core.repository.ProductRepository;
import com.paybook.core.repository.UserPointRepository;
import com.paybook.order.config.DeliveryFeeConfig;
import com.paybook.order.config.DiscountPolicyConfig;
import com.paybook.order.entity.OrderEntity;
import com.paybook.order.repository.OrderRepository;
import com.paybook.order.service.OrderService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import({OrderService.class, DiscountPolicyConfig.class, DeliveryFeeConfig.class})
@DisplayName("주문 API 계약 슬라이스 테스트 — request/response 형식 + Bean Validation 검증")
class OrderApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private CouponRepository couponRepository;

    @MockitoBean
    private UserPointRepository userPointRepository;

    @MockitoBean
    private DiscountPolicyConfig discountPolicyConfig;

    @MockitoBean
    private DeliveryFeeConfig deliveryFeeConfig;

    private final Map<String, OrderEntity> stubStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUpMocks() {
        stubStore.clear();

        // 할인 한도 30%, PG 최소 결제 50%
        when(discountPolicyConfig.maxDiscountPercent()).thenReturn(30);
        when(discountPolicyConfig.minPgPaymentPercent()).thenReturn(50);

        // 배송비: 무료배송 (테스트 편의)
        when(deliveryFeeConfig.fee()).thenReturn(0);
        when(deliveryFeeConfig.freeThreshold()).thenReturn(0);

        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity entity = invocation.getArgument(0);
            stubStore.put(entity.getOrderId(), entity);
            return entity;
        });

        when(orderRepository.findByOrderId(anyString())).thenAnswer(invocation -> {
            String orderId = invocation.getArgument(0);
            return Optional.ofNullable(stubStore.get(orderId));
        });

        // 상품 mock: PROD-001, PROD-002 → price=10000, stock=100
        when(productRepository.findByProductId("PROD-001"))
                .thenReturn(Optional.of(new ProductEntity("PROD-001", "상품1", 10000, 100)));
        when(productRepository.findByProductId("PROD-002"))
                .thenReturn(Optional.of(new ProductEntity("PROD-002", "상품2", 10000, 100)));

        // 재고 부족 상품 mock: PROD-OOS → stock=0
        when(productRepository.findByProductId("PROD-OOS"))
                .thenReturn(Optional.of(new ProductEntity("PROD-OOS", "재고없음", 10000, 0)));

        // 쿠폰 mock (새 생성자 사용)
        when(couponRepository.findByCouponId("USED"))
                .thenReturn(Optional.of(new CouponEntity("USED", CouponStatus.USED,
                        CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1000, null)));
        when(couponRepository.findByCouponId("EXPIRED"))
                .thenReturn(Optional.of(new CouponEntity("EXPIRED", CouponStatus.EXPIRED,
                        CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1000, null)));
        when(couponRepository.findByCouponId("INVALID"))
                .thenReturn(Optional.empty());
        when(couponRepository.findByCouponId("COUPON-VALID"))
                .thenReturn(Optional.of(new CouponEntity("COUPON-VALID", CouponStatus.ACTIVE,
                        CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1000, null)));

        // 포인트 mock: USER-001 → balance=100000
        when(userPointRepository.findByUserId("USER-001"))
                .thenReturn(Optional.of(new UserPointEntity("USER-001", 100000)));
    }

    private static final String VALID_ORDER_REQUEST = """
            {
                "userId": "USER-001",
                "items": [
                    { "productId": "PROD-001", "quantity": 2 },
                    { "productId": "PROD-002", "quantity": 1 }
                ],
                "deliveryAddress": "서울시 강남구 테헤란로 123"
            }
            """;

    private String createOrderAndGetId() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_ORDER_REQUEST)
                )
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("orderId").asString();
    }


    // ================================================================
    // 주문 생성 성공 — 201 Created + 응답 스키마 검증
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 201: 유효한 요청 시 응답 스키마가 올바르다")
    void createOrder_ValidRequest_Returns201WithCorrectSchema() throws Exception {
        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_ORDER_REQUEST)
                )
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.orderId").isString())
                .andExpect(jsonPath("$.userId").value("USER-001"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalAmount").isNumber())
                .andExpect(jsonPath("$.couponDiscountAmount").value(0))
                .andExpect(jsonPath("$.pointDiscountAmount").value(0))
                .andExpect(jsonPath("$.pgPaymentAmount").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.items[0].productId").value("PROD-001"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].price").isNumber());
    }


    // ================================================================
    // 주문 생성 실패 — userId 누락 시 400
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 400: userId 누락 시 INVALID_REQUEST")
    void createOrder_MissingUserId_Returns400() throws Exception {
        String request = """
                {
                    "items": [{ "productId": "PROD-001", "quantity": 1 }]
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 주문 생성 실패 — 수량 0 시 400
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 400: 수량이 0이면 검증 실패")
    void createOrder_InvalidQuantity_Returns400() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 0 }]
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 주문 생성 실패 — items 빈 배열 시 400
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 400: items가 빈 배열이면 검증 실패")
    void createOrder_EmptyItems_Returns400() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [],
                    "deliveryAddress": "서울시 강남구 테헤란로 123"
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 주문 생성 실패 — 포인트 사용 금액 음수 시 400
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 400: 포인트 사용 금액이 음수이면 검증 실패")
    void createOrder_NegativePointAmount_Returns400() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "deliveryAddress": "서울시 강남구 테헤란로 123",
                    "pointAmountToUse": -1000
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 주문 생성 실패 — 잘못된 JSON 형식 시 400
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 400: 잘못된 JSON 형식이면 파싱 에러")
    void createOrder_MalformedJson_Returns400() throws Exception {
        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ invalid json }")
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"))
                .andExpect(jsonPath("$.message").exists());
    }


    // ================================================================
    // 주문 생성 실패 — Content-Type 누락 시 415
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 415: Content-Type 없이 요청하면 Unsupported Media Type")
    void createOrder_MissingContentType_Returns415() throws Exception {
        mockMvc.perform(
                        post("/api/orders")
                                .content(VALID_ORDER_REQUEST)
                )
                .andDo(print())
                .andExpect(status().isUnsupportedMediaType());
    }


    // ================================================================
    // 주문 조회 성공 — 200 OK + 응답 스키마 검증
    // ================================================================
    @Test
    @DisplayName("GET /api/orders/{orderId} → 200: 존재하는 주문 조회 시 올바른 응답")
    void getOrder_ExistingOrder_Returns200() throws Exception {
        String orderId = createOrderAndGetId();

        mockMvc.perform(
                        get("/api/orders/{orderId}", orderId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.userId").value("USER-001"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalAmount").isNumber())
                .andExpect(jsonPath("$.couponDiscountAmount").isNumber())
                .andExpect(jsonPath("$.pointDiscountAmount").isNumber())
                .andExpect(jsonPath("$.pgPaymentAmount").isNumber())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.createdAt").exists());
    }


    // ================================================================
    // 주문 조회 실패 — 존재하지 않는 주문 404
    // ================================================================
    @Test
    @DisplayName("GET /api/orders/{orderId} → 404: 존재하지 않는 주문 조회 시 ORDER_NOT_FOUND")
    void getOrder_NonExistentOrder_Returns404() throws Exception {
        mockMvc.perform(
                        get("/api/orders/{orderId}", "NON-EXISTENT-ORDER-ID")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }


    // ================================================================
    // 주문 취소 성공 → 중복 취소 시 409
    // ================================================================
    @Test
    @DisplayName("PATCH /api/orders/{orderId}/cancel → 200 후 재취소 시 409 Conflict")
    void cancelOrder_ThenCancelAgain_Returns409() throws Exception {
        String orderId = createOrderAndGetId();

        // 최초 취소 → 200 OK
        mockMvc.perform(
                        patch("/api/orders/{orderId}/cancel", orderId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 중복 취소 → 409 Conflict
        mockMvc.perform(
                        patch("/api/orders/{orderId}/cancel", orderId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_CANCELLED"))
                .andExpect(jsonPath("$.message").exists());
    }


    // ================================================================
    // 주문 취소 실패 — 존재하지 않는 주문 404
    // ================================================================
    @Test
    @DisplayName("PATCH /api/orders/{orderId}/cancel → 404: 존재하지 않는 주문 취소 시 ORDER_NOT_FOUND")
    void cancelOrder_NonExistentOrder_Returns404() throws Exception {
        mockMvc.perform(
                        patch("/api/orders/{orderId}/cancel", "NON-EXISTENT-ORDER-ID")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }


    // ================================================================
    // 주문 생성 실패 — 재고 부족 시 409
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 409: 재고 부족 시 OUT_OF_STOCK 에러")
    void createOrder_OutOfStock_Returns409() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-OOS", "quantity": 1 }],
                    "deliveryAddress": "서울시 강남구 테헤란로 123"
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 주문 생성 실패 — 이미 사용된 쿠폰 409
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 409: 이미 사용된 쿠폰 적용 시 COUPON_ALREADY_USED 에러")
    void createOrder_CouponAlreadyUsed_Returns409() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "deliveryAddress": "서울시 강남구 테헤란로 123",
                    "couponId": "USED"
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_ALREADY_USED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 주문 생성 실패 — 만료된 쿠폰 409
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 409: 만료된 쿠폰 적용 시 COUPON_EXPIRED 에러")
    void createOrder_CouponExpired_Returns409() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "deliveryAddress": "서울시 강남구 테헤란로 123",
                    "couponId": "EXPIRED"
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_EXPIRED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 주문 생성 실패 — 존재하지 않는 쿠폰 404
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 404: 존재하지 않는 쿠폰 ID 시 COUPON_NOT_FOUND 에러")
    void createOrder_CouponNotFound_Returns404() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "deliveryAddress": "서울시 강남구 테헤란로 123",
                    "couponId": "INVALID"
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 주문 생성 실패 — 포인트 잔액 부족 409
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 409: 포인트 잔액 부족 시 POINTS_UNAVAILABLE 에러")
    void createOrder_PointsUnavailable_Returns409() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "deliveryAddress": "서울시 강남구 테헤란로 123",
                    "pointAmountToUse": 200000
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POINTS_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 주문 생성 성공 — 쿠폰 + 포인트 동시 적용
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 201: 쿠폰과 포인트를 동시에 적용한 주문 생성 성공")
    void createOrder_WithCouponAndPoints_Returns201() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "deliveryAddress": "서울시 강남구 테헤란로 123",
                    "couponId": "COUPON-VALID",
                    "pointAmountToUse": 1000
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.userId").value("USER-001"))
                .andExpect(jsonPath("$.totalAmount").isNumber())
                .andExpect(jsonPath("$.couponDiscountAmount").value(1000))
                .andExpect(jsonPath("$.pointDiscountAmount").value(1000))
                .andExpect(jsonPath("$.pgPaymentAmount").value(8000))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.createdAt").exists());
    }


    // ================================================================
    // 할인 한도 초과 — 409
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 409: 할인 한도 초과 시 DISCOUNT_LIMIT_EXCEEDED 에러")
    void createOrder_DiscountLimitExceeded_Returns409() throws Exception {
        // COUPON-VALID 할인 1000 + 포인트 3000 = 4000 > 30% of 10000 = 3000
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "deliveryAddress": "서울시 강남구 테헤란로 123",
                    "couponId": "COUPON-VALID",
                    "pointAmountToUse": 3000
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISCOUNT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").exists());
    }
}
