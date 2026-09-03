<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { isApiError } from '@/api/client'
import { useDrugStore } from '@/stores/drugs'
import { useNotificationStore } from '@/stores/notifications'
import { useChatStore } from '@/stores/chat'
import { NOTIFICATION_META } from '@/constants/notifications'
import type { AppNotification, Drug } from '@/types/api'

const drugStore = useDrugStore()
const noti = useNotificationStore()
const chat = useChatStore()
const router = useRouter()

const query = ref('')
const openDropdown = ref<string | null>(null)
const showRegister = ref(false)
const form = reactive({
  product_name: '',
  ingredientsText: '',
  strength: '',
  dosage_form: 'capsule',
  nameError: false,
  ingError: false,
  error: '',
})
const registering = ref(false)

// '나라 추가' — 규제 문서가 있어야 채팅 대상국이 된다 (문서 업로드 = 추가)
const showAddCountry = ref(false)
const countryForm = reactive({
  country_id: '',
  file: null as File | null,
  title: '',
  authority: '',
  effective_date: '',
  source_url: '',
  error: '',
})
const addingCountry = ref(false)

function onCountryFile(e: Event) {
  countryForm.file = (e.target as HTMLInputElement).files?.[0] ?? null
}

async function onAddCountry() {
  if (!countryForm.country_id || !countryForm.file || !countryForm.title.trim()) {
    countryForm.error =
      '국가·규제 문서 파일·문서 제목은 필수입니다 (문서가 있어야 추가할 수 있어요)'
    return
  }
  addingCountry.value = true
  countryForm.error = ''
  try {
    await chat.addCountryWithDocument({
      country_id: countryForm.country_id,
      file: countryForm.file,
      title: countryForm.title,
      authority: countryForm.authority,
      effective_date: countryForm.effective_date || undefined,
      source_url: countryForm.source_url || undefined,
    })
    showAddCountry.value = false
    countryForm.country_id = ''
    countryForm.file = null
    countryForm.title = ''
    countryForm.authority = ''
  } catch (e) {
    countryForm.error = isApiError(e)
      ? e.message
      : '요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.'
  } finally {
    addingCountry.value = false
  }
}

// 성분/버전 변경 (PATCH /drugs) — 재검토 배너 트리거
const editTarget = ref<Drug | null>(null)
const editForm = reactive({
  ingredientsText: '',
  strength: '',
  dosage_form: 'capsule',
  ingError: false,
})
const updating = ref(false)
const reassessBanner = ref<{ drug: Drug; version: number; countries: string[] } | null>(null)

function openEdit(d: Drug) {
  editTarget.value = d
  editForm.ingredientsText = d.ingredients.join(', ')
  editForm.strength = d.strength
  editForm.dosage_form = d.dosage_form
  editForm.ingError = false
}

async function onUpdate() {
  if (!editTarget.value) return
  const ingredients = editForm.ingredientsText
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
  editForm.ingError = ingredients.length === 0
  if (editForm.ingError) return
  updating.value = true
  try {
    const res = await drugStore.update(editTarget.value.drug_id, {
      ingredients,
      strength: editForm.strength,
      dosage_form: editForm.dosage_form,
    })
    const drug = drugStore.drugs.find((d) => d.drug_id === res.drug_id)!
    if (res.has_prior_assessments) {
      const info = await drugStore.reassessmentNeeded(res.drug_id)
      reassessBanner.value = { drug, version: res.version, countries: info.prior_countries }
    }
    editTarget.value = null
  } finally {
    updating.value = false
  }
}

function onNotification(n: AppNotification) {
  noti.markRead(n.notification_id)
  if (n.type === 'REASSESS_NEEDED' && n.drug_id) {
    openDropdown.value = n.drug_id
  } else if (n.conversation_id) {
    router.push({ name: 'chat', params: { id: n.conversation_id } })
  } else if (n.type === 'REGULATION_CHANGE') {
    router.push({ name: 'admin-review' })
  } else {
    router.push({ name: 'changes' })
  }
}

