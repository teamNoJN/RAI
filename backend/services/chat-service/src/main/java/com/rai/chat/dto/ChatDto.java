package com.rai.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rai.chat.entity.Message;
import com.rai.chat.entity.Source;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 3 · 3C · 3N · 3R · 3E 화면 계약 (docs/api-spec/screen-03*.md). */
public final class ChatDto {

    private ChatDto() {}

    // --- Request ------------------------------------------------------

    /** 전송 버튼 · 퀵 액션 칩 공용. */
    public record MessageRequest(
            @NotBlank(message = "메시지를 입력해주세요")
            @Size(max = 4000, message = "메시지는 4000자를 넘을 수 없습니다")
            String message
    ) {}

    /** 3C 컨텍스트 변경 — 약은 변경 불가, 국가만. */
    public record ContextRequest(
            @NotBlank(message = "국가를 선택해주세요")
            String countryId
    ) {}

    /** 판정 카드 피드백. */
    public record FeedbackRequest(
            @NotBlank(message = "rating 은 필수입니다")
            @Pattern(regexp = "helpful|needs_revision", message = "rating 은 helpful 또는 needs_revision 이어야 합니다")
            String rating,

            @Size(max = 2000, message = "사유는 2000자를 넘을 수 없습니다")
            String reason
    ) {}

    // --- Response -----------------------------------------------------

    /** 타임라인 복원 (GET messages). */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MessageResponse(String role, String content, String intent,
                                  String status, Instant createdAt) {

        public static MessageResponse from(Message message) {
            return new MessageResponse(message.getRole(), message.getContent(),
                    message.getIntent(), message.getStatus(), message.getCreatedAt());
        }
    }

    /** 판정이 고정한 약+국가 컨텍스트. */
    public record Context(String drugId, String countryId) {}

    /** 성분별 판정 행 — 4번 근거 패널이 그대로 렌더한다. */
    public record IngredientAssessment(String ingredient, String status, String reason) {}

    /** AI 가변 본문. */
    public record Result(String summary, String eligibility,
                         List<IngredientAssessment> ingredientAssessments,
                         List<String> requirements, List<String> risks,
                         List<String> recommendedActions) {}

    /** 판정 근거. sources 가 비면 3R 가드레일 — 문서명·조항을 절대 만들지 않는다. */
    public record SourceResponse(String documentId, String title, String authority,
                                 String version, LocalDate effectiveDate,
                                 String section, String sourceUrl) {

        public static SourceResponse from(Source source) {
            return new SourceResponse(source.getDocumentId(), source.getTitle(), source.getAuthority(),
                    source.getDocumentVersion(), source.getEffectiveDate(),
                    source.getSection(), source.getSourceUrl());
        }
    }

    /**
     * AI 응답 계약. POST(202)와 GET /api/assessments/{id} 가 같은 구조를 쓴다.
     * pending·failed 일 때는 result/sources 가 없으므로 null 필드를 뺀다 (3E).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AssessmentResponse(String requestId, String status, String intent,
                                     Context context, Result result,
                                     List<SourceResponse> sources) {

        /** 202 — 판정 진행 중. FE 는 2초 간격 폴링, 30초 초과 시 3E. */
        public static AssessmentResponse pending(String requestId, String intent, Context context) {
            return new AssessmentResponse(requestId, "pending", intent, context, null, null);
        }

        /** 3E — 실패는 상태만 준다. 화면은 공통 에러 문구 + [재시도]. */
        public static AssessmentResponse failed(String requestId) {
            return new AssessmentResponse(requestId, "failed", null, null, null, null);
        }
    }

    /** 피드백 기록 201. */
    public record FeedbackResponse(String status) {

        public static FeedbackResponse recorded() {
            return new FeedbackResponse("recorded");
        }
    }
}
