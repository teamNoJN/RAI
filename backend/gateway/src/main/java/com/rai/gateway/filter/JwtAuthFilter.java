package com.rai.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * JWT 를 검증하고 사용자 정보를 헤더로 바꿔 다운스트림에 넘긴다 (README 인증 구조).
 *
 * <pre>
 * [FE] Authorization: Bearer ...
 *   → [Gateway] 서명·만료·타입 검증
 *   → X-User-Id / X-Company-Id 주입
 *   → [서비스] @CurrentUser 로 수령
 * </pre>
 *
 * <p>서명키는 user-service 의 발급키와 같은 값이어야 한다.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    /**
     * 검증 없이 통과시키는 경로.
     *
     * <p>★ refresh 를 빼면 안 된다. access token 이 만료된 상태에서 호출되는 엔드포인트라
     * 빠뜨리면 토큰 갱신이 영구히 불가능해지고 명세 1E "세션 만료"에서 못 빠져나온다.
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/signup",
            "/api/auth/refresh");

    private static final String BEARER_PREFIX = "Bearer ";

    // user-service 의 JwtProvider 가 넣는 claim 이름 (sub 는 표준 claim).
    private static final String CLAIM_COMPANY_ID = "company_id";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private final SecretKey key;

    public JwtAuthFilter(@Value("${rai.jwt.secret}") String secret) {
        // HS256 은 32바이트 이상을 요구한다. 짧으면 여기서 기동이 실패해 배포 전에 잡힌다.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // ★ 클라이언트가 직접 보낸 인증 헤더는 무조건 버린다.
        //   서비스는 이 헤더를 검증 없이 신뢰하므로(common/security/CurrentUserArgumentResolver),
        //   지우지 않으면 X-Company-Id 를 위조해 남의 회사 데이터를 읽을 수 있다.
        //   README 데이터 모델의 "모든 조회에 company_id 조건" 규칙이 여기서 무너진다.
        ServerHttpRequest.Builder sanitized = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(AuthHeaders.USER_ID);
                    headers.remove(AuthHeaders.COMPANY_ID);
                    headers.remove(AuthHeaders.ROLE);
                });

        // CORS preflight 는 Authorization 을 달고 오지 않는다. 여기서 401 을 내면 브라우저가 본 요청을 못 보낸다.
        String path = exchange.getRequest().getURI().getPath();
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod()) || PUBLIC_PATHS.contains(path)) {
            return chain.filter(exchange.mutate().request(sanitized.build()).build());
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "인증 토큰이 없습니다.");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(authorization.substring(BEARER_PREFIX.length()))
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, "유효하지 않은 토큰입니다.");
        }

        // refresh 토큰으로 일반 API 를 부르지 못하게 막는다.
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            return unauthorized(exchange, "access 토큰이 필요합니다.");
        }

        String userId = claims.getSubject();
        String companyId = claims.get(CLAIM_COMPANY_ID, String.class);
        if (userId == null || companyId == null) {
            return unauthorized(exchange, "토큰에 사용자 정보가 없습니다.");
        }

        ServerHttpRequest request = sanitized
                .header(AuthHeaders.USER_ID, userId)
                .header(AuthHeaders.COMPANY_ID, companyId)
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /** 공통 에러 규약 {@code {"error":{"code":"...","message":"..."}}} 를 그대로 따른다 (README API 공통 규약). */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
