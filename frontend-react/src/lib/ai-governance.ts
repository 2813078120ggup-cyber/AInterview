export type AiGovernancePolicy = {
  id: string
  companyId?: string | null
  aiEnabled: boolean
  emergencyStop: boolean
  emergencyReason?: string | null
  evaluationGateRequired: boolean
  evaluationValidDays: number
  minimumPassRate: number
  maximumScoreDrift: number
  maximumFairnessGap: number
  humanReviewMode: 'ALL' | 'ADVERSE_ONLY' | 'LOW_CONFIDENCE'
  adverseScoreThreshold: number
  sensitiveDataMode: 'REDACT' | 'BLOCK_ON_DETECTION'
  dailyCostLimitUsd: number
  monthlyCostLimitUsd: number
  inputCostPerMillionUsd: number
  outputCostPerMillionUsd: number
  perRequestTokenLimit: number
  version: number
  updatedAt?: string
}

export type AiGovernanceRun = {
  id: string
  suiteId: string
  status: 'RUNNING' | 'PASSED' | 'FAILED'
  provider?: string | null
  model?: string | null
  promptCode?: string | null
  promptVersion?: number | null
  caseCount: number
  passedCaseCount: number
  passRate?: number | null
  maximumScoreDrift?: number | null
  maximumFairnessGap?: number | null
  failureSummary?: string | null
  startedBy?: string | null
  startedAt: string
  finishedAt?: string | null
}

export type AiGovernanceSuite = {
  id: string
  suiteCode: string
  name: string
  evaluationType: 'RESUME_ANALYSIS' | 'JOB_MATCH' | 'INTERVIEW_SCORING'
  promptCode: string
  description?: string | null
  caseCount: number
  gateReady: boolean
  targetProvider?: string | null
  targetModel?: string | null
  targetPromptVersion?: number | null
  latestRun?: AiGovernanceRun | null
}

export type AiGovernanceEvent = {
  id: string
  companyId?: string | null
  eventType: string
  generationType?: string | null
  decision: 'ALLOWED' | 'BLOCKED' | 'CHANGED'
  reasonCode?: string | null
  summary: string
  createdAt: string
}

export type AiGovernanceOverview = {
  generatedAt: string
  readiness: 'READY' | 'BLOCKED' | 'STOPPED'
  globalPolicy: AiGovernancePolicy
  tenantPolicies: AiGovernancePolicy[]
  suites: AiGovernanceSuite[]
  globalCost: { todayUsd: number; monthUsd: number; dailyLimitUsd: number; monthlyLimitUsd: number }
  pendingMatchReviews: number
  pendingReportReviews: number
  recentEvents: AiGovernanceEvent[]
}

export type AiGovernancePolicyPayload = Omit<AiGovernancePolicy,
  'id' | 'companyId' | 'emergencyStop' | 'emergencyReason' | 'updatedAt'>

export function policyPayload(policy: AiGovernancePolicy): AiGovernancePolicyPayload {
  return {
    aiEnabled: policy.aiEnabled,
    evaluationGateRequired: policy.evaluationGateRequired,
    evaluationValidDays: Number(policy.evaluationValidDays),
    minimumPassRate: Number(policy.minimumPassRate),
    maximumScoreDrift: Number(policy.maximumScoreDrift),
    maximumFairnessGap: Number(policy.maximumFairnessGap),
    humanReviewMode: policy.humanReviewMode,
    adverseScoreThreshold: Number(policy.adverseScoreThreshold),
    sensitiveDataMode: policy.sensitiveDataMode,
    dailyCostLimitUsd: Number(policy.dailyCostLimitUsd),
    monthlyCostLimitUsd: Number(policy.monthlyCostLimitUsd),
    inputCostPerMillionUsd: Number(policy.inputCostPerMillionUsd),
    outputCostPerMillionUsd: Number(policy.outputCostPerMillionUsd),
    perRequestTokenLimit: Number(policy.perRequestTokenLimit),
    version: policy.version,
  }
}
