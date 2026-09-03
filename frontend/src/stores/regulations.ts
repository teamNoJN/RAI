import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/api/client'
import type { RegulationDetail, RegulationFeedItem } from '@/types/api'

/** 규제 변경 검수 콘솔 (admin 전용) — docs/api-spec/screen-06-review-console.md */
export const useRegulationStore = defineStore('regulations', () => {
  const feed = ref<RegulationFeedItem[]>([])
  const detail = ref<RegulationDetail | null>(null)
  const approving = ref(false)

  async function loadFeed() {
    feed.value = await api<RegulationFeedItem[]>('GET', '/api/regulations/feed')
  }

  async function open(regulationId: string) {
    detail.value = await api<RegulationDetail>('GET', `/api/regulations/${regulationId}`)
  }

  /** 명시적 사람 액션 — 승인 시각·주체는 서버가 감사 기록으로 남긴다 */
  async function approve() {
    if (!detail.value || approving.value) return
    approving.value = true
    try {
      await api('POST', `/api/regulations/${detail.value.regulation_id}/review`, { approved: true })
      await Promise.all([loadFeed(), open(detail.value.regulation_id)])
    } finally {
      approving.value = false
    }
  }

  return { feed, detail, approving, loadFeed, open, approve }
})
