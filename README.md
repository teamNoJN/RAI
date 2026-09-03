# RAI (Regulatory AI)

제약·바이오 RA 담당자를 위한 규제 검토 코파일럿. 제품과 수출 대상 국가를 고정한 뒤 자연어 질문으로
수출 가능성 검토 → 규제 근거 확인 → 보고서 초안 작성까지 한 대화 안에서 처리한다.

```
로그인 → 제품 선택/등록 → 국가 선택 → 채팅 질문 → 수출 가능성 검토
→ 규제 근거 확인 → 보고서 초안 생성 → 채팅으로 수정 → PDF 내보내기
```

## 문서

| 문서 | 내용 |
|---|---|
| **[docs/api-spec/](docs/api-spec/)** | **API 명세 v0.4 (확정)** — 화면별 17개. 코드와 어긋나면 이쪽이 맞다 |
| [docs/00-project-plan.md](docs/00-project-plan.md) | 아키텍처 결정 근거, 엔드포인트↔서비스 매핑, ERD, 배포, 3일 일정 |

백엔드 작업 시작 전에 **계획서 4부(API 계약 → 구현 매핑)** 를 먼저 읽는다.
"내 엔드포인트가 어느 서비스이고 어느 테이블·토픽을 쓰는가"가 거기 다 있다.

## 아키텍처

MSA + Kafka. Eureka와 별도 Auth Server는 쓰지 않는다 (근거는 계획서 0부).

```
브라우저 ──REST──> API Gateway :8080 ──┬──> user-service  :8081   rai_user
          (JWT)    (검증·라우팅·CORS)   ├──> drug-service  :8082   rai_drug
                                       ├──> chat-service  :8083   rai_chat   ← 오케스트레이터
                                       └──> ai-service    :8084   rai_ai
                                                 ↕ Kafka (토픽 5개)
                              PostgreSQL + pgvector · OpenAI
```

**FE는 서비스가 4개인 걸 모른다.** Gateway 주소 하나(`/api/**`)만 안다.
서비스 이름은 Docker Compose 컨테이너명과 K8s Service명에 동일하게 맞춰, 환경별 URL 분기를 없앤다.

### Kafka 토픽

| 토픽 | 발행 → 구독 | 언제 |
|---|---|---|
| `assessment.requested` | chat → ai | 사용자가 채팅으로 질문을 보냄 |
| `assessment.completed` | ai → chat | 판정이 끝남 (성공/실패 모두) |
| `report.requested` | chat → ai | `POST /api/reports` 호출 |
| `report.completed` | ai → chat | 보고서 초안 생성 완료 |
| `drug.version.created` | drug → chat | 성분·버전 변경 → 재판정 트리거 |

## API 공통 규약

명세 `docs/api-spec/README.md` 의 공통 규약이다. **서비스 4개가 전부 동일하게 지킨다.**

| 항목 | 규약 |
|---|---|
| 필드명 | **snake_case** — `spring.jackson.property-naming-strategy: SNAKE_CASE` |
| 성공 응답 | **봉투 없음.** DTO / `List<DTO>` 를 그대로 반환 (`{"success":…,"data":…}` 래퍼 금지) |
| 에러 응답 | `{"error": {"code": "...", "message": "..."}}` |
| 에러 코드 | `VALIDATION_ERROR`(400) `UNAUTHORIZED`(401) `NOT_FOUND`(404) `CONFLICT`(409) `INTERNAL_ERROR`(500) |
| ID | **모두 문자열** — `"D001"` `"CV01"` `"req_001"`, `country_id`는 ISO 코드(`VN`) |
| 날짜·시각 | ISO-8601 UTC `2026-09-03T10:00:00Z` (`Instant` 사용) |
| 인증 | `Authorization: Bearer {access_token}` → Gateway가 `X-User-Id`/`X-Company-Id` 헤더로 변환 |
| 비동기 | `pending → completed / failed` · 2초 폴링 · 30초 타임아웃 |

판정과 보고서 생성은 즉시 `202`를 반환하고 폴링한다.

```
POST /api/conversations/{id}/messages  → 202 { "request_id": "req_001", "status": "pending" }
GET  /api/assessments/req_001          → 2초마다 폴링, completed 되면 판정 카드 렌더

POST /api/reports                      → 202 { "job_id": "job_01", "status": "pending" }
GET  /api/reports/jobs/job_01          → 동일 패턴
```

## 디렉터리 구조

```
RAI/
├── docs/
│   ├── api-spec/                  API 명세 v0.4 확정본 (화면별 17개)
│   └── 00-project-plan.md         프로젝트 계획서
├── backend/                       Gradle 멀티모듈 루트 (Java 21 / Spring Boot 3.4)
│   ├── settings.gradle            include: common, gateway, services:*
│   ├── common/                    공유 라이브러리 — event / dto / exception / security / config
│   ├── gateway/                   Spring Cloud Gateway :8080
│   └── services/
│       ├── user-service/          :8081  인증·회사·사용자
│       ├── drug-service/          :8082  제품·버전·성분·국가
│       ├── chat-service/          :8083  대화·판정·보고서·알림
│       └── ai-service/            :8084  Spring AI·RAG·규제 KB (parser 포함)
├── frontend/                      Vue 3 + Vite + TypeScript (FE 담당 영역)
├── ai/                            프롬프트 원본 · Golden Test Set
├── init-db/                       ★ DB 스키마의 소유자 (스키마 4개 + 시드)
├── k8s/                           EKS 배포 매니페스트
├── docker-compose.yml
└── .env.example
```

