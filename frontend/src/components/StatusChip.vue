<script setup lang="ts">
import { computed } from 'vue'
import type { Eligibility, IngredientStatus } from '@/types/api'

const props = defineProps<{ status: Eligibility | IngredientStatus }>()

const LABELS: Record<string, { label: string; color: string }> = {
  POSSIBLE: { label: '가능', color: 'var(--ok)' },
  NO_RESTRICTION: { label: '제한 없음', color: 'var(--ok)' },
  CONDITIONAL: { label: '조건부', color: 'var(--warn)' },
  REVIEW_REQUIRED: { label: '추가 검토 필요', color: 'var(--faint)' },
  RESTRICTED: { label: '제한 가능성', color: 'var(--danger)' },
}
const meta = computed(() => LABELS[props.status] ?? { label: props.status, color: 'var(--faint)' })
</script>

<template>
  <span class="chip">
    <span class="dot" :style="{ background: meta.color }" />
    {{ meta.label }}
  </span>
</template>
