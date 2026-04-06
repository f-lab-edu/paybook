package com.paybook.settlement.dto;

public record ErrorResponse(
        String code,
        String message
) {
}
