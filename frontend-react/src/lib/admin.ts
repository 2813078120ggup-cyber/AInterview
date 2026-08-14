export type AdminWorkspaceMetrics = {
  companyCount: number
  activeUserCount: number
  recruitingPositionCount: number
  weeklyApplicationCount: number
  inProgressInterviewCount: number
  reportBacklogCount: number
  aiFailedTaskCount: number
  pendingTicketCount: number
}

export type AdminWorkerStatus = {
  code: 'IDLE' | 'WORKING' | 'ATTENTION' | string
  label: string
  summary: string
  recommendation: string
  queuedCount: number
  runningCount: number
  oldestQueuedAt?: string | null
}

export type AdminWorkspaceAction = {
  type: string
  label: string
  description: string
  recommendation: string
  count: number
  severity: 'info' | 'warning' | 'danger' | string
  targetPath: string
}

export type AdminWorkspaceSummary = {
  periodStart: string
  periodEnd: string
  generatedAt: string
  metrics: AdminWorkspaceMetrics
  worker: AdminWorkerStatus
  actions: AdminWorkspaceAction[]
}

export type AdminCompany = {
  id: string
  companyCode: string
  name: string
  shortName?: string | null
  logoUrl?: string | null
  industry?: string | null
  companySize?: string | null
  city?: string | null
  description?: string | null
  websiteUrl?: string | null
  recruitmentContactName?: string | null
  recruitmentContactEmail?: string | null
  recruitmentContactPhone?: string | null
  status: number
  recruitingPositionCount: number
  applicationCount: number
  memberCount: number
  createdAt?: string | null
  updatedAt?: string | null
}

export type AdminCompanyOverview = {
  recruitingPositionCount: number
  applicationCount: number
  memberCount: number
  inProgressInterviewCount: number
}

export type AdminCompanyDetail = {
  company: AdminCompany
  overview: AdminCompanyOverview
}

export type AdminCompanyMember = {
  id: string
  username: string
  realName: string
  email?: string | null
  phone?: string | null
  status: number
  roles: string[]
  lastLoginAt?: string | null
  createdAt?: string | null
}

