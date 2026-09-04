package com.rai.chat.assessment;

import java.util.List;

/**
 * 근거 검색 질의.
 *
 * <p>지금 구현체({@code CountryRegulationRetriever})는 {@code countryId} 만 쓰고 나머지는 버린다.
 * 그런데도 질문·성분·topK 를 시그니처에 담아 두는 이유는, 벡터 검색 구현체가 들어올 때
 * <b>호출부를 한 줄도 고치지 않기 위해서다</b>. 이 record 가 확장 지점의 실물이다.
 *
 * @param question    사용자 질문. 임베딩 검색의 주 질의가 된다.
 * @param ingredients 제품 성분. 대개 영문·라틴어라 영문 규제 원문과 잘 붙는다 —
 *                    벡터 검색 구현체는 question 과 이어 붙여 임베딩하는 게 좋다.
 * @param topK        가져올 근거 수. 유사도 검색이 아닌 지금 구현체는 무시한다.
 */
public record RetrievalQuery(String countryId, String question, List<String> ingredients, int topK) {

    public static final int DEFAULT_TOP_K = 5;

    public RetrievalQuery(String countryId, String question, List<String> ingredients) {
        this(countryId, question, ingredients, DEFAULT_TOP_K);
    }
}
