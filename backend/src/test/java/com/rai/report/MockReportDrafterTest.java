package com.rai.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rai.report.dto.ReportDto;
import com.rai.report.repository.AssessmentSnapshot;
import com.rai.report.service.MockReportDrafter;
import com.rai.report.service.ReportDrafter;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 판정 JSONB → 마크다운 초안 조립. AI 연동 전까지 이 결과가 5번 화면의 본문이 된다. */
class MockReportDrafterTest {

    private final MockReportDrafter drafter = new MockReportDrafter(new ObjectMapper());

    private static final String RESULT_JSON = """
            {
              "ingredient_assessments": [
                {"ingredient": "Amoxicillin", "status": "CONDITIONAL", "reason": "등록 서류 보완 필요"}
              ],
              "requirements": ["수입 허가 취득"],
              "risks": ["규제 개정 예정"],
              "recommended_actions": ["DAV 사전 문의"]
            }
            """;

    @Test
    void assemblesAllSectionsFromAssessment() {
        String draft = drafter.draft(new ReportDrafter.DraftContext(
                new AssessmentSnapshot("req_001", UUID.randomUUID(), "completed",
                        "CONDITIONAL", "조건부 수출 가능", RESULT_JSON),
                List.of(ReportDto.SourceResponse.builder()
                        .documentId("VN-CIRC-2024-01").title("의약품 수입 요건").authority("DAV")
                        .version("2024.1").effectiveDate(LocalDate.of(2024, 3, 1)).section("12")
                        .sourceUrl("https://example.test/vn").build())));

        assertThat(draft)
                .contains("# 수출 적합성 검토 보고서")
                .contains("req_001")
                .contains("조건부 가능 (CONDITIONAL)")
                .contains("조건부 수출 가능")
                .contains("| Amoxicillin | CONDITIONAL | 등록 서류 보완 필요 |")
                .contains("- 수입 허가 취득")
                .contains("- 규제 개정 예정")
                .contains("- DAV 사전 문의")
                .contains("의약품 수입 요건 (DAV · 2024.1 · 시행 2024-03-01 · 12조)");
    }

    @Test
    void survivesMissingResultAndSources() {
        String draft = drafter.draft(new ReportDrafter.DraftContext(
                new AssessmentSnapshot("req_002", UUID.randomUUID(), "completed", null, null, null),
                List.of()));

        assertThat(draft)
                .contains("성분별 판정 결과가 없습니다")
                .contains("해당 사항 없음")
                .contains("등록된 근거 출처가 없습니다");
    }

    @Test
    void reviseAppendsInstructionToExistingContent() {
        String revised = drafter.revise("# 원본 본문", "3번 항목을 더 자세히");

        assertThat(revised).startsWith("# 원본 본문").contains("3번 항목을 더 자세히");
    }
}
