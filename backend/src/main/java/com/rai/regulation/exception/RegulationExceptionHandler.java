package com.rai.regulation.exception;

import com.rai.regulation.controller.RegulationReviewController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * screen-06 검수 콘솔({@link RegulationReviewController}) 전용 에러 핸들러.
 *
 * 기존 {@code com.rai.config.GlobalExceptionHandler} 는 운영자용 KB 관리 API 의 ApiResponse
 * 봉투를 쓰는데, 이 화면은 FE 계약(명세 공통 에러 {"error":{"code","message"}})을 따라야 하므로
 * assignableTypes 로 이 컨트롤러에만 좁혀 적용한다 — 기존 RegulationController 동작에는 영향 없음.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = RegulationReviewController.class)
public class RegulationExceptionHandler {

    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    @ExceptionHandler(RegulationApiException.class)
    public ResponseEntity<ErrorResponse> handleRegulationApi(RegulationApiException e) {
        return ResponseEntity.status(e.getStatus()).body(ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ErrorResponse.of(VALIDATION_ERROR, message));
    }

    /** 경로의 {regulationId} 가 UUID 가 아닌 경우 — 500 이 아니라 400 이다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(VALIDATION_ERROR,
                e.getName() + " 값이 올바르지 않습니다: " + e.getValue()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("검수 콘솔 API 처리 중 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "요청 처리 중 오류가 발생했습니다. 다시 시도해주세요."));
    }
}