async function onReassess() {
  if (!reassessBanner.value) return
  const { drug, countries } = reassessBanner.value
  const country = countries[0] ?? chat.availableCountries[0]?.country_id
  if (!country) return
  const cv = await chat.startSession(drug.drug_id, country)
  reassessBanner.value = null
  router.push({ name: 'chat', params: { id: cv.conversation_id } })
}

onMounted(() => {
  drugStore.load()
  noti.load()
  chat.loadCountries()
})

let searchTimer: ReturnType<typeof setTimeout> | undefined
function onSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => drugStore.load(query.value), 300)
}

async function startChat(drugId: string, countryId: string) {
  const cv = await chat.startSession(drugId, countryId)
  openDropdown.value = null
  router.push({ name: 'chat', params: { id: cv.conversation_id } })
}

async function onRegister() {
  form.nameError = !form.product_name.trim()
  const ingredients = form.ingredientsText
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
  form.ingError = ingredients.length === 0
  if (form.nameError || form.ingError) return
  registering.value = true
  form.error = ''
  try {
    await drugStore.register({
      product_name: form.product_name,
      ingredients,
      strength: form.strength,
      dosage_form: form.dosage_form,
    })
    showRegister.value = false
    form.product_name = ''
    form.ingredientsText = ''
    form.strength = ''
  } catch (e) {
    form.error = isApiError(e) ? e.message : '요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.'
  } finally {
    registering.value = false
  }
}
</script>

