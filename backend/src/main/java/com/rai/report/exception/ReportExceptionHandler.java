package com.rai.report.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * report 패키지 전용 에러 핸들러.
 *
 * 기존 {@code com.rai.config.GlobalExceptionHandler} 는 운영자용 API 의 ApiResponse 봉투를 쓰는데,
 * 5·5L 은 FE 계약(명세)을 따라야 하므로 이 어드바이스를 우선 적용한다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.rai.report")
public class ReportExceptionHandler {

    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    @ExceptionHandler(ReportApiException.class)
    public ResponseEntity<ErrorResponse> handleReportApi(ReportApiException e) {
        return ResponseEntity.status(e.getStatus()).body(ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ErrorResponse.of(VALIDATION_ERROR, message));
    }

    /** 경로의 {report_id} 가 UUID 가 아닌 경우 — 500 이 아니라 400 이다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(VALIDATION_ERROR,
                e.getName() + " 값이 올바르지 않습니다: " + e.getValue()));
    }

    /** 본문 JSON 이 깨졌거나 타입이 안 맞는 경우. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(VALIDATION_ERROR, "요청 본문을 해석할 수 없습니다"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        // 404(없는 경로)·405(잘못된 메서드) 같은 MVC 자체 오류까지 500 으로 뭉개지 않는다.
        if (e instanceof org.springframework.web.ErrorResponse mvcError) {
            HttpStatusCode status = mvcError.getStatusCode();
            if (status.is4xxClientError()) {
                String code = status.value() == HttpStatus.NOT_FOUND.value() ? "NOT_FOUND" : VALIDATION_ERROR;
                return ResponseEntity.status(status).body(ErrorResponse.of(code, e.getMessage()));
            }
        }
        log.error("보고서 API 처리 중 오류", e);
        // PRD 운영 요구사항: 기술 오류 원문을 사용자에게 노출하지 않음
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "요청 처리 중 오류가 발생했습니다. 다시 시도해주세요."));
    }
}
