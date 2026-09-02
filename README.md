# RAI (Regulatory AI)

제약·바이오 RA 담당자를 위한 규제 검토 코파일럿. 제품과 수출 대상 국가를 선택한 뒤 자연어 질문으로
1차 수출 가능성 검토 → 규제 근거 확인 → 보고서 초안 작성까지 지원한다.

## 구조

```
RAI/
├── backend/                       # Spring Boot 3.4.5 / Java 21 (port 8090)
│   ├── src/main/java/com/rai/
│   │   ├── config/                # Security, JPA, 전역 예외 처리
│   │   ├── common/                # ApiResponse
│   │   ├── regulation/            # 규제 KB 도메인 (controller / service / repository / entity / dto)
│   │   └── parser/                # 규제 문서 파서 (PDF/Text → 청크 → 임베딩 → pgvector)
│   ├── src/main/resources/        # application.yml + local / docker 프로필
│   ├── build.gradle
│   └── Dockerfile
├── ai/                            # 프롬프트, Golden Test Set 등 (예정)
├── frontend/                      # Vue 3 + Vite + TypeScript (port 5173)
├── init-db/                       # PostgreSQL 초기 DDL (pgvector, regulation_* 테이블)
├── docker-compose.yml             # 개발용 인프라
└── .env.example                   # compose 환경변수 예시
```

msa-lecture 에서 가져온 것: Gradle / Spring Boot 구성, 도메인별 controller-service-repository-entity-dto 레이어,
프로필 계층(`application.yml` → `application-{profile}.yml` → compose `environment`), Dockerfile 과 compose 형식.
Eureka 와 서비스 분리는 MVP 규모에 맞지 않아 쓰지 않는다. 도메인이 늘어나면 `com.rai.<domain>` 패키지를 추가한다.

## 사전 준비

- **Java 21** (Temurin 등). Gradle 은 wrapper 를 쓰므로 설치 불필요.
- **Docker Desktop** (compose 포함)
- **Node.js 22+** (frontend)

## 로컬 개발

```bash
# 1. PostgreSQL + pgvector 기동 (최초 실행 시 init-db/ 의 DDL 자동 적용)
docker compose up -d postgres

# 2. 백엔드 기동 (기본 프로필 local)
cd backend
./gradlew bootRun
```

확인:

- Swagger UI: http://localhost:8090/swagger-ui.html
- 규제 문서 목록: http://localhost:8090/api/regulations

테스트 (postgres 컨테이너가 떠 있어야 함):

```bash
cd backend && ./gradlew test
```

DB 직접 접속:

```bash
docker exec -it rai-postgres psql -U rai -d rai_db
```

## 규제 문서 적재 (파서)

운영자가 검수한 규제 문서를 파일 + Metadata 로 등록하면 텍스트 추출 → 청크 분할 → 저장까지 수행한다.
임베딩 생성은 Embedding Adapter 연동 후 붙는다.

```bash
curl -X POST http://localhost:8090/api/regulations \
  -F file=@./sample.pdf \
  -F documentId=VN-REG-001 -F country=VN \
  -F authority="Drug Administration of Vietnam" -F title="Regulation Title" \
  -F documentVersion=2026.01 -F effectiveDate=2026-01-01 -F section=4.2 \
  -F sourceUrl=https://...
```

## 프론트엔드 로컬 개발

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

## 컨테이너로 전체 실행

```bash
docker compose --profile full up -d --build
docker compose --profile full down
```

## 포트

| 구성요소 | 호스트 포트 | 비고 |
|---|---|---|
| PostgreSQL (`rai-postgres`) | 5434 | DB `rai_db`, 계정 `rai` / `rai-dev-1`. 루트 `.env` 의 `POSTGRES_PORT` 로 변경 |
| backend | 8090 | |
| frontend (Vite dev) | 5173 | |

## 설정과 프로필

| 프로필 | 용도 |
|---|---|
| `local` (기본) | `localhost:5434` DB 사용, SQL 로그 출력 |
| `docker` | compose 에서 사용. datasource 는 environment 로 주입 |

- `spring.jpa.hibernate.ddl-auto=validate` 이다. 스키마는 `init-db/01_init.sql` 이 소유하고, 엔티티와 불일치하면 기동에 실패한다. 엔티티를 바꾸면 DDL 도 같이 바꾼다.
- DB 를 처음부터 다시 만들려면 `docker compose down -v` 후 다시 `up`.

## 데이터 모델

PRD 3. 데이터 요구사항의 규제 문서 Metadata 를 그대로 사용한다.

- `regulation_documents` — document_id, country, authority, title, document_version, published_date, effective_date, section, source_url, status
- `regulation_chunks` — 파싱된 본문 청크 + `VECTOR(1536)` 임베딩 (RAG Retrieval 대상)

DDL 은 `init-db/01_init.sql`, 엔티티는 `backend/src/main/java/com/rai/regulation/entity/` 에 있다.

## 참고

- 인증(Supabase Auth)은 아직 연동 전이라 `SecurityConfig` 가 전체 `permitAll` 이다. 연동 시 `application.yml` 의 jwk-set-uri 주석을 풀고 `oauth2ResourceServer(jwt)` 로 전환한다.
- DB 비밀번호 기본값은 로컬 개발용이다. 배포 환경에서는 반드시 `.env` / Secret 으로 덮어쓴다.