<template>
  <AppShell>
    <div class="dash">
      <header class="dash__head">
        <h1>제품 대시보드</h1>
        <button class="btn" @click="showRegister = true">＋ 제품 등록</button>
      </header>

      <div v-if="reassessBanner" class="reassess-banner">
        <strong>⚡ v{{ reassessBanner.version }} 저장됨</strong>
        <span>
          기존 판정 결과가 존재합니다 ({{ reassessBanner.countries.join(', ') }}) — 재검토가 필요할
          수 있어요
        </span>
        <span style="flex: 1" />
        <button class="chip chip--primary" @click="onReassess">재검토 →</button>
        <button class="reassess-banner__close" @click="reassessBanner = null">✕</button>
      </div>

      <div class="dash__search">
        <input
          v-model="query"
          class="input"
          placeholder="약 이름·성분으로 검색"
          @input="onSearch"
        />
      </div>

      <div class="dash__body">
        <section class="dash__products">
          <!-- Empty state (02E) -->
          <div
            v-if="!drugStore.loading && drugStore.drugs.length === 0 && !query"
            class="dash__empty"
          >
            <span class="dash__empty-icon" />
            <h2>아직 등록된 제품이 없어요</h2>
            <p>제품을 등록하면 바로 첫 질문을 던질 수 있습니다</p>
            <ol class="dash__steps">
              <li><b>1</b> 제품 등록</li>
              <li>→</li>
              <li><b>2</b> 국가 선택</li>
              <li>→</li>
              <li><b>3</b> 첫 질문</li>
            </ol>
            <button class="btn" @click="showRegister = true">＋ 첫 제품 등록하기</button>
          </div>

          <!-- 검색 결과 없음 (02S) -->
          <div
            v-else-if="!drugStore.loading && drugStore.drugs.length === 0"
            class="dash__empty dash__empty--search"
          >
            <h2>'{{ query }}' 검색 결과가 없어요</h2>
            <p>철자를 확인하거나, 새 제품으로 등록할 수 있어요</p>
            <button class="btn" @click="((form.product_name = query), (showRegister = true))">
              ＋ '{{ query }}' 새로 등록
            </button>
          </div>

          <div v-else class="dash__grid">
            <article v-for="d in drugStore.drugs" :key="d.drug_id" class="card product">
              <div class="product__top">
                <span class="product__thumb">💊</span>
                <div class="product__name">
                  <strong>{{ d.product_name }}</strong>
                  <span>{{ d.ingredients.join(' · ') }}</span>
                </div>
                <span class="chip">{{ d.dosage_form }}</span>
                <span class="chip">v{{ d.version }}</span>
              </div>
              <div class="product__bottom">
                <span class="chip">{{ d.strength }}</span>
                <span style="flex: 1" />
                <button class="chip" @click.stop="openEdit(d)">수정</button>
                <button
                  class="chip chip--primary"
                  @click="openDropdown = openDropdown === d.drug_id ? null : d.drug_id"
                >
                  채팅 시작 ▾
                </button>
              </div>
              <div v-if="openDropdown === d.drug_id" class="product__dropdown card">
                <button
                  v-for="c in chat.availableCountries"
                  :key="c.country_id"
                  @click="startChat(d.drug_id, c.country_id)"
                >
                  🌐 {{ c.name }} <em>→ 세션 생성</em>
                </button>
                <button
                  class="product__add-country"
                  @click="((showAddCountry = true), (openDropdown = null))"
                >
                  ＋ 나라 추가 <em>규제 문서 등록 필요</em>
                </button>
              </div>
            </article>
          </div>
        </section>

        <!-- 변경사항 패널 -->
        <aside class="card changes">
          <header class="changes__head">
            <strong>변경사항</strong>
            <span v-if="noti.items.filter((n) => !n.read).length" class="changes__count">
              {{ noti.items.filter((n) => !n.read).length }}
            </span>
            <span style="flex: 1" />
            <button class="changes__read" @click="noti.markAllRead()">모두 읽음</button>
          </header>
          <button
            v-for="n in noti.items"
            :key="n.notification_id"
            class="changes__item"
            :class="{ 'changes__item--unread': !n.read }"
            @click="onNotification(n)"
          >
            <span class="changes__kind">
              <span v-if="!n.read" class="dot" style="background: var(--warn)" />
              {{ NOTIFICATION_META[n.type].icon }} {{ NOTIFICATION_META[n.type].label }}
            </span>
            <span class="changes__title">{{ n.title }}</span>
            <span class="chip" :class="{ 'chip--primary': !n.read }">{{
              NOTIFICATION_META[n.type].action
            }}</span>
          </button>
          <RouterLink class="changes__all" :to="{ name: 'changes' }"
            >모든 변경사항 보기 →</RouterLink
          >
        </aside>
      </div>
    </div>

    <!-- 제품 등록 모달 (02F) -->
    <div v-if="showRegister" class="modal-backdrop" @click.self="showRegister = false">
      <form class="modal card" @submit.prevent="onRegister">
        <header class="modal__head">
          <h2>제품 등록</h2>
          <button type="button" class="modal__close" @click="showRegister = false">✕</button>
        </header>
        <label :class="{ err: form.nameError }"
          >제품명 (product_name) *
          <input
            v-model="form.product_name"
            class="input"
            :class="{ 'input--error': form.nameError }"
            placeholder="예: 아목시실린 캡슐"
          />
          <span v-if="form.nameError" class="field-error">✕ 제품명을 입력해주세요</span>
        </label>
        <label :class="{ err: form.ingError }"
          >성분 (ingredients, 쉼표 구분) *
          <input
            v-model="form.ingredientsText"
            class="input"
            :class="{ 'input--error': form.ingError }"
            placeholder="Amoxicillin, 첨가제 B"
          />
          <span v-if="form.ingError" class="field-error">✕ 성분을 1개 이상 추가해주세요</span>
        </label>
        <div class="modal__row">
          <label
            >함량 (strength)
            <input v-model="form.strength" class="input" placeholder="500mg" />
          </label>
          <label
            >제형 (dosage_form)
            <select v-model="form.dosage_form" class="input">
              <option value="capsule">캡슐</option>
              <option value="tablet">정제</option>
              <option value="syrup">시럽</option>
            </select>
          </label>
        </div>
        <p v-if="form.error" class="field-error">✕ {{ form.error }}</p>
        <footer class="modal__foot">
          <button type="button" class="btn btn--outline" @click="showRegister = false">취소</button>
          <button class="btn" :disabled="registering">
            {{ registering ? '등록 중…' : '등록하기' }}
          </button>
        </footer>
        <p class="disclaimer">등록 완료 시 version 1로 생성됩니다</p>
      </form>
    </div>

    <!-- 나라 추가 모달 — 규제 문서(POST /api/regulations)가 있어야 채팅 대상국이 된다 -->
    <div v-if="showAddCountry" class="modal-backdrop" @click.self="showAddCountry = false">
      <form class="modal card" @submit.prevent="onAddCountry">
        <header class="modal__head">
          <h2>나라 추가</h2>
          <button type="button" class="modal__close" @click="showAddCountry = false">✕</button>
        </header>
        <p class="disclaimer">
          근거 없는 판정을 막기 위해, 해당 국가의 공식 규제 문서를 등록해야 채팅에서 선택할 수
          있습니다
        </p>
        <label
          >국가 *
          <select v-model="countryForm.country_id" class="input">
            <option value="" disabled>규제 문서가 아직 없는 나라</option>
            <option v-for="c in chat.candidateCountries" :key="c.country_id" :value="c.country_id">
              🌐 {{ c.name }} ({{ c.country_id }})
            </option>
          </select>
        </label>
        <label
          >규제 문서 파일 (PDF) *
          <input type="file" accept=".pdf,.txt,.md" class="input" @change="onCountryFile" />
        </label>
        <label
          >문서 제목 *
          <input
            v-model="countryForm.title"
            class="input"
            placeholder="예: Circular 08/2022/TT-BYT"
          />
        </label>
        <div class="modal__row">
          <label
            >규제 기관
            <input
              v-model="countryForm.authority"
              class="input"
              placeholder="예: Ministry of Health"
            />
          </label>
          <label
            >시행일
            <input v-model="countryForm.effective_date" type="date" class="input" />
          </label>
        </div>
        <label
          >출처 URL
          <input v-model="countryForm.source_url" class="input" placeholder="https://…" />
        </label>
        <p v-if="countryForm.error" class="field-error">✕ {{ countryForm.error }}</p>
        <footer class="modal__foot">
          <button type="button" class="btn btn--outline" @click="showAddCountry = false">
            취소
          </button>
          <button class="btn" :disabled="addingCountry || chat.candidateCountries.length === 0">
            {{ addingCountry ? '문서 등록 중…' : '문서 등록하고 나라 추가' }}
          </button>
        </footer>
        <p v-if="chat.candidateCountries.length === 0" class="disclaimer">
          추가할 수 있는 후보 국가가 없어요 — 국가 마스터 확장은 관리자에게 요청해주세요
        </p>
      </form>
    </div>

    <!-- 성분/버전 변경 모달 (PATCH /api/drugs/{id}) -->
    <div v-if="editTarget" class="modal-backdrop" @click.self="editTarget = null">
      <form class="modal card" @submit.prevent="onUpdate">
        <header class="modal__head">
          <h2>
            성분 정보 수정
            <span class="chip">v{{ editTarget.version }} → v{{ editTarget.version + 1 }}</span>
          </h2>
          <button type="button" class="modal__close" @click="editTarget = null">✕</button>
        </header>
        <p class="disclaimer" style="text-align: left; margin: 0">
          {{ editTarget.product_name }} — 기존 데이터는 덮어쓰지 않고 버전이 증가합니다
        </p>
        <label :class="{ err: editForm.ingError }"
          >성분 (ingredients, 쉼표 구분) *
          <input
            v-model="editForm.ingredientsText"
            class="input"
            :class="{ 'input--error': editForm.ingError }"
          />
          <span v-if="editForm.ingError" class="field-error">✕ 성분을 1개 이상 입력해주세요</span>
        </label>
        <div class="modal__row">
          <label
            >함량 (strength)
            <input v-model="editForm.strength" class="input" />
          </label>
          <label
            >제형 (dosage_form)
            <select v-model="editForm.dosage_form" class="input">
              <option value="capsule">캡슐</option>
              <option value="tablet">정제</option>
              <option value="syrup">시럽</option>
            </select>
          </label>
        </div>
        <footer class="modal__foot">
          <button type="button" class="btn btn--outline" @click="editTarget = null">취소</button>
          <button class="btn" :disabled="updating">{{ updating ? '저장 중…' : '저장하기' }}</button>
        </footer>
        <p class="disclaimer">저장 시 판정 이력이 있으면 재검토 배너가 표시됩니다</p>
      </form>
    </div>
  </AppShell>
