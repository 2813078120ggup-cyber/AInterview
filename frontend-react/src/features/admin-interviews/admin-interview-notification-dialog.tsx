import { Bell, X } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { fillTemplate, listTemplates, saveTemplate, type NotificationTemplate } from '@/lib/notifications'
import { adminInterviewsApi, type Candidate, type InterviewRow } from './admin-interviews-api'

const dateText = (value?: string) => value?.replace('T', ' ').slice(0, 16) || '-'

type Props = { interview: InterviewRow; candidate?: Candidate; onClose: () => void }

export function AdminInterviewNotificationDialog({ interview, candidate, onClose }: Props) {
  const candidateName = candidate?.realName || candidate?.username || `候选人 #${interview.candidateId}`
  const scheduledAt = dateText(interview.scheduledAt)
  const [templates, setTemplates] = useState<NotificationTemplate[]>(() => listTemplates())
  const [templateId, setTemplateId] = useState(templates[0]?.id || '')
  const [title, setTitle] = useState(() => fillTemplate(templates[0]?.title || '面试通知', { candidateName, interviewTitle: interview.title, scheduledAt }))
  const [content, setContent] = useState(() => fillTemplate(templates[0]?.content || '', { candidateName, interviewTitle: interview.title, scheduledAt }))
  const [templateName, setTemplateName] = useState('')
  const [savingTemplate, setSavingTemplate] = useState(false)
  const [busy, setBusy] = useState(false)
  const [mailError, setMailError] = useState('')

  const applyTemplate = (id: string) => {
    setTemplateId(id)
    const template = templates.find(item => item.id === id)
    if (!template) return
    const variables = { candidateName, interviewTitle: interview.title, scheduledAt }
    setTitle(fillTemplate(template.title, variables))
    setContent(fillTemplate(template.content, variables))
  }

  const createTemplate = () => {
    if (!templateName.trim() || !title.trim() || !content.trim()) return
    const item = saveTemplate({ name: templateName.trim(), title: title.trim(), content: content.trim() })
    setTemplates(listTemplates())
    setTemplateId(item.id)
    setTemplateName('')
    setSavingTemplate(false)
  }

  const submit = async () => {
    if (!title.trim() || !content.trim()) return
    setBusy(true)
    setMailError('')
    try {
      const recipientId = candidate?.id || interview.candidateId
      await adminInterviewsApi.sendSiteNotification({ recipientId, title: title.trim(), content: content.trim(), notificationType: 'INTERVIEW_MESSAGE', businessType: 'INTERVIEW', businessId: interview.id, dedupeKey: `interview:${interview.id}:${Date.now()}` })
      await adminInterviewsApi.syncMailNotification({ candidateId: recipientId, candidateUsername: candidate?.username || undefined, title: title.trim(), content: content.trim(), interviewTitle: interview.title, scheduledAt })
      onClose()
    } catch (reason) {
      setMailError(reason instanceof Error ? reason.message : '邮件同步发送失败')
    } finally {
      setBusy(false)
    }
  }

  return <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="notice-dialog-title">
    <div className="mx-auto my-4 max-w-3xl rounded-[24px] border border-border bg-surface p-5 shadow-2xl sm:my-8 sm:rounded-[32px] sm:p-7">
      <div className="flex items-start justify-between gap-5"><div><p className="text-sm font-semibold tracking-[0.08em] text-[var(--accent)]">候选人通知</p><h2 id="notice-dialog-title" className="mt-1 text-2xl font-black">发送面试通知</h2><p className="mt-2 text-sm text-muted-foreground">通知将发送给 {candidateName}。</p></div><Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={onClose} aria-label="关闭通知对话框"><X className="h-5 w-5" /></Button></div>
      <div className="mt-6 rounded-[24px] border border-border bg-muted/30 p-4"><p className="text-xs font-semibold tracking-[0.08em] text-muted-foreground">面试信息</p><div className="mt-2 grid gap-2 text-sm sm:grid-cols-3"><strong>{interview.title}</strong><span className="text-muted-foreground">候选人：{candidateName}</span><span className="text-muted-foreground">时间：{scheduledAt}</span></div></div>
      <div className="mt-6 grid gap-5">{mailError && <p className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{mailError}</p>}
        <label className="text-sm font-semibold">发送模板<ResponsiveSelect ariaLabel="选择发送模板" value={templateId} onValueChange={applyTemplate} className="mt-2 w-full" options={templates.map(item => ({ value: item.id, label: item.name }))} /></label>
        <label className="text-sm font-semibold">通知标题<input value={title} onChange={event => setTitle(event.target.value)} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
        <label className="text-sm font-semibold">通知内容<textarea value={content} onChange={event => setContent(event.target.value)} rows={5} className="mt-2 w-full rounded-2xl border border-border bg-background p-4 font-normal leading-7 outline-none focus:border-[var(--accent)]" /></label>
        <div className="rounded-[24px] border border-border bg-background p-4"><div className="flex items-center justify-between gap-3"><div><p className="font-semibold">保存为发送模板</p><p className="mt-1 text-xs text-muted-foreground">可使用变量：{'{candidateName}'}、{'{interviewTitle}'}、{'{scheduledAt}'}。</p></div><Button variant="secondary" onClick={() => setSavingTemplate(value => !value)}>{savingTemplate ? '收起' : '新建模板'}</Button></div>{savingTemplate && <div className="mt-4 flex flex-col gap-3 sm:flex-row"><input value={templateName} onChange={event => setTemplateName(event.target.value)} placeholder="模板名称，例如：复盘提醒" className="h-11 flex-1 rounded-2xl border border-border bg-surface px-4 outline-none focus:border-[var(--accent)]" /><Button onClick={createTemplate}>保存模板</Button></div>}</div>
      </div>
      <div className="mt-7 flex justify-end gap-3"><Button variant="secondary" onClick={onClose} disabled={busy}>取消</Button><Button onClick={() => void submit()} disabled={busy}><Bell className="h-4 w-4" />{busy ? '正在发送…' : '发送通知'}</Button></div>
    </div>
  </div>
}
