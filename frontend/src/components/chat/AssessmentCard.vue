<script setup lang="ts">
import { ref } from 'vue'
import StatusChip from '@/components/StatusChip.vue'
import type { AssessmentResult } from '@/types/api'

const props = defineProps<{
  assessment: AssessmentResult
  generating?: boolean
  /** 피드백 전송 함수 — 실패 시 표시를 되돌리기 위해 emit 대신 async prop 으로 받는다 */
  sendFeedback?: (rating: 'helpful' | 'needs_revision') => Promise<void>
}>()
const emit = defineEmits<{ evidence: []; report: [] }>()
const feedbackSent = ref<'helpful' | 'needs_revision' | null>(null)
const feedbackError = ref('')

async function onFeedback(rating: 'helpful' | 'needs_revision') {
  const prev = feedbackSent.value
  feedbackSent.value = rating
  feedbackError.value = ''
  try {
    await props.sendFeedback?.(rating)
  } catch {
    feedbackSent.value = prev // 실패면 성공처럼 보이지 않게 되돌린다
    feedbackError.value = '피드백 전송에 실패했습니다 — 다시 눌러주세요'
  }
}
</script>

<template>
  <div
    class="ac card"
    :class="{ 'ac--review': assessment.result?.eligibility === 'REVIEW_REQUIRED' }"
  >
    <header class="ac__head">
      <strong>수출 가능 여부</strong>
      <span style="flex: 1" />
      <template v-if="assessment.changed_from && assessment.result">
        <StatusChip :status="assessment.changed_from" />
        <span class="ac__arrow">→</span>
        <StatusChip :status="assessment.result.eligibility" />
      </template>
      <StatusChip v-else-if="assessment.result" :status="assessment.result.eligibility" />
    </header>
    <p v-if="assessment.changed_from" class="ac__changed">
      재판정 — 개정 기준으로 판정이 달라졌습니다
    </p>
    <p class="ac__summary">{{ assessment.result?.summary }}</p>

    <ul class="ac__ingredients">
      <li v-for="ia in assessment.result?.ingredient_assessments" :key="ia.ingredient">
        <span class="ac__ing-name">{{ ia.ingredient }}</span>
        <StatusChip :status="ia.status" />
      </li>
    </ul>

    <div v-if="assessment.sources?.length === 0" class="ac__nosource">
      sources: [] — 인용 가능한 근거 없음 (출처를 지어내지 않습니다)
    </div>

    <footer class="ac__foot">
      <template v-if="assessment.result?.eligibility !== 'REVIEW_REQUIRED'">
        <button class="chip chip--primary" @click="emit('evidence')">근거 보기 →</button>
        <button class="chip" :disabled="generating" @click="emit('report')">
          {{ generating ? '보고서 생성 중…' : '보고서 만들어줘' }}
        </button>
      </template>
      <span style="flex: 1" />
      <button
        class="chip"
        :class="{ 'chip--primary': feedbackSent === 'helpful' }"
        @click="onFeedback('helpful')"
      >
        👍 유용
      </button>
      <button
        class="chip"
        :class="{ 'chip--primary': feedbackSent === 'needs_revision' }"
        @click="onFeedback('needs_revision')"
      >
        ✎ 수정 필요
      </button>
    </footer>
    <p v-if="feedbackError" class="ac__fberror">✕ {{ feedbackError }}</p>
    <p class="disclaimer">
      ⓘ AI 기반 초안입니다 · 최종 허가·수출 판단은 RA 전문가 검토가 필요합니다
    </p>
  </div>
</template>

<style scoped>
.ac__fberror {
  margin: 0;
  font-size: 12px;
  color: #cc5a4d;
  text-align: right;
}
.ac {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 640px;
}
.ac--review {
  border-style: dashed;
}
.ac__head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.ac__arrow {
  color: var(--sub);
  font-weight: 700;
  font-size: 12px;
}
.ac__changed {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--danger);
}
.ac__summary {
  margin: 0;
  font-size: 13px;
  color: var(--sub);
}
.ac__ingredients {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ac__ingredients li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
}
.ac__nosource {
  background: var(--panel);
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 12px;
  color: var(--faint);
  font-weight: 500;
}
.ac__foot {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
