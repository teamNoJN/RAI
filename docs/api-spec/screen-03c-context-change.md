# 3C번 — 컨텍스트 변경 팝오버

목적: [변경] 클릭 시 국가 재선택. 약(drug)은 변경 불가 — 새 세션으로 안내.

## PATCH /api/conversations/{conversation_id} — 200 · 404
```json
// Request
{ "country_id": "ID" }
// Response 200
{ "conversation_id": "CV01", "drug_id": "D001", "country_id": "ID" }
```

> 노트: 변경 후 판정은 새 국가 기준으로 실행됨을 안내 문구로 표시
