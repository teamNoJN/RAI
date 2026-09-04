<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRegulationStore } from '@/stores/regulations'
import type { ReviewStatus } from '@/types/api'

/** 규제 변경 검수 — '규제 변경 사항' 화면의 [규제 검수] 탭 본문 (screen-06-review-console.md) */
const reg = useRegulationStore()
const statusFilter = ref<'ALL' | ReviewStatus>('ALL')
const loadError = ref('')

onMounted(async () => {
  try {
    await reg.loadFeed()
    const first = reg.feed.find((f) => f.review_status === 'PENDING') ?? reg.feed[0]
    if (first) await reg.open(first.regulation_id)
  } catch {
    loadError.value = '검수 피드를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'
  }
})

const filtered = computed(() =>
  reg.feed.filter((f) => statusFilter.value === 'ALL' || f.review_status === statusFilter.value),
)
const pendingCount = computed(() => reg.feed.filter((f) => f.review_status === 'PENDING').length)
</script>

<template>
  <div class="review">
    <header class="review__head">
      <span class="chip">개정 전/후 검수 후 승인 시 지식베이스 반영</span>
      <span style="flex: 1" />
      <button
        v-for="f in ['ALL', 'PENDING', 'REFLECTED'] as const"
        :key="f"
        class="chip"
        :class="{ 'chip--primary': statusFilter === f }"
        @click="statusFilter = f"
      >
        {{ f === 'ALL' ? '전체' : f === 'PENDING' ? `검수 대기 ${pendingCount}` : '반영됨' }}
      </button>
    </header>

    <div class="review__split">
      <!-- 검수 대기 목록 -->
      <div class="review__list">
        <p v-if="loadError" class="field-error" style="padding: 8px">✕ {{ loadError }}</p>
        <p v-if="filtered.length === 0 && !loadError" class="disclaimer" style="padding: 24px 8px">
          표시할 규제 변경 건이 없어요 — 스케줄러가 변경을 감지하면 여기에 쌓입니다
        </p>
        <button
          v-for="item in filtered"
          :key="item.regulation_id"
          class="card review__item"
          :class="{ selected: reg.detail?.regulation_id === item.regulation_id }"
          @click="reg.open(item.regulation_id)"
        >
          <span class="review__item-head">
            <span class="chip">🌐 {{ item.country_id }}</span>
            <span class="chip">{{ item.regulation_type }}</span>
            <span style="flex: 1" />
            <span
              class="chip"
              :style="
                item.review_status === 'PENDING'
                  ? 'color: var(--warn); background: var(--warn-soft)'
                  : 'color: var(--ok); background: var(--ok-soft)'
              "
            >
              {{ item.review_status === 'PENDING' ? '검수 대기' : '반영됨' }}
            </span>
          </span>
          <strong>{{ item.title }}</strong>
          <span class="review__item-sub"
            >{{ item.summary }} · 시행일 {{ item.effective_date }}</span
          >
        </button>
      </div>

      <!-- 상세: 개정 전/후 대조 -->
      <div v-if="reg.detail" class="card review__detail">
        <header class="review__detail-head">
          <strong>{{ reg.detail.title }}</strong>
          <a class="chip" :href="reg.detail.source_url" target="_blank" rel="noopener"
            >원문 보기 ↗</a
          >
        </header>
        <p class="review__ai">✦ AI 요약 — {{ reg.detail.ai_summary }}</p>
        <div class="review__diff">
          <div class="review__col">
            <span class="review__col-label">개정 전</span>
            <p>{{ reg.detail.before }}</p>
          </div>
          <div class="review__col review__col--after">
            <span class="review__col-label">개정 후</span>
            <p>{{ reg.detail.after }}</p>
          </div>
        </div>

        <template v-if="reg.detail.review_status === 'PENDING'">
          <div class="review__actions">
            <button class="btn" :disabled="reg.approving" @click="reg.approve()">
              {{ reg.approving ? '반영 중…' : '승인 후 지식베이스 반영' }}
            </button>
            <span class="disclaimer">자동 반영 없음 — 승인 시각·주체가 감사 로그에 기록됩니다</span>
          </div>
        </template>
        <div v-else class="review__audit">
          ✓ 반영됨 ·
          {{
            reg.detail.reflected_at ? new Date(reg.detail.reflected_at).toLocaleString('ko-KR') : ''
          }}
          · {{ reg.detail.reflected_by }} — 감사 추적 기록
        </div>
        <p class="disclaimer">
          승인 시 영향 국가 세션에 규제 변경 알림이 발행되고, 이후 판정은 개정 기준으로 실행됩니다
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.review {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}
.review__head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.review__split {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.review__list {
  width: 380px;
  flex: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.review__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 7px;
  padding: 13px 14px;
  text-align: left;
  font-size: 12.5px;
}
.review__item.selected {
  border-color: var(--primary);
  border-width: 1.5px;
}
.review__item:hover {
  border-color: var(--primary);
}
.review__item-head {
  display: flex;
  gap: 6px;
  width: 100%;
  align-items: center;
}
.review__item strong {
  font-size: 13.5px;
}
.review__item-sub {
  color: var(--sub);
}
.review__detail {
  flex: 1;
  padding: 20px 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.review__detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: space-between;
  font-size: 15px;
}
.review__ai {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--primary-dark);
}
.review__diff {
  display: flex;
  gap: 12px;
}
.review__col {
  flex: 1;
  background: var(--panel);
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 12.5px;
}
.review__col p {
  margin: 6px 0 0;
  line-height: 1.6;
}
.review__col--after {
  background: var(--primary-soft);
}
.review__col-label {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--sub);
}
.review__col--after .review__col-label {
  color: var(--primary-dark);
}
.review__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.review__audit {
  background: var(--ok-soft);
  color: var(--ok);
  font-weight: 500;
  font-size: 12.5px;
  border-radius: 10px;
  padding: 10px 14px;
}
.disclaimer {
  margin: 0;
}
</style>