`common/` 모듈은 **서비스 간 계약서**다. Kafka 이벤트 record와 `AssessmentResult`를 여기 두고
발행·구독 양쪽이 같은 클래스를 쓴다. 서비스마다 DTO를 복붙하면 반드시 어긋난다.

## 사전 준비

- **Java 21** (Temurin 등). Gradle은 wrapper를 쓰므로 설치 불필요
- **Docker Desktop** (compose 포함)
- **Node.js 22+** (frontend)

## 로컬 개발

### 내 서비스만 IDE, 나머지는 컨테이너 (권장)

```bash
# 1) 인프라 + 내가 안 건드리는 서비스는 컨테이너로
docker compose up -d postgres kafka gateway user-service drug-service ai-service

# 2) 내 담당(예: chat-service)만 직접 실행
cd backend && ./gradlew :services:chat-service:bootRun
```

5개를 다 IDE에서 띄울 필요 없다. 컨테이너로 뜬 서비스와 로컬 서비스가 같은 이름으로 서로를 찾는다.
로컬 실행 서비스는 `localhost:5434`(DB) / `localhost:9092`(Kafka)를 보도록 `local` 프로필에 적혀 있다.

| 상황 | 명령 (`backend/` 에서) |
|---|---|
| 전체 빌드 | `./gradlew build` |
| 내 서비스만 빌드 | `./gradlew :services:chat-service:build` |
| 내 서비스만 실행 | `./gradlew :services:chat-service:bootRun` |
| 테스트 | `./gradlew test` (postgres 컨테이너가 떠 있어야 함) |

### 전체 컨테이너 기동

```bash
docker compose up -d --build
docker compose logs -f            # 전체
docker compose logs -f rai-chat   # 개별
```

### 확인

```bash
# 각 서비스 health
for p in 8080 8081 8082 8083 8084; do curl -s localhost:$p/actuator/health; echo; done

# Kafka 토픽 (5개)
docker exec -it rai-kafka kafka-topics --bootstrap-server localhost:9092 --list

# DB 접속
docker exec -it rai-postgres psql -U rai -d rai_db
```

Swagger UI는 서비스별로 뜬다: `http://localhost:8081/swagger-ui.html` 등.

## 포트

| 구성요소 | 호스트 포트 | 비고 |
|---|---|---|
| `rai-postgres` | 5434 → 5432 | DB `rai_db`, 계정 `rai`. `.env` 의 `POSTGRES_PORT` 로 변경 |
| `rai-kafka` | 9092 | KRaft 단일 브로커 |
| `rai-gateway` | **8080** | ★ FE가 바라보는 유일한 주소 |
| `rai-user` / `rai-drug` / `rai-chat` / `rai-ai` | 8081 / 8082 / 8083 / 8084 | |
| frontend (Vite dev) | 5173 | |

> 이 머신은 5432(로컬 PG), 5433/8080/8081(sjw-* 컨테이너)이 점유 중일 수 있다.
> 겹치면 **호스트 포트만** 바꾼다. 컨테이너 내부 포트와 K8s 포트는 유지해야 환경 간 URL이 같아진다.

## 설정과 프로필

| 프로필 | 용도 |
|---|---|
| `local` (기본) | `localhost:5434` DB / `localhost:9092` Kafka, SQL 로그 출력 |
| `docker` | compose 에서 사용. datasource·kafka 주소는 environment 로 주입 |
| `k8s` | EKS 배포. Service 이름으로 접속, Secret 주입 |
| `mock` | `MockAiClient` 활성화 — LLM 호출 없이 고정 JSON 판정 반환 |

우선순위: `compose environment` / `K8s env` > `application-{profile}.yml` > `application.yml`

## 데이터 모델

PostgreSQL 1인스턴스 + **스키마 4개**로 서비스별 소유를 나눈다.

| 스키마 | 소유 | 테이블 |
|---|---|---|
| `rai_user` | user-service | companies, users |
| `rai_drug` | drug-service | countries, drugs, drug_versions, drug_ingredients, drug_target_countries |
| `rai_chat` | chat-service | conversations, messages, assessments, assessment_citations, assessment_feedback, reports, notifications, analytics_events |
| `rai_ai` | ai-service | regulation_documents, vector_store |

**규칙 3가지**

1. **다른 서비스의 스키마를 직접 조회하지 않는다.** 필요하면 `/internal/**` REST나 Kafka로 받는다
2. **서비스 경계를 넘는 FK는 만들지 않는다.** `conversations.drug_id`는 값만 저장하고
   유효성은 `GET /internal/drugs/{id}` 로 확인한다
3. **모든 조회에 `company_id` 조건을 넣는다.** ID만으로 조회하면 다른 회사 데이터가 새어 나간다.
   `company_id`는 사용자가 보낸 값이 아니라 Gateway가 JWT에서 꺼내 헤더로 넣어준 값을 쓴다

