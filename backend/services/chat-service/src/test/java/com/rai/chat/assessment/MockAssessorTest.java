package com.rai.chat.assessment;

import com.rai.chat.dto.ChatDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3R 가드레일 — 근거가 없을 때 판정을 지어내지 않는지가 핵심이다.
 * "적합처럼 보이게" 렌더되면 규제 담당자가 잘못된 판단을 하게 된다.
 */
class MockAssessorTest {

    private final MockAssessor assessor = new MockAssessor();

    @Test
    void 근거가_없으면_REVIEW_REQUIRED_로_명시한다() {
        ChatDto.Result result = assessor.assess(input(List.of()));

        assertThat(result.eligibility()).isEqualTo("REVIEW_REQUIRED");
        assertThat(result.summary()).isEqualTo("현재 등록된 규제 자료만으로 판단하기 어렵습니다.");
    }

    @Test
    void 근거가_없으면_성분도_전부_REVIEW_REQUIRED_다() {
        ChatDto.Result result = assessor.assess(input(List.of()));

        assertThat(result.ingredientAssessments())
                .allSatisfy(i -> assertThat(i.status()).isEqualTo("REVIEW_REQUIRED"));
    }

    @Test
    void 근거가_없으면_요구사항_권고를_만들어내지_않는다() {
        ChatDto.Result result = assessor.assess(input(List.of()));

        assertThat(result.requirements()).isEmpty();
        assertThat(result.risks()).isEmpty();
        assertThat(result.recommendedActions()).isEmpty();
    }

    @Test
    void 근거가_있으면_판정하고_근거_건수를_요약에_밝힌다() {
        ChatDto.Result result = assessor.assess(input(List.of(source("의약품 등록 규정", "4.2"))));

        assertThat(result.eligibility()).isIn("POSSIBLE", "CONDITIONAL");
        assertThat(result.summary()).contains("1건");
        assertThat(result.ingredientAssessments()).hasSize(2);
    }

    @Test
    void 제한_신호가_있는_근거는_조건부로_본다() {
        ChatDto.Result result = assessor.assess(input(List.of(source("성분 제한 고시", "4.2"))));

        assertThat(result.eligibility()).isEqualTo("CONDITIONAL");
        assertThat(result.ingredientAssessments())
                .allSatisfy(i -> assertThat(i.status()).isEqualTo("CONDITIONAL"));
    }

    private AssessmentInput input(List<ChatDto.SourceResponse> sources) {
        return new AssessmentInput("베트남 수출 가능한가?", "아목시실린 캡슐",
                List.of("Amoxicillin", "첨가제 B"), "500mg", "capsule", "VN", sources);
    }

    private ChatDto.SourceResponse source(String title, String section) {
        return new ChatDto.SourceResponse("VN-REG-001", title, "Drug Administration of Vietnam",
                "2026.01", LocalDate.of(2026, 1, 1), section, "https://example.test");
    }
}
