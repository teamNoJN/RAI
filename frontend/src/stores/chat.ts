import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, isApiError } from '@/api/client'
import { mockAppendAssistant } from '@/api/mock'
import type {
  AssessmentResult,
  ChatMessage,
  Conversation,
  ConversationSummary,
  Country,
} from '@/types/api'

const POLL_INTERVAL = 2000
const POLL_TIMEOUT = 30000

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

  async function openSession(conversation_id: string, drug_id: string, country_id: string) {
    current.value = { conversation_id, drug_id, country_id, created_at: '' }
    messages.value = await api<ChatMessage[]>(
      'GET',
      `/api/conversations/${conversation_id}/messages`,
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
    const pending: ChatMessage = {
      role: 'assistant',
      content: '',
      status: 'pending',
      created_at: new Date().toISOString(),
    }
    messages.value.push(pending)

    try {
      const ack = await api<{ request_id: string; status: string; intent: ChatMessage['intent'] }>(
        'POST',
        `/api/conversations/${cvId}/messages`,
        { message: text },
      )
      pending.intent = ack.intent
      const result = await pollAssessment(ack.request_id)
      if (result.status === 'failed') {
        pending.status = 'failed'
        pending.content = text
      } else {
        pending.status = 'completed'
        pending.assessment = result
        pending.content = result.result?.summary ?? ''
        mockAppendAssistant(cvId, { ...pending })
      }
    } catch (e) {
      pending.status = 'failed'
      pending.content = text
      if (isApiError(e)) pending.intent = pending.intent ?? undefined
    } finally {
      sending.value = false
    }
  }

  async function pollAssessment(requestId: string): Promise<AssessmentResult> {
    const startedAt = Date.now()
    for (;;) {
      const res = await api<AssessmentResult>('GET', `/api/assessments/${requestId}`)
      if (res.status !== 'pending') return res
      if (Date.now() - startedAt > POLL_TIMEOUT) return { ...res, status: 'failed' }
      await new Promise((r) => setTimeout(r, POLL_INTERVAL))
    }
  }

  async function retry(message: ChatMessage) {
    const text = message.content
    const idx = messages.value.indexOf(message)
    if (idx >= 0) messages.value.splice(idx - 1, 2) // user + failed 버블 제거 후 재전송
    await send(text)
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
    sendFeedback,
  }
})
