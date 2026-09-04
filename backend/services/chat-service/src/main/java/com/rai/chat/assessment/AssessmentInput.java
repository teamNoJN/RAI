package com.rai.chat.assessment;

import com.rai.chat.dto.ChatDto;

import java.util.List;

/**
 * 판정에 필요한 재료. 제품 정보는 drug-service, 규제 근거는 규제 KB 에서 모아온다.
 *
 * <p>그대로 EXPORT_ELIGIBILITY_CHECK 프롬프트의 [Input] 블록이 된다.
 * {@link MockAssessor} 는 규칙 기반이라 strength·dosageForm 을 보지 않지만,
 * ai-service 의 LLM 구현체는 이 둘로 제형·함량별 규제를 가른다.
 *
 * @param sources 검색된 근거. 비어 있으면 판정기는 3R 가드레일로 가야 한다.
 */
public record AssessmentInput(
        String question,
        String productName,
        List<String> ingredients,
        String strength,
        String dosageForm,
        String countryId,
        List<ChatDto.SourceResponse> sources
) {
}
