package com.rai.common.security;

import com.rai.common.exception.ApiException;
import com.rai.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * 컨트롤러의 {@link CurrentUser} 파라미터를 채운다.
 *
 * <ol>
 *   <li>Gateway 를 거쳐 왔으면 X-User-Id / X-Company-Id 헤더를 신뢰한다 (README 인증 구조)</li>
 *   <li>헤더가 없으면 Authorization: Bearer 토큰을 같은 비밀키로 직접 검증한다
 *       — Gateway 배포 전 구간에서도 서비스가 단독 동작하도록</li>
 *   <li>둘 다 없으면 401</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        CurrentUser fromGateway = fromHeaders(webRequest);
        return fromGateway != null ? fromGateway : fromBearerToken(webRequest);
    }

    private CurrentUser fromHeaders(NativeWebRequest request) {
        String userId = request.getHeader(AuthHeaders.USER_ID);
        String companyId = request.getHeader(AuthHeaders.COMPANY_ID);
        if (isBlank(userId) || isBlank(companyId)) {
            return null;
        }
        try {
            return new CurrentUser(UUID.fromString(userId), UUID.fromString(companyId), null);
        } catch (IllegalArgumentException e) {
            throw unauthorized();
        }
    }

    private CurrentUser fromBearerToken(NativeWebRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw unauthorized();
        }
        try {
            Claims claims = jwtVerifier.parse(
                    authorization.substring(BEARER_PREFIX.length()), JwtVerifier.TYPE_ACCESS);
            return new CurrentUser(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get(JwtVerifier.CLAIM_COMPANY_ID, String.class)),
                    claims.get(JwtVerifier.CLAIM_EMAIL, String.class));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw unauthorized();
        }
    }

    private ApiException unauthorized() {
        return new ApiException(ErrorCode.UNAUTHORIZED);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
