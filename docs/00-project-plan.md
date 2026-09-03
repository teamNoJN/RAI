# RAI 프로젝트 계획서

> 기준 문서: **API 명세 v0.4 (확정)** — `docs/api-spec/` 17개 파일.
> 이 계획서와 명세가 어긋나면 **명세가 맞다.** 명세를 고쳐야 하는 안건은 [부록](#부록-미해결-안건)에 모은다.

제약·바이오 RA 담당자를 위한 규제 검토 코파일럿. 제품과 수출 대상 국가를 고정한 뒤 자연어 질문으로
수출 가능성 검토 → 규제 근거 확인 → 보고서 초안 작성까지 한 대화 안에서 처리한다.

MVP 핵심 UX Flow:

```
로그인 → 제품 선택/등록 → 국가 선택 → 채팅 질문 → 수출 가능성 검토
→ 규제 근거 확인 → 보고서 초안 생성 → 채팅으로 수정 → PDF 내보내기
```

평가는 미니프로젝트 가이드 루브릭을 따른다. **"완성된 앱을 보여주는 자리가 아니라, 우리 팀의 설계가
얼마나 논리적이고 확장성 있는가를 설득하는 Tech Talk"** 이므로, 기능 개수보다 **문서 산출물 + 하나의
완결된 E2E 흐름**이 점수를 만든다.

## 확정 사항

| 항목 | 결정 | 비고 |
|---|---|---|
| 목표 범위 | 3일 루브릭 최적화 | 문서 산출물 우선, 코드는 E2E 1개 흐름 완결 |
| 아키텍처 | **MSA + Kafka** | 서비스 4개 + Gateway |
| Service Discovery | **Eureka 사용 안 함** | 0부에 근거 |
| 인증 | user-service 자체 JWT 발급, Gateway 검증 | 별도 Auth Server 없음 |
| AI 단계 | 3단계 RAG | pgvector + Spring AI VectorStore |
| DB | PostgreSQL 1인스턴스 + **스키마 4개** | 서비스별 논리 분리 |
| ID 체계 | **문자열 VARCHAR PK** | `"D001"` `"CV01"` `"VN"` — 명세 그대로 |
| JSON | **snake_case · 봉투 없음** | 명세 공통 규약 |
| 비동기 | 판정·보고서 생성 모두 `202 + 폴링` | Kafka 토픽 5개 |
| 배포 | SKALA EKS 실배포 | Harbor → ArgoCD → `skala-gj4` |
| LLM | OpenAI `gpt-4o-mini` + `text-embedding-3-small` (**1536차원**) | 부록 3 참조 |

### 작업 범위 경계

**`frontend/` 는 Frontend Developer 전용 영역이다.** 백엔드 작업자는 파일을 수정하지 않는다.
이 계획서의 FE 관련 서술(3부 ①, 6부)은 FE 담당자에게 넘기는 명세이지 백엔드 작업 항목이 아니다.

---

# 0부. 왜 Eureka를 쓰지 않는가

교재 `msa-lecture`는 컨테이너 10개다: MariaDB, Kafka, **Eureka**, **Auth Server**, API Gateway, 서비스 5개.
우리는 **7개**로 간다. 무엇을 왜 뺐는지가 곧 설계 근거이고, 발표 3번 섹션의 핵심 소재다.

| 컴포넌트 | 채택 | 근거 |
|---|---|---|
| Kafka | ✅ | 비동기 판정·보고서 생성 + 재판정 트리거에 **실제로 필요**하다 (3부 ④) |
| API Gateway | ✅ | 단일 진입점 + JWT 검증 1곳. K8s Ingress가 대체 못 하는 일 |
| **Eureka** | ❌ | 아래 3가지 |
| **Auth Server (별도)** | ❌ | user-service가 JWT를 발급하면 컨테이너 1개가 준다 |
| Config Server | ❌ | 환경변수 + K8s ConfigMap으로 충분 |

## Eureka를 빼는 3가지 근거

**① K8s에 배포하므로 기능이 중복된다.**
Eureka가 하는 일은 "서비스 이름 → 실제 주소 찾기 + 로드밸런싱"이다.
쿠버네티스 **Service** 리소스가 정확히 같은 일을 한다. 둘을 같이 쓰면 같은 역할의 레이어가 두 겹이 된다.

**② Docker Compose에서도 컨테이너 이름이 곧 DNS다.**
`http://ai-service:8084` 가 별도 설정 없이 그냥 동작한다.

**③ 교재 실습 코드조차 Eureka를 일관되게 쓰지 않는다.**
같은 `enrollment-service` 안에서 두 방식이 섞여 있다.
- `PaymentServiceClient.java:28` → `http://payment-service:8084/...` — 포트 포함 = 컨테이너 DNS 직접 호출
- `CourseServiceClient.java:25` → `http://course-service/api/...` — 포트 없음 = Eureka `lb://` 해석

게다가 `application.yml`에 정의된 `service.course-service.url` 프로퍼티는 **읽는 코드가 존재하지 않는다**
(`@Value`는 Kafka 토픽명에만 쓰임). 교재도 정리가 덜 된 상태이고, Eureka 없는 쪽도 문제없이 돈다.

## 빼서 얻는 것 — 환경 분기가 사라진다

```
Docker Compose : http://ai-service:8084   ← 컨테이너 이름
Kubernetes     : http://ai-service:8084   ← Service 이름
                 ^^^^^^^^^^^^^^^^^^^^^^ 똑같다
```

**서비스 이름을 컨테이너명·K8s Service명에 동일하게 맞춘다.** 그러면 코드도 설정도 하나로 끝난다.
이 규칙은 5부 배포 매니페스트까지 그대로 이어진다.

## Gateway를 남기는 이유 (Eureka와 다른 점)

Gateway가 하는 일은 K8s가 대신해주지 않는다.

1. **JWT 검증을 한 곳에서** — 없으면 4개 서비스에 검증 코드를 복붙해야 한다
2. **FE가 볼 주소가 하나** — 로컬 compose엔 Ingress가 없다. Gateway가 없으면 FE가 8081~8084를 다 알아야 한다
3. **CORS 설정도 한 곳**

결과적으로 FE 코드는 로컬이든 EKS든 `/api/**` 하나만 부른다.

---

# 1부. 산출물

루브릭 배점: **정량 60% (교수) + Peer Review 40%**.
정량 60점 = `서비스 기획 & Architecture 30점` + `시스템 설계 & Scaffolding 30점`.

| # | 산출물 | 형태 | 위치 | 루브릭 대응 |
|---|---|---|---|---|
| D1 | **Use-Case 정의서** | Markdown + 다이어그램 | `docs/01-use-case.md` | 기획 30점 — Use-Case 정의 |
| D2 | **UI 와이어프레임** | Figma + PNG | Figma `03 Design`, `docs/assets/` | 기획 30점 — 와이어프레임 완성도 |
| D3 | **AI-Ready 설계서** | Markdown + `.st` 파일 | `docs/02-ai-ready.md`, `ai-service/.../prompts/` | 기획 30점 — AI 확장 지점, 프롬프트/JSON 타당성 |
| D4 | **시스템 아키텍처 다이어그램** | 이미지 | `docs/03-architecture.md` | 설계 30점 — FE-BE-DB 구조 명확성 |
| D5 | **ERD** | DBML + 이미지 | `docs/04-erd.dbml` | 설계 30점 — 1:N / N:M, 정규화 |
| D6 | **API 명세 + Kafka 이벤트 명세** | MD(확정) + OpenAPI + Postman | **`docs/api-spec/*.md`** · `docs/05-api-spec.yaml` · `docs/06-event-spec.md` | 설계 30점 — RESTful 규격 준수 |
| C | **코드** (FE + 4서비스 + Gateway + K8s) | Git 레포 | 이 레포 | 설계 30점 — 구조·DB 연동·데이터 바인딩 시연 |
| P | **발표자료** | PPT/Gamma | 별도 | Peer Review 40% |

> **D3·D5·D6는 코드가 그대로 산출물이 되게 만든다.**
> - 프롬프트 → `prompts/*.st` 파일 자체
> - ERD → `init-db/*.sql`이 원본, DBML은 그림으로 옮긴 것
> - API 명세 → `docs/api-spec/*.md`가 확정본. Springdoc이 코드에서 뽑은 `/api-docs`와 일치해야 한다
> - **이벤트 명세 → `common/event/*.java` record 파일 자체** ← MSA라서 생긴 산출물

## 문서 파일과 코드 파일의 구분

비전공자 팀원이 가장 헷갈리는 지점이라 명시한다.

### (A) 실행되는 코드
`frontend/src/**/*.vue`·`*.ts` (브라우저) · `backend/**/src/main/java/**/*.java` (서버) · `init-db/*.sql` (테이블 생성)

### (B) 실행되지 않지만 코드를 돌리기 위해 반드시 필요한 파일

| 파일 | 하는 일 | 없으면 |
|---|---|---|
| `backend/settings.gradle` | 어떤 모듈이 이 프로젝트에 속하는지 선언 | 서비스가 빌드 대상에서 빠짐 |
| `backend/build.gradle` (루트 + 모듈별) | 쓸 라이브러리 목록 | 빌드 자체가 안 됨 |
| `application.yml` | 포트·DB주소·Kafka주소·AI모델 등 **값**을 코드 밖에 둔 곳 | 코드에 비밀번호를 박게 됨 |
| `frontend/package.json` | FE 라이브러리 목록 + 실행 명령 | `npm install` 불가 |
| `frontend/vite.config.ts` | 개발 서버 + `/api` 프록시 (FE→Gateway) | CORS 에러 |
| `docker-compose.yml` | 컨테이너 8개를 한 번에 띄우는 설명서 + 기동 순서 | 각자 수동 실행, 순서 꼬임 |
| `Dockerfile` (모듈마다 1개) | 코드를 "어디서든 도는 상자"로 포장 | 배포 불가 |
| `k8s/*.yaml` | 쿠버네티스 배포 설명서 | EKS 배포 불가 |
| `.env` / K8s Secret | API 키·비밀번호. **Git에 절대 안 올림** | 키 유출 |

### (C) 문서 파일
`docs/**/*.md` · `docs/*.yaml` · `docs/*.dbml` · `README.md`

---

# 2부. 시스템 구조

## 2-1. 서비스와 포트

| 컨테이너 | 포트 | 역할 | 스키마 |
|---|---|---|---|
| `rai-postgres` | 5434→5432 | PostgreSQL + pgvector | — |
| `rai-kafka` | 9092 | KRaft 모드 단일 브로커 (`cp-kafka:7.7.0`) | — |
| `rai-gateway` | **8080** | ★ FE가 바라보는 유일한 주소. JWT 검증·라우팅·CORS | — |
| `rai-user` | 8081 | 인증·회사·사용자 | `rai_user` |
| `rai-drug` | 8082 | 제품·버전·성분·국가 | `rai_drug` |
| `rai-chat` | 8083 | 대화·판정·보고서·알림 (**오케스트레이터**) | `rai_chat` |
| `rai-ai` | 8084 | Spring AI·RAG·규제 KB | `rai_ai` |
| `rai-frontend` | 5173→80 | Nginx 정적 서빙 (FE 담당자가 추가) | — |

> **컨테이너 이름·K8s Service 이름·`application.yml`의 서비스 URL을 모두 동일하게 맞춘다.**
> 0부에서 Eureka를 뺀 대가를 여기서 회수한다.

**포트 충돌 주의**: 이 머신은 5432(로컬 PG), 5433/8080/8081(sjw-* 컨테이너)이 점유 중이다.
겹치면 **호스트 포트만** 18080/18081 등으로 바꾼다. 컨테이너 내부 포트와 K8s 포트는 8080/8081을 유지해야
환경 간 URL이 같아진다. **Day 1에 반드시 확인한다.**

## 2-2. 디렉터리 구조

Gradle **멀티모듈**로 간다. 서비스 4개가 Kafka 이벤트 DTO와 `AssessmentResult`를 공유해야 하는데,
복붙하면 반드시 어긋나기 때문이다. (교재는 이벤트 DTO를 서비스마다 따로 정의해서
`EnrollmentKafkaConsumer`가 타입을 못 받고 `Map<String, Object>`로 우회한다 — 그 문제를 피한다.)

```
RAI/
├── README.md
├── docker-compose.yml                        컨테이너 8개
├── .env.example                              POSTGRES_*, JWT_SECRET, OPENAI_API_KEY
│
├── docs/                                     [산출물]
│   ├── 00-project-plan.md                    이 문서
│   ├── api-spec/                             ★ API 명세 v0.4 확정본 — 화면별 17개 (원본)
│   ├── 01-use-case.md
│   ├── 02-ai-ready.md                        프롬프트 + 입출력 JSON 스키마
│   ├── 03-architecture.md                    MSA 구조도
│   ├── 04-erd.dbml                           dbdiagram.io 입력용
│   ├── 05-api-spec.yaml                      api-spec/ 의 OpenAPI 3.0 변환본
│   ├── 06-event-spec.md                      ★ Kafka 토픽·페이로드 명세
│   ├── RAI.postman_collection.json
│   └── assets/                               와이어프레임·다이어그램 이미지
│
├── ai/                                       프롬프트 원본 · Golden Test Set 보관
│
├── init-db/                                  ★ 스키마의 소유자
│   ├── 01_schema_user.sql
│   ├── 02_schema_drug.sql
│   ├── 03_schema_chat.sql
│   ├── 04_schema_ai.sql                      regulation_documents + vector_store
│   └── 05_seed.sql                           국가 2건, 데모 계정·제품
│
├── backend/                                  Gradle 멀티모듈 루트
│   ├── gradlew, gradlew.bat, gradle/
│   ├── settings.gradle                       include 6개 모듈
│   ├── build.gradle                          subprojects 공통 설정 (Java 21, BOM)
│   │
│   ├── common/                               [공유 라이브러리 · 실행 앱 아님]
│   │   ├── build.gradle
│   │   └── src/main/java/com/rai/common/
│   │       ├── event/                        ★ Kafka 이벤트 record (서비스 간 계약서)
│   │       ├── dto/                          AssessmentResult, ErrorResponse, 공유 DTO
│   │       ├── exception/                    GlobalExceptionHandler, 도메인 예외
│   │       ├── security/                     CurrentUser, JwtProvider
│   │       └── config/                       Jackson(snake_case) 등 공통 웹 설정
│   │
│   ├── gateway/                              [Spring Cloud Gateway :8080]
│   │   ├── build.gradle, Dockerfile
│   │   └── src/main/
│   │       ├── java/com/rai/gateway/
│   │       │   ├── GatewayApplication.java
│   │       │   └── filter/                   ★ JwtAuthFilter — 검증 후 X-User-Id 헤더 변환
│   │       └── resources/application{,-local,-docker,-k8s}.yml
│   │
│   └── services/
│       ├── user-service/                     [:8081]
│       │   ├── build.gradle, Dockerfile
│       │   └── src/main/java/com/rai/user/
│       │       ├── UserServiceApplication.java
│       │       └── controller/ service/ entity/ repository/ dto/
│       │
│       ├── drug-service/                     [:8082]
│       │   └── (동일 구조)
│       │       └── kafka/                    drug.version.created 발행
│       │
│       ├── chat-service/                     [:8083 오케스트레이터]
│       │   └── (동일 구조)
│       │       ├── kafka/                    ★ assessment/report requested 발행 · completed 수신
│       │       └── client/                   drug-service·ai-service 동기 REST 호출
│       │
│       └── ai-service/                       [:8084]
│           └── src/
│               ├── main/java/com/rai/ai/
│               │   ├── AiServiceApplication.java
│               │   ├── config/               ChatClient·VectorStore·Kafka 빈
│               │   ├── kafka/                ★ requested 수신 / completed 발행
│               │   ├── client/               ★ AiClient — Mock ↔ SpringAI 교체 지점
│               │   ├── retriever/            ★ RegulationRetriever — 검색 교체 지점
│               │   ├── regulation/           규제 KB 도메인 (문서 등록·목록)
│               │   └── parser/               PDFBox 텍스트 추출 · 청크 분할
│               ├── main/resources/
│               │   ├── application{,-local,-docker,-k8s}.yml
│               │   └── prompts/              ★ [D3] 프롬프트 .st 파일
│               └── test/
│
├── frontend/                                 Vue 3 + Vite + TS (FE 담당 영역)
│
└── k8s/                                      [EKS 배포]
    ├── 00-configmap-initdb.yaml
    ├── 01-postgres.yaml                      Deployment + PVC(ebs-sc) + Service
    ├── 02-kafka.yaml                         단일 브로커 KRaft + Service
    ├── 03-user-service.yaml
    ├── 04-drug-service.yaml
    ├── 05-chat-service.yaml
    ├── 06-ai-service.yaml
    ├── 07-gateway.yaml
    ├── 08-frontend.yaml                      FE 담당자가 채움
    ├── 09-ingress.yaml                       skala-gj4-rai.skala-gj.com
    ├── secret.example.yaml                   ★ 키 이름만. 실제 Secret은 Git 금지
    └── README.md                             배포 절차·트러블슈팅
```

## 2-3. 매일 개발할 때 — 내 서비스만 IDE, 나머지는 컨테이너

```bash
# 1) 인프라 + 내가 안 건드리는 서비스는 컨테이너로
docker compose up -d postgres kafka gateway user-service drug-service ai-service

# 2) 내 담당(예: chat-service)만 IDE / 터미널에서 직접 실행
cd backend && ./gradlew :services:chat-service:bootRun
```

**5개를 다 IDE에서 띄울 필요 없다.** 컨테이너로 뜬 서비스와 로컬 서비스가 같은 이름으로 서로를 찾는다.
단, 로컬 실행 서비스는 `localhost:5434`(DB), `localhost:9092`(Kafka)를 봐야 하므로 `local` 프로필에 그렇게 적어둔다.

| 상황 | 명령 (모두 `backend/` 에서) |
|---|---|
| 전체 빌드 | `./gradlew build` |
| 내 서비스만 빌드 | `./gradlew :services:chat-service:build` |
| 내 서비스만 실행 | `./gradlew :services:chat-service:bootRun` |
| 전체 컨테이너 기동 | `docker compose up -d --build` (루트에서) |
| 특정 서비스 로그 | `docker compose logs -f rai-chat` |
| Kafka 토픽 확인 | `docker exec -it rai-kafka kafka-topics --bootstrap-server localhost:9092 --list` |

## 2-4. Gradle 설정

```gradle
// backend/settings.gradle
rootProject.name = 'rai'
include 'common', 'gateway'
include 'services:user-service', 'services:drug-service',
        'services:chat-service', 'services:ai-service'
```

```gradle
// backend/build.gradle — 공통 설정 한 곳
subprojects {
    apply plugin: 'java'
    apply plugin: 'io.spring.dependency-management'
    group = 'com.rai'
    java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
    repositories { mavenCentral() }
    dependencies {
        compileOnly 'org.projectlombok:lombok'
        annotationProcessor 'org.projectlombok:lombok'
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
    }
}
```

모듈별 추가 의존성:

```gradle
// 공통 (user / drug / chat / ai)
implementation project(':common')
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0'
runtimeOnly   'org.postgresql:postgresql'

// drug / chat / ai — Kafka
implementation 'org.springframework.kafka:spring-kafka'

// user — JWT 발급 + BCrypt
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly   'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly   'io.jsonwebtoken:jjwt-jackson:0.12.6'
implementation 'org.springframework.boot:spring-boot-starter-security'

// ai — Spring AI (BOM으로 버전 통일)
implementation platform("org.springframework.ai:spring-ai-bom:1.0.x")
implementation 'org.springframework.ai:spring-ai-starter-model-openai'
implementation 'org.springframework.ai:spring-ai-starter-vector-store-pgvector'
implementation 'org.apache.pdfbox:pdfbox:3.0.3'

// gateway
implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
```

**새로 배워야 하는 것은 실질적으로 2개뿐이다.** Kafka Producer/Consumer(교재 `enrollment-service/kafka/`
3개 파일 패턴)와 Gradle 멀티모듈(`include` + 모듈 앞에 `:`). 나머지 Controller/Service/Repository/
Entity/JPA/Docker는 기존 Spring Boot 구조 그대로다.

---

# 3부. 연결 지점 — 무엇이 무엇에 어떻게 붙는가

연결은 6종류다. 미니프로젝트 가이드 R&R 역할과 함께 표시한다.

```
                        ┌──────────────────┐
  브라우저 ──── ① ────> │   API Gateway     │  :8080  (JWT 검증, 라우팅)
  (Vue.js)      REST    └────────┬─────────┘
                                 │ ②  REST (경로별 라우팅)
              ┌──────────────────┼──────────────────┬───────────────┐
              ▼                  ▼                  ▼               ▼
        user-service       drug-service        chat-service     ai-service
          :8081              :8082               :8083            :8084
              │                  │        ③ REST    │  ↕              │
              │                  │  <───────────────┤────────────────>│
              │                  │                  │                 │
              │                  └──── ④ Kafka ────>│<──── ④ Kafka ──>│
              │                    drug.version.    │  assessment.*   │
              │                    created          │  report.*       │
              └──────────────────┬──────────────────┴─────────────────┘
                                 │ ⑤  JPA / SQL              │ ⑥
                                 ▼                            ▼
                        ┌────────────────┐          ┌──────────────┐
                        │  PostgreSQL    │          │   LLM API    │
                        │  + pgvector    │          │  (OpenAI)    │
                        │  스키마 4개     │          └──────────────┘
                        └────────────────┘
```

## ① FE ↔ Gateway

**담당 R&R**: `API Architect`(규격) + `Frontend Developer`(호출) + `DevOps`(Gateway 라우팅)

Vue는 브라우저 안에서, 서버들은 각자 따로 돈다. 둘을 잇는 유일한 통로가 **HTTP 요청 + JSON**이다.
**MSA에서 중요한 점: FE는 서비스가 4개인 걸 모른다.** 오직 Gateway 하나만 안다.
서비스를 나중에 6개로 쪼개도 FE 코드는 한 줄도 안 바뀐다.

### 실제 흐름 (제품 목록 불러오기)

```
1. 사용자가 대시보드를 연다
2. [FE] DashboardView.vue → drugApi.list()
3. [FE] services/http.ts (axios) 가 GET /api/drugs 전송
        헤더에 Authorization: Bearer <JWT> 자동 부착
4. [FE] vite proxy 가 http://localhost:8080/api/drugs 로 전달   ← Gateway
5. [GW] JwtAuthFilter 가 토큰 검증 → userId, companyId 추출
        → X-User-Id, X-Company-Id 헤더를 붙여서
6. [GW] 경로 /api/drugs/** 규칙에 따라 http://drug-service:8082 로 전달
7. [drug] DrugController 가 받음. @CurrentUser 로 companyId 획득
8. [drug] Service → Repository → DB 조회 (⑤ 연결)
9. [drug] JSON 응답 → Gateway → axios → Pinia store → 화면 갱신
```

### 이 연결을 만드는 파일 5개

| 파일 | 역할 | 비유 |
|---|---|---|
| `frontend/vite.config.ts` 의 `proxy` | `/api` 요청을 8080(Gateway)로 자동 전달 | 우편물 자동 전달 설정 |
| `frontend/src/services/http.ts` | axios 공통 설정. 모든 요청에 JWT 부착, 401이면 refresh 후 재시도 | 회사 대표전화 교환대 |
| `frontend/src/types/api.ts` | 서버가 주는 JSON의 **모양**을 TypeScript로 | FE·BE가 서명한 계약서 |
| `services/*/dto/*.java` | 서버가 내보낼 JSON의 **모양**을 Java로 | 계약서의 서버 쪽 사본 |
| `gateway/.../application.yml` | 경로 → 서비스 매핑 규칙 | 안내데스크의 층별 안내판 |

> `types/api.ts` 와 `dto/*.java`는 **같은 모양이어야 한다.** 어긋나면 화면에 `undefined`가 뜬다.
> 둘 다 `docs/api-spec/` 확정본에서 파생시킨다. 4부 매핑표가 그 단일 참조점이다.

### Gateway 라우팅 규칙 (`gateway/src/main/resources/application.yml`)

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: ${USER_SERVICE_URL:http://user-service:8081}
          predicates: [ Path=/api/auth/** ]

        - id: chat-reassessment              # ★ 반드시 drug-service 라우트보다 먼저
          uri: ${CHAT_SERVICE_URL:http://chat-service:8083}
          predicates: [ Path=/api/drugs/*/reassessment-needed ]

        - id: drug-service
          uri: ${DRUG_SERVICE_URL:http://drug-service:8082}
          predicates: [ Path=/api/drugs/**,/api/countries/** ]

        - id: chat-service
          uri: ${CHAT_SERVICE_URL:http://chat-service:8083}
          predicates: [ Path=/api/conversations/**,/api/assessments/**,/api/reports/**,/api/notifications/** ]

        - id: ai-service
          uri: ${AI_SERVICE_URL:http://ai-service:8084}
          predicates: [ Path=/api/regulations/** ]
```

**작성 규칙 3가지 — 놓치면 바로 404가 난다.**

1. **`Path=` 는 라우트당 하나로 쓰고 패턴은 콤마로 잇는다.**
   Spring Cloud Gateway는 predicate 리스트를 **AND로 묶는다.** `[ Path=/api/drugs/**, Path=/api/countries/** ]`
   처럼 쓰면 한 요청이 두 패턴을 동시에 만족해야 하므로 그 라우트는 영원히 매칭되지 않는다.
2. **`chat-reassessment` 라우트를 `drug-service` 보다 먼저 선언한다.**
   `GET /api/drugs/{id}/reassessment-needed`(명세 2번)는 경로상 제품이지만 판정 이력을 읽으므로
   데이터가 `rai_chat`에 있다. 이 경로만 chat-service로 보내면 **서비스 간 동기 호출이 0회**가 되고,
   "drug-service는 chat-service의 존재를 모른다"는 설계 논지도 그대로 유지된다.
3. **`/internal/**` 은 라우팅 규칙에 넣지 않는다.** 넣지 않는 것이 곧 외부 차단이다.

**기본값이 컨테이너/Service 이름이므로 Compose와 K8s에서 환경변수를 안 넣어도 그대로 동작한다.** (0부의 이득)

### CORS

- **개발 중**: `vite.config.ts` 프록시로 우회 (브라우저 입장에선 같은 5173) → 권장
- **Gateway**: `globalcors` 설정 한 곳만. 서비스 4개에 각각 안 넣어도 됨
- **배포 후**: Nginx가 FE와 `/api`를 같은 도메인으로 서빙 → CORS 자체가 발생 안 함

```ts
// frontend/vite.config.ts
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true }   // ← Gateway
  }
}
```

## ② Gateway ↔ 서비스, ③ 서비스 ↔ 서비스

**담당 R&R**: `Backend Developer` + `DevOps & Integration`

### 주소는 그냥 "이름:포트"

```java
// chat-service/client/DrugServiceClient.java
@Component
@RequiredArgsConstructor
public class DrugServiceClient {
    private final RestClient restClient;   // Spring Boot 3.4 기본 제공

    @Value("${service.drug-service.url:http://drug-service:8082}")
    private String drugServiceUrl;

    // ★ ID는 모두 String ("D001", "C001") — 4부 ID 규약
    public DrugSnapshot getDrugSnapshot(String drugId, String companyId) {
        return restClient.get()
            .uri(drugServiceUrl + "/internal/drugs/{id}", drugId)
            .header("X-Company-Id", companyId)
            .retrieve()
            .body(DrugSnapshot.class);
    }
}
```

`drug-service`라는 이름이 **Compose에서는 컨테이너 이름, K8s에서는 Service 이름**으로 똑같이 해석된다.

### `/internal/**` 규칙

서비스끼리만 부르는 엔드포인트는 `/internal/` 로 시작하게 한다 (교재 `/api/courses/internal/exists/{id}` 패턴).

- Gateway 라우팅 규칙에 `/internal/**` 을 **넣지 않는다** → 외부에서 접근 불가
- 어떤 API가 사용자용이고 어떤 게 내부용인지 경로만 봐도 구분된다

MVP의 `/internal` 엔드포인트는 3개다 (4부 매핑표 참조).

### 동기 REST vs 비동기 Kafka — 언제 무엇을 쓰나

| 상황 | 방식 | 이유 |
|---|---|---|
| 세션 만들 때 제품 존재 확인 | **동기 REST** | 없으면 즉시 404를 줘야 함. 기다려도 50ms |
| 판정 요청 (15~30초) | **비동기 Kafka** | 기다리면 스레드 점유 + 타임아웃 |
| 보고서 초안 생성 (15~30초) | **비동기 Kafka** | 명세가 `202 + job_id` 폴링으로 확정 |
| 성분 변경 → 재판정 트리거 | **비동기 Kafka** | drug-service가 chat-service를 몰라도 됨 |
| 보고서 수정 (`PATCH`) | **동기 REST** | 명세가 `200` 즉시 응답으로 확정 |

**둘 다 쓰는 게 맞다.** 발표에서 "동기와 비동기를 상황에 맞게 나눴다"고 설명할 수 있다.

## ④ Kafka — MSA의 핵심

**담당 R&R**: `API Architect`(이벤트 스키마) + `Backend Developer`(구현)

### 왜 Kafka인가 — 억지로 끼워넣는 게 아니다

기획서에 이미 요구사항으로 있다:
- 비기능 요구사항: *"AI 요청은 `pending → completed / failed` 상태로 관리되어야 하며"*, *"추후 Queue 확장 가능 구조"*
- 가이드 AI-Ready 4원칙: *"Asynchronous Pipeline — 비동기 처리(Async/Await, **Queue**) 및 상태 관리(Pending/Completed)"*

명세도 판정과 보고서 생성을 **`202 Accepted` + 2초 폴링 + 30초 타임아웃**으로 확정했다. Kafka가 그 Queue다.

**REST로 하면 안 되나?** chat-service가 ai-service를 REST로 부르면 **15~30초 동안 스레드가 묶인다.**
동시 사용자 10명이면 스레드 10개가 죽는다. ai-service가 재시작 중이면 요청이 그냥 실패한다.
Kafka면 이벤트가 브로커에 남아 있다가 ai-service가 살아나면 처리된다.

### 토픽 5개 (`docs/06-event-spec.md` = 산출물 D6)

| 토픽 | 발행 | 구독 | 언제 |
|---|---|---|---|
| `assessment.requested` | chat-service | ai-service | 사용자가 채팅으로 질문을 보냄 |
| `assessment.completed` | ai-service | chat-service | 판정이 끝남 (성공/실패 모두) |
| `report.requested` | chat-service | ai-service | `POST /api/reports` 호출 |
| `report.completed` | ai-service | chat-service | 보고서 초안 생성 완료 |
| `drug.version.created` | drug-service | chat-service | 성분·버전이 바뀜 → 재판정 트리거 |

`assessment.*`와 `report.*`는 **같은 패턴의 파이프라인 2개**다. Producer/Consumer 코드를 그대로 복사하면 된다.
`drug.version.created`는 방향이 반대여서 **"이벤트로 서비스를 느슨하게 연결한다"는 걸 두 방향으로 보여준다.**

### 판정 요청 전체 흐름 (명세 3번)

```
[FE] POST /api/conversations/{id}/messages  { "message": "이 제품 베트남 수출 가능한가?" }
   ↓
[chat] ① request_id 생성 (서버가 만든다. 요청 body에 없다)
       ② messages 테이블에 status='pending' 으로 저장
       ③ drug-service 에 동기 REST → 제품 성분 스냅샷 조회
       ④ Kafka 에 assessment.requested 발행
       ⑤ 즉시 응답:  HTTP 202  { "request_id": "req_001", "status": "pending" }
   ↑ 사용자에겐 "처리 중…" 스켈레톤 버블이 보임
   ↓
[ai]   ⑥ assessment.requested 수신
       ⑦ RegulationRetriever 로 규제 근거 검색 (⑥ 연결)
       ⑧ Spring AI 로 LLM 호출 → AssessmentResult 로 구조화 수신
       ⑨ Kafka 에 assessment.completed 발행
   ↓
[chat] ⑩ assessment.completed 수신
       ⑪ assessments + assessment_citations 저장, status='completed' 로 UPDATE
   ↓
[FE]   2초마다 GET /api/assessments/req_001 폴링
       → completed 되면 판정 카드로 교체 (명세 3)
       → failed 이거나 30초 초과면 재시도 화면 (명세 3E)
```

> **응답 코드는 항상 202로 고정한다.** 명세 헤더가 `200 · 202`를 모두 허용하지만, MVP에서 200 즉답 경로를
> 두면 FE가 분기를 둘 구현해야 하고 Mock ↔ 실제 AI 전환 시 응답 코드가 바뀐다. 폴링 한 경로로 통일한다.

### 보고서 생성 흐름 (명세 4·5번)

```
[FE]  POST /api/reports  { "conversation_id": "CV01", "request_id": "req_001" }
   ↓
[chat] job_id 생성 → reports 행을 status='pending' 으로 INSERT
       → Kafka 에 report.requested 발행 → 202 { "status":"pending", "job_id":"job_01" }
   ↓
[ai]  판정 결과 + 근거로 초안 생성 → report.completed 발행
   ↓
[chat] 수신 → reports UPDATE (draft_content, version=1, status='completed')
   ↓
[FE]  GET /api/reports/jobs/job_01 폴링 → completed 되면 프리뷰·citations 렌더
```

### 성분 변경 → 재판정 흐름

```
[FE]  PATCH /api/drugs/{id}   (성분 변경 · 명세 P)
   ↓
[drug] 새 drug_versions 행 저장 → Kafka 에 drug.version.created 발행 → 200 즉시 응답
   ↓
[chat] 이벤트 수신 → 이 제품의 기존 판정 이력 조회
       → 영향받는 국가 목록 산출 → notifications 행 생성 (REASSESS_NEEDED)
   ↓
[FE]  GET /api/drugs/{id}/reassessment-needed 로 재검토 배지 표시 (명세 2)
      [재검토] 클릭 시 기존 국가로 새 세션
```

> **여기가 Kafka의 가치가 가장 잘 드러나는 지점이다.** drug-service는 chat-service의 존재를 모른다.
> 나중에 알림 서비스·보고서 서비스가 같은 이벤트를 필요로 해도 drug-service는 안 고친다.

### 구현 파일 (교재 `enrollment-service/kafka/` 패턴 그대로)

| 파일 | 역할 |
|---|---|
| `common/event/Topics.java` | 토픽명 문자열 상수. 오타 방지 |
| `common/event/*Event.java` | 이벤트 페이로드 **record**. ★ 서비스 간 계약서 |
| `*/kafka/*Producer.java` | `KafkaTemplate.send(topic, key, event)` |
| `*/kafka/*Consumer.java` | `@KafkaListener(topics = …, groupId = …)` |
| `*/config/KafkaConfig.java` | `TopicBuilder` 로 토픽 자동 생성 (partitions 3, replicas 1) |

**교재보다 개선하는 점**: `common` 모듈에 record를 두고 발행·구독 양쪽이 **같은 클래스**를 쓰므로
타입을 그대로 받는다 (교재는 `Map<String, Object>`로 우회한다).

```yaml
# 컨슈머 설정 — common 모듈 패키지를 신뢰
spring.kafka.consumer.properties:
  spring.json.trusted.packages: "com.rai.common.event"
```

### 멱등성 — 반드시 챙길 것

Kafka는 **같은 메시지를 두 번 줄 수 있다**(at-least-once). 판정이 두 번 저장되면 안 된다.
이벤트에 `requestId`를 실어 보내고, chat-service가 `assessments.request_id` **UNIQUE 제약**으로 중복을 막는다.
`reports.job_id`도 동일하게 UNIQUE다.

## ⑤ 서비스 ↔ DB

**담당 R&R**: `Data Architect`(ERD 설계) + `Backend Developer`(구현)

DB는 **엑셀 표**처럼 생겼고 Java는 **객체**를 다룬다. 자동 변환해주는 게 **JPA(Hibernate)** 다.
`@Entity` 붙은 Java 클래스 1개 = DB 테이블 1개.

```
[Java]  Drug drug = new Drug("아목시실린 캡슐", "capsule")
   ↓  JPA 자동 번역
[SQL]   INSERT INTO rai_drug.drugs (product_name, dosage_form) VALUES ('아목시실린 캡슐', 'capsule')
   ↓  JDBC 드라이버
[PostgreSQL]  저장
```

### MSA인데 DB는 하나 — 스키마로 나눈다

MSA 정석은 "서비스마다 DB 하나"지만, 3일 + EKS 배포에서 Postgres 인스턴스 4개는 무리다.
**인스턴스 1개 + 스키마 4개**로 간다. 교재도 단일 DB를 공유한다.

```yaml
# 각 서비스는 자기 스키마만 본다
spring.jpa.properties.hibernate.default_schema: rai_drug
```

**규칙: 다른 서비스의 스키마를 직접 조회하지 않는다.** 필요하면 REST(`/internal/**`)나 Kafka로 받는다.
이걸 지켜야 나중에 진짜로 DB를 쪼갤 수 있다. 발표에서 *"물리적으론 한 인스턴스, 논리적으론 서비스별 소유.
확장 시 스키마 단위로 분리 이전 가능"* 이라고 정직하게 설명하면 된다.

### 스키마의 주인은 `init-db/*.sql` 이다

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

**JPA는 "일치하는지 검사만" 한다.** 엔티티에 필드를 추가했는데 SQL을 안 고치면 **서버가 아예 안 뜬다.**
버그가 아니라 안전장치다. → 엔티티를 고치면 SQL도 같이 고치고, `docker compose down -v` 후 다시 올린다.

### 환경별 설정 우선순위

```
docker-compose environment / K8s env  >  application-{profile}.yml  >  application.yml
(가장 셈)                                                                (기본값)
```

### 멀티테넌시(회사별 격리) — 보안 요구사항

> *"사용자는 다른 회사의 Drug, Conversation, Report ID를 직접 입력하더라도 조회할 수 없어야 한다."*

**모든 조회에 `companyId` 조건을 넣는다. ID만으로 조회하지 않는다.**

```java
// ❌ 위험: 다른 회사 제품도 나옴
drugRepository.findById(drugId)
// ✅ 안전 (drugId·companyId 모두 String)
drugRepository.findByDrugIdAndCompanyId(drugId, currentUser.companyId())
```

`companyId`는 사용자가 보내는 값이 아니라 **Gateway가 JWT에서 꺼내 헤더로 넣어준 값**을 쓴다. 이게 핵심이다.

## ⑥ ai-service ↔ LLM — AI 확장 지점

**담당 R&R**: `API Architect`(프롬프트·JSON 규격) + `Backend Developer`(구현)

### 왜 인터페이스를 두는가 (루브릭 "AI 확장 지점" 배점의 핵심)

가이드 **Interface First**: *"Backend가 AI 모델의 API를 호출하도록 변경되더라도, Frontend는 기존 Mock API
규격(JSON)을 그대로 유지하므로 화면 수정 없이 AI 기능 적용이 가능하도록 설계"*

**인터페이스 2개**를 둔다. 발표에서 보여줄 "확장 지점"의 실물이다.

```java
// 교체 지점 1 — AI를 부르는 방식
public interface AiClient {
    AssessmentResult assessExport(AssessmentRequestedEvent request);
    ReportDraft      generateReport(ReportRequestedEvent request);
    ReportDraft      reviseReport(String reportId, String instruction);
}
   ├── MockAiClient    @Profile("mock")   고정 JSON 반환 (Day 2 데모)
   └── SpringAiClient  @Profile("!mock")  실제 LLM 호출 (Day 3)

// 교체 지점 2 — 규제 근거를 찾는 방식
public interface RegulationRetriever {
    List<Document> retrieve(String query, String countryId, int topK);
}
   └── PgVectorRetriever   pgvector 유사도 검색
```

**Kafka Consumer는 인터페이스만 안다.** 구현체를 갈아끼워도 Consumer 코드·chat-service·FE는 한 줄도 안 바뀐다.
발표에서 `application.yml` 프로필 한 줄로 Mock ↔ 실제 AI를 전환해 보이면 설득력이 크다.

### 3단계 RAG 실제 흐름

```
assessment.requested 이벤트 수신 (제품 성분·국가·질문 포함)
   ↓
[1] IntentClassifier → intent = EXPORT_ELIGIBILITY_CHECK
   ↓
[2] PgVectorRetriever: 질문+성분을 임베딩으로 바꿔
    vector_store 에서 country='VN' 필터 + 유사도 상위 5개 검색
    → 규제 원문 조각 5개 + 메타데이터(문서명·기관·버전·시행일·section·URL)
   ↓
[3] 프롬프트 조립 (prompts/export-eligibility.st)
    System: "너는 RA 코파일럿이다. 검색된 문서에 없는 내용은 절대 지어내지 마라.
             근거가 부족하면 eligibility 를 REVIEW_REQUIRED 로 답하라."
    User:   제품정보 + 국가 + 검색된 규제 원문 + 질문
   ↓
[4] ChatClient.call().entity(AssessmentResult.class)
    → LLM이 JSON으로 답하고 Spring AI가 Java 객체로 자동 변환
   ↓
[5] assessment.completed 이벤트 발행 → chat-service 가 저장
```

### 구조화 출력 — 할루시네이션 방지

문자열이 아니라 **record**로 받는다. 형식이 어긋나면 그 자리에서 실패한다.
필드명은 명세 3번의 응답 계약과 1:1이다 (Jackson이 snake_case로 직렬화한다).

```java
// common/dto/AssessmentResult.java — ai-service 와 chat-service 가 공유
public record AssessmentResult(
    String summary,
    Eligibility eligibility,
    List<IngredientAssessment> ingredientAssessments,   // → ingredient_assessments
    List<String> requirements,
    List<String> risks,
    List<String> recommendedActions,                    // → recommended_actions
    List<SourceRef> sources
) {}

public record IngredientAssessment(String ingredient, IngredientStatus status, String reason) {}

public record SourceRef(String documentId, String title, String authority,
                        String version, LocalDate effectiveDate,
                        String section, String sourceUrl) {}

public enum Eligibility        { POSSIBLE, CONDITIONAL, REVIEW_REQUIRED, RESTRICTED }
public enum IngredientStatus   { NO_RESTRICTION, CONDITIONAL, REVIEW_REQUIRED, RESTRICTED }
```

**가드레일 (명세 3R, 우선순위 상)**: 근거가 부족하면 값을 지어내지 말고 `REVIEW_REQUIRED`를 반환한다.
`sources`가 비면 문서명·조항·시행일을 절대 생성하지 않는다. 구조화 출력 파싱 자체가 실패하면
`status = 'failed'`로 두고 명세 3E 경로를 탄다. **모델이 억지로 값을 고르게 두지 않는다.**

### API 키 관리

```yaml
spring.ai.openai.api-key: ${OPENAI_API_KEY}   # 자리표시만
```

- 로컬: `.env` (`.gitignore`에 포함)
- Compose: `environment`로 주입
- EKS: `kubectl create secret` → Deployment에서 `secretKeyRef`
- **한 번이라도 커밋했으면 히스토리를 지워도 유출된 것으로 본다. 즉시 폐기·재발급.**

## R&R ↔ 연결 지점 매핑표 (발표 슬라이드용)

| R&R 역할 | 담당 연결 지점 | 산출물 | 담당 서비스 |
|---|---|---|---|
| **PM** | — (조율) | 발표 슬라이드, 일정 | — |
| **Product/UX Designer** | 사용자 ↔ 화면 | D1 Use-Case, D2 Figma | — |
| **Data Architect** | **⑤ 서비스 ↔ DB** | D5 ERD, `init-db/*.sql`, entity | 전 서비스 스키마 |
| **API Architect** | **① FE↔GW 계약**, **④ 이벤트 스키마**, **⑥ 프롬프트/JSON** | D6 API+이벤트 명세, D3 프롬프트 | `common/` 모듈 |
| **Frontend Developer** | **① 의 FE 쪽** | `services/*.ts`, `views/*.vue`, `types/api.ts` | frontend |
| **Backend Developer** | **②③ 서비스 간**, **⑤⑥** | controller/service/repository, kafka | user, drug, chat, ai |
| **DevOps & Integration** | **전 구간 + Gateway + 배포** | Gateway 라우팅, Dockerfile ×6, `k8s/*.yaml`, E2E | gateway, 인프라 |

> **5명 팀이면**: 1인이 서비스 1개씩 맡고, DevOps가 Gateway + Compose + K8s를 전담하는 배치가 자연스럽다.
> `common/` 모듈은 **Day 1에 API Architect가 먼저 확정**해야 나머지가 병렬로 움직인다.

---

# 4부. API 계약 → 구현 매핑 (백엔드 단일 참조점)

`docs/api-spec/` 확정본을 서비스·테이블·토픽에 배정한 표다.
**"내가 만들 엔드포인트가 어느 서비스이고 어느 테이블을 쓰는가"는 이 부만 보면 끝난다.**

## 4-1. 전역 JSON 규약 (`common/` 모듈에서 한 번만 설정)

| 항목 | 규약 | 구현 |
|---|---|---|
| 필드명 | **snake_case** | `spring.jackson.property-naming-strategy: SNAKE_CASE` — Java record는 camelCase로 두면 자동 변환된다 |
| 성공 응답 | **봉투 없음.** DTO / `List<DTO>` 를 그대로 반환 | `{"success":…,"data":…}` 같은 래퍼를 만들지 않는다 |
| 에러 응답 | `{"error":{"code","message"}}` | `common/dto/ErrorResponse.java` + `GlobalExceptionHandler` |
| ID | **모두 `String`** | VARCHAR PK (4-4) |
| 날짜·시각 | ISO-8601 UTC `2026-09-03T10:00:00Z` | `Instant` 사용. `LocalDateTime`은 `Z`가 안 붙으므로 금지 |
| 인증 | `Authorization: Bearer {access_token}` | Gateway 검증 후 `X-User-Id` / `X-Company-Id` 헤더로 변환 |
| 비동기 | `pending → completed / failed` · 2초 폴링 · 30초 타임아웃 | 판정·보고서 생성 공통 |

### 에러 코드표

명세 공통 규약은 `code` 3개를 명시하고 404/409 응답을 쓰므로, 5개로 확정한다.

| HTTP | `code` | 사용처 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 필수 필드 누락 (명세 2F) |
| 401 | `UNAUTHORIZED` | 로그인 실패, 토큰 없음/만료 (명세 1E) |
| 404 | `NOT_FOUND` | drug / conversation / report 없음 |
| 409 | `CONFLICT` | 이메일 중복 (명세 1 signup) |
| 500 | `INTERNAL_ERROR` | 그 외 전부. **기술 오류 원문을 사용자에게 노출하지 않는다** |

```java
// common/dto/ErrorResponse.java
public record ErrorResponse(ErrorBody error) {
    public record ErrorBody(String code, String message) {}
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorBody(code, message));
    }
}
```

## 4-2. 엔드포인트 매핑표 (MVP 공개 23개)

| # | 메서드 · 경로 | 서비스 | 주 테이블 | 이벤트 | 명세 화면 |
|---|---|---|---|---|---|
| 1 | `POST /api/auth/login` | user | users | — | 1 |
| 2 | `POST /api/auth/signup` | user | companies, users | — | 1 |
| 3 | `POST /api/auth/refresh` ★검증제외 | user | — (stateless) | — | 1 |
| 4 | `GET /api/auth/me` | user | users | — | 1 |
| 5 | `GET /api/drugs` (`?q=` 검색 겸용) | drug | drugs + 최신 version | — | 2, 2E, 2S |
| 6 | `POST /api/drugs` | drug | drugs, drug_versions, drug_ingredients | — | 2F |
| 7 | `PATCH /api/drugs/{drug_id}` *(Post-MVP)* | drug | drug_versions(+1), drug_ingredients | **pub** `drug.version.created` | P |
| 8 | `GET /api/countries` | drug | countries | — | 2 |
| 9 | `GET /api/drugs/{id}/reassessment-needed` | **chat** ★GW 라우팅 | assessments | — | 2 |
| 10 | `POST /api/conversations` | chat | conversations | — | 2 |
| 11 | `GET /api/conversations?limit=5` | chat | conversations | — | 2 |
| 12 | `PATCH /api/conversations/{id}` | chat | conversations | — | 3C |
| 13 | `POST /api/conversations/{id}/messages` → **202** | chat | messages | **pub** `assessment.requested` | 3, 3N |
| 14 | `GET /api/conversations/{id}/messages` | chat | messages | — | 3, 3N |
| 15 | `GET /api/assessments/{request_id}` | chat | assessments, assessment_citations | **sub** `assessment.completed` | 3, 3E, 3R |
| 16 | `POST /api/assessments/{request_id}/feedback` | chat | assessment_feedback | — | 3 |
| 17 | `GET /api/notifications` ⚠부록 1 | chat | notifications | **sub** `drug.version.created` | 2N |
| 18 | `POST /api/reports` → **202** | chat | reports (pending) | **pub** `report.requested` | 4, 5 |
| 19 | `GET /api/reports/jobs/{job_id}` | chat | reports | **sub** `report.completed` | 5 |
| 20 | `GET /api/reports` | chat | reports (`status='completed'`) | — | 5L |
| 21 | `PATCH /api/reports/{report_id}` → **200 동기** | chat | reports (version+1) | — (`/internal` REST) | 5 |
| 22 | `GET /api/reports/{id}/export?format=pdf` | chat | reports | — | 5 |
| 23 | `POST /api/regulations` (규제 문서 적재) | ai | regulation_documents, vector_store | — | 운영자용 |

**Post-MVP (6번 admin 검수 콘솔)**: `GET /api/regulations/feed` · `GET /api/regulations/{id}` ·
`POST /api/regulations/{id}/review` — ai-service. 확장 시 출발점으로만 명세에 남긴다.

### `/internal/**` 3개 (Gateway 라우팅에 넣지 않음 → 외부 접근 불가)

| 경로 | 제공 | 호출 | 용도 |
|---|---|---|---|
| `GET /internal/drugs/{id}` | drug | chat | #10 세션 생성 시 제품 존재 확인 + 성분 스냅샷 |
| `GET /internal/assessments/exists?drug_id=` | chat | drug | #7 응답의 `has_prior_assessments` |
| `POST /internal/reports/revise` | ai | chat | #21 보고서 동기 수정 (read timeout 60초) |

### 화면 4번(판정 근거 상세 패널)은 신규 호출이 없다

`#15` 응답의 `result.ingredient_assessments[]` → 성분별 행, `sources[]` → 근거 출처로 렌더링만 한다.
**백엔드는 이 화면을 위한 엔드포인트를 만들지 않는다.**

## 4-3. enum 확정표 (`common/` 에 한 번만 선언)

| enum | 값 | 위치 |
|---|---|---|
| `eligibility` | `POSSIBLE` `CONDITIONAL` `REVIEW_REQUIRED` `RESTRICTED` | `AssessmentResult` |
| 성분 `status` | `NO_RESTRICTION` `CONDITIONAL` `REVIEW_REQUIRED` `RESTRICTED` | `IngredientAssessment` |
| `intent` | `EXPORT_ELIGIBILITY_CHECK` `REPORT_GENERATE` `REPORT_REVISE` | 메시지·판정 |
| 비동기 `status` | `pending` `completed` `failed` | messages, assessments, reports — **소문자** |
| `role` | `user` `assistant` | messages — **소문자** |
| feedback `rating` | `helpful` `needs_revision` | #16 — **소문자** |
| notification `type` | `REGULATION_CHANGE` `REASSESS_NEEDED` `REASSESS_DONE` | #17 |
| 에러 `code` | 4-1 표 5종 | 전역 |

> ★ **대소문자가 섞여 있다.** 판정 도메인 값은 대문자, 상태·역할·rating은 소문자 — 명세 원문 그대로다.
> `@JsonValue` / `@JsonCreator`로 고정하고 임의로 통일하지 않는다.

## 4-4. ID 규약

명세의 ID는 전부 문자열이므로 **VARCHAR를 PK로 직접 쓴다.** 변환 코드가 0줄이 된다.

```sql
CREATE SEQUENCE rai_drug.drug_seq;
CREATE TABLE rai_drug.drugs (
  drug_id    VARCHAR(16) PRIMARY KEY
             DEFAULT 'D' || lpad(nextval('rai_drug.drug_seq')::text, 3, '0'),
  company_id VARCHAR(16) NOT NULL,
  ...
);
```

```java
@Id
@Column(name = "drug_id")
private String drugId;      // "D001" — 그대로
```

| 접두사 | 대상 | 예 |
|---|---|---|
| `C` | company | `C001` |
| `U` | user | `U001` |
| `D` | drug | `D001` |
| `CV` | conversation | `CV01` |
| `R` | report | `R001` |
| `N` | notification | `N001` |
| `req_` | assessment request | `req_001` |
| `job_` | report job | `job_01` |
| — | **country_id 는 ISO 코드 그대로** | `VN`, `ID` |

## 4-5. 평면 DTO ↔ 정규화 ERD 매핑 (drug-service)

명세는 제품을 **평면**으로 주고받고, ERD는 이력 유지를 위해 **정규화**돼 있다. 변환 규칙은 이렇다.

```json
// 명세 GET /api/drugs 응답
{ "drug_id":"D001", "product_name":"아목시실린 캡슐",
  "ingredients":["Amoxicillin","첨가제 B"], "strength":"500mg",
  "dosage_form":"capsule", "version":2 }
```

| DTO 필드 | 출처 |
|---|---|
| `drug_id`, `product_name` | `drugs` |
| `version` | 해당 제품의 **최신** `drug_versions.version_no` |
| `strength`, `dosage_form` | 최신 `drug_versions` (버전마다 바뀔 수 있으므로 `drugs`가 아니다) |
| `ingredients[]` | 최신 버전의 `drug_ingredients.name` 배열 |

- `POST /api/drugs` → `drugs` 1행 + `drug_versions` v1 + `drug_ingredients` N행을 **한 트랜잭션**으로
- `PATCH /api/drugs/{id}` → 기존 행 수정 금지. **새 `drug_versions`(v+1) + 새 ingredients** 저장 후
  `drug.version.created` 발행. 이력 유지가 명세 5번 "버전 타임라인"의 전제다.

## 4-6. 인증 구조

```
[user-service]  로그인 성공 → JWT 발급 (claims: userId, companyId)
      ↓
[FE]            access/refresh 저장 → 모든 요청 헤더에 Bearer 부착
      ↓
[Gateway]       JwtAuthFilter 가 서명·만료 검증
                → X-User-Id, X-Company-Id 헤더로 변환해서 전달
                → ★ /api/auth/login, /api/auth/signup, /api/auth/refresh 는 검증 제외
      ↓
[각 서비스]      @CurrentUser 로 헤더를 객체로 받음
```

> **`/api/auth/refresh` 를 검증 제외 목록에 반드시 넣는다.** access token이 만료된 상태에서 호출되는
> 엔드포인트이므로, 빠뜨리면 Gateway가 401을 던져 토큰 갱신이 영구히 불가능해지고 명세 1E "세션 만료"
> 화면에서 빠져나올 수 없다.

**핵심 보안 규칙**: 각 서비스는 `/internal/**` 을 제외한 모든 요청에서 헤더가 없으면 **401을 던진다.**
Gateway를 거치지 않은 요청은 헤더가 없기 때문이다. 각 서비스의 `SecurityConfig`에 `permitAll`을 두지 않는다.
(K8s NetworkPolicy로 더 막을 수 있지만 3일 범위 밖)

---

# 5부. 데이터 모델

루브릭이 "1:N, N:M 관계 및 정규화 타당성"을 보므로 관계를 명시적으로 만든다. **테이블 17개.**

| 스키마 | 테이블 | 관계 · 비고 |
|---|---|---|
| `rai_user` | `companies` | — |
| | `users` | companies **1:N** / `password_hash` (평문 금지) |
| `rai_drug` | `countries` | 마스터. **VN(베트남), ID(인도네시아)** 2건 시드 |
| | `drugs` | companies 1:N (`company_id` 격리) |
| | `drug_versions` | drugs **1:N** — 덮어쓰지 않고 이력 유지 (`version_no`, `strength`, `dosage_form`) |
| | `drug_ingredients` | drug_versions **1:N** |
| | `drug_target_countries` | drugs ↔ countries **N:M** ★ 조인 테이블 (부록 5) |
| `rai_chat` | `conversations` | users / drugs / countries 각 1:N |
| | `messages` | conversations **1:N** / `role`, `status`, `request_id` |
| | `assessments` | conversations 1:N / **`request_id` UNIQUE** (멱등성) |
| | `assessment_citations` | assessments **1:N** ★ 판정↔근거 연결 |
| | `assessment_feedback` | assessments 1:N / `rating`, `reason` (명세 3) |
| | `reports` | conversations 1:N / **`job_id` UNIQUE**, `status`, `draft_content`, `version` |
| | `notifications` | users 1:N / `type`, `read` (명세 2N) |
| | `analytics_events` | users 1:N (KPI Funnel) |
| `rai_ai` | `regulation_documents` | 규제 문서 메타데이터 |
| | `vector_store` | Spring AI 표준 스키마 |

**보고서에 별도 job 테이블을 두지 않는다.** `POST /api/reports`가 `reports` 행을 `status='pending'`으로
먼저 만들고, `GET /api/reports/jobs/{job_id}` 는 `findByJobId` 한 번이면 된다.
보관함(`GET /api/reports`)은 `status='completed'`만 필터한다.

> **서비스 경계를 넘는 FK는 만들지 않는다.** `rai_chat.conversations.drug_id`는 FK 제약 없이 값만 저장하고,
> 유효성은 `/internal/drugs/{id}` 호출로 확인한다. 이게 MSA의 원칙이고, 발표에서 설명할 포인트다.

## 규제 KB — `vector_store`

`vectorStore.add(documents)` 한 줄로 임베딩 생성 + 저장이 끝난다.
metadata에 citation 정보를 다 넣어두면 검색 결과에서 바로 근거 표기(명세 4번 `sources[]`)가 나온다.

```java
List<Document> docs = chunks.stream().map(text -> new Document(text, Map.of(
        "documentId",    document.getDocumentId(),
        "country",       document.getCountry(),
        "authority",     document.getAuthority(),
        "title",         document.getTitle(),
        "section",       document.getSection(),
        "version",       document.getDocumentVersion(),   // 명세 sources[].version
        "effectiveDate", String.valueOf(document.getEffectiveDate()),
        "sourceUrl",     document.getSourceUrl()
))).toList();
vectorStore.add(docs);
```

> `initialize-schema: false`로 두고 `04_schema_ai.sql`에 `vector_store` DDL을 직접 적는다.
> "스키마의 주인은 `init-db`"라는 이 레포의 규약을 깨지 않기 위해서다.
> `VECTOR(1536)` — 임베딩 모델을 바꾸면 차원이 달라져 기존 벡터를 전부 다시 만들어야 한다 (부록 3).

---

# 6부. 배포

## Step 1. 로컬 Docker Compose

**기동 순서가 중요하다.**

```
postgres · kafka (인프라, healthcheck 대기)
  → user · drug · chat · ai (서비스, depends_on: service_healthy)
    → gateway
      → frontend
```

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg17
    # healthcheck 유지, ./init-db 마운트

  kafka:                                    # KRaft (ZooKeeper 없음)
    image: confluentinc/cp-kafka:7.7.0
    container_name: rai-kafka
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
    healthcheck:
      test: ["CMD", "kafka-topics", "--bootstrap-server", "localhost:9092", "--list"]
      start_period: 60s

  ai-service:
    build: { context: ./backend, dockerfile: services/ai-service/Dockerfile }   # ★ 컨텍스트가 backend/
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/rai_db
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - SPRING_AI_OPENAI_API_KEY=${OPENAI_API_KEY}      # ← .env 에서. 이미지에 안 굽는다
    depends_on:
      postgres: { condition: service_healthy }
      kafka:    { condition: service_healthy }
```

**멀티모듈 Dockerfile** — 빌드 컨텍스트가 `backend/` 여야 `common` 모듈을 포함할 수 있다:

```dockerfile
# backend/services/ai-service/Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY common common
COPY services/ai-service services/ai-service
RUN chmod +x gradlew && ./gradlew :services:ai-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/services/ai-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**`frontend/Dockerfile`** (FE 담당):

```dockerfile
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

**`frontend/nginx.conf`** — `/api`는 Gateway로. CORS가 원천 소멸:

```nginx
server {
  listen 80;
  location /api/ {
    proxy_pass http://gateway:8080;      # compose: 컨테이너명 / k8s: Service명 (같음)
    proxy_set_header Host $host;
  }
  location / {
    root /usr/share/nginx/html;
    try_files $uri $uri/ /index.html;    # Vue Router 새로고침 대응
  }
}
```

## Step 2. 이미지 빌드 → Harbor 푸시

실습 환경 정보는 `교재/cloud/skala-gj4-안내.pdf` 참조. **계정·비밀번호는 여기 옮겨 적지 않는다.**

| 항목 | 값 |
|---|---|
| 클러스터 | `skala-gj` (AWS ap-northeast-2) |
| 네임스페이스 | `skala-gj4` |
| Harbor | `harbor.skala-gj.com` / 프로젝트 `skala-gj4` |
| ArgoCD | `argocd.skala-gj.com` |
| 도메인 | `*.skala-gj.com` |

```bash
# 1) kubeconfig (최초 1회)
aws eks update-kubeconfig --name skala-gj --region ap-northeast-2 --profile skala-gj4
kubectl config set-context --current --namespace=skala-gj4

# 2) Harbor 로그인 — ★ https:// 를 붙이지 않는다 (붙이면 push 에서 401)
docker login harbor.skala-gj.com -u skala-gj4

# 3) ★ Mac(Apple Silicon)은 반드시 amd64 — EKS 노드가 x86_64
cd backend
for s in user-service drug-service chat-service ai-service; do
  docker buildx build --platform linux/amd64 \
    -f services/$s/Dockerfile \
    -t harbor.skala-gj.com/skala-gj4/rai-$s:v1 .        # ← 컨텍스트는 backend/
  docker push harbor.skala-gj.com/skala-gj4/rai-$s:v1
done

docker buildx build --platform linux/amd64 -f gateway/Dockerfile \
  -t harbor.skala-gj.com/skala-gj4/rai-gateway:v1 .
docker push harbor.skala-gj.com/skala-gj4/rai-gateway:v1

cd ../frontend
docker buildx build --platform linux/amd64 \
  -t harbor.skala-gj.com/skala-gj4/rai-frontend:v1 .
docker push harbor.skala-gj.com/skala-gj4/rai-frontend:v1
```

> arm64로 푸시하면 파드가 `exec format error`로 죽는다. **가장 흔한 실수다.**

## Step 3. 쿠버네티스 배포

### 사전 준비 — Secret 2개 (Git 커밋 금지)

```bash
kubectl create secret docker-registry harbor-cred -n skala-gj4 \
  --docker-server=harbor.skala-gj.com \
  --docker-username=skala-gj4 --docker-password='<안내PDF 참조>'

kubectl create secret generic rai-secret -n skala-gj4 \
  --from-literal=OPENAI_API_KEY='sk-...' \
  --from-literal=POSTGRES_PASSWORD='...' \
  --from-literal=JWT_SECRET='...'
```

`k8s/secret.example.yaml`에는 **키 이름만** 적은 템플릿을 둔다.

### 매니페스트 구성

| 파일 | 요점 |
|---|---|
| `00-configmap-initdb.yaml` | `init-db/*.sql` → Postgres `/docker-entrypoint-initdb.d` 마운트 |
| `01-postgres.yaml` | Deployment + **PVC(`ebs-sc`)** + Service `postgres`. ebs-sc는 RWO — DB에 정확히 맞음 |
| `02-kafka.yaml` | 단일 브로커 KRaft + PVC + Service `kafka:9092` |
| `03~06-*-service.yaml` | Deployment + Service. **Service 이름을 컨테이너명과 동일하게** |
| `07-gateway.yaml` | Deployment + Service `gateway:8080` |
| `08-frontend.yaml` | Deployment + Service `frontend:80` |
| `09-ingress.yaml` | `host: skala-gj4-rai.skala-gj.com`, `ingressClassName: nginx` |

```yaml
# 06-ai-service.yaml (발췌)
spec:
  imagePullSecrets:
    - name: harbor-cred
  containers:
    - name: ai-service
      image: harbor.skala-gj.com/skala-gj4/rai-ai-service:v1
      env:
        - name: SPRING_PROFILES_ACTIVE
          value: k8s
        - name: SPRING_DATASOURCE_URL
          value: jdbc:postgresql://postgres:5432/rai_db      # ← Service 이름
        - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
          value: kafka:9092                                  # ← Service 이름
        - name: SPRING_AI_OPENAI_API_KEY
          valueFrom:
            secretKeyRef: { name: rai-secret, key: OPENAI_API_KEY }
      readinessProbe:
        httpGet: { path: /actuator/health, port: 8084 }
```

> **여기가 0부(Eureka 제외)의 결실이다.** `postgres`, `kafka`, `drug-service` 같은 이름이
> Compose에서는 컨테이너명, K8s에서는 Service명으로 **동일하게** 해석된다. 환경별 URL 분기가 없다.

```yaml
# 09-ingress.yaml
spec:
  ingressClassName: nginx
  rules:
    - host: skala-gj4-rai.skala-gj.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service: { name: frontend, port: { number: 80 } }
```

> **Ingress에 `/api` 규칙이 없는 이유**: frontend 파드의 Nginx가 이미 `/api`를 Gateway로 넘긴다.
> 진입점을 하나로 두면 라우팅이 단순해진다.

### 배포 실행

**(a) 수동 — 데모 직전 권장**

```bash
kubectl apply -f k8s/ -n skala-gj4
kubectl get all -n skala-gj4
kubectl logs -f deploy/rai-chat-service -n skala-gj4
```

**(b) ArgoCD GitOps — 발표 가점**

```bash
argocd login argocd.skala-gj.com --username skala-gj4
argocd app create rai \
  --project skala-gj4 \
  --repo https://github.com/teamNoJN/RAI.git \
  --path k8s \
  --dest-server https://kubernetes.default.svc \
  --dest-namespace skala-gj4 \
  --sync-policy automated
```

`--project`와 `--dest-namespace`는 **반드시 `skala-gj4`**. 다른 값은 거부된다.
"main에 push → 자동 배포"를 발표에서 보여줄 수 있다.

### 트러블슈팅

접속: `https://skala-gj4-rai.skala-gj.com`

| 증상 | 원인 |
|---|---|
| `Forbidden` | 네임스페이스가 `skala-gj4`가 아님 |
| `ImagePullBackOff` | `harbor-cred` 미생성 또는 이미지 경로 오타 |
| push `401 Unauthorized` | `docker login`에 `https://`를 붙였거나 프로젝트명이 `skala-gj4`가 아님 |
| `exec format error` | arm64로 빌드 → `--platform linux/amd64`로 재빌드 |
| 파드 계속 `Pending` | 노드가 내려가 있을 수 있음 → 강사에게 기동 요청 |
| Ingress 접속 안 됨 | host가 `*.skala-gj.com`인지, `ingressClassName: nginx`인지 |
| **Kafka 컨슈머가 조용함** | `KAFKA_ADVERTISED_LISTENERS`가 Service명(`kafka:9092`)인지 확인 |
| 라우팅이 404 | Gateway `predicates`에 `Path=`를 두 번 쓰지 않았는지 (3부 ①) |

---

# 7부. 3일 실행 순서

**문서(D1~D6)가 코드보다 먼저다.** 특히 `common/` 모듈(이벤트 스키마·공유 DTO)이 Day 1에 확정돼야
나머지가 병렬로 움직인다.

## Day 1 — 서비스 기획 & Architecture 정의

| 담당 | 작업 | 산출물 |
|---|---|---|
| PM | R&R 확정, GitHub 브랜치 전략 (`feature/<service>`) | 팀 보드 |
| UX | Use-Case (Actor: member/admin/운영자) + Figma 7화면 | **D1, D2** |
| API Architect | ★ **부록 미해결 안건 9개 클로즈** → `common/` 확정 — 이벤트 record 5개 + `AssessmentResult` + `ErrorResponse` + 프롬프트 초안 | **D3, D6** |
| DA | ERD 17개 테이블, 스키마 4개 분할 + **VARCHAR PK 시퀀스 DEFAULT** 설계 | **D5 초안** |
| DevOps | 멀티모듈 `settings.gradle`/`build.gradle` + compose 뼈대 + **포트 충돌 확인** | 빌드 통과 |

> **Day 1 종료 조건**: `./gradlew build` 가 통과하고, 빈 서비스 4개가 compose로 뜬다.
> `common/event/*.java` 가 확정된다. 여기서 지연되면 Day 2가 통째로 막힌다.

## Day 2 — 시스템 설계 및 Scaffolding

| 담당 | 작업 | 산출물 |
|---|---|---|
| DA | `init-db/*.sql` 5개 작성 + 시드(국가 2건, 데모 계정/제품) | **D5 확정** |
| API Architect | `docs/api-spec/*.md` → `05-api-spec.yaml` 변환 + Postman Mock Server | **D6 확정** |
| BE-1 | user-service (JWT 발급, refresh 포함) + gateway JwtAuthFilter | 코드 |
| BE-2 | drug-service + `drug.version.created` 발행 | 코드 |
| BE-3 | chat-service + Kafka Producer/Consumer (`assessment.*`) + **MockAiClient 경유 판정** | 코드 |
| FE | `http.ts`, `types/api.ts`, Login/Dashboard/Chat 3화면 | 코드 |
| DevOps | Dockerfile ×6, compose 전체 기동, **E2E 1회 성공** | 연동 확인 |

**Day 2 종료 조건 (반드시)**: 로그인 → 제품 선택 → 국가 선택 → 채팅 입력 → **202 Accepted →
Kafka 왕복 → `GET /api/assessments/{request_id}` 폴링으로 Mock 판정 결과 카드 렌더링**.
이게 되면 루브릭 *"Mock API를 활용한 실제 데이터 바인딩 및 화면 시연"* 배점이 확보된다.

## Day 3 — 설계 검증 및 최종 발표

| 시간 | 작업 |
|---|---|
| 오전 | `SpringAiClient` + `PgVectorRetriever` 연결 → **프로필 한 줄로 Mock → 실제 AI 전환** |
| 오전 | 규제 문서 2~3건 실제 적재 → 근거 검색 확인 |
| 오전 | amd64 빌드 ×6 → Harbor push → EKS 배포 → 도메인 접속 확인 |
| 오후 | 발표자료 6섹션 작성, 리허설 |
| 15:00 | **Project Pitch (15분) + Q&A (5분)** |

**우선순위 최하위 (시간 남으면)**: 보고서 파이프라인(`report.*`), PDF Export, 보고서 대화형 수정,
`drug.version.created` 재판정 UI, `GET /api/notifications`, analytics.

> 못 하면 **"Out-of-Scope로 의도적으로 제외했고 확장 지점은 이렇게 열어뒀다"**고 발표하는 게
> 어설프게 반쯤 구현하는 것보다 루브릭에 유리하다 (*"완성된 앱을 보여주는 자리가 아니다"*).

## 발표 슬라이드 = 가이드 목차 그대로 (15분)

| # | 섹션 | 시간 | 우리 산출물 |
|---|---|---|---|
| 1 | 서비스 기획 & Use-Case | 3분 | 페르소나 3인, D1 |
| 2 | AI-Ready 설계 포인트 | 2분 | **D3** + `AiClient`/`RegulationRetriever` 인터페이스 + `AssessmentResult` record |
| 3 | 시스템 아키텍처 & 설계 | 4분 | MSA 구조도, **★ Eureka를 뺀 근거(0부)**, **D5 ERD**(N:M), **D6 API+이벤트 명세** |
| 4 | Scaffolding & 데모 | 4분 | 멀티모듈 구조 + **라이브 데모**(Kafka 로그 함께) + EKS 배포 화면 |
| 5 | 회고 및 확장 계획 | 2분 | 서비스 추가 시 이벤트만 구독, DB per service 분리, HPA |
| 6 | Q&A | 5분 | — |

> **3번 섹션의 "Eureka를 왜 뺐는가"가 이 발표의 차별점이다.** 다른 조는 교재를 그대로 따라 했을 가능성이 높다.
> "교재 코드에서 두 방식이 섞여 있는 걸 확인했고, K8s Service와 기능이 중복돼서 뺐다"는 설명은
> *"설계가 얼마나 논리적인가"* 라는 루브릭 문구에 정확히 답한다.

---

# 8부. 검증 방법

**Day 2 종료 시점에 1~8번이 전부 통과해야 한다.**

```bash
# 1. 빌드 (멀티모듈 전체)
cd backend && ./gradlew build

# 2. 인프라 기동 + 스키마 확인
docker compose down -v && docker compose up -d postgres kafka
docker exec -it rai-postgres psql -U rai -d rai_db -c "\dn"        # 스키마 4개
docker exec -it rai-postgres psql -U rai -d rai_db -c "\dt rai_chat.*"

# 3. Kafka 토픽 자동 생성 확인 (서비스 기동 후) — 5개
docker exec -it rai-kafka kafka-topics --bootstrap-server localhost:9092 --list
# → assessment.requested, assessment.completed, report.requested,
#    report.completed, drug.version.created

# 4. 전체 기동 + 각 서비스 health
docker compose up -d
for p in 8080 8081 8082 8083 8084; do curl -s localhost:$p/actuator/health; echo; done

# 5. Gateway 경유 인증 + 회사 격리
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@rai.com","password":"demo1234"}' | jq -r .access_token)
# → 다른 회사 drug_id 를 직접 넣어 404 인지 (보안 요구사항)
# → 토큰 없이 호출 시 401 인지
# → Gateway 우회해서 localhost:8082/api/drugs 직접 호출 시 401 인지  ★ 중요
# → 토큰 없이 POST /api/auth/refresh 가 200 인지 (검증 제외 경로)  ★ 중요

# 6. JSON 계약 (명세 공통 규약)
curl -s localhost:8080/api/drugs -H "Authorization: Bearer $TOKEN" | jq '.[0] | keys'
# → snake_case 키만 나오고 success/data 래퍼가 없어야 한다
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"demo@rai.com","password":"wrong"}' | jq '.error.code'
# → "UNAUTHORIZED"

# 7. Kafka 왕복 (비동기 파이프라인) — MSA 핵심 검증
# POST /api/conversations/{id}/messages → 202 + { request_id, status: "pending" }
docker compose logs -f rai-chat rai-ai
# → [Kafka Producer] assessment.requested 발행 (chat)
# → [Kafka Consumer] assessment.requested 수신 (ai)
# → [Kafka Producer] assessment.completed 발행 (ai)
# → [Kafka Consumer] assessment.completed 수신 (chat) → assessments INSERT
curl -s localhost:8080/api/assessments/req_001 -H "Authorization: Bearer $TOKEN" | jq .status
# → pending → completed 전환 확인

# 8. 멱등성 — 같은 이벤트를 두 번 줘도 판정이 한 번만 저장되는지
# (assessments.request_id UNIQUE 로 두 번째는 무시되는지 로그 확인)

# 9. FE 전 구간 (브라우저)
cd frontend && npm run dev
# → localhost:5173 로그인 → 제품 선택 → 채팅 → 판정 카드 → 근거 패널

# 10. RAG 실제 검색 (Day 3)
curl -X POST localhost:8080/api/regulations -F file=@sample.pdf \
  -F documentId=VN-REG-001 -F country=VN ...
docker exec -it rai-postgres psql -U rai -d rai_db \
  -c "SELECT count(*) FROM rai_ai.vector_store;"
# → 청크 수 > 0, embedding 이 null 이 아닌지
# → 판정 결과 sources[] 에 VN-REG-001 이 나오는지

# 11. EKS (Day 3)
kubectl get all -n skala-gj4
kubectl logs -f deploy/rai-ai-service -n skala-gj4
curl -I https://skala-gj4-rai.skala-gj.com
```

## 자동 테스트

- `ChunkSplitterTest` — ai-service에서 유지
- 추가 권장: `AuthServiceTest`(BCrypt), `DrugServiceTest`(**회사 격리**),
  `MockAiClient` 기반 `ConversationServiceTest`, `@EmbeddedKafka` 이벤트 왕복 테스트
- 교재 원칙: **단위 테스트는 모델을 모킹한다.** `AiClient`가 인터페이스라 모킹이 쉽다 — 발표 포인트.

---

# 부록. 미해결 안건

## A. 명세 공백 — Day 1 오전에 API Architect가 클로즈

**미해결 상태로 Day 2에 들어가면 담당자가 막힌다.**

| # | 공백 | 막히는 사람 | 제안 |
|---|---|---|---|
| 1 | `GET /api/notifications` 신설 여부 (명세 2N에 "⚠ 신설 제안"으로만 있음) | BE-3 | 신설. 미합의 시 `#9` 폴링만으로 대체 |
| 2 | 규제 변경을 세션에 push하는 방식 (명세 3N 미정의) | BE-3, FE | MVP는 폴링만. 웹소켓 Out-of-Scope |
| 3 | 404 / 409 에러 `code` 문자열 미정의 | 전원 | 4-1 표로 확정 (`NOT_FOUND`, `CONFLICT`) |
| 4 | 보고서 버전 조회·되돌리기 API 없음 (명세 5번 화면엔 버전 타임라인이 있음) | BE-3, FE | `report_versions` + `GET /api/reports/{id}/versions` 신설, 또는 화면에서 제거 |
| 5 | `drug_target_countries` N:M을 쓰는 API가 없음 | DA | `POST /api/drugs`에 `target_countries[]` 추가 협의. 아니면 시드 전용 + 발표 설명용 |
| 6 | 로그아웃 / refresh token 폐기 API 없음 | BE-1 | MVP는 stateless refresh (테이블 없음), FE 로컬 스토리지 삭제만 |
| 7 | `dosage_form` 값 도메인 (`capsule` 같은 영문 코드 vs 한글) | DA, FE | 영문 소문자 코드값 + FE 라벨 매핑 |
| 8 | `GET /api/assessments/{request_id}` 에서 없는 id → 404 vs pending | BE-3, FE | 없으면 404, 처리 중이면 200 + `pending` |
| 9 | `POST /api/conversations` 응답에 `product_name` 없음 (3번 화면 헤더용) | FE | FE가 `#5` 응답 캐시 사용. 서버 변경 없음 |

## B. 팀 확인이 필요한 결정

1. **멀티모듈 vs 서비스별 독립 프로젝트** — 멀티모듈로 간다(이벤트 DTO 공유). 리스크는 한 명이 루트
   `build.gradle`을 깨면 전원이 막히는 것 → **Day 1에 DevOps가 잡고 고정, 이후 수정 금지.**
2. **LLM 공급자** — OpenAI `gpt-4o-mini` + `text-embedding-3-small`(**1536차원**) 기준이다.
   Ollama 로컬 모델로 가면 임베딩이 **1024차원**이 되어 `vector_store` DDL도 바꿔야 한다.
   *"임베딩 모델을 바꾸면 차원이 달라져 기존 벡터를 전부 다시 만들어야 한다."* **Day 1에 확정.**
3. **지원 국가** — `countries` 마스터에는 VN·ID 2건을 시드하되, **실제 규제 문서는 베트남(VN) 2~3건만**
   적재한다. 늘리면 Day 3 데모가 위험하다.
4. **PDF Export** — BE에서 OpenPDF 생성 시 **한글 폰트 임베딩**(NanumGothic.ttf)에서 시간이 샌다.
   여유 없으면 FE `window.print()` + `@media print` CSS로 대체하고 엔드포인트는 명세에만 남긴다.
5. **포트 충돌** — 이 머신의 5432/5433/8080/8081이 점유 중이다. **Day 1에 확인**하고 겹치면
   호스트 포트만 바꾼다(컨테이너 내부·K8s 포트는 유지).
6. **기획서 4장 갱신** — *"MVP는 단일 Spring Boot 애플리케이션으로 시작"* 서술과 구조 그림을
   MSA + Kafka로 고쳐야 한다. 문서와 코드가 다르면 발표 Q&A에서 지적당한다.
