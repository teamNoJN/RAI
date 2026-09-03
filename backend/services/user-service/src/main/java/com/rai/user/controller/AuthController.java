package com.rai.user.controller;

import com.rai.common.exception.ApiException;
import com.rai.common.exception.ErrorCode;
import com.rai.common.security.AuthHeaders;
import com.rai.user.dto.AuthDto;
import com.rai.user.security.JwtProvider;
import com.rai.user.service.AuthService;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 1번 로그인/회원가입 화면 (docs/api-spec/screen-01-login.md). */
@Tag(name = "Auth", description = "로그인 · 회원가입 · 토큰")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final JwtProvider jwtProvider;

    @Operation(summary = "로그인", description = "200 · 401")
    @PostMapping("/login")
    public AuthDto.LoginResponse login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "회원가입", description = "201 · 400 · 409 (409 → 이메일 필드 인라인 에러)")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDto.SignupResponse signup(@Valid @RequestBody AuthDto.SignupRequest request) {
        return authService.signup(request);
    }

    @Operation(summary = "access 토큰 재발급", description = "200 · 401 (401 → 세션 만료 토스트)")
    @PostMapping("/refresh")
    public AuthDto.RefreshResponse refresh(@Valid @RequestBody AuthDto.RefreshRequest request) {
        return authService.refresh(request);
    }

    @Operation(summary = "현재 사용자", description = "200 · 401")
    @GetMapping("/me")
    public AuthDto.UserResponse me(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String gatewayUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.me(resolveUserId(gatewayUserId, authorization));
    }

    /**
     * Gateway 를 거쳐 오면 X-User-Id 헤더가 이미 붙어 있다(4-5). 이 서비스를 단독으로
     * 호출할 때(로컬 개발 · Gateway 미배포 구간)를 위해 Bearer 토큰 파싱도 지원한다.
     */
    private UUID resolveUserId(String gatewayUserId, String authorization) {
        if (gatewayUserId != null && !gatewayUserId.isBlank()) {
            try {
                return UUID.fromString(gatewayUserId);
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.UNAUTHORIZED);
            }
        }
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return jwtProvider.parseAccessTokenUserId(authorization.substring(BEARER_PREFIX.length()));
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
    }
}
