export const INTERVIEW_STATUS = {
  PENDING: 0,
  IN_PROGRESS: 1,
  COMPLETED: 2,
  CANCELLED: 3,
  PASSED: 4,
  REPORT_GENERATING: 5,
  REPORT_READY: 6,
  FAILED: 7,
} as const

export const interviewStatusText: Record<number, string> = {
  [INTERVIEW_STATUS.PENDING]: '待开始',
  [INTERVIEW_STATUS.IN_PROGRESS]: '进行中',
  [INTERVIEW_STATUS.COMPLETED]: '已结束',
  [INTERVIEW_STATUS.CANCELLED]: '已取消',
  [INTERVIEW_STATUS.PASSED]: '已通过',
  [INTERVIEW_STATUS.REPORT_GENERATING]: '报告生成中',
  [INTERVIEW_STATUS.REPORT_READY]: '报告已生成',
  [INTERVIEW_STATUS.FAILED]: '未通过',
}

export type BadgeTone = 'default' | 'success' | 'warning' | 'danger' | 'info'

export function interviewStatusTone(status: number): BadgeTone {
  if (status === INTERVIEW_STATUS.PASSED || status === INTERVIEW_STATUS.REPORT_READY) return 'success'
  if (status === INTERVIEW_STATUS.IN_PROGRESS) return 'success'
  if (status === INTERVIEW_STATUS.PENDING) return 'info'
  if (status === INTERVIEW_STATUS.REPORT_GENERATING) return 'warning'
  if (status === INTERVIEW_STATUS.CANCELLED) return 'warning'
  if (status === INTERVIEW_STATUS.FAILED) return 'danger'
  return 'default'
}

export function canEnterInterview(status: number) {
  return status === INTERVIEW_STATUS.PENDING || status === INTERVIEW_STATUS.IN_PROGRESS
}

export function canViewReport(status: number) {
  return status === INTERVIEW_STATUS.COMPLETED
    || status === INTERVIEW_STATUS.PASSED
    || status === INTERVIEW_STATUS.REPORT_READY
    || status === INTERVIEW_STATUS.FAILED
}

export function canWriteReflection(status: number) {
  return status === INTERVIEW_STATUS.COMPLETED
    || status === INTERVIEW_STATUS.PASSED
    || status === INTERVIEW_STATUS.REPORT_GENERATING
    || status === INTERVIEW_STATUS.REPORT_READY
    || status === INTERVIEW_STATUS.FAILED
}

export function isInterviewFinished(status?: number) {
  return status === INTERVIEW_STATUS.COMPLETED
    || status === INTERVIEW_STATUS.CANCELLED
    || status === INTERVIEW_STATUS.PASSED
    || status === INTERVIEW_STATUS.REPORT_GENERATING
    || status === INTERVIEW_STATUS.REPORT_READY
    || status === INTERVIEW_STATUS.FAILED
}

export function isReportPending(status: number) {
  return status === INTERVIEW_STATUS.REPORT_GENERATING
}
