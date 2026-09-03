// 1단계 MockAiClient 전략 (API 명세서 v0.4) — 계약과 동일한 스키마의 고정/시나리오 응답.
// 실제 백엔드 연동 시 client.ts 의 USE_MOCK 만 끄면 된다.
import type {
  AssessmentResult,
  ChatMessage,
  Drug,
  Report,
  AppNotification,
  RegulationKbDocument,
  Source,
} from '@/types/api'

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms))

const err = (code: string, message: string, status: number) =>
  Object.assign(new Error(message), { code, status })

function iso(offsetSec = 0) {
  return new Date(Date.now() + offsetSec * 1000).toISOString()
}

// ── in-memory DB ──────────────────────────────────────────────
let seq = 100
const id = (p: string) => `${p}${String(seq++).padStart(3, '0')}`

const VN_SOURCE: Source = {
  document_id: 'VN-REG-001',
  title: 'Circular 32/2018/TT-BYT',
  authority: 'Drug Administration of Vietnam',
  version: '2026.01',
  effective_date: '2026-01-01',
  section: '4.2',
  source_url: 'https://dav.gov.vn/',
}

const VN_SOURCE_REVISED: Source = {
  ...VN_SOURCE,
  document_id: 'VN-REG-001-R1',
  title: 'Circular 32/2018/TT-BYT (개정)',
  version: '2026.09',
  effective_date: '2026-09-01',
}

const db = {
  users: [
    {
      user_id: 'U001',
      name: '이서연',
      email: 'ra@pharm.co',
      company_id: 'C001',
      password: 'rai1234',
    },
    {
      user_id: 'U002',
      name: '박준호',
      email: 'pm@pharm.co',
      company_id: 'C001',
      password: 'rai1234',
    },
  ],
  drugs: [
    {
      drug_id: 'D001',
      product_name: '아목시실린 캡슐',
      ingredients: ['Amoxicillin', '첨가제 B'],
      strength: '500mg',
      dosage_form: 'capsule',
      version: 2,
    },
    {
      drug_id: 'D002',
      product_name: '세프디닐 정',
      ingredients: ['Cefdinir'],
      strength: '100mg',
      dosage_form: 'tablet',
      version: 1,
    },
  ] as Drug[],
  countries: [
    { country_id: 'VN', name: '베트남' },
    { country_id: 'ID', name: '인도네시아' },
    { country_id: 'PH', name: '필리핀' },
    { country_id: 'TH', name: '태국' },
    { country_id: 'SG', name: '싱가포르' },
  ],
  // 규제 KB 문서 — 문서가 있는 나라만 채팅 시작 가능 (TH·SG 는 없어서 '나라 추가' 후보)
  kbDocuments: [
    {
      documentId: 'VN-MOH-CIRCULAR-08-2022',
      country: 'VN',
      authority: 'Ministry of Health (Vietnam) / DAV',
      title: 'Circular 08/2022/TT-BYT — Registration of Drugs and Medicinal Ingredients',
      documentVersion: '2022',
      effectiveDate: '2022-10-20',
      sourceUrl: 'https://thuvienphapluat.vn',
      status: 'ACTIVE',
      chunkCount: 203,
    },
    {
      documentId: 'ID-BPOM-24-2017',
      country: 'ID',
      authority: 'BPOM',
      title: 'Peraturan Kepala BPOM Nomor 24 Tahun 2017 — Registrasi Obat',
      documentVersion: '2017',
      effectiveDate: '2017-11-29',
      sourceUrl: 'https://registrasiobat.pom.go.id',
      status: 'ACTIVE',
      chunkCount: 578,
    },
    {
      documentId: 'PH-FDA-AO-2024-0013',
      country: 'PH',
      authority: 'FDA Philippines / DOH',
      title: 'Administrative Order 2024-0013 — Registration of Pharmaceutical Products',
      documentVersion: '2024',
      effectiveDate: '2024-10-05',
      sourceUrl: 'https://www.fda.gov.ph',
      status: 'ACTIVE',
      chunkCount: 100,
    },
  ] as RegulationKbDocument[],
  conversations: [] as {
    conversation_id: string
    drug_id: string
    country_id: string
    created_at: string
    last_message_at: string
  }[],
  messages: {} as Record<string, ChatMessage[]>,
  /** 규제 변경이 반영된 세션 — 재판정 시 개정 기준(RESTRICTED)으로 응답 */
  revisedConversations: new Set<string>(),
  assessments: {} as Record<string, { polls: number; final: AssessmentResult }>,
  reports: [] as Report[],
  reportJobs: {} as Record<string, { polls: number; report_id: string }>,
  notifications: [] as AppNotification[],
  regulations: [] as {
    regulation_id: string
    country_id: string
    regulation_type: string
    title: string
    summary: string
    before: string
    after: string
    ai_summary: string
    effective_date: string
    source_url: string
    review_status: 'PENDING' | 'REFLECTED'
    reflected_at: string | null
    reflected_by: string | null
    created_at: string
  }[],
}

