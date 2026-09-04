package com.rai.chat.assessment;

import com.rai.chat.dto.ChatDto;

import java.util.List;

/**
 * 근거를 <b>찾아오는 방식</b>. {@link Assessor}(근거로 <b>판정하는 방식</b>)와 한 쌍으로
 * AI 확장 지점을 이룬다 — 둘 다 구현체를 갈아끼워도 {@code AssessmentWorker} 는 안 바뀐다.
 *
 * <p>지금 구현체는 {@code CountryRegulationRetriever} 하나뿐이고 국가로 거르기만 한다.
 * <b>유사도 검색이 아니다</b> — 질문과 무관한 문서도 그대로 올라온다. RAG 를 붙인다는 건
 * {@code regulation_chunk.embedding} 을 채우고, 질문+성분을 임베딩해 코사인 유사도로 훑는
 * 구현체를 여기에 꽂는다는 뜻이다.
 *
 * <p><b>계약</b>: 검색이 실패하든 결과가 없든 예외를 올리지 않고 빈 목록을 준다.
 * 그래야 판정기가 3R 가드레일(REVIEW_REQUIRED)로 안전하게 떨어진다 — 없는 근거를
 * 지어내는 것보다 낫다.
 */
public interface RegulationRetriever {

    List<ChatDto.SourceResponse> retrieve(RetrievalQuery query);
}
