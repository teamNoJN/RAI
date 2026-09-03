package com.rai.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rai.report.dto.ReportDto;
import com.rai.report.repository.AssessmentSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * LLM 연동 전까지 쓰는 템플릿 기반 초안 생성기.
 * assessment.result(JSONB) 와 근거 출처를 마크다운으로 조립할 뿐, 새로운 판단을 하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockReportDrafter implements ReportDrafter {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Map<String, String> ELIGIBILITY_LABELS = Map.of(
            "POSSIBLE", "수출 가능",
            "CONDITIONAL", "조건부 가능",
            "REVIEW_REQUIRED", "추가 검토 필요",
            "RESTRICTED", "제한됨");

    private final ObjectMapper objectMapper;

    @Override
    public String draft(DraftContext context) {
        AssessmentSnapshot assessment = context.assessment();
        StringBuilder sb = new StringBuilder();

        sb.append("# 수출 적합성 검토 보고서\n\n");
        sb.append("- 판정 ID: ").append(assessment.requestId()).append('\n');
        sb.append("- 판정 결과: ").append(label(assessment.eligibility())).append('\n');
        sb.append("- 작성 시각: ").append(LocalDateTime.now().format(TIMESTAMP)).append("\n\n");

        sb.append("## 1. 요약\n\n");
        sb.append(blankToDash(assessment.summary())).append("\n\n");

        JsonNode result = parseResult(assessment.resultJson());

        sb.append("## 2. 성분별 검토\n\n");
        appendIngredients(sb, result.path("ingredient_assessments"));

        appendBulletSection(sb, "## 3. 요구사항\n\n", result.path("requirements"));
        appendBulletSection(sb, "## 4. 리스크\n\n", result.path("risks"));
        appendBulletSection(sb, "## 5. 권고 조치\n\n", result.path("recommended_actions"));

        sb.append("## 6. 근거 출처\n\n");
        appendSources(sb, context.sources());

        return sb.toString();
    }

    @Override
    public String revise(String currentContent, String instruction) {
        // Mock 단계에서는 본문을 재생성할 수 없으므로 수정 요청을 이력으로 덧붙인다.
        // 실제 구현체는 currentContent 를 지시에 맞게 재작성해 반환해야 한다.
        return currentContent
                + "\n---\n\n### 수정 요청 반영 (Mock)\n\n"
                + "- " + LocalDateTime.now().format(TIMESTAMP) + " — " + instruction + '\n';
    }

    private void appendIngredients(StringBuilder sb, JsonNode ingredients) {
        if (!ingredients.isArray() || ingredients.isEmpty()) {
            sb.append("성분별 판정 결과가 없습니다.\n\n");
            return;
        }
        sb.append("| 성분 | 판정 | 사유 |\n|---|---|---|\n");
        for (JsonNode node : ingredients) {
            sb.append("| ").append(node.path("ingredient").asText("-"))
              .append(" | ").append(node.path("status").asText("-"))
              .append(" | ").append(node.path("reason").asText("-"))
              .append(" |\n");
        }
        sb.append('\n');
    }

    private void appendBulletSection(StringBuilder sb, String heading, JsonNode items) {
        sb.append(heading);
        if (!items.isArray() || items.isEmpty()) {
            sb.append("해당 사항 없음\n\n");
            return;
        }
        for (JsonNode item : items) {
            sb.append("- ").append(item.isTextual() ? item.asText() : item.toString()).append('\n');
        }
        sb.append('\n');
    }

    private void appendSources(StringBuilder sb, List<ReportDto.SourceResponse> sources) {
        if (sources.isEmpty()) {
            sb.append("등록된 근거 출처가 없습니다.\n");
            return;
        }
        int index = 1;
        for (ReportDto.SourceResponse source : sources) {
            sb.append(index++).append(". ").append(blankToDash(source.getTitle()));
            sb.append(" (").append(blankToDash(source.getAuthority()));
            if (source.getVersion() != null) {
                sb.append(" · ").append(source.getVersion());
            }
            if (source.getEffectiveDate() != null) {
                sb.append(" · 시행 ").append(source.getEffectiveDate());
            }
            if (source.getSection() != null) {
                sb.append(" · ").append(source.getSection()).append("조");
            }
            sb.append(')');
            if (source.getSourceUrl() != null) {
                sb.append(" — ").append(source.getSourceUrl());
            }
            sb.append('\n');
        }
    }

    private JsonNode parseResult(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(resultJson);
        } catch (Exception e) {
            log.warn("assessment.result 파싱 실패 — 빈 본문으로 진행합니다", e);
            return objectMapper.createObjectNode();
        }
    }

    private String label(String eligibility) {
        if (eligibility == null) {
            return "-";
        }
        return ELIGIBILITY_LABELS.getOrDefault(eligibility, eligibility) + " (" + eligibility + ")";
    }

    private String blankToDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
