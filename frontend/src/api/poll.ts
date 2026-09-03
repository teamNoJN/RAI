/** 비동기 작업 공통 폴링 — API 명세: 2초 간격, 30초 타임아웃 (pending → completed/failed) */
export const POLL_INTERVAL_MS = 2000
export const POLL_TIMEOUT_MS = 30000

export class PollTimeoutError extends Error {
  constructor() {
    super('요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.')
    this.name = 'PollTimeoutError'
  }
}

/**
 * fetchOnce 를 done 이 true 를 반환할 때까지 반복 호출한다.
 * 타임아웃 시 PollTimeoutError 를 던진다.
 */
export async function pollUntil<T>(
  fetchOnce: () => Promise<T>,
  done: (value: T) => boolean,
  { intervalMs = POLL_INTERVAL_MS, timeoutMs = POLL_TIMEOUT_MS } = {},
): Promise<T> {
  const startedAt = Date.now()
  for (;;) {
    const value = await fetchOnce()
    if (done(value)) return value
    if (Date.now() - startedAt > timeoutMs) throw new PollTimeoutError()
    await new Promise((r) => setTimeout(r, intervalMs))
  }
}
