package com.rai.drug.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** rai.services.* — 서비스 간 내부 호출 대상 주소. compose/k8s 에서는 서비스명으로 바뀐다. */
@ConfigurationProperties(prefix = "rai.services")
public record ServiceUrlProperties(String chatUrl) {
}
