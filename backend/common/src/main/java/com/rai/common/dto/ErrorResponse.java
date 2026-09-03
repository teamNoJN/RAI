package com.rai.common.dto;

/**
 * 전 서비스 공통 에러 계약 (docs/api-spec/README.md).
 * <pre>{ "error": { "code": "UNAUTHORIZED", "message": "..." } }</pre>
 */
public record ErrorResponse(Body error) {

    public record Body(String code, String message) {}

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new Body(code, message));
    }
}
