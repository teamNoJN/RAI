#!/usr/bin/env bash
# =============================================================
# RAI — 모의 테스트(시연)용 데이터 구성
#
# 하는 일:
#   1) 업무 데이터 초기화 (제품·대화·판정·보고서·검수 피드)
#      — 계정·국가·규제 KB(문서/청크)는 보존
#   2) 실제 API 로 현실적인 제품 3종 등록
#   3) 실제 판정 2건 생성 (VN·US — RAG 이 실 KB 출처를 인용)
#   4) 보고서 1건 생성 + 대화형 수정 1회 (v2)
#   5) 검수 콘솔 피드 시드 (PENDING 2건 + REFLECTED 1건, 감사 기록 포함)
#
# 사용법:
#   ./scripts/seed-demo.sh <이메일> <비밀번호> [게이트웨이 URL]
#   예: ./scripts/seed-demo.sh seoyeon@pharm.co password123
#
# 전제: docker compose --profile full 스택 기동 + seed-kb.sh 로 KB 적재 완료
# =============================================================
set -euo pipefail

EMAIL="${1:?사용법: seed-demo.sh <이메일> <비밀번호> [게이트웨이]}"
PASSWORD="${2:?비밀번호를 입력해주세요}"
BASE="${3:-http://localhost:18080}"
# -i 필수: heredoc(STDIN)으로 SQL 을 넘긴다
PSQL=(docker exec -i rai-postgres psql -U rai rai_db -q -v ON_ERROR_STOP=1)

json() { python3 -c "import json,sys;print(json.load(sys.stdin)$1)"; }

echo "▸ [1/5] 업무 데이터 초기화 (계정·국가·규제 KB 는 보존)"
"${PSQL[@]}" <<'SQL'
TRUNCATE message, assessment, source, feedback, report, conversation, drug,
         regulation_revision, analytics_event CASCADE;
SQL

echo "▸ 로그인: $EMAIL"
TOKEN=$(curl -sf -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | json "['access_token']")
AUTH="Authorization: Bearer $TOKEN"
USER_ID=$("${PSQL[@]}" -tA -c "SELECT user_id FROM app_user WHERE email='$EMAIL'")

echo "▸ [2/5] 제품 등록 (3종)"
reg_drug() {
  curl -sf -X POST "$BASE/api/drugs" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "$1" | json "['drug_id']"
}
D1=$(reg_drug '{"product_name":"아목시실린 캡슐 500mg","ingredients":["Amoxicillin"],"strength":"500mg","dosage_form":"capsule"}')
D2=$(reg_drug '{"product_name":"세프디닐 정 100mg","ingredients":["Cefdinir"],"strength":"100mg","dosage_form":"tablet"}')
D3=$(reg_drug '{"product_name":"아세트아미노펜 시럽","ingredients":["Acetaminophen"],"strength":"160mg/5mL","dosage_form":"syrup"}')
echo "   - $D1 아목시실린 / $D2 세프디닐 / $D3 아세트아미노펜"

ask() { # drug country question -> conversation_id (판정 완료까지 대기)
  local CV REQ STATUS
  CV=$(curl -sf -X POST "$BASE/api/conversations" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"drug_id\":\"$1\",\"country_id\":\"$2\"}" | json "['conversation_id']")
  REQ=$(curl -sf -X POST "$BASE/api/conversations/$CV/messages" -H "$AUTH" \
    -H 'Content-Type: application/json' -d "{\"message\":\"$3\"}" | json "['request_id']")
  for _ in $(seq 1 15); do
    sleep 2
    STATUS=$(curl -sf "$BASE/api/assessments/$REQ" -H "$AUTH" | json "['status']")
    [ "$STATUS" != "pending" ] && break
  done
  echo "$CV $REQ"
}

echo "▸ [3/5] 판정 세션 생성 (실 RAG — KB 출처 인용)"
read -r CV1 REQ1 <<<"$(ask "$D1" VN "이 약 베트남 수출 가능해?")"
echo "   - VN 세션 $CV1 (판정 $REQ1)"
read -r CV2 REQ2 <<<"$(ask "$D2" US "미국 수출 시 등록 요건 알려줘")"
echo "   - US 세션 $CV2 (판정 $REQ2)"

echo "▸ [4/5] 보고서 생성 + 수정 1회 (v2)"
JOB=$(curl -sf -X POST "$BASE/api/reports" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"conversation_id\":\"$CV1\",\"request_id\":\"$REQ1\"}" | json "['job_id']")
for _ in $(seq 1 15); do
  sleep 2
  ST=$(curl -sf "$BASE/api/reports/jobs/$JOB" -H "$AUTH" | json "['status']")
  [ "$ST" != "pending" ] && break
done
curl -sf -X PATCH "$BASE/api/reports/$JOB" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"instruction":"요구사항 항목을 더 구체적으로 정리해줘"}' >/dev/null
echo "   - 보고서 $JOB (v2)"

echo "▸ [5/5] 검수 콘솔 피드 시드 (PENDING 2 · REFLECTED 1)"
"${PSQL[@]}" <<SQL
INSERT INTO regulation_revision
  (country_id, regulation_type, title, summary, before_content, after_content, ai_summary,
   effective_date, source_url, review_status, reflected_at, reflected_by)
VALUES
  ('VN','고시','Circular 08/2022/TT-BYT 개정안 — 등록 갱신 서류 간소화',
   '갱신 신청 시 CPP 제출 요건 완화 · 심사 기한 단축',
   '갱신 신청 시 WHO 양식 CPP 원본 및 공증 사본을 모두 제출하여야 한다. 심사 기한은 접수일로부터 3개월로 한다.',
   '갱신 신청 시 WHO 양식 CPP 사본 제출로 갈음할 수 있다. 심사 기한은 접수일로부터 2개월로 단축한다.',
   'CPP 원본 제출 의무가 사본 제출로 완화되고 갱신 심사 기한이 3개월→2개월로 단축됩니다. 갱신 예정 품목의 서류 준비 부담이 줄어듭니다.',
   '2026-11-01','https://thuvienphapluat.vn','PENDING',NULL,NULL),
  ('ID','규정','BPOM 첨가제 함량 기준 개정 — 시럽제 보존제 상한 인하',
   '시럽제 벤조산나트륨 상한 0.1% → 0.05%',
   '시럽제의 벤조산나트륨(sodium benzoate) 함량은 0.1%를 초과할 수 없다.',
   '시럽제의 벤조산나트륨(sodium benzoate) 함량은 0.05%를 초과할 수 없다. 기허가 품목은 시행일로부터 12개월 내 변경허가를 받아야 한다.',
   '시럽제 보존제(벤조산나트륨) 상한이 절반으로 인하됩니다. 기허가 시럽 품목은 12개월 내 처방 변경 또는 변경허가가 필요합니다.',
   '2027-01-01','https://registrasiobat.pom.go.id','PENDING',NULL,NULL),
  ('US','연방규정','21 CFR Part 314 연차 개정판 반영 (2023 Edition)',
   '2023년판 CFR 발행 — 자구 정리 중심, 실질 요건 변경 없음',
   '21 CFR Part 314 (2022 Edition)',
   '21 CFR Part 314 (2023 Edition)',
   '연차 개정판 발행에 따른 자구 정리로 실질적인 등록 요건 변경은 없습니다. KB 문서 버전만 갱신했습니다.',
   '2023-04-01','https://www.govinfo.gov','REFLECTED', now() - interval '2 days', '$USER_ID');
SQL

echo "✔ 완료 — 대시보드·채팅·보고서·검수 콘솔 모두 시연 데이터 준비됨"
