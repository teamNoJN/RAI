# 4번 — 판정 근거 상세 패널

목적: 성분별 verdict + AI 요약 + 규제 원문 출처를 짝지어 표시. 신뢰(이서연) 핵심 화면.
버튼 이동: [이 근거로 보고서에 반영]→5 · ✕→3 · 출처 ↗→외부

## 신규 호출 없음 — 메시지 payload 재사용 (렌더 매핑)
- `result.ingredient_assessments[]` → 성분별 행 (ingredient / status / reason)
- `sources[]` → 근거 출처 (title · authority · version · effective_date · section · source_url)
- `sources[].version` → "판정 기준 · 지식베이스 반영 일자" 표기

## POST /api/reports — 202 · 404
트리거: [이 근거로 보고서에 반영]
```json
// Request
{ "conversation_id": "CV01", "request_id": "req_001" }
// Response 202
{ "status": "pending", "job_id": "job_01" }
```

> 출처 링크 클릭 시 `citation_clicked` 이벤트 (PostHog FE SDK)
