package com.rai.report.service;

import com.rai.report.dto.ReportDto;
import com.rai.report.entity.Report;
import com.rai.report.entity.ReportStatus;
import com.rai.report.repository.AssessmentSnapshot;
import com.rai.report.repository.ReportQueryRepository;
import com.rai.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 초안 생성 백그라운드 작업 (POST /api/reports 202 이후 단계).
 *
 * ReportService 와 같은 클래스에 두면 self-invocation 이라 프록시를 타지 않아 @Async 가 무시된다.
 * 반드시 별도 빈으로 유지할 것.
 *
 * 트랜잭션을 열어두지 않는다. 초안 생성은 LLM 이 붙으면 15~30초가 걸리는데 그동안 커넥션을 잡고
 * 있을 이유가 없고, @Async 와 @Transactional 을 겹치면 적용 순서에 따라 트랜잭션이 호출 스레드에
 * 열려 워커 스레드에서 변경이 반영되지 않을 수 있다. 조회·저장을 각각 짧게 끊는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerationWorker {

    private final ReportRepository reportRepository;
    private final ReportQueryRepository queryRepository;
    private final ReportDrafter drafter;

    @Async("reportTaskExecutor")
    public void generate(UUID reportId) {
        Report report = reportRepository.findById(reportId).orElse(null);
        if (report == null) {
            log.warn("생성 대상 보고서를 찾을 수 없습니다: {}", reportId);
            return;
        }
        try {
            AssessmentSnapshot assessment = queryRepository.findAssessment(report.getRequestId())
                    .orElseThrow(() -> new IllegalStateException("판정을 찾을 수 없습니다: " + report.getRequestId()));
            List<ReportDto.SourceResponse> sources = queryRepository.findSources(report.getRequestId());

            report.setDraftContent(drafter.draft(new ReportDrafter.DraftContext(assessment, sources)));
            report.setStatus(ReportStatus.COMPLETED);
            log.info("보고서 초안 생성 완료: {}", reportId);
        } catch (Exception e) {
            // 예외를 삼키고 상태만 failed 로 남긴다 — FE 는 폴링 응답의 status 로 3E 를 띄운다.
            log.error("보고서 초안 생성 실패: {}", reportId, e);
            report.setStatus(ReportStatus.FAILED);
        }
        reportRepository.save(report);
    }
}
