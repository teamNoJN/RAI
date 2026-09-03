package com.rai.regulation.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;

/**
 * 판정 근거 스냅샷 (서비스 간 내부 계약).
 * chat-service 가 판정 시점 값을 source 테이블에 복사해 박제한다 — 규제가 개정돼도
 * 그때의 근거가 남아야 감사에 안전하다 (init-db/01_schema.sql source 주석).
 *
 * <p>이 앱은 기본이 camelCase 라서 내부 계약을 snake_case 로 못박아 둔다 —
 * 호출측(chat-service)은 snake_case 를 기대한다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RegulationSourceDto(
        String documentId,
        String title,
        String authority,
        String documentVersion,
        LocalDate effectiveDate,
        String section,
        String sourceUrl
) {
}
