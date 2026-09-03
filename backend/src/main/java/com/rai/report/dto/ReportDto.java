package com.rai.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rai.report.entity.Report;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

public class ReportDto {

    /** [이 근거로 보고서에 반영] 트리거 (screen-04). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotNull(message = "conversation_id 는 필수입니다")
        @JsonProperty("conversation_id")
        private UUID conversationId;

        @NotBlank(message = "request_id 는 필수입니다")
        @JsonProperty("request_id")
        private String requestId;
    }

    @Getter
    @Builder
    public static class CreateResponse {
        @JsonProperty("status")
        private String status;

        @JsonProperty("job_id")
        private String jobId;

        public static CreateResponse from(Report report) {
            return CreateResponse.builder()
                    .status(report.getStatus())
                    .jobId(report.getReportId().toString())
                    .build();
        }
    }
}
