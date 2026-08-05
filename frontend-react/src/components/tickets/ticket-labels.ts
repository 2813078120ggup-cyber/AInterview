import type { TicketStatus, TicketType } from '@/lib/ticket-api'

export const ticketTypeLabels: Record<TicketType, string> = {
  INTERVIEW_FAILURE: '面试故障',
  FEATURE_SUGGESTION: '功能建议',
  BUG_REPORT: 'BUG 上报',
}

export const ticketStatusLabels: Record<TicketStatus, string> = {
  DRAFT: '草稿',
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
}

export function statusTone(status: TicketStatus): 'default' | 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'CLOSED') return 'default'
  if (status === 'RESOLVED') return 'success'
  if (status === 'PROCESSING') return 'info'
  if (status === 'PENDING') return 'warning'
  return 'default'
}

export function formatTicketDate(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}
