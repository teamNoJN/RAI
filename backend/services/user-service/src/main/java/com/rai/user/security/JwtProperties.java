package com.rai.user.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * rai.jwt.* 설정. secret 은 Gateway 와 같은 값을 써야 검증이 통과한다
 * (운영에서는 환경변수 JWT_SECRET 으로 주입).
 */
@ConfigurationProperties(prefix = "rai.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
}
