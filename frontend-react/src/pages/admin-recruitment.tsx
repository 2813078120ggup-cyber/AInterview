import { AlertTriangle, BriefcaseBusiness, ChevronLeft, ChevronRight, Clock3, Loader2, Radar, RefreshCw, Search, TimerReset } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AdminRowActionLink } from '@/components/admin/admin-row-actions'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'
import type { AdminRecruitmentApplication, AdminRecruitmentSummary, AdminRecruitmentTask } from '@/lib/admin'

type Page<T> = { records: T[]; total: number; pageNo: number; pageSize: number }

const stages = [
  { value: '', label: '全部阶段' },
  { value: 'SUBMITTED', label: '已投递' },
  { value: 'AI_INTERVIEW_PENDING', label: '待 AI 面试' },
  { value: 'AI_INTERVIEWING', label: 'AI 面试中' },
  { value: 'UNDER_REVIEW', label: '企业评估中' },
  { value: 'OFFLINE_INTERVIEW', label: '线下面试' },
  { value: 'REJECTED', label: '未通过' },
  { value: 'HIRED', label: '已录用' },
]

function formatDate(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function taskTone(status?: string | null): 'default' | 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'info'
  if (status === 'PENDING') return 'warning'
  if (status === 'SUCCESS') return 'success'
  return 'default'
}

function taskLabel(task?: AdminRecruitmentTask | null) {
  if (!task) return '未创建'
  return task.status === 'FAILED' ? '失败' : task.status === 'PENDING' ? '排队中' : task.status === 'RUNNING' ? '执行中' : task.status === 'SUCCESS' ? '成功' : task.status
}

