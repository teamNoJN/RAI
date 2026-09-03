package com.rai.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * rai.jwt.* — 전 서비스 공유 설정.
 * secret 은 user-service(발급) · Gateway(검증) · 각 서비스(폴백 검증)가 같은 값을 써야 한다.
 * TTL 은 발급하는 user-service 만 의미가 있다.
 */
@ConfigurationProperties(prefix = "rai.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("1h") Duration accessTokenTtl,
        @DefaultValue("14d") Duration refreshTokenTtl) {
}
