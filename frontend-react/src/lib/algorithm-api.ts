import { request } from '@/lib/api'

export type AlgorithmTag = { id: number; name: string; code: string }

export type AlgorithmProblemListItem = {
  id: number
  title: string
  slug?: string
  difficulty: string
  difficultyLabel: string
  progressStatus?: string
  mySubmitCount: number
  submissionCount: number
  acceptedCount: number
  acceptanceRate: number
  tags: AlgorithmTag[]
  favorited: boolean
  hasNote: boolean
}

export type AlgorithmSampleCase = {
  id: number
  inputData: string
  expectedOutput: string
  score: number
  sortNo?: number
}

export type AlgorithmProblemDetail = {
  id: number
  title: string
  slug?: string
  difficulty: string
  difficultyLabel: string
  descriptionMd: string
  inputDescription?: string
  outputDescription?: string
  constraintsDescription?: string
  hintContent?: string
  timeLimitMs: number
  memoryLimitMb: number
  defaultLanguage: string
  starterCode: string
  progressStatus?: string
  mySubmitCount: number
  favorited: boolean
  note?: string
  tags: AlgorithmTag[]
  sampleCases: AlgorithmSampleCase[]
}

export type AlgorithmRunResponse = {
  submissionId: number
  status: string
  output: string
  errorMessage?: string
  executionTimeMs?: number
  memoryUsageKb?: number
}

export type AlgorithmSubmissionItem = {
  id: number
  problemId: number
  problemTitle: string
  language: string
  submitType: string
  status: string
  passedCount: number
  totalCount: number
  executionTimeMs?: number
  memoryUsageKb?: number
  createdAt: string
}

export type AlgorithmCaseResult = {
  testCaseId?: number
  caseType: string
  status: string
  actualOutput?: string
  executionTimeMs?: number
  memoryUsageKb?: number
}

export type AlgorithmSubmissionDetail = AlgorithmSubmissionItem & {
  score: number
  sourceCode: string
  compileMessage?: string
  runtimeMessage?: string
  caseResults: AlgorithmCaseResult[]
}

export type AlgorithmRecentPractice = {
  id: number
  problemId: number
  problemTitle: string
  status: string
  submitType: string
  language: string
  passedCount: number
  totalCount: number
  executionTimeMs?: number
  createdAt: string
}

export type AlgorithmDashboard = {
  acceptedProblemCount: number
  todayAcceptedCount: number
  submissionCount: number
  acceptanceRate: number
  continuousPracticeDays: number
  difficultyProgress: Record<string, { accepted: number; total: number }>
  recentPractice: AlgorithmRecentPractice[]
  recommended: AlgorithmProblemListItem[]
  hot: AlgorithmProblemListItem[]
}

export type AlgorithmWrongProblem = {
  id: number
  title: string
  slug?: string
  difficulty: string
  difficultyLabel: string
  mySubmitCount: number
  favorited: boolean
  hasNote: boolean
}

export type AlgorithmAdminProblem = {
  id: number
  title: string
  slug?: string
  difficulty: string
  status: number
  sortNo: number
  submissionCount: number
  acceptedCount: number
  createdAt: string
}

export type AlgorithmAdminTestCase = {
  id?: number
  inputData?: string
  expectedOutput: string
  caseType: 'SAMPLE' | 'HIDDEN'
  score: number
  sortNo?: number
  enabled: boolean
}

export type AlgorithmAdminProblemDetail = {
  id: number
  title: string
  slug?: string
  difficulty: string
  descriptionMd: string
  inputDescription?: string
  outputDescription?: string
  constraintsDescription?: string
  hintContent?: string
  timeLimitMs: number
  memoryLimitMb: number
  defaultLanguage: string
  starterCode: string
  solutionCode: string
  status: number
  sortNo: number
  tags: AlgorithmTag[]
  testCases: AlgorithmAdminTestCase[]
  createdAt: string
  updatedAt: string
}

export type AlgorithmAdminSaveRequest = {
  title: string
  slug?: string
  difficulty: string
  descriptionMd: string
  inputDescription?: string
  outputDescription?: string
  constraintsDescription?: string
  hintContent?: string
  timeLimitMs: number
  memoryLimitMb: number
  defaultLanguage: string
  starterCode: string
  solutionCode: string
  status: number
  sortNo: number
  tagIds: number[]
  testCases: AlgorithmAdminTestCase[]
}

export type PageResult<T> = { records: T[]; total: number; pageNo: number; pageSize: number }

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '' && value !== null) search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

export const algorithmApi = {
  dashboard: () => request<AlgorithmDashboard>('/algorithm/dashboard'),

  problems: (params: {
    keyword?: string
    difficulty?: string
    tagId?: number
    progressStatus?: string
    page?: number
    pageSize?: number
  } = {}) => request<PageResult<AlgorithmProblemListItem>>(`/algorithm/problems${query(params)}`),

  problem: (problemId: number) => request<AlgorithmProblemDetail>(`/algorithm/problems/${problemId}`),
  tags: () => request<AlgorithmTag[]>('/algorithm/tags'),

  run: (body: { problemId: number; language: string; sourceCode: string; input: string }) =>
    request<AlgorithmRunResponse>('/algorithm/run', { method: 'POST', body: JSON.stringify(body) }),

  submit: (body: { problemId: number; language: string; sourceCode: string }) =>
    request<number>('/algorithm/submit', { method: 'POST', body: JSON.stringify(body) }),

  submissions: (params: { problemId?: number; status?: string; page?: number; pageSize?: number } = {}) =>
    request<PageResult<AlgorithmSubmissionItem>>(`/algorithm/submissions${query(params)}`),

  submission: (submissionId: number) =>
    request<AlgorithmSubmissionDetail>(`/algorithm/submissions/${submissionId}`),

  favorite: (problemId: number, favorite: boolean) =>
    request<void>(`/algorithm/problems/${problemId}/favorite`, { method: favorite ? 'POST' : 'DELETE' }),

  note: (problemId: number, content: string) =>
    request<void>(`/algorithm/problems/${problemId}/note`, {
      method: 'PUT',
      body: JSON.stringify({ content }),
    }),

  wrongProblems: () => request<AlgorithmWrongProblem[]>('/algorithm/wrong-problems'),

  adminProblems: () => request<AlgorithmAdminProblem[]>('/algorithm/admin/problems'),
  adminProblem: (problemId: number) => request<AlgorithmAdminProblemDetail>(`/algorithm/admin/problems/${problemId}`),
  adminCreate: (body: AlgorithmAdminSaveRequest) =>
    request<number>('/algorithm/admin/problems', { method: 'POST', body: JSON.stringify(body) }),
  adminUpdate: (problemId: number, body: AlgorithmAdminSaveRequest) =>
    request<void>(`/algorithm/admin/problems/${problemId}`, { method: 'PUT', body: JSON.stringify(body) }),
  adminStatus: (problemId: number, status: number) =>
    request<void>(`/algorithm/admin/problems/${problemId}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    }),
}
