<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import AssessmentCard from '@/components/chat/AssessmentCard.vue'
import EvidencePanel from '@/components/chat/EvidencePanel.vue'
import { useChatStore } from '@/stores/chat'
import { useDrugStore } from '@/stores/drugs'
import { useReportStore } from '@/stores/reports'
import type { AssessmentResult } from '@/types/api'

const route = useRoute()
const router = useRouter()
const chat = useChatStore()
const drugStore = useDrugStore()
const reportStore = useReportStore()

const input = ref('')
const evidence = ref<AssessmentResult | null>(null)
const streamEl = ref<HTMLElement | null>(null)

// 세 칩이 서로 다른 흐름을 연다 — 판정 / 보고서 / 규제 변경 조회
const QUICK_CHIPS = ['수출 가능한지 확인해줘', '보고서 만들어줘', '규제 변경사항 있나 보여줘']

/** '규제 변경사항 있나' 류의 질문 — 판정이 아니라 검수 피드를 보여준다 */
const REGULATION_CHANGE_RE =
  /(규제|고시|규정|법령).*(변경|개정|바뀐)|(변경|개정|바뀐).*(규제|고시|규정|법령)/

const drug = computed(() => drugStore.drugs.find((d) => d.drug_id === chat.current?.drug_id))
const countryName = computed(
  () =>
    chat.countries.find((c) => c.country_id === chat.current?.country_id)?.name ??
    chat.current?.country_id,
)

/** 현재 국가의 규제 KB 현황 — 헤더 배지 (하드코딩 금지, 실제 적재 문서 기준) */
const kbBadge = computed(() => {
  const docs = chat.kbDocuments.filter(
    (d) => d.country === chat.current?.country_id && d.status === 'ACTIVE',
  )
  if (docs.length === 0) return '근거 문서 없음'
  const latest = docs
    .map((d) => d.effectiveDate)
    .filter(Boolean)
    .sort()
    .at(-1)
  return `근거 문서 ${docs.length}건${latest ? ` · 최신 ${latest}` : ''}`
})

const loadError = ref('')

onMounted(async () => {
  try {
    await Promise.all([drugStore.load(), chat.loadCountries()])
    await chat.openSession(String(route.params.id))
  } catch {
    loadError.value = '세션을 불러오지 못했습니다. 새로고침하거나 잠시 후 다시 시도해주세요.'
  }
})

// 레일에서 다른 최근 대화를 눌러도 같은 컴포넌트가 재사용된다 — 파라미터 변경 감지로 세션 교체
watch(
  () => route.params.id,
  async (id) => {
    if (!id) return
    evidence.value = null
    input.value = ''
    await chat.openSession(String(id))
  },
)

watch(
  () => chat.messages.length,
  () =>
    nextTick(() =>
      streamEl.value?.scrollTo({ top: streamEl.value.scrollHeight, behavior: 'smooth' }),
    ),
)

async function onSend(text?: string) {
  const message = (text ?? input.value).trim()
  if (!message || chat.sending) return
  input.value = ''
  if (REGULATION_CHANGE_RE.test(message)) {
    await chat.showRegulationChanges(message)
    return
  }
  if (/보고서/.test(message)) {
    await chat.requestReport(message)
    return
  }
  await chat.send(message)
}
</script>

