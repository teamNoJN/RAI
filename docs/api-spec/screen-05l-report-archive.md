# 5L번 — 보고서 보관함

목적: 보고서 이력 목록 (유형 필터). 레일 [보고서 보관함] 목적지.
버튼 이동: 행 클릭→5 · [PDF]→다운로드

## GET /api/reports — 200
```json
// Response 200
[ { "report_id": "R001", "drug_id": "D001", "country_id": "VN",
    "status": "completed", "version": 3, "created_at": "..." } ]
```

> 유형 태그(적합성검토/규제변경영향/제품변경보고/정기현황)는 FE 분류 표시
