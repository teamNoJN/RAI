import type { NotificationType } from '@/types/api'

/** 알림 유형별 표기 — 대시보드 패널과 변경사항 전체 화면이 공유 */
export const NOTIFICATION_META: Record<
  NotificationType,
  { icon: string; label: string; action: string }
> = {
  REGULATION_CHANGE: { icon: '🔔', label: '규제 변경', action: '세션에서 확인 →' },
  REASSESS_NEEDED: { icon: '⚡', label: '재검토 필요', action: '재검토 →' },
  REASSESS_DONE: { icon: '✓', label: '재판정 완료', action: '결과 보기' },
}
