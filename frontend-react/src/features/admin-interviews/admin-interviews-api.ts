import { request, type Interview } from '@/lib/api'
import type { ReportDetailData } from '@/components/report-detail-view'

export type Candidate = { id: string; username: string; realName: string }

export type Question = {
  id: string
  content: string
  questionType: string
  difficulty: number
  score: number
}

export type QuestionBank = {
  id: string
  name: string
  bankCode: string
  description?: string
  status: number
}

export type Page<T> = { records: T[]; total: number }
export type InterviewRow = Interview & { candidateId: string }

export type ReportItem = {
  reportId: string
  interviewId: string
  interviewTitle: string
  candidateName: string
  candidateUsername: string
  scheduledAt: string
  totalScore: number
  professionalScore: number
  expressionScore: number
  logicScore: number
  adaptabilityScore: number
  status: number
}

export type ReportDetail = ReportDetailData

export type CreateInterviewPayload = {
  title: string
  candidateId: string
  scheduledAt: string
  duration: number
  type: string
  interviewerStyle: string
  questionIds: string[]
  questionBankId?: string
  questionCount?: number
}

export type Template = {
  id: string
  name: string
  title: string
  type: string
  duration: number
  questionCount: number
  note: string
}

export type FormState = {
  title: string
  candidateId: string
  scheduledAt: string
  duration: number
  type: string
  source: 'question' | 'bank'
  questionIds: string[]
  questionBankId: string
  questionCount: number
  interviewerStyle: string
}

export type BulkState = {
  templateId: string
  title: string
  candidateIds: string[]
  scheduledAt: string
  interval: number
  duration: number
  type: string
  source: 'question' | 'bank'
  questionIds: string[]
  questionBankId: string
  questionCount: number
  interviewerStyle: string
}

export type CreateMode = 'single' | 'bulk'

export const adminInterviewsApi = {
  listInterviews: () => request<InterviewRow[]>('/v1/interviews'),
  listCandidates: () => request<Candidate[]>('/v1/users/candidates'),
  listBanks: () => request<Page<QuestionBank>>('/v1/question-banks?pageNo=1&pageSize=100&status=1'),
  listReports: () => request<Page<ReportItem>>('/v1/reports/page?pageNo=1&pageSize=300'),
  listBankQuestions: (bankId: string) => request<Page<Question>>(`/v1/question-banks/${bankId}/questions?pageNo=1&pageSize=300`),
  createInterview: (payload: CreateInterviewPayload) => request('/v1/interviews', { method: 'POST', body: JSON.stringify(payload) }),
  sendSiteNotification: (payload: Record<string, unknown>) => request('/v1/notifications/site', { method: 'POST', body: JSON.stringify(payload) }),
  syncMailNotification: (payload: Record<string, unknown>) => request('/v1/notifications/mail-sync', { method: 'POST', body: JSON.stringify(payload) }),
  report: (interviewId: string) => request<ReportDetail>(`/v1/interviews/${interviewId}/report`),
  regenerateReport: (interviewId: string) => request<{ id: string }>(`/v1/interviews/${interviewId}/evaluation-task/regenerate`, { method: 'POST' }),
  aiTask: (taskId: string) => request<{ status: string; errorMessage?: string }>(`/v1/ai-tasks/${taskId}`),
  passInterview: (interviewId: string) => request(`/v1/interviews/${interviewId}/pass`, { method: 'POST' }),
  deleteInterview: (interviewId: string) => request(`/v1/interviews/${interviewId}`, { method: 'DELETE' }),
}
