import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/api/client'
import type { AppNotification } from '@/types/api'

export const useNotificationStore = defineStore('notifications', () => {
  const items = ref<AppNotification[]>([])

  async function load() {
    // ⚠ GET /api/notifications 는 백엔드 협의 중인 신설 제안 (docs/api-spec/screen-02n-changes.md)
    items.value = await api<AppNotification[]>('GET', '/api/notifications')
  }

  function markAllRead() {
    items.value = items.value.map((n) => ({ ...n, read: true }))
  }

  return { items, load, markAllRead }
})
