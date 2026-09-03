<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { useNotificationStore } from '@/stores/notifications'
import { NOTIFICATION_META } from '@/constants/notifications'
import type { NotificationType } from '@/types/api'

const noti = useNotificationStore()
const router = useRouter()
const filter = ref<'ALL' | NotificationType | 'UNREAD'>('ALL')

onMounted(() => noti.load())

const FILTERS: { key: typeof filter.value; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'REGULATION_CHANGE', label: '규제 변경' },
  { key: 'REASSESS_NEEDED', label: '재검토 필요' },
  { key: 'UNREAD', label: '읽지 않음만' },
]

const filtered = computed(() =>
  noti.items.filter((n) => {
    if (filter.value === 'ALL') return true
    if (filter.value === 'UNREAD') return !n.read
    return n.type === filter.value
  }),
)
</script>

<template>
  <AppShell>
    <div class="changes-page">
      <header class="changes-page__head">
        <h1>변경사항 전체</h1>
        <span style="flex: 1" />
        <button
          v-for="f in FILTERS"
          :key="f.key"
          class="chip"
          :class="{ 'chip--primary': filter === f.key }"
          @click="filter = f.key"
        >
          {{ f.label }}
        </button>
      </header>

      <div class="changes-page__list">
        <button
          v-for="n in filtered"
          :key="n.notification_id"
          class="card changes-page__item"
          :class="{ unread: !n.read }"
          @click="
            n.conversation_id
              ? router.push({ name: 'chat', params: { id: n.conversation_id } })
              : router.push({ name: 'dashboard' })
          "
        >
          <span v-if="!n.read" class="dot" style="background: var(--warn)" />
          <div class="changes-page__info">
            <strong
              >{{ NOTIFICATION_META[n.type].icon }} {{ NOTIFICATION_META[n.type].label }}</strong
            >
            <span>{{ n.title }}</span>
          </div>
          <span class="changes-page__date">{{
            new Date(n.created_at).toLocaleString('ko-KR')
          }}</span>
          <span class="chip" :class="{ 'chip--primary': !n.read }">{{
            NOTIFICATION_META[n.type].action
          }}</span>
        </button>
      </div>
      <p class="disclaimer">
        ⚠ 알림 목록 API(GET /api/notifications)는 백엔드 협의 중 — 현재 Mock 데이터
      </p>
    </div>
  </AppShell>
</template>

<style scoped>
.changes-page {
  padding: 26px 32px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.changes-page__head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.changes-page__head h1 {
  margin: 0;
  font-size: 19px;
}
.changes-page__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.changes-page__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
  text-align: left;
  font-size: 12.5px;
}
.changes-page__item.unread {
  background: var(--panel);
}
.changes-page__item:hover {
  border-color: var(--primary);
}
.changes-page__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.changes-page__info strong {
  font-size: 13px;
}
.changes-page__info span {
  color: var(--sub);
}
.changes-page__date {
  color: var(--faint);
  font-size: 11.5px;
}
</style>
