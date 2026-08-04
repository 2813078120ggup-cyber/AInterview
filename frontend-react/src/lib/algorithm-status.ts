export const algorithmStatusMeta: Record<string, { label: string; tone: 'default' | 'success' | 'warning' | 'danger' | 'info' }> = {
  QUEUED: { label: '排队中', tone: 'default' },
  COMPILING: { label: '编译中', tone: 'info' },
  RUNNING: { label: '判题中', tone: 'info' },
  ACCEPTED: { label: '通过', tone: 'success' },
  WRONG_ANSWER: { label: '答案错误', tone: 'danger' },
  COMPILE_ERROR: { label: '编译错误', tone: 'danger' },
  RUNTIME_ERROR: { label: '运行错误', tone: 'danger' },
  TIME_LIMIT_EXCEEDED: { label: '运行超时', tone: 'warning' },
  MEMORY_LIMIT_EXCEEDED: { label: '内存超限', tone: 'warning' },
  OUTPUT_LIMIT_EXCEEDED: { label: '输出超限', tone: 'warning' },
  SYSTEM_ERROR: { label: '判题系统异常', tone: 'danger' },
}

export function algorithmStatusLabel(status?: string) {
  return (status && algorithmStatusMeta[status]?.label) || status || '未知'
}

export function algorithmStatusTone(status?: string): 'default' | 'success' | 'warning' | 'danger' | 'info' {
  return (status && algorithmStatusMeta[status]?.tone) || 'default'
}

export const algorithmDifficultyMeta: Record<string, { label: string; tone: 'success' | 'warning' | 'danger' }> = {
  EASY: { label: '简单', tone: 'success' },
  MEDIUM: { label: '中等', tone: 'warning' },
  HARD: { label: '困难', tone: 'danger' },
}

export function difficultyLabel(difficulty?: string) {
  return (difficulty && algorithmDifficultyMeta[difficulty]?.label) || difficulty || '未知'
}

export const progressStatusMeta: Record<string, string> = {
  NOT_STARTED: '未开始',
  ATTEMPTED: '尝试过',
  ACCEPTED: '已通过',
}
