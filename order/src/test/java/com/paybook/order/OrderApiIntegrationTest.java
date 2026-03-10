package com.paybook.order;

import com.paybook.core.entity.*;
import com.paybook.core.repository.CouponRepository;
import com.paybook.core.repository.ProductRepository;
import com.paybook.core.repository.UserPointRepository;
import com.paybook.order.dto.ErrorResponse;
import com.paybook.order.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DisplayName("주문 API 통합 테스트 — 실제 HTTP 요청을 통한 전체 파이프라인 검증")
class OrderApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserPointRepository userPointRepository;

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

    @BeforeEach
    void setUpData() {
        if (productRepository.findByProductId("PROD-001").isEmpty()) {
            productRepository.save(new ProductEntity("PROD-001", "상품1", 10000, 100));
        }
        if (productRepository.findByProductId("PROD-002").isEmpty()) {
            productRepository.save(new ProductEntity("PROD-002", "상품2", 10000, 100));
        }
        if (productRepository.findByProductId("PROD-OOS").isEmpty()) {
            productRepository.save(new ProductEntity("PROD-OOS", "재고없음", 10000, 0));
        }

        if (couponRepository.findByCouponId("USED").isEmpty()) {
            couponRepository.save(new CouponEntity("USED", CouponStatus.USED,
                    CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1000, null));
        }
        if (couponRepository.findByCouponId("EXPIRED").isEmpty()) {
            couponRepository.save(new CouponEntity("EXPIRED", CouponStatus.EXPIRED,
                    CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1000, null));
        }

        if (userPointRepository.findByUserId("USER-001").isEmpty()) {
            userPointRepository.save(new UserPointEntity("USER-001", 100000));
        }
    }

    private <T> ResponseEntity<T> postOrder(String body, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/api/orders", HttpMethod.POST,
                new HttpEntity<>(body, headers),
                responseType
        );
    }

    private String createOrderAndGetId() {
        ResponseEntity<OrderResponse> response = postOrder(VALID_ORDER_REQUEST, OrderResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().orderId();
    }


    // ================================================================
    // 주문 생성 성공 — 201 Created + 응답 검증
    // ================================================================
    @Test
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
        assertThat(body.couponDiscountAmount()).isEqualTo(0);
        assertThat(body.pointDiscountAmount()).isEqualTo(0);
        assertThat(body.deliveryFee()).isGreaterThanOrEqualTo(0);
        assertThat(body.pgPaymentAmount()).isEqualTo(body.totalAmount() + body.deliveryFee());
        assertThat(body.items().getFirst().itemStatus()).isEqualTo("ACTIVE");
        assertThat(body.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(body.createdAt()).isNotBlank();
    }


    // ================================================================
    // 주문 생성 실패 — userId 누락 시 400
    // ================================================================
    @Test
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
    // 주문 생성 실패 — 수량 0 시 400
    // ================================================================
    @Test
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
    // 주문 생성 실패 — items 빈 배열 시 400
    // ================================================================
    @Test
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
    // 주문 생성 실패 — 포인트 사용 금액 음수 시 400
    // ================================================================
    @Test
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
    // 주문 생성 실패 — 잘못된 JSON 형식 시 400
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 400: 잘못된 JSON 형식이면 파싱 에러")
    void createOrder_MalformedJson_Returns400() {
        ResponseEntity<ErrorResponse> response = postOrder("{ invalid json }", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_JSON");
    }


    // ================================================================
    // 주문 생성 실패 — Content-Type text/plain 시 415
    // ================================================================
    @Test
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
    // 주문 조회 성공 — 200 OK
    // ================================================================
    @Test
    @DisplayName("GET /api/orders/{orderId} → 200: 존재하는 주문 조회 성공")
    void getOrder_ExistingOrder_Returns200() {
        String orderId = createOrderAndGetId();

        ResponseEntity<OrderResponse> response = restTemplate.getForEntity(
                "/api/orders/{orderId}", OrderResponse.class, orderId
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.orderId()).isEqualTo(orderId);
        assertThat(body.userId()).isEqualTo("USER-001");
        assertThat(body.items()).isNotEmpty();
        assertThat(body.totalAmount()).isGreaterThan(0);
        assertThat(body.couponDiscountAmount()).isGreaterThanOrEqualTo(0);
        assertThat(body.pointDiscountAmount()).isGreaterThanOrEqualTo(0);
        assertThat(body.pgPaymentAmount()).isGreaterThan(0);
        assertThat(body.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(body.createdAt()).isNotBlank();
    }


    // ================================================================
    // 주문 조회 실패 — 존재하지 않는 주문 404
    // ================================================================
    @Test
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
    // 주문 취소 성공 → 중복 취소 시 409
    // ================================================================
    @Test
    @DisplayName("PATCH /api/orders/{orderId}/cancel → 200 후 재취소 시 409 Conflict")
    void cancelOrder_ThenCancelAgain_Returns409() {
        String orderId = createOrderAndGetId();

        // 최초 취소 → 200 OK
        ResponseEntity<OrderResponse> cancelResponse = restTemplate.exchange(
                "/api/orders/{orderId}/cancel", HttpMethod.PATCH,
                HttpEntity.EMPTY, OrderResponse.class, orderId
        );

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResponse.getBody()).isNotNull();
        assertThat(cancelResponse.getBody().orderId()).isEqualTo(orderId);
        assertThat(cancelResponse.getBody().status()).isEqualTo("CANCELLED");

        // 중복 취소 → 409 Conflict
        ResponseEntity<ErrorResponse> duplicateResponse = restTemplate.exchange(
                "/api/orders/{orderId}/cancel", HttpMethod.PATCH,
                HttpEntity.EMPTY, ErrorResponse.class, orderId
        );

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateResponse.getBody()).isNotNull();
        assertThat(duplicateResponse.getBody().code()).isEqualTo("ORDER_ALREADY_CANCELLED");
    }


    // ================================================================
    // 주문 취소 실패 — 존재하지 않는 주문 404
    // ================================================================
    @Test
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
    // 주문 생성 실패 — 재고 부족 시 409
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 409: 재고 부족 시 OUT_OF_STOCK 에러")
    void createOrder_OutOfStock_Returns409() {
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-OOS", "quantity": 1 }],
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
    // 주문 생성 실패 — 이미 사용된 쿠폰 409
    // ================================================================
    @Test
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
    // 주문 생성 실패 — 만료된 쿠폰 409
    // ================================================================
    @Test
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
    // 주문 생성 실패 — 존재하지 않는 쿠폰 404
    // ================================================================
    @Test
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
    // 주문 생성 실패 — 포인트 잔액 부족 409
    // ================================================================
    @Test
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
    // 주문 생성 성공 — 쿠폰 + 포인트 동시 적용
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 201: 쿠폰과 포인트를 동시에 적용한 주문 생성 성공")
    void createOrder_WithCouponAndPoints_Returns201() {
        if (couponRepository.findByCouponId("COUPON-COMBO").isEmpty()) {
            couponRepository.save(new CouponEntity("COUPON-COMBO", CouponStatus.ACTIVE,
                    CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 1000, null));
        }

        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "deliveryAddress": "서울시 강남구 테헤란로 123",
                    "couponId": "COUPON-COMBO",
                    "pointAmountToUse": 1000
                }
                """;

        ResponseEntity<OrderResponse> response = postOrder(request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.orderId()).startsWith("ORD-");
        assertThat(body.userId()).isEqualTo("USER-001");
        assertThat(body.totalAmount()).isEqualTo(10000);
        assertThat(body.couponDiscountAmount()).isEqualTo(1000);
        assertThat(body.pointDiscountAmount()).isEqualTo(1000);
        // PG = 상품금액 - 할인 + 배송비 = 10000 - 1000 - 1000 + 3000
        assertThat(body.deliveryFee()).isEqualTo(3000);
        assertThat(body.pgPaymentAmount()).isEqualTo(11000);
        assertThat(body.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(body.createdAt()).isNotBlank();
    }


    // ================================================================
    // 할인 한도 초과 — 409
    // ================================================================
    @Test
    @DisplayName("POST /api/orders → 409: 할인 한도 초과 시 DISCOUNT_LIMIT_EXCEEDED 에러")
    void createOrder_DiscountLimitExceeded_Returns409() {
        if (couponRepository.findByCouponId("COUPON-LIMIT").isEmpty()) {
            couponRepository.save(new CouponEntity("COUPON-LIMIT", CouponStatus.ACTIVE,
                    CouponType.PLATFORM, DiscountType.FIXED_AMOUNT, 2000, null));
        }

        // 쿠폰 2000 + 포인트 2000 = 4000 > 30% of 10000 = 3000
        String request = """
                {
                    "userId": "USER-001",
                    "items": [{ "productId": "PROD-001", "quantity": 1 }],
                    "couponId": "COUPON-LIMIT",
                    "pointAmountToUse": 2000
                }
                """;

        ResponseEntity<ErrorResponse> response = postOrder(request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DISCOUNT_LIMIT_EXCEEDED");
    }
}
