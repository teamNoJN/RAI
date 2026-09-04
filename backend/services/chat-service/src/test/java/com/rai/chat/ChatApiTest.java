package com.rai.chat;

import com.rai.chat.client.DrugServiceClient;
import com.rai.chat.client.RegulationClient;
import com.rai.chat.dto.ChatDto;
import org.awaitility.Awaitility;
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

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 3 · 3C · 3E · 3R 화면 계약 검증 (docs/api-spec/screen-03*.md). 실제 postgres 사용. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ChatApiTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID OTHER_COMPANY = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID DRUG = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean DrugServiceClient drugServiceClient;
    @MockitoBean RegulationClient regulationClient;

    private UUID conversationId;

    private static final String COMPANY_NAME = "테스트제약-ChatApiTest";
    private static final String OTHER_COMPANY_NAME = "남의제약-ChatApiTest";

    @BeforeEach
    void setUp() {
        // 앞선 run 이 중간에 죽으면 회사 행이 남아 이름 UNIQUE 에 걸린다. 먼저 치운다.
        deleteCompaniesByName();
        jdbc.update("INSERT INTO company (company_id, company_name) VALUES (?, ?)",
                COMPANY, COMPANY_NAME);
        jdbc.update("INSERT INTO company (company_id, company_name) VALUES (?, ?)",
                OTHER_COMPANY, OTHER_COMPANY_NAME);
        jdbc.update("""
                INSERT INTO app_user (user_id, company_id, email, password_hash, name)
                VALUES (?, ?, 'chat-test@rai.local', '(test)', 'tester')
                """, USER, COMPANY);
        jdbc.update("""
                INSERT INTO drug (drug_id, company_id, product_name, ingredients)
                VALUES (?, ?, '아목시실린 캡슐', '["Amoxicillin","첨가제 B"]'::jsonb)
                """, DRUG, COMPANY);
        conversationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO conversation (conversation_id, company_id, user_id, drug_id, country_id)
                VALUES (?, ?, ?, ?, 'VN')
                """, conversationId, COMPANY, USER, DRUG);

        given(drugServiceClient.requireDrug(any(), any())).willReturn(
                new DrugServiceClient.InternalDrug(DRUG.toString(), "아목시실린 캡슐",
                        List.of("Amoxicillin", "첨가제 B"), "500mg", "capsule", 1));
        given(drugServiceClient.countryExists(anyString())).willReturn(true);
        given(regulationClient.findSources(anyString())).willReturn(List.of(source()));
    }

    @AfterEach
    void cleanup() {
        deleteCompaniesByName();
    }

    /**
     * assessment.drug_id 에는 ON DELETE 가 없다(판정 이력 보호). company 를 먼저 지우면
     * drug 캐스케이드가 그 FK 에 막히므로, 대화를 먼저 지워 판정을 캐스케이드로 걷어낸다.
     */
    private void deleteCompaniesByName() {
        jdbc.update("""
                DELETE FROM conversation WHERE company_id IN
                    (SELECT company_id FROM company WHERE company_name IN (?, ?))
                """, COMPANY_NAME, OTHER_COMPANY_NAME);
        jdbc.update("DELETE FROM company WHERE company_name IN (?, ?)", COMPANY_NAME, OTHER_COMPANY_NAME);
    }

    @Test
    void 메시지를_보내면_202_로_request_id_와_컨텍스트를_먼저_준다() throws Exception {
        mvc.perform(send("이 제품 베트남 수출 가능한가?"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.request_id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.intent").value("EXPORT_ELIGIBILITY_CHECK"))
                .andExpect(jsonPath("$.context.drug_id").value(DRUG.toString()))
                .andExpect(jsonPath("$.context.country_id").value("VN"));
    }

    @Test
    void 폴링하면_판정이_완료되고_명세_계약대로_나온다() throws Exception {
        String requestId = requestId(send("이 제품 베트남 수출 가능한가?"));

        awaitCompleted(requestId);

        mvc.perform(authed(get("/api/assessments/{id}", requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_id").value(requestId))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.intent").value("EXPORT_ELIGIBILITY_CHECK"))
                .andExpect(jsonPath("$.result.summary").isNotEmpty())
                .andExpect(jsonPath("$.result.eligibility").isNotEmpty())
                .andExpect(jsonPath("$.result.ingredient_assessments.length()").value(2))
                .andExpect(jsonPath("$.result.ingredient_assessments[0].ingredient").value("Amoxicillin"))
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.sources[0].document_id").value("VN-REG-001"))
                .andExpect(jsonPath("$.sources[0].version").value("2026.01"));
    }

    /** 3R — 근거가 없으면 문서명·조항을 만들어내지 않고 REVIEW_REQUIRED. */
    @Test
    void 근거가_없으면_REVIEW_REQUIRED_이고_sources_가_빈다() throws Exception {
        given(regulationClient.findSources(anyString())).willReturn(List.of());
        String requestId = requestId(send("이 제품 베트남 수출 가능한가?"));

        awaitCompleted(requestId);

        mvc.perform(authed(get("/api/assessments/{id}", requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.eligibility").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.result.summary").value("현재 등록된 규제 자료만으로 판단하기 어렵습니다."))
                .andExpect(jsonPath("$.sources.length()").value(0));
    }

    /** 3E — 판정이 실패하면 상태만 준다. */
    @Test
    void 판정이_실패하면_status_failed_만_준다() throws Exception {
        given(drugServiceClient.requireDrug(any(), any()))
                .willThrow(new IllegalStateException("drug-service 장애"));
        String requestId = requestId(send("이 제품 베트남 수출 가능한가?"));

        awaitStatus(requestId, "failed");

        mvc.perform(authed(get("/api/assessments/{id}", requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request_id").value(requestId))
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 처리_중에_또_보내면_400_이다() throws Exception {
        mvc.perform(send("이 제품 베트남 수출 가능한가?")).andExpect(status().isAccepted());
        jdbc.update("UPDATE assessment SET status = 'pending' WHERE conversation_id = ?", conversationId);

        mvc.perform(send("한 번 더 물어본다"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void 타임라인은_user_와_assistant_메시지를_순서대로_준다() throws Exception {
        String requestId = requestId(send("이 제품 베트남 수출 가능한가?"));
        awaitCompleted(requestId);

        mvc.perform(authed(get("/api/conversations/{id}/messages", conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("이 제품 베트남 수출 가능한가?"))
                .andExpect(jsonPath("$[1].role").value("assistant"))
                .andExpect(jsonPath("$[1].intent").value("EXPORT_ELIGIBILITY_CHECK"))
                .andExpect(jsonPath("$[1].status").value("completed"));
    }

    @Test
    void 판정_피드백을_기록한다() throws Exception {
        String requestId = requestId(send("이 제품 베트남 수출 가능한가?"));

        mvc.perform(authed(post("/api/assessments/{id}/feedback", requestId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"helpful\",\"reason\":\"근거가 명확함\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("recorded"));
    }

    @Test
    void 잘못된_rating_은_400() throws Exception {
        String requestId = requestId(send("이 제품 베트남 수출 가능한가?"));

        mvc.perform(authed(post("/api/assessments/{id}/feedback", requestId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"awesome\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    /** 3C — 국가만 바뀐다. */
    @Test
    void 컨텍스트_변경은_국가를_바꾼다() throws Exception {
        mvc.perform(authed(patch("/api/conversations/{id}", conversationId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country_id\":\"KR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation_id").value(conversationId.toString()))
                .andExpect(jsonPath("$.drug_id").value(DRUG.toString()))
                .andExpect(jsonPath("$.country_id").value("KR"));
    }

    @Test
    void 목록에_없는_국가로는_바꿀_수_없다() throws Exception {
        given(drugServiceClient.countryExists("ZZ")).willReturn(false);

        mvc.perform(authed(patch("/api/conversations/{id}", conversationId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country_id\":\"ZZ\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 다른_회사의_대화에는_메시지를_보낼_수_없다() throws Exception {
        mvc.perform(post("/api/conversations/{id}/messages", conversationId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Company-Id", OTHER_COMPANY.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"남의 대화\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 인증_없이는_401() throws Exception {
        mvc.perform(get("/api/conversations/{id}/messages", conversationId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // --- helpers ------------------------------------------------------

    private MockHttpServletRequestBuilder send(String message) {
        return authed(post("/api/conversations/{id}/messages", conversationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"%s\"}".formatted(message));
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header("X-User-Id", USER.toString())
                .header("X-Company-Id", COMPANY.toString());
    }

    private String requestId(MockHttpServletRequestBuilder request) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                mvc.perform(request).andReturn().getResponse().getContentAsString(), "$.request_id");
    }

    /** 비동기 워커가 끝날 때까지 기다린다 — FE 의 2초 폴링에 해당. */
    private void awaitCompleted(String requestId) {
        awaitStatus(requestId, "completed");
    }

    private void awaitStatus(String requestId, String expected) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100))
                .until(() -> expected.equals(jdbc.queryForObject(
                        "SELECT status FROM assessment WHERE request_id = ?", String.class, requestId)));
    }

    private ChatDto.SourceResponse source() {
        return new ChatDto.SourceResponse("VN-REG-001", "의약품 등록 규정",
                "Drug Administration of Vietnam", "2026.01",
                LocalDate.of(2026, 1, 1), "4.2", "https://example.test");
    }
}
