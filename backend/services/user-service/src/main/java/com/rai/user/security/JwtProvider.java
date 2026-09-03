package com.rai.user.security;

import com.rai.common.security.JwtProperties;
import com.rai.common.security.JwtVerifier;
import com.rai.user.entity.AppUser;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 발급. user-service 만 발급하고, 검증은 common 의 {@link JwtVerifier} 가 한다
 * (Gateway 도 같은 비밀키로 검증 — docs/00-project-plan.md 4-5).
 *
 * <p>claims: sub=user_id · company_id · email · typ(access|refresh)
 */
@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtProperties properties;
    private final JwtVerifier verifier;

    public String createAccessToken(AppUser user) {
        return build(user, JwtVerifier.TYPE_ACCESS, properties.accessTokenTtl());
    }

    public String createRefreshToken(AppUser user) {
        return build(user, JwtVerifier.TYPE_REFRESH, properties.refreshTokenTtl());
    }

    /** refresh 토큰에서 user_id 를 꺼낸다. 서명·만료·타입이 어긋나면 JwtException. */
    public UUID parseRefreshTokenUserId(String token) {
        return UUID.fromString(verifier.parse(token, JwtVerifier.TYPE_REFRESH).getSubject());
    }

    private String build(AppUser user, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim(JwtVerifier.CLAIM_COMPANY_ID, user.getCompany().getCompanyId().toString())
                .claim(JwtVerifier.CLAIM_EMAIL, user.getEmail())
                .claim(JwtVerifier.CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(verifier.key())
                .compact();
    }
}
