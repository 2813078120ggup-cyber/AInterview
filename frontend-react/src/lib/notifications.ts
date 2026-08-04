export type NotificationAudience = {
  userId: string
  username: string
  realName: string
}

export type InterviewNotification = {
  id: string
  title: string
  content: string
  interviewId?: string
  interviewTitle?: string
  scheduledAt?: string
  candidate: NotificationAudience
  sender: NotificationAudience
  createdAt: string
  readBy: string[]
}

export type NotificationTemplate = {
  id: string
  name: string
  title: string
  content: string
  createdAt: string
}

const notificationKey = 'ai_interview_notifications'
const templateKey = 'ai_interview_notification_templates'
export const notificationEvent = 'ai-interview-notifications-changed'

const defaultTemplates: NotificationTemplate[] = [
  {
    id: 'interview-reminder',
    name: '面试开始提醒',
    title: '你的 AI 面试即将开始',
    content:
      '你好，{candidateName}。你预约的「{interviewTitle}」将于 {scheduledAt} 开始，请提前进入面试大厅，并检查麦克风与摄像头权限。',
    createdAt: 'system',
  },
  {
    id: 'interview-passed',
    name: '面试通过通知',
    title: '恭喜你通过本次 AI 面试评测',
    content:
      '你好，{candidateName}。恭喜你在「{interviewTitle}」中达到通过标准。请及时查看评测报告，复盘优势与改进建议，并留意后续安排。',
    createdAt: 'system',
  },
  {
    id: 'practice-follow-up',
    name: '练习跟进提醒',
    title: '建议继续完成本场模拟练习',
    content:
      '你好，{candidateName}。管理员建议你继续完成「{interviewTitle}」，结束后系统会自动生成评分、追问反馈和能力报告。',
    createdAt: 'system',
  },
]

function readJson<T>(key: string, fallback: T): T {
  try {
    const value = localStorage.getItem(key)
    return value ? (JSON.parse(value) as T) : fallback
  } catch {
    return fallback
  }
}

function writeJson<T>(key: string, value: T) {
  localStorage.setItem(key, JSON.stringify(value))
  window.dispatchEvent(new Event(notificationEvent))
}

export function listNotifications() {
  return readJson<InterviewNotification[]>(notificationKey, [])
}

export function listTemplates() {
  const stored = readJson<NotificationTemplate[]>(templateKey, [])
  const merged = [...defaultTemplates]
  stored.forEach(item => {
    if (!merged.some(template => template.id === item.id)) {
      merged.push(item)
    }
  })
  return merged
}

export function saveTemplate(template: Omit<NotificationTemplate, 'id' | 'createdAt'>) {
  const item: NotificationTemplate = {
    ...template,
    id: `template-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    createdAt: new Date().toISOString(),
  }
  writeJson(templateKey, [item, ...readJson<NotificationTemplate[]>(templateKey, [])])
  return item
}

export function sendNotification(payload: Omit<InterviewNotification, 'id' | 'createdAt' | 'readBy'>) {
  const item: InterviewNotification = {
    ...payload,
    id: `notice-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    createdAt: new Date().toISOString(),
    readBy: [],
  }
  writeJson(notificationKey, [item, ...listNotifications()])
  return item
}

export function markNotificationRead(id: string, userId: string) {
  writeJson(
    notificationKey,
    listNotifications().map(item => {
      if (item.id !== id || item.readBy.includes(userId)) return item
      return { ...item, readBy: [...item.readBy, userId] }
    }),
  )
}

export function markAllNotificationsRead(userId: string) {
  writeJson(
    notificationKey,
    listNotifications().map(item => (item.readBy.includes(userId) ? item : { ...item, readBy: [...item.readBy, userId] })),
  )
}

export function fillTemplate(
  text: string,
  data: { candidateName: string; interviewTitle?: string; scheduledAt?: string },
) {
  return text
    .replaceAll('{candidateName}', data.candidateName)
    .replaceAll('{interviewTitle}', data.interviewTitle || 'AI 模拟面试')
    .replaceAll('{scheduledAt}', data.scheduledAt || '预约时间')
}
