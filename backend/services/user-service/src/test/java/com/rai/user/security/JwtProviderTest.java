package com.rai.user.security;

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
        return new JwtProvider(new JwtProperties(SECRET, accessTtl, Duration.ofDays(14)));
    }

    @Test
    void access_토큰에서_user_id_를_꺼낸다() {
        JwtProvider provider = provider(Duration.ofHours(1));

        String token = provider.createAccessToken(user);

        assertThat(provider.parseAccessTokenUserId(token)).isEqualTo(user.getUserId());
    }

    @Test
    void refresh_토큰을_access_토큰으로_쓸_수_없다() {
        JwtProvider provider = provider(Duration.ofHours(1));

        String refresh = provider.createRefreshToken(user);

        assertThatThrownBy(() -> provider.parseAccessTokenUserId(refresh))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 만료된_토큰은_거부된다() {
        JwtProvider provider = provider(Duration.ofSeconds(-1));

        String expired = provider.createAccessToken(user);

        assertThatThrownBy(() -> provider.parseAccessTokenUserId(expired))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 다른_비밀키로_서명된_토큰은_거부된다() {
        String token = provider(Duration.ofHours(1)).createAccessToken(user);
        JwtProvider other = new JwtProvider(
                new JwtProperties("completely-different-secret-key-32bytes+", Duration.ofHours(1), Duration.ofDays(14)));

        assertThatThrownBy(() -> other.parseAccessTokenUserId(token))
                .isInstanceOf(JwtException.class);
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
