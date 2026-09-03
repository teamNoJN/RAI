<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { marked } from 'marked'
import AppShell from '@/components/AppShell.vue'
import { IS_MOCK, apiDownload, isApiError } from '@/api/client'
import { useChatStore } from '@/stores/chat'
import { useDrugStore } from '@/stores/drugs'
import { useReportStore } from '@/stores/reports'

const route = useRoute()
const reportStore = useReportStore()
const drugStore = useDrugStore()
const chat = useChatStore()
const instruction = ref('')
const revising = ref(false)
const chatLog = ref<{ role: 'user' | 'assistant'; text: string }[]>([])

onMounted(() => {
  reportStore.open(String(route.params.id))
  if (drugStore.drugs.length === 0) drugStore.load()
  if (chat.countries.length === 0) chat.loadCountries()
})

const report = computed(() => reportStore.current)

const productName = computed(
  () => drugStore.drugs.find((d) => d.drug_id === report.value?.drug_id)?.product_name,
)
const countryName = computed(
  () =>
    chat.countries.find((c) => c.country_id === report.value?.country_id)?.name ??
    report.value?.country_id,
)

/** 본문은 마크다운(백엔드 초안 템플릿) — 문서처럼 렌더링한다 */
const renderedContent = computed(() =>
  marked.parse(report.value?.draft_content ?? '', { async: false }),
)

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

async function exportPdf() {
  if (IS_MOCK) {
    // mock 모드에서는 인쇄 다이얼로그로 대체
    window.print()
    return
  }
  try {
    await apiDownload(
      `/api/reports/${report.value?.report_id}/export?format=pdf`,
      `RAI_report_${report.value?.report_id}.pdf`,
    )
  } catch (e) {
    chatLog.value.push({
      role: 'assistant',
      text: isApiError(e) ? e.message : '파일을 내려받지 못했습니다. 다시 시도해주세요.',
    })
  }
}
</script>

<template>
  <AppShell>
    <div class="report" v-if="report">
      <div class="report__doc-area">
        <header class="report__head">
          <span class="chip">적합성검토</span>
          <strong>보고서 · 초안 v{{ report.version }}</strong>
          <span v-if="productName" class="chip">{{ productName }}</span>
          <span v-if="countryName" class="chip">🌐 {{ countryName }}</span>
          <span style="flex: 1" />
          <button class="btn btn--outline" @click="exportPdf">PDF</button>
        </header>
        <div class="report__warn">
          ⓘ AI 생성 초안 · 제출 전 검토 필요 — 이 배지는 내보내기 전까지 항상 표시됩니다
        </div>
        <div class="report__sheet-wrap">
          <!-- 본문은 우리 백엔드가 생성한 마크다운 초안 (외부 입력 아님) -->
          <!-- eslint-disable-next-line vue/no-v-html -->
          <article class="report__sheet card" v-html="renderedContent" />
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
  font-size: 13px;
  line-height: 1.7;
}
/* 마크다운 문서 스타일 (v-html 렌더 결과) */
.report__sheet :deep(h1) {
  font-size: 19px;
  margin: 0 0 18px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--ink);
}
.report__sheet :deep(h2) {
  font-size: 14.5px;
  margin: 22px 0 8px;
  color: var(--primary-dark);
}
.report__sheet :deep(h3) {
  font-size: 13px;
  margin: 18px 0 6px;
}
.report__sheet :deep(p) {
  margin: 0 0 10px;
}
.report__sheet :deep(ul) {
  margin: 0 0 10px;
  padding-left: 18px;
}
.report__sheet :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 6px 0 12px;
  font-size: 12.5px;
}
.report__sheet :deep(th),
.report__sheet :deep(td) {
  border: 1px solid var(--border);
  padding: 7px 10px;
  text-align: left;
}
.report__sheet :deep(th) {
  background: var(--panel);
  font-weight: 600;
}
.report__sheet :deep(hr) {
  border: none;
  border-top: 1px dashed var(--border);
  margin: 18px 0;
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
