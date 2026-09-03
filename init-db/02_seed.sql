-- =============================================================
-- RAI — 마스터 시드 (init-db/02_seed.sql)
-- 01_schema.sql 다음에 실행(파일명 정렬 순). country가 비어 있으면
-- /api/conversations 가 country_id FK 위반으로 실패하므로 최소 시드 필요.
-- =============================================================

-- MVP Pilot 대상 국가 — 기획 시나리오(VN/ID/PH) + KR. 필요 시 행 추가로 국가 확장.
INSERT INTO country (country_id, name) VALUES
    ('VN', 'Vietnam'),
    ('ID', 'Indonesia'),
    ('PH', 'Philippines'),
    ('KR', 'South Korea')
ON CONFLICT (country_id) DO NOTHING;