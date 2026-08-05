import { AlertCircle, CheckCircle2, Clock3, FileImage, MessageCircle, Paperclip, UserRound } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { requestBlob } from '@/lib/api'
import { type TicketActivity, type TicketAttachment, type TicketStatus, type TicketType } from '@/lib/ticket-api'
import { formatTicketDate, statusTone, ticketStatusLabels, ticketTypeLabels } from '@/components/tickets/ticket-labels'

export function TicketStatusBadge({ status }: { status: TicketStatus }) {
  return <Badge tone={statusTone(status)}>{ticketStatusLabels[status]}</Badge>
}

export function TicketTypeBadge({ type }: { type: TicketType }) {
  return <Badge tone="info">{ticketTypeLabels[type]}</Badge>
}

function AttachmentPreview({ attachment }: { attachment: TicketAttachment }) {
  const [source, setSource] = useState('')
  const [error, setError] = useState(false)

  useEffect(() => {
    let active = true
    let objectUrl = ''
    const load = async () => {
      try {
        const blob = await requestBlob(attachment.contentUrl || '')
        objectUrl = URL.createObjectURL(blob)
        if (active) setSource(objectUrl)
        else URL.revokeObjectURL(objectUrl)
      } catch {
        if (active) setError(true)
      }
    }
    load()
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [attachment.contentUrl])

  if (error || !attachment.contentUrl) return <div className="grid aspect-video place-items-center rounded-2xl bg-muted text-xs text-muted-foreground">附件不可用</div>
  if (!source) return <div className="grid aspect-video place-items-center rounded-2xl bg-muted text-xs text-muted-foreground">加载截图…</div>
  return <a href={source} target="_blank" rel="noreferrer" className="block overflow-hidden rounded-2xl border border-border bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]">
    <img src={source} alt={attachment.originalName || '工单截图'} className="aspect-video w-full object-contain" />
  </a>
}

export function AttachmentGrid({ attachments }: { attachments: TicketAttachment[] }) {
  if (!attachments.length) return null
  return <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
    {attachments.map(attachment => <div key={attachment.id} className="min-w-0">
      <AttachmentPreview attachment={attachment} />
      <p className="mt-1 flex items-center gap-1 truncate text-xs text-muted-foreground"><FileImage className="h-3.5 w-3.5 shrink-0" />{attachment.originalName}</p>
    </div>)}
  </div>
}

export function ActivityTimeline({ activities }: { activities: TicketActivity[] }) {
  if (!activities.length) return <div className="grid min-h-40 place-items-center rounded-3xl border border-dashed border-border text-sm text-muted-foreground">暂无处理记录</div>
  return <div className="space-y-3">
    {activities.map(activity => {
      const system = activity.activityType !== 'COMMENT'
      return <article key={activity.id} className={`rounded-3xl border p-4 ${system ? 'border-[var(--accent)]/20 bg-[var(--accent-soft)]/40' : 'border-border bg-surface'}`}>
        <div className="flex items-start gap-3">
          <span className={`grid h-9 w-9 shrink-0 place-items-center rounded-2xl ${system ? 'bg-[var(--accent-soft)] text-[var(--accent)]' : 'bg-muted text-muted-foreground'}`}>
            {activity.activityType === 'STATUS_CHANGE' ? <CheckCircle2 className="h-4 w-4" /> : activity.activityType === 'ASSIGNMENT' ? <UserRound className="h-4 w-4" /> : activity.activityType === 'SUBMITTED' ? <Paperclip className="h-4 w-4" /> : <MessageCircle className="h-4 w-4" />}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="font-bold text-foreground">{activity.actorName || '系统'}</p>
              <time className="text-xs text-muted-foreground">{formatTicketDate(activity.createdAt)}</time>
            </div>
            <p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-muted-foreground">{activity.content || '状态发生变化'}</p>
            <AttachmentGrid attachments={activity.attachments} />
          </div>
        </div>
      </article>
    })}
  </div>
}

export function TicketEmpty({ title, content, action }: { title: string; content: string; action?: React.ReactNode }) {
  return <div className="grid min-h-72 place-items-center rounded-[28px] border border-dashed border-border bg-surface/60 px-6 text-center">
    <div><AlertCircle className="mx-auto h-8 w-8 text-[var(--accent)]" /><h3 className="mt-4 text-lg font-black">{title}</h3><p className="mt-2 text-sm text-muted-foreground">{content}</p>{action && <div className="mt-5">{action}</div>}</div>
  </div>
}

export function TicketLoading() {
  return <div className="grid min-h-52 place-items-center rounded-[28px] border border-border bg-surface text-sm text-muted-foreground"><Clock3 className="mr-2 h-4 w-4 animate-pulse" />正在加载工单…</div>
}
