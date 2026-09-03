import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, IS_MOCK } from '@/api/client'
import type { AppNotification, Drug, RegulationFeedItem } from '@/types/api'

/**
 * 변경사항 알림 — 전용 백엔드 API(GET /api/notifications)는 협의 중이라,
 * 실모드에서는 실제 시스템 상태에서 파생한다 (더미 아님):
 *  - 검수 피드 PENDING   → 🔔 규제 변경 (검수 필요)
 *  - 검수 피드 REFLECTED → ⚡ 재검토 필요 (KB 반영됨 — 관련 세션 재판정 권장)
 *  - 제품 version > 1 + 판정 이력 → ⚡ 재검토 필요 (성분 변경)
 * 읽음 상태는 알림 API가 없으므로 브라우저(localStorage)에 보관한다.
 */
const READ_KEY = 'rai_read_notifications'

function loadReadSet(): Set<string> {
  try {
    return new Set<string>(JSON.parse(localStorage.getItem(READ_KEY) ?? '[]'))
  } catch {
    return new Set()
  }
}

function persistReadSet(s: Set<string>) {
  try {
    localStorage.setItem(READ_KEY, JSON.stringify([...s]))
  } catch {
    /* storage 불가 환경 — 세션 내 상태만 유지 */
  }
}

export const useNotificationStore = defineStore('notifications', () => {
  const items = ref<AppNotification[]>([])

  async function load() {
    if (IS_MOCK) {
      items.value = await api<AppNotification[]>('GET', '/api/notifications')
      return
    }
    const read = loadReadSet()
    const [feed, drugs] = await Promise.all([
      api<RegulationFeedItem[]>('GET', '/api/regulations/feed'),
      api<Drug[]>('GET', '/api/drugs'),
    ])

    const fromFeed: AppNotification[] = feed.map((r) => {
      const pending = r.review_status === 'PENDING'
      const id = `reg-${r.regulation_id}-${r.review_status}`
      return {
        notification_id: id,
        type: pending ? 'REGULATION_CHANGE' : 'REASSESS_NEEDED',
        title: pending
          ? `${r.country_id} · ${r.title}`
          : `${r.country_id} 규제 반영됨 — 관련 세션 재검토 권장 (${r.title})`,
        country_id: r.country_id,
        read: read.has(id),
        created_at: r.created_at,
      }
    })

    // 성분/버전 변경 후 판정 이력이 있는 제품 → 재검토 필요
    const changed = drugs.filter((d) => d.version > 1)
    const reassess = await Promise.all(
      changed.map(async (d): Promise<AppNotification | null> => {
        const info = await api<{ needed: boolean; prior_countries: string[] }>(
          'GET',
          `/api/drugs/${d.drug_id}/reassessment-needed`,
        )
        if (!info.needed) return null
        const id = `drug-${d.drug_id}-v${d.version}`
        return {
          notification_id: id,
          type: 'REASSESS_NEEDED',
          title: `${d.product_name} v${d.version} 성분 변경 — 판정 이력 재검토 필요 (${info.prior_countries.join(', ')})`,
          drug_id: d.drug_id,
          read: read.has(id),
          created_at: new Date().toISOString(),
        }
      }),
    )

    const fromDrugs = reassess.filter((n): n is AppNotification => n !== null)
    items.value = [...fromFeed, ...fromDrugs].sort((a, b) => (a.created_at < b.created_at ? 1 : -1))
  }

  function markAllRead() {
    const read = loadReadSet()
    items.value.forEach((n) => read.add(n.notification_id))
    persistReadSet(read)
    items.value = items.value.map((n) => ({ ...n, read: true }))
  }

  function markRead(notificationId: string) {
    const read = loadReadSet()
    read.add(notificationId)
    persistReadSet(read)
    const n = items.value.find((x) => x.notification_id === notificationId)
    if (n) n.read = true
  }

  return { items, load, markAllRead, markRead }
})
