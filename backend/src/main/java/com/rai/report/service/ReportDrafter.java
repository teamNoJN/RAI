package com.rai.report.service;

import com.rai.report.dto.ReportDto;
import com.rai.report.repository.AssessmentSnapshot;

import java.util.List;

/**
 * 보고서 초안 생성·수정 전략.
 *
 * MVP 는 {@link MockReportDrafter} 로 동작하고, AI 서비스가 붙으면 구현체만 교체한다.
 * 이 인터페이스가 5번 화면과 AI 파이프라인 사이의 계약이다.
 */
public interface ReportDrafter {

    /** 판정 결과와 근거로 초안 본문을 만든다. */
    String draft(DraftContext context);

    /** 수정 지시를 반영해 본문을 다시 만든다 (PATCH — 호출 1건 = version +1). */
    String revise(String currentContent, String instruction);

    record DraftContext(AssessmentSnapshot assessment, List<ReportDto.SourceResponse> sources) {
    }
}
