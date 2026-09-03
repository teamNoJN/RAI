package com.rai.user.controller;

import com.rai.common.security.CurrentUser;
import com.rai.user.dto.AuthDto;
import com.rai.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 1번 로그인/회원가입 화면 (docs/api-spec/screen-01-login.md). */
@Tag(name = "Auth", description = "로그인 · 회원가입 · 토큰")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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

    /** CurrentUser 는 common 의 resolver 가 채운다(헤더 우선, 없으면 Bearer 검증). 실패 시 401. */
    @Operation(summary = "현재 사용자", description = "200 · 401")
    @GetMapping("/me")
    public AuthDto.UserResponse me(CurrentUser currentUser) {
        return authService.me(currentUser.userId());
    }
}
