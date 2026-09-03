package com.rai.common.exception;

import lombok.Getter;

/** 서비스 로직에서 던지는 공통 예외. ErrorCode 가 HTTP 상태와 code 를 결정한다. */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    /** 화면 문구가 명세에 고정된 경우 메시지를 직접 준다. */
    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
