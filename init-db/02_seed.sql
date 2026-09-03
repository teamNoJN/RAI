-- =============================================================
-- RAI — 마스터 시드 (init-db/02_seed.sql)
-- 01_schema.sql 다음에 실행(파일명 정렬 순). country가 비어 있으면
-- /api/conversations 가 country_id FK 위반으로 실패하므로 최소 시드 필요.
-- =============================================================

-- 국가 마스터 = 규제 문서를 등록할 수 있는 후보국 (regulation.country_id FK 대상).
-- 채팅 드롭다운에는 이 중 "규제 문서가 KB 에 있는 나라"만 노출된다 (FE 가드).
INSERT INTO country (country_id, name) VALUES
    ('VN', 'Vietnam'),
    ('ID', 'Indonesia'),
    ('PH', 'Philippines'),
    ('KR', 'South Korea'),
    ('US', 'United States'),
    ('TH', 'Thailand'),
    ('SG', 'Singapore')
ON CONFLICT (country_id) DO NOTHING;