package com.rai.report.service;

import com.rai.report.dto.ReportDto;
import com.rai.report.entity.Report;
import com.rai.report.entity.ReportStatus;
import com.rai.report.exception.ReportApiException;
import com.rai.report.pdf.ReportPdf;
import com.rai.report.pdf.ReportPdfRenderer;
import com.rai.report.repository.AssessmentSnapshot;
import com.rai.report.repository.ReportQueryRepository;
import com.rai.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 5번(보고서 작업 뷰) · 5L번(보고서 보관함) 유스케이스.
 *
 * 별도 job 테이블을 두지 않는다 — POST 가 report 행을 pending 으로 먼저 만들고
 * 그 report_id 를 job_id 로 돌려준다. 폴링은 findById 한 번이면 끝난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String PDF_FORMAT = "pdf";

    private final ReportRepository reportRepository;
    private final ReportQueryRepository queryRepository;
    private final ReportGenerationWorker generationWorker;
    private final ReportDrafter drafter;
    private final ReportPdfRenderer pdfRenderer;

    /**
     * POST /api/reports — 202.
     *
     * 일부러 {@code @Transactional} 을 붙이지 않았다. 트랜잭션 안에서 비동기 워커를 깨우면
     * 커밋 전에 다른 스레드가 조회를 시작해 방금 만든 행을 못 찾는다.
     * save() 가 자체 트랜잭션으로 커밋을 끝낸 뒤에 워커를 깨워야 한다.
     */
    public ReportDto.CreateResponse create(UUID companyId, ReportDto.CreateRequest request) {
        if (!queryRepository.conversationExists(request.getConversationId(), companyId)) {
            throw ReportApiException.notFound(NOT_FOUND, "대화를 찾을 수 없습니다: " + request.getConversationId());
        }
        AssessmentSnapshot assessment = queryRepository.findAssessment(request.getRequestId())
                .orElseThrow(() -> ReportApiException.notFound(NOT_FOUND,
                        "판정을 찾을 수 없습니다: " + request.getRequestId()));

        // 다른 대화의 판정으로 보고서를 만들면 5L 목록의 제품·국가(conversation 조인)가 본문과 어긋난다.
        if (!assessment.conversationId().equals(request.getConversationId())) {
            throw ReportApiException.notFound(NOT_FOUND,
                    "판정이 이 대화에 속하지 않습니다: " + request.getRequestId());
        }

        Report report = reportRepository.save(Report.builder()
                .conversationId(request.getConversationId())
                .requestId(request.getRequestId())
                .status(ReportStatus.PENDING)
                .version(1)
                .build());

        generationWorker.generate(report.getReportId());

        return ReportDto.CreateResponse.builder()
                .status(ReportStatus.PENDING)
                .jobId(report.getReportId())
                .build();
    }

    /** GET /api/reports/jobs/{job_id} — 2초 폴링·30초 타임아웃(FE)의 대상. */
    @Transactional(readOnly = true)
    public ReportDto.JobResponse getJob(UUID companyId, UUID jobId) {
        Report report = findOwned(companyId, jobId);

        // pending·failed 는 본문이 없다. FE 는 status 만 보고 폴링을 계속하거나 3E 를 띄운다.
        if (!report.isCompleted()) {
            return ReportDto.JobResponse.builder()
                    .status(report.getStatus())
                    .reportId(report.getReportId())
                    .build();
        }
        return ReportDto.JobResponse.builder()
                .status(report.getStatus())
                .reportId(report.getReportId())
                .draftContent(report.getDraftContent())
                .sources(queryRepository.findSources(report.getRequestId()))
                .version(report.getVersion())
                .build();
    }

    /** PATCH /api/reports/{report_id} — 수정 채팅 1건 = version +1 (동기 200). */
    @Transactional
    public ReportDto.ReviseResponse revise(UUID companyId, UUID reportId, String instruction) {
        Report report = findOwned(companyId, reportId);
        if (!report.isCompleted()) {
            throw ReportApiException.badRequest(VALIDATION_ERROR,
                    "초안 생성이 끝난 보고서만 수정할 수 있습니다 (현재 상태: " + report.getStatus() + ")");
        }

        report.setDraftContent(drafter.revise(report.getDraftContent(), instruction));
        report.setVersion(report.getVersion() + 1);
        log.info("보고서 수정: {} → v{}", reportId, report.getVersion());

        return ReportDto.ReviseResponse.builder()
                .reportId(report.getReportId())
                .draftContent(report.getDraftContent())
                .version(report.getVersion())
                .build();
    }

    /**
     * GET /api/reports — 5L 보관함. 완료된 보고서만 보여준다(생성 중·실패는 목록에 없다).
     *
     * @param companyId Gateway 가 JWT 에서 꺼내 넣어주는 X-Company-Id. 아직 Gateway 가 없어
     *                  null 이면 필터하지 않는다 — 붙는 즉시 회사 격리가 켜진다.
     */
    @Transactional(readOnly = true)
    public List<ReportDto.ListItem> list(UUID companyId) {
        return queryRepository.findCompletedListItems(companyId);
    }

    /** GET /api/reports/{report_id}/export?format=pdf — MVP 는 PDF 만. */
    @Transactional(readOnly = true)
    public ReportPdf export(UUID companyId, UUID reportId, String format) {
        if (!PDF_FORMAT.equalsIgnoreCase(format)) {
            throw ReportApiException.badRequest(VALIDATION_ERROR,
                    "지원하지 않는 내보내기 형식입니다: " + format + " (MVP 는 pdf 만 지원합니다)");
        }
        Report report = findOwned(companyId, reportId);
        if (!report.isCompleted()) {
            throw ReportApiException.badRequest(VALIDATION_ERROR,
                    "초안 생성이 끝난 보고서만 내보낼 수 있습니다 (현재 상태: " + report.getStatus() + ")");
        }
        return pdfRenderer.render(report);
    }

    /**
     * 다른 회사 보고서는 존재해도 404 로 가린다 — 있다/없다가 새면 그 자체가 정보다.
     */
    private Report findOwned(UUID companyId, UUID reportId) {
        if (!queryRepository.reportBelongsToCompany(reportId, companyId)) {
            throw ReportApiException.notFound(NOT_FOUND, "보고서를 찾을 수 없습니다: " + reportId);
        }
        return findOrThrow(reportId);
    }

    private Report findOrThrow(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> ReportApiException.notFound(NOT_FOUND, "보고서를 찾을 수 없습니다: " + reportId));
    }
}
