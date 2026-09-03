# RAI 화면별 API 명세 (상세)

기준: **API 명세서 v0.4 (MVP)** · Figma `03 Design` 페이지 화면 번호와 1:1 대응.
공통 규약: `Authorization: Bearer {access_token}` · snake_case · 공통 에러 `{"error":{"code","message"}}` · 비동기 `pending→completed/failed` (2초 폴링, 30초 타임아웃).

| # | 화면 | 파일 |
|---|---|---|
| 1 | 로그인 / 회원가입 | [screen-01-login.md](screen-01-login.md) |
| 1E | 인증 예외 상태 | [screen-01e-auth-errors.md](screen-01e-auth-errors.md) |
| 2 | 제품 대시보드 | [screen-02-dashboard.md](screen-02-dashboard.md) |
| 2E | 대시보드 Empty State | [screen-02e-empty.md](screen-02e-empty.md) |
| 2F | 제품 등록 폼 | [screen-02f-drug-register.md](screen-02f-drug-register.md) |
| 2S | 약 검색 | [screen-02s-drug-search.md](screen-02s-drug-search.md) |
| 2N | 변경사항 전체 목록 | [screen-02n-changes.md](screen-02n-changes.md) |
| 3 | 채팅 워크스페이스 (핵심) | [screen-03-chat.md](screen-03-chat.md) |
| 3C | 컨텍스트 변경 팝오버 | [screen-03c-context-change.md](screen-03c-context-change.md) |
| 3N | 세션 변경사항 알림 | [screen-03n-session-notice.md](screen-03n-session-notice.md) |
| 3R | 근거 불충분 가드레일 | [screen-03r-review-required.md](screen-03r-review-required.md) |
| 3E | 판정 실패 / 타임아웃 | [screen-03e-failed.md](screen-03e-failed.md) |
| 4 | 판정 근거 상세 패널 | [screen-04-evidence-panel.md](screen-04-evidence-panel.md) |
| 5 | 보고서 작업 뷰 | [screen-05-report-view.md](screen-05-report-view.md) |
| 5L | 보고서 보관함 | [screen-05l-report-archive.md](screen-05l-report-archive.md) |
| 6 | 규제 변경 검수 콘솔 (admin) | [screen-06-review-console.md](screen-06-review-console.md) |
| P | Post-MVP (6·7번) | [screen-p-post-mvp.md](screen-p-post-mvp.md) |
