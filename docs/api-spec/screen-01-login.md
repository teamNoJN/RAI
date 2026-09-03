# 1번 — 로그인 / 회원가입

목적: 이메일 인증 + `company_name` 기반 회사 자동 생성/소속. 성공 시 **2번 대시보드**로 이동.
버튼 이동: [로그인]→2 · 실패(401)→1E · [가입하고 시작하기]→2

## POST /api/auth/login — 200 · 401
트리거: [로그인] 버튼
```json
// Request
{ "email": "ra@pharm.co", "password": "••••••" }
// Response 200
{
  "access_token": "eyJ...", "refresh_token": "eyJ...",
  "user": { "user_id": "U001", "name": "이서연", "email": "ra@pharm.co", "company_id": "C001" }
}
```

## POST /api/auth/signup — 201 · 400 · 409
트리거: [가입하고 시작하기] 버튼 (409 → 이메일 필드 인라인 에러)
```json
// Request
{ "email": "ra@pharm.co", "password": "••••••", "name": "이서연", "company_name": "한빛제약" }
// Response 201
{ "user_id": "U001", "email": "ra@pharm.co", "company_id": "C001", "company_name": "한빛제약" }
```

## POST /api/auth/refresh — 200 · 401
트리거: 앱 부팅·새로고침 시 자동 (실패 시 1E 세션 만료)
```json
// Request
{ "refresh_token": "eyJ..." }
// Response 200
{ "access_token": "eyJ..." }
```

## GET /api/auth/me — 200 · 401
트리거: 부팅 시 사용자 확인 → 레일 하단 표시
```json
// Response 200
{ "user_id": "U001", "name": "이서연", "email": "ra@pharm.co", "company_id": "C001" }
```

> 노트: SSO 없음(이메일 전용) · role 없음 · 회사명이 기존과 같으면 자동 소속, 새 이름이면 회사 생성
