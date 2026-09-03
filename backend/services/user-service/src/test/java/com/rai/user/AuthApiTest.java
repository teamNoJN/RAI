package com.rai.user;

import com.rai.user.repository.AppUserRepository;
import com.rai.user.repository.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1번 / 1E 화면 계약 검증 (docs/api-spec/screen-01-login.md).
 * 실제 postgres(localhost:5434)에 붙는다 — 기존 테스트와 동일한 방식.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthApiTest {

    private static final String EMAIL = "auth-test@rai.local";
    private static final String COLLEAGUE_EMAIL = "auth-test-2@rai.local";
    private static final String PASSWORD = "pw-secret-1234";
    private static final String COMPANY = "테스트제약-AuthApiTest";

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository userRepository;
    @Autowired CompanyRepository companyRepository;

    @AfterEach
    void cleanup() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(COLLEAGUE_EMAIL).ifPresent(userRepository::delete);
        userRepository.flush();
        companyRepository.findByCompanyName(COMPANY).ifPresent(companyRepository::delete);
    }

    @Test
    void 회원가입하면_회사가_생성되고_201_을_준다() throws Exception {
        mvc.perform(signup(EMAIL, "이서연"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.company_id").isNotEmpty())
                .andExpect(jsonPath("$.company_name").value(COMPANY));

        assertThat(companyRepository.findByCompanyName(COMPANY)).isPresent();
    }

    @Test
    void 회사명이_같으면_같은_회사에_자동_소속된다() throws Exception {
        String first = json(mvc.perform(signup(EMAIL, "이서연")).andReturn(), "company_id");
        String second = json(mvc.perform(signup(COLLEAGUE_EMAIL, "박민수")).andReturn(), "company_id");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void 이미_가입된_이메일이면_409_CONFLICT() throws Exception {
        mvc.perform(signup(EMAIL, "이서연")).andExpect(status().isCreated());

        mvc.perform(signup(EMAIL, "이서연"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("이미 가입된 이메일입니다"));
    }

    @Test
    void 비밀번호가_8자_미만이면_400() throws Exception {
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"short","name":"이서연","company_name":"%s"}
                                """.formatted(EMAIL, COMPANY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void 로그인하면_토큰_두_개와_사용자_정보를_준다() throws Exception {
        mvc.perform(signup(EMAIL, "이서연")).andExpect(status().isCreated());

        mvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.user.name").value("이서연"))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.company_id").isNotEmpty());
    }

    @Test
    void 비밀번호가_틀리면_401_공통_에러_계약() throws Exception {
        mvc.perform(signup(EMAIL, "이서연")).andExpect(status().isCreated());

        mvc.perform(login(EMAIL, "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 일치하지 않습니다"));
    }

    @Test
    void 없는_계정도_같은_401_문구를_준다() throws Exception {
        mvc.perform(login("nobody@rai.local", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 일치하지 않습니다"));
    }

    @Test
    void refresh_토큰으로_access_토큰을_재발급한다() throws Exception {
        mvc.perform(signup(EMAIL, "이서연")).andExpect(status().isCreated());
        String refreshToken = json(mvc.perform(login(EMAIL, PASSWORD)).andReturn(), "refresh_token");

        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    void 위조된_refresh_토큰이면_401_세션_만료() throws Exception {
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"eyJ-not-a-real-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("세션이 만료되었습니다. 다시 로그인해주세요"));
    }

    @Test
    void me_는_access_토큰으로_현재_사용자를_준다() throws Exception {
        mvc.perform(signup(EMAIL, "이서연")).andExpect(status().isCreated());
        String accessToken = json(mvc.perform(login(EMAIL, PASSWORD)).andReturn(), "access_token");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("이서연"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.company_id").isNotEmpty());
    }

    @Test
    void 토큰_없이_me_를_부르면_401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // --- helpers ------------------------------------------------------

    private org.springframework.test.web.servlet.RequestBuilder signup(String email, String name) {
        return post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s","name":"%s","company_name":"%s"}
                        """.formatted(email, PASSWORD, name, COMPANY));
    }

    private org.springframework.test.web.servlet.RequestBuilder login(String email, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password));
    }

    private String json(MvcResult result, String field) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$." + field);
    }
}
