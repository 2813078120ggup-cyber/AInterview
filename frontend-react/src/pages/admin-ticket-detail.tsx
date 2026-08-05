import { ArrowLeft, Check, Loader2, MessageCircle, Send, UserRound } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AdminConfirmDialog } from '@/components/admin-confirm-dialog'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { ActivityTimeline, AttachmentGrid, TicketLoading, TicketStatusBadge, TicketTypeBadge } from '@/components/tickets/ticket-ui'
import { formatTicketDate } from '@/components/tickets/ticket-labels'
import { assignTicket, changeTicketStatus, getTicket, listActivities, listAssignees, markTicketRead, sendTicketMessage, type Assignee, type TicketActivity, type TicketDetail, type TicketStatus } from '@/lib/ticket-api'

export function AdminTicketDetail() {
  const { id = '' } = useParams()
  const [detail, setDetail] = useState<TicketDetail | null>(null)
  const [activities, setActivities] = useState<TicketActivity[]>([])
  const [assignees, setAssignees] = useState<Assignee[]>([])
  const [message, setMessage] = useState('')
  const [resolution, setResolution] = useState('')
  const [statusTarget, setStatusTarget] = useState<TicketStatus>()
  const [busy, setBusy] = useState(true)
  const [sending, setSending] = useState(false)
  const [actionBusy, setActionBusy] = useState('')
  const [error, setError] = useState('')
  const latestId = useRef('')

  async function load() {
    try { const result = await getTicket(id); setDetail(result); setActivities(result.activities); latestId.current = result.activities.at(-1)?.id || ''; await markTicketRead(id, latestId.current || undefined); setError('') }
    catch (reason) { setError(reason instanceof Error ? reason.message : '工单加载失败') }
    finally { setBusy(false) }
  }
  useEffect(() => { load(); listAssignees().then(setAssignees).catch(() => setAssignees([])) }, [id])
  useEffect(() => {
    if (!detail || detail.ticket.status === 'CLOSED') return
    const timer = window.setInterval(async () => { try { const next = await listActivities(id, latestId.current || undefined); if (!next.length) return; setActivities(previous => [...previous, ...next]); latestId.current = next.at(-1)?.id || latestId.current; await markTicketRead(id, latestId.current) } catch { /* retry next interval */ } }, 3000)
    return () => window.clearInterval(timer)
  }, [id, detail?.ticket.status])

  async function assign(value: string) {
    if (!detail || actionBusy) return
    setActionBusy('assign')
    try {
      setDetail(await assignTicket(id, value || null, detail.version))
      const next = (await listActivities(id)).slice(-200)
      setActivities(next)
      latestId.current = next.at(-1)?.id || latestId.current
    }
    catch (reason) { setError(reason instanceof Error ? reason.message : '转派失败') }
    finally { setActionBusy('') }
  }
  async function status(target: TicketStatus, resolutionText = '') {
    if (!detail || actionBusy) return
    setActionBusy(target)
    try {
      setDetail(await changeTicketStatus(id, target, resolutionText, detail.version))
      setResolution(resolutionText)
      const next = (await listActivities(id)).slice(-200)
      setActivities(next)
      latestId.current = next.at(-1)?.id || latestId.current
    }
    catch (reason) { setError(reason instanceof Error ? reason.message : '状态更新失败') }
    finally { setActionBusy('') }
  }
  function requestStatus(target: TicketStatus) {
    if (!detail || detail.ticket.status === 'CLOSED' || actionBusy) return
    if (target === 'RESOLVED' || target === 'CLOSED') {
      setResolution(detail.resolution || '')
      setStatusTarget(target)
      return
    }
    void status(target)
  }
  async function confirmStatus() {
    if (!statusTarget) return
    const target = statusTarget
    setStatusTarget(undefined)
    await status(target, resolution.trim())
  }
  async function send() {
    if (!message.trim() || !detail || detail.ticket.status === 'CLOSED') return
    setSending(true)
    try { const item = await sendTicketMessage(id, message.trim(), crypto.randomUUID()); setActivities(previous => [...previous, item]); latestId.current = item.id; setMessage(''); await markTicketRead(id, item.id) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '回复失败') }
    finally { setSending(false) }
  }

  if (busy) return <TicketLoading />
  if (!detail) return <div className="mx-auto max-w-7xl space-y-4 p-4 sm:p-6 lg:p-10"><Link to="/admin/tickets" className="inline-flex h-9 items-center gap-1 rounded-full px-3 text-sm font-semibold text-muted-foreground transition hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]"><ArrowLeft className="h-4 w-4" />返回工单列表</Link><div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error || '工单不存在'}</div></div>
  const closed = detail.ticket.status === 'CLOSED'
  const assigneeOptions = [{ value: '', label: '暂未分配' }, ...assignees.map(item => ({ value: item.id, label: `${item.realName}（${item.username}）` }))]
  return <div className="mx-auto max-w-7xl space-y-6 p-4 sm:p-6 lg:p-10">
    <Link to="/admin/tickets" className="inline-flex h-9 items-center gap-1 rounded-full px-3 text-sm font-semibold text-muted-foreground transition hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]"><ArrowLeft className="h-4 w-4" />返回工单队列</Link>
    {error && <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</div>}
    <section className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
      <Card className="p-5 sm:p-7"><div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><TicketTypeBadge type={detail.ticket.ticketType} /><TicketStatusBadge status={detail.ticket.status} /></div><h1 className="mt-3 break-words text-2xl font-black">{detail.ticket.title || '未命名草稿'}</h1><p className="mt-2 text-xs text-muted-foreground">{detail.ticket.ticketNo || '草稿未提交'} · {detail.ticket.creatorName} · {formatTicketDate(detail.ticket.createdAt)}</p></div><div className="text-sm text-muted-foreground">最后更新 {formatTicketDate(detail.ticket.lastActivityAt)}</div></div><div className="mt-6 whitespace-pre-wrap break-words rounded-3xl bg-muted/55 p-4 text-sm leading-7 sm:p-5">{detail.description || '暂无问题描述'}</div><AttachmentGrid attachments={detail.attachments} />{detail.resolution && <div className="mt-4 rounded-3xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-7 text-emerald-800"><p className="font-bold">处理说明</p><p className="mt-1 whitespace-pre-wrap">{detail.resolution}</p></div>}</Card>
      <Card className="h-fit space-y-5 p-5"><div><p className="flex items-center gap-2 text-sm font-bold"><UserRound className="h-4 w-4 text-[var(--accent)]" />处理设置</p><p className="mt-1 text-xs leading-5 text-muted-foreground">转派记录会自动写入工单时间线。</p></div><ResponsiveSelect ariaLabel="处理管理员" value={detail.ticket.assigneeId || ''} options={assigneeOptions} onValueChange={value => void assign(value)} searchable disabled={closed || Boolean(actionBusy)} /><div className="grid gap-2"><Button type="button" variant="secondary" className="h-9 w-full" disabled={closed || detail.ticket.status !== 'PENDING' || Boolean(actionBusy)} onClick={() => requestStatus('PROCESSING')}>{actionBusy === 'PROCESSING' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}{actionBusy === 'PROCESSING' ? '处理中…' : '开始处理'}</Button><Button type="button" variant="secondary" className="h-9 w-full" disabled={closed || detail.ticket.status !== 'PROCESSING' || Boolean(actionBusy)} onClick={() => requestStatus('RESOLVED')}>{actionBusy === 'RESOLVED' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}{actionBusy === 'RESOLVED' ? '处理中…' : '标记已解决'}</Button><Button type="button" variant="danger" className="h-9 w-full" disabled={closed || Boolean(actionBusy)} onClick={() => requestStatus('CLOSED')} aria-busy={actionBusy === 'CLOSED'}>{actionBusy === 'CLOSED' ? <Loader2 className="h-4 w-4 animate-spin" /> : null}{actionBusy === 'CLOSED' ? '处理中…' : '关闭工单'}</Button></div><p className="text-xs text-muted-foreground">关闭后双方都不能继续留言或上传附件。</p></Card>
    </section>
    <Card className="p-5 sm:p-7"><div className="mb-5 flex items-center justify-between gap-3"><div><h2 className="flex items-center gap-2 text-xl font-black"><MessageCircle className="h-5 w-5 text-[var(--accent)]" />沟通记录</h2><p className="mt-1 text-sm text-muted-foreground">页面会自动刷新新的候选人回复。</p></div><span className="text-xs text-muted-foreground">{activities.length} 条记录</span></div><ActivityTimeline activities={activities} /></Card>
    <Card className="sticky bottom-3 p-4 sm:p-5"><textarea value={message} onChange={event => setMessage(event.target.value)} disabled={closed || sending} rows={4} maxLength={5000} placeholder={closed ? '该工单已关闭，不能继续留言' : '回复候选人…'} className="w-full resize-y rounded-2xl border border-border bg-background p-4 text-sm leading-6 outline-none focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20 disabled:cursor-not-allowed disabled:bg-muted" /><div className="mt-3 flex items-center justify-between gap-3"><span className="text-xs text-muted-foreground">{message.length}/5000</span><Button type="button" onClick={() => void send()} disabled={closed || sending || !message.trim()} aria-busy={sending}>{sending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}{sending ? '发送中…' : '发送回复'}</Button></div></Card>
    {statusTarget && <AdminConfirmDialog
      title={statusTarget === 'CLOSED' ? '关闭反馈工单' : '标记工单已解决'}
      description={statusTarget === 'CLOSED' ? '关闭后候选人和管理员都不能继续留言或上传附件。' : '标记为已解决后，候选人仍可以反馈问题未解决。'}
      confirmLabel={statusTarget === 'CLOSED' ? '确认关闭' : '确认解决'}
      danger={statusTarget === 'CLOSED'}
      busy={actionBusy === statusTarget}
      onClose={() => { if (!actionBusy) setStatusTarget(undefined) }}
      onConfirm={() => void confirmStatus()}
    >
      <label className="mt-5 block text-sm font-semibold">处理说明<span className="mt-2 block text-xs font-normal text-muted-foreground">可选，内容会写入工单时间线。</span><textarea value={resolution} onChange={event => setResolution(event.target.value)} maxLength={2000} rows={4} className="mt-2 w-full resize-y rounded-2xl border border-border bg-background p-3 text-sm font-normal leading-6 outline-none focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20" placeholder="请输入处理说明" /></label>
    </AdminConfirmDialog>}
  </div>
}
