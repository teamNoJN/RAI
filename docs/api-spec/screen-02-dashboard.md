# 2번 — 제품 대시보드

목적: 약+국가 컨텍스트를 고정해 채팅 세션을 시작하는 허브 + 변경사항 알림 패널.
버튼 이동: [＋ 제품 등록]→2F · 검색창→2S · [채팅 시작▾]→국가 선택→3 · 레일 최근 대화 [이어하기]→3 · 변경사항 [세션에서 확인]→3N · [재검토]→3(새 세션) · [모든 변경사항 보기]→2N

## GET /api/drugs — 200
트리거: 진입 시 제품 카드 로드 (빈 배열 → 2E)
```json
// Response 200
[ { "drug_id": "D001", "product_name": "아목시실린 캡슐",
    "ingredients": ["Amoxicillin", "첨가제 B"],
    "strength": "500mg", "dosage_form": "capsule", "version": 2 } ]
```

## GET /api/countries — 200
트리거: [채팅 시작 ▾] 드롭다운 구성 — **목록 외 선택 차단**
```json
// Response 200
[ { "country_id": "VN", "name": "베트남" }, { "country_id": "ID", "name": "인도네시아" } ]
```

## POST /api/conversations — 201 · 400(국가 미선택) · 404
트리거: 드롭다운에서 국가 선택 → 세션 생성 → 3번 이동
```json
// Request
{ "drug_id": "D001", "country_id": "VN" }
// Response 201
{ "conversation_id": "CV01", "drug_id": "D001", "country_id": "VN", "created_at": "2026-09-03T10:00:00Z" }
```

## GET /api/conversations?limit=5 — 200
트리거: 레일 최근 대화 목록
```json
// Response 200
[ { "conversation_id": "CV01", "product_name": "아목시실린 캡슐", "country_id": "VN", "last_message_at": "..." } ]
```

## GET /api/drugs/{drug_id}/reassessment-needed — 200
트리거: 재검토 필요 배지 · 변경사항 패널
```json
// Response 200
{ "needed": true, "prior_countries": ["VN"], "message": "기존 판정 결과가 존재합니다. 재검토가 필요할 수 있습니다." }
```

> ⚠ 변경사항 패널 데이터 소스 미정 — `GET /api/notifications` 신설 협의 (2N 참고)
