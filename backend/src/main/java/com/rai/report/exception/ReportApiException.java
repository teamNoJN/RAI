package com.rai.report.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 5·5L 엔드포인트가 명세대로 error.code / error.message 를 내보내기 위한 예외. */
@Getter
public class ReportApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ReportApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ReportApiException notFound(String code, String message) {
        return new ReportApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ReportApiException badRequest(String code, String message) {
        return new ReportApiException(HttpStatus.BAD_REQUEST, code, message);
    }
}
