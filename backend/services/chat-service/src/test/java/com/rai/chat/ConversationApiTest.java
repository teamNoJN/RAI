package com.rai.chat;

import com.rai.chat.client.DrugServiceClient;
import com.rai.common.exception.ApiException;
import com.rai.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 2번 대시보드의 세션 시작 · 최근 대화 (docs/api-spec/screen-02-dashboard.md). 실제 postgres 사용. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ConversationApiTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final UUID DRUG = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    /** drug-service 는 이 테스트에서 띄우지 않는다. */
    @MockitoBean DrugServiceClient drugServiceClient;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO company (company_id, company_name) VALUES (?, ?)",
                COMPANY, "테스트제약-ConversationApiTest");
        insertUser(USER, "conv-test@rai.local");
        insertUser(OTHER_USER, "conv-test-2@rai.local");
        jdbc.update("""
                INSERT INTO drug (drug_id, company_id, product_name, ingredients)
                VALUES (?, ?, ?, '["Amoxicillin"]'::jsonb)
                """, DRUG, COMPANY, "아목시실린 캡슐");

        given(drugServiceClient.requireDrug(any(), any()))
                .willReturn(new DrugServiceClient.InternalDrug(DRUG.toString(), "아목시실린 캡슐"));
        given(drugServiceClient.nameLookup(anyList(), any()))
                .willReturn(Map.of(DRUG, "아목시실린 캡슐")::get);
    }

    @AfterEach
    void cleanup() {
        // conversation·drug 는 company 에 ON DELETE CASCADE 로 딸려 지워진다.
        jdbc.update("DELETE FROM company WHERE company_id = ?", COMPANY);
    }

    @Test
    void 약과_국가를_고정해_세션을_만든다() throws Exception {
        mvc.perform(authed(post("/api/conversations"), USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"drug_id":"%s","country_id":"VN"}
                                """.formatted(DRUG)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversation_id").isNotEmpty())
                .andExpect(jsonPath("$.drug_id").value(DRUG.toString()))
                .andExpect(jsonPath("$.country_id").value("VN"))
                .andExpect(jsonPath("$.created_at").isNotEmpty());
    }

    @Test
    void 국가를_고르지_않으면_400() throws Exception {
        mvc.perform(authed(post("/api/conversations"), USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drug_id\":\"%s\"}".formatted(DRUG)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("국가를 선택해주세요"));
    }

    @Test
    void 없는_제품이면_404() throws Exception {
        willThrow(new ApiException(ErrorCode.NOT_FOUND, "제품을 찾을 수 없습니다"))
                .given(drugServiceClient).requireDrug(any(), any());

        mvc.perform(authed(post("/api/conversations"), USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"drug_id":"%s","country_id":"VN"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 최근_대화에_제품명이_채워진다() throws Exception {
        createConversation("VN");

        mvc.perform(authed(get("/api/conversations"), USER).param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].conversation_id").isNotEmpty())
                .andExpect(jsonPath("$[0].product_name").value("아목시실린 캡슐"))
                .andExpect(jsonPath("$[0].country_id").value("VN"));
    }

    @Test
    void 최근_대화는_limit_만큼만_준다() throws Exception {
        createConversation("VN");
        createConversation("KR");
        createConversation("VN");

        mvc.perform(authed(get("/api/conversations"), USER).param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 다른_사용자의_대화는_보이지_않는다() throws Exception {
        createConversation("VN");

        mvc.perform(authed(get("/api/conversations"), OTHER_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 인증_없이_부르면_401() throws Exception {
        mvc.perform(get("/api/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 내부_API_는_이_제품으로_대화한_국가를_돌려준다() throws Exception {
        createConversation("VN");
        createConversation("VN");
        createConversation("KR");

        mvc.perform(get("/internal/conversations/prior-countries")
                        .header("X-Company-Id", COMPANY.toString())
                        .param("drug_id", DRUG.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // --- helpers ------------------------------------------------------

    private void createConversation(String countryId) throws Exception {
        mvc.perform(authed(post("/api/conversations"), USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"drug_id":"%s","country_id":"%s"}
                                """.formatted(DRUG, countryId)))
                .andExpect(status().isCreated());
    }

    /** Gateway 가 넣어주는 헤더를 흉내낸다. */
    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, UUID userId) {
        return builder.header("X-User-Id", userId.toString())
                .header("X-Company-Id", COMPANY.toString());
    }

    private void insertUser(UUID userId, String email) {
        jdbc.update("""
                INSERT INTO app_user (user_id, company_id, email, password_hash, name)
                VALUES (?, ?, ?, '(test)', 'tester')
                """, userId, COMPANY, email);
    }
}
