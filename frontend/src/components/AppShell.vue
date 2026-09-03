<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'

const auth = useAuthStore()
const chat = useChatStore()
const router = useRouter()

onMounted(() => chat.loadRecent())

function openRecent(cvId: string) {
  router.push({ name: 'chat', params: { id: cvId } })
}

function onLogout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <aside class="rail">
      <div class="rail__top">
        <div class="rail__logo">
          <img class="rail__mark" src="@/assets/logo.svg" alt="RAI" />
          <strong>RAI</strong>
        </div>
        <RouterLink class="btn btn--block" :to="{ name: 'dashboard' }">＋ 새 대화</RouterLink>
        <nav class="rail__recent" v-if="chat.recent.length">
          <p class="rail__label">최근 대화</p>
          <button
            v-for="s in chat.recent"
            :key="s.conversation_id"
            class="rail__session"
            @click="openRecent(s.conversation_id)"
          >
            <span class="rail__session-name">{{ s.product_name }}</span>
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
