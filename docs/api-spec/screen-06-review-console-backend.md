# 6번 규제 변경 검수 콘솔 — 백엔드 구현 노트

[screen-06-review-console.md](screen-06-review-console.md) 명세에 대응하는 백엔드 구현 기록.
브랜치: `feature/review-console` (base: `origin/backend` 최신 커밋 `21746f1`,
main과 통합된 이후 시점 — chat-service·drug-service·user-service·report API·FE 전부 포함)

## 이 화면의 역할

규제 변경 검수 콘솔은 **KB(지식베이스)에 아직 반영되지 않은 규제 개정안을 사람이 검토하고
승인해야만 반영되게 막는 게이트**다. AI가 요약은 만들어주지만 최종 반영 여부는 반드시
사람의 명시적 클릭이어야 하고, 누가·언제 승인했는지 감사 기록을 남긴다.

```
[운영자/AI가 개정안 등록] ──▶ regulation_revision(status=PENDING)
                                        │
                         [검수자가 목록에서 선택 → 개정 전/후 대조 확인]
                                        │
                        [승인 후 지식베이스 반영] 클릭 (명시적 사람 액션)
                                        │
                                        ▼
                    review_status=REFLECTED, reflected_at/reflected_by 기록
                                        │
                            (범위 밖) KB 실제 반영·알림 발행
```

- **기존 `regulation`/`regulation_chunk`(파싱된 KB 원문)와는 별개**의 대기열이다.
  승인해도 청킹·임베딩 재적재는 자동으로 하지 않는다 — 그건 운영자가 기존 `POST /api/regulations`로
  별도 수행하는 영역이라 이번 구현 범위 밖으로 명시했다.
- **접근 제어는 권한이 아니라 감사 기록으로 확보**한다. 로그인 사용자 전원이 검수 가능(B2B —
  회사 담당자 모두가 검수 권한 보유)하고, 대신 `reflected_at`/`reflected_by`가 누가 승인했는지를 남긴다.

## 구현 위치

`backend/src/main/java/com/rai/regulation/` — 기존 `regulation` 도메인(현재 `/api/regulations`가
이미 이 패키지에 있음)을 확장하는 형태로 넣었다. 새 서비스 모듈(`backend/services/*`)로 분리하지
않은 이유: 이 화면이 다루는 리소스가 이미 monolith의 `/api/regulations` 경로 아래 있고, 별도
도메인을 새로 만드는 게 아니라 기존 도메인의 워크플로를 확장하는 성격이기 때문.

## 작성한 파일

| 파일 | 역할 |
|---|---|
| `init-db/01_schema.sql` (수정) | `regulation_revision` 테이블 신설 |
| `regulation/entity/RegulationRevision.java` | 위 테이블과 매핑되는 엔티티 |
| `regulation/repository/RegulationRevisionRepository.java` | 목록 필터 조회 + `app_user.name` 조회(감사 표시용) |
| `regulation/dto/RegulationReviewDto.java` | `FeedItem`/`Detail`/`ReviewRequest`/`ReviewResponse` — snake_case 키를 `@JsonProperty`로 고정 |
| `regulation/service/RegulationReviewService.java` | 목록·상세 조회, 승인 처리(중복 승인 방지) |
| `regulation/controller/RegulationReviewController.java` | 3개 엔드포인트 라우팅 + 승인자 헤더 파싱 |
| `regulation/exception/RegulationApiException.java` | 404·409·401·400을 코드별로 구분해 던지는 예외 |
| `regulation/exception/ErrorResponse.java` | 명세 공통 에러 포맷 `{"error":{"code","message"}}` |
| `regulation/exception/RegulationExceptionHandler.java` | 위 예외를 HTTP 응답으로 변환. `RegulationReviewController`에만 적용되도록 범위를 좁혀서 기존 `RegulationController`(KB 관리 API) 동작에는 영향 없음 |
| `test/.../RegulationReviewServiceTest.java` | Mockito 단위 테스트 4건 |

## API 계약

```
GET  /api/regulations/feed?country=&status=     — 200, 목록(필터링)
GET  /api/regulations/{regulation_id}            — 200 · 404, 개정 전/후 대조 상세
POST /api/regulations/{regulation_id}/review      — 200 · 404 · 409(이미 반영) · 400(approved=false)
     Request  { "approved": true }
     Response { "regulation_id", "review_status": "REFLECTED",
                "reflected_at": "...", "reflected_by": "<승인자 이름>" }
```