### 스키마의 주인은 `init-db/*.sql` 이다

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

JPA는 "일치하는지 검사만" 한다. 엔티티에 필드를 추가했는데 SQL을 안 고치면 **서버가 아예 안 뜬다.**
버그가 아니라 안전장치다. 엔티티를 고치면 DDL도 같이 고치고 `docker compose down -v` 후 다시 올린다.

ID는 명세대로 **VARCHAR PK + 시퀀스 DEFAULT** 를 쓴다.

```sql
CREATE SEQUENCE rai_drug.drug_seq;
CREATE TABLE rai_drug.drugs (
  drug_id VARCHAR(16) PRIMARY KEY
          DEFAULT 'D' || lpad(nextval('rai_drug.drug_seq')::text, 3, '0'),
  ...
);
```

## 규제 문서 적재 (ai-service)

운영자가 검수한 규제 문서를 파일 + 메타데이터로 등록하면
텍스트 추출(PDFBox) → 청크 분할 → 임베딩 생성 → `vector_store` 저장까지 수행한다.
메타데이터는 판정 결과의 `sources[]` (명세 4번 근거 패널)에 그대로 실린다.

```bash
curl -X POST http://localhost:8080/api/regulations \
  -F file=@./sample.pdf \
  -F documentId=VN-REG-001 -F country=VN \
  -F authority="Drug Administration of Vietnam" -F title="Regulation Title" \
  -F documentVersion=2026.01 -F effectiveDate=2026-01-01 -F section=4.2 \
  -F sourceUrl=https://...
```

```bash
# 적재 확인
docker exec -it rai-postgres psql -U rai -d rai_db \
  -c "SELECT count(*) FROM rai_ai.vector_store;"
```

> 임베딩은 OpenAI `text-embedding-3-small` (**1536차원**) 기준이다.
> 모델을 바꾸면 차원이 달라져 `vector_store` DDL과 기존 벡터를 전부 다시 만들어야 한다.

## AI 확장 지점

인터페이스 2개로 교체 지점을 고정한다. Kafka Consumer는 인터페이스만 알기 때문에
구현체를 갈아끼워도 Consumer·chat-service·FE 코드는 한 줄도 바뀌지 않는다.

```java
public interface AiClient {              // 교체 지점 1 — AI 호출 방식
    AssessmentResult assessExport(AssessmentRequestedEvent request);
    ReportDraft      generateReport(ReportRequestedEvent request);
    ReportDraft      reviseReport(String reportId, String instruction);
}
   ├── MockAiClient    @Profile("mock")    고정 JSON
   └── SpringAiClient  @Profile("!mock")   실제 LLM

public interface RegulationRetriever {   // 교체 지점 2 — 근거 검색 방식
    List<Document> retrieve(String query, String countryId, int topK);
}
   └── PgVectorRetriever
```

**가드레일**: 근거가 부족하면 값을 지어내지 말고 `eligibility = REVIEW_REQUIRED` 를 반환한다.
`sources` 가 비면 문서명·조항·시행일을 절대 생성하지 않는다 (명세 3R).

## 프론트엔드

Vue 3 + Vite + TypeScript + Vue Router + Pinia. 린트는 ESLint + oxlint, 포맷은 Prettier.

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
```

| 스크립트 | 용도 |
|---|---|
| `npm run dev` | 개발 서버 |
| `npm run build` | 타입체크 + 프로덕션 빌드 |
| `npm run lint` | oxlint + eslint (--fix) |
| `npm run format` | prettier |

`vite.config.ts` 의 `/api` 프록시가 Gateway(8080)를 가리키므로 개발 중 CORS가 발생하지 않는다.
배포 시에는 Nginx가 FE와 `/api`를 같은 도메인으로 서빙한다.

```ts
server: { proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } } }
```

## 배포

Harbor → EKS(`skala-gj4`). 상세 절차와 트러블슈팅은 [계획서 6부](docs/00-project-plan.md) 참조.

```bash
# ★ Mac(Apple Silicon)은 반드시 amd64 — EKS 노드가 x86_64
docker buildx build --platform linux/amd64 \
  -f services/ai-service/Dockerfile \
  -t harbor.skala-gj.com/skala-gj4/rai-ai-service:v1 .
```

arm64로 푸시하면 파드가 `exec format error`로 죽는다. 가장 흔한 실수다.

## 보안

- **API 키·비밀번호는 Git에 올리지 않는다.** 로컬은 `.env`(`.gitignore` 포함), Compose는 `environment`,
  EKS는 Secret + `secretKeyRef`
- 한 번이라도 커밋했으면 히스토리를 지워도 유출된 것으로 본다. **즉시 폐기·재발급**
- 각 서비스는 `/internal/**` 을 제외한 모든 요청에서 Gateway가 넣어준 헤더가 없으면 **401을 던진다.**
  `SecurityConfig` 에 `permitAll` 을 두지 않는다
- DB 비밀번호 기본값은 로컬 개발용이다. 배포 환경에서는 반드시 덮어쓴다
