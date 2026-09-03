package com.rai.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 5번(보고서 작업 뷰) · 5L번(보고서 보관함) 요청/응답 계약.
 * 명세 공통 규약에 따라 JSON 필드는 snake_case 로 노출한다.
 */
public class ReportDto {

    /** POST /api/reports 요청 — 4번 [이 근거로 보고서에 반영] · 5번 진입. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class CreateRequest {
        @NotNull(message = "conversation_id 는 필수입니다")
        private UUID conversationId;

        @NotBlank(message = "request_id 는 필수입니다")
        private String requestId;
    }

    /** POST /api/reports 202 응답. job_id 는 생성된 report_id 를 그대로 사용한다. */
    @Getter
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class CreateResponse {
        private String status;
        private UUID jobId;
    }

    /** GET /api/reports/jobs/{job_id} 응답 — 생성 폴링(2초 간격, 30초 타임아웃). */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class JobResponse {
        private String status;
        private UUID reportId;
        private String draftContent;
        private List<SourceResponse> sources;
        private Integer version;
    }

    /** PATCH /api/reports/{report_id} 요청 — 우측 수정 채팅. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ReviseRequest {
        @NotBlank(message = "instruction 은 필수입니다")
        private String instruction;
    }

    /** PATCH /api/reports/{report_id} 200 응답. */
    @Getter
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ReviseResponse {
        private UUID reportId;
        private String draftContent;
        private Integer version;
    }

    /** GET /api/reports 200 응답 항목 (5L 보관함 목록). */
    @Getter
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ListItem {
        private UUID reportId;
        private UUID drugId;
        private String countryId;
        private String status;
        private Integer version;
        private Instant createdAt;
    }

    /**
     * 판정 근거 출처 — 4번 패널과 동일 스키마.
     * source.document_version 이 명세상 `version` 으로 노출된다.
     */
    @Getter
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class SourceResponse {
        private String documentId;
        private String title;
        private String authority;
        private String version;
        private LocalDate effectiveDate;
        private String section;
        private String sourceUrl;
    }
}
