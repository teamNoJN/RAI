package com.rai.common.security;

import java.util.UUID;

/**
 * 요청을 보낸 사용자. 컨트롤러 파라미터로 선언하면 자동으로 채워진다
 * (없으면 401). company_id 는 사용자가 보내는 값이 아니라 토큰/게이트웨이에서 온 값이라,
 * 회사 격리 쿼리는 항상 이 값을 써야 한다.
 */
public record CurrentUser(UUID userId, UUID companyId, String email) {
}
