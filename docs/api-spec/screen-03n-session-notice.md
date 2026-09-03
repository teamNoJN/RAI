# 3N번 — 세션 변경사항 알림

목적: 규제 변경/재판정 결과가 세션에 후속 assistant 메시지로 추가된 상태. 판정 변화(조건부→제한 가능성) 표시.
버튼 이동: [재검토 실행]→전송 · [근거 보기]→4 · [보고서 업데이트]→5

## GET /api/conversations/{id}/messages — 200
알림·재판정 결과가 assistant 메시지로 포함되어 조회됨.
```json
[ { "role": "assistant", "intent": "EXPORT_ELIGIBILITY_CHECK",
    "content": "재판정 결과 — 판정이 달라졌습니다 (조건부 → 제한 가능성)",
    "status": "completed", "created_at": "..." } ]
```

## POST /api/conversations/{id}/messages — 202
트리거: [재검토 실행] 칩 → `{ "message": "이 제품 다시 판정해줘" }`

> ⚠ 규제 변경을 세션에 push하는 방식(폴링/웹소켓)은 백엔드 협의 필요 — v0.4 미정의
