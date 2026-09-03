<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { useReportStore } from '@/stores/data'

const route = useRoute()
const reportStore = useReportStore()
const instruction = ref('')
const revising = ref(false)
const chatLog = ref<{ role: 'user' | 'assistant'; text: string }[]>([])

onMounted(() => reportStore.open(String(route.params.id)))

const report = computed(() => reportStore.current)

/** 마지막 수정으로 추가된 단락 강조 표시용 */
const paragraphs = computed(() => {
  const content = report.value?.draft_content ?? ''
  return content.split('\n\n').map((p) => ({
    text: p,
    edited: p.startsWith(`[v${report.value?.version} 수정]`),
  }))
})

async function onRevise() {
  const text = instruction.value.trim()
  if (!text || revising.value || !report.value) return
  instruction.value = ''
  revising.value = true
  chatLog.value.push({ role: 'user', text })
  try {
    await reportStore.revise(text)
    chatLog.value.push({
      role: 'assistant',
      text: `✓ v${report.value.version} 저장됨 — 수정 1건 = 버전 +1`,
    })
  } catch {
    chatLog.value.push({ role: 'assistant', text: '수정에 실패했습니다. 다시 시도해주세요.' })
  } finally {
    revising.value = false
  }
}

function exportPdf() {
  // MVP: GET /api/reports/{id}/export?format=pdf — mock에서는 인쇄 다이얼로그로 대체
  window.print()
}
</script>

<template>
  <AppShell>
    <div class="report" v-if="report">
      <div class="report__doc-area">
        <header class="report__head">
          <span class="chip">적합성검토</span>
          <strong>보고서 · 초안 v{{ report.version }}</strong>
          <span style="flex: 1" />
          <button class="btn btn--outline" @click="exportPdf">PDF</button>
        </header>
        <div class="report__warn">
          ⓘ AI 생성 초안 · 제출 전 검토 필요 — 이 배지는 내보내기 전까지 항상 표시됩니다
        </div>
        <div class="report__sheet-wrap">
          <article class="report__sheet card">
            <p
              v-for="(p, i) in paragraphs"
              :key="i"
              class="report__para"
              :class="{ 'report__para--edited': p.edited }"
            >
              <span v-if="p.edited" class="report__edited-tag">✦ 방금 수정됨</span>
              {{ p.text }}
            </p>
          </article>
        </div>
      </div>

      <aside class="report__side">
        <section class="report__chat">
          <strong>대화형 수정</strong>
          <div class="report__log">
            <p
              v-for="(m, i) in chatLog"
              :key="i"
              :class="m.role === 'user' ? 'log--user' : 'log--ai'"
            >
              <span v-if="m.role === 'assistant'">✦ </span>{{ m.text }}
            </p>
            <p v-if="chatLog.length === 0" class="log--hint">
              "3번 항목 더 자세히" 처럼 수정 지시를 입력해보세요
            </p>
          </div>
        </section>

        <section class="card report__cites" v-if="report.sources?.length">
          <strong>근거 출처 (citations)</strong>
          <a
            v-for="s in report.sources"
            :key="s.document_id"
            :href="s.source_url"
            target="_blank"
            rel="noopener"
          >
            {{ s.title }} §{{ s.section }} ↗
          </a>
        </section>

        <section class="report__versions" v-if="report.history">
          <strong>버전 타임라인</strong> <span class="report__vhint">· 채팅 수정 1건 = v+1</span>
          <ol>
            <li
              v-for="h in report.history"
              :key="h.version"
              :class="{ current: h.version === report.version }"
            >
              <span class="chip" :class="{ 'chip--primary': h.version === report.version }">
                v{{ h.version }}{{ h.version === report.version ? ' · 현재' : '' }}
              </span>
              <span class="report__vcause">{{ h.instruction }}</span>
            </li>
          </ol>
          <p class="disclaimer">PATCH /reports/{id} 마다 version 자동 증가 · 이전 버전 보존</p>
        </section>

        <form class="report__input" @submit.prevent="onRevise">
          <input
            v-model="instruction"
            class="input"
            placeholder="수정 지시 입력…"
            :disabled="revising"
          />
          <button class="composer__send btn" :disabled="revising">↑</button>
        </form>
      </aside>
    </div>
  </AppShell>
</template>

<style scoped>
.report {
  display: flex;
  flex: 1;
  min-height: 0;
  height: 100vh;
}
.report__doc-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--panel);
  min-width: 0;
}
.report__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 24px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  font-size: 14px;
}
.report__warn {
  padding: 8px 24px;
  background: var(--primary-soft);
  color: var(--primary-dark);
  font-size: 12px;
  font-weight: 500;
}
.report__sheet-wrap {
  flex: 1;
  overflow-y: auto;
  padding: 26px;
  display: flex;
  justify-content: center;
}
.report__sheet {
  width: 620px;
  padding: 40px 48px;
  height: fit-content;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  border: none;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.7;
}
.report__para {
  margin: 0 0 14px;
}
.report__para--edited {
  background: var(--primary-soft);
  border-radius: 8px;
  padding: 10px 12px;
}
.report__edited-tag {
  display: block;
  color: var(--primary);
  font-size: 10.5px;
  font-weight: 700;
  margin-bottom: 4px;
}

.report__side {
  width: 360px;
  flex: none;
  border-left: 1px solid var(--border);
  background: var(--surface);
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
  overflow-y: auto;
}
.report__chat {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
}
.report__log {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.report__log p {
  margin: 0;
  font-size: 12.5px;
}
.log--user {
  align-self: flex-end;
  background: var(--chip-bg);
  border-radius: 12px;
  padding: 8px 13px;
}
.log--ai {
  color: var(--sub);
}
.log--hint {
  color: var(--faint);
}
.report__cites {
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  background: var(--panel);
  border: none;
}
.report__cites a {
  color: var(--sub);
  text-decoration: none;
}
.report__cites a:hover {
  color: var(--primary);
}
.report__versions {
  font-size: 13px;
}
.report__vhint {
  font-size: 11px;
  color: var(--faint);
}
.report__versions ol {
  list-style: none;
  margin: 8px 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.report__versions li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.report__vcause {
  color: var(--sub);
}
.report__versions li.current .report__vcause {
  color: var(--ink);
  font-weight: 500;
}
.report__input {
  margin-top: auto;
  display: flex;
  gap: 8px;
}
.composer__send {
  width: 38px;
  padding: 0;
  border-radius: 50%;
  height: 38px;
  flex: none;
}
.disclaimer {
  margin: 4px 0 0;
}
</style>
