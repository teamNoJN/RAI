import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/api/client'
import { pollUntil } from '@/api/poll'
import type { AsyncStatus, Report } from '@/types/api'

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

  /** 판정 결과 기반 초안 생성 — 202 접수 후 job 폴링, report_id 반환 */
  async function generate(conversation_id: string, request_id: string): Promise<string> {
    generating.value = true
    try {
      const ack = await api<{ job_id: string }>('POST', '/api/reports', {
        conversation_id,
        request_id,
      })
      const job = await pollUntil(
        () =>
          api<{ status: AsyncStatus; report_id?: string }>(
            'GET',
            `/api/reports/jobs/${ack.job_id}`,
          ),
        (j) => j.status !== 'pending',
      )
      if (job.status !== 'completed' || !job.report_id)
        throw new Error('보고서 생성에 실패했습니다. 다시 시도해주세요.')
      return job.report_id
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
