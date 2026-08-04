export type InterviewerStyleKey = 'gentle' | 'pressure' | 'big-tech' | 'hr' | 'project-deep' | 'campus-basic'

export const interviewerStyles: Array<{
  key: InterviewerStyleKey
  label: string
  description: string
}> = [
  { key: 'gentle', label: '温和引导', description: '循序追问，侧重建立表达信心' },
  { key: 'pressure', label: '压力追问', description: '高强度追问，训练抗压与边界表达' },
  { key: 'big-tech', label: '技术深挖', description: '侧重原理、场景、取舍与复杂度' },
  { key: 'hr', label: 'HR 综合', description: '关注动机、稳定性、沟通与职业规划' },
  { key: 'project-deep', label: '项目复盘', description: '围绕职责、难点、方案与结果追问' },
  { key: 'campus-basic', label: '校招基础', description: '覆盖核心基础概念与常见应用' },
]

export function interviewerStyleLabel(value?: string) {
  return interviewerStyles.find(item => item.key === value)?.label ?? '技术深挖'
}

export function interviewerStyleFromRemark(remark?: string) {
  const match = remark?.match(/interviewerStyle=([a-zA-Z0-9-]+)/)
  return (match?.[1] as InterviewerStyleKey | undefined) ?? 'big-tech'
}

export function isPracticeInterview(remark?: string) {
  return remark?.includes('candidate-practice') ?? false
}
