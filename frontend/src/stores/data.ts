import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/api/client'
import type {
  AppNotification,
  Drug,
  DrugPatchResponse,
  ReassessmentNeeded,
  Report,
} from '@/types/api'

const POLL_INTERVAL = 2000
const POLL_TIMEOUT = 30000

export const useDrugStore = defineStore('drugs', () => {
  const drugs = ref<Drug[]>([])
  const loading = ref(false)

  async function load(q = '') {
    loading.value = true
    try {
      drugs.value = await api<Drug[]>(
        'GET',
        q ? `/api/drugs?q=${encodeURIComponent(q)}` : '/api/drugs',
      )
    } finally {
      loading.value = false
    }
  }

  async function register(payload: {
    product_name: string
    ingredients: string[]
    strength: string
    dosage_form: string
  }) {
    const res = await api<{ drug_id: string }>('POST', '/api/drugs', payload)
    await load()
    return res.drug_id
  }

  /** 성분/버전 변경 — PATCH 성공 시 version+1, 판정 이력 있으면 재검토 배너 트리거 */
  async function update(
    drugId: string,
    payload: { ingredients?: string[]; strength?: string; dosage_form?: string },
  ): Promise<DrugPatchResponse> {
    const res = await api<DrugPatchResponse>('PATCH', `/api/drugs/${drugId}`, payload)
    await load()
    return res
  }

  async function reassessmentNeeded(drugId: string): Promise<ReassessmentNeeded> {
    return api<ReassessmentNeeded>('GET', `/api/drugs/${drugId}/reassessment-needed`)
  }

  return { drugs, loading, load, register, update, reassessmentNeeded }
})

export const useReportStore = defineStore('reports', () => {
  const list = ref<Report[]>([])
  const current = ref<Report | null>(null)
  const generating = ref(false)

  async function loadList() {
    list.value = await api<Report[]>('GET', '/api/reports')
  }

  async function open(reportId: string) {
    current.value = await api<Report>('GET', `/api/reports/${reportId}`)
  }

  /** 판정 결과 기반 초안 생성 → job 폴링 → report_id 반환 */
  async function generate(conversation_id: string, request_id: string): Promise<string> {
    generating.value = true
    try {
      const ack = await api<{ job_id: string }>('POST', '/api/reports', {
        conversation_id,
        request_id,
      })
      const startedAt = Date.now()
      for (;;) {
        const job = await api<{ status: string; report_id?: string }>(
          'GET',
          `/api/reports/jobs/${ack.job_id}`,
        )
        if (job.status === 'completed' && job.report_id) return job.report_id
        if (job.status === 'failed' || Date.now() - startedAt > POLL_TIMEOUT)
          throw new Error('보고서 생성에 실패했습니다. 다시 시도해주세요.')
        await new Promise((r) => setTimeout(r, POLL_INTERVAL))
      }
    } finally {
      generating.value = false
    }
  }

  /** 대화형 수정 — 수정 1건 = version +1 */
  async function revise(instruction: string) {
    if (!current.value) return
    await api('PATCH', `/api/reports/${current.value.report_id}`, { instruction })
    await open(current.value.report_id)
  }

  return { list, current, generating, loadList, open, generate, revise }
})

export const useNotificationStore = defineStore('notifications', () => {
  const items = ref<AppNotification[]>([])

  async function load() {
    items.value = await api<AppNotification[]>('GET', '/api/notifications')
  }

  function markAllRead() {
    items.value = items.value.map((n) => ({ ...n, read: true }))
  }

  return { items, load, markAllRead }
})