<template>
  <AppShell>
    <div class="chatwrap">
      <div class="chatmain">
        <!-- 컨텍스트 바 -->
        <header class="ctxbar">
          <button class="chip chip--outline" @click="router.push({ name: 'dashboard' })">
            ← 대시보드
          </button>
          <span class="chip chip--primary"
            >{{ drug?.product_name ?? '제품' }} · v{{ drug?.version ?? 1 }}</span
          >
          <span class="chip">🌐 {{ countryName }}</span>
          <span style="flex: 1" />
          <span class="chip">📚 {{ kbBadge }}</span>
        </header>

        <!-- 타임라인 -->
        <div ref="streamEl" class="stream">
          <div v-if="chat.messages.length === 0" class="stream__greet">
            <span class="stream__spark">✦</span>
            <h2>{{ drug?.product_name }}, {{ countryName }}에 대해 물어보세요</h2>
            <p>모든 답변에는 규정 원문 근거가 함께 제시됩니다</p>
          </div>

          <p v-if="loadError" class="msg__failed" style="margin: 12px auto; max-width: 480px">
            ✕ {{ loadError }}
          </p>
          <template v-for="(m, i) in chat.messages" :key="m.uid ?? `i-${i}`">
            <!-- user -->
            <div v-if="m.role === 'user'" class="msg msg--user">
              <span class="msg__bubble">{{ m.content }}</span>
            </div>

            <!-- assistant -->
            <div v-else class="msg msg--ai">
              <span class="msg__spark">✦</span>
              <div class="msg__body">
                <span v-if="m.intent" class="msg__intent">{{ m.intent }}</span>

                <!-- pending (스켈레톤) -->
                <div v-if="m.status === 'pending'" class="msg__pending card">
                  <span class="msg__spinner" />
                  {{ m.intent === 'REPORT_GENERATE' ? '보고서 초안 생성 중…' : '판정 진행 중…' }}
                  (status: pending)
                </div>

                <!-- failed (3E) -->
                <div v-else-if="m.status === 'failed'" class="msg__failed">
                  <strong>✕ 요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.</strong>
                  <p>30초 내에 응답을 받지 못했거나 서버 오류가 발생했습니다.</p>
                  <button class="chip chip--primary" @click="chat.retry(m)">↻ 재시도</button>
                </div>

                <!-- 판정 카드 -->
                <AssessmentCard
                  v-else-if="m.assessment"
                  :assessment="m.assessment"
                  :generating="reportStore.generating"
                  @evidence="evidence = m.assessment!"
                  @report="chat.requestReport()"
                  :send-feedback="(r) => chat.sendFeedback(m.assessment!.request_id, r)"
                />

                <!-- 보고서 완료 / 일반 텍스트 -->
                <div v-else class="msg__text" :class="{ 'msg__text--notice': m.notice }">
                  {{ m.content }}
                  <!-- 규제 변경 목록 — 검수 콘솔로 이어진다 -->
                  <ul v-if="m.regulations?.length" class="regs">
                    <li v-for="r in m.regulations" :key="r.regulation_id" class="regs__item">
                      <span class="regs__head">
                        <span class="chip">🌐 {{ r.country_id }}</span>
                        <span class="chip">{{ r.regulation_type }}</span>
                        <span
                          class="chip"
                          :style="
                            r.review_status === 'PENDING'
                              ? 'color: var(--warn); background: var(--warn-soft)'
                              : 'color: var(--ok); background: var(--ok-soft)'
                          "
                          >{{ r.review_status === 'PENDING' ? '검수 대기' : '반영됨' }}</span
                        >
                      </span>
                      <strong>{{ r.title }}</strong>
                      <span class="regs__sub">{{ r.summary }} · 시행일 {{ r.effective_date }}</span>
                    </li>
                  </ul>
                  <button
                    v-if="m.regulations?.length"
                    class="chip chip--primary"
                    @click="router.push({ name: 'changes', query: { tab: 'review' } })"
                  >
                    규제 변경 사항에서 검수 →
                  </button>
                  <button
                    v-if="m.report_id"
                    class="chip chip--primary"
                    @click="router.push({ name: 'report', params: { id: m.report_id } })"
                  >
                    보고서 열기 →
                  </button>
                  <div v-if="m.actions?.length" class="msg__actions">
                    <button
                      v-for="a in m.actions"
                      :key="a.label"
                      class="chip chip--primary"
                      @click="onSend(a.message)"
                    >
                      {{ a.label }}
                    </button>
                    <button class="chip" @click="m.actions = []">나중에</button>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>

        <!-- 입력 -->
        <footer class="composer">
          <div class="composer__chips">
            <button
              v-for="q in QUICK_CHIPS"
              :key="q"
              class="chip chip--outline"
              :disabled="chat.sending || reportStore.generating"
              @click="onSend(q)"
            >
              {{ q }}
            </button>
          </div>
          <form class="composer__bar" @submit.prevent="onSend()">
            <input
              v-model="input"
              class="composer__input"
              placeholder="자연어로 물어보세요 — 버튼도 같은 채팅으로 전송됩니다"
            />
            <button class="composer__send" :disabled="chat.sending" title="전송">↑</button>
          </form>
          <p class="disclaimer">AI 답변은 근거와 함께 제시되는 참고용입니다 · 최종 판단은 담당자</p>
        </footer>
      </div>

      <!-- 04 근거 패널 -->
      <EvidencePanel
        v-if="evidence"
        :assessment="evidence"
        :generating="reportStore.generating"
        @close="evidence = null"
        @report="((evidence = null), chat.requestReport())"
      />
    </div>
  </AppShell>
