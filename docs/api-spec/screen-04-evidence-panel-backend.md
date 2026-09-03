# 4번 판정 근거 상세 패널 — 백엔드 구현 노트

[screen-04-evidence-panel.md](screen-04-evidence-panel.md) 명세에 대응하는 백엔드 구현 기록.
브랜치: `feature/evidence-panel` (base: `origin/backend`)

## 이 화면의 역할

Evidence Panel은 데이터를 새로 만들지 않는다. **채팅(screen-03)에서 이미 완성된 판정 결과를
중계해서 성분별로 재구성해 보여주고**, 사용자가 그 근거에 만족하면 **보고서 생성으로 넘겨주는**
파이프라인 중간 정거장이다.

```
[채팅 판정 결과] ──(payload 재사용, 신규 호출 없음)──▶ [evidence-panel 렌더링]
                                                              │
                                          [이 근거로 보고서에 반영] 클릭
                                                              ▼
                                                  POST /api/reports
                                                              │
                                                              ▼
                                                   report(status=pending)
                                                              │
                                                              ▼
                                                    [screen-05 보고서 뷰]
```

- **받는 쪽**: `result.ingredient_assessments[]`, `sources[]` — screen-03 메시지 응답에 이미 포함된 값을
  그대로 매핑해서 렌더링. 별도 GET API 없음.
- **주는 쪽**: `POST /api/reports` — 이번에 구현한 **유일한 신규 엔드포인트**.

## 구현 위치

`backend/src` (현재 실제로 빌드·구동되는 단일 앱 구조. `backend/services/chat-service`는
아직 빈 멀티모듈 스캐폴딩이라 이번 작업 대상에서 제외)

## 작성한 파일

| 파일 | 역할 |
|---|---|
| `backend/src/main/java/com/rai/common/exception/NotFoundException.java` | 404 응답용 예외 |
| `backend/src/main/java/com/rai/config/GlobalExceptionHandler.java` (수정) | `NotFoundException` → 404 핸들러 추가 |
| `backend/src/main/java/com/rai/report/entity/Report.java` | `init-db/01_schema.sql`의 `report` 테이블과 매핑 |
| `backend/src/main/java/com/rai/report/repository/ReportRepository.java` | `conversation_id`/`request_id` 존재 검증(native query) |
| `backend/src/main/java/com/rai/report/dto/ReportDto.java` | 요청/응답 DTO. snake_case 키를 `@JsonProperty`로 명시 고정 |
| `backend/src/main/java/com/rai/report/service/ReportService.java` | 존재 검증 후 `status=pending` 보고서 생성 |
| `backend/src/main/java/com/rai/report/controller/ReportController.java` | `POST /api/reports` — 202 반환 |
| `backend/src/test/java/com/rai/report/ReportServiceTest.java` | Mockito 단위 테스트 3건 |

## API 계약

```
POST /api/reports
Request  { "conversation_id": "<uuid>", "request_id": "req_001" }
Response 202 { "status": "pending", "job_id": "<report_id>" }
Response 404 conversation_id 또는 request_id(해당 conversation 소속)가 존재하지 않음
```

`job_id`는 생성된 `report.report_id`를 그대로 반환한다.

## 검증 로직

1. `conversation_id`가 `conversation` 테이블에 존재하는지 확인 → 없으면 404
2. `request_id`가 해당 `conversation_id` 소속 `assessment`로 존재하는지 확인 → 없으면 404
3. 둘 다 통과하면 `report` row를 `status='pending'`으로 생성

`conversation`/`assessment`는 아직 이 서비스에 JPA 엔티티가 없어서(screen-03이 미구현 상태),
존재 여부만 native query로 확인하고 엔티티는 새로 만들지 않았다 — 이번 작업 범위를
`POST /api/reports` 최소 구현으로 한정하기로 한 결정에 따른 것.

## 테스트

`ReportServiceTest` — DB 의존 없는 Mockito 단위 테스트 3건, 전부 통과 확인:
- 정상 생성 (conversation·assessment 존재 → pending 생성)
- conversation 없음 → `NotFoundException`
- assessment 없음 → `NotFoundException`

## 알려진 범위 밖 이슈

`regulation/` 모듈의 기존 엔티티(`RegulationDocument` → 테이블명 `regulation_documents`,
`RegulationChunk` → `regulation_chunks`)가 실제 `init-db/01_schema.sql`의 테이블명(`regulation`,
`regulation_chunk`)·컬럼 구조와 어긋나 있다. `ddl-auto: validate` 설정상 DB 연결 시 기동 실패
가능성이 있으나, 이번 `POST /api/reports` 작업과 무관한 기존 상태라 손대지 않았다.
