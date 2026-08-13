export type CompanySettings = {
  id: string
  companyCode: string
  name: string
  shortName?: string
  logoUrl?: string
  industry?: string
  companySize?: string
  city?: string
  description?: string
  websiteUrl?: string
  recruitmentContactName?: string
  recruitmentContactEmail?: string
  recruitmentContactPhone?: string
  updatedAt?: string
}

export type CompanySettingsInput = Omit<CompanySettings, 'id' | 'companyCode' | 'updatedAt'>

export type CompanyTeamMember = {
  id: string
  username: string
  realName: string
  email?: string
  phone?: string
  status: number
  roles: string[]
  createdAt?: string
}

export type CompanyAnalyticsFunnelStage = {
  status: string
  label: string
  count: number
  conversionRate: number
  shareOfApplications: number
}

export type CompanyAnalyticsScoreBucket = {
  key: string
  label: string
  count: number
  percentage: number
}

export type CompanyAnalyticsOverview = {
  from: string
  to: string
  sampleSize: number
  lowSample: boolean
  funnel: CompanyAnalyticsFunnelStage[]
  averageInitialScreeningHours: number
  averageTimeToInterviewHours: number
  averageHiringCycleDays: number
  applicationCount: number
  interviewConversionRate: number
  hireRate: number
  matchScoreDistribution: CompanyAnalyticsScoreBucket[]
  generatedAt?: string
}

export type CompanyPositionAnalytics = {
  positionId: string
  positionName: string
  recruitmentStatus: string
  applicationCount: number
  averageMatchScore: number
  interviewCount: number
  hiredCount: number
  interviewConversionRate: number
  hireRate: number
}
