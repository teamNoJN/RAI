// 1단계 MockAiClient 전략 (API 명세서 v0.4) — 계약과 동일한 스키마의 고정/시나리오 응답.
// 실제 백엔드 연동 시 client.ts 의 USE_MOCK 만 끄면 된다.
import type {
  AssessmentResult,
  ChatMessage,
  Drug,
  Eligibility,
  IngredientAssessment,
  IngredientStatus,
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
  // 기본 등록 제품 12종 — 항생제·해열진통·만성질환·바이오·점안제까지 폭넓게 (시연용)
  drugs: [
    {
      drug_id: 'D001',
      product_name: '아목시실린 캡슐 500mg',
      ingredients: ['Amoxicillin', '젤라틴(캡슐기제)', '스테아르산마그네슘'],
      strength: '500mg',
      dosage_form: 'capsule',
      version: 2,
    },
    {
      drug_id: 'D002',
      product_name: '세프디닐 정 100mg',
      ingredients: ['Cefdinir', '유당수화물'],
      strength: '100mg',
      dosage_form: 'tablet',
      version: 1,
    },
    {
      drug_id: 'D003',
      product_name: '아세트아미노펜 시럽',
      ingredients: ['Acetaminophen', '벤조산나트륨', '수크랄로스'],
      strength: '160mg/5mL',
      dosage_form: 'syrup',
      version: 1,
    },
    {
      drug_id: 'D004',
      product_name: '이부프로펜 정 400mg',
      ingredients: ['Ibuprofen', '히프로멜로스'],
      strength: '400mg',
      dosage_form: 'tablet',
      version: 1,
    },
    {
      drug_id: 'D005',
      product_name: '로수바스타틴 정 10mg',
      ingredients: ['Rosuvastatin Calcium', '유당수화물'],
      strength: '10mg',
      dosage_form: 'tablet',
      version: 1,
    },
    {
      drug_id: 'D006',
      product_name: '메트포르민 서방정 500mg',
      ingredients: ['Metformin HCl', '히프로멜로스'],
      strength: '500mg',
      dosage_form: 'tablet(ER)',
      version: 1,
    },
    {
      drug_id: 'D007',
      product_name: '에스오메프라졸 캡슐 20mg',
      ingredients: ['Esomeprazole Magnesium', '수크로스구체'],
      strength: '20mg',
      dosage_form: 'capsule',
      version: 1,
    },
    {
      drug_id: 'D008',
      product_name: '레보세티리진 정 5mg',
      ingredients: ['Levocetirizine', '타르트라진(황색4호)'],
      strength: '5mg',
      dosage_form: 'tablet',
      version: 1,
    },
    {
      drug_id: 'D009',
      product_name: '몬테루카스트 츄어블정 5mg',
      ingredients: ['Montelukast Sodium', '아스파탐'],
      strength: '5mg',
      dosage_form: 'chewable',
      version: 1,
    },
    {
      drug_id: 'D010',
      product_name: '클래리트로마이신 정 250mg',
      ingredients: ['Clarithromycin', '탈크'],
      strength: '250mg',
      dosage_form: 'tablet',
      version: 1,
    },
    {
      drug_id: 'D011',
      product_name: '인슐린 글라진 주사액 100IU/mL',
      ingredients: ['Insulin Glargine', '메타크레졸', '글리세롤'],
      strength: '100IU/mL',
      dosage_form: 'injection',
      version: 1,
    },
    {
      drug_id: 'D012',
      product_name: '히알루론산 점안액 0.1%',
      ingredients: ['Sodium Hyaluronate', '벤잘코늄염화물'],
      strength: '0.1%',
      dosage_form: 'eye drops',
      version: 1,
    },
  ] as Drug[],
  // 국가 마스터 30개국 — 규제 문서를 등록할 수 있는 후보국 전체
  countries: [
    { country_id: 'VN', name: '베트남' },
    { country_id: 'ID', name: '인도네시아' },
    { country_id: 'PH', name: '필리핀' },
    { country_id: 'TH', name: '태국' },
    { country_id: 'SG', name: '싱가포르' },
    { country_id: 'MY', name: '말레이시아' },
    { country_id: 'KH', name: '캄보디아' },
    { country_id: 'MM', name: '미얀마' },
    { country_id: 'LA', name: '라오스' },
    { country_id: 'BN', name: '브루나이' },
    { country_id: 'JP', name: '일본' },
    { country_id: 'CN', name: '중국' },
    { country_id: 'TW', name: '대만' },
    { country_id: 'IN', name: '인도' },
    { country_id: 'US', name: '미국' },
    { country_id: 'CA', name: '캐나다' },
    { country_id: 'BR', name: '브라질' },
    { country_id: 'MX', name: '멕시코' },
    { country_id: 'EU', name: '유럽연합' },
    { country_id: 'GB', name: '영국' },
    { country_id: 'CH', name: '스위스' },
    { country_id: 'TR', name: '튀르키예' },
    { country_id: 'RU', name: '러시아' },
    { country_id: 'AE', name: '아랍에미리트' },
    { country_id: 'SA', name: '사우디아라비아' },
    { country_id: 'EG', name: '이집트' },
    { country_id: 'ZA', name: '남아프리카공화국' },
    { country_id: 'AU', name: '호주' },
    { country_id: 'NZ', name: '뉴질랜드' },
    { country_id: 'KR', name: '대한민국' },
  ],
  // 규제 KB 문서 — 문서가 있는 나라만 채팅 시작 가능. 나머지는 '나라 추가' 후보로 남는다.
  kbDocuments: [] as RegulationKbDocument[],
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

// ── 규제 KB 문서 시드 ──────────────────────────────────────────
/** 근거는 있으나 적재량이 적어 성분 판단까지는 못 가는 나라 — REVIEW_REQUIRED 시나리오 */
const SPARSE_KB = new Set(['MM', 'LA', 'KH'])

const KB_SEED: [string, string, string, string, string, string, number][] = [
  // [country, authority, title, version, effectiveDate, sourceUrl, chunkCount]
  [
    'VN',
    'Ministry of Health (Vietnam) / DAV',
    'Circular 08/2022/TT-BYT — Registration of Drugs and Medicinal Ingredients',
    '2022',
    '2022-10-20',
    'https://thuvienphapluat.vn',
    203,
  ],
  [
    'ID',
    'BPOM',
    'Peraturan Kepala BPOM No.24/2017 — Registrasi Obat',
    '2017',
    '2017-11-29',
    'https://registrasiobat.pom.go.id',
    578,
  ],
  [
    'PH',
    'FDA Philippines / DOH',
    'Administrative Order 2024-0013 — Registration of Pharmaceutical Products',
    '2024',
    '2024-10-05',
    'https://www.fda.gov.ph',
    164,
  ],
  [
    'TH',
    'Thai FDA',
    'Drug Act B.E. 2510 (Amendment 2019) — Registration Requirements',
    '2019',
    '2019-04-16',
    'https://www.fda.moph.go.th',
    141,
  ],
  [
    'SG',
    'HSA Singapore',
    'Health Products (Therapeutic Products) Regulations 2016',
    '2016',
    '2016-11-01',
    'https://www.hsa.gov.sg',
    128,
  ],
  [
    'MY',
    'NPRA Malaysia',
    'Drug Registration Guidance Document (DRGD) 3rd Edition',
    '2023',
    '2023-07-01',
    'https://npra.gov.my',
    312,
  ],
  [
    'MM',
    'Myanmar FDA',
    'Guideline for Registration of Imported Medicines (Summary)',
    '2021',
    '2021-02-10',
    'https://www.fdamyanmar.gov.mm',
    18,
  ],
  [
    'LA',
    'Lao FDD',
    'Regulation on Drug Registration No.1067 (Summary)',
    '2020',
    '2020-06-15',
    'https://www.fdd.gov.la',
    12,
  ],
  [
    'JP',
    'PMDA / MHLW',
    '医薬品医療機器等法 — 承認申請の取扱い (2024)',
    '2024',
    '2024-04-01',
    'https://www.pmda.go.jp',
    421,
  ],
  [
    'CN',
    'NMPA',
    '药品注册管理办法 (国家市场监督管理总局令 第27号)',
    '2020',
    '2020-07-01',
    'https://www.nmpa.gov.cn',
    388,
  ],
  [
    'US',
    'US FDA',
    '21 CFR Part 314 — Applications for FDA Approval to Market a New Drug',
    '2023',
    '2023-04-01',
    'https://www.govinfo.gov',
    640,
  ],
  [
    'EU',
    'EMA',
    'Directive 2001/83/EC — Community Code Relating to Medicinal Products',
    '2022',
    '2022-01-28',
    'https://www.ema.europa.eu',
    705,
  ],
  [
    'GB',
    'MHRA',
    'Human Medicines Regulations 2012 (as amended 2024)',
    '2024',
    '2024-01-01',
    'https://www.gov.uk/mhra',
    254,
  ],
  [
    'IN',
    'CDSCO',
    'New Drugs and Clinical Trials Rules, 2019',
    '2019',
    '2019-03-19',
    'https://cdsco.gov.in',
    197,
  ],
  [
    'BR',
    'ANVISA',
    'RDC No. 753/2022 — Registro de Medicamentos Genéricos',
    '2022',
    '2022-09-28',
    'https://www.gov.br/anvisa',
    233,
  ],
  [
    'AE',
    'MOHAP UAE',
    'Drug Registration Guideline (2023 Edition)',
    '2023',
    '2023-05-01',
    'https://mohap.gov.ae',
    96,
  ],
  [
    'SA',
    'SFDA',
    'Saudi Guidelines for Drug Registration v4.0',
    '2022',
    '2022-03-01',
    'https://www.sfda.gov.sa',
    174,
  ],
  [
    'AU',
    'TGA',
    'Therapeutic Goods Regulations 1990 — Prescription Medicines',
    '2023',
    '2023-07-01',
    'https://www.tga.gov.au',
    188,
  ],
]

function seedKb() {
  db.kbDocuments = KB_SEED.map(
    ([country, authority, title, documentVersion, effectiveDate, sourceUrl, chunkCount]) => ({
      documentId: `${country}-REG-${documentVersion}`,
      country,
      authority,
      title,
      documentVersion,
      effectiveDate,
      sourceUrl,
      status: 'ACTIVE' as const,
      chunkCount,
    }),
  )
}

function kbDoc(countryId: string): RegulationKbDocument | undefined {
  return db.kbDocuments.find((d) => d.country === countryId && d.status === 'ACTIVE')
}

/** 판정 근거 — 실제 적재된 KB 문서에서 만든다(문서명을 지어내지 않는다) */
function sourceOf(countryId: string, section: string): Source[] {
  const doc = kbDoc(countryId)
  if (!doc) return []
  return [
    {
      document_id: doc.documentId,
      title: doc.title,
      authority: doc.authority,
      version: doc.documentVersion ?? '',
      effective_date: doc.effectiveDate ?? '',
      section,
      source_url: doc.sourceUrl ?? '',
    },
  ]
}

// ── 판정 규칙 ─────────────────────────────────────────────────
/**
 * 성분 × 국가 규칙표. 시연에서 적합/조건부/부적합/검토필요가 모두 나오도록 구성했다.
 * 실제 구현에서는 LLM + RAG 가 대신할 자리다 — 여기서는 결정론적으로 같은 답을 준다.
 */
type IngredientRule = {
  ingredient: string
  countries: string[] | '*'
  status: IngredientStatus
  reason: string
  section: string
  requirement?: string
  risk?: string
  action?: string
}

const INGREDIENT_RULES: IngredientRule[] = [
  {
    ingredient: '타르트라진(황색4호)',
    countries: ['ID', 'MY', 'SA', 'AE', 'EG'],
    status: 'RESTRICTED',
    reason: '해당 국가는 경구용 의약품에 타르트라진(황색4호) 사용을 금지하고 있습니다.',
    section: '착색료',
    risk: '현행 처방으로는 허가 반려가 예상됩니다 — 착색료 대체가 선행되어야 합니다.',
    action: '착색료를 산화철(적색)로 대체하고 처방 변경 후 재검토하세요.',
  },
  {
    ingredient: '벤조산나트륨',
    countries: ['ID', 'TH', 'VN'],
    status: 'CONDITIONAL',
    reason: '시럽제 보존제 함량 상한(0.1% 이하) 준수 여부를 증빙해야 합니다.',
    section: '보존제',
    requirement: '보존제 함량 시험성적서(COA) 및 안정성 자료',
  },
  {
    ingredient: '아스파탐',
    countries: '*',
    status: 'CONDITIONAL',
    reason: '페닐케톤뇨증 환자 경고 문구를 라벨에 표시해야 합니다.',
    section: '표시기재',
    requirement: '현지어 경고문구가 반영된 라벨 시안',
  },
  {
    ingredient: '메타크레졸',
    countries: '*',
    status: 'CONDITIONAL',
    reason: '주사제 보존제로 사용 시 함량 근거와 콜드체인 유지 자료가 요구됩니다.',
    section: '주사제',
    requirement: '콜드체인 수송 검증 자료(2~8℃)',
  },
  {
    ingredient: '벤잘코늄염화물',
    countries: ['JP', 'EU', 'GB', 'AU'],
    status: 'CONDITIONAL',
    reason: '점안제 보존제 사용은 장기 투여 안전성 자료 제출을 조건으로 허용됩니다.',
    section: '점안제',
    requirement: '보존제 장기 투여 안전성 자료',
  },
  {
    ingredient: 'Insulin Glargine',
    countries: '*',
    status: 'CONDITIONAL',
    reason: '바이오의약품으로 분류되어 화학합성의약품과 다른 별도 심사 트랙을 따릅니다.',
    section: '바이오의약품',
    requirement: '품질동등성 비교 자료 및 GMP 실사 일정',
  },
  {
    ingredient: 'Clarithromycin',
    countries: ['CN', 'RU', 'IN'],
    status: 'CONDITIONAL',
    reason: '항생제 내성 관리 대상 성분으로 처방 제한 및 별도 사용 근거가 필요합니다.',
    section: '항생제',
    requirement: '항생제 사용 관리 계획서',
  },
  {
    ingredient: 'Amoxicillin',
    countries: ['VN', 'PH', 'TH'],
    status: 'CONDITIONAL',
    reason: '항생제 처방 관리 품목으로 등록 시 유통 경로 신고가 함께 요구됩니다.',
    section: '4.2',
    requirement: '유통 경로 및 도매상 지정 신고서',
  },
  {
    ingredient: 'Cefdinir',
    countries: ['VN', 'ID'],
    status: 'CONDITIONAL',
    reason: '세팔로스포린계 항생제는 생물학적 동등성 자료 제출이 조건입니다.',
    section: '4.3',
    requirement: '생물학적 동등성 시험 자료',
  },
]

const STATUS_RANK: Record<IngredientStatus, number> = {
  NO_RESTRICTION: 0,
  CONDITIONAL: 1,
  REVIEW_REQUIRED: 2,
  RESTRICTED: 3,
}

const NO_RESTRICTION_REASON = '현재 검색된 규제에서 직접적인 제한이 확인되지 않았습니다.'

function ruleFor(ingredient: string, countryId: string): IngredientRule | undefined {
  return INGREDIENT_RULES.find(
    (r) => r.ingredient === ingredient && (r.countries === '*' || r.countries.includes(countryId)),
  )
}

function buildAssessment(drugId: string, countryId: string, revised: boolean): AssessmentResult {
  const drug = db.drugs.find((d) => d.drug_id === drugId)!
  const base = {
    request_id: id('req_'),
    status: 'completed' as const,
    intent: 'EXPORT_ELIGIBILITY_CHECK' as const,
    context: { drug_id: drugId, country_id: countryId },
  }
  const country = db.countries.find((c) => c.country_id === countryId)?.name ?? countryId

  // 시나리오 1 — 근거 불충분: KB 문서가 없거나 적재량이 적은 나라 (가드레일, sources 없음)
  if (!kbDoc(countryId) || SPARSE_KB.has(countryId)) {
    return {
      ...base,
      result: {
        summary: `${country}에 등록된 규제 자료만으로는 판단하기 어렵습니다. 원문 추가 등록이 필요합니다.`,
        eligibility: 'REVIEW_REQUIRED',
        ingredient_assessments: drug.ingredients.map((ing) => ({
          ingredient: ing,
          status: 'REVIEW_REQUIRED' as const,
          reason: '해당 국가의 등록 규제 자료가 부족하여 추가 검토가 필요합니다.',
        })),
        requirements: [],
        risks: [],
        recommended_actions: ['해당 국가 규제 원문 추가 등록', '규제 담당자 검토 요청'],
      },
      sources: [],
    }
  }

  // 시나리오 2 — 규제 개정 반영 후 재판정: 첨가제 상한 인하로 판정이 뒤집힌다 (3N)
  if (revised) {
    const target = drug.ingredients.at(-1)!
    return {
      ...base,
      changed_from: 'CONDITIONAL',
      result: {
        summary: `개정 고시 기준으로 ${target}의 함량 상한이 낮아져 제한 가능성이 확인되었습니다.`,
        eligibility: 'RESTRICTED',
        ingredient_assessments: drug.ingredients.map((ing) => ({
          ingredient: ing,
          status: ing === target ? ('RESTRICTED' as const) : ('NO_RESTRICTION' as const),
          reason:
            ing === target
              ? '개정 고시에서 해당 첨가제의 함량 상한이 인하되어 현재 함량이 기준을 초과합니다.'
              : '개정 기준에서도 직접적인 제한이 확인되지 않았습니다.',
        })),
        requirements: ['함량 조정 또는 대체 성분 검토', '변경허가 신청 계획서'],
        risks: ['현행 함량으로는 허가 반려 가능성'],
        recommended_actions: ['처방 변경 후 재판정', '규제 담당자 검토 요청'],
      },
      sources: sourceOf(countryId, '개정 부칙'),
    }
  }

  // 기본 — 성분 × 국가 규칙으로 판정을 합성한다
  const rows = drug.ingredients.map((ing) => {
    const rule = ruleFor(ing, countryId)
    const row: IngredientAssessment = {
      ingredient: ing,
      status: rule?.status ?? 'NO_RESTRICTION',
      reason: rule?.reason ?? NO_RESTRICTION_REASON,
    }
    return { row, rule }
  })

  const worst = rows.reduce<IngredientStatus>(
    (acc, { row }) => (STATUS_RANK[row.status] > STATUS_RANK[acc] ? row.status : acc),
    'NO_RESTRICTION',
  )
  const eligibility: Eligibility = worst === 'NO_RESTRICTION' ? 'POSSIBLE' : (worst as Eligibility)

  const hit = rows.filter((r) => r.rule)
  const requirements = [...new Set(hit.map((r) => r.rule!.requirement).filter(Boolean))] as string[]
  const risks = [...new Set(hit.map((r) => r.rule!.risk).filter(Boolean))] as string[]
  const actions = [...new Set(hit.map((r) => r.rule!.action).filter(Boolean))] as string[]
  const section = hit[0]?.rule?.section ?? '등록 요건'

  const SUMMARY: Record<Eligibility, string> = {
    POSSIBLE: `${country} 규제를 검토한 결과 직접적인 제한은 확인되지 않았습니다. 표준 등록 절차로 진행 가능합니다.`,
    CONDITIONAL: `${country} 수출은 가능하나 ${hit.length}개 성분에 대해 조건 충족이 필요합니다.`,
    RESTRICTED: `${country}에서 사용이 금지된 성분이 포함되어 현행 처방으로는 수출이 어렵습니다.`,
    REVIEW_REQUIRED: `${country} 규제 자료만으로 판단하기 어렵습니다.`,
  }

  return {
    ...base,
    result: {
      summary: SUMMARY[eligibility],
      eligibility,
      ingredient_assessments: rows.map((r) => r.row),
      requirements,
      risks,
      recommended_actions:
        actions.length > 0
          ? actions
          : eligibility === 'POSSIBLE'
            ? ['규제 담당자 검토 후 제출']
            : [],
    },
    sources: sourceOf(countryId, section),
  }
}

// ── 시드 ─────────────────────────────────────────────────────
function seedConversation(
  cvId: string,
  drugId: string,
  countryId: string,
  ageSec: number,
  question: string,
) {
  db.conversations.push({
    conversation_id: cvId,
    drug_id: drugId,
    country_id: countryId,
    created_at: iso(-ageSec - 60),
    last_message_at: iso(-ageSec),
  })
  const assessment = buildAssessment(drugId, countryId, false)
  db.messages[cvId] = [
    { role: 'user', content: question, created_at: iso(-ageSec - 60) },
    {
      role: 'assistant',
      content: assessment.result?.summary ?? '',
      intent: 'EXPORT_ELIGIBILITY_CHECK',
      status: 'completed',
      assessment,
      created_at: iso(-ageSec),
    },
  ]
  return assessment
}

/** 시연 시나리오 — 판정 4종(적합·조건부·부적합·검토필요)이 최근 대화에 모두 보이게 시드한다 */
function seed() {
  seedKb()

  // 3N — 규제 변경 알림이 도착한 세션 (조건부 → 재판정 시 부적합으로 뒤집힘)
  const cv1 = 'CV001'
  seedConversation(cv1, 'D001', 'VN', 600, '이 제품 베트남 수출 가능한가?')
  db.revisedConversations.add(cv1)
  db.messages[cv1]!.push({
    role: 'assistant',
    notice: true,
    status: 'completed',
    created_at: iso(-540),
    content:
      '🔔 규제 변경 알림 — 이 세션의 판정 기준(지식베이스)이 업데이트되었습니다 · DAV 고시 개정 · 2026.09.01',
    actions: [{ label: '재검토 실행', message: '이 제품 다시 판정해줘' }],
  })

  // 부적합 — 타르트라진 함유 제품의 인도네시아 수출
  seedConversation('CV002', 'D008', 'ID', 4200, '레보세티리진 인도네시아 수출 가능해?')
  // 적합 — 제한 성분 없는 제품의 미국 수출
  seedConversation('CV003', 'D004', 'US', 9000, '이부프로펜 미국 등록 요건 알려줘')
  // 조건부 — 바이오의약품의 일본 수출
  seedConversation('CV004', 'D011', 'JP', 90000, '인슐린 글라진 일본 수출 검토해줘')
  // 검토 필요 — 근거 문서 적재량이 적은 나라
  seedConversation('CV005', 'D003', 'MM', 172800, '아세트아미노펜 시럽 미얀마 가능한가요?')

  db.notifications = [
    {
      notification_id: 'N001',
      type: 'REGULATION_CHANGE',
      title: 'DAV 고시 개정 · 베트남 — 판정 기준 업데이트',
      drug_id: 'D001',
      country_id: 'VN',
      conversation_id: cv1,
      read: false,
      created_at: iso(-600),
    },
    {
      notification_id: 'N002',
      type: 'REASSESS_NEEDED',
      title: '아목시실린 캡슐 500mg v2 성분 변경 — 판정 이력 재검토 필요 (VN)',
      drug_id: 'D001',
      read: false,
      created_at: iso(-3600),
    },
    {
      notification_id: 'N003',
      type: 'REGULATION_CHANGE',
      title: 'BPOM 시럽제 보존제 상한 인하 · 인도네시아 — 검수 대기',
      country_id: 'ID',
      read: false,
      created_at: iso(-7200),
    },
    {
      notification_id: 'N004',
      type: 'REASSESS_NEEDED',
      title: 'SFDA 착색료 기준 개정 · 사우디아라비아 — 타르트라진 함유 품목 재검토',
      country_id: 'SA',
      read: false,
      created_at: iso(-28800),
    },
    {
      notification_id: 'N005',
      type: 'REASSESS_DONE',
      title: '이부프로펜 정 400mg · 미국 — 재판정 결과 변화 없음 (적합 유지)',
      drug_id: 'D004',
      country_id: 'US',
      conversation_id: 'CV003',
      read: true,
      created_at: iso(-90000),
    },
    {
      notification_id: 'N006',
      type: 'REGULATION_CHANGE',
      title: 'EMA Directive 2001/83/EC 부속서 개정 · 유럽연합 — 반영 완료',
      country_id: 'EU',
      read: true,
      created_at: iso(-172800),
    },
  ]

  db.regulations = [
    {
      regulation_id: 'REG001',
      country_id: 'VN',
      regulation_type: '고시',
      title: 'DAV 고시 2026-45호 — 첨가제 함량 상한 인하',
      summary: '스테아르산마그네슘 1일 최대 함량 상한 인하',
      before: '제4조 2항 — 첨가제의 1일 최대 함량 상한은 1.0mg 으로 한다.',
      after: '제4조 2항 — 첨가제의 1일 최대 함량 상한은 0.5mg 으로 한다. (2026.09.01 시행)',
      ai_summary:
        '고시 개정으로 첨가제 함량 상한이 1.0mg → 0.5mg 로 인하됨 — 해당 성분 포함 제품 재검토 필요',
      effective_date: '2026-09-01',
      source_url: 'https://dav.gov.vn/',
      review_status: 'PENDING',
      reflected_at: null,
      reflected_by: null,
      created_at: iso(-7200),
    },
    {
      regulation_id: 'REG002',
      country_id: 'ID',
      regulation_type: '규정',
      title: 'BPOM 시럽제 보존제 상한 인하',
      summary: '벤조산나트륨 상한 0.1% → 0.05%',
      before: '시럽제의 벤조산나트륨 함량은 0.1%를 초과할 수 없다.',
      after:
        '시럽제의 벤조산나트륨 함량은 0.05%를 초과할 수 없다. 기허가 품목은 시행일로부터 12개월 내 변경허가를 받아야 한다.',
      ai_summary:
        '시럽제 보존제 상한이 절반으로 인하됩니다. 기허가 시럽 품목은 12개월 내 처방 변경 또는 변경허가가 필요합니다.',
      effective_date: '2027-01-01',
      source_url: 'https://registrasiobat.pom.go.id',
      review_status: 'PENDING',
      reflected_at: null,
      reflected_by: null,
      created_at: iso(-10800),
    },
    {
      regulation_id: 'REG003',
      country_id: 'SA',
      regulation_type: '지침',
      title: 'SFDA 착색료 사용 기준 개정 — 타르트라진 금지 확대',
      summary: '경구용 고형제 타르트라진 사용 전면 금지',
      before: '타르트라진은 1일 섭취허용량 이내에서 사용할 수 있다.',
      after: '경구용 의약품에 타르트라진(E102) 사용을 금지한다. (경과조치 6개월)',
      ai_summary:
        '타르트라진이 전면 금지되어 해당 착색료를 쓰는 품목은 6개월 내 처방 변경이 필요합니다.',
      effective_date: '2026-12-01',
      source_url: 'https://www.sfda.gov.sa',
      review_status: 'PENDING',
      reflected_at: null,
      reflected_by: null,
      created_at: iso(-28800),
    },
    {
      regulation_id: 'REG004',
      country_id: 'JP',
      regulation_type: '통지',
      title: 'PMDA 바이오의약품 심사 트랙 변경 통지',
      summary: '품질동등성 자료 제출 시점 앞당김',
      before: '품질동등성 비교 자료는 심사 착수 후 제출할 수 있다.',
      after: '품질동등성 비교 자료는 신청 시 함께 제출하여야 한다.',
      ai_summary:
        '바이오의약품 신청 시 품질동등성 자료를 처음부터 제출해야 해 준비 기간이 앞당겨집니다.',
      effective_date: '2026-11-15',
      source_url: 'https://www.pmda.go.jp',
      review_status: 'PENDING',
      reflected_at: null,
      reflected_by: null,
      created_at: iso(-54000),
    },
    {
      regulation_id: 'REG005',
      country_id: 'US',
      regulation_type: '연방규정',
      title: '21 CFR Part 314 연차 개정판 반영 (2023 Edition)',
      summary: '자구 정리 중심 — 실질 요건 변경 없음',
      before: '21 CFR Part 314 (2022 Edition)',
      after: '21 CFR Part 314 (2023 Edition)',
      ai_summary: '연차 개정판 발행에 따른 자구 정리로 실질적인 등록 요건 변경은 없습니다.',
      effective_date: '2026-04-01',
      source_url: 'https://www.govinfo.gov',
      review_status: 'REFLECTED',
      reflected_at: iso(-172800),
      reflected_by: '박준호',
      created_at: iso(-259200),
    },
    {
      regulation_id: 'REG006',
      country_id: 'EU',
      regulation_type: '지침',
      title: 'EMA Directive 2001/83/EC 부속서 I 개정',
      summary: '표시기재에 현지어 병기 의무화',
      before: '포장 표시에 성분명을 영문으로 기재한다.',
      after: '포장 표시에 성분명을 영문 및 판매국 공용어로 병기한다.',
      ai_summary: '표시기재 요건에 현지어 병기가 추가되어 라벨 변경이 필요할 수 있습니다.',
      effective_date: '2026-10-01',
      source_url: 'https://www.ema.europa.eu',
      review_status: 'REFLECTED',
      reflected_at: iso(-190000),
      reflected_by: '이서연',
      created_at: iso(-280000),
    },
    {
      regulation_id: 'REG007',
      country_id: 'TH',
      regulation_type: '고시',
      title: 'Thai FDA 등록 갱신 서류 간소화',
      summary: 'CPP 원본 → 사본 제출 허용, 심사 기한 단축',
      before: '갱신 신청 시 WHO 양식 CPP 원본 및 공증 사본을 모두 제출한다. 심사 기한 3개월.',
      after: '갱신 신청 시 CPP 사본 제출로 갈음할 수 있다. 심사 기한은 2개월로 단축한다.',
      ai_summary:
        'CPP 원본 제출 의무가 완화되고 갱신 심사 기한이 3개월 → 2개월로 단축됩니다. 갱신 부담이 줄어듭니다.',
      effective_date: '2026-11-01',
      source_url: 'https://www.fda.moph.go.th',
      review_status: 'REFLECTED',
      reflected_at: iso(-300000),
      reflected_by: '박준호',
      created_at: iso(-400000),
    },
    {
      regulation_id: 'REG008',
      country_id: 'VN',
      regulation_type: '고시',
      title: 'DAV Circular 08/2022 부칙 용어 정정',
      summary: '용어 정정 (내용 변경 없음)',
      before: '부칙 — 용어 "첨가물"',
      after: '부칙 — 용어 "첨가제"',
      ai_summary: '용어 정정으로 실질 기준 변화는 없습니다.',
      effective_date: '2026-08-20',
      source_url: 'https://dav.gov.vn/',
      review_status: 'REFLECTED',
      reflected_at: iso(-345600),
      reflected_by: '박준호',
      created_at: iso(-432000),
    },
  ]
}

const REPORT_DRAFT = (drug: Drug, country: string, assessment?: AssessmentResult) => {
  const r = assessment?.result
  const verdict = r ? ELIGIBILITY_LABEL[r.eligibility] : '검토 중'
  return `# ${drug.product_name} — ${country} 수출 적합성 검토 (초안)

1. 제품 개요
   ${drug.product_name} (${drug.strength}, ${drug.dosage_form}) · v${drug.version}

2. 판정 요약
   종합 판정: ${verdict}
   ${r?.summary ?? ''}

3. 성분별 판정
${(r?.ingredient_assessments ?? []).map((i) => `   - ${i.ingredient} — ${INGREDIENT_LABEL[i.status]}: ${i.reason}`).join('\n')}

4. 요구 조건
${(r?.requirements ?? []).length ? (r?.requirements ?? []).map((q) => `   - ${q}`).join('\n') : '   - 별도 조건 없음'}

5. 결론 및 권고
   ${(r?.recommended_actions ?? []).join(' / ') || '제출 전 RA 전문가 최종 검토가 필요함.'}`
}

const ELIGIBILITY_LABEL: Record<Eligibility, string> = {
  POSSIBLE: '적합 (수출 가능)',
  CONDITIONAL: '조건부 적합',
  RESTRICTED: '부적합 (제한)',
  REVIEW_REQUIRED: '검토 필요',
}

const INGREDIENT_LABEL: Record<IngredientStatus, string> = {
  NO_RESTRICTION: '제한 없음',
  CONDITIONAL: '조건부',
  RESTRICTED: '제한',
  REVIEW_REQUIRED: '검토 필요',
}

/** 보관함이 비어 보이지 않게 보고서 2건을 시드한다 */
function seedReports() {
  const make = (reportId: string, cvId: string, ageSec: number, version: number) => {
    const cv = db.conversations.find((c) => c.conversation_id === cvId)!
    const drug = db.drugs.find((d) => d.drug_id === cv.drug_id)!
    const country = db.countries.find((c) => c.country_id === cv.country_id)?.name ?? cv.country_id
    const assessment = db.messages[cvId]?.find((m) => m.assessment)?.assessment
    db.reports.push({
      report_id: reportId,
      drug_id: cv.drug_id,
      country_id: cv.country_id,
      status: 'completed',
      version,
      created_at: iso(-ageSec),
      draft_content:
        REPORT_DRAFT(drug, country, assessment) +
        (version > 1 ? `\n\n[v2 수정] 요구 조건 항목을 더 구체적으로 정리했습니다.` : ''),
      sources: assessment?.sources ?? [],
      history:
        version > 1
          ? [
              {
                version: 2,
                instruction: '요구 조건 항목을 더 구체적으로 정리해줘',
                at: iso(-ageSec),
              },
              { version: 1, instruction: '판정 결과로 최초 생성', at: iso(-ageSec - 3600) },
            ]
          : [{ version: 1, instruction: '판정 결과로 최초 생성', at: iso(-ageSec) }],
    })
  }
  make('R001', 'CV002', 4000, 2)
  make('R002', 'CV003', 88000, 1)
}

// ── 영속화 — 새로고침·화면 이동에도 대화 이력이 남게 한다 ───────
const PERSIST_KEY = 'rai_mock_db_v1'

type Snapshot = {
  seq: number
  drugs: Drug[]
  kbDocuments: RegulationKbDocument[]
  conversations: (typeof db)['conversations']
  messages: Record<string, ChatMessage[]>
  revisedConversations: string[]
  reports: Report[]
  notifications: AppNotification[]
  regulations: (typeof db)['regulations']
}

function persist() {
  try {
    const snapshot: Snapshot = {
      seq,
      drugs: db.drugs,
      kbDocuments: db.kbDocuments,
      conversations: db.conversations,
      messages: db.messages,
      revisedConversations: [...db.revisedConversations],
      reports: db.reports,
      notifications: db.notifications,
      regulations: db.regulations,
    }
    localStorage.setItem(PERSIST_KEY, JSON.stringify(snapshot))
  } catch {
    /* storage 불가 환경 — 세션 내 메모리 상태만 유지 */
  }
}

function restore(): boolean {
  try {
    const raw = localStorage.getItem(PERSIST_KEY)
    if (!raw) return false
    const s = JSON.parse(raw) as Snapshot
    if (!s?.conversations || !s?.messages) return false
    seq = s.seq ?? seq
    db.drugs = s.drugs
    db.kbDocuments = s.kbDocuments
    db.conversations = s.conversations
    db.messages = s.messages
    db.revisedConversations = new Set(s.revisedConversations ?? [])
    db.reports = s.reports
    db.notifications = s.notifications
    db.regulations = s.regulations
    return true
  } catch {
    return false
  }
}

/** 시연 데이터 초기화 — 콘솔에서 `__raiResetMock()` 로 언제든 처음 상태로 되돌린다 */
export function resetMockDb() {
  localStorage.removeItem(PERSIST_KEY)
  window.location.reload()
}

seed()
if (!restore()) {
  seedReports()
  persist()
}
;(window as unknown as Record<string, unknown>).__raiResetMock = resetMockDb

// ── mock router ───────────────────────────────────────────────
export async function mockFetch(method: string, path: string, body?: unknown): Promise<unknown> {
  const result = await route(method, path, body)
  if (method !== 'GET') persist()
  return result
}

async function route(method: string, path: string, body?: unknown): Promise<unknown> {
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
    // 같은 약·국가면 새로 만들지 않고 기존 세션을 돌려준다 (백엔드 ConversationService 와 동일).
    // 새로 만들면 '변경' 으로 같은 조합을 고를 때마다 지난 대화가 사라져 보인다.
    const existing = db.conversations.find(
      (c) => c.drug_id === drug.drug_id && c.country_id === b.country_id,
    )
    if (existing) return existing
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
    return [...db.conversations]
      .sort((a, c) => (a.last_message_at < c.last_message_at ? 1 : -1))
      .slice(0, limit)
      .map((c) => ({
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
    const assessment =
      db.assessments[String(b.request_id ?? '')]?.final ??
      [...(db.messages[cv.conversation_id] ?? [])].reverse().find((m) => m.assessment)?.assessment
    const report: Report = {
      report_id: id('R'),
      drug_id: cv.drug_id,
      country_id: cv.country_id,
      status: 'completed',
      version: 1,
      created_at: iso(),
      draft_content: REPORT_DRAFT(drug, country, assessment),
      sources: assessment?.sources ?? [],
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
    const f = body as Record<string, string>
    if (!f?.country || !f?.title) throw err('VALIDATION_ERROR', '입력값이 올바르지 않습니다', 400)
    const doc: RegulationKbDocument = {
      documentId: f.documentId ?? `${f.country}-DOC-${Date.now()}`,
      country: f.country,
      authority: f.authority ?? '',
      title: f.title,
      documentVersion: f.documentVersion ?? null,
      effectiveDate: f.effectiveDate ?? null,
      sourceUrl: f.sourceUrl ?? null,
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

/** 채팅 화면에서 만들어진 메시지를 세션 이력에 반영 (mock 전용 헬퍼) */
export function mockAppendAssistant(cvId: string, ...msgs: ChatMessage[]) {
  const list = (db.messages[cvId] ??= [])
  for (const m of msgs) list.push(m)
  const cv = db.conversations.find((c) => c.conversation_id === cvId)
  if (cv) cv.last_message_at = iso()
  persist()
}
