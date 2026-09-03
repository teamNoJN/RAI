# 2N번 — 변경사항 전체 목록

목적: 규제 변경·재검토 필요·재판정 완료 알림 전체 조회 (유형/읽음 필터).
버튼 이동: [세션에서 확인]→3N · [재검토]→3(새 세션) · [결과 보기]→3N

## GET /api/notifications — 200 ⚠ 신설 제안 (v0.4에 없음)
```json
// Response 200 (제안 스키마)
[ { "notification_id": "N001",
    "type": "REGULATION_CHANGE",   // REGULATION_CHANGE | REASSESS_NEEDED | REASSESS_DONE
    "title": "MFDS 고시 개정", "drug_id": "D001", "country_id": "VN",
    "conversation_id": "CV01", "read": false, "created_at": "..." } ]
```

> ⚠ 백엔드 협의 안건. 합의 전 MVP 대안: `GET /drugs/{id}/reassessment-needed` 폴링만 사용
