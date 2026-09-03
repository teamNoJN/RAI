# 3E번 — 판정 실패 / 타임아웃

목적: `failed` 또는 30초 폴링 초과 시 공통 에러 + [재시도]. 전송 중 send 비활성(중복 방지).
버튼 이동: [재시도]→동일 message 재전송 · [질문 수정하기]→입력창 포커스

## GET /api/assessments/{request_id} — 200 (status: failed)
```json
{ "request_id": "req_001", "status": "failed" }
```
UI 표시(공통 에러 계약):
```json
{ "error": { "code": "INTERNAL_ERROR", "message": "요청 처리 중 오류가 발생했습니다. 다시 시도해주세요." } }
```

> 보고서 생성 실패(`GET /reports/jobs/{job_id}` → failed)도 동일 패턴
