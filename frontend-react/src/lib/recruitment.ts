export type PageResult<T> = { records: T[]; total: number; pageNo: number; pageSize: number }

export type Company = {
  id: string
  name: string
  shortName?: string
  logoUrl?: string
  industry?: string
  companySize?: string
  city?: string
  description?: string
}

export type RecruitmentJob = {
  id: string
  positionCode: string
  company: Company
  name: string
  department?: string
  salaryMin?: number
  salaryMax?: number
  city?: string
  experienceRequirement?: string
  educationRequirement?: string
  jobType: string
  description?: string
  requirements?: string
  skillTags: string[]
  recruitmentStatus: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
  publishedAt?: string
  expiresAt?: string
  approvalStatus: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED'
  frozen: boolean
  applied: boolean
  updatedAt: string
}

export type PositionStatistics = {
  applicationCount: number
  averageMatchScore: number
  interviewCount: number
  hiredCount: number
}

export type PositionDetail = {
  job: RecruitmentJob
  statistics: PositionStatistics
  requisition: RecruitmentRequisition
  approvalHistory: RequisitionEvent[]
}

export type RecruitmentRequisition = {
  id: string
  requisitionNo: string
  headcountCode: string
  requestedHeadcount: number
  approvedHeadcount?: number
  costCenterCode: string
  costCenterName?: string
  budgetAmount: number
  budgetCurrency: string
  businessJustification: string
  approvalStatus: RecruitmentJob['approvalStatus']
  submittedBy?: string
  submittedAt?: string
  reviewedBy?: string
  reviewedAt?: string
  reviewNote?: string
  frozen: boolean
  frozenBy?: string
  frozenAt?: string
  freezeReason?: string
  updatedAt?: string
}

export type RequisitionEvent = {
  id: string
  eventType: string
  fromStatus?: string
  toStatus?: string
  operatorId?: string
  operatorName: string
  note?: string
  createdAt: string
}

export type Resume = {
  id: string
  title: string
  fileName?: string
  summary?: string
  skills: string[]
  defaultResume: boolean
  parseStatus: 'MANUAL' | 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'
  parseVersion: number
  parseError?: string
  mediaId?: string
  parsedAt?: string
  updatedAt: string
}

export type ResumeParseTask = {
  taskId: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'
  attempts: number
  maxAttempts: number
  errorMessage?: string
  createdAt?: string
  finishedAt?: string
}

export type OfflineInterview = {
  id: string
  scheduledAt: string
  durationMinutes: number
  interviewType: 'ONSITE' | 'VIDEO' | 'PHONE'
  location?: string
  meetingUrl?: string
  contactName?: string
  contactPhone?: string
  note?: string
  status: string
}

export type RecruitmentInterview = {
  id: string
  title: string
  scheduledAt: string
  duration: number
  status: number
  type: string
}

export type ApplicationHistory = {
  fromStatus?: string
  toStatus: string
  operatorName: string
  note?: string
  createdAt: string
}

export type ApplicationStatusTransition = {
  status: ApplicationStatus
  label: string
  requiresNote: boolean
}

export type JobApplication = {
  id: string
  applicationNo: string
  companyId: string
  companyName: string
  positionId: string
  positionName: string
  candidateId: string
  candidateName: string
  candidateEmail?: string
  candidatePhone?: string
  resume?: Resume
  status: ApplicationStatus
  matchScore?: number
  matchStatus?: MatchStatus
  matchVersion?: number
  matchError?: string
  matchCompletedAt?: string
  matchSummary?: string
  matchDetails?: string
  candidateMessage?: string
  reviewNote?: string
  interviewId?: string
  interview?: RecruitmentInterview
  offlineInterview?: OfflineInterview
  history: ApplicationHistory[]
  submittedAt: string
  updatedAt: string
  allowedTransitions: ApplicationStatusTransition[]
  interviewStatus?: string
  recentActivityAt?: string
  nextStep?: string
}

export type MatchEvaluation = {
  id: string
  applicationId: string
  evaluationVersion: number
  resumeVersion: number
  status: 'PROCESSING' | 'SUCCESS' | 'FAILED'
  ruleScore?: number
  aiScore?: number
  finalScore?: number
  summary?: string
  ruleMatchedSkills: string[]
  matchedSkills: string[]
  strengths: string[]
  gaps: string[]
  risks: string[]
  evidence: string[]
  confidence?: string
  providerName?: string
  modelName?: string
  promptVersion?: number
  recommendation?: string
  humanReviewRequired: boolean
  humanReviewStatus?: 'NOT_REQUIRED' | 'PENDING' | 'APPROVED' | 'OVERRIDDEN' | 'DISMISSED'
  humanReviewDecision?: 'APPROVE' | 'OVERRIDE' | 'DISMISS'
  humanReviewNote?: string
  humanReviewedBy?: string
  humanReviewedAt?: string
  createdAt?: string
  finishedAt?: string
}

