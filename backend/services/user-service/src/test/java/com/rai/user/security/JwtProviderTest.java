package com.rai.user.security;

import com.rai.common.security.JwtProperties;
import com.rai.common.security.JwtVerifier;
import com.rai.user.entity.AppUser;
import com.rai.user.entity.Company;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 토큰 발급/검증 단위 테스트 (DB·컨텍스트 불필요). */
class JwtProviderTest {

    private static final String SECRET = "rai-test-secret-key-must-be-at-least-32-bytes";

    private final AppUser user = user();

    private JwtProvider provider(Duration accessTtl) {
        return provider(SECRET, accessTtl);
    }

    private JwtProvider provider(String secret, Duration accessTtl) {
        JwtProperties properties = new JwtProperties(secret, accessTtl, Duration.ofDays(14));
        return new JwtProvider(properties, new JwtVerifier(properties));
    }

    @Test
    void access_토큰에서_user_id_를_꺼낸다() {
        JwtProvider provider = provider(Duration.ofHours(1));

        String token = provider.createAccessToken(user);

        assertThat(userIdOf(SECRET, token)).isEqualTo(user.getUserId());
    }

    @Test
    void refresh_토큰을_access_토큰으로_쓸_수_없다() {
        JwtProvider provider = provider(Duration.ofHours(1));

        String refresh = provider.createRefreshToken(user);

        assertThatThrownBy(() -> userIdOf(SECRET, refresh)).isInstanceOf(JwtException.class);
    }

    @Test
    void 만료된_토큰은_거부된다() {
        JwtProvider provider = provider(Duration.ofSeconds(-1));

        String expired = provider.createAccessToken(user);

        assertThatThrownBy(() -> userIdOf(SECRET, expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void 다른_비밀키로_서명된_토큰은_거부된다() {
        String token = provider(Duration.ofHours(1)).createAccessToken(user);

        assertThatThrownBy(() -> userIdOf("completely-different-secret-key-32bytes+", token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void access_토큰을_refresh_로_쓸_수_없다() {
        JwtProvider provider = provider(Duration.ofHours(1));

        String access = provider.createAccessToken(user);

        assertThatThrownBy(() -> provider.parseRefreshTokenUserId(access))
                .isInstanceOf(JwtException.class);
    }

    /** Gateway·다른 서비스가 하는 것과 같은 검증 경로. */
    private UUID userIdOf(String secret, String token) {
        JwtVerifier verifier = new JwtVerifier(
                new JwtProperties(secret, Duration.ofHours(1), Duration.ofDays(14)));
        return UUID.fromString(verifier.parse(token, JwtVerifier.TYPE_ACCESS).getSubject());
    }

    private static AppUser user() {
        Company company = Company.builder()
                .companyId(UUID.randomUUID())
                .companyName("한빛제약")
                .build();
        return AppUser.builder()
                .userId(UUID.randomUUID())
                .company(company)
                .email("ra@pharm.co")
                .passwordHash("(hash)")
                .name("이서연")
                .build();
    }
}
