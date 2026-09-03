package com.rai.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 공통 에러 코드. FE 는 code 로 분기하고 message 를 그대로 노출한다. */
@Getter
public enum ErrorCode {

    BAD_REQUEST(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다"),
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
