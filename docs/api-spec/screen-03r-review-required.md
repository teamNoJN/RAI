# 3R번 — 근거 불충분 가드레일 (REVIEW_REQUIRED)

목적: 근거 부족 시 임의 판정·출처 생성 없이 "추가 검토 필요"로 명시 (AI Guardrail, 상 우선순위).
별도 API 없음 — messages 응답의 분기.

```json
// Response (REVIEW_REQUIRED 케이스)
{ "result": { "eligibility": "REVIEW_REQUIRED",
    "summary": "현재 등록된 규제 자료만으로 판단하기 어렵습니다." },
  "sources": [] }
```

> 규칙: sources가 비면 문서명·조항·시행일을 절대 생성하지 않음 · "적합처럼 보이게" 렌더 금지
