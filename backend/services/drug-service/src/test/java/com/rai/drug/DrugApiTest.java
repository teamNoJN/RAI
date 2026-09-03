package com.rai.drug;

import com.rai.drug.client.ChatServiceClient;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 2 · 2E · 2F · 2S 화면 계약 검증 (docs/api-spec/screen-02*.md). 실제 postgres 사용. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DrugApiTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID OTHER_COMPANY = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    /** chat-service 는 이 테스트에서 띄우지 않는다. 이력 유무를 여기서 정한다. */
    @MockitoBean ChatServiceClient chatServiceClient;

    @BeforeEach
    void setUp() {
        insertCompany(COMPANY, "테스트제약-DrugApiTest");
        insertCompany(OTHER_COMPANY, "남의제약-DrugApiTest");
        given(chatServiceClient.priorCountries(any(), any())).willReturn(List.of());
    }

    @AfterEach
    void cleanup() {
        // drug 는 company 에 ON DELETE CASCADE 로 딸려 지워진다.
        jdbc.update("DELETE FROM company WHERE company_id IN (?, ?)", COMPANY, OTHER_COMPANY);
    }

    @Test
    void 제품을_등록하면_201_이고_목록에_나온다() throws Exception {
        mvc.perform(create("아목시실린 캡슐", "\"Amoxicillin\", \"첨가제 B\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.drug_id").isNotEmpty())
                .andExpect(jsonPath("$.product_name").value("아목시실린 캡슐"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(authed(get("/api/drugs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].product_name").value("아목시실린 캡슐"))
                .andExpect(jsonPath("$[0].ingredients[0]").value("Amoxicillin"))
                .andExpect(jsonPath("$[0].strength").value("500mg"))
                .andExpect(jsonPath("$[0].dosage_form").value("capsule"));
    }

    @Test
    void 제품이_없으면_빈_배열을_준다() throws Exception {
        mvc.perform(authed(get("/api/drugs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 제품명이_없으면_400_VALIDATION_ERROR() throws Exception {
        mvc.perform(authed(post("/api/drugs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredients\":[\"Amoxicillin\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("제품명을 입력해주세요"));
    }

    @Test
    void 성분이_비어있으면_400_VALIDATION_ERROR() throws Exception {
        mvc.perform(authed(post("/api/drugs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_name\":\"아목시실린 캡슐\",\"ingredients\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("성분을 1개 이상 입력해주세요"));
    }

    @Test
    void 다른_회사_제품은_보이지_않는다() throws Exception {
        mvc.perform(create("아목시실린 캡슐", "\"Amoxicillin\"")).andExpect(status().isCreated());

        mvc.perform(get("/api/drugs")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Company-Id", OTHER_COMPANY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 제품명으로_검색한다() throws Exception {
        mvc.perform(create("아목시실린 캡슐", "\"Amoxicillin\"")).andExpect(status().isCreated());
        mvc.perform(create("타이레놀 정", "\"Acetaminophen\"")).andExpect(status().isCreated());

        mvc.perform(authed(get("/api/drugs")).param("q", "아목시"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].product_name").value("아목시실린 캡슐"));
    }

    @Test
    void 성분으로도_검색된다() throws Exception {
        mvc.perform(create("아목시실린 캡슐", "\"Amoxicillin\"")).andExpect(status().isCreated());

        mvc.perform(authed(get("/api/drugs")).param("q", "Amoxi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 검색_결과가_없으면_빈_배열() throws Exception {
        mvc.perform(create("아목시실린 캡슐", "\"Amoxicillin\"")).andExpect(status().isCreated());

        mvc.perform(authed(get("/api/drugs")).param("q", "없는약"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 대화_이력이_있으면_재검토가_필요하다() throws Exception {
        String drugId = drugId(mvc.perform(create("아목시실린 캡슐", "\"Amoxicillin\"")).andReturn());
        given(chatServiceClient.priorCountries(any(), any())).willReturn(List.of("VN"));

        mvc.perform(authed(get("/api/drugs/{id}/reassessment-needed", drugId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needed").value(true))
                .andExpect(jsonPath("$.prior_countries[0]").value("VN"))
                .andExpect(jsonPath("$.message").value("기존 판정 결과가 존재합니다. 재검토가 필요할 수 있습니다."));
    }

    @Test
    void 대화_이력이_없으면_재검토가_필요없다() throws Exception {
        String drugId = drugId(mvc.perform(create("아목시실린 캡슐", "\"Amoxicillin\"")).andReturn());

        mvc.perform(authed(get("/api/drugs/{id}/reassessment-needed", drugId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needed").value(false))
                .andExpect(jsonPath("$.prior_countries.length()").value(0));
    }

    @Test
    void 남의_제품_재검토_조회는_404() throws Exception {
        String drugId = drugId(mvc.perform(create("아목시실린 캡슐", "\"Amoxicillin\"")).andReturn());

        mvc.perform(get("/api/drugs/{id}/reassessment-needed", drugId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Company-Id", OTHER_COMPANY.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void 인증_없이_제품을_조회하면_401() throws Exception {
        mvc.perform(get("/api/drugs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 국가_목록은_인증_없이도_준다() throws Exception {
        mvc.perform(get("/api/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.country_id == 'VN')]").exists());
    }

    // --- helpers ------------------------------------------------------

    private RequestBuilder create(String productName, String ingredientsJson) {
        return authed(post("/api/drugs"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"product_name":"%s","ingredients":[%s],"strength":"500mg","dosage_form":"capsule"}
                        """.formatted(productName, ingredientsJson));
    }

    /** Gateway 가 넣어주는 헤더를 흉내낸다. */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authed(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder.header("X-User-Id", USER.toString())
                .header("X-Company-Id", COMPANY.toString());
    }

    private void insertCompany(UUID companyId, String name) {
        jdbc.update("INSERT INTO company (company_id, company_name) VALUES (?, ?)", companyId, name);
    }

    private String drugId(MvcResult result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.drug_id");
    }
}
