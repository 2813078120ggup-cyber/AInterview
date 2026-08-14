import { RefreshCw, Search, Wrench } from 'lucide-react'
import { useEffect, useEffectEvent, useState } from 'react'
import { Link } from 'react-router-dom'
import { AdminRowActionLink } from '@/components/admin/admin-row-actions'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { TicketEmpty, TicketLoading, TicketStatusBadge, TicketTypeBadge } from '@/components/tickets/ticket-ui'
import { formatTicketDate } from '@/components/tickets/ticket-labels'
import { listAdminTickets, listAssignees, type Assignee, type TicketPage } from '@/lib/ticket-api'

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已关闭' },
]
const typeOptions = [{ value: '', label: '全部类型' }, { value: 'INTERVIEW_FAILURE', label: '面试故障' }, { value: 'FEATURE_SUGGESTION', label: '功能建议' }, { value: 'BUG_REPORT', label: 'BUG 上报' }]

export function AdminTickets() {
  const [status, setStatus] = useState('')
  const [type, setType] = useState('')
  const [assigneeId, setAssigneeId] = useState('')
  const [keyword, setKeyword] = useState('')
  const [assignees, setAssignees] = useState<Assignee[]>([])
  const [items, setItems] = useState<TicketPage | null>(null)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState('')

  async function load() {
    setBusy(true)
    try { setError(''); setItems(await listAdminTickets({ pageNo: 1, pageSize: 100, status, ticketType: type, assigneeId, keyword })) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '工单加载失败') }
    finally { setBusy(false) }
  }
  async function listAssigneeOptions() { try { setAssignees(await listAssignees()) } catch { setAssignees([]) } }
  const loadEffect = useEffectEvent(load)
  useEffect(() => { listAssigneeOptions() }, [])
  useEffect(() => { void loadEffect() }, [status, type, assigneeId])

  const assigneeOptions = [{ value: '', label: '全部处理人' }, { value: 'me', label: '我负责' }, { value: 'unassigned', label: '未分配' }, ...assignees.map(item => ({ value: item.id, label: item.realName || item.username }))]
  return <div className="space-y-6">
    <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
      <div>
        <p className="text-sm font-semibold text-[var(--accent)]">服务与支持</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">反馈工单</h1>
        <p className="mt-3 max-w-2xl text-muted-foreground">集中查看候选人反馈，分配处理人、回复问题并推进解决状态。</p>
      </div>
    </header>
    <Card className="mt-7 p-0"><div className="grid gap-3 border-b border-border p-5 md:grid-cols-[minmax(0,1fr)_180px_180px_220px_auto] md:items-center"><label className="flex h-12 items-center gap-2 rounded-full border border-border bg-surface px-4"><Search className="h-4 w-4 text-muted-foreground" /><span className="sr-only">搜索工单</span><input value={keyword} onChange={event => setKeyword(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') void load() }} placeholder="搜索工单号、标题或描述" className="min-w-0 flex-1 bg-transparent text-sm outline-none" /></label><ResponsiveSelect ariaLabel="工单状态" value={status} options={statusOptions} onValueChange={setStatus} /><ResponsiveSelect ariaLabel="工单类型" value={type} options={typeOptions} onValueChange={setType} /><ResponsiveSelect ariaLabel="处理人" value={assigneeId} options={assigneeOptions} onValueChange={setAssigneeId} searchable /><Button type="button" variant="secondary" className="h-9 w-full px-4 md:w-auto" onClick={() => void load()} disabled={busy} aria-busy={busy}><RefreshCw className={`h-4 w-4 ${busy ? 'animate-spin' : ''}`} />{busy ? '刷新中' : '刷新'}</Button></div></Card>
    {error && <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"><span className="min-w-0 flex-1">{error}</span><Button type="button" variant="secondary" className="h-9 shrink-0 px-3 text-xs" disabled={busy} onClick={() => void load()}><RefreshCw className={`h-3.5 w-3.5 ${busy ? 'animate-spin' : ''}`} />{busy ? '重试中' : '重试'}</Button></div>}
    {busy && !items ? <div className="mt-5"><TicketLoading /></div> : !items?.records.length ? <div className="mt-5"><TicketEmpty title="暂无匹配工单" content="调整筛选条件后可以继续查看全部反馈。" /></div> : <Card className="mt-5 overflow-hidden p-0"><div className="hidden overflow-x-auto md:block"><table className="w-full min-w-[900px] text-left text-sm"><thead className="bg-muted/55 text-xs text-muted-foreground"><tr><th className="px-5 py-4 font-semibold">工单</th><th className="px-5 py-4 font-semibold">提交人</th><th className="px-5 py-4 font-semibold">状态</th><th className="px-5 py-4 font-semibold">处理人</th><th className="px-5 py-4 font-semibold">最近活动</th><th className="px-5 py-4 text-right font-semibold">操作</th></tr></thead><tbody className="divide-y divide-border">{items.records.map(item => <tr key={item.id} className="transition hover:bg-muted/30"><td className="max-w-[300px] px-5 py-4"><Link to={`/admin/tickets/${item.id}`} className="block rounded-2xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--accent)]"><div className="flex flex-wrap items-center gap-2"><TicketTypeBadge type={item.ticketType} />{item.unreadCount > 0 && <Badge tone="danger">{item.unreadCount} 新</Badge>}</div><p className="mt-2 truncate font-bold hover:text-[var(--accent)]">{item.title || '未命名草稿'}</p><p className="mt-1 text-xs text-muted-foreground">{item.ticketNo || '草稿未提交'}</p></Link></td><td className="px-5 py-4 text-muted-foreground">{item.creatorName}</td><td className="px-5 py-4"><TicketStatusBadge status={item.status} /></td><td className="px-5 py-4 text-muted-foreground">{item.assigneeName || '未分配'}</td><td className="px-5 py-4 text-xs text-muted-foreground">{formatTicketDate(item.lastActivityAt)}</td><td className="px-5 py-4 text-right"><AdminRowActionLink to={`/admin/tickets/${item.id}`} label="处理工单" icon={Wrench} /></td></tr>)}</tbody></table></div><div className="grid gap-3 p-3 md:hidden">{items.records.map(item => <Link key={item.id} to={`/admin/tickets/${item.id}`} className="rounded-3xl border border-border p-4 transition hover:border-[var(--accent)]/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]"><div className="flex flex-wrap items-center gap-2"><TicketTypeBadge type={item.ticketType} /><TicketStatusBadge status={item.status} />{item.unreadCount > 0 && <Badge tone="danger">{item.unreadCount} 条新</Badge>}</div><h2 className="mt-3 font-bold">{item.title || '未命名草稿'}</h2><p className="mt-1 text-xs text-muted-foreground">{item.ticketNo || '草稿未提交'} · {item.creatorName}</p><div className="mt-4 flex justify-between gap-3 border-t border-border pt-3 text-xs text-muted-foreground"><span>{item.assigneeName || '未分配'}</span><span>{formatTicketDate(item.lastActivityAt)}</span></div></Link>)}</div></Card>}
  </div>
}
