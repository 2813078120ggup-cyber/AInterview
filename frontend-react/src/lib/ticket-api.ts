import { request, upload } from '@/lib/api'

export type TicketType = 'INTERVIEW_FAILURE' | 'FEATURE_SUGGESTION' | 'BUG_REPORT'
export type TicketStatus = 'DRAFT' | 'PENDING' | 'PROCESSING' | 'RESOLVED' | 'CLOSED'

export type TicketQuery = {
  pageNo?: number
  pageSize?: number
  keyword?: string
  ticketType?: string
  status?: string
  assigneeId?: string
}

export type TicketSummary = {
  id: string
  ticketNo: string
  creatorId: string
  creatorName: string
  ticketType: TicketType
  title: string
  status: TicketStatus
  assigneeId?: string
  assigneeName?: string
  lastActivityAt: string
  createdAt: string
  unreadCount: number
}

export type TicketAttachment = {
  id: string
  mediaId: string
  originalName: string
  contentType: string
  sizeBytes: number
  contentUrl?: string
  createdAt: string
}

export type TicketActivity = {
  id: string
  ticketId: string
  actorId?: string
  actorName: string
  activityType: 'COMMENT' | 'STATUS_CHANGE' | 'ASSIGNMENT' | 'SUBMITTED'
  content?: string
  fromStatus?: TicketStatus
  toStatus?: TicketStatus
  fromAssigneeId?: string
  toAssigneeId?: string
  createdAt: string
  attachments: TicketAttachment[]
}

export type TicketDetail = {
  ticket: TicketSummary
  description: string
  resolution?: string
  version: number
  attachments: TicketAttachment[]
  activities: TicketActivity[]
  permissions: {
    canEdit: boolean
    canSubmit: boolean
    canReply: boolean
    canAssign: boolean
    canChangeStatus: boolean
    canClose: boolean
  }
}

export type TicketPage = {
  records: TicketSummary[]
  total: number
  pageNo: number
  pageSize: number
}

export type Assignee = { id: string; username: string; realName: string }

function queryString(query: TicketQuery) {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  const result = params.toString()
  return result ? `?${result}` : ''
}

export function listMyTickets(query: TicketQuery = {}) {
  return request<TicketPage>(`/v1/tickets/my${queryString(query)}`)
}

export function listAdminTickets(query: TicketQuery = {}) {
  return request<TicketPage>(`/v1/admin/tickets${queryString(query)}`)
}

export function getTicket(id: string) {
  return request<TicketDetail>(`/v1/tickets/${id}`)
}

export function createTicket(payload: { ticketType: TicketType; title?: string; description?: string }) {
  return request<TicketDetail>('/v1/tickets', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateTicket(id: string, payload: { ticketType: TicketType; title: string; description: string }) {
  return request<TicketDetail>(`/v1/tickets/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function deleteTicket(id: string) {
  return request<void>(`/v1/tickets/${id}`, { method: 'DELETE' })
}

export function submitTicket(id: string) {
  return request<TicketDetail>(`/v1/tickets/${id}/submit`, { method: 'POST' })
}

export function listActivities(id: string, afterId?: string) {
  const suffix = afterId ? `?afterId=${encodeURIComponent(afterId)}&limit=100` : '?limit=200'
  return request<TicketActivity[]>(`/v1/tickets/${id}/activities${suffix}`)
}

export function sendTicketMessage(id: string, content: string, clientRequestId: string) {
  return request<TicketActivity>(`/v1/tickets/${id}/messages`, {
    method: 'POST',
    body: JSON.stringify({ content, clientRequestId }),
  })
}

export function uploadTicketAttachment(id: string, file: File) {
  const data = new FormData()
  data.append('file', file)
  return upload<TicketAttachment>(`/v1/tickets/${id}/attachments`, data)
}

export function attachmentUrl(attachment: TicketAttachment) {
  return attachment.contentUrl ? `/api${attachment.contentUrl}` : ''
}

export function markTicketRead(id: string, activityId?: string) {
  return request<void>(`/v1/tickets/${id}/read${activityId ? `?activityId=${encodeURIComponent(activityId)}` : ''}`, { method: 'PUT' })
}

export function listAssignees() {
  return request<Assignee[]>('/v1/admin/tickets/assignees')
}

export function assignTicket(id: string, assigneeId: string | null, version: number) {
  return request<TicketDetail>(`/v1/admin/tickets/${id}/assignee`, {
    method: 'PUT',
    body: JSON.stringify({ assigneeId, version }),
  })
}

export function changeTicketStatus(id: string, targetStatus: TicketStatus, resolution: string, version: number) {
  return request<TicketDetail>(`/v1/admin/tickets/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ targetStatus, resolution, version }),
  })
}
