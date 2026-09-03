// API v0.4 계약 타입 — docs/api-spec/ 참고

export interface ApiError {
  error: { code: string; message: string }
}

export interface User {
  user_id: string
  name: string
  email: string
  company_id: string
}

export interface LoginResponse {
  access_token: string
  refresh_token: string
  user: User
}

export interface Drug {
  drug_id: string
  product_name: string
  ingredients: string[]
  strength: string
  dosage_form: string
  version: number
}

export interface Country {
  country_id: string
  name: string
}

export interface Conversation {
  conversation_id: string
  drug_id: string
  country_id: string
  created_at: string
}

export interface ConversationSummary {
  conversation_id: string
  product_name: string
  country_id: string
  /** 메시지가 없는 새 세션은 null (백엔드 lastMessageAt) */
  last_message_at: string | null
}

export type AsyncStatus = 'pending' | 'completed' | 'failed'
export type Intent = 'EXPORT_ELIGIBILITY_CHECK' | 'REPORT_GENERATE' | 'REPORT_REVISE'
export type Eligibility = 'POSSIBLE' | 'CONDITIONAL' | 'REVIEW_REQUIRED' | 'RESTRICTED'
export type IngredientStatus = 'NO_RESTRICTION' | 'CONDITIONAL' | 'REVIEW_REQUIRED' | 'RESTRICTED'

export interface IngredientAssessment {
  ingredient: string
  status: IngredientStatus
  reason: string
}

export interface Source {
  document_id: string
  title: string
  authority: string
  version: string
  effective_date: string
  section: string
  source_url: string
}

export interface AssessmentResult {
  request_id: string
  status: AsyncStatus
  intent: Intent
  context: { drug_id: string; country_id: string }
  /** FE 확장: 재판정으로 판정이 달라진 경우 이전 판정 */
  changed_from?: Eligibility
  result?: {
    summary: string
    eligibility: Eligibility
    ingredient_assessments: IngredientAssessment[]
    requirements: string[]
    risks: string[]
    recommended_actions: string[]
  }
  sources?: Source[]
}

export interface ChatMessage {
  /** FE 확장: 리스트 렌더링 안정 키 (index 키 금지 — retry splice 와 조합 시 상태 전이) */
  uid?: number
  role: 'user' | 'assistant'
  content: string
  intent?: Intent
  status?: AsyncStatus
  created_at: string
  /** FE 확장: 판정 결과가 붙은 메시지 */
  assessment?: AssessmentResult
  /** FE 확장: 생성된 보고서로 이동 */
  report_id?: string
  /** FE 확장: 시스템 알림 메시지 표시용 */
  notice?: boolean
  /** FE 확장: 알림 메시지에 붙는 액션 칩 (클릭 시 채팅으로 전송) */
  actions?: { label: string; message: string }[]
}

export interface DrugPatchResponse {
  drug_id: string
  version: number
  has_prior_assessments: boolean
}

export interface ReassessmentNeeded {
  needed: boolean
  prior_countries: string[]
  message: string
}

export interface Report {
  report_id: string
  drug_id: string
  country_id: string
  status: AsyncStatus
  version: number
  created_at: string
  draft_content?: string
  sources?: Source[]
  /** FE 확장: 버전 타임라인 — 수정 1건 = version +1 */
  history?: { version: number; instruction: string; at: string }[]
}

export type NotificationType = 'REGULATION_CHANGE' | 'REASSESS_NEEDED' | 'REASSESS_DONE'

export interface AppNotification {
  notification_id: string
  type: NotificationType
  title: string
  drug_id?: string
  country_id?: string
  conversation_id?: string
  read: boolean
  created_at: string
}

export type ReviewStatus = 'PENDING' | 'REFLECTED'

export interface RegulationFeedItem {
  regulation_id: string
  country_id: string
  regulation_type: string
  title: string
  summary: string
  effective_date: string
  source_url: string
  review_status: ReviewStatus
  created_at: string
}

/** 상세는 목록(FeedItem)과 필드 구성이 다르다 — 백엔드 Detail 엔 summary/created_at 이 없음 */
export interface RegulationDetail {
  regulation_id: string
  country_id: string
  regulation_type: string
  title: string
  before: string
  after: string
  ai_summary: string
  effective_date: string
  source_url: string
  review_status: ReviewStatus
  reflected_at: string | null
  reflected_by: string | null
}

/**
 * 규제 KB 문서 (GET/POST /api/regulations — 모놀리스 운영 API).
 * 주의: 이 API 만 ApiResponse 봉투({success, data}) + camelCase 를 쓴다 (기존 계약).
 */
export interface RegulationKbDocument {
  documentId: string
  country: string
  authority: string
  title: string
  documentVersion: string | null
  effectiveDate: string | null
  sourceUrl: string | null
  status: 'ACTIVE' | 'REVISED'
  chunkCount: number
}
