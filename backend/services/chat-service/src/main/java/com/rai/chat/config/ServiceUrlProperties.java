package com.rai.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** rai.services.* — 서비스 간 내부 호출 대상 주소. */
@ConfigurationProperties(prefix = "rai.services")
public record ServiceUrlProperties(String drugUrl, String regulationUrl) {
}