export type ResumeAnalysis = {
  resumeId?: string
  analysisVersion?: number
  status?: string
  summary?: string
  targetRoles: string[]
  skills: string[]
  experienceHighlights: string[]
  projects: { name: string; role?: string; evidence?: string }[]
  interviewFocus: string[]
  riskPoints: string[]
  createdAt?: string
  finishedAt?: string
}

export type CompanyResumeAnalysis = {
  skills: string[]
  workExperience: string[]
  projects: { name: string; role?: string; evidence?: string }[]
  education: string[]
  strengths: string[]
  risks: string[]
  followUpDirections: string[]
  analysisVersion?: number
  status?: string
}

export type ApplicationInterview = {
  applicationId: string
  interviewId?: string
  interview?: RecruitmentInterview
  offlineInterview?: OfflineInterview
  interviewStatus?: string
}

export type ApplicationTimelineEvent = {
  id: string
  type: string
  title: string
  description?: string
  actorName?: string
  occurredAt?: string
  tone?: 'default' | 'success' | 'warning' | 'danger' | 'info'
}

export type CompanyReportDetail = {
  applicationId: string
  id?: string
  interviewId?: string
  totalScore?: number
  professionalScore?: number
  expressionScore?: number
  logicScore?: number
  adaptabilityScore?: number
  summary?: string
  strengths?: string
  weaknesses?: string
  improvementSuggestions?: string
  status?: number
  generatedAt?: string
  publishedAt?: string
  questionCount: number
  reliabilityWarning?: string
  reportStatus: 'NOT_AVAILABLE' | 'PENDING' | 'RUNNING' | 'FAILED' | 'READY' | 'PUBLISHED'
  taskStatus?: string
  taskAttempts?: number
  taskMessage?: string
  canRetry: boolean
  questionReviews: CompanyQuestionReview[]
  recording?: CompanyRecordingView
  humanReviewRequired: boolean
  humanReviewStatus?: 'NOT_REQUIRED' | 'PENDING' | 'APPROVED' | 'OVERRIDDEN' | 'DISMISSED'
  humanReviewDecision?: string
  humanReviewNote?: string
  humanReviewedBy?: string
  humanReviewedAt?: string
}

export type CompanyQuestionReview = {
  id: string
  sequenceNo?: number
  question: string
  questionType?: string
  answer: string
  answeredAt?: string
  followUps: string[]
  evaluation?: CompanyEvaluationView
}

export type CompanyEvaluationView = {
  professionalScore?: number
  expressionScore?: number
  logicScore?: number
  adaptabilityScore?: number
  overallScore?: number
  comment?: string
  source?: string
  status?: number
}

export type CompanyRecordingView = {
  id: string
  interviewId: string
  mode: string
  status: string
  startedAt?: string
  endedAt?: string
  segments: { id: string; interviewQuestionId: string; segmentNo: number; startedOffsetMs: number; endedOffsetMs: number; contentType: string; contentPath: string }[]
  events: { id: string; interviewQuestionId?: string; eventType: string; offsetMs: number; content?: string; createdAt?: string }[]
}

export type InterviewQuestionBank = { id: string; name: string; description?: string }

export type CompanyInterviewRange = 'TODAY' | 'NEXT_7_DAYS' | 'COMPLETED' | 'CANCELLED' | 'ALL'
export type CompanyInterviewItem = {
  activityId: string
  interviewKind: 'AI' | 'OFFLINE'
  interviewId?: string
  offlineInterviewId?: string
  applicationId: string
  positionId: string
  positionName: string
  candidateId: string
  candidateName: string
  candidateEmail?: string
  candidatePhone?: string
  activityType: 'AI' | 'ONSITE' | 'VIDEO' | 'PHONE'
  rawStatus?: number
  status: 'SCHEDULED' | 'RUNNING' | 'COMPLETED' | 'CANCELLED' | 'FAILED'
  scheduledAt: string
  durationMinutes: number
  location?: string
  meetingUrl?: string
  contactName?: string
  contactPhone?: string
  note?: string
  applicationStatus: ApplicationStatus
  notificationStatus: 'SENT' | 'NOT_SENT'
  updatedAt?: string
}
export type CompanyInterviewPage = { records: CompanyInterviewItem[]; total: number; pageNo: number; pageSize: number; serverNow: string }
export type CompanyInterviewHistory = { interviewKind: string; fromStatus?: string; toStatus: string; reason?: string; notificationStatus: string; operatorName: string; createdAt: string }
export type CompanyInterviewDetail = { item: CompanyInterviewItem; aiTaskStatus?: string; aiTaskAttempts?: number; aiTaskMessage?: string; statusHistory: CompanyInterviewHistory[] }

