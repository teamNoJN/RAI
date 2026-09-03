# P — Post-MVP (6번 검수 콘솔 · 7번 제품 이력)

MVP 범위 밖 — 참고용. 제품 성분 변경만 API가 존재하며, 결과는 2번 변경사항 패널의 "재검토 필요"로 연결.

## PATCH /api/drugs/{drug_id} — 200 · 404 (7번 [성분 정보 수정])
```json
// Request
{ "ingredients": ["Amoxicillin", "첨가제 C"], "strength": "500mg" }
// Response 200
{ "drug_id": "D001", "version": 3, "has_prior_assessments": true }
```
`has_prior_assessments: true` → FE가 "재검토 필요할 수 있습니다 → [재검토]" 배너 표시 (자동 재판정은 Post-MVP).

## 6번 admin 검수 콘솔 — v0.2 참고
`GET /api/regulations/feed` · `GET /api/regulations/{id}` · `POST /api/regulations/{id}/review` (확장 시 출발점)
