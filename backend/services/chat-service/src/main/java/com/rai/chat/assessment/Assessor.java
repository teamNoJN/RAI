package com.rai.chat.assessment;

import com.rai.chat.dto.ChatDto;

/**
 * 수출 가능성 판정기.
 *
 * <p>MVP 는 {@link MockAssessor} 로 동작하고, ai-service(RAG)가 붙으면 구현체만 교체한다.
 * 보고서 쪽 {@code ReportDrafter} 와 같은 구조다.
 */
public interface Assessor {

    ChatDto.Result assess(AssessmentInput input);
}
