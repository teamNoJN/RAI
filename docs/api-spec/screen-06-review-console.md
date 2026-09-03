# 6번 — 규제 변경 검수 콘솔 (admin 전용)

목적: 신규·개정 규제를 사람이 검수·승인해 지식베이스에 반영. 승인은 반드시 명시적 사람 액션이며(자동 반영 금지), 누가·언제 승인했는지 감사 기록으로 남긴다 (박준호 페르소나).
버튼 이동: 레일 [규제 검수 (admin)]→6 · 목록 행 클릭→우측 상세 · [승인 후 반영]→상태 전환 + 알림 발행
접근 제어: `role: "admin"` 만 접근 가능 — 그 외 403 (라우트 가드 + 서버 검증 이중)

> 전제: **auth 응답에 `role` 필드 추가** (member | admin). 회사를 새로 만든 첫 사용자가 admin.
> `screen-01-login.md` 의 login/me Response 에 `"role": "admin"` 필드가 추가된다.

## GET /api/regulations/feed?country=&status= — 200 · 403
트리거: 콘솔 진입 시 검수 대기 목록 로드 (국가·상태 필터)
```json
// Response 200
[ { "regulation_id": "REG001", "country_id": "VN",
    "regulation_type": "고시", "title": "MFDS 고시 2026-45호 개정",
    "summary": "첨가제 함량 상한 인하",
    "effective_date": "2026-09-01", "source_url": "https://...",
    "review_status": "PENDING",     // PENDING | REFLECTED
    "created_at": "..." } ]
```

## GET /api/regulations/{regulation_id} — 200 · 403 · 404
트리거: 목록 행 선택 → 우측 상세 (개정 전/후 대조는 요약만 믿지 않도록 원문과 함께)
```json
// Response 200
{ "regulation_id": "REG001", "country_id": "VN",
  "regulation_type": "고시", "title": "MFDS 고시 2026-45호 개정",
  "before": "제4조 2항 — 첨가제 B 함량 상한 1.0mg …(개정 전 원문)",
  "after":  "제4조 2항 — 첨가제 B 함량 상한 0.5mg …(개정 후 원문)",
  "ai_summary": "○○ 고시 개정으로 첨가제 함량 상한이 1.0mg → 0.5mg 로 인하됨",
  "effective_date": "2026-09-01", "source_url": "https://...",
  "review_status": "PENDING",
  "reflected_at": null, "reflected_by": null }
```

## POST /api/regulations/{regulation_id}/review — 200 · 403 · 404 · 409(이미 반영)
트리거: [승인 후 지식베이스 반영] — 명시적 사람 액션
```json
// Request
{ "approved": true }
// Response 200 — 반영 시각·주체 자동 로깅 (감사 추적)
{ "regulation_id": "REG001", "review_status": "REFLECTED",
  "reflected_at": "2026-09-03T14:02:00Z", "reflected_by": "박준호" }
```

## 승인 후 시스템 동작 (플로우 연결)
1. 지식베이스에 개정 문서 반영 → 이후 판정은 새 기준으로 실행
2. 영향 국가의 세션 보유 사용자에게 **REGULATION_CHANGE 알림 발행** (`GET /api/notifications` 에 추가)
3. 사용자 흐름: 알림 → 3N 세션 알림 → [재검토 실행] → 판정 변화 표시

> 감사 이력: reflected_at / reflected_by 는 서버가 기록하며 수정 불가 · REFLECTED 항목도 목록에서 조회 가능(이력)