// 새로고침에도 mock 로그인 사용자를 유지 (실백엔드의 세션 유지에 해당)
let currentUserId = localStorage.getItem('rai_mock_uid') ?? 'U001'
function setCurrentUser(uid: string) {
  currentUserId = uid
  localStorage.setItem('rai_mock_uid', uid)
}

function buildAssessment(drugId: string, countryId: string, revised: boolean): AssessmentResult {
  const drug = db.drugs.find((d) => d.drug_id === drugId)!
  const reqId = id('req_')
  const base = {
    request_id: reqId,
    status: 'completed' as const,
    intent: 'EXPORT_ELIGIBILITY_CHECK' as const,
    context: { drug_id: drugId, country_id: countryId },
  }
  // 시나리오 1: 필리핀 = 근거 불충분 → REVIEW_REQUIRED, sources 없음 (가드레일)
  if (countryId === 'PH') {
    return {
      ...base,
      result: {
        summary: '현재 등록된 규제 자료만으로 판단하기 어렵습니다.',
        eligibility: 'REVIEW_REQUIRED',
        ingredient_assessments: drug.ingredients.map((ing) => ({
          ingredient: ing,
          status: 'REVIEW_REQUIRED' as const,
          reason: '해당 국가의 등록 규제 자료가 부족하여 추가 검토가 필요합니다.',
        })),
        requirements: [],
        risks: [],
        recommended_actions: ['규제 담당자 검토 요청'],
      },
      sources: [],
    }
  }
  // 시나리오 2: 규제 개정 후 재판정 → 조건부 → 제한 가능성으로 변화 (3N)
  if (revised) {
    return {
      ...base,
      changed_from: 'CONDITIONAL',
      result: {
        summary:
          '개정 고시(2026.09) 기준으로 첨가제 함량 상한이 낮아져 제한 가능성이 확인되었습니다.',
        eligibility: 'RESTRICTED',
        ingredient_assessments: drug.ingredients.map((ing, i) => ({
          ingredient: ing,
          status:
            i === drug.ingredients.length - 1 && drug.ingredients.length > 1
              ? ('RESTRICTED' as const)
              : ('NO_RESTRICTION' as const),
          reason:
            i === drug.ingredients.length - 1 && drug.ingredients.length > 1
              ? '개정 고시에서 해당 첨가제의 함량 상한이 인하되어 현재 함량이 기준을 초과합니다.'
              : '개정 기준에서도 직접적인 제한이 확인되지 않았습니다.',
        })),
        requirements: ['함량 조정 또는 대체 성분 검토'],
        risks: ['현행 함량으로는 허가 반려 가능성'],
        recommended_actions: [],
      },
      sources: [VN_SOURCE_REVISED],
    }
  }
  // 기본: 조건부
  return {
    ...base,
    result: {
      summary: '일부 성분에 대한 추가 검토가 필요합니다.',
      eligibility: 'CONDITIONAL',
      ingredient_assessments: drug.ingredients.map((ing, i) => ({
        ingredient: ing,
        status:
          i === drug.ingredients.length - 1 && drug.ingredients.length > 1
            ? ('CONDITIONAL' as const)
            : ('NO_RESTRICTION' as const),
        reason:
          i === drug.ingredients.length - 1 && drug.ingredients.length > 1
            ? '해당 함량은 기준치 이내이나, 병용 첨가제에 대한 함량 상한 조건이 존재합니다.'
            : '현재 검색된 규제에서 직접적인 제한이 확인되지 않았습니다.',
      })),
      requirements: ['성분별 함량 증빙 서류'],
      risks: [],
      recommended_actions: [],
    },
    sources: [VN_SOURCE],
  }
}

