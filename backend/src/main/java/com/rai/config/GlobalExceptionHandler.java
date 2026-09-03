package com.rai.config;

import com.rai.common.ApiResponse;
import com.rai.common.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
// 순서 명시: 전용 핸들러(HIGHEST, report/검수콘솔) > 이 핸들러(봉투, KB 운영 API) > common ApiExceptionHandler({error:}).
// 둘 다 무순위 전역이면 어느 형식으로 나갈지 빈 등록 순서에 의존한다 — 결정화가 목적.
@Order(0)
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * CurrentUser 리졸버(401 등)가 던지는 공통 예외 — @Order(0) 로 이 핸들러가 common 보다
     * 먼저 잡게 됐으므로, catch-all(500) 로 삼키지 않도록 상태 코드를 보존해 넘긴다.
     */
    @ExceptionHandler(com.rai.common.exception.ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(com.rai.common.exception.ApiException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        // PRD 운영 요구사항: 기술 오류 원문을 사용자에게 노출하지 않음
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("요청 처리 중 오류가 발생했습니다. 다시 시도해주세요."));
    }
}
