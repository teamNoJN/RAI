<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { useDrugStore } from '@/stores/drugs'
import { useReportStore } from '@/stores/reports'

const reportStore = useReportStore()
const drugStore = useDrugStore()
const router = useRouter()
onMounted(() => {
  reportStore.loadList()
  if (drugStore.drugs.length === 0) drugStore.load()
})

function drugName(drugId: string) {
  return drugStore.drugs.find((d) => d.drug_id === drugId)?.product_name ?? drugId
}
</script>

<template>
  <AppShell>
    <div class="archive">
      <header class="archive__head">
        <h1>보고서 보관함</h1>
        <span class="disclaimer">GET /api/reports</span>
      </header>

      <div v-if="reportStore.list.length === 0" class="archive__empty">
        <p>아직 생성된 보고서가 없어요</p>
        <p class="disclaimer">채팅에서 판정 후 "보고서 만들어줘"라고 요청해보세요</p>
      </div>

      <div v-else class="card archive__list">
        <button
          v-for="r in reportStore.list"
          :key="r.report_id"
          class="archive__row"
          @click="router.push({ name: 'report', params: { id: r.report_id } })"
        >
          <span class="archive__icon">📄</span>
          <span class="archive__name">적합성 검토 보고서</span>
          <span class="chip">{{ drugName(r.drug_id) }}</span>
          <span class="chip">🌐 {{ r.country_id }}</span>
          <span class="chip">초안 v{{ r.version }}</span>
          <span class="archive__date">{{
            new Date(r.created_at).toLocaleDateString('ko-KR')
          }}</span>
          <span class="chip chip--primary">열기 →</span>
        </button>
      </div>
      <p class="disclaimer">모든 문서에 "AI 초안 · 검토 필요" 배지가 유지됩니다</p>
    </div>
  </AppShell>
</template>

<style scoped>
.archive {
  padding: 26px 32px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.archive__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.archive__head h1 {
  margin: 0;
  font-size: 19px;
}
.archive__empty {
  border: 1.5px dashed var(--border);
  border-radius: 12px;
  padding: 44px;
  text-align: center;
}
.archive__empty p {
  margin: 0 0 6px;
  font-weight: 500;
}
.archive__list {
  overflow: hidden;
}
.archive__row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: none;
  background: none;
  padding: 14px 20px;
  font-size: 13px;
  text-align: left;
  border-top: 1px solid var(--border-light);
}
.archive__row:first-child {
  border-top: none;
}
.archive__row:hover {
  background: var(--panel);
}
.archive__icon {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: var(--primary-soft);
  flex: none;
  display: grid;
  place-items: center;
  font-size: 13px;
}
.archive__name {
  flex: 1;
  font-weight: 500;
}
.archive__date {
  color: var(--faint);
  font-size: 11.5px;
}
</style>
