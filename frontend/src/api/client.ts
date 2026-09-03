import { mockFetch } from './mock'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const USE_MOCK = (import.meta.env.VITE_USE_MOCK ?? 'true') !== 'false'

export interface ApiClientError extends Error {
  code: string
  status: number
}

export function isApiError(e: unknown): e is ApiClientError {
  return e instanceof Error && 'code' in e && 'status' in e
}

async function realFetch(method: string, path: string, body?: unknown): Promise<unknown> {
  const token = localStorage.getItem('rai_access_token')
  const res = await fetch(BASE_URL + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!res.ok) {
    let code = 'INTERNAL_ERROR'
    let message = '요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.'
    try {
      const data = await res.json()
      code = data?.error?.code ?? code
      message = data?.error?.message ?? message
    } catch {
      /* 본문 없는 에러 */
    }
    throw Object.assign(new Error(message), { code, status: res.status })
  }
  if (res.status === 204) return null
  return res.json()
}

export function api<T>(method: string, path: string, body?: unknown): Promise<T> {
  return (USE_MOCK ? mockFetch(method, path, body) : realFetch(method, path, body)) as Promise<T>
}
