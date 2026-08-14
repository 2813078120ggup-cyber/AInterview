import { ArrowLeft, Edit3, MessageCircle, Send } from 'lucide-react'
import { useEffect, useEffectEvent, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ActivityTimeline, AttachmentGrid, TicketLoading, TicketStatusBadge, TicketTypeBadge } from '@/components/tickets/ticket-ui'
import { formatTicketDate } from '@/components/tickets/ticket-labels'
import { getTicket, listActivities, markTicketRead, sendTicketMessage, type TicketActivity, type TicketDetail } from '@/lib/ticket-api'

export function CandidateTicketDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [detail, setDetail] = useState<TicketDetail | null>(null)
  const [activities, setActivities] = useState<TicketActivity[]>([])
  const [content, setContent] = useState('')
  const [busy, setBusy] = useState(true)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const latestId = useRef('')

  async function load() {
    try {
      const result = await getTicket(id)
      setDetail(result)
      setActivities(result.activities)
      latestId.current = result.activities.at(-1)?.id || ''
      await markTicketRead(id, latestId.current || undefined)
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '工单加载失败')
    } finally { setBusy(false) }
  }

  const loadEffect = useEffectEvent(load)

  useEffect(() => { void loadEffect() }, [id])

  const ticketStatus = detail?.ticket.status
  useEffect(() => {
    if (!id || !ticketStatus || ticketStatus === 'CLOSED') return
    const poll = window.setInterval(async () => {
      try {
        const next = await listActivities(id, latestId.current || undefined)
        if (!next.length) return
        setActivities(previous => [...previous, ...next])
        latestId.current = next.at(-1)?.id || latestId.current
        await markTicketRead(id, latestId.current)
      } catch {
        // Keep the existing conversation visible while a later poll retries.
      }
    }, 3000)
    return () => window.clearInterval(poll)
  }, [id, ticketStatus])

  async function send() {
    if (!content.trim() || !detail) return
    setSending(true)
    try {
      const item = await sendTicketMessage(id, content.trim(), crypto.randomUUID())
      setActivities(previous => [...previous, item])
      latestId.current = item.id
      setContent('')
      await markTicketRead(id, item.id)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '发送失败')
    } finally { setSending(false) }
  }

  if (busy) return <TicketLoading />
  if (!detail) return <div className="space-y-4"><Link to="/candidate/tickets" className="inline-flex items-center gap-2 text-sm font-semibold text-muted-foreground"><ArrowLeft className="h-4 w-4" />返回工单列表</Link><div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error || '工单不存在'}</div></div>
  const closed = detail.ticket.status === 'CLOSED'
  return <div className="mx-auto max-w-5xl space-y-6">
    <div className="flex flex-wrap items-center justify-between gap-3"><Link to="/candidate/tickets" className="inline-flex items-center gap-2 text-sm font-semibold text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" />返回工单列表</Link>{detail.permissions.canEdit && <Button variant="secondary" onClick={() => navigate(`/candidate/tickets/${id}/edit`)}><Edit3 className="h-4 w-4" />编辑草稿</Button>}</div>
    {error && <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</div>}
    <Card className="p-5 sm:p-7"><div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><TicketTypeBadge type={detail.ticket.ticketType} /><TicketStatusBadge status={detail.ticket.status} /></div><h1 className="mt-3 break-words text-2xl font-black">{detail.ticket.title || '未命名草稿'}</h1><p className="mt-2 text-xs text-muted-foreground">{detail.ticket.ticketNo || '草稿未提交'} · 创建于 {formatTicketDate(detail.ticket.createdAt)} · 最后更新 {formatTicketDate(detail.ticket.lastActivityAt)}</p></div><div className="shrink-0 text-left text-sm text-muted-foreground sm:text-right">{detail.ticket.assigneeName ? `处理人：${detail.ticket.assigneeName}` : '等待管理员受理'}</div></div><div className="mt-6 whitespace-pre-wrap break-words rounded-3xl bg-muted/55 p-4 text-sm leading-7 text-foreground sm:p-5">{detail.description || '暂无问题描述'}</div><AttachmentGrid attachments={detail.attachments} />{detail.resolution && <div className="mt-4 rounded-3xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-7 text-emerald-800"><p className="font-bold">处理说明</p><p className="mt-1 whitespace-pre-wrap">{detail.resolution}</p></div>}</Card>
    <Card className="p-5 sm:p-7"><div className="mb-5 flex items-center justify-between gap-3"><div><h2 className="flex items-center gap-2 text-xl font-black"><MessageCircle className="h-5 w-5 text-[var(--accent)]" />处理记录</h2><p className="mt-1 text-sm text-muted-foreground">消息会自动刷新，管理员回复后会出现在这里。</p></div><span className="text-xs text-muted-foreground">{activities.length} 条记录</span></div><ActivityTimeline activities={activities} /></Card>
    <Card className="sticky bottom-3 p-4 sm:p-5"><label className="block"><span className="sr-only">回复内容</span><textarea value={content} onChange={event => setContent(event.target.value)} disabled={closed || sending} rows={4} maxLength={5000} placeholder={closed ? '该工单已关闭，不能继续留言' : '补充问题信息或回复管理员…'} className="w-full resize-y rounded-2xl border border-border bg-background p-4 text-sm leading-6 outline-none focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20 disabled:cursor-not-allowed disabled:bg-muted" /></label><div className="mt-3 flex items-center justify-between gap-3"><span className="text-xs text-muted-foreground">{content.length}/5000</span><Button onClick={send} disabled={closed || sending || !content.trim()}><Send className="h-4 w-4" />发送回复</Button></div></Card>
  </div>
}
