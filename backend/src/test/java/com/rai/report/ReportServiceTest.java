package com.rai.report;

import com.rai.common.exception.NotFoundException;
import com.rai.report.dto.ReportDto;
import com.rai.report.entity.Report;
import com.rai.report.repository.ReportRepository;
import com.rai.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportRepository reportRepository;
    @InjectMocks ReportService reportService;

    @Test
    void createsPendingReportWhenConversationAndAssessmentExist() {
        UUID conversationId = UUID.randomUUID();
        ReportDto.CreateRequest request = ReportDto.CreateRequest.builder()
                .conversationId(conversationId).requestId("req_001").build();

        when(reportRepository.existsConversation(conversationId)).thenReturn(true);
        when(reportRepository.existsAssessment("req_001", conversationId)).thenReturn(true);
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> {
            Report r = inv.getArgument(0);
            r.setReportId(UUID.randomUUID());
            return r;
        });

        Report result = reportService.create(request);

        assertThat(result.getStatus()).isEqualTo("pending");
        assertThat(result.getConversationId()).isEqualTo(conversationId);
        assertThat(result.getRequestId()).isEqualTo("req_001");
        assertThat(result.getReportId()).isNotNull();
    }

    @Test
    void throwsNotFoundWhenConversationMissing() {
        UUID conversationId = UUID.randomUUID();
        ReportDto.CreateRequest request = ReportDto.CreateRequest.builder()
                .conversationId(conversationId).requestId("req_001").build();

        when(reportRepository.existsConversation(conversationId)).thenReturn(false);

        assertThatThrownBy(() -> reportService.create(request))
                .isInstanceOf(NotFoundException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void throwsNotFoundWhenAssessmentMissing() {
        UUID conversationId = UUID.randomUUID();
        ReportDto.CreateRequest request = ReportDto.CreateRequest.builder()
                .conversationId(conversationId).requestId("req_missing").build();

        when(reportRepository.existsConversation(conversationId)).thenReturn(true);
        when(reportRepository.existsAssessment("req_missing", conversationId)).thenReturn(false);

        assertThatThrownBy(() -> reportService.create(request))
                .isInstanceOf(NotFoundException.class);
        verify(reportRepository, never()).save(any());
    }
}