// ── 시드: 규제 변경 알림이 도착한 기존 세션 (3N 시나리오) ────────
function seed() {
  const cvId = 'CV001'
  db.conversations.push({
    conversation_id: cvId,
    drug_id: 'D001',
    country_id: 'VN',
    created_at: iso(-86400),
    last_message_at: iso(-600),
  })
  db.revisedConversations.add(cvId)
  const past = buildAssessment('D001', 'VN', false)
  db.messages[cvId] = [
    { role: 'user', content: '이 제품 베트남 수출 가능한가?', created_at: iso(-86400) },
    {
      role: 'assistant',
      content: past.result?.summary ?? '',
      intent: 'EXPORT_ELIGIBILITY_CHECK',
      status: 'completed',
      assessment: past,
      created_at: iso(-86390),
    },
    {
      role: 'assistant',
      notice: true,
      status: 'completed',
      created_at: iso(-600),
      content:
        '🔔 규제 변경 알림 — 이 세션의 판정 기준(지식베이스)이 업데이트되었습니다 · MFDS 고시 개정 · 2026.09.01',
      actions: [{ label: '재검토 실행', message: '이 제품 다시 판정해줘' }],
    },
  ]
  db.notifications = [
    {
      notification_id: 'N001',
      type: 'REGULATION_CHANGE',
      title: 'MFDS 고시 개정 · 베트남 — 판정 기준 업데이트',
      drug_id: 'D001',
      country_id: 'VN',
      conversation_id: cvId,
      read: false,
      created_at: iso(-600),
    },
    {
      notification_id: 'N002',
      type: 'REASSESS_NEEDED',
      title: '아목시실린 캡슐 성분 변경 — 판정 이력 재검토 필요',
      drug_id: 'D001',
      read: false,
      created_at: iso(-3600),
    },
    {
      notification_id: 'N003',
      type: 'REASSESS_DONE',
      title: '세프디닐 정 · 필리핀 — 판정 변화 없음',
      drug_id: 'D002',
      country_id: 'PH',
      read: true,
      created_at: iso(-86400),
    },
  ]
  db.regulations = [
    {
      regulation_id: 'REG001',
      country_id: 'VN',
      regulation_type: '고시',
      title: 'MFDS 고시 2026-45호 개정',
      summary: '첨가제 함량 상한 인하',
      before: '제4조 2항 — 첨가제 B의 1일 최대 함량 상한은 1.0mg 으로 한다.',
      after: '제4조 2항 — 첨가제 B의 1일 최대 함량 상한은 0.5mg 으로 한다. (2026.09.01 시행)',
      ai_summary:
        '고시 개정으로 첨가제 B 함량 상한이 1.0mg → 0.5mg 로 인하됨 — 해당 성분 포함 제품 재검토 필요',
      effective_date: '2026-09-01',
      source_url: 'https://www.mfds.go.kr/',
      review_status: 'PENDING',
      reflected_at: null,
      reflected_by: null,
      created_at: iso(-7200),
    },
    {
      regulation_id: 'REG002',
      country_id: 'ID',
      regulation_type: '규정',
      title: 'BPOM Reg. No.11 개정안',
      summary: '표시기재 요건 변경',
      before: '제7조 — 포장 표시에 성분명을 영문으로 기재한다.',
      after: '제7조 — 포장 표시에 성분명을 영문 및 현지어로 병기한다.',
      ai_summary: '표시기재 요건에 현지어 병기가 추가됨 — 라벨 변경 필요 가능성',
      effective_date: '2026-10-01',
      source_url: 'https://www.pom.go.id/',
      review_status: 'PENDING',
      reflected_at: null,
      reflected_by: null,
      created_at: iso(-86400),
    },
    {
      regulation_id: 'REG003',
      country_id: 'VN',
      regulation_type: '고시',
      title: 'DAV Circular 32/2018 부칙 정정',
      summary: '용어 정정 (내용 변경 없음)',
      before: '부칙 — 용어 "첨가물"',
      after: '부칙 — 용어 "첨가제"',
      ai_summary: '용어 정정으로 실질 기준 변화 없음',
      effective_date: '2026-08-20',
      source_url: 'https://dav.gov.vn/',
      review_status: 'REFLECTED',
      reflected_at: iso(-172800),
      reflected_by: '박준호',
      created_at: iso(-259200),
    },
  ]
}
seed()

