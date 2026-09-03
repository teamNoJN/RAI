package com.rai.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 토큰 검증만 담당 (발급은 user-service). Gateway 가 앞에 서면 이 경로는 안 타지만,
 * Gateway 없이 서비스를 직접 호출하는 로컬 개발 구간에서 같은 검증을 보장한다.
 */
@Component
public class JwtVerifier {

    public static final String CLAIM_COMPANY_ID = "company_id";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;

    public JwtVerifier(JwtProperties properties) {
        // HS256 은 32바이트 이상을 요구한다. 짧으면 여기서 기동이 실패해 배포 전에 잡힌다.
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public SecretKey key() {
        return key;
    }

    /** 서명·만료·타입을 검증하고 claims 를 돌려준다. 어긋나면 JwtException. */
    public Claims parse(String token, String expectedType) {
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
