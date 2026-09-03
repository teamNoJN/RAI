package com.rai.user.service;

import com.rai.common.exception.ApiException;
import com.rai.common.exception.ErrorCode;
import com.rai.user.dto.AuthDto;
import com.rai.user.entity.AppUser;
import com.rai.user.entity.Company;
import com.rai.user.repository.AppUserRepository;
import com.rai.user.repository.CompanyRepository;
import com.rai.user.security.JwtProvider;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 1번 로그인/회원가입 · 1E 인증 예외 (docs/api-spec/screen-01-login.md). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // 1E 명세 고정 문구. 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다(계정 존재 여부 노출 방지).
    private static final String LOGIN_FAILED = "이메일 또는 비밀번호가 일치하지 않습니다";
    private static final String SESSION_EXPIRED = "세션이 만료되었습니다. 다시 로그인해주세요";

    private final AppUserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /** 회원가입. 회사명이 기존과 같으면 자동 소속, 새 이름이면 회사를 생성한다. */
    @Transactional
    public AuthDto.SignupResponse signup(AuthDto.SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.CONFLICT, "이미 가입된 이메일입니다");
        }

        AppUser user = AppUser.builder()
                .company(resolveCompany(request.companyName().trim()))
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name().trim())
                .build();

        try {
            return AuthDto.SignupResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // existsByEmail 통과 후 동시 가입으로 UNIQUE 제약에 걸린 경우.
            throw new ApiException(ErrorCode.CONFLICT, "이미 가입된 이메일입니다");
        }
    }

    /** 로그인. 실패는 이유를 구분하지 않고 동일한 401 을 준다. */
    @Transactional(readOnly = true)
    public AuthDto.LoginResponse login(AuthDto.LoginRequest request) {
        AppUser user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAILED);
        }

        return new AuthDto.LoginResponse(
                jwtProvider.createAccessToken(user),
                jwtProvider.createRefreshToken(user),
                AuthDto.UserResponse.from(user));
    }

    /** refresh 토큰으로 access 토큰 재발급. 실패 시 FE 는 1E "세션 만료" 토스트를 띄운다. */
    @Transactional(readOnly = true)
    public AuthDto.RefreshResponse refresh(AuthDto.RefreshRequest request) {
        UUID userId;
        try {
            userId = jwtProvider.parseRefreshTokenUserId(request.refreshToken());
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, SESSION_EXPIRED);
        }

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, SESSION_EXPIRED));

        return new AuthDto.RefreshResponse(jwtProvider.createAccessToken(user));
    }

    /** 부팅 시 사용자 확인. */
    @Transactional(readOnly = true)
    public AuthDto.UserResponse me(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, SESSION_EXPIRED));
        return AuthDto.UserResponse.from(user);
    }

    /**
     * 같은 이름의 회사가 있으면 그 회사에 소속시키고, 없으면 만든다.
     * company_name 은 UNIQUE 이므로 동시 가입 경합은 조회로 되돌린다.
     */
    private Company resolveCompany(String companyName) {
        return companyRepository.findByCompanyName(companyName)
                .orElseGet(() -> {
                    try {
                        return companyRepository.saveAndFlush(
                                Company.builder().companyName(companyName).build());
                    } catch (DataIntegrityViolationException e) {
                        log.debug("company 동시 생성 경합 — 기존 회사에 소속: {}", companyName);
                        return companyRepository.findByCompanyName(companyName)
                                .orElseThrow(() -> e);
                    }
                });
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
