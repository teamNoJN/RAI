package com.rai.user.dto;

import com.rai.user.entity.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 1번 로그인/회원가입 화면 계약 (docs/api-spec/screen-01-login.md).
 * JSON 은 spring.jackson.property-naming-strategy=SNAKE_CASE 로 snake_case 변환된다.
 */
public final class AuthDto {

    private AuthDto() {}

    // --- Request ------------------------------------------------------

    public record LoginRequest(
            @NotBlank(message = "이메일을 입력해주세요")
            @Email(message = "이메일 형식이 올바르지 않습니다")
            String email,

            @NotBlank(message = "비밀번호를 입력해주세요")
            String password
    ) {}

    public record SignupRequest(
            @NotBlank(message = "이메일을 입력해주세요")
            @Email(message = "이메일 형식이 올바르지 않습니다")
            String email,

            @NotBlank(message = "비밀번호를 입력해주세요")
            @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다")
            String password,

            @NotBlank(message = "이름을 입력해주세요")
            @Size(max = 100, message = "이름은 100자를 넘을 수 없습니다")
            String name,

            @NotBlank(message = "회사명을 입력해주세요")
            @Size(max = 255, message = "회사명은 255자를 넘을 수 없습니다")
            String companyName
    ) {}

    public record RefreshRequest(
            @NotBlank(message = "refresh_token 이 필요합니다")
            String refreshToken
    ) {}

    // --- Response -----------------------------------------------------

    /** 로그인 성공 200. */
    public record LoginResponse(String accessToken, String refreshToken, UserResponse user) {}

    /** 회원가입 성공 201. */
    public record SignupResponse(String userId, String email, String companyId, String companyName) {

        public static SignupResponse from(AppUser user) {
            return new SignupResponse(
                    user.getUserId().toString(),
                    user.getEmail(),
                    user.getCompany().getCompanyId().toString(),
                    user.getCompany().getCompanyName());
        }
    }

    /** 토큰 재발급 200. */
    public record RefreshResponse(String accessToken) {}

    /** GET /api/auth/me 200 · 로그인 응답의 user 필드. */
    public record UserResponse(String userId, String name, String email, String companyId) {

        public static UserResponse from(AppUser user) {
            return new UserResponse(
                    user.getUserId().toString(),
                    user.getName(),
                    user.getEmail(),
                    user.getCompany().getCompanyId().toString());
        }
    }
}
