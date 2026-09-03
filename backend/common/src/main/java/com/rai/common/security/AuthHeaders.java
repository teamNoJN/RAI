package com.rai.common.security;

/**
 * Gateway 가 JWT 를 검증한 뒤 다운스트림 서비스로 넘기는 헤더 이름
 * (docs/00-project-plan.md 4-5). 서비스는 이 헤더를 신뢰한다.
 */
public final class AuthHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String COMPANY_ID = "X-Company-Id";
    public static final String ROLE = "X-Role";

    private AuthHeaders() {}
}
