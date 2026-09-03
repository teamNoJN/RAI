package com.rai.report;

import com.rai.report.dto.ReportDto;
import com.rai.report.entity.Report;
import com.rai.report.entity.ReportStatus;
import com.rai.report.exception.ReportApiException;
import com.rai.report.pdf.ReportPdfRenderer;
import com.rai.report.repository.AssessmentSnapshot;
import com.rai.report.repository.ReportQueryRepository;
import com.rai.report.repository.ReportRepository;
import com.rai.report.service.ReportDrafter;
import com.rai.report.service.ReportGenerationWorker;
import com.rai.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * POST /api/reports 의 사전 검증 (screen-04 근거 패널 → 보고서 반영 진입점).
 * 존재하지 않는 대화·판정으로 보고서를 만들지 못하게 막는 것이 핵심이다.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final String REQUEST_ID = "req_001";

    @Mock ReportRepository reportRepository;
    @Mock ReportQueryRepository queryRepository;
    @Mock ReportGenerationWorker generationWorker;
    @Mock ReportDrafter drafter;
    @Mock ReportPdfRenderer pdfRenderer;

    @InjectMocks ReportService reportService;

    @Test
    void 대화와_판정이_있으면_pending_보고서를_만든다() {
        UUID conversationId = UUID.randomUUID();

        when(queryRepository.conversationExists(conversationId)).thenReturn(true);
        when(queryRepository.findAssessment(REQUEST_ID))
                .thenReturn(Optional.of(assessment(conversationId)));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setReportId(UUID.randomUUID());
            return report;
        });

        ReportDto.CreateResponse response = reportService.create(request(conversationId, REQUEST_ID));

        assertThat(response.getStatus()).isEqualTo(ReportStatus.PENDING);
        // job_id 는 별도 테이블 없이 방금 만든 report_id 를 그대로 쓴다.
        assertThat(response.getJobId()).isNotNull();
        verify(generationWorker).generate(response.getJobId());
    }

    @Test
    void 대화가_없으면_404_이고_저장하지_않는다() {
        UUID conversationId = UUID.randomUUID();
        when(queryRepository.conversationExists(conversationId)).thenReturn(false);

        assertThatThrownBy(() -> reportService.create(request(conversationId, REQUEST_ID)))
                .isInstanceOf(ReportApiException.class)
                .hasMessageContaining("대화를 찾을 수 없습니다");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void 판정이_없으면_404_이고_저장하지_않는다() {
        UUID conversationId = UUID.randomUUID();
        when(queryRepository.conversationExists(conversationId)).thenReturn(true);
        when(queryRepository.findAssessment("req_missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.create(request(conversationId, "req_missing")))
                .isInstanceOf(ReportApiException.class)
                .hasMessageContaining("판정을 찾을 수 없습니다");
        verify(reportRepository, never()).save(any());
    }

    /** 다른 대화의 판정을 끌어오면 5L 목록의 제품·국가가 본문과 어긋난다. */
    @Test
    void 판정이_다른_대화의_것이면_404_이고_저장하지_않는다() {
        UUID conversationId = UUID.randomUUID();
        when(queryRepository.conversationExists(conversationId)).thenReturn(true);
        when(queryRepository.findAssessment(REQUEST_ID))
                .thenReturn(Optional.of(assessment(UUID.randomUUID())));

        assertThatThrownBy(() -> reportService.create(request(conversationId, REQUEST_ID)))
                .isInstanceOf(ReportApiException.class)
                .hasMessageContaining("판정이 이 대화에 속하지 않습니다");
        verify(reportRepository, never()).save(any());
    }

    private ReportDto.CreateRequest request(UUID conversationId, String requestId) {
        // CreateRequest 에는 @Builder 가 없다(@AllArgsConstructor 만).
        return new ReportDto.CreateRequest(conversationId, requestId);
    }

    private AssessmentSnapshot assessment(UUID conversationId) {
        return new AssessmentSnapshot(REQUEST_ID, conversationId, "completed",
                "ELIGIBLE", "수출 가능", "{}");
    }
}
