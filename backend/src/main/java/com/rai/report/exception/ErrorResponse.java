package com.rai.report.exception;

/** 명세 공통 에러 형식: {"error":{"code","message"}} */
public record ErrorResponse(Body error) {

    public record Body(String code, String message) {
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new Body(code, message));
    }
}
