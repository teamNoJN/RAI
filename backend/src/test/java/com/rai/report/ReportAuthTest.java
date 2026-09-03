package com.rai.report;

import com.rai.common.config.CommonWebConfig;
import com.rai.common.security.AuthHeaders;
import com.rai.common.security.JwtVerifier;
import com.rai.report.controller.ReportController;
import com.rai.report.dto.ReportDto;
import com.rai.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보고서 API 의 인증·회사 격리.
 *
 * <p>한때 이 엔드포인트들이 인증 없이 열려 있었고, 5L 목록은 X-Company-Id 가 없으면
 * 전 회사 보고서를 그대로 돌려줬다. 같은 구멍이 다시 열리면 여기서 잡는다.
 */
@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CommonWebConfig.class)
class ReportAuthTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID REPORT_ID = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockitoBean ReportService reportService;
    @MockitoBean JwtVerifier jwtVerifier;

    @Test
    void 인증_없이_보관함을_열면_401_이고_조회하지_않는다() throws Exception {
        mvc.perform(get("/api/reports"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        verify(reportService, never()).list(any());
    }

    @Test
    void 인증_없이_보고서를_만들_수_없다() throws Exception {
        mvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversation_id\":\"%s\",\"request_id\":\"req_001\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());

        verify(reportService, never()).create(any(), any());
    }

    @Test
    void 인증_없이_잡을_폴링할_수_없다() throws Exception {
        mvc.perform(get("/api/reports/jobs/{id}", REPORT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_없이_PDF_를_내보낼_수_없다() throws Exception {
        mvc.perform(get("/api/reports/{id}/export", REPORT_ID))
                .andExpect(status().isUnauthorized());
    }

    /** 목록 조회는 반드시 요청자의 company_id 로 좁혀서 내려가야 한다. */
    @Test
    void 보관함은_요청자의_회사로만_조회한다() throws Exception {
        given(reportService.list(COMPANY)).willReturn(List.<ReportDto.ListItem>of());

        mvc.perform(get("/api/reports")
                        .header(AuthHeaders.USER_ID, UUID.randomUUID().toString())
                        .header(AuthHeaders.COMPANY_ID, COMPANY.toString()))
                .andExpect(status().isOk());

        verify(reportService).list(COMPANY);
    }
}