export type TalentPoolQuery = {
  keyword?: string
  tagId?: string
  skill?: string
  positionId?: string
  lastContactFrom?: string
  lastContactTo?: string
  sort?: string
}
export type TalentPoolTag = { id: string; name: string; color?: string }
export type TalentPoolCandidate = {
  poolId: string
  candidateId: string
  candidateName: string
  email?: string
  phone?: string
  candidateStatus?: number
  poolStatus: 'ACTIVE' | 'REMOVED'
  lastContactedAt?: string
  addedAt?: string
  updatedAt?: string
  noteCount: number
  applicationCount: number
  lastApplicationAt?: string
  lastActivityAt?: string
  tags: TalentPoolTag[]
}
export type TalentPoolNote = {
  id: string
  applicationId?: string
  content: string
  authorId: string
  authorName: string
  updatedBy?: string
  updatedByName?: string
  version: number
  createdAt: string
  updatedAt: string
}
export type TalentPoolApplication = {
  applicationId: string
  applicationNo: string
  positionId: string
  positionName: string
  status: ApplicationStatus
  matchScore?: number
  interviewStatus: string
  submittedAt: string
  updatedAt: string
}
export type TalentPoolDetail = {
  candidate: TalentPoolCandidate
  tags: TalentPoolTag[]
  notes: PageResult<TalentPoolNote>
  applications: TalentPoolApplication[]
}
export type TalentPoolMembership = { active: boolean; poolId?: string; version?: number; tags: TalentPoolTag[] }

export type MatchStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'MANUAL'

export const matchStatusMeta: Record<MatchStatus, { label: string; tone: 'default' | 'success' | 'warning' | 'danger' | 'info' }> = {
  PENDING: { label: '等待 AI 分析', tone: 'warning' },
  PROCESSING: { label: 'AI 分析中', tone: 'info' },
  SUCCESS: { label: 'AI 分析完成', tone: 'success' },
  FAILED: { label: '分析失败', tone: 'danger' },
  MANUAL: { label: '人工/历史结果', tone: 'default' },
}

export type ApplicationStatus =
  | 'SUBMITTED'
  | 'AI_INTERVIEW_PENDING'
  | 'AI_INTERVIEWING'
  | 'UNDER_REVIEW'
  | 'OFFLINE_INTERVIEW'
  | 'REJECTED'
  | 'HIRED'

export const applicationStatusMeta: Record<ApplicationStatus, { label: string; tone: 'default' | 'success' | 'warning' | 'danger' | 'info' }> = {
  SUBMITTED: { label: '已投递', tone: 'default' },
  AI_INTERVIEW_PENDING: { label: '待 AI 面试', tone: 'warning' },
  AI_INTERVIEWING: { label: 'AI 面试中', tone: 'info' },
  UNDER_REVIEW: { label: '企业评估中', tone: 'info' },
  OFFLINE_INTERVIEW: { label: '线下面试', tone: 'warning' },
  REJECTED: { label: '未通过', tone: 'danger' },
  HIRED: { label: '已录用', tone: 'success' },
}

export const positionStatusMeta = {
  DRAFT: { label: '草稿', tone: 'default' as const },
  PUBLISHED: { label: '招聘中', tone: 'success' as const },
  CLOSED: { label: '已关闭', tone: 'danger' as const },
}

export const approvalStatusMeta = {
  DRAFT: { label: '待提交审批', tone: 'default' as const },
  PENDING_APPROVAL: { label: '等待超级管理员审核', tone: 'warning' as const },
  APPROVED: { label: '已批准', tone: 'success' as const },
  REJECTED: { label: '已驳回', tone: 'danger' as const },
}

export function salaryLabel(job: Pick<RecruitmentJob, 'salaryMin' | 'salaryMax'>) {
  if (job.salaryMin == null && job.salaryMax == null) return '薪资面议'
  if (job.salaryMin != null && job.salaryMax != null) return `${job.salaryMin}–${job.salaryMax}K`
  return job.salaryMin != null ? `${job.salaryMin}K 起` : `最高 ${job.salaryMax}K`
}

export function formatDateTime(value?: string) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}

export function appendQuery(values: Record<string, string | number | undefined>) {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && String(value).trim()) params.set(key, String(value))
  })
  const query = params.toString()
  return query ? `?${query}` : ''
}
