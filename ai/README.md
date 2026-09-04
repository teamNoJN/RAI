# ai/ — 프롬프트 원본

LLM 을 붙일 때 쓸 프롬프트의 **원본 보관소**다. 어느 서비스의 classpath 에도 올라가 있지 않고,
현재 코드에서 읽지 않는다 — 아직 LLM 호출이 없기 때문이다.

## 지금 상태

| | 상태 |
|---|---|
| 프롬프트 초안 | `prompts/export-eligibility-{system,user}.st` |
| 실제 LLM 호출 | **없음** — 판정은 `chat-service` 의 `MockAssessor`(규칙 기반)가 한다 |
| 임베딩·유사도 검색 | **없음** — 근거는 `CountryRegulationRetriever` 가 국가로 거른 ACTIVE 규제 전부 |

## 붙일 때

1. 이 파일들을 쓰는 서비스의 `src/main/resources/prompts/` 로 옮긴다.
2. `Assessor` 구현체를 하나 더 만들어(예: `LlmAssessor`) 프롬프트를 채우고 구조화 출력으로 받는다.
3. `MockAssessor` 와 둘 중 하나만 뜨도록 조건부 빈으로 가른다. 호출측(`AssessmentWorker`)은 안 바뀐다.

프롬프트의 가드레일(3·5번 규칙)은 `MockAssessor` 가 이미 코드로 지키고 있는 것과 같은 계약이다 —
근거가 없으면 `REVIEW_REQUIRED`, 문서명·조항은 절대 만들지 않음, `sources` 는 검색 결과에서만 채움.
LLM 으로 바꿔도 이 계약이 유지되어야 한다.
