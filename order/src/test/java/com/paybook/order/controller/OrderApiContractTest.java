package com.paybook.order.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("주문 API 계약 슬라이스 테스트 — request/response 형식 + Bean Validation 검증")
class OrderApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 유효한 요청 JSON (price는 백엔드에서 결정하므로 요청에 포함하지 않음)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private static final String VALID_ORDER_REQUEST = """
            {
                "userId": "USER-001",
                "items": [
                    {
                        "productId": "PROD-001",
                        "quantity": 2
                    },
                    {
                        "productId": "PROD-002",
                        "quantity": 1
                    }
                ],
                "deliveryAddress": "서울시 강남구 테헤란로 123"
            }
            """;

    // 생성된 주문 ID를 테스트 간 공유 (순서 의존 테스트용)
    private static String createdOrderId;


    // ================================================================
    // 1. 주문 생성 성공 — 201 Created + 응답 스키마 검증
    // ================================================================
    @Test
    @Order(1)
    @DisplayName("POST /api/orders → 201: 유효한 요청 시 응답 스키마가 올바르다")
    void createOrder_ValidRequest_Returns201WithCorrectSchema() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_ORDER_REQUEST)
                )
                .andDo(print())
                .andExpect(status().isCreated())

                // ── 응답 스키마 검증 ──
                .andExpect(jsonPath("$.orderId").exists())
                .andExpect(jsonPath("$.orderId").isString())
                .andExpect(jsonPath("$.userId").value("USER-001"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalAmount").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").exists())

                // ── items 내부 스키마 검증 (price는 백엔드가 채운 값) ──
                .andExpect(jsonPath("$.items[0].productId").value("PROD-001"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].price").isNumber())
                .andReturn();

        // 다음 테스트(조회/취소)에서 사용할 orderId 저장
        String responseBody = result.getResponse().getContentAsString();
        createdOrderId = objectMapper.readTree(responseBody).get("orderId").asString();
    }


    // ================================================================
    // 2. 주문 생성 실패 — userId 누락 시 400
    // ================================================================
    @Test
    @Order(2)
    @DisplayName("POST /api/orders → 400: userId 누락 시 INVALID_REQUEST")
    void createOrder_MissingUserId_Returns400() throws Exception {
        String requestWithoutUserId = """
                {
                    "items": [
                        {
                            "productId": "PROD-001",
                            "quantity": 1
                        }
                    ]
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestWithoutUserId)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 3. 주문 생성 실패 — 수량 0 시 400
    // ================================================================
    @Test
    @Order(3)
    @DisplayName("POST /api/orders → 400: 수량이 0이면 검증 실패")
    void createOrder_InvalidQuantity_Returns400() throws Exception {
        String requestWithZeroQuantity = """
                {
                    "userId": "USER-001",
                    "items": [
                        {
                            "productId": "PROD-001",
                            "quantity": 0
                        }
                    ]
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestWithZeroQuantity)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 4. 주문 생성 실패 — items 빈 배열 시 400
    // ================================================================
    @Test
    @Order(4)
    @DisplayName("POST /api/orders → 400: items가 빈 배열이면 검증 실패")
    void createOrder_EmptyItems_Returns400() throws Exception {
        String requestWithEmptyItems = """
                {
                    "userId": "USER-001",
                    "items": [],
                    "deliveryAddress": "서울시 강남구 테헤란로 123"
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestWithEmptyItems)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 5. 주문 생성 실패 — 포인트 사용 금액 음수 시 400
    // ================================================================
    @Test
    @Order(5)
    @DisplayName("POST /api/orders → 400: 포인트 사용 금액이 음수이면 검증 실패")
    void createOrder_NegativePointAmount_Returns400() throws Exception {
        String requestWithNegativePoints = """
                {
                    "userId": "USER-001",
                    "items": [
                        {
                            "productId": "PROD-001",
                            "quantity": 1
                        }
                    ],
                    "deliveryAddress": "서울시 강남구 테헤란로 123",
                    "pointAmountToUse": -1000
                }
                """;

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestWithNegativePoints)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isString());
    }


    // ================================================================
    // 6. 주문 생성 실패 — 잘못된 JSON 형식 시 400
    // ================================================================
    @Test
    @Order(6)
    @DisplayName("POST /api/orders → 400: 잘못된 JSON 형식이면 파싱 에러")
    void createOrder_MalformedJson_Returns400() throws Exception {
        String malformedJson = "{ invalid json }";

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(malformedJson)
                )
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"))
                .andExpect(jsonPath("$.message").exists());
    }


    // ================================================================
    // 7. 주문 생성 실패 — Content-Type 누락 시 415
    // ================================================================
    @Test
    @Order(7)
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
    // 8. 주문 조회 성공 — 200 OK + 응답 스키마 검증
    // ================================================================
    @Test
    @Order(8)
    @DisplayName("GET /api/orders/{orderId} → 200: 존재하는 주문 조회 시 올바른 응답")
    void getOrder_ExistingOrder_Returns200() throws Exception {
        Assertions.assertNotNull(createdOrderId, "테스트 1에서 주문이 생성되어야 합니다");

        mockMvc.perform(
                        get("/api/orders/{orderId}", createdOrderId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isOk())

                // ── 조회 응답 스키마 검증 ──
                .andExpect(jsonPath("$.orderId").value(createdOrderId))
                .andExpect(jsonPath("$.userId").value("USER-001"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalAmount").isNumber())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.createdAt").exists());
    }


    // ================================================================
    // 9. 주문 조회 실패 — 존재하지 않는 주문 404
    // ================================================================
    @Test
    @Order(9)
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
    // 10. 주문 취소 성공 → 중복 취소 시 409
    // ================================================================
    @Test
    @Order(10)
    @DisplayName("PATCH /api/orders/{orderId}/cancel → 200 후 재취소 시 409 Conflict")
    void cancelOrder_ThenCancelAgain_Returns409() throws Exception {
        Assertions.assertNotNull(createdOrderId, "테스트 1에서 주문이 생성되어야 합니다");

        // ── 1단계: 최초 취소 → 200 OK ──
        mockMvc.perform(
                        patch("/api/orders/{orderId}/cancel", createdOrderId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(createdOrderId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // ── 2단계: 중복 취소 → 409 Conflict ──
        mockMvc.perform(
                        patch("/api/orders/{orderId}/cancel", createdOrderId)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_CANCELLED"))
                .andExpect(jsonPath("$.message").exists());
    }


    // ================================================================
    // 11. 주문 취소 실패 — 존재하지 않는 주문 404
    // ================================================================
    @Test
    @Order(11)
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
    // 12. 주문 생성 실패 — 재고 부족 시 409
    // ================================================================
    @Test
    @Order(12)
    @DisplayName("POST /api/orders → 409: 재고 부족 시 OUT_OF_STOCK 에러")
    void createOrder_OutOfStock_Returns409() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [
                        {
                            "productId": "PROD-001",
                            "quantity": 999999
                        }
                    ],
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
    // 13. 주문 생성 실패 — 이미 사용된 쿠폰 409
    // ================================================================
    @Test
    @Order(13)
    @DisplayName("POST /api/orders → 409: 이미 사용된 쿠폰 적용 시 COUPON_ALREADY_USED 에러")
    void createOrder_CouponAlreadyUsed_Returns409() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [
                        {
                            "productId": "PROD-001",
                            "quantity": 1
                        }
                    ],
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
    // 14. 주문 생성 실패 — 만료된 쿠폰 409
    // ================================================================
    @Test
    @Order(14)
    @DisplayName("POST /api/orders → 409: 만료된 쿠폰 적용 시 COUPON_EXPIRED 에러")
    void createOrder_CouponExpired_Returns409() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [
                        {
                            "productId": "PROD-001",
                            "quantity": 1
                        }
                    ],
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
    // 15. 주문 생성 실패 — 존재하지 않는 쿠폰 404
    // ================================================================
    @Test
    @Order(15)
    @DisplayName("POST /api/orders → 404: 존재하지 않는 쿠폰 ID 시 COUPON_NOT_FOUND 에러")
    void createOrder_CouponNotFound_Returns404() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [
                        {
                            "productId": "PROD-001",
                            "quantity": 1
                        }
                    ],
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
    // 16. 주문 생성 실패 — 포인트 잔액 부족 409
    // ================================================================
    @Test
    @Order(16)
    @DisplayName("POST /api/orders → 409: 포인트 잔액 부족 시 POINTS_UNAVAILABLE 에러")
    void createOrder_PointsUnavailable_Returns409() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [
                        {
                            "productId": "PROD-001",
                            "quantity": 1
                        }
                    ],
                    "deliveryAddress": "서울시 강남구 테헤란로 123",
                    "pointAmountToUse": 999999
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
    // 17. 주문 생성 성공 — 쿠폰 + 포인트 동시 적용
    // ================================================================
    @Test
    @Order(17)
    @DisplayName("POST /api/orders → 201: 쿠폰과 포인트를 동시에 적용한 주문 생성 성공")
    void createOrder_WithCouponAndPoints_Returns201() throws Exception {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [
                        {
                            "productId": "PROD-001",
                            "quantity": 1
                        }
                    ],
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
                .andExpect(jsonPath("$.orderId").isString())
                .andExpect(jsonPath("$.userId").value("USER-001"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalAmount").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").exists());
    }
}
