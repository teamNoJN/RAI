import { mockFetch } from './mock'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const USE_MOCK = (import.meta.env.VITE_USE_MOCK ?? 'true') !== 'false'

/** Mock 모드 여부 — mock 전용 부수효과(mockAppendAssistant 등)의 가드로만 사용 */
export const IS_MOCK = USE_MOCK

/**
 * 백엔드 미구현 엔드포인트 — real 모드에서도 mock 으로 폴백한다.
 * (게이트웨이 라우트는 있으나 컨트롤러가 아직 없음. 백엔드에 구현되면 여기서 지우면 끝.)
 *  - GET   /api/notifications : 변경사항 알림 (협의 안건)
 *  - PATCH /api/drugs/{id}    : 성분/버전 변경
 */
const MOCK_FALLBACK: [string, RegExp][] = [
  ['GET', /^\/api\/notifications(\?|$)/],
  ['PATCH', /^\/api\/drugs\/[^/]+$/],
]

function isMockFallback(method: string, path: string): boolean {
  return MOCK_FALLBACK.some(([m, re]) => m === method && re.test(path))
}

export interface ApiClientError extends Error {
  code: string
  status: number
}

export function isApiError(e: unknown): e is ApiClientError {
  return e instanceof Error && 'code' in e && 'status' in e
}

/** 401 시 refresh 1회 재시도 — 실패하면 토큰을 지우고 로그인으로 (명세 §0 공통 규약). */
async function tryRefresh(): Promise<boolean> {
  const refreshToken = localStorage.getItem('rai_refresh_token')
  if (!refreshToken) return false
  try {
    const res = await fetch(`${BASE_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh_token: refreshToken }),
    })
    if (!res.ok) return false
    const data = await res.json()
    if (!data?.access_token) return false
    localStorage.setItem('rai_access_token', data.access_token)
    return true
  } catch {
    return false
  }
}

function forceLogin() {
  localStorage.removeItem('rai_access_token')
  localStorage.removeItem('rai_refresh_token')
  if (!window.location.pathname.startsWith('/login')) window.location.assign('/login')
}

async function realFetch(
  method: string,
  path: string,
  body?: unknown,
  retried = false,
): Promise<unknown> {
  const token = localStorage.getItem('rai_access_token')
  const res = await fetch(BASE_URL + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (res.status === 401 && !path.startsWith('/api/auth/') && !retried) {
    if (await tryRefresh()) return realFetch(method, path, body, true)
    forceLogin()
  }
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
  if (USE_MOCK || isMockFallback(method, path)) return mockFetch(method, path, body) as Promise<T>
  return realFetch(method, path, body) as Promise<T>
}

/**
 * multipart 업로드 (규제 KB 문서 등록). Content-Type 은 브라우저가 boundary 포함해 채운다.
 * mock 모드에서는 FormData 를 평범한 객체로 풀어 mockFetch 에 넘긴다.
 */
export async function apiUpload<T>(path: string, form: FormData): Promise<T> {
  if (USE_MOCK) {
    const obj: Record<string, unknown> = {}
    form.forEach((v, k) => {
      obj[k] = v instanceof File ? { name: v.name, size: v.size } : v
    })
    return mockFetch('POST', path, obj) as Promise<T>
  }
  const token = localStorage.getItem('rai_access_token')
  const res = await fetch(BASE_URL + path, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  })
  if (!res.ok) {
    let message = '요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.'
    let code = 'INTERNAL_ERROR'
    try {
      const data = await res.json()
      message = data?.error?.message ?? data?.message ?? message
      code = data?.error?.code ?? code
    } catch {
      /* 본문 없는 에러 */
    }
    throw Object.assign(new Error(message), { code, status: res.status })
  }
  return res.json() as Promise<T>
}

/** 인증 헤더를 실어 파일(blob)을 내려받는다 — 보고서 PDF export 용 */
export async function apiDownload(path: string, filename: string): Promise<void> {
  const token = localStorage.getItem('rai_access_token')
  const res = await fetch(BASE_URL + path, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!res.ok)
    throw Object.assign(new Error('파일을 내려받지 못했습니다. 다시 시도해주세요.'), {
      code: 'DOWNLOAD_FAILED',
      status: res.status,
    })
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
