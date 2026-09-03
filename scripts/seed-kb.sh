#!/usr/bin/env bash
# =============================================================
# RAI — 규제 KB 시드 (공식 문서 다운로드 → 게이트웨이로 적재)
#
# 사용법:
#   1) docker compose --profile full up -d  (스택 기동)
#   2) ./scripts/seed-kb.sh <이메일> <비밀번호> [게이트웨이 URL]
#      예: ./scripts/seed-kb.sh pm@pharm.co password123 http://localhost:18080
#
# 문서가 있는 나라만 채팅 드롭다운에 나타난다 (FE 가드).
# 전부 공개된 공식 출처의 원문/영역본 PDF 이며, 텍스트 추출 가능 확인됨.
# 주의: 필리핀 AO 2024-0013 정식본(fda.gov.ph 2024/09)은 스캔본이라
#       텍스트 추출이 안 되어, 텍스트 레이어가 있는 드래프트본을 사용한다.
# =============================================================
set -euo pipefail

EMAIL="${1:?사용법: seed-kb.sh <이메일> <비밀번호> [게이트웨이]}"
PASSWORD="${2:?비밀번호를 입력해주세요}"
BASE="${3:-http://localhost:18080}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "▸ 로그인…"
TOKEN=$(curl -sf -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | python3 -c "import json,sys;print(json.load(sys.stdin)['access_token'])")

dl() { echo "▸ 다운로드: $2"; curl -sfL -A "Mozilla/5.0" -o "$TMP/$1" "$2"; }

ingest() { # file docId country authority title version pubDate effDate srcUrl
  echo "▸ 적재: $2 ($3)"
  curl -sf -X POST "$BASE/api/regulations" -H "Authorization: Bearer $TOKEN" \
    -F "file=@$TMP/$1" -F "documentId=$2" -F "country=$3" -F "authority=$4" \
    -F "title=$5" -F "documentVersion=$6" -F "publishedDate=$7" -F "effectiveDate=$8" \
    -F "sourceUrl=$9"
  echo
}

dl vn.pdf "https://clinregs.niaid.nih.gov/sites/default/files/documents/vietnam/DrugRgstrtn_GoogleTranslation.pdf"
ingest vn.pdf VN-MOH-CIRCULAR-08-2022 VN "Ministry of Health (Vietnam) / DAV" \
  "Circular 08/2022/TT-BYT — Registration of Circulation of Drugs and Medicinal Ingredients" \
  2022 2022-09-05 2022-10-20 \
  "https://thuvienphapluat.vn/van-ban/EN/The-thao-Y-te/Circular-08-2022-TT-BYT-marketing-authorization-of-drugs-and-medicinal-materials/535436/tieng-anh.aspx"

dl id.pdf "https://registrasiobat.pom.go.id/files/regulations/PERATURAN%20KEPALA%20BPOM%20NOMOR%2024%20TAHUN%202017%20TENTANG%20KRITERIA%20DAN%20TATA%20LAKSANA%20REGISTRASI%20OBAT.pdf"
ingest id.pdf ID-BPOM-24-2017 ID "BPOM (Badan Pengawas Obat dan Makanan)" \
  "Peraturan Kepala BPOM Nomor 24 Tahun 2017 — Kriteria dan Tata Laksana Registrasi Obat" \
  2017 2017-11-29 2017-11-29 \
  "https://registrasiobat.pom.go.id/files/regulations/PERATURAN%20KEPALA%20BPOM%20NOMOR%2024%20TAHUN%202017%20TENTANG%20KRITERIA%20DAN%20TATA%20LAKSANA%20REGISTRASI%20OBAT.pdf"

dl ph.pdf "https://www.fda.gov.ph/wp-content/uploads/2024/05/Draft-AO-Rules-and-Regulations-on-the-Issuance-of-Authorization-for-Registration-Applications-of-Pharmaceutical-Products-and-Active-Pharmaceutical-Ingredients-for-Human-Use.pdf"
ingest ph.pdf PH-FDA-AO-2024-0013 PH "FDA Philippines / DOH" \
  "Administrative Order 2024-0013 — Registration of Pharmaceutical Products and APIs (text ver.)" \
  2024 2024-09-20 2024-10-05 \
  "https://www.fda.gov.ph/wp-content/uploads/2024/09/Administrative-Order-No.-2024-0013.pdf"

dl us.pdf "https://www.govinfo.gov/content/pkg/CFR-2023-title21-vol5/pdf/CFR-2023-title21-vol5-part314.pdf"
ingest us.pdf US-FDA-21CFR-314 US "U.S. FDA" \
  "21 CFR Part 314 — Applications for FDA Approval to Market a New Drug" \
  2023 2023-04-01 2023-04-01 \
  "https://www.govinfo.gov/content/pkg/CFR-2023-title21-vol5/pdf/CFR-2023-title21-vol5-part314.pdf"

echo "✔ 완료 — GET $BASE/api/regulations 로 확인하세요"
