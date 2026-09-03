# 2F번 — 제품 등록 폼 (모달)

목적: 제품 신규 등록. 성공 시 대시보드 복귀 + 새 카드 + 채팅 시작 유도.
버튼 이동: [등록하기]→2(성공) · [취소]→2

## POST /api/drugs — 201 · 400
트리거: [등록하기] 버튼
```json
// Request
{ "product_name": "아목시실린 캡슐",
  "ingredients": ["Amoxicillin", "첨가제 B"],
  "strength": "500mg", "dosage_form": "capsule" }
// Response 201
{ "drug_id": "D002", "product_name": "아목시실린 캡슐", "version": 1 }
// Response 400 — product_name/ingredients 누락, 필드 인라인 표시
{ "error": { "code": "VALIDATION_ERROR", "message": "요청 처리 중 오류가 발생했습니다. 다시 시도해주세요." } }
```
