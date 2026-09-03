package com.rai.chat.assessment;

import com.rai.chat.dto.ChatDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 규칙 기반 임시 판정기. LLM 이 아니라 검색된 근거의 유무·본문만 본다.
 *
 * <p><b>가드레일(screen-03r)</b>: 근거가 없으면 임의로 판정하지 않고 REVIEW_REQUIRED 를 준다.
 * 문서명·조항·시행일을 지어내지 않는 책임은 여기서 시작한다 — sources 를 만들지 않고,
 * 판정 근거로 삼지 않은 내용을 summary 에 쓰지 않는다.
 */
@Slf4j
@Component
public class MockAssessor implements Assessor {

    private static final String NO_EVIDENCE_SUMMARY = "현재 등록된 규제 자료만으로 판단하기 어렵습니다.";
    private static final String REVIEW_REASON = "현재 검색된 규제에서 판단 근거를 찾지 못했습니다.";
    private static final String NO_RESTRICTION_REASON =
            "현재 검색된 규제에서 직접적인 제한이 확인되지 않았습니다.";
    private static final String CONDITIONAL_REASON =
            "검색된 규제에 조건부 허용으로 볼 수 있는 문구가 있어 확인이 필요합니다.";

    /** 조건부로 볼 신호. 실제 구현체에서는 LLM 이 판단할 영역이다. */
    private static final List<String> CONDITIONAL_HINTS =
            List.of("조건", "제한", "허가", "승인", "restrict", "condition", "approval");

    @Override
    public ChatDto.Result assess(AssessmentInput input) {
        if (input.sources().isEmpty()) {
            // 3R: 근거가 없다. 성분별로도 판정하지 않는다.
            log.info("근거 없음 → REVIEW_REQUIRED. country={} product={}",
                    input.countryId(), input.productName());
            return new ChatDto.Result(
                    NO_EVIDENCE_SUMMARY,
                    Eligibility.REVIEW_REQUIRED,
                    input.ingredients().stream()
                            .map(i -> new ChatDto.IngredientAssessment(i, Eligibility.REVIEW_REQUIRED, REVIEW_REASON))
                            .toList(),
                    List.of(), List.of(), List.of());
        }

        boolean conditional = hasConditionalHint(input);
        List<ChatDto.IngredientAssessment> ingredients = input.ingredients().stream()
                .map(i -> conditional
                        ? new ChatDto.IngredientAssessment(i, Eligibility.CONDITIONAL, CONDITIONAL_REASON)
                        : new ChatDto.IngredientAssessment(i, Eligibility.NO_RESTRICTION, NO_RESTRICTION_REASON))
                .toList();

        String eligibility = conditional ? Eligibility.CONDITIONAL : Eligibility.POSSIBLE;
        String summary = conditional
                ? "%s 수출 시 확인이 필요한 조건이 있습니다. 근거 %d건을 확인하세요."
                        .formatted(input.countryId(), input.sources().size())
                : "%s 규제 %d건을 검토한 결과 직접적인 제한은 확인되지 않았습니다."
                        .formatted(input.countryId(), input.sources().size());

        return new ChatDto.Result(summary, eligibility, ingredients,
                conditional ? List.of("규제 원문의 해당 조항을 검토하고 필요 서류를 확인하세요.") : List.of(),
                List.of(),
                List.of("규제 담당자 검토 후 제출하세요."));
    }

    private boolean hasConditionalHint(AssessmentInput input) {
        String haystack = input.sources().stream()
                .map(s -> (s.title() == null ? "" : s.title()) + " " + (s.section() == null ? "" : s.section()))
                .reduce("", (a, b) -> a + " " + b)
                .toLowerCase(Locale.ROOT);
        return CONDITIONAL_HINTS.stream().anyMatch(haystack::contains);
    }

    /** 명세 enum. eligibility 와 성분 status 가 값 집합을 공유한다. */
    public static final class Eligibility {
        public static final String POSSIBLE = "POSSIBLE";
        public static final String CONDITIONAL = "CONDITIONAL";
        public static final String REVIEW_REQUIRED = "REVIEW_REQUIRED";
        public static final String RESTRICTED = "RESTRICTED";
        public static final String NO_RESTRICTION = "NO_RESTRICTION";

        private Eligibility() {}
    }
}
