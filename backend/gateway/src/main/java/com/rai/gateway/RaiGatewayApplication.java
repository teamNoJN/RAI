package com.rai.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway (:8080) — 외부에서 들어오는 유일한 진입점.
 *
 * <p>Eureka 를 쓰지 않는 대신 라우팅 대상은 컨테이너/Service 이름으로 고정한다
 * (docs/00-project-plan.md 0부). 그래서 Compose 와 K8s 에서 같은 설정이 그대로 동작한다.
 */
@SpringBootApplication
public class RaiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaiGatewayApplication.class, args);
    }
}
