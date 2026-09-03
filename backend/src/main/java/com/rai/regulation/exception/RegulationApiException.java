package com.rai.regulation.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** screen-06 검수 콘솔 엔드포인트가 명세대로 error.code / error.message 를 내보내기 위한 예외. */
@Getter
public class RegulationApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public RegulationApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static RegulationApiException notFound(String message) {
        return new RegulationApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static RegulationApiException conflict(String message) {
        return new RegulationApiException(HttpStatus.CONFLICT, "ALREADY_REFLECTED", message);
    }

    public static RegulationApiException unauthorized(String message) {
        return new RegulationApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
