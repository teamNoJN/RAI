package com.rai.report.controller;

import com.rai.report.dto.ReportDto;
import com.rai.report.pdf.ReportPdf;
import com.rai.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 명세 5번(보고서 작업 뷰) · 5L번(보고서 보관함).
 *
 * 응답은 공통 규약대로 봉투 없이 DTO 를 그대로 낸다 (운영자용 API 의 ApiResponse 를 쓰지 않는다).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 초안 생성 요청 — 즉시 202, 본문은 폴링으로 받는다. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReportDto.CreateResponse create(@Valid @RequestBody ReportDto.CreateRequest request) {
        return reportService.create(request);
    }

    /** 생성 폴링. job_id 는 POST 202 가 돌려준 값(=report_id)이다. */
    @GetMapping("/jobs/{job_id}")
    public ReportDto.JobResponse job(@PathVariable("job_id") UUID jobId) {
        return reportService.getJob(jobId);
    }

    /** 5L 보관함 목록 (완료된 보고서만). */
    @GetMapping
    public List<ReportDto.ListItem> list(
            @RequestHeader(name = "X-Company-Id", required = false) UUID companyId) {
        return reportService.list(companyId);
    }

    /** 수정 채팅 — 호출 1건 = version +1. */
    @PatchMapping("/{report_id}")
    public ReportDto.ReviseResponse revise(@PathVariable("report_id") UUID reportId,
                                           @Valid @RequestBody ReportDto.ReviseRequest request) {
        return reportService.revise(reportId, request.getInstruction());
    }

    /** PDF 내보내기 — 파일 스트림. */
    @GetMapping("/{report_id}/export")
    public ResponseEntity<byte[]> export(@PathVariable("report_id") UUID reportId,
                                         @RequestParam(name = "format", defaultValue = "pdf") String format) {
        ReportPdf pdf = reportService.export(reportId, format);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // 파일명은 ASCII 고정이라 charset 을 붙이지 않는다 (붙이면 =?UTF-8?Q?..?= 로 인코딩된다)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }
}