export type AdminUser = {
  id: string
  username: string
  realName: string
  email?: string | null
  phone?: string | null
  avatarUrl?: string | null
  companyId?: string | null
  companyName?: string | null
  status: number
  roles: string[]
  roleIds: string[]
  lastLoginAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type AdminCandidateAccount = {
  id: string
  username: string
  realName: string
  status: number
  avatarAvailable: boolean
  email?: string | null
  emailVerified: boolean
  phone?: string | null
  phoneVerified: boolean
  availableLoginMethods: string[]
  roles: string[]
  identityConsistent: boolean
  lastLoginAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export type AdminCandidateProfile = {
  account: AdminCandidateAccount
  overview: {
    resumeCount: number
    applicationCount: number
    interviewCount: number
    reportCount: number
    latestScore?: number | null
    latestActivityAt?: string | null
  }
  resumes: {
    id: string
    title: string
    fileName?: string | null
    summary?: string | null
    skills: string[]
    defaultResume: boolean
    parseStatus?: string | null
    parseVersion?: number | null
    parsedAt?: string | null
    updatedAt?: string | null
  }[]
  applications: {
    id: string
    applicationNo: string
    companyId: string
    companyName: string
    positionId: string
    positionName: string
    status: string
    matchScore?: number | null
    matchStatus?: string | null
    submittedAt?: string | null
    updatedAt?: string | null
  }[]
  interviews: {
    id: string
    title: string
    scheduledAt?: string | null
    duration?: number | null
    status: number
    type?: string | null
    updatedAt?: string | null
  }[]
  reports: {
    id: string
    interviewId: string
    interviewTitle: string
    scheduledAt?: string | null
    totalScore?: number | null
    professionalScore?: number | null
    expressionScore?: number | null
    logicScore?: number | null
    adaptabilityScore?: number | null
    status: number
    publishedAt?: string | null
    generatedAt?: string | null
  }[]
}

export type AdminRole = {
  id: string
  roleCode: string
  roleName: string
  description?: string | null
  status: number
  permissionIds: string[]
  protectedRole: boolean
  affectedUserCount: number
  version: number
}

export type AdminPermission = {
  id: string
  permissionCode: string
  permissionName: string
  resourceType: string
  description?: string | null
}

export type AdminRecruitmentTask = {
  id: string
  kind: 'MATCH' | 'REPORT' | string
  taskType: string
  status: string
  attempts?: number | null
  maxAttempts?: number | null
  scheduledAt?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  retryable: boolean
  failureSummary?: string | null
}

export type AdminRecruitmentApplication = {
  id: string
  applicationNo: string
  company: { id: string; code?: string | null; name: string; secondary?: string | null }
  position: { id: string; code?: string | null; name: string; secondary?: string | null }
  candidate: { id: string; username: string; name: string }
  status: string
  statusLabel: string
  matchScore?: number | null
  matchStatus: string
  matchTask?: AdminRecruitmentTask | null
  interview?: {
    id: string
    type?: string | null
    status?: number | null
    scheduledAt?: string | null
    startedAt?: string | null
    endedAt?: string | null
    reportStatus?: number | null
    reportGeneratedAt?: string | null
    reportPublishedAt?: string | null
    reportTask?: AdminRecruitmentTask | null
  } | null
  submittedAt?: string | null
  updatedAt?: string | null
  stale: boolean
  nextAction: string
}

export type AdminRecruitmentSummary = {
  generatedAt: string
  staleDays: number
  staleCount: number
  funnel: { status: string; label: string; count: number; terminal: boolean }[]
}

export type AdminRecruitmentDetail = {
  application: AdminRecruitmentApplication
  statusHistory: { id: string; fromStatus?: string | null; toStatus: string; operatorId?: string | null; createdAt: string }[]
}

export type AdminAiOperationsProvider = {
  id: string
  name: string
  code: string
  kind: string
  model?: string | null
  state: string
  stateLabel: string
  enabled: boolean
  textDefault: boolean
  voiceDefault: boolean
  lastTestState?: 'SUCCESS' | 'FAILED' | 'TIMEOUT' | null
  lastTestStatusCode?: number | null
  lastTestLatencyMs?: number | null
  lastTestMessage?: string | null
  lastTestedAt?: string | null
}

export type AdminAiOperationsPrompt = {
  code: string
  name: string
  category: string
  version: number
  active: boolean
  activatedAt?: string | null
}

export type AdminAiOperationsCall = {
  id: string
  requestId: string
  taskId?: string | null
  interviewId?: string | null
  freeInterviewSessionId?: string | null
  generationType: string
  promptCode?: string | null
  promptVersion?: number | null
  provider: string
  model: string
  status: string
  latencyMs?: number | null
  inputChars: number
  outputChars: number
  totalTokens?: number | null
  httpStatus?: number | null
  errorSummary?: string | null
  startedAt: string
  finishedAt?: string | null
}

export type AdminAiOperationsBusinessRef = {
  type: string
  id: string
  label: string
  path?: string | null
}

export type AdminAiOperationsTask = {
  id: string
  taskType: string
  status: string
  attempts?: number | null
  maxAttempts?: number | null
  scheduledAt?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  interviewId?: string | null
  answerId?: string | null
  generationRequestId?: string | null
  provider?: string | null
  model?: string | null
  promptCode?: string | null
  promptVersion?: number | null
  retryable: boolean
  failureSummary?: string | null
  business?: AdminAiOperationsBusinessRef | null
}

export type AdminAiOperationsOverview = {
  generatedAt: string
  ai: { total: number; success: number; failed: number; running: number; averageLatencyMs: number; totalTokens: number; windowLabel: string }
  tasks: { pending: number; running: number; failed: number; backlog: number; reportBacklog: number; oldestPendingAt?: string | null }
  providers: AdminAiOperationsProvider[]
  prompts: AdminAiOperationsPrompt[]
  recentCalls: AdminAiOperationsCall[]
  recentTasks: AdminAiOperationsTask[]
}

export type AdminAiOperationsTrace = {
  business?: AdminAiOperationsBusinessRef | null
  task?: AdminAiOperationsTask | null
  generation: { id: string; requestId: string; status: string; generationType: string; latencyMs?: number | null; inputChars: number; outputChars: number; totalTokens?: number | null; httpStatus?: number | null; startedAt: string; finishedAt?: string | null }
  provider: { code: string; name: string; kind?: string | null; model?: string | null }
  prompt: { code?: string | null; name: string; version?: number | null; category?: string | null; active: boolean }
  result: { type: string; label: string; path?: string | null }
}

export type AdminOperationsSummary = {
  generatedAt: string
  degraded: boolean
  metricsPath: string
  metricsLabel: string
  components: { code: string; label: string; state: string; stateLabel: string; summary: string; recommendation: string }[]
}
