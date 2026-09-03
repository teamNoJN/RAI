package com.rai.report.repository;

import java.util.UUID;

/**
 * 보고서 생성에 필요한 판정 스냅샷 (읽기 전용).
 *
 * @param resultJson assessment.result JSONB 원문. AI 응답 계약이 바뀌어도 스키마를 고정하지 않기 위해
 *                   문자열로 받아 사용하는 쪽에서 해석한다.
 */
public record AssessmentSnapshot(
        String requestId,
        UUID conversationId,
        String status,
        String eligibility,
        String summary,
        String resultJson
) {
}
