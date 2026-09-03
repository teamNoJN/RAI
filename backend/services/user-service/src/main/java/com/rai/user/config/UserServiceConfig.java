package com.rai.user.config;

import com.rai.user.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class UserServiceConfig {

    /**
     * spring-security-crypto 만 의존하므로 서비스 자체 필터체인은 켜지지 않는다.
     * 인증 검증은 Gateway 담당 (docs/00-project-plan.md 4-5).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
