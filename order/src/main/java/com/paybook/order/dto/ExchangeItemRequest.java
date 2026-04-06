package com.paybook.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ExchangeItemRequest(

        @NotBlank(message = "주문 ID는 필수입니다")
        String orderId,

        @NotBlank(message = "기존 상품 ID는 필수입니다")
        String originalProductId,

        @NotBlank(message = "교환 상품 ID는 필수입니다")
        String newProductId,

        @Min(value = 1, message = "수량은 1 이상이어야 합니다")
        int newQuantity
) {}
