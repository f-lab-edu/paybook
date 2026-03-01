package com.paybook.order.dto;

/**
 * 에러 응답 DTO
 *
 * 모든 에러 응답은 이 형식을 따른다.
 * 프론트엔드에서 에러 핸들링할 때 일관된 구조를 보장.
 */
public record ErrorResponse(
        String code,
        String message
) {}