export function AdminRecruitment() {
  const [searchParams, setSearchParams] = useSearchParams()
  const pageNo = Math.max(1, Number(searchParams.get('pageNo') ?? '1') || 1)
  const pageSize = 12
  const companyKeyword = searchParams.get('companyKeyword') ?? ''
  const positionKeyword = searchParams.get('positionKeyword') ?? ''
  const keyword = searchParams.get('keyword') ?? ''
  const status = searchParams.get('status') ?? ''
  const from = searchParams.get('from') ?? ''
  const to = searchParams.get('to') ?? ''
  const staleOnly = searchParams.get('staleOnly') === 'true'
  const staleDays = Number(searchParams.get('staleDays') ?? '3') || 3
  const [draft, setDraft] = useState({ companyKeyword, positionKeyword, keyword, from, to })
  const [page, setPage] = useState<Page<AdminRecruitmentApplication>>({ records: [], total: 0, pageNo, pageSize })
  const [summary, setSummary] = useState<AdminRecruitmentSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retrying, setRetrying] = useState<string | null>(null)

  useEffect(() => setDraft({ companyKeyword, positionKeyword, keyword, from, to }), [companyKeyword, positionKeyword, keyword, from, to])

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize), staleDays: String(staleDays) })
    if (companyKeyword) params.set('companyKeyword', companyKeyword)
    if (positionKeyword) params.set('positionKeyword', positionKeyword)
    if (keyword) params.set('keyword', keyword)
    if (status) params.set('status', status)
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    if (staleOnly) params.set('staleOnly', 'true')
    try {
      const [nextPage, nextSummary] = await Promise.all([
        request<Page<AdminRecruitmentApplication>>(`/v1/admin/recruitment/applications?${params.toString()}`),
        request<AdminRecruitmentSummary>(`/v1/admin/recruitment/summary?${params.toString()}`),
      ])
      setPage(nextPage)
      setSummary(nextSummary)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '招聘运营数据加载失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }, [companyKeyword, from, keyword, pageNo, positionKeyword, staleDays, staleOnly, status, to])

  useEffect(() => { void load() }, [load])

  function updateQuery(next: Record<string, string | boolean | number | undefined>) {
    const nextParams = new URLSearchParams(searchParams)
    Object.entries(next).forEach(([key, value]) => {
      if (value === undefined || value === '' || value === false) nextParams.delete(key)
      else nextParams.set(key, String(value))
    })
    nextParams.set('pageNo', '1')
    setSearchParams(nextParams)
  }

  async function retry(task: AdminRecruitmentTask) {
    if (!task.retryable || !window.confirm('确认重试这个技术任务吗？该操作不会改变申请阶段或企业的录用决定。')) return
    setRetrying(task.id)
    try {
      await request(`/v1/admin/recruitment/tasks/${task.id}/retry`, { method: 'POST', body: JSON.stringify({ confirm: true }) })
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '技术任务重试失败，请稍后重试。')
    } finally {
      setRetrying(null)
    }
  }

  const totalPages = Math.max(1, Math.ceil(page.total / pageSize))
  const maxFunnel = Math.max(1, ...(summary?.funnel.map(item => item.count) ?? [1]))

  return <div className="space-y-6">
    <header className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
      <div className="min-w-0">
        <div className="flex items-center gap-2 text-sm font-semibold text-[var(--accent)]"><Radar className="h-4 w-4" />平台异常定位</div>
        <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">招聘运营</h1>
        <p className="mt-3 max-w-3xl text-muted-foreground">跨企业观察申请链路、AI 任务、面试与报告状态。这里是平台观测台，不替企业做录用或淘汰决定。</p>
      </div>
      <Button type="button" variant="secondary" onClick={() => void load()} disabled={loading}><RefreshCw className="h-4 w-4" />刷新观测</Button>
    </header>

    {error && <Card className="border-[var(--danger)]/30 bg-[var(--danger)]/5 text-sm text-[var(--danger)]">{error}</Card>}

    <section className="grid gap-4 md:grid-cols-[1.3fr_1fr_1fr]">
      <Card className="relative overflow-hidden border-[var(--accent)]/30 bg-[var(--accent-soft)]/45"><div className="absolute right-5 top-5 grid h-10 w-10 place-items-center rounded-2xl bg-surface text-[var(--accent)]"><AlertTriangle className="h-5 w-5" /></div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">需要平台关注</p><strong className="mt-3 block text-4xl tabular-nums">{loading ? '…' : summary?.staleCount ?? 0}</strong><p className="mt-2 max-w-xs text-sm text-muted-foreground">超过 {summary?.staleDays ?? staleDays} 天没有推进的非终态申请</p></Card>
      <Card><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">符合条件申请</p><strong className="mt-3 block text-3xl tabular-nums">{loading ? '…' : page.total}</strong><p className="mt-2 text-sm text-muted-foreground">分页由数据库执行</p></Card>
      <Card><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">观测时间</p><strong className="mt-3 block text-lg tabular-nums">{formatDate(summary?.generatedAt)}</strong><p className="mt-2 text-sm text-muted-foreground">仅展示脱敏关联状态</p></Card>
    </section>

    <Card>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">跨企业招聘漏斗</p><p className="mt-2 text-sm text-muted-foreground">按当前企业、岗位、阶段、时间筛选条件聚合。</p></div><Badge tone="info">平台只读</Badge></div>
      <div className="mt-6 grid gap-3 md:grid-cols-7">{summary?.funnel.map(item => <div key={item.status} className="min-w-0"><div className="flex items-baseline justify-between gap-2"><span className="truncate text-xs text-muted-foreground">{item.label}</span><strong className="tabular-nums">{item.count}</strong></div><div className="mt-2 h-2 overflow-hidden rounded-full bg-muted"><div className="h-full rounded-full bg-[var(--accent)] transition-all" style={{ width: `${Math.max(item.count ? 8 : 0, item.count / maxFunnel * 100)}%` }} /></div></div>)}</div>
    </Card>

    <Card className="overflow-hidden p-0">
      <form className="grid gap-3 border-b border-border p-4 md:grid-cols-[1fr_1fr_1.2fr_150px_150px] md:p-5" onSubmit={event => { event.preventDefault(); updateQuery(draft) }}>
        <label className="flex h-11 min-w-0 items-center gap-2 rounded-full border border-border bg-background px-4"><Search className="h-4 w-4 shrink-0 text-muted-foreground" /><span className="sr-only">搜索申请</span><input value={draft.keyword} onChange={event => setDraft({ ...draft, keyword: event.target.value })} className="min-w-0 flex-1 bg-transparent text-sm outline-none" placeholder="申请编号、候选人账号或姓名" /></label>
        <input value={draft.companyKeyword} onChange={event => setDraft({ ...draft, companyKeyword: event.target.value })} className="h-11 rounded-full border border-border bg-background px-4 text-sm outline-none focus:border-[var(--accent)]" placeholder="企业名称或编码" aria-label="企业名称或编码" />
        <input value={draft.positionKeyword} onChange={event => setDraft({ ...draft, positionKeyword: event.target.value })} className="h-11 rounded-full border border-border bg-background px-4 text-sm outline-none focus:border-[var(--accent)]" placeholder="岗位名称或编码" aria-label="岗位名称或编码" />
        <ResponsiveSelect ariaLabel="申请阶段" value={status} onValueChange={value => updateQuery({ status: value })} options={stages} />
        <Button type="submit" variant="secondary" className="h-11"><Search className="h-4 w-4" />筛选</Button>
        <label className="flex h-11 items-center gap-2 rounded-full border border-border bg-background px-4 text-sm md:col-span-2"><span className="text-xs text-muted-foreground">提交时间</span><input type="date" value={draft.from} onChange={event => setDraft({ ...draft, from: event.target.value })} className="min-w-0 flex-1 bg-transparent outline-none" aria-label="开始日期" /><span className="text-muted-foreground">—</span><input type="date" value={draft.to} onChange={event => setDraft({ ...draft, to: event.target.value })} className="min-w-0 flex-1 bg-transparent outline-none" aria-label="结束日期" /></label>
        <label className="flex h-11 items-center gap-2 rounded-full border border-border bg-background px-4 text-sm"><input type="checkbox" checked={staleOnly} onChange={event => updateQuery({ staleOnly: event.target.checked })} className="h-4 w-4 accent-[var(--accent)]" />只看未推进</label>
        <ResponsiveSelect ariaLabel="未推进阈值" value={String(staleDays)} onValueChange={value => updateQuery({ staleDays: Number(value) })} options={[{ value: '3', label: '超过 3 天' }, { value: '7', label: '超过 7 天' }, { value: '14', label: '超过 14 天' }]} />
      </form>
      {loading ? <div className="flex items-center justify-center gap-2 p-16 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" />正在加载跨企业申请观测…</div> : <>
        <table className="mobile-card-table text-left text-sm"><thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground"><tr><th className="px-5 py-4">申请 / 关联</th><th className="px-5 py-4">阶段</th><th className="px-5 py-4">匹配任务</th><th className="px-5 py-4">面试 / 报告</th><th className="px-5 py-4">下一步观测</th><th className="px-5 py-4 text-right">详情</th></tr></thead><tbody>{page.records.map(item => <tr key={item.id} className={`border-b border-border/70 last:border-0 hover:bg-muted/30 ${item.stale ? 'bg-[var(--warning)]/10' : ''}`}>
          <td data-label="申请 / 关联" className="px-5 py-4"><Link to={`/admin/recruitment/applications/${item.id}`} state={{ from: window.location.search }} className="block min-w-0 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]"><strong className="block truncate">{item.candidate.name}</strong><span className="mt-1 block truncate text-xs text-muted-foreground">{item.company.name} · {item.position.name}</span><span className="mt-1 block text-xs text-muted-foreground">{item.applicationNo} · 投递 {formatDate(item.submittedAt)}</span></Link></td>
          <td data-label="阶段" className="px-5 py-4"><Badge tone={item.stale ? 'warning' : item.status === 'REJECTED' ? 'danger' : item.status === 'HIRED' ? 'success' : 'default'}>{item.statusLabel}</Badge><span className="mt-2 block text-xs text-muted-foreground">{item.matchScore == null ? '未形成匹配分' : `匹配 ${item.matchScore}`}</span></td>
          <td data-label="匹配任务" className="px-5 py-4">{item.matchTask ? <div className="flex flex-wrap items-center gap-2"><Badge tone={taskTone(item.matchTask.status)}>{taskLabel(item.matchTask)}</Badge>{item.matchTask.retryable && <Button type="button" variant="secondary" className="h-8 px-3 text-xs" onClick={() => void retry(item.matchTask!)} disabled={retrying === item.matchTask.id}>{retrying === item.matchTask.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <TimerReset className="h-3.5 w-3.5" />}重试</Button>}</div> : <span className="text-xs text-muted-foreground">未创建</span>}</td>
          <td data-label="面试 / 报告" className="px-5 py-4"><div className="flex flex-wrap gap-2">{item.interview ? <Badge tone={item.interview.status === 7 ? 'danger' : item.interview.status === 6 ? 'success' : 'info'}>{item.interview.status === 7 ? '面试失败' : item.interview.status === 6 ? '报告就绪' : item.interview.status === 5 ? '报告生成中' : '面试已关联'}</Badge> : <span className="text-xs text-muted-foreground">无面试</span>}{item.interview?.reportTask && <Badge tone={taskTone(item.interview.reportTask.status)}>报告 {taskLabel(item.interview.reportTask)}</Badge>}</div></td>
          <td data-label="下一步观测" className="px-5 py-4"><span className="flex items-center gap-2 text-xs text-muted-foreground">{item.stale ? <Clock3 className="h-3.5 w-3.5 text-[var(--accent)]" /> : <BriefcaseBusiness className="h-3.5 w-3.5" />}{item.nextAction}</span></td>
          <td data-label="详情" className="px-5 py-4 text-right"><AdminRowActionLink to={`/admin/recruitment/applications/${item.id}`} state={{ from: window.location.search }} /></td>
        </tr>)}{!page.records.length && <tr><td data-mobile-full colSpan={6} className="p-14 text-center text-muted-foreground">当前筛选下没有招聘申请。平台运营页不会创建或修改业务决定。</td></tr>}</tbody></table>
        <div className="flex flex-col gap-3 border-t border-border px-5 py-4 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between"><span>第 {pageNo} / {totalPages} 页 · 共 {page.total} 份申请</span><div className="flex gap-2"><Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo <= 1} onClick={() => updateQuery({ pageNo: pageNo - 1 })}><ChevronLeft className="h-4 w-4" />上一页</Button><Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo >= totalPages} onClick={() => updateQuery({ pageNo: pageNo + 1 })}>下一页<ChevronRight className="h-4 w-4" /></Button></div></div>
      </>}
    </Card>
  </div>
}
