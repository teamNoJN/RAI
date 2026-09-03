package com.rai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 필터 체인은 permitAll — 실제 인증은 컨트롤러의 CurrentUser 리졸버가 강제한다
 * (Gateway 헤더 또는 Bearer 토큰, 둘 다 없으면 401).
 * CORS 는 열지 않는다: 브라우저는 게이트웨이만 보므로(gateway 만 CORS 허용)
 * 여기서 origin 을 열어두면 게이트웨이 우회 경로만 넓어진다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
