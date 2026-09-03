package com.rai.report.service;

import com.rai.common.exception.NotFoundException;
import com.rai.report.dto.ReportDto;
import com.rai.report.entity.Report;
import com.rai.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;

    /** screen-04 [이 근거로 보고서에 반영] → 보고서 초안 생성 작업을 pending 으로 접수한다. */
    public Report create(ReportDto.CreateRequest request) {
        if (!reportRepository.existsConversation(request.getConversationId())) {
            throw new NotFoundException("대화 세션을 찾을 수 없습니다: " + request.getConversationId());
        }
        if (!reportRepository.existsAssessment(request.getRequestId(), request.getConversationId())) {
            throw new NotFoundException("판정 결과를 찾을 수 없습니다: " + request.getRequestId());
        }

        Report report = Report.builder()
                .conversationId(request.getConversationId())
                .requestId(request.getRequestId())
                .status("pending")
                .build();
        return reportRepository.save(report);
    }
}
