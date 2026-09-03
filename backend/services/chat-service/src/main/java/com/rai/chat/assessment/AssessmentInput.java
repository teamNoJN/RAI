package com.rai.chat.assessment;

import com.rai.chat.dto.ChatDto;

import java.util.List;

/**
 * 판정에 필요한 재료. 제품 정보는 drug-service, 규제 근거는 규제 KB 에서 모아온다.
 *
 * @param sources 검색된 근거. 비어 있으면 판정기는 3R 가드레일로 가야 한다.
 */
public record AssessmentInput(
        String question,
        String productName,
        List<String> ingredients,
        String countryId,
        List<ChatDto.SourceResponse> sources
) {
}
