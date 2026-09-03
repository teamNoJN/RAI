package com.rai.user.security;

import com.rai.user.entity.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 발급/검증. user-service 가 발급하고 Gateway 가 같은 비밀키로 검증한다
 * (docs/00-project-plan.md 4-5).
 *
 * <p>claims: sub=user_id · company_id · email · typ(access|refresh)
 */
@Component
public class JwtProvider {

    private static final String CLAIM_COMPANY_ID = "company_id";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtProvider(JwtProperties properties) {
        this.properties = properties;
        // HS256 은 32바이트 이상을 요구한다. 짧으면 여기서 기동이 실패해 배포 전에 잡힌다.
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(AppUser user) {
        return build(user, TYPE_ACCESS, properties.accessTokenTtl());
    }

    public String createRefreshToken(AppUser user) {
        return build(user, TYPE_REFRESH, properties.refreshTokenTtl());
    }

    /** access 토큰에서 user_id 를 꺼낸다. 서명·만료·타입이 어긋나면 예외. */
    public UUID parseAccessTokenUserId(String token) {
        return UUID.fromString(parse(token, TYPE_ACCESS).getSubject());
    }

    /** refresh 토큰에서 user_id 를 꺼낸다. */
    public UUID parseRefreshTokenUserId(String token) {
        return UUID.fromString(parse(token, TYPE_REFRESH).getSubject());
    }

    private String build(AppUser user, String type, java.time.Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim(CLAIM_COMPANY_ID, user.getCompany().getCompanyId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    private Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("토큰 종류가 올바르지 않습니다: " + expectedType + " 필요");
        }
        return claims;
    }
}
