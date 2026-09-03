package com.rai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 규제 KB · 보고서 API.
 *
 * <p>common 모듈(com.rai.common)이 같은 베이스 패키지 아래라 인증 리졸버가 자동으로 붙는다.
 * common 의 에러 어드바이스도 함께 스캔되지만, ReportExceptionHandler 가
 * HIGHEST_PRECEDENCE 라 응답 형식은 기존 그대로다.
 */
@SpringBootApplication
public class RaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaiApplication.class, args);
    }

}