</template>

<style scoped>
.dash {
  padding: 26px 32px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
}
.dash__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.reassess-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 12px;
  background: var(--primary);
  color: #fff;
  font-size: 13px;
}
.reassess-banner .chip--primary {
  background: #fff;
  color: var(--primary);
}
.reassess-banner__close {
  border: none;
  background: none;
  color: rgba(255, 255, 255, 0.8);
}
.dash__head h1 {
  font-size: 19px;
  margin: 0;
}
.dash__body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  flex: 1;
}
.dash__products {
  flex: 1;
  min-width: 0;
}
.dash__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}

.product {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  position: relative;
}
.product__top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.product__thumb {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--primary-soft);
  flex: none;
  display: grid;
  place-items: center;
  font-size: 16px;
}
.product__name {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.product__name strong {
  font-size: 14px;
}
.product__name span {
  font-size: 11.5px;
  color: var(--faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.product__bottom {
  display: flex;
  align-items: center;
  gap: 6px;
}
.product__dropdown {
  position: absolute;
  left: 16px;
  right: 16px;
  top: calc(100% - 8px);
  z-index: 10;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  border-color: var(--primary);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}
.product__dropdown button {
  border: none;
  background: none;
  text-align: left;
  padding: 9px 10px;
  border-radius: 6px;
  font-size: 13px;
  display: flex;
  justify-content: space-between;
}
.product__dropdown button:hover {
  background: var(--primary-soft);
}
.product__dropdown em {
  color: var(--faint);
  font-style: normal;
  font-size: 11.5px;
}
.product__add-country {
  border-top: 1px dashed var(--border) !important;
  border-radius: 0 0 6px 6px !important;
  margin-top: 4px;
  color: var(--primary);
  font-weight: 600;
}

.dash__empty {
  border: 1.5px dashed var(--border);
  border-radius: 12px;
  padding: 48px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}
.dash__empty h2 {
  margin: 0;
  font-size: 18px;
}
.dash__empty p {
  margin: 0;
  color: var(--sub);
  font-size: 13px;
}
.dash__empty-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: var(--chip-bg);
}
.dash__steps {
  display: flex;
  gap: 12px;
  list-style: none;
  padding: 0;
  margin: 8px 0;
  color: var(--sub);
  font-size: 12.5px;
  align-items: center;
}
.dash__steps b {
  display: inline-flex;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  align-items: center;
  justify-content: center;
  margin-right: 6px;
  font-size: 11px;
}

.changes {
  width: 340px;
  flex: none;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.changes__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.changes__count {
  background: var(--primary);
  color: #fff;
  border-radius: 50%;
  width: 20px;
  height: 20px;
  font-size: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.changes__read {
  border: none;
  background: none;
  font-size: 11.5px;
  color: var(--faint);
}
.changes__item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  border: 1px solid var(--border-light);
  border-radius: 10px;
  background: var(--surface);
  padding: 11px 12px;
  text-align: left;
  font-size: 12.5px;
}
.changes__item--unread {
  background: var(--panel);
  border-color: var(--border);
}
.changes__item:hover {
  border-color: var(--primary);
}
.changes__kind {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  font-size: 12.5px;
}
.changes__title {
  color: var(--sub);
}
.changes__all {
  font-size: 12px;
  color: var(--sub);
  text-decoration: none;
  align-self: center;
  padding-top: 4px;
}
.changes__all:hover {
  color: var(--primary);
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(18, 20, 26, 0.45);
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
}
.modal {
  width: 520px;
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  border-radius: 16px;
}
.modal__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.modal__head h2 {
  margin: 0;
  font-size: 17px;
}
.modal__close {
  border: none;
  background: none;
  font-size: 14px;
  color: var(--sub);
}
.modal label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 12px;
  font-weight: 500;
}
.modal label.err {
  color: var(--danger);
}
.modal__row {
  display: flex;
  gap: 12px;
}
.modal__row label {
  flex: 1;
}
.modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.disclaimer {
  text-align: center;
  margin: 0;
}
</style>
