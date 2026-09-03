# 3번 — 채팅 워크스페이스 (핵심)

목적: 자연어 한 줄로 조회·판정·보고서 처리. 퀵 칩 = 정해진 문장의 단축 경로 (동일 엔드포인트).
버튼 이동: [← 대시보드]→2 · [변경]→3C · [근거 보기]→4 · 보고서 완료→5 · 퀵 칩→(전송)
intent 3종: `EXPORT_ELIGIBILITY_CHECK` · `REPORT_GENERATE` · `REPORT_REVISE`

## POST /api/conversations/{id}/messages — 200 · 202 · 400(중복) · 404
트리거: 전송 버튼 · 퀵 액션 칩 공용
```json
// Request
{ "message": "이 제품 베트남 수출 가능한가?" }
// Response 200 — AI 응답 계약 (202면 status:"pending" + request_id 먼저)
{
  "request_id": "req_001",
  "status": "completed",
  "intent": "EXPORT_ELIGIBILITY_CHECK",
  "context": { "drug_id": "D001", "country_id": "VN" },
  "result": {
    "summary": "일부 성분에 대한 추가 검토가 필요합니다.",
    "eligibility": "REVIEW_REQUIRED",
    "ingredient_assessments": [
      { "ingredient": "Ingredient A", "status": "NO_RESTRICTION",
        "reason": "현재 검색된 규제에서 직접적인 제한이 확인되지 않았습니다." }
    ],
    "requirements": [], "risks": [], "recommended_actions": []
  },
  "sources": [
    { "document_id": "VN-REG-001", "title": "Regulation Title",
      "authority": "Drug Administration of Vietnam", "version": "2026.01",
      "effective_date": "2026-01-01", "section": "4.2", "source_url": "https://..." }
  ]
}
```
enum: `eligibility` = POSSIBLE | CONDITIONAL | REVIEW_REQUIRED | RESTRICTED / 성분 status = NO_RESTRICTION | CONDITIONAL | REVIEW_REQUIRED | RESTRICTED

## GET /api/conversations/{id}/messages — 200 · 404
트리거: 진입·이어하기 시 타임라인 복원
```json
// Response 200
[ { "role": "user", "content": "...", "created_at": "..." },
  { "role": "assistant", "content": "...", "intent": "EXPORT_ELIGIBILITY_CHECK",
    "status": "completed", "created_at": "..." } ]
```

## GET /api/assessments/{request_id} — 200 · 404
트리거: 202 수신 후 2초 폴링, 30초 초과 → 3E. 응답은 AI 응답 계약과 동일 구조.

## POST /api/assessments/{request_id}/feedback — 201 · 404
트리거: 판정 카드 👍 유용 / ✎ 수정 필요
```json
// Request
{ "rating": "helpful", "reason": "..." }   // rating: helpful | needs_revision
// Response 201
{ "status": "recorded" }
```

> 노트: 모든 판정 카드에 Disclaimer 고정 · 전송 중 send 비활성(중복 방지)
