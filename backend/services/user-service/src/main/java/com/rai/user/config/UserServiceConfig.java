package com.rai.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class UserServiceConfig {

    /**
     * spring-security-crypto 만 의존하므로 서비스 자체 필터체인은 켜지지 않는다.
     * 인증 검증은 Gateway 담당 (README 인증 구조).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
