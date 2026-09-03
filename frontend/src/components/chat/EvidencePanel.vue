<script setup lang="ts">
import { ref, watch } from 'vue'
import StatusChip from '@/components/StatusChip.vue'
import type { AssessmentResult } from '@/types/api'

const props = defineProps<{ assessment: AssessmentResult; generating?: boolean }>()
const emit = defineEmits<{ close: []; report: [] }>()

const defaultExpanded = () =>
  props.assessment.result?.ingredient_assessments.find((i) => i.status !== 'NO_RESTRICTION')
    ?.ingredient ?? null

const expanded = ref<string | null>(defaultExpanded())
// 패널이 열린 채 다른 카드의 근거를 보면 이전 성분이 펼쳐져 있던 문제 — 판정이 바뀌면 초기화
watch(
  () => props.assessment,
  () => (expanded.value = defaultExpanded()),
)

function trackCitation() {
  // PostHog: citation_clicked (근거 확인율 퍼널)
  console.info('[analytics] citation_clicked')
}
</script>

<template>
  <aside class="ep">
    <header class="ep__head">
      <strong>판정 근거 상세</strong>
      <StatusChip v-if="assessment.result" :status="assessment.result.eligibility" />
      <span style="flex: 1" />
      <button class="ep__close" @click="emit('close')">✕</button>
    </header>
    <span class="chip" v-if="assessment.sources?.length">
      판정 기준 · 지식베이스 반영 {{ assessment.sources?.[0]?.version }}
    </span>

    <div class="ep__list">
      <section
        v-for="ia in assessment.result?.ingredient_assessments"
        :key="ia.ingredient"
        class="ep__row card"
        :class="{ 'ep__row--open': expanded === ia.ingredient }"
      >
        <button
          class="ep__row-head"
          @click="expanded = expanded === ia.ingredient ? null : ia.ingredient"
        >
          <span class="ep__ing">{{ ia.ingredient }}</span>
          <StatusChip :status="ia.status" />
          <span class="ep__caret">{{ expanded === ia.ingredient ? '∧' : '∨' }}</span>
        </button>
        <div v-if="expanded === ia.ingredient" class="ep__detail">
          <p class="ep__reason">AI 요약 — {{ ia.reason }}</p>
          <blockquote v-for="s in assessment.sources" :key="s.document_id" class="ep__cite">
            <strong>근거 원문</strong>
            <p>{{ s.title }} §{{ s.section }} · {{ s.authority }}</p>
            <a :href="s.source_url" target="_blank" rel="noopener" @click="trackCitation">
              출처: {{ s.document_id }} (시행 {{ s.effective_date }}) ↗
            </a>
          </blockquote>
          <p v-if="!assessment.sources?.length" class="ep__nosource">
            인용 가능한 근거 없음 — 추가 검토가 필요합니다
          </p>
        </div>
      </section>
    </div>

    <button
      v-if="assessment.result?.eligibility !== 'REVIEW_REQUIRED'"
      class="btn btn--block"
      :disabled="generating"
      @click="emit('report')"
    >
      {{ generating ? '보고서 생성 중…' : '이 근거로 보고서에 반영' }}
    </button>
    <p class="disclaimer">→ 채팅에 REPORT_GENERATE로 전송됩니다</p>
  </aside>
</template>

<style scoped>
.ep {
  width: 440px;
  flex: none;
  height: 100%;
  border-left: 1.5px solid var(--border);
  background: var(--surface);
  box-shadow: -6px 0 20px rgba(0, 0, 0, 0.06);
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}
.ep__head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
}
.ep__close {
  border: none;
  background: none;
  font-size: 14px;
  color: var(--sub);
}
.ep__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}
.ep__row--open {
  border-color: var(--primary);
  background: var(--panel);
}
.ep__row-head {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: none;
  background: none;
  padding: 12px 14px;
  font-size: 13px;
  text-align: left;
}
.ep__ing {
  flex: 1;
  font-weight: 500;
}
.ep__caret {
  color: var(--faint);
  font-size: 11px;
}
.ep__detail {
  padding: 0 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ep__reason {
  margin: 0;
  font-size: 12.5px;
  color: var(--sub);
}
.ep__cite {
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid var(--primary);
  background: var(--surface);
  border-radius: 0 8px 8px 0;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ep__cite p {
  margin: 0;
  color: var(--sub);
}
.ep__cite a {
  color: var(--primary);
  text-decoration: none;
  font-weight: 500;
}
.ep__nosource {
  margin: 0;
  font-size: 12px;
  color: var(--faint);
}
.disclaimer {
  text-align: center;
  margin: 0;
}
</style>
