import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/api/client'
import type { LoginResponse, User } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(localStorage.getItem('rai_access_token'))

  async function login(email: string, password: string) {
    const res = await api<LoginResponse>('POST', '/api/auth/login', { email, password })
    token.value = res.access_token
    localStorage.setItem('rai_access_token', res.access_token)
    localStorage.setItem('rai_refresh_token', res.refresh_token)
    localStorage.setItem('rai_role', res.user.role)
    user.value = res.user
  }

  async function signup(payload: {
    email: string
    password: string
    name: string
    company_name: string
  }) {
    await api('POST', '/api/auth/signup', payload)
    await login(payload.email, payload.password)
  }

  async function fetchMe() {
    if (!token.value) return
    try {
      user.value = await api<User>('GET', '/api/auth/me')
    } catch {
      logout()
    }
  }

  function logout() {
    user.value = null
    token.value = null
    localStorage.removeItem('rai_access_token')
    localStorage.removeItem('rai_refresh_token')
    localStorage.removeItem('rai_role')
  }

  return { user, token, login, signup, fetchMe, logout }
})
