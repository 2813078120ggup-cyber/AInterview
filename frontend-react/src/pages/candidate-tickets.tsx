import { ArrowLeft, MessageSquareWarning, Plus, RefreshCw, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { TicketEmpty, TicketLoading, TicketStatusBadge, TicketTypeBadge } from '@/components/tickets/ticket-ui'
import { formatTicketDate } from '@/components/tickets/ticket-labels'
import { listMyTickets, type TicketPage, type TicketStatus } from '@/lib/ticket-api'

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已关闭' },
]

export function CandidateTickets() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [status, setStatus] = useState(searchParams.get('status') || '')
  const [keyword, setKeyword] = useState('')
  const [items, setItems] = useState<TicketPage | null>(null)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState('')

  async function load() {
    setBusy(true)
    try {
      setError('')
      setItems(await listMyTickets({ pageNo: 1, pageSize: 50, status, keyword }))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '工单加载失败')
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => { load() }, [status])

  return <div className="space-y-6">
    <header className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
      <div>
        <p className="text-sm font-semibold text-[var(--accent)]">服务与支持</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight md:text-4xl">问题反馈</h1>
        <p className="mt-2 max-w-2xl text-muted-foreground">遇到面试故障、发现产品问题或有功能建议，都可以在这里留下记录并跟进处理进度。</p>
      </div>
      <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
        <Button type="button" variant="secondary" className="min-w-0 px-3 sm:px-5" onClick={() => navigate('/candidate/interviews')}>
          <ArrowLeft className="h-4 w-4 shrink-0" /><span className="truncate">返回面试大厅</span>
        </Button>
        <Button type="button" className="min-w-0 px-3 sm:px-5" onClick={() => navigate('/candidate/tickets/new')}>
          <Plus className="h-4 w-4 shrink-0" /><span className="truncate">提交反馈</span>
        </Button>
      </div>
    </header>

    <Card className="p-4 sm:p-5">
      <div className="grid gap-3 md:grid-cols-[1fr_220px_auto] md:items-center">
        <label className="flex h-12 items-center gap-2 rounded-full border border-border bg-background px-4"><Search className="h-4 w-4 text-muted-foreground" /><span className="sr-only">搜索工单</span><input value={keyword} onChange={event => setKeyword(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') load() }} placeholder="搜索工单号或标题" className="min-w-0 flex-1 bg-transparent text-sm outline-none" /></label>
        <ResponsiveSelect ariaLabel="工单状态" value={status} options={statusOptions} onValueChange={setStatus} />
        <Button variant="secondary" onClick={load} disabled={busy}><RefreshCw className={`h-4 w-4 ${busy ? 'animate-spin' : ''}`} />刷新</Button>
      </div>
    </Card>

    {error && <div className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</div>}
    {busy && !items ? <TicketLoading /> : !items?.records.length ? <TicketEmpty title="还没有反馈工单" content="提交第一个问题反馈，管理员会在这里跟进处理。" action={<Link to="/candidate/tickets/new"><Button><Plus className="h-4 w-4" />提交反馈</Button></Link>} /> : <div className="grid gap-4">
      {items.records.map(item => <Link key={item.id} to={`/candidate/tickets/${item.id}`} className="group block">
        <Card className="p-5 transition group-hover:border-[var(--accent)]/50 sm:p-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><TicketTypeBadge type={item.ticketType} /><TicketStatusBadge status={item.status as TicketStatus} />{item.unreadCount > 0 && <Badge tone="danger">{item.unreadCount} 条新回复</Badge>}</div><h2 className="mt-3 truncate text-lg font-bold group-hover:text-[var(--accent)]">{item.title || '未命名草稿'}</h2><p className="mt-1 text-xs text-muted-foreground">{item.ticketNo || '尚未提交'} · 最后更新 {formatTicketDate(item.lastActivityAt)}</p></div>
            <span className="shrink-0 text-sm font-semibold text-muted-foreground group-hover:text-foreground">查看详情 →</span>
          </div>
          <div className="mt-5 flex flex-wrap gap-x-6 gap-y-2 border-t border-border/70 pt-4 text-xs text-muted-foreground"><span className="inline-flex items-center gap-1"><MessageSquareWarning className="h-3.5 w-3.5" />{item.assigneeName ? `处理人：${item.assigneeName}` : '等待管理员受理'}</span><span>创建于 {formatTicketDate(item.createdAt)}</span></div>
        </Card>
      </Link>)}
    </div>}
  </div>
}
