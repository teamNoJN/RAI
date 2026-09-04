package com.rai.chat.client;

import com.rai.chat.assessment.RegulationRetriever;
import com.rai.chat.assessment.RetrievalQuery;
import com.rai.chat.dto.ChatDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 현재 유일한 검색 구현. 규제 KB 에서 <b>해당 국가의 ACTIVE 규제를 전부</b> 가져온다.
 *
 * <p>{@link RetrievalQuery} 의 question·ingredients·topK 를 쓰지 않는 것은 이게 유사도 검색이
 * 아니기 때문이다. 벡터 검색 구현체가 들어오면 그때부터 쓰인다 — 그래서 인터페이스는
 * 지금부터 그 정보를 받아 둔다.
 */
@Component
@RequiredArgsConstructor
public class CountryRegulationRetriever implements RegulationRetriever {

    private final RegulationClient regulationClient;

    @Override
    public List<ChatDto.SourceResponse> retrieve(RetrievalQuery query) {
        return regulationClient.findSources(query.countryId());
    }
}
