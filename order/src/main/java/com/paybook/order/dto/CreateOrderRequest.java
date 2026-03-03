package com.paybook.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 주문 생성 요청 DTO
 *
 * record를 사용하여 불변 객체로 정의.
 * Bean Validation으로 요청 형식을 강제하여
 * 컨트롤러 진입 전에 잘못된 요청을 걸러냄.
 */
public record CreateOrderRequest(

        @NotBlank(message = "사용자 ID는 필수입니다")
        String userId,

        @NotEmpty(message = "주문 상품은 최소 1개 이상이어야 합니다")
        @Valid
        List<OrderItemRequest> items,

        String deliveryAddress,

        String couponId,

        @PositiveOrZero(message = "포인트 사용 금액은 0 이상이어야 합니다")
        Integer pointAmountToUse

) {
    public record OrderItemRequest(

            @NotBlank(message = "상품 ID는 필수입니다")
            String productId,

            @Min(value = 1, message = "수량은 1 이상이어야 합니다")
            int quantity
    ) {}
}