## 설계 결정과 이유

**새 테이블 vs 기존 `regulation` 테이블 확장**: 새 테이블(`regulation_revision`)을 만들었다.
`regulation`은 이미 파싱·청킹까지 끝난 KB 원본이고, 이 화면이 다루는 건 "아직 반영 안 된 개정안"이라
생명주기와 스키마(개정 전/후 원문, AI 요약, 검수 상태)가 다르다. 한 테이블에 억지로 우겨넣으면
"이 row가 KB 원본인지 대기 중인 개정안인지"를 컬럼 하나로 계속 분기해야 해서 더 복잡해진다.

**`approved=false` → 400**: 명세에 승인(true) 흐름만 정의돼 있고 반려(reject) 상태·전이는
`review_status` enum(`PENDING`/`REFLECTED`)에 없다. 반려를 조용히 무시하거나 임의로 새 상태를
만들어내는 대신, 정의 안 된 입력이라는 걸 명확히 400으로 알린다.

**중복 승인 방지 (409)**: 이미 `REFLECTED`인 항목을 다시 승인 요청하면 409를 던진다.
감사 기록(`reflected_at`/`reflected_by`)이 최초 승인 시점의 진실이어야 하는데, 재승인을 허용하면
그 값이 덮어씌워져 "누가 진짜 최초 승인자인지"가 깨진다.

**승인자 식별 — `X-User-Id` 헤더 신뢰**: `docs/00-project-plan.md` 4-5절과 `common` 모듈의
`AuthHeaders` 규약을 그대로 따라, Gateway가 JWT 검증 후 내려주는 `X-User-Id` 헤더를 신뢰하는
방식으로 만들었다. 헤더가 없으면 401.
> **알려진 제약**: 이 monolith(`backend/src`)는 아직 Gateway 뒤에 있지 않고 자체 JWT 검증도 하지
> 않는다(`SecurityConfig`가 전체 `permitAll`). 로컬에서 이 엔드포인트를 테스트하려면 호출자가
> `X-User-Id` 헤더를 직접 실어 보내야 한다 — Gateway가 실제로 붙기 전까지의 임시 상태.

**에러 응답 포맷**: 기존 `RegulationController`(KB 관리, `/api/regulations` GET·POST ingest)는
구식 `ApiResponse` 봉투를 쓰지만, 이 화면은 FE가 직접 소비하는 명세 계약이라 `report` 모듈이
먼저 정착시킨 `{"error":{"code","message"}}` 패턴을 재사용했다. `@RestControllerAdvice`의
`assignableTypes`를 `RegulationReviewController`로 좁혀서 기존 KB 관리 API의 응답 형식은
건드리지 않았다.

## 범위 밖으로 명시한 것

- **KB 실제 반영**: 승인해도 `regulation`/`regulation_chunk`에 자동으로 청킹·임베딩되지 않는다.
  명세의 "승인 후 시스템 동작 1) 지식베이스에 개정 문서 반영"은 `review_status` 전환까지만 구현했다.
- **알림 발행**: 명세의 "영향 국가 세션 보유 사용자에게 REGULATION_CHANGE 알림 발행
  (`GET /api/notifications`에 추가)"은 구현하지 않았다. `GET /api/notifications` 자체가 아직
  어디에도 없는 "⚠ 신설 제안" 상태([screen-02n-changes.md](screen-02n-changes.md) 참고)라 이번
  범위에 포함하지 않기로 했다.

## 테스트

`RegulationReviewServiceTest` — DB 의존 없는 Mockito 단위 테스트 4건, 전부 통과:
- 승인 시 `REFLECTED` 전환 + 승인자 이름 기록
- 이미 반영된 항목 재승인 → 409
- 존재하지 않는 규제 승인 시도 → 404
- `approved=false` → 400

전체 테스트(`./gradlew :test`)는 33건 중 31건 통과 — 실패 2건은 로컬에 Postgres가 없어서 나는
기존 DB 연동 테스트(`RegulationChunkRepositoryTest`, `RaiApplicationTests`)로 이번 작업과 무관.
