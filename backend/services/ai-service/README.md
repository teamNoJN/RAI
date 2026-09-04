# ai-service — 아직 없는 서비스

**이 디렉토리는 패키지 골격만 있는 자리표시자다.** 실제로는 아무것도 들어 있지 않다.

- `build.gradle` · `application.yml` · `@SpringBootApplication` 클래스 **없음**
- 루트 `settings.gradle` 의 `include` 목록에 **없음** → **Gradle 빌드 대상이 아니다**
- `docker-compose.yml` · `k8s/` 에도 **없음**

설계 단계에서는 여기에 Spring AI + pgvector RAG 가 들어가고 메시지 브로커로 `chat-service` 와
이어지는 그림이었다. 그건 아직 **계획일 뿐 구현이 아니다** — 루트 `README.md` 의 로드맵 참고.

## 그 역할은 지금 어디가 하고 있나

| 설계상 자리 | 실제 위치 | 실제 동작 |
|---|---|---|
| `AiClient` (판정) | `chat-service` 의 `Assessor` ← `MockAssessor` | LLM 없음. 근거 텍스트의 키워드로 판정 |
| `RegulationRetriever` (검색) | `chat-service` 의 `RegulationRetriever` ← `CountryRegulationRetriever` | 유사도 검색 없음. 국가의 ACTIVE 규제 전부 |
| 문서 파싱·청킹 | 모놀리스 `backend/src` 의 `com.rai.parser` | 동작함. PDF → 텍스트 → 고정 길이 청크 |
| 임베딩 저장 | `regulation_chunk.embedding vector(1536)` | 컬럼과 인덱스는 있으나 **값이 전부 NULL** |
| chat ↔ ai 이벤트 | `@Async` + DB 상태 폴링 | Kafka 없음 |

## 분리한다면 이음새는 어디인가

1. `RegulationRetriever` 구현체를 HTTP 호출로 바꾼다. `RegulationClient` 의 baseUrl 은 이미
   `ServiceUrlProperties` 로 설정화돼 있어 URL 만 바뀐다.
2. `Assessor` 구현체를 여기로 옮긴다. `AssessmentWorker` 는 인터페이스만 알아서 안 바뀐다.
3. `com.rai.parser` 와 `regulation`/`regulation_chunk` 테이블 소유권을 옮긴다 — 이게 유일한
   진짜 마이그레이션이다. 판정 시점 근거 스냅샷(`source`)은 `chat-service` 에 그대로 남는다.

먼저 할 일은 서비스를 세우는 게 아니라 **`regulation_chunk.embedding` 을 채우는 것**이다.
그게 없으면 이 서비스를 만들어도 검색할 벡터가 없다.
