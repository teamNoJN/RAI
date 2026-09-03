package com.rai.chat.assessment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** intent 3종 분기 (screen-03). 퀵 칩 문장이 정확히 갈리는지가 중요하다. */
class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    @Test
    void 수출_질문은_판정_intent() {
        assertThat(classifier.classify("이 제품 베트남 수출 가능한가?"))
                .isEqualTo(IntentClassifier.EXPORT_ELIGIBILITY_CHECK);
    }

    @Test
    void 재검토_요청도_판정_intent() {
        assertThat(classifier.classify("이 제품 다시 판정해줘"))
                .isEqualTo(IntentClassifier.EXPORT_ELIGIBILITY_CHECK);
    }

    @Test
    void 보고서_요청은_생성_intent() {
        assertThat(classifier.classify("보고서 만들어줘"))
                .isEqualTo(IntentClassifier.REPORT_GENERATE);
    }

    @Test
    void 보고서_수정_요청은_생성이_아니라_수정_intent() {
        assertThat(classifier.classify("보고서 결론 부분 수정해줘"))
                .isEqualTo(IntentClassifier.REPORT_REVISE);
    }
}