const REPORT_DRAFT = (
  drug: Drug,
  country: string,
) => `# ${drug.product_name} — ${country} 수출 적합성 검토 (초안)

1. 제품 개요
   ${drug.product_name} (${drug.strength}, ${drug.dosage_form}) · v${drug.version}

2. 성분별 판정 요약
   ${drug.ingredients.join(', ')} — 일부 성분 조건부.

3. 조건부 성분 상세
   병용 첨가제 함량 상한 조건 존재 (Circular 32/2018 §4.2).

4. 결론 및 권고
   조건 충족 시 수출 가능성이 있으며, 제출 전 RA 전문가 검토가 필요함.`

// ── mock router ───────────────────────────────────────────────
export async function mockFetch(method: string, path: string, body?: unknown): Promise<unknown> {
  await delay(250 + Math.random() * 250)
  const b = (body ?? {}) as Record<string, unknown>
  const url = new URL(path, 'http://mock')
  const p = url.pathname
  const q = url.searchParams

  // Auth
  if (method === 'POST' && p === '/api/auth/login') {
    const u = db.users.find((x) => x.email === b.email && x.password === b.password)
    if (!u) throw err('UNAUTHORIZED', '이메일 또는 비밀번호가 일치하지 않습니다', 401)
    setCurrentUser(u.user_id)
    const { password: _pw, ...user } = u
    return { access_token: 'mock-access', refresh_token: 'mock-refresh', user }
  }
  if (method === 'POST' && p === '/api/auth/signup') {
    if (!b.email || !b.password || !b.name || !b.company_name)
      throw err('VALIDATION_ERROR', '요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.', 400)
    if (db.users.some((x) => x.email === b.email))
      throw err('DUPLICATE_EMAIL', '이미 가입된 이메일입니다', 409)
    const isExistingCompany = b.company_name === '한빛제약'
    const u = {
      user_id: id('U'),
      name: String(b.name),
      email: String(b.email),
      company_id: isExistingCompany ? 'C001' : id('C'),
      password: String(b.password),
    }
    db.users.push(u)
    setCurrentUser(u.user_id)
    return {
      user_id: u.user_id,
      email: u.email,
      company_id: u.company_id,
      company_name: b.company_name,
    }
  }
  if (method === 'POST' && p === '/api/auth/refresh') return { access_token: 'mock-access' }
  if (method === 'POST' && p === '/api/auth/logout') return { status: 'ok' }
  if (method === 'GET' && p === '/api/auth/me') {
    const me = db.users.find((u) => u.user_id === currentUserId) ?? db.users[0]!
    const { password: _pw, ...user } = me
    return user
  }

  // Drugs / Countries
  if (method === 'GET' && p === '/api/drugs') {
    const kw = q.get('q')?.trim()
    return kw
      ? db.drugs.filter(
          (d) => d.product_name.includes(kw) || d.ingredients.some((i) => i.includes(kw)),
        )
      : db.drugs
  }
  if (method === 'POST' && p === '/api/drugs') {
    if (!b.product_name || !Array.isArray(b.ingredients) || b.ingredients.length === 0)
      throw err('VALIDATION_ERROR', '요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.', 400)
    const d: Drug = {
      drug_id: id('D'),
      product_name: String(b.product_name),
      ingredients: b.ingredients as string[],
      strength: String(b.strength ?? ''),
      dosage_form: String(b.dosage_form ?? ''),
      version: 1,
    }
    db.drugs.push(d)
    return { drug_id: d.drug_id, product_name: d.product_name, version: 1 }
  }
  if (method === 'GET' && /^\/api\/drugs\/[^/]+\/reassessment-needed$/.test(p)) {
    const drugId = p.split('/')[3] ?? ''
    const countries = [
      ...new Set(db.conversations.filter((c) => c.drug_id === drugId).map((c) => c.country_id)),
    ]
    return countries.length
      ? {
          needed: true,
          prior_countries: countries,
          message: '기존 판정 결과가 존재합니다. 재검토가 필요할 수 있습니다.',
        }
      : { needed: false, prior_countries: [], message: '' }
  }
  if (method === 'PATCH' && /^\/api\/drugs\/[^/]+$/.test(p)) {
    const d = db.drugs.find((x) => x.drug_id === (p.split('/')[3] ?? ''))
    if (!d) throw err('NOT_FOUND', '존재하지 않는 제품입니다.', 404)
    if (Array.isArray(b.ingredients) && b.ingredients.length > 0)
      d.ingredients = b.ingredients as string[]
    if (b.strength !== undefined) d.strength = String(b.strength)
    if (b.dosage_form !== undefined) d.dosage_form = String(b.dosage_form)
    d.version += 1
    const hasPrior = db.conversations.some((c) => c.drug_id === d.drug_id)
    return { drug_id: d.drug_id, version: d.version, has_prior_assessments: hasPrior }
  }
  if (method === 'GET' && /^\/api\/drugs\/[^/]+$/.test(p)) {
    const d = db.drugs.find((x) => x.drug_id === (p.split('/')[3] ?? ''))
    if (!d) throw err('NOT_FOUND', '존재하지 않는 제품입니다.', 404)
    return d
  }
  if (method === 'GET' && p === '/api/countries') return db.countries

  // Conversations
  if (method === 'POST' && p === '/api/conversations') {
    if (!b.country_id) throw err('VALIDATION_ERROR', '국가를 선택해주세요.', 400)
    const drug = db.drugs.find((d) => d.drug_id === b.drug_id)
    if (!drug) throw err('NOT_FOUND', '존재하지 않는 제품입니다.', 404)
    const cv = {
      conversation_id: id('CV'),
      drug_id: drug.drug_id,
      country_id: String(b.country_id),
      created_at: iso(),
      last_message_at: iso(),
    }
    db.conversations.unshift(cv)
    db.messages[cv.conversation_id] = []
    return cv
  }
  if (method === 'GET' && p === '/api/conversations') {
    const limit = Number(q.get('limit') ?? 5)
    return db.conversations.slice(0, limit).map((c) => ({
      conversation_id: c.conversation_id,
      product_name: db.drugs.find((d) => d.drug_id === c.drug_id)?.product_name ?? '',
      country_id: c.country_id,
      last_message_at: c.last_message_at,
    }))
  }
  if (method === 'GET' && /^\/api\/conversations\/[^/]+$/.test(p)) {
    const cv = db.conversations.find((c) => c.conversation_id === p.split('/')[3])
    if (!cv) throw err('NOT_FOUND', '세션을 찾을 수 없습니다.', 404)
    return {
      conversation_id: cv.conversation_id,
      drug_id: cv.drug_id,
      country_id: cv.country_id,
      created_at: cv.created_at,
    }
  }
  if (method === 'PATCH' && /^\/api\/conversations\/[^/]+$/.test(p)) {
    const cv = db.conversations.find((c) => c.conversation_id === p.split('/')[3])
    if (!cv) throw err('NOT_FOUND', '세션을 찾을 수 없습니다.', 404)
    if (b.country_id) cv.country_id = String(b.country_id)
    return { conversation_id: cv.conversation_id, drug_id: cv.drug_id, country_id: cv.country_id }
  }
  if (method === 'GET' && /^\/api\/conversations\/[^/]+\/messages$/.test(p)) {
    return db.messages[p.split('/')[3] ?? ''] ?? []
  }
  if (method === 'POST' && /^\/api\/conversations\/[^/]+\/messages$/.test(p)) {
    const cvId = p.split('/')[3] ?? ''
    const cv = db.conversations.find((c) => c.conversation_id === cvId)
    if (!cv) throw err('NOT_FOUND', '세션을 찾을 수 없습니다.', 404)
    const text = String(b.message ?? '')
    cv.last_message_at = iso()
    ;(db.messages[cvId] ??= []).push({ role: 'user', content: text, created_at: iso() })

    // '실패' 키워드 → failed 시나리오
    const willFail = text.includes('실패')
    const revised = db.revisedConversations.has(cvId)
    const assessment = buildAssessment(cv.drug_id, cv.country_id, revised)
    if (willFail) assessment.status = 'failed'
    db.assessments[assessment.request_id] = { polls: 0, final: assessment }
    return { request_id: assessment.request_id, status: 'pending', intent: assessment.intent }
  }
  if (method === 'GET' && /^\/api\/assessments\/[^/]+$/.test(p)) {
    const rec = db.assessments[p.split('/')[3] ?? '']
    if (!rec) throw err('NOT_FOUND', '판정을 찾을 수 없습니다.', 404)
    rec.polls += 1
    if (rec.polls < 2)
      return { request_id: rec.final.request_id, status: 'pending', intent: rec.final.intent }
    return rec.final
  }
  if (method === 'POST' && /^\/api\/assessments\/[^/]+\/feedback$/.test(p))
    return { status: 'recorded' }

  // Reports
  if (method === 'POST' && p === '/api/reports') {
    const cv = db.conversations.find((c) => c.conversation_id === b.conversation_id)
    if (!cv) throw err('NOT_FOUND', '판정 결과가 없습니다.', 404)
    const drug = db.drugs.find((d) => d.drug_id === cv.drug_id)!
    const country = db.countries.find((c) => c.country_id === cv.country_id)?.name ?? cv.country_id
    const report: Report = {
      report_id: id('R'),
      drug_id: cv.drug_id,
      country_id: cv.country_id,
      status: 'completed',
      version: 1,
      created_at: iso(),
      draft_content: REPORT_DRAFT(drug, country),
      sources: [VN_SOURCE],
      history: [{ version: 1, instruction: '판정 결과로 최초 생성', at: iso() }],
    }
    db.reports.unshift(report)
    const jobId = id('job_')
    db.reportJobs[jobId] = { polls: 0, report_id: report.report_id }
    return { status: 'pending', job_id: jobId }
  }
  if (method === 'GET' && /^\/api\/reports\/jobs\/[^/]+$/.test(p)) {
    const job = db.reportJobs[p.split('/')[4] ?? '']
    if (!job) throw err('NOT_FOUND', '작업을 찾을 수 없습니다.', 404)
    job.polls += 1
    if (job.polls < 2) return { status: 'pending' }
    const r = db.reports.find((x) => x.report_id === job.report_id)!
    return {
      status: 'completed',
      report_id: r.report_id,
      draft_content: r.draft_content,
      sources: r.sources,
      version: r.version,
    }
  }
  if (method === 'PATCH' && /^\/api\/reports\/[^/]+$/.test(p)) {
    const r = db.reports.find((x) => x.report_id === p.split('/')[3])
    if (!r) throw err('NOT_FOUND', '보고서를 찾을 수 없습니다.', 404)
    r.version += 1
    r.draft_content = `${r.draft_content}\n\n[v${r.version} 수정] ${b.instruction} — 해당 항목을 보강했습니다.`
    r.history!.unshift({ version: r.version, instruction: String(b.instruction ?? ''), at: iso() })
    return { report_id: r.report_id, draft_content: r.draft_content, version: r.version }
  }
  if (method === 'GET' && /^\/api\/reports\/[^/]+$/.test(p) && !p.includes('jobs')) {
    const r = db.reports.find((x) => x.report_id === p.split('/')[3])
    if (!r) throw err('NOT_FOUND', '보고서를 찾을 수 없습니다.', 404)
    return r
  }
  if (method === 'GET' && p === '/api/reports') return db.reports

  // 규제 KB 문서 목록/등록 — 이 API 만 ApiResponse 봉투 + camelCase (모놀리스 기존 계약)
  if (method === 'GET' && p === '/api/regulations') {
    return { success: true, data: db.kbDocuments }
  }
  if (method === 'POST' && p === '/api/regulations') {
    const b = body as Record<string, string>
    if (!b?.country || !b?.title) throw err('VALIDATION_ERROR', '입력값이 올바르지 않습니다', 400)
    const doc: RegulationKbDocument = {
      documentId: b.documentId ?? `${b.country}-DOC-${Date.now()}`,
      country: b.country,
      authority: b.authority ?? '',
      title: b.title,
      documentVersion: b.documentVersion ?? null,
      effectiveDate: b.effectiveDate ?? null,
      sourceUrl: b.sourceUrl ?? null,
      status: 'ACTIVE',
      chunkCount: 120,
    }
    db.kbDocuments.push(doc)
    return {
      success: true,
      message: '규제 문서가 등록되었습니다',
      data: { documentId: doc.documentId, chunkCount: doc.chunkCount },
    }
  }

  // Regulations — 검수 콘솔 (screen-06-review-console.md · B2B: 로그인 사용자 전원 접근)
  if (method === 'GET' && p === '/api/regulations/feed') {
    const country = q.get('country')
    const status = q.get('status')
    return db.regulations
      .filter(
        (r) => (!country || r.country_id === country) && (!status || r.review_status === status),
      )
      .map(({ before: _b, after: _a, ai_summary: _s2, reflected_at, reflected_by, ...item }) => ({
        ...item,
        reflected_at,
        reflected_by,
      }))
  }
  if (method === 'GET' && /^\/api\/regulations\/[^/]+$/.test(p)) {
    const r = db.regulations.find((x) => x.regulation_id === (p.split('/')[3] ?? ''))
    if (!r) throw err('NOT_FOUND', '규제 항목을 찾을 수 없습니다.', 404)
    return r
  }
  if (method === 'POST' && /^\/api\/regulations\/[^/]+\/review$/.test(p)) {
    const r = db.regulations.find((x) => x.regulation_id === (p.split('/')[3] ?? ''))
    if (!r) throw err('NOT_FOUND', '규제 항목을 찾을 수 없습니다.', 404)
    if (r.review_status === 'REFLECTED')
      throw err('ALREADY_REFLECTED', '이미 반영된 항목입니다.', 409)
    const me = db.users.find((u) => u.user_id === currentUserId)!
    r.review_status = 'REFLECTED'
    r.reflected_at = iso()
    r.reflected_by = me.name
    // 플로우 연결: 승인 → 영향 세션 개정 기준 적용 + 알림 발행 → 3N 재검토 유도
    const affected = db.conversations.find((c) => c.country_id === r.country_id)
    for (const c of db.conversations)
      if (c.country_id === r.country_id) db.revisedConversations.add(c.conversation_id)
    db.notifications.unshift({
      notification_id: id('N'),
      type: 'REGULATION_CHANGE',
      title: `${r.title} 반영 — 판정 기준 업데이트`,
      country_id: r.country_id,
      conversation_id: affected?.conversation_id,
      read: false,
      created_at: iso(),
    })
    return {
      regulation_id: r.regulation_id,
      review_status: 'REFLECTED',
      reflected_at: r.reflected_at,
      reflected_by: r.reflected_by,
    }
  }

  // Notifications — GET /api/notifications (docs/api-spec/screen-02n-changes.md 제안 스키마)
  if (method === 'GET' && p === '/api/notifications') return db.notifications

  throw err('NOT_FOUND', `mock: ${method} ${p} 미구현`, 404)
}

/** 채팅 화면에서 assistant 메시지를 세션 이력에 반영 (mock 전용 헬퍼) */
export function mockAppendAssistant(cvId: string, msg: ChatMessage) {
  db.messages[cvId]?.push(msg)
}