</template>

<style scoped>
.chatwrap {
  display: flex;
  flex: 1;
  min-height: 0;
  height: 100vh;
}
.chatmain {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.ctxbar {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 28px;
  background: var(--panel);
  border-bottom: 1px solid var(--border);
}

.stream {
  flex: 1;
  overflow-y: auto;
  padding: 26px 110px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.stream__greet {
  margin: auto;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}
.stream__greet h2 {
  margin: 0;
  font-size: 20px;
}
.stream__greet p {
  margin: 0;
  color: var(--sub);
  font-size: 13px;
}
.stream__spark {
  color: var(--primary);
  font-size: 24px;
}

.msg--user {
  display: flex;
  justify-content: flex-end;
}
.msg__bubble {
  background: var(--chip-bg);
  border-radius: 13px;
  padding: 9px 15px;
  font-size: 14px;
  max-width: 70%;
}
.msg--ai {
  display: flex;
  gap: 11px;
}
.msg__spark {
  color: var(--primary);
  flex: none;
  padding-top: 2px;
}
.msg__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  flex: 1;
}
.msg__intent {
  align-self: flex-start;
  font-size: 10px;
  font-weight: 500;
  color: var(--faint);
  border: 1px solid var(--border);
  border-radius: 5px;
  padding: 2px 8px;
  letter-spacing: 0.04em;
}
.msg__pending {
  display: flex;
  align-items: center;
  gap: 10px;
  border-style: dashed;
  background: var(--panel);
  padding: 13px 16px;
  font-size: 13px;
  color: var(--sub);
  max-width: 480px;
}
.msg__spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  flex: none;
  border: 2px solid var(--primary);
  border-top-color: transparent;
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.msg__failed {
  background: var(--danger-soft);
  border-radius: 12px;
  padding: 14px 16px;
  max-width: 520px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12.5px;
}
.msg__failed strong {
  color: var(--danger);
  font-size: 13.5px;
}
.msg__failed p {
  margin: 0;
  color: var(--sub);
}
.msg__failed .chip {
  align-self: flex-start;
}
.msg__text {
  font-size: 13.5px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}
.msg__actions {
  display: flex;
  gap: 6px;
}
.regs {
  list-style: none;
  margin: 2px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  max-width: 560px;
}
.regs__item {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 11px 13px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--panel);
}
.regs__head {
  display: flex;
  gap: 6px;
  align-items: center;
}
.regs__sub {
  color: var(--sub);
  font-size: 12px;
}
.msg__text--notice {
  background: var(--panel);
  border: 1.5px solid var(--primary);
  border-radius: 12px;
  padding: 12px 16px;
}

.composer {
  padding: 8px 110px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.composer__chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.composer__bar {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1.5px solid var(--border);
  border-radius: 14px;
  background: var(--surface);
  padding: 8px 8px 8px 18px;
}
.composer__bar:focus-within {
  border-color: var(--primary);
}
.composer__input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 13.5px;
  background: none;
}
.composer__send {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: var(--primary);
  color: #fff;
  font-weight: 700;
}
.composer__send:disabled {
  background: var(--border);
}
.disclaimer {
  text-align: center;
  margin: 0;
}
</style>
