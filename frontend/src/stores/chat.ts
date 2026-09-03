import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api, apiUpload, IS_MOCK } from '@/api/client'
import { mockAppendAssistant } from '@/api/mock'
import { pollUntil, PollTimeoutError } from '@/api/poll'
import { useDrugStore } from '@/stores/drugs'
import { useReportStore } from '@/stores/reports'
import type {
  AssessmentResult,
  ChatMessage,
  Conversation,
  ConversationSummary,
  Country,
  RegulationKbDocument,
} from '@/types/api'

/** 메시지 리스트 안정 키 — index 키는 retry 의 splice 와 조합 시 상태가 옆 메시지로 전이된다 */
let uidSeq = 0
const nextUid = () => ++uidSeq

export const useChatStore = defineStore('chat', () => {
  const current = ref<Conversation | null>(null)
  const messages = ref<ChatMessage[]>([])
  const recent = ref<ConversationSummary[]>([])
  const countries = ref<Country[]>([])
  const sending = ref(false)
  /** 세션 전환 세대 — 이전 세션에서 돌던 폴링의 늦은 응답이 새 세션 상태를 건드리지 못하게 한다 */
  let sessionEpoch = 0

  const kbDocuments = ref<RegulationKbDocument[]>([])

  async function loadCountries() {
    const [list, docs] = await Promise.allSettled([
      api<Country[]>('GET', '/api/countries'),
      // 이 API 만 ApiResponse 봉투 + camelCase (모놀리스 규제 KB 기존 계약)
      api<{ data: RegulationKbDocument[] }>('GET', '/api/regulations'),
    ])
    if (list.status === 'rejected') throw list.reason
    countries.value = list.value
    // KB 목록 실패가 국가 목록까지 비우지 않게 분리 — 확인 불가 시엔 가드대로 채팅 시작이 막힌다(안전)
    kbDocuments.value = docs.status === 'fulfilled' ? (docs.value.data ?? []) : []
  }

  /** 규제 문서가 KB 에 있는 나라만 채팅 시작 가능 — 근거 없는 판정을 만들지 않기 위한 가드 */
  const docCountryIds = computed(
    () => new Set(kbDocuments.value.filter((d) => d.status === 'ACTIVE').map((d) => d.country)),
  )
  const availableCountries = computed(() =>
    countries.value.filter((c) => docCountryIds.value.has(c.country_id)),
  )
  /** '나라 추가' 후보 — 마스터에는 있지만 아직 규제 문서가 없는 나라 */
  const candidateCountries = computed(() =>
    countries.value.filter((c) => !docCountryIds.value.has(c.country_id)),
  )

  /** 나라 추가 = 그 나라의 공식 규제 문서 등록. 성공하면 드롭다운에 나타난다. */
  async function addCountryWithDocument(input: {
    country_id: string
    file: File
    title: string
    authority: string
    effective_date?: string
    source_url?: string
  }) {
    const form = new FormData()
    form.append('file', input.file)
    form.append('documentId', `${input.country_id}-DOC-${Date.now()}`)
    form.append('country', input.country_id)
    form.append('authority', input.authority)
    form.append('title', input.title)
    if (input.effective_date) form.append('effectiveDate', input.effective_date)
    if (input.source_url) form.append('sourceUrl', input.source_url)
    await apiUpload('/api/regulations', form)
    await loadCountries()
  }

  async function loadRecent() {
    recent.value = await api<ConversationSummary[]>('GET', '/api/conversations?limit=5')
  }

  async function fetchMessages(conversationId: string) {
    return (await api<ChatMessage[]>('GET', `/api/conversations/${conversationId}/messages`)).map(
      (m) => ({ ...m, uid: nextUid() }),
    )
  }

  async function startSession(drug_id: string, country_id: string) {
    const cv = await api<Conversation>('POST', '/api/conversations', { drug_id, country_id })
    sessionEpoch++
    sending.value = false
    current.value = cv
    // 서버는 같은 약·국가면 기존 세션을 그대로 돌려준다. 비우면 지난 대화가 사라져 보이므로
    // 항상 불러온다 — 새 세션이면 빈 배열이라 결과는 같다.
    // ChatView 는 startSession 뒤 openSession 을 부르지만 같은 세션이면 조기 반환하므로
    // 여기서 채워두지 않으면 이어보기가 성립하지 않는다.
    messages.value = await fetchMessages(cv.conversation_id)
    // 레일 '최근 대화'에 즉시 반영 (화면 재마운트에 의존하지 않는다)
    loadRecent().catch(() => {})
    return cv
  }

  /** 세션 복원 — 단건 조회로 컨텍스트(약·국가)를 얻는다. 최근 5건 밖 세션도 정확하게. */
  async function openSession(conversationId: string) {
    if (current.value?.conversation_id === conversationId) return
    sessionEpoch++
    sending.value = false // 이전 세션의 폴링이 잡고 있던 전송 잠금 해제
    let cv: Conversation | null = null
    try {
      cv = await api<Conversation>('GET', `/api/conversations/${conversationId}`)
    } catch {
      // 단건 조회 미구현 환경 폴백 — 최근 목록에서 유추 (이름 매칭의 한계 있음)
      if (recent.value.length === 0) await loadRecent()
      const summary = recent.value.find((s) => s.conversation_id === conversationId)
      const drugStore = useDrugStore()
      if (drugStore.drugs.length === 0) await drugStore.load()
      const drug = drugStore.drugs.find((d) => d.product_name === summary?.product_name)
      cv = {
        conversation_id: conversationId,
        drug_id: drug?.drug_id ?? '',
        country_id: summary?.country_id ?? '',
        created_at: '',
      }
    }
    current.value = cv
    messages.value = await fetchMessages(conversationId)
  }

  /** 핵심: 메시지 전송 → pending 버블 → 폴링 → 결과 카드 교체 */
  async function send(text: string) {
    if (!current.value || sending.value || !text.trim()) return
    sending.value = true
    const cvId = current.value.conversation_id
    const epoch = sessionEpoch
    messages.value.push({
      uid: nextUid(),
      role: 'user',
      content: text,
      created_at: new Date().toISOString(),
    })
    messages.value.push({
      uid: nextUid(),
      role: 'assistant',
      content: '',
      status: 'pending',
      created_at: new Date().toISOString(),
    })
    // push 한 원본이 아니라 배열이 감싼 reactive 프록시를 잡아야 이후 변경이 화면에 반영된다
    const pending = messages.value[messages.value.length - 1]!

    try {
      const ack = await api<{ request_id: string; status: string; intent: ChatMessage['intent'] }>(
        'POST',
        `/api/conversations/${cvId}/messages`,
        { message: text },
      )
      pending.intent = ack.intent
      const result = await pollUntil(
        () => api<AssessmentResult>('GET', `/api/assessments/${ack.request_id}`),
        (r) => r.status !== 'pending',
      )
      if (result.status === 'failed') {
        pending.status = 'failed'
        pending.content = text
      } else {
        pending.status = 'completed'
        pending.assessment = result
        pending.content = result.result?.summary ?? ''
        // 실백엔드는 서버가 세션 이력에 저장한다 — mock 모드에서만 로컬 이력 반영
        if (IS_MOCK) mockAppendAssistant(cvId, { ...pending })
      }
    } catch (e) {
      pending.status = 'failed'
      pending.content = text
      if (!(e instanceof PollTimeoutError)) console.error(e)
    } finally {
      // 폴링 중 세션이 바뀌었으면 sending 은 openSession 이 이미 리셋했다 — 늦게 덮어쓰지 않는다
      if (epoch === sessionEpoch) sending.value = false
      // last_message_at 갱신 → 레일 정렬·상대 시간 최신화
      loadRecent().catch(() => {})
    }
  }

  async function retry(message: ChatMessage) {
    const text = message.content
    const idx = messages.value.indexOf(message)
    if (idx >= 0) messages.value.splice(idx - 1, 2) // user + 실패 버블 제거 후 재실행
    // 보고서 생성 실패는 일반 채팅이 아니라 보고서 오케스트레이션으로 재시도해야 한다
    if (message.intent === 'REPORT_GENERATE') await requestReport(text)
    else await send(text)
  }

  /** 보고서 초안 생성 오케스트레이션 — REPORT_GENERATE 대화 흐름을 스토어가 소유 */
  async function requestReport(userText = '보고서 만들어줘'): Promise<void> {
    const reportStore = useReportStore()
    if (!current.value || reportStore.generating) return // 더블클릭 중복 생성 방지
    const lastAssessment = [...messages.value].reverse().find((m) => m.assessment)?.assessment
    if (!lastAssessment) {
      messages.value.push({
        uid: nextUid(),
        role: 'assistant',
        content: '보고서를 만들려면 먼저 수출 가능 여부 판정을 실행해주세요.',
        status: 'completed',
        notice: true,
        created_at: new Date().toISOString(),
      })
      return
    }
    messages.value.push({
      uid: nextUid(),
      role: 'user',
      content: userText,
      created_at: new Date().toISOString(),
    })
    messages.value.push({
      uid: nextUid(),
      role: 'assistant',
      content: '',
      intent: 'REPORT_GENERATE',
      status: 'pending',
      created_at: new Date().toISOString(),
    })
    const pending = messages.value[messages.value.length - 1]!
    try {
      const reportId = await reportStore.generate(
        current.value.conversation_id,
        lastAssessment.request_id,
      )
      pending.status = 'completed'
      pending.content = '초안을 만들었어요. 보고서 작업 뷰에서 대화로 수정할 수 있습니다.'
      pending.report_id = reportId
    } catch {
      pending.status = 'failed'
      pending.content = userText
    }
  }

  async function sendFeedback(requestId: string, rating: 'helpful' | 'needs_revision') {
    await api('POST', `/api/assessments/${requestId}/feedback`, { rating })
  }

  return {
    current,
    messages,
    recent,
    countries,
    kbDocuments,
    availableCountries,
    candidateCountries,
    sending,
    loadCountries,
    addCountryWithDocument,
    loadRecent,
    startSession,
    openSession,
    send,
    retry,
    requestReport,
    sendFeedback,
  }
})
