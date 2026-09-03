package com.rai.report;

import com.rai.common.config.CommonWebConfig;
import com.rai.common.security.AuthHeaders;
import com.rai.common.security.JwtVerifier;
import com.rai.report.controller.ReportController;
import com.rai.report.dto.ReportDto;
import com.rai.report.exception.ReportApiException;
import com.rai.report.pdf.ReportPdf;
import com.rai.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 5·5L 엔드포인트의 HTTP 계약(상태코드 · snake_case 필드 · 에러 형식) 검증. */
@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CommonWebConfig.class, ReportControllerTest.GatewayHeaders.class})
class ReportControllerTest {

    private static final UUID COMPANY_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    /**
     * 이 테스트는 HTTP 계약을 보는 곳이라 인증 헤더를 매 호출에 붙이지 않고 기본값으로 깐다.
     * 인증이 없을 때의 동작은 아래 별도 테스트에서 본다.
     */
    @TestConfiguration
    static class GatewayHeaders {
        @Bean
        MockMvcBuilderCustomizer defaultAuthHeaders() {
            return builder -> builder.defaultRequest(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/")
                            .header(AuthHeaders.USER_ID, UUID.randomUUID().toString())
                            .header(AuthHeaders.COMPANY_ID, COMPANY_ID.toString()));
        }
    }

    private static final UUID CONVERSATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REPORT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DRUG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired MockMvc mvc;

    @MockitoBean ReportService reportService;
    @MockitoBean JwtVerifier jwtVerifier;   // Bearer 폴백 경로는 이 테스트에서 타지 않는다

    @Test
    void createReturns202WithJobId() throws Exception {
        given(reportService.create(eq(COMPANY_ID), any())).willReturn(
                ReportDto.CreateResponse.builder().status("pending").jobId(REPORT_ID).build());

        mvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversation_id":"%s","request_id":"req_001"}""".formatted(CONVERSATION_ID)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.job_id").value(REPORT_ID.toString()));
    }

    @Test
    void createRejectsMissingRequestId() throws Exception {
        mvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversation_id":"%s"}""".formatted(CONVERSATION_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createReturns404WhenConversationMissing() throws Exception {
        willThrow(ReportApiException.notFound("NOT_FOUND", "대화를 찾을 수 없습니다"))
                .given(reportService).create(eq(COMPANY_ID), any());

        mvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversation_id":"%s","request_id":"req_001"}""".formatted(CONVERSATION_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void jobReturnsDraftAndSourcesWhenCompleted() throws Exception {
        given(reportService.getJob(COMPANY_ID, REPORT_ID)).willReturn(ReportDto.JobResponse.builder()
                .status("completed")
                .reportId(REPORT_ID)
                .draftContent("# 수출 적합성 검토 보고서")
                .version(1)
                .sources(List.of(ReportDto.SourceResponse.builder()
                        .documentId("VN-CIRC-2024-01")
                        .title("의약품 수입 요건")
                        .authority("DAV")
                        .version("2024.1")
                        .effectiveDate(LocalDate.of(2024, 3, 1))
                        .section("12")
                        .sourceUrl("https://example.test/vn")
                        .build()))
                .build());

        mvc.perform(get("/api/reports/jobs/{id}", REPORT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.report_id").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.draft_content").value("# 수출 적합성 검토 보고서"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.sources[0].document_id").value("VN-CIRC-2024-01"))
                .andExpect(jsonPath("$.sources[0].effective_date").value("2024-03-01"));
    }

    @Test
    void jobOmitsBodyWhilePending() throws Exception {
        given(reportService.getJob(COMPANY_ID, REPORT_ID)).willReturn(ReportDto.JobResponse.builder()
                .status("pending").reportId(REPORT_ID).build());

        mvc.perform(get("/api/reports/jobs/{id}", REPORT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.draft_content").doesNotExist())
                .andExpect(jsonPath("$.sources").doesNotExist());
    }

    @Test
    void jobRejectsMalformedId() throws Exception {
        mvc.perform(get("/api/reports/jobs/{id}", "job_01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void reviseBumpsVersion() throws Exception {
        given(reportService.revise(COMPANY_ID, REPORT_ID, "3번 항목을 더 자세히")).willReturn(
                ReportDto.ReviseResponse.builder()
                        .reportId(REPORT_ID).draftContent("...(수정됨)...").version(2).build());

        mvc.perform(patch("/api/reports/{id}", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instruction":"3번 항목을 더 자세히"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report_id").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void listReturnsArchiveRows() throws Exception {
        given(reportService.list(COMPANY_ID)).willReturn(List.of(ReportDto.ListItem.builder()
                .reportId(REPORT_ID)
                .drugId(DRUG_ID)
                .countryId("VN")
                .status("completed")
                .version(3)
                .createdAt(Instant.parse("2026-09-03T10:00:00Z"))
                .build()));

        mvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].report_id").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$[0].drug_id").value(DRUG_ID.toString()))
                .andExpect(jsonPath("$[0].country_id").value("VN"))
                .andExpect(jsonPath("$[0].version").value(3))
                .andExpect(jsonPath("$[0].created_at").value("2026-09-03T10:00:00Z"));
    }

    @Test
    void exportStreamsPdf() throws Exception {
        given(reportService.export(COMPANY_ID, REPORT_ID, "pdf")).willReturn(
                new ReportPdf("report-x-v1.pdf", "%PDF-1.6".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/api/reports/{id}/export", REPORT_ID).param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("report-x-v1.pdf")));
    }

    @Test
    void exportRejectsUnsupportedFormat() throws Exception {
        willThrow(ReportApiException.badRequest("VALIDATION_ERROR", "지원하지 않는 내보내기 형식입니다: docx"))
                .given(reportService).export(eq(COMPANY_ID), eq(REPORT_ID), eq("docx"));

        mvc.perform(get("/api/reports/{id}/export", REPORT_ID).param("format", "docx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
