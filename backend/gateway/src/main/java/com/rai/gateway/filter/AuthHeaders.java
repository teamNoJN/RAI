package com.rai.gateway.filter;

/**
 * Gateway 가 JWT 검증 후 다운스트림 서비스로 넘기는 헤더 이름.
 *
 * <p>★ {@code common/security/AuthHeaders.java} 와 값이 반드시 같아야 한다.
 * 상수를 복제해 둔 이유는 gateway 가 {@code :common} 을 의존할 수 없기 때문이다
 * (WebFlux ↔ MVC 클래스패스 충돌 — build.gradle 주석 참조).
 * 한쪽을 고치면 반드시 다른 쪽도 고친다.
 */
public final class AuthHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String COMPANY_ID = "X-Company-Id";
    public static final String ROLE = "X-Role";

    private AuthHeaders() {}
}
