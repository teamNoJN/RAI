package com.rai.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** user-service (:8081) — 인증 · 회사 · 사용자. */
// com.rai.common 의 공통 에러 핸들러까지 스캔한다.
@SpringBootApplication(scanBasePackages = {"com.rai.user", "com.rai.common"})
public class RaiUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaiUserServiceApplication.class, args);
    }
}
