# 5번 — 보고서 작업 뷰

목적: 초안을 대화로 다듬고 PDF 내보내기. **채팅 수정 1건 = version +1** (버전 타임라인).
버튼 이동: 수정 채팅→PATCH · [PDF]→다운로드 · 버전 행→해당 버전 보기/되돌리기

## POST /api/reports — 202 · 404
```json
// Request
{ "conversation_id": "CV01", "request_id": "req_001" }
// Response 202
{ "status": "pending", "job_id": "job_01" }
```

## GET /api/reports/jobs/{job_id} — 200 · 404
트리거: 생성 폴링 — 완료 시 프리뷰·citations 렌더
```json
// Response 200
{ "status": "completed", "report_id": "R001",
  "draft_content": "...", "sources": [ "...4번과 동일 스키마" ], "version": 1 }
```

## PATCH /api/reports/{report_id} — 200 · 404
트리거: 우측 수정 채팅 전송 (버전 타임라인에 수정 문장이 라벨로 기록)
```json
// Request
{ "instruction": "3번 항목을 더 자세히" }
// Response 200
{ "report_id": "R001", "draft_content": "...(수정됨)...", "version": 2 }
```

## GET /api/reports/{report_id}/export?format=pdf — 200 · 404
Response: 파일 스트림 (application/pdf) — 생성 시각 + "AI 초안 / 사람 검토 필요" 문구 포함. MVP는 PDF만.

> "AI 생성 초안 · 제출 전 검토 필요" 배지는 어떤 상태에서도 숨기지 않음
