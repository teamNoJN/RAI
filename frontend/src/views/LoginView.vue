<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { isApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const mode = ref<'login' | 'signup'>('login')
const busy = ref(false)
const login = reactive({ email: '', password: '', error: '' })
const signup = reactive({
  email: '',
  password: '',
  name: '',
  company_name: '',
  emailError: '',
  error: '',
})

async function onLogin() {
  login.error = ''
  busy.value = true
  try {
    await auth.login(login.email, login.password)
    router.push({ name: 'dashboard' })
  } catch (e) {
    login.error = isApiError(e) ? e.message : '요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.'
  } finally {
    busy.value = false
  }
}

async function onSignup() {
  signup.error = ''
  signup.emailError = ''
  busy.value = true
  try {
    await auth.signup({ ...signup })
    router.push({ name: 'dashboard' })
  } catch (e) {
    if (isApiError(e) && e.status === 409) signup.emailError = '이미 가입된 이메일입니다'
    else
      signup.error = isApiError(e)
        ? e.message
        : '요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="auth">
    <div class="auth__card card">
      <div class="auth__logo">
        <img class="auth__mark" src="@/assets/logo.svg" alt="RAI" />
        <strong>RAI</strong>
        <p>약·국가를 고르고, 대화로 확인하세요</p>
      </div>

      <template v-if="mode === 'login'">
        <form class="auth__form" @submit.prevent="onLogin">
          <input
            v-model="login.email"
            class="input"
            type="email"
            placeholder="업무용 이메일"
            required
          />
          <input
            v-model="login.password"
            class="input"
            :class="{ 'input--error': login.error }"
            type="password"
            placeholder="비밀번호"
            required
          />
          <p v-if="login.error" class="auth__error">✕ {{ login.error }}</p>
          <button class="btn btn--block" :disabled="busy">
            {{ busy ? '로그인 중…' : '로그인' }}
          </button>
        </form>
        <button class="auth__switch" @click="mode = 'signup'">처음이신가요? 회원가입 →</button>
        <p class="disclaimer">데모 계정: ra@pharm.co 또는 pm@pharm.co / rai1234</p>
      </template>

      <template v-else>
        <form class="auth__form" @submit.prevent="onSignup">
          <input v-model="signup.name" class="input" placeholder="이름" required />
          <input
            v-model="signup.email"
            class="input"
            :class="{ 'input--error': signup.emailError }"
            type="email"
            placeholder="업무용 이메일"
            required
          />
          <p v-if="signup.emailError" class="field-error">✕ {{ signup.emailError }}</p>
          <input
            v-model="signup.password"
            class="input"
            type="password"
            placeholder="비밀번호"
            required
          />
          <input
            v-model="signup.company_name"
            class="input"
            placeholder="회사명 (company_name)"
            required
          />
          <p class="disclaimer">
            ⓘ 회사명이 기존 회사와 같으면 자동 소속, 새 이름이면 회사가 생성됩니다
          </p>
          <p v-if="signup.error" class="auth__error">✕ {{ signup.error }}</p>
          <button class="btn btn--block" :disabled="busy">가입하고 시작하기 → 제품 대시보드</button>
        </form>
        <button class="auth__switch" @click="mode = 'login'">← 로그인으로 돌아가기</button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--panel);
}
.auth__card {
  width: 420px;
  padding: 40px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  border-radius: 16px;
}
.auth__logo {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  font-size: 18px;
}
.auth__logo p {
  margin: 0;
  font-size: 12.5px;
  color: var(--sub);
  font-weight: 400;
}
.auth__mark {
  width: 44px;
  height: 44px;
}
.auth__form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.auth__error {
  margin: 0;
  padding: 9px 12px;
  border-radius: 8px;
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 12.5px;
  font-weight: 500;
}
.auth__switch {
  border: none;
  background: none;
  font-size: 12.5px;
  color: var(--sub);
  align-self: center;
}
.auth__switch:hover {
  color: var(--primary);
}
.disclaimer {
  text-align: center;
  margin: 0;
}
</style>
