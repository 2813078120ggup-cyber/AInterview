import { baseUrl, request, requestBlob } from '@/lib/api'

export type AuditLog = {
  id: string
  requestId: string
  actorId?: string | null
  actorRole?: string | null
  companyId?: string | null
  module: string
  action: string
  resourceType: string
  resourceId?: string | null
  result: 'SUCCESS' | 'FAILURE' | 'DENIED'
  summary: string
  ipAddress?: string | null
  userAgent?: string | null
  createdAt: string
}

export type AuditLogQuery = {
  pageNo?: number
  pageSize?: number
  keyword?: string
  module?: string
  action?: string
  resourceType?: string
  result?: string
  actorId?: string
  companyId?: string
  from?: string
  to?: string
}

export type AuditLogPage = {
  records: AuditLog[]
  total: number
  pageNo: number
  pageSize: number
}

function queryString(query: AuditLogQuery) {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim()) params.set(key, String(value))
  })
  const encoded = params.toString()
  return encoded ? `?${encoded}` : ''
}

export function listAuditLogs(query: AuditLogQuery = {}) {
  return request<AuditLogPage>(`/v1/admin/operation-audit-logs${queryString(query)}`)
}

export async function downloadAuditLogs(query: AuditLogQuery = {}) {
  const blob = await requestBlob(`/v1/admin/operation-audit-logs/export${queryString(query)}`)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `operation-audit-logs-${new Date().toISOString().slice(0, 10)}.csv`
  anchor.click()
  URL.revokeObjectURL(url)
}

export function auditLogEndpoint(query: AuditLogQuery = {}) {
  return `${baseUrl}/v1/admin/operation-audit-logs/export${queryString(query)}`
}
