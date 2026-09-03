<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useDrugStore } from '@/stores/drugs'

const auth = useAuthStore()
const chat = useChatStore()
const drugStore = useDrugStore()
const router = useRouter()

onMounted(() => {
  chat.loadRecent().catch(() => {}) // 레일은 본문과 독립 — 실패해도 화면을 막지 않는다
  window.addEventListener('keydown', onEsc)
})
onUnmounted(() => window.removeEventListener('keydown', onEsc))

function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') showNewChat.value = false
}

function openRecent(cvId: string) {
  router.push({ name: 'chat', params: { id: cvId } })
}

/** 마지막 메시지 시각 → 상대 시간 (실데이터 기반) */
function timeAgo(iso: string | null): string {
  if (!iso) return '새 세션'
  const diff = Date.now() - new Date(iso).getTime()
  if (Number.isNaN(diff)) return ''
  const min = Math.floor(diff / 60000)
  if (min < 1) return '방금 전'
  if (min < 60) return `${min}분 전`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour}시간 전`
  const day = Math.floor(hour / 24)
  if (day < 7) return `${day}일 전`
  return new Date(iso).toLocaleDateString('ko-KR', { month: 'numeric', day: 'numeric' })
}

function onLogout() {
  auth.logout()
  router.push({ name: 'login' })
}

// ＋ 새 대화 — 어느 화면에서든 약·국가 골라 바로 세션 시작
const showNewChat = ref(false)
const newChat = reactive({ drug_id: '', country_id: '', error: '', starting: false })

async function openNewChat() {
  showNewChat.value = true
  newChat.error = ''
  if (drugStore.drugs.length === 0) drugStore.load()
  if (chat.countries.length === 0) chat.loadCountries()
}

async function startNewChat() {
  if (!newChat.drug_id || !newChat.country_id) {
    newChat.error = '약과 국가를 선택해주세요'
    return
  }
  newChat.starting = true
  try {
    const cv = await chat.startSession(newChat.drug_id, newChat.country_id)
    showNewChat.value = false
    newChat.drug_id = ''
    newChat.country_id = ''
    router.push({ name: 'chat', params: { id: cv.conversation_id } })
  } catch {
    newChat.error = '세션 생성에 실패했습니다. 다시 시도해주세요.'
  } finally {
    newChat.starting = false
  }
}
</script>

<template>
  <div class="shell">
    <aside class="rail">
      <div class="rail__top">
        <RouterLink class="rail__logo" :to="{ name: 'dashboard' }" title="제품 대시보드로">
          <img class="rail__mark" src="@/assets/logo.svg" alt="RAI" />
          <strong>RAI</strong>
        </RouterLink>
        <button class="btn btn--block" @click="openNewChat">＋ 새 대화</button>
        <nav class="rail__recent" v-if="chat.recent.length">
          <p class="rail__label">최근 대화</p>
          <button
            v-for="s in chat.recent"
            :key="s.conversation_id"
            class="rail__session"
            @click="openRecent(s.conversation_id)"
          >
            <span class="rail__session-main">
              <span class="rail__session-name">{{ s.product_name }}</span>
              <span class="rail__session-time">{{ timeAgo(s.last_message_at) }}</span>
            </span>
            <span class="chip">{{ s.country_id }}</span>
          </button>
        </nav>
      </div>
      <div class="rail__bottom">
        <RouterLink class="rail__link" :to="{ name: 'dashboard' }">제품 대시보드</RouterLink>
        <RouterLink class="rail__link" :to="{ name: 'report-archive' }">보고서 보관함</RouterLink>
        <RouterLink class="rail__link" :to="{ name: 'changes' }">변경사항</RouterLink>
        <RouterLink class="rail__link" :to="{ name: 'admin-review' }">규제 검수</RouterLink>
        <div class="rail__user">
          <span class="rail__avatar" />
          <span class="rail__username">{{ auth.user?.name ?? '사용자' }}</span>
          <button class="rail__logout" @click="onLogout">로그아웃</button>
        </div>
      </div>
    </aside>
    <main class="shell__main">
      <slot />
    </main>

    <!-- 새 대화 모달 — 약·국가 선택 후 바로 세션 시작 -->
    <div v-if="showNewChat" class="nc-backdrop" @click.self="showNewChat = false">
      <form class="nc card" @submit.prevent="startNewChat">
        <header class="nc__head">
          <h2>새 대화</h2>
          <button type="button" class="nc__close" @click="showNewChat = false">✕</button>
        </header>
        <label
          >약 *
          <select v-model="newChat.drug_id" class="input">
            <option value="" disabled>제품 선택</option>
            <option v-for="d in drugStore.drugs" :key="d.drug_id" :value="d.drug_id">
              💊 {{ d.product_name }} (v{{ d.version }})
            </option>
          </select>
        </label>
        <label
          >국가 *
          <select v-model="newChat.country_id" class="input">
            <option value="" disabled>규제 문서 보유국</option>
            <option v-for="c in chat.availableCountries" :key="c.country_id" :value="c.country_id">
              🌐 {{ c.name }}
            </option>
          </select>
        </label>
        <p v-if="drugStore.drugs.length === 0" class="nc__hint">
          등록된 제품이 없어요 — 대시보드에서 먼저 제품을 등록해주세요
        </p>
        <p v-if="newChat.error" class="nc__error">✕ {{ newChat.error }}</p>
        <footer class="nc__foot">
          <button type="button" class="btn btn--outline" @click="showNewChat = false">취소</button>
          <button class="btn" :disabled="newChat.starting || drugStore.drugs.length === 0">
            {{ newChat.starting ? '세션 생성 중…' : '채팅 시작 →' }}
          </button>
        </footer>
      </form>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  height: 100vh;
}
.rail {
  width: 250px;
  flex: none;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 20px 14px 16px;
  background: var(--panel);
  border-right: 1px solid var(--border);
  overflow-y: auto;
}
.rail__top {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.rail__logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-left: 6px;
  font-size: 16px;
  color: inherit;
  text-decoration: none;
  border-radius: 8px;
}
.rail__logo:hover {
  opacity: 0.8;
}

/* 새 대화 모달 */
.nc-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(18, 20, 26, 0.45);
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nc {
  width: 380px;
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.nc__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.nc__head h2 {
  margin: 0;
  font-size: 16px;
}
.nc__close {
  border: none;
  background: none;
  font-size: 14px;
  color: var(--faint);
  cursor: pointer;
}
.nc label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12.5px;
  color: var(--sub);
}
.nc__hint {
  margin: 0;
  font-size: 12px;
  color: var(--faint);
}
.nc__error {
  margin: 0;
  font-size: 12px;
  color: var(--danger, #cc5a4d);
}
.nc__foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.rail__mark {
  width: 28px;
  height: 28px;
}
.rail__label {
  font-size: 11.5px;
  color: var(--faint);
  margin: 0 0 4px 8px;
  font-weight: 500;
}
.rail__recent {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.rail__session-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  text-align: left;
}
.rail__session-time {
  font-size: 10.5px;
  color: var(--faint);
}
.rail__session {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 10px;
  border: none;
  border-radius: 8px;
  background: none;
  font-size: 13px;
  color: var(--ink);
  text-align: left;
}
.rail__session:hover {
  background: var(--chip-bg);
}
.rail__session-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rail__bottom {
  display: flex;
  flex-direction: column;
  gap: 2px;
  border-top: 1px solid var(--border);
  padding-top: 10px;
}
.rail__link {
  padding: 9px 10px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--sub);
  text-decoration: none;
}
.rail__link:hover,
.rail__link.router-link-active {
  background: var(--chip-bg);
  color: var(--ink);
}
.rail__user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 10px 4px;
}
.rail__avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--border);
  flex: none;
}
.rail__username {
  font-size: 13px;
  flex: 1;
}
.rail__logout {
  border: none;
  background: none;
  font-size: 11.5px;
  color: var(--faint);
}
.rail__logout:hover {
  color: var(--danger);
}
.shell__main {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
</style>
