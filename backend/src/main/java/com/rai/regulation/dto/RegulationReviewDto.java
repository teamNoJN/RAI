package com.rai.regulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rai.regulation.entity.RegulationRevision;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;

public class RegulationReviewDto {

    /** GET /api/regulations/feed 목록 카드. */
    @Getter
    @Builder
    public static class FeedItem {
        @JsonProperty("regulation_id") private UUID regulationId;
        @JsonProperty("country_id") private String countryId;
        @JsonProperty("regulation_type") private String regulationType;
        private String title;
        private String summary;
        @JsonProperty("effective_date") private LocalDate effectiveDate;
        @JsonProperty("source_url") private String sourceUrl;
        @JsonProperty("review_status") private String reviewStatus;
        @JsonProperty("created_at") private Instant createdAt;

        static FeedItem from(RegulationRevision r) {
            return FeedItem.builder()
                    .regulationId(r.getRegulationId())
                    .countryId(r.getCountryId())
                    .regulationType(r.getRegulationType())
                    .title(r.getTitle())
                    .summary(r.getSummary())
                    .effectiveDate(r.getEffectiveDate())
                    .sourceUrl(r.getSourceUrl())
                    .reviewStatus(r.getReviewStatus())
                    .createdAt(r.getCreatedAt())
                    .build();
        }

        public static java.util.List<FeedItem> from(java.util.List<RegulationRevision> revisions) {
            return revisions.stream().map(FeedItem::from).toList();
        }
    }

    /** GET /api/regulations/{regulation_id} 상세 — 개정 전/후 대조. */
    @Getter
    @Builder
    public static class Detail {
        @JsonProperty("regulation_id") private UUID regulationId;
        @JsonProperty("country_id") private String countryId;
        @JsonProperty("regulation_type") private String regulationType;
        private String title;
        private String before;
        private String after;
        @JsonProperty("ai_summary") private String aiSummary;
        @JsonProperty("effective_date") private LocalDate effectiveDate;
        @JsonProperty("source_url") private String sourceUrl;
        @JsonProperty("review_status") private String reviewStatus;
        @JsonProperty("reflected_at") private Instant reflectedAt;
        @JsonProperty("reflected_by") private String reflectedBy;

        /** reflectedByName: reflected_by(user_id) → 표시 이름 변환 결과. REFLECTED 가 아니면 null. */
        public static Detail from(RegulationRevision r, Function<UUID, String> reflectedByName) {
            String reflectedBy = r.getReflectedBy() == null ? null : reflectedByName.apply(r.getReflectedBy());
            return Detail.builder()
                    .regulationId(r.getRegulationId())
                    .countryId(r.getCountryId())
                    .regulationType(r.getRegulationType())
                    .title(r.getTitle())
                    .before(r.getBeforeContent())
                    .after(r.getAfterContent())
                    .aiSummary(r.getAiSummary())
                    .effectiveDate(r.getEffectiveDate())
                    .sourceUrl(r.getSourceUrl())
                    .reviewStatus(r.getReviewStatus())
                    .reflectedAt(r.getReflectedAt())
                    .reflectedBy(reflectedBy)
                    .build();
        }
    }

    /** POST /api/regulations/{regulation_id}/review 요청. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewRequest {
        @NotNull(message = "approved 는 필수입니다")
        private Boolean approved;
    }

    /** POST /api/regulations/{regulation_id}/review 응답. */
    @Getter
    @Builder
    public static class ReviewResponse {
        @JsonProperty("regulation_id") private UUID regulationId;
        @JsonProperty("review_status") private String reviewStatus;
        @JsonProperty("reflected_at") private Instant reflectedAt;
        @JsonProperty("reflected_by") private String reflectedBy;

        public static ReviewResponse from(RegulationRevision r, String reflectedByName) {
            return ReviewResponse.builder()
                    .regulationId(r.getRegulationId())
                    .reviewStatus(r.getReviewStatus())
                    .reflectedAt(r.getReflectedAt())
                    .reflectedBy(reflectedByName)
                    .build();
        }
    }
}
