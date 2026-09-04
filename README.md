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
| 이 README | 구조·설계 근거·실행·배포. **현재 구현 상태의 정본이다** |

백엔드 작업 전에 명세에서 자기 화면 문서를 읽고, 이 README 의 [데이터 모델](#데이터-모델)에서
어느 서비스가 어느 테이블을 갖는지 확인한다. 아직 구현되지 않은 계획은 맨 아래
[로드맵](#로드맵--아직-구현되지-않음)에 모아 뒀다 — 문서가 코드보다 앞서 나가지 않게 하기 위해서다.

## 아키텍처

MSA. Eureka와 별도 Auth Server는 쓰지 않는다 (근거는 [설계 근거](#설계-근거--왜-이-구조인가)).
서비스 간 통신은 동기 REST(`/internal/**`)이고, 오래 걸리는 작업(판정·보고서)은 서비스 안에서
`@Async` + 상태 폴링으로 처리한다. **메시지 브로커는 아직 쓰지 않는다** (맨 아래 [로드맵](#로드맵--아직-구현되지-않음) 참고).

```
브라우저 ──REST──> API Gateway :8080 ──┬──> user-service :8081  /api/auth/**
          (JWT)    (검증·라우팅·CORS)   ├──> drug-service :8082  /api/drugs/**, /api/countries/**
                                       ├──> chat-service :8083  /api/conversations/**, /api/assessments/**
                                       │                         ← 오케스트레이터
                                       └──> backend      :8090  /api/reports/**, /api/regulations/**
                                                                 └ 규제 KB · PDF 파서 · 보고서 (모놀리스)
                              PostgreSQL 1개 (public 스키마, pgvector 확장)
```

**FE는 서비스가 5개인 걸 모른다.** Gateway 주소 하나(`/api/**`)만 안다.
서비스 이름은 Docker Compose 컨테이너명과 K8s Service명에 동일하게 맞춰, 환경별 URL 분기를 없앤다.
라우팅 정본은 `backend/gateway/src/main/resources/application.yml` 이다.

`backend`(:8090)는 신규 도메인을 서비스로 떼어내고 남은 **모놀리스**다. 규제 문서 적재·파싱과
보고서 생성·PDF 를 담당하고, `chat-service` 가 판정 근거를 `GET /internal/regulations` 로 가져간다.

## 설계 근거 — 왜 이 구조인가

교재(`msa-lecture`)는 컨테이너 10개다: DB, Kafka, **Eureka**, **Auth Server**, API Gateway, 서비스 5개.
무엇을 왜 뺐는지가 곧 설계 근거다.

| 컴포넌트 | 채택 | 근거 |
|---|---|---|
| API Gateway | ✅ | 단일 진입점 + JWT 검증 1곳. K8s Ingress 가 대체하지 못하는 일이다 |
| **Eureka** | ❌ | 아래 3가지 |
| **Auth Server (별도)** | ❌ | user-service 가 JWT 를 발급하면 컨테이너 1개가 준다 |
| Config Server | ❌ | 환경변수 + K8s ConfigMap 으로 충분하다 |
| 메시지 브로커 | ❌ | 지금 규모에선 `@Async` + 상태 폴링으로 충분하다 ([로드맵](#로드맵--아직-구현되지-않음)) |

### Eureka 를 빼는 3가지 근거

1. **K8s 에 배포하므로 기능이 중복된다.** Eureka 가 하는 일("이름 → 주소 + 로드밸런싱")을
   쿠버네티스 **Service** 리소스가 정확히 똑같이 한다. 같이 쓰면 레이어가 두 겹이 된다.
2. **Compose 에서도 컨테이너 이름이 곧 DNS 다.** `http://drug-service:8082` 가 별도 설정 없이 동작한다.
3. **교재 실습 코드조차 Eureka 를 일관되게 쓰지 않는다.** 같은 서비스 안에서
   `http://payment-service:8084/...`(컨테이너 DNS 직접 호출)와 `http://course-service/api/...`(Eureka `lb://`)가
   섞여 있고, `application.yml` 의 URL 프로퍼티는 읽는 코드조차 없다.

### 이름을 통일해 환경 분기를 없앤다

```
Docker Compose : http://drug-service:8082   ← 컨테이너 이름
Kubernetes     : http://drug-service:8082   ← Service 이름
                 ^^^^^^^^^^^^^^^^^^^^^^^^ 똑같다
```

**서비스 이름을 컨테이너명·K8s Service 명에 동일하게 맞춘다.** 그러면 코드도 설정도 하나로 끝난다.
이 규칙은 [배포](#배포) 매니페스트까지 그대로 이어진다.

### Gateway 는 남기는 이유

Gateway 가 하는 일은 K8s 가 대신해 주지 않는다.

1. **JWT 검증을 한 곳에서** — 없으면 서비스마다 검증 코드를 복붙해야 한다
2. **FE 가 볼 주소가 하나** — 로컬 compose 엔 Ingress 가 없다. Gateway 가 없으면 FE 가 8081~8090 을 다 알아야 한다
3. **CORS 설정도 한 곳**

결과적으로 FE 코드는 로컬이든 EKS 든 `/api/**` 하나만 부른다.

## API 공통 규약

명세 `docs/api-spec/README.md` 의 공통 규약이다. **서비스 5개가 전부 동일하게 지킨다.**

| 항목 | 규약 |
|---|---|
| 필드명 | **snake_case** — `spring.jackson.property-naming-strategy: SNAKE_CASE` |
| 성공 응답 | **봉투 없음.** DTO / `List<DTO>` 를 그대로 반환 (`{"success":…,"data":…}` 래퍼 금지) |
| 에러 응답 | `{"error": {"code": "...", "message": "..."}}` |
| 에러 코드 | `VALIDATION_ERROR`(400) `UNAUTHORIZED`(401) `NOT_FOUND`(404) `CONFLICT`(409) `INTERNAL_ERROR`(500) |
| ID | **모두 문자열** — `drug_id`·`conversation_id` 는 UUID, `request_id` 는 `req_` + 8자리(`req_a1b2c3d4`), `country_id` 는 ISO 코드(`VN`) |
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

## 인증 구조

```
[user-service]  로그인 성공 → JWT 발급 (claims: userId, companyId)
      ↓
[FE]            access/refresh 저장 → 모든 요청 헤더에 Bearer 부착
      ↓
[Gateway]       JwtAuthFilter 가 서명·만료 검증
                → X-User-Id, X-Company-Id 헤더로 변환해서 전달
                → ★ /api/auth/{login,signup,refresh} 는 검증 제외 (JwtAuthFilter.PUBLIC_PATHS)
      ↓
[각 서비스]      CurrentUser 리졸버가 헤더를 객체로 받음
```

> **`/api/auth/refresh` 는 검증 제외여야 한다.** access token 이 만료된 상태에서 호출되는
> 엔드포인트라, 빠뜨리면 Gateway 가 401 을 던져 토큰 갱신이 영구히 불가능해지고
> 명세 1E "세션 만료" 화면에서 빠져나올 수 없다.

**Gateway 를 거치지 않은 요청은 헤더가 없다.** 그래서 서비스는 헤더 없는 요청을 401 로 막아야 하는데,
이 강제를 Spring Security 필터가 아니라 **컨트롤러의 `CurrentUser` 리졸버**가 한다
(필터 체인 자체는 `permitAll`). 자세한 주의사항은 [보안](#보안) 참고.

`/internal/**` 은 Gateway 라우팅에 넣지 않는다 — **넣지 않는 것이 곧 외부 차단이다.**
서비스끼리만 컨테이너 네트워크로 호출한다.

## 디렉터리 구조

```
RAI/
├── docs/
│   └── api-spec/                  API 명세 v0.4 확정본 (화면별 17개)
├── backend/                       Gradle 멀티모듈 루트 (Java 21 / Spring Boot 3.4)
│   ├── settings.gradle            include: common, gateway, services:{user,drug,chat}-service
│   ├── src/                       ★ 모놀리스 :8090 — regulation(규제 KB) · parser(PDF) · report
│   ├── common/                    공유 라이브러리 — dto / exception / security / config
│   ├── gateway/                   Spring Cloud Gateway :8080
│   └── services/
│       ├── user-service/          :8081  인증·회사·사용자
│       ├── drug-service/          :8082  제품·버전·성분·국가
│       ├── chat-service/          :8083  대화·판정
│       └── ai-service/            빈 껍데기 — 빌드 대상 아님 (services/ai-service/README.md)
├── frontend/                      Vue 3 + Vite + TypeScript (FE 담당 영역)
├── ai/                            프롬프트 원본 (아직 코드에서 읽지 않음)
├── init-db/                       ★ DB 스키마의 소유자 (01_schema.sql + 02_seed.sql)
├── k8s/                           EKS 배포 매니페스트
├── docker-compose.yml
└── .env.example
```

`common/` 모듈은 **서비스 간 계약서**다. 지금은 `dto/ErrorResponse`(에러 응답 계약),
`security/`(`CurrentUser`·`JwtVerifier`·`AuthHeaders` — Gateway 헤더 인증 계약), 공통 예외가 들어 있다.
서비스마다 DTO를 복붙하면 반드시 어긋난다.

## 사전 준비

- **Java 21** (Temurin 등). Gradle은 wrapper를 쓰므로 설치 불필요
- **Docker Desktop** (compose 포함)
- **Node.js 22+** (frontend)

## 로컬 개발

### 내 서비스만 IDE, 나머지는 컨테이너 (권장)

```bash
# 1) 인프라 + 내가 안 건드리는 서비스는 컨테이너로
docker compose up -d postgres backend gateway user-service drug-service

# 2) 내 담당(예: chat-service)만 직접 실행
cd backend && ./gradlew :services:chat-service:bootRun
```

전부 IDE에서 띄울 필요 없다. 컨테이너로 뜬 서비스와 로컬 서비스가 같은 이름으로 서로를 찾는다.
로컬 실행 서비스는 `localhost:5434`(DB)를 보도록 `local` 프로필에 적혀 있다.

| 상황 | 명령 (`backend/` 에서) |
|---|---|
| 전체 빌드 | `./gradlew build` |
| 내 서비스만 빌드 | `./gradlew :services:chat-service:build` |
| 내 서비스만 실행 | `./gradlew :services:chat-service:bootRun` |
| 테스트 | `./gradlew test` (postgres 컨테이너가 떠 있어야 함) |

### 전체 컨테이너 기동

```bash
docker compose up -d --build
docker compose logs -f                    # 전체
docker compose logs -f rai-chat-service   # 개별 (컨테이너명은 아래 포트 표 참고)
```

### 확인

```bash
# health — actuator 는 gateway 에만 들어 있다. 나머지 서비스는 404 다.
curl -s localhost:8080/actuator/health

# 나머지는 Swagger 로 살아 있는지 본다
open http://localhost:8083/swagger-ui.html

# DB 접속 + 테이블 확인
docker exec -it rai-postgres psql -U rai -d rai_db -c "\dt"
```

Swagger UI는 서비스별로 뜬다: `http://localhost:8081/swagger-ui.html` 등.

## 포트

| 구성요소 | 호스트 포트 | 비고 |
|---|---|---|
| `rai-postgres` | 5434 → 5432 | DB `rai_db`, 계정 `rai`. `.env` 의 `POSTGRES_PORT` 로 변경 |
| `rai-gateway` | **8080** | ★ FE가 바라보는 유일한 주소 |
| `rai-user-service` / `rai-drug-service` / `rai-chat-service` | 8081 / 8082 / 8083 | |
| `rai-backend` | 8090 | 모놀리스 — 규제 KB · 파서 · 보고서 |
| frontend (Vite dev) | 5173 | |

> 이 머신은 5432(로컬 PG), 5433/8080/8081(sjw-* 컨테이너)이 점유 중일 수 있다.
> 겹치면 **호스트 포트만** 바꾼다. 컨테이너 내부 포트와 K8s 포트는 유지해야 환경 간 URL이 같아진다.

## 설정과 프로필

| 프로필 | 용도 |
|---|---|
| `local` (기본) | `localhost:5434` DB, SQL 로그 출력 |
| `docker` | compose 에서 사용. datasource 주소는 environment 로 주입 |
| `k8s` | EKS 배포. Service 이름으로 접속, Secret 주입 |

LLM 을 끄는 프로필은 없다 — **아직 LLM 호출 자체가 없다.** 판정은 규칙 기반 `MockAssessor` 가
프로필과 무관하게 항상 담당한다 (아래 [AI 확장 지점](#ai-확장-지점) 참고).

우선순위: `compose environment` / `K8s env` > `application-{profile}.yml` > `application.yml`

## 데이터 모델

PostgreSQL 1인스턴스 + **단일 `public` 스키마**다. 스키마를 서비스별로 쪼개지는 않았고(로드맵 참고),
소유는 코드 규약으로 지킨다. 아래는 실제 JPA 엔티티 기준이다.

| 소유 | 테이블 |
|---|---|
| user-service | `company`, `app_user` |
| drug-service | `country`, `drug` |
| chat-service | `conversation`, `message`, `assessment`, `source`, `feedback` |
| backend (모놀리스) | `regulation`, `regulation_chunk`, `regulation_revision`, `report` |

정본은 `init-db/01_schema.sql` 이다. 실제 목록은 `psql -c "\dt"` 로 확인한다.
(`analytics_event` 는 스키마에만 있고 매핑하는 엔티티가 아직 없다.)

**규칙 3가지**

1. **다른 서비스의 테이블을 직접 조회하지 않는다.** 필요하면 `/internal/**` REST 로 받는다
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

> **⚠ 볼륨이 남아 있으면 스키마가 갱신되지 않는다 — 가장 자주 밟는 지뢰다.**
> Postgres 의 init 스크립트는 **빈 데이터 디렉터리에서만** 실행된다. 스키마를 고친 뒤 기존 볼륨을
> 그대로 쓰면 옛 스키마가 조용히 남는다.
>
> **증상**: 기동이나 `./gradlew test` 가 `Schema-validation: missing table [assessment]` 로 죽는다.
> **해결**: `docker compose down -v && docker compose up -d postgres`
>
> 데이터를 지켜야 하면, `01_schema.sql` 에는 `DROP` 이 없으므로 기존 DB 에 그대로 적용해도 된다:
> ```bash
> docker exec -i rai-postgres psql -U rai -d rai_db < init-db/01_schema.sql
> ```

ID는 **UUID PK** 를 쓴다 (`uuid-ossp` 확장).

```sql
CREATE TABLE company (
    company_id    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_name  VARCHAR(255) NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## 규제 문서 적재 (backend :8090)

운영자가 검수한 규제 문서를 파일 + 메타데이터로 등록하면
텍스트 추출(PDFBox) → 청크 분할 → `regulation_chunk` 저장까지 수행한다 (`com.rai.parser`).
메타데이터는 판정 결과의 `sources[]` (명세 4번 근거 패널)에 그대로 실린다.

> **임베딩은 아직 생성하지 않는다.** `regulation_chunk.embedding` 컬럼과 pgvector 인덱스는 있지만
> 값이 전부 NULL 이고, 유사도 검색도 없다. 자세한 건 [로드맵](#로드맵--아직-구현되지-않음).

```bash
curl -X POST http://localhost:8080/api/regulations \
  -F file=@./sample.pdf \
  -F documentId=VN-REG-001 -F country=VN \
  -F authority="Drug Administration of Vietnam" -F title="Regulation Title" \
  -F documentVersion=2026.01 -F effectiveDate=2026-01-01 -F section=4.2 \
  -F sourceUrl=https://...
```

```bash
# 적재 확인 — 등록된 문서와 청크 수
docker exec -it rai-postgres psql -U rai -d rai_db \
  -c "SELECT document_id, country_id, title FROM regulation;" \
  -c "SELECT count(*) FROM regulation_chunk;"
```

`scripts/seed-kb.sh` 가 공식 규제 PDF 4건(VN·ID·PH·US)을 이 API 로 한 번에 적재한다.

## AI 확장 지점

**LLM 호출은 아직 없다.** 대신 AI 가 들어올 자리를 인터페이스 3개로 고정해 뒀다.
호출측(`AssessmentWorker`·`ReportGenerationWorker`)은 인터페이스만 알기 때문에, 구현체를 갈아끼워도
워커·FE·API 계약은 한 줄도 바뀌지 않는다.

```java
// chat-service — 근거로 "판정하는" 방식
public interface Assessor            { ChatDto.Result assess(AssessmentInput input); }
   └── MockAssessor                    규칙 기반 (현재 유일)

// chat-service — 근거를 "찾아오는" 방식
public interface RegulationRetriever { List<ChatDto.SourceResponse> retrieve(RetrievalQuery query); }
   └── CountryRegulationRetriever      국가의 ACTIVE 규제 전부 — 유사도 검색이 아니다

// backend — 보고서 본문을 "쓰는" 방식
public interface ReportDrafter       { String draft(DraftContext ctx); String revise(String cur, String ins); }
   └── MockReportDrafter               템플릿 기반 (현재 유일)
```

`RetrievalQuery` 는 지금 구현체가 쓰지도 않는 `question`·`ingredients`·`topK` 까지 실어 나른다.
나중에 벡터 검색 구현체를 꽂을 때 **호출부를 고치지 않기 위해서다.** 프롬프트 초안은 `ai/prompts/` 에 있다.

**가드레일**: 근거가 부족하면 값을 지어내지 말고 `eligibility = REVIEW_REQUIRED` 를 반환한다.
`sources` 가 비면 문서명·조항·시행일을 절대 생성하지 않는다 (명세 3R).
`MockAssessor` 가 이미 이 계약을 코드로 지키고 있고, LLM 으로 바꿔도 유지해야 한다.

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

Harbor → EKS. 실습 환경 계정·비밀번호는 여기 적지 않는다 (안내 PDF 참조).

| 항목 | 값 |
|---|---|
| 클러스터 | `skala-gj` (AWS ap-northeast-2) |
| 네임스페이스 | `skala-gj4` |
| Harbor | `harbor.skala-gj.com` / 프로젝트 `skala-gj4` |
| 도메인 | `skala-gj4-rai.skala-gj.com` |

### 1) 이미지 빌드 → Harbor 푸시

```bash
aws eks update-kubeconfig --name skala-gj --region ap-northeast-2 --profile skala-gj4
kubectl config set-context --current --namespace=skala-gj4

# ★ https:// 를 붙이지 않는다 (붙이면 push 에서 401)
docker login harbor.skala-gj.com -u skala-gj4

# ★ Mac(Apple Silicon)은 반드시 amd64 — EKS 노드가 x86_64. 빌드 컨텍스트는 backend/
cd backend
for s in user-service drug-service chat-service; do
  docker buildx build --platform linux/amd64 -f services/$s/Dockerfile \
    -t harbor.skala-gj.com/skala-gj4/rai-$s:v1 .
  docker push harbor.skala-gj.com/skala-gj4/rai-$s:v1
done

# 모놀리스(:8090)와 gateway 는 Dockerfile 위치가 다르다
docker buildx build --platform linux/amd64 -f Dockerfile \
  -t harbor.skala-gj.com/skala-gj4/rai-backend:v1 .
docker buildx build --platform linux/amd64 -f gateway/Dockerfile \
  -t harbor.skala-gj.com/skala-gj4/rai-gateway:v1 .

cd ../frontend
docker buildx build --platform linux/amd64 \
  -t harbor.skala-gj.com/skala-gj4/rai-frontend:v1 .
```

`k8s/build-push.sh` 가 이 과정을 묶어 둔 스크립트다.

### 2) Secret 2개 (Git 커밋 금지)

```bash
kubectl create secret docker-registry harbor-cred -n skala-gj4 \
  --docker-server=harbor.skala-gj.com \
  --docker-username=skala-gj4 --docker-password='<안내 PDF 참조>'

kubectl create secret generic rai-secret -n skala-gj4 \
  --from-literal=POSTGRES_PASSWORD='...' \
  --from-literal=JWT_SECRET='...'          # ★ 32바이트 이상 (HS256)
```

`k8s/01-secret.example.yaml` 은 **키 이름만** 적은 템플릿이다.

### 3) 매니페스트 적용

| 파일 | 요점 |
|---|---|
| `00-configmap-initdb.yaml` | `init-db/*.sql` → Postgres `/docker-entrypoint-initdb.d` 마운트 |
| `02-postgres.yaml` | Deployment + **PVC(`ebs-sc`)** + Service `postgres`. ebs-sc 는 RWO — DB 에 맞는다 |
| `04`~`06-*-service.yaml` | user / drug / chat. **Service 이름을 컨테이너명과 동일하게** |
| `07-backend.yaml` | 모놀리스 :8090 (규제 KB · 파서 · 보고서) |
| `08-gateway.yaml` | Deployment + Service `gateway:8080` |
| `09-frontend.yaml` | Deployment + Service `frontend:80` |
| `10-ingress.yaml` | `host: skala-gj4-rai.skala-gj.com`, `ingressClassName: nginx` |

`03-kafka.yaml` 도 있지만 **쓰는 코드가 없다** ([로드맵](#로드맵--아직-구현되지-않음)).

```bash
kubectl apply -f k8s/ -n skala-gj4          # k8s/deploy.sh 가 묶어 둔 것
kubectl get all -n skala-gj4
kubectl logs -f deploy/rai-chat-service -n skala-gj4
```

> **여기가 [설계 근거](#설계-근거--왜-이-구조인가)의 결실이다.** `postgres`·`drug-service` 같은 이름이
> Compose 에서는 컨테이너명, K8s 에서는 Service 명으로 **동일하게** 해석된다. 환경별 URL 분기가 없다.

> **Ingress 에 `/api` 규칙이 없는 이유**: frontend 파드의 Nginx 가 이미 `/api` 를 Gateway 로 넘긴다.
> 진입점을 하나로 두면 라우팅이 단순해진다.

### 트러블슈팅

접속: **http://skala-gj4-rai.skala-gj.com** — `https` 가 아니라 **`http`** 다.
`10-ingress.yaml` 에 `tls:` 블록이 없어 80 포트만 열려 있다.

| 증상 | 원인 |
|---|---|
| 브라우저·curl 이 인증서 오류 | `https` 로 접속했다. Ingress 에 TLS 가 없으니 `http` 로 들어간다 (curl 은 exit 60) |
| `exec format error` | arm64 로 빌드했다 → `--platform linux/amd64` 로 재빌드. **가장 흔한 실수** |
| push `401 Unauthorized` | `docker login` 에 `https://` 를 붙였거나 프로젝트명이 `skala-gj4` 가 아님 |
| `ImagePullBackOff` | `harbor-cred` 미생성 또는 이미지 경로 오타 |
| `Forbidden` | 네임스페이스가 `skala-gj4` 가 아님 |
| 파드 계속 `Pending` | 노드가 내려가 있을 수 있음 → 강사에게 기동 요청 |
| Ingress 접속 안 됨 | host 가 `*.skala-gj.com` 인지, `ingressClassName: nginx` 인지 |
| 라우팅이 404 | Gateway `predicates` 에 `Path=` 를 두 번 쓰지 않았는지 (라우트당 한 줄, 패턴은 콤마로 잇는다) |
| 업로드가 413 | Nginx 기본 상한 1MB. 규제 PDF 적재에는 `client_max_body_size` 를 올려야 한다 |

## 보안

- **API 키·비밀번호는 Git에 올리지 않는다.** 로컬은 `.env`(`.gitignore` 포함), Compose는 `environment`,
  EKS는 Secret + `secretKeyRef`
- 한 번이라도 커밋했으면 히스토리를 지워도 유출된 것으로 본다. **즉시 폐기·재발급**
- 각 서비스는 `/internal/**` 을 제외한 모든 요청에서 Gateway가 넣어준 헤더가 없으면 **401을 던진다.**
  다만 이 강제는 Spring Security 필터가 아니라 **컨트롤러의 `CurrentUser` 리졸버**가 한다 —
  필터 체인 자체는 `permitAll` 이다(`SecurityConfig`). 새 컨트롤러에서 `CurrentUser` 파라미터를
  빠뜨리면 그 엔드포인트만 조용히 무인증이 되므로 주의한다
- DB 비밀번호 기본값은 로컬 개발용이다. 배포 환경에서는 반드시 덮어쓴다

## 로드맵 — 아직 구현되지 않음

설계 단계에서 계획했지만 **코드에는 없는** 것들이다.
위 본문의 서술과 섞이지 않게 여기 모아 둔다 — 문서가 코드보다 앞서 나가면 아무도 문서를 믿지 않는다.

| 항목 | 계획 | 현재 |
|---|---|---|
| `ai-service` :8084 | Spring AI·RAG 전담 서비스 | 빈 껍데기. `settings.gradle` 에 include 안 됨 = 빌드 대상 아님 |
| Kafka (토픽 5개) | `assessment.*` / `report.*` / `drug.version.created` 로 chat ↔ ai 연결 | 코드 없음. `@Async` + 상태 폴링. `k8s/03-kafka.yaml` 은 있으나 쓰는 코드가 없다 |
| 임베딩 · 벡터 검색 | OpenAI `text-embedding-3-small`(1536차원) → pgvector 유사도 top-K | `regulation_chunk.embedding` 전량 NULL. 검색은 국가 필터뿐 |
| LLM 판정 · 보고서 | `SpringAiClient` 로 실제 호출 | `MockAssessor` · `MockReportDrafter` (규칙·템플릿 기반) |
| 스키마 분리 | `rai_user` / `rai_drug` / `rai_chat` / `rai_ai` 4개 | 단일 `public` 스키마 |
| 알림 | `GET /api/notifications` | Gateway 라우트만 있고 구현·테이블 모두 없음 |

붙이는 방법은 [AI 확장 지점](#ai-확장-지점)에 적힌 인터페이스 3개에 구현체를 꽂는 것이다.
`backend/services/ai-service/README.md` 에 분리 시 이음새를 정리해 뒀다.
