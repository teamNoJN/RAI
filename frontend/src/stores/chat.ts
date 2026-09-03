import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, IS_MOCK } from '@/api/client'
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
} from '@/types/api'

export const useChatStore = defineStore('chat', () => {
  const current = ref<Conversation | null>(null)
  const messages = ref<ChatMessage[]>([])
  const recent = ref<ConversationSummary[]>([])
  const countries = ref<Country[]>([])
  const sending = ref(false)

  async function loadCountries() {
    countries.value = await api<Country[]>('GET', '/api/countries')
  }

  async function loadRecent() {
    recent.value = await api<ConversationSummary[]>('GET', '/api/conversations?limit=5')
  }

  async function startSession(drug_id: string, country_id: string) {
    current.value = await api<Conversation>('POST', '/api/conversations', { drug_id, country_id })
    messages.value = []
    return current.value
  }

  /** 세션 복원 — 최근 세션 목록에서 컨텍스트(약·국가)를 스스로 해석한다 */
  async function openSession(conversationId: string) {
    if (current.value?.conversation_id === conversationId) return
    if (recent.value.length === 0) await loadRecent()
    const summary = recent.value.find((s) => s.conversation_id === conversationId)
    const drugStore = useDrugStore()
    if (drugStore.drugs.length === 0) await drugStore.load()
    const drug = drugStore.drugs.find((d) => d.product_name === summary?.product_name)
    current.value = {
      conversation_id: conversationId,
      drug_id: drug?.drug_id ?? current.value?.drug_id ?? '',
      country_id: summary?.country_id ?? current.value?.country_id ?? '',
      created_at: '',
    }
    messages.value = await api<ChatMessage[]>(
      'GET',
      `/api/conversations/${conversationId}/messages`,
    )
  }

  async function changeCountry(country_id: string) {
    if (!current.value) return
    const res = await api<Conversation>(
      'PATCH',
      `/api/conversations/${current.value.conversation_id}`,
      { country_id },
    )
    current.value = { ...current.value, country_id: res.country_id }
  }

  /** 핵심: 메시지 전송 → pending 버블 → 폴링 → 결과 카드 교체 */
  async function send(text: string) {
    if (!current.value || sending.value || !text.trim()) return
    sending.value = true
    const cvId = current.value.conversation_id
    messages.value.push({ role: 'user', content: text, created_at: new Date().toISOString() })
    messages.value.push({
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
      sending.value = false
    }
  }

  async function retry(message: ChatMessage) {
    const text = message.content
    const idx = messages.value.indexOf(message)
    if (idx >= 0) messages.value.splice(idx - 1, 2) // user + failed 버블 제거 후 재전송
    await send(text)
  }

  /** 보고서 초안 생성 오케스트레이션 — REPORT_GENERATE 대화 흐름을 스토어가 소유 */
  async function requestReport(userText = '보고서 만들어줘'): Promise<void> {
    if (!current.value) return
    const lastAssessment = [...messages.value].reverse().find((m) => m.assessment)?.assessment
    if (!lastAssessment) {
      messages.value.push({
        role: 'assistant',
        content: '보고서를 만들려면 먼저 수출 가능 여부 판정을 실행해주세요.',
        status: 'completed',
        notice: true,
        created_at: new Date().toISOString(),
      })
      return
    }
    messages.value.push({ role: 'user', content: userText, created_at: new Date().toISOString() })
    messages.value.push({
      role: 'assistant',
      content: '',
      intent: 'REPORT_GENERATE',
      status: 'pending',
      created_at: new Date().toISOString(),
    })
    const pending = messages.value[messages.value.length - 1]!
    try {
      const reportStore = useReportStore()
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
    sending,
    loadCountries,
    loadRecent,
    startSession,
    openSession,
    changeCountry,
    send,
    retry,
    requestReport,
    sendFeedback,
  }
})
