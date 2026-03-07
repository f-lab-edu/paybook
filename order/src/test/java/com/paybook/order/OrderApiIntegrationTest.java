package com.paybook.order;

import com.paybook.order.dto.ErrorResponse;
import com.paybook.order.dto.OrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 API 통합 테스트
 *
 * 슬라이스 테스트(@WebMvcTest)와의 차이점:
 * - @SpringBootTest로 전체 애플리케이션 컨텍스트를 로드
 * - RANDOM_PORT로 실제 임베디드 서버를 기동
 * - TestRestTemplate으로 실제 HTTP 요청/응답을 검증
 * - 전체 파이프라인(HTTP → 서버 → 필터 → DispatcherServlet → 컨트롤러 → 직렬화)을 통합 검증
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("주문 API 통합 테스트 — 실제 HTTP 요청을 통한 전체 파이프라인 검증")
class OrderApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static String createdOrderId;

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

    /**
     * JSON Content-Type 헤더로 POST /api/orders 요청을 보내는 헬퍼
     */
    private <T> ResponseEntity<T> postOrder(String body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/api/orders", HttpMethod.POST,
                new HttpEntity<>(body, headers),
                responseType
        );
    }


    // ================================================================
    // 1. 주문 생성 성공 — 201 Created + 응답 검증
    // ================================================================
    @Test
    @Order(1)
    @DisplayName("POST /api/orders → 201: 유효한 요청으로 주문 생성 성공")
    void createOrder_ValidRequest_Returns201() {
        ResponseEntity<OrderResponse> response = postOrder(VALID_ORDER_REQUEST, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.orderId()).startsWith("ORD-");
        assertThat(body.userId()).isEqualTo("USER-001");
        assertThat(body.items()).hasSize(2);
        assertThat(body.items().getFirst().productId()).isEqualTo("PROD-001");
        assertThat(body.items().getFirst().quantity()).isEqualTo(2);
        assertThat(body.items().getFirst().price()).isGreaterThan(0);
        assertThat(body.totalAmount()).isGreaterThan(0);
        assertThat(body.status()).isEqualTo("PENDING");
        assertThat(body.createdAt()).isNotBlank();

        createdOrderId = body.orderId();
    }


    // ================================================================
    // 2. 주문 생성 실패 — userId 누락 시 400
    // ================================================================
    @Test
    @Order(2)
    @DisplayName("POST /api/orders → 400: userId 누락 시 INVALID_REQUEST")
    void createOrder_MissingUserId_Returns400() {
        String request = """
                {
                    "items": [{ "productId": "PROD-001", "quantity": 1 }]
                }
                """;

        ResponseEntity<ErrorResponse> response = postOrder(request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().message()).isNotBlank();
    }


    // ================================================================
    // 3. 주문 생성 실패 — 수량 0 시 400
    // ================================================================
    @Test
    @Order(3)
    @DisplayName("POST /api/orders → 400: 수량이 0이면 검증 실패")
    void createOrder_InvalidQuantity_Returns400() {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 0 }]
                }
                """;

        ResponseEntity<ErrorResponse> response = postOrder(request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    }


    // ================================================================
    // 4. 주문 생성 실패 — items 빈 배열 시 400
    // ================================================================
    @Test
    @Order(4)
    @DisplayName("POST /api/orders → 400: items가 빈 배열이면 검증 실패")
    void createOrder_EmptyItems_Returns400() {
        String request = """
                {
                    "userId": "USER-001",
                    "items": []
                }
                """;

        ResponseEntity<ErrorResponse> response = postOrder(request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    }


    // ================================================================
    // 5. 주문 생성 실패 — 포인트 사용 금액 음수 시 400
    // ================================================================
    @Test
    @Order(5)
    @DisplayName("POST /api/orders → 400: 포인트 사용 금액이 음수이면 검증 실패")
    void createOrder_NegativePointAmount_Returns400() {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "pointAmountToUse": -1000
                }
                """;

        ResponseEntity<ErrorResponse> response = postOrder(request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    }


    // ================================================================
    // 6. 주문 생성 실패 — 잘못된 JSON 형식 시 400
    // ================================================================
    @Test
    @Order(6)
    @DisplayName("POST /api/orders → 400: 잘못된 JSON 형식이면 파싱 에러")
    void createOrder_MalformedJson_Returns400() {
        ResponseEntity<ErrorResponse> response = postOrder("{ invalid json }", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_JSON");
    }


    // ================================================================
    // 7. 주문 생성 실패 — Content-Type text/plain 시 415
    // ================================================================
    @Test
    @Order(7)
    @DisplayName("POST /api/orders → 415: Content-Type이 JSON이 아니면 Unsupported Media Type")
    void createOrder_WrongContentType_Returns415() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/orders", HttpMethod.POST,
                new HttpEntity<>(VALID_ORDER_REQUEST, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }


    // ================================================================
    // 8. 주문 조회 성공 — 200 OK
    // ================================================================
    @Test
    @Order(8)
    @DisplayName("GET /api/orders/{orderId} → 200: 존재하는 주문 조회 성공")
    void getOrder_ExistingOrder_Returns200() {
        assertThat(createdOrderId).as("테스트 1에서 주문이 생성되어야 합니다").isNotNull();

        ResponseEntity<OrderResponse> response = restTemplate.getForEntity(
                "/api/orders/{orderId}", OrderResponse.class, createdOrderId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.orderId()).isEqualTo(createdOrderId);
        assertThat(body.userId()).isEqualTo("USER-001");
        assertThat(body.items()).isNotEmpty();
        assertThat(body.totalAmount()).isGreaterThan(0);
        assertThat(body.status()).isEqualTo("PENDING");
        assertThat(body.createdAt()).isNotBlank();
    }


    // ================================================================
    // 9. 주문 조회 실패 — 존재하지 않는 주문 404
    // ================================================================
    @Test
    @Order(9)
    @DisplayName("GET /api/orders/{orderId} → 404: 존재하지 않는 주문 조회 시 ORDER_NOT_FOUND")
    void getOrder_NonExistentOrder_Returns404() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity(
                "/api/orders/{orderId}", ErrorResponse.class, "NON-EXISTENT-ORDER-ID"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(response.getBody().message()).isNotBlank();
    }


    // ================================================================
    // 10. 주문 취소 성공 → 중복 취소 시 409
    // ================================================================
    @Test
    @Order(10)
    @DisplayName("PATCH /api/orders/{orderId}/cancel → 200 후 재취소 시 409 Conflict")
    void cancelOrder_ThenCancelAgain_Returns409() {
        assertThat(createdOrderId).as("테스트 1에서 주문이 생성되어야 합니다").isNotNull();

        // ── 1단계: 최초 취소 → 200 OK ──
        ResponseEntity<OrderResponse> cancelResponse = restTemplate.exchange(
                "/api/orders/{orderId}/cancel", HttpMethod.PATCH,
                HttpEntity.EMPTY, OrderResponse.class, createdOrderId
        );

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResponse.getBody()).isNotNull();
        assertThat(cancelResponse.getBody().orderId()).isEqualTo(createdOrderId);
        assertThat(cancelResponse.getBody().status()).isEqualTo("CANCELLED");

        // ── 2단계: 중복 취소 → 409 Conflict ──
        ResponseEntity<ErrorResponse> duplicateResponse = restTemplate.exchange(
                "/api/orders/{orderId}/cancel", HttpMethod.PATCH,
                HttpEntity.EMPTY, ErrorResponse.class, createdOrderId
        );

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateResponse.getBody()).isNotNull();
        assertThat(duplicateResponse.getBody().code()).isEqualTo("ORDER_ALREADY_CANCELLED");
    }


    // ================================================================
    // 11. 주문 취소 실패 — 존재하지 않는 주문 404
    // ================================================================
    @Test
    @Order(11)
    @DisplayName("PATCH /api/orders/{orderId}/cancel → 404: 존재하지 않는 주문 취소 시 ORDER_NOT_FOUND")
    void cancelOrder_NonExistentOrder_Returns404() {
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/orders/{orderId}/cancel", HttpMethod.PATCH,
                HttpEntity.EMPTY, ErrorResponse.class, "NON-EXISTENT-ORDER-ID"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ORDER_NOT_FOUND");
    }


    // ================================================================
    // 12. 주문 생성 실패 — 재고 부족 시 409
    // ================================================================
    @Test
    @Order(12)
    @DisplayName("POST /api/orders → 409: 재고 부족 시 OUT_OF_STOCK 에러")
    void createOrder_OutOfStock_Returns409() {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 999999 }],
                    "deliveryAddress": "서울시 강남구 테헤란로 123"
                }
                """;

        ResponseEntity<ErrorResponse> response = postOrder(request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("OUT_OF_STOCK");
        assertThat(response.getBody().message()).isNotBlank();
    }


    // ================================================================
    // 13. 주문 생성 실패 — 이미 사용된 쿠폰 409
    // ================================================================
    @Test
    @Order(13)
    @DisplayName("POST /api/orders → 409: 이미 사용된 쿠폰 적용 시 COUPON_ALREADY_USED 에러")
    void createOrder_CouponAlreadyUsed_Returns409() {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "couponId": "USED"
                }
                """;

        ResponseEntity<ErrorResponse> response = postOrder(request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("COUPON_ALREADY_USED");
    }


    // ================================================================
    // 14. 주문 생성 실패 — 만료된 쿠폰 409
    // ================================================================
    @Test
    @Order(14)
    @DisplayName("POST /api/orders → 409: 만료된 쿠폰 적용 시 COUPON_EXPIRED 에러")
    void createOrder_CouponExpired_Returns409() {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "couponId": "EXPIRED"
                }
                """;

        ResponseEntity<ErrorResponse> response = postOrder(request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("COUPON_EXPIRED");
    }


    // ================================================================
    // 15. 주문 생성 실패 — 존재하지 않는 쿠폰 404
    // ================================================================
    @Test
    @Order(15)
    @DisplayName("POST /api/orders → 404: 존재하지 않는 쿠폰 ID 시 COUPON_NOT_FOUND 에러")
    void createOrder_CouponNotFound_Returns404() {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "couponId": "INVALID"
                }
                """;

        ResponseEntity<ErrorResponse> response = postOrder(request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("COUPON_NOT_FOUND");
    }


    // ================================================================
    // 16. 주문 생성 실패 — 포인트 잔액 부족 409
    // ================================================================
    @Test
    @Order(16)
    @DisplayName("POST /api/orders → 409: 포인트 잔액 부족 시 POINTS_UNAVAILABLE 에러")
    void createOrder_PointsUnavailable_Returns409() {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "pointAmountToUse": 999999
                }
                """;

        ResponseEntity<ErrorResponse> response = postOrder(request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("POINTS_UNAVAILABLE");
    }


    // ================================================================
    // 17. 주문 생성 성공 — 쿠폰 + 포인트 동시 적용
    // ================================================================
    @Test
    @Order(17)
    @DisplayName("POST /api/orders → 201: 쿠폰과 포인트를 동시에 적용한 주문 생성 성공")
    void createOrder_WithCouponAndPoints_Returns201() {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "deliveryAddress": "서울시 강남구 테헤란로 123",
                    "couponId": "COUPON-VALID",
                    "pointAmountToUse": 1000
                }
                """;

        ResponseEntity<OrderResponse> response = postOrder(request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.orderId()).startsWith("ORD-");
        assertThat(body.userId()).isEqualTo("USER-001");
        assertThat(body.items()).hasSize(1);
        assertThat(body.totalAmount()).isGreaterThan(0);
        assertThat(body.status()).isEqualTo("PENDING");
        assertThat(body.createdAt()).isNotBlank();
    }
}
