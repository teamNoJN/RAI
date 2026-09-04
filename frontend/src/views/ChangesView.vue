<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import RegulationReviewPanel from '@/components/RegulationReviewPanel.vue'
import { useNotificationStore } from '@/stores/notifications'
import { NOTIFICATION_META } from '@/constants/notifications'
import type { NotificationType } from '@/types/api'

/**
 * 규제 변경 사항 — 알림과 검수를 한 화면에서 처리한다.
 * 레일에서 '규제 검수' 탭을 없애고 여기 [규제 검수] 탭으로 합쳤다 (?tab=review 로 딥링크).
 */
const noti = useNotificationStore()
const route = useRoute()
const router = useRouter()
const filter = ref<'ALL' | NotificationType | 'UNREAD'>('ALL')
const tab = ref<'alerts' | 'review'>(route.query.tab === 'review' ? 'review' : 'alerts')

watch(
  () => route.query.tab,
  (t) => (tab.value = t === 'review' ? 'review' : 'alerts'),
)

function selectTab(next: 'alerts' | 'review') {
  tab.value = next
  router.replace({ name: 'changes', query: next === 'review' ? { tab: 'review' } : {} })
}

const loadError = ref('')
onMounted(() =>
  noti.load().catch(() => {
    loadError.value = '규제 변경 사항을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'
  }),
)

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

const unreadCount = computed(() => noti.items.filter((n) => !n.read).length)

function onItem(n: (typeof noti.items)[number]) {
  noti.markRead(n.notification_id)
  if (n.conversation_id) router.push({ name: 'chat', params: { id: n.conversation_id } })
  else if (n.drug_id && n.type === 'REASSESS_NEEDED') router.push({ name: 'dashboard' })
  else selectTab('review') // 규제 변경 건은 같은 화면의 검수 탭에서 이어서 처리한다
}
</script>

<template>
  <AppShell>
    <div class="changes-page">
      <header class="changes-page__head">
        <h1>규제 변경 사항</h1>
        <nav class="tabs">
          <button
            class="tabs__btn"
            :class="{ 'tabs__btn--on': tab === 'alerts' }"
            @click="selectTab('alerts')"
          >
            변경 알림<span v-if="unreadCount" class="tabs__count">{{ unreadCount }}</span>
          </button>
          <button
            class="tabs__btn"
            :class="{ 'tabs__btn--on': tab === 'review' }"
            @click="selectTab('review')"
          >
            규제 검수
          </button>
        </nav>
        <span style="flex: 1" />
        <template v-if="tab === 'alerts'">
          <button
            v-for="f in FILTERS"
            :key="f.key"
            class="chip"
            :class="{ 'chip--primary': filter === f.key }"
            @click="filter = f.key"
          >
            {{ f.label }}
          </button>
          <button class="chip" @click="noti.markAllRead()">모두 읽음</button>
        </template>
      </header>

      <template v-if="tab === 'alerts'">
        <p v-if="loadError" class="field-error">✕ {{ loadError }}</p>
        <div class="changes-page__list">
          <button
            v-for="n in filtered"
            :key="n.notification_id"
            class="card changes-page__item"
            :class="{ unread: !n.read }"
            @click="onItem(n)"
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
          <p
            v-if="filtered.length === 0 && !loadError"
            class="disclaimer"
            style="padding: 24px 8px"
          >
            표시할 변경 알림이 없어요
          </p>
        </div>
        <p class="disclaimer">
          ⓘ 규제 검수 피드·제품 변경에서 실시간 파생된 알림입니다 (전용 알림 API 는 백엔드 협의 중)
        </p>
      </template>

      <RegulationReviewPanel v-else />
    </div>
  </AppShell>
</template>

<style scoped>
.changes-page {
  padding: 26px 32px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: 1;
}
.changes-page__head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.changes-page__head h1 {
  margin: 0 10px 0 0;
  font-size: 19px;
}
.tabs {
  display: flex;
  gap: 2px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 3px;
}
.tabs__btn {
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: none;
  border-radius: 7px;
  padding: 6px 12px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--sub);
}
.tabs__btn--on {
  background: var(--surface);
  color: var(--ink);
  box-shadow: 0 1px 2px rgba(18, 20, 26, 0.08);
}
.tabs__count {
  background: var(--warn);
  color: #fff;
  border-radius: 9px;
  padding: 0 6px;
  font-size: 10.5px;
  line-height: 16px;
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
.disclaimer {
  margin: 0;
}
</style>
