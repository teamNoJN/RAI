package com.rai.chat.assessment;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 메시지 → intent 3종 (screen-03). 퀵 칩은 정해진 문장이라 규칙으로 충분하고,
 * 자유 입력은 ai-service 가 붙으면 그쪽으로 넘긴다.
 */
@Component
public class IntentClassifier {

    public static final String EXPORT_ELIGIBILITY_CHECK = "EXPORT_ELIGIBILITY_CHECK";
    public static final String REPORT_GENERATE = "REPORT_GENERATE";
    public static final String REPORT_REVISE = "REPORT_REVISE";

    public String classify(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        boolean mentionsReport = text.contains("보고서") || text.contains("report");
        if (mentionsReport) {
            // 수정 의도가 먼저다 — "보고서 수정해줘"는 생성이 아니다.
            boolean revise = text.contains("수정") || text.contains("고쳐") || text.contains("바꿔")
                    || text.contains("다시 써") || text.contains("revise");
            return revise ? REPORT_REVISE : REPORT_GENERATE;
        }
        return EXPORT_ELIGIBILITY_CHECK;
    }
}
