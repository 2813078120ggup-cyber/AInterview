import { BriefcaseBusiness, ChevronLeft, ChevronRight, FileSearch, Filter, RefreshCw, Search, Table2, Trello, UserRound } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'
import { applicationStatusMeta, appendQuery, formatDateTime, type ApplicationStatus, type JobApplication, type RecruitmentJob, type PageResult } from '@/lib/recruitment'

const TABLE_PAGE_SIZE = 10
const BOARD_PAGE_SIZE = 6
const stages: ApplicationStatus[] = ['SUBMITTED', 'AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'UNDER_REVIEW', 'OFFLINE_INTERVIEW', 'HIRED', 'REJECTED']

const statusOptions = [{ value: '', label: '全部阶段' }, ...stages.map(status => ({ value: status, label: applicationStatusMeta[status].label }))]
const interviewStatusOptions = [
  { value: '', label: '全部面试状态' },
  { value: 'NONE', label: '尚未安排面试' },
  { value: 'AI_PENDING', label: 'AI 面试待开始' },
  { value: 'AI_IN_PROGRESS', label: 'AI 面试中' },
  { value: 'AI_COMPLETED', label: 'AI 面试已完成' },
  { value: 'OFFLINE_SCHEDULED', label: '线下面试已安排' },
  { value: 'OFFLINE_COMPLETED', label: '线下面试已完成' },
  { value: 'OFFLINE_CANCELLED', label: '线下面试已取消' },
]
const sortOptions = [
  { value: 'LATEST', label: '最新投递' },
  { value: 'MATCH_SCORE', label: '匹配度最高' },
  { value: 'OLDEST_UNPROCESSED', label: '最长未处理' },
]
const inputClass = 'h-11 w-full rounded-2xl border border-border bg-background px-3 text-sm outline-none focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--brand)]/20'

type ApplicationFilter = {
  keyword: string
  status: string
  positionId: string
  minMatchScore: string
  maxMatchScore: string
  from: string
  to: string
  interviewStatus: string
  sort: string
}

type BoardResult = Record<string, PageResult<JobApplication> | undefined>
type BoardError = Record<string, string | undefined>

function readPage(value: string | null) {
  const parsed = Number(value ?? '1')
  return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : 1
}

function readFilters(searchParams: URLSearchParams): ApplicationFilter {
  return {
    keyword: searchParams.get('keyword') ?? '',
    status: searchParams.get('status') ?? '',
    positionId: searchParams.get('positionId') ?? '',
    minMatchScore: searchParams.get('minMatchScore') ?? '',
    maxMatchScore: searchParams.get('maxMatchScore') ?? '',
    from: searchParams.get('from') ?? '',
    to: searchParams.get('to') ?? '',
    interviewStatus: searchParams.get('interviewStatus') ?? '',
    sort: searchParams.get('sort') || 'LATEST',
  }
}

function buildQuery(filters: ApplicationFilter, pageNo: number, pageSize: number, status?: ApplicationStatus) {
  return appendQuery({
    pageNo,
    pageSize,
    keyword: filters.keyword,
    status: status ?? filters.status,
    positionId: filters.positionId,
    minMatchScore: filters.minMatchScore,
    maxMatchScore: filters.maxMatchScore,
    submittedFrom: filters.from ? `${filters.from}T00:00:00` : undefined,
    submittedTo: filters.to ? `${filters.to}T23:59:59` : undefined,
    interviewStatus: filters.interviewStatus,
    sort: filters.sort,
  })
}

function statusMeta(status: string) {
  return applicationStatusMeta[status as ApplicationStatus] ?? { label: '处理中', tone: 'default' as const }
}

function interviewStatusMeta(status?: string) {
  const labels: Record<string, { label: string; tone: 'default' | 'success' | 'warning' | 'danger' | 'info' }> = {
    NONE: { label: '未安排面试', tone: 'default' },
    AI_PENDING: { label: 'AI 待开始', tone: 'warning' },
    AI_IN_PROGRESS: { label: 'AI 面试中', tone: 'info' },
    AI_COMPLETED: { label: 'AI 已完成', tone: 'success' },
    AI_CANCELLED: { label: 'AI 已取消', tone: 'danger' },
    OFFLINE_SCHEDULED: { label: '线下已安排', tone: 'warning' },
    OFFLINE_COMPLETED: { label: '线下已完成', tone: 'success' },
    OFFLINE_CANCELLED: { label: '线下已取消', tone: 'danger' },
  }
  return labels[status || 'NONE'] ?? { label: status || '未安排面试', tone: 'default' as const }
}

function Pagination({ pageNo, pageSize, total, onChange }: { pageNo: number; pageSize: number; total: number; onChange: (page: number) => void }) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  return <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4 text-sm text-muted-foreground">
    <span>共 {total} 位候选人 · 第 {pageNo} / {totalPages} 页</span>
    <div className="flex items-center gap-2">
      <Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo <= 1} onClick={() => onChange(pageNo - 1)}><ChevronLeft className="h-4 w-4" />上一页</Button>
      <Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo >= totalPages} onClick={() => onChange(pageNo + 1)}>下一页<ChevronRight className="h-4 w-4" /></Button>
    </div>
  </div>
}

function ApplicationRow({ item, onOpen }: { item: JobApplication; onOpen: () => void }) {
  const stage = statusMeta(item.status)
  const interview = interviewStatusMeta(item.interviewStatus)
  return <tr className="border-t border-border align-top transition-colors hover:bg-muted/25">
    <td className="px-4 py-4"><button type="button" onClick={onOpen} className="flex min-w-0 items-center gap-3 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]"><span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><UserRound className="h-4 w-4" /></span><span className="min-w-0"><strong className="block truncate">{item.candidateName}</strong><span className="mt-1 block truncate text-xs text-muted-foreground">{item.applicationNo}</span></span></button></td>
    <td className="max-w-44 px-4 py-4"><span className="block truncate font-semibold">{item.positionName}</span><span className="mt-1 block text-xs text-muted-foreground">{item.companyName}</span></td>
    <td className="px-4 py-4"><strong className="text-[var(--accent)]">{item.matchScore == null ? '待评估' : `${item.matchScore}%`}</strong></td>
    <td className="px-4 py-4"><Badge tone={stage.tone}>{stage.label}</Badge><Badge className="mt-2" tone={interview.tone}>{interview.label}</Badge></td>
    <td className="max-w-48 px-4 py-4"><span className="block truncate text-sm">{item.nextStep || '查看申请详情'}</span><span className="mt-1 block text-xs text-muted-foreground">{formatDateTime(item.recentActivityAt || item.updatedAt)}</span></td>
    <td className="px-4 py-4 text-right"><Button type="button" variant="secondary" className="h-9 px-3" onClick={onOpen}>查看<ChevronRight className="h-4 w-4" /></Button></td>
  </tr>
}

function ApplicationCard({ item, onOpen }: { item: JobApplication; onOpen: () => void }) {
  const stage = statusMeta(item.status)
  const interview = interviewStatusMeta(item.interviewStatus)
  return <button type="button" onClick={onOpen} className="w-full text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]"><Card className="p-4 transition-colors hover:border-[var(--accent)]/70"><div className="flex items-start justify-between gap-3"><span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><UserRound className="h-5 w-5" /></span><div className="flex flex-wrap justify-end gap-2"><Badge tone={stage.tone}>{stage.label}</Badge><Badge tone={interview.tone}>{interview.label}</Badge></div></div><h2 className="mt-4 text-lg font-black">{item.candidateName}</h2><p className="mt-1 text-sm text-muted-foreground">{item.positionName}</p><div className="mt-4 grid grid-cols-2 gap-3 border-t border-border pt-3 text-xs"><span className="text-muted-foreground">匹配度 <strong className="text-[var(--accent)]">{item.matchScore == null ? '待评估' : `${item.matchScore}%`}</strong></span><span className="text-right text-muted-foreground">{formatDateTime(item.recentActivityAt || item.updatedAt)}</span></div><p className="mt-3 rounded-2xl bg-muted px-3 py-2 text-xs text-muted-foreground">下一步：{item.nextStep || '查看申请详情'}</p></Card></button>
}

function ApplicationSkeleton({ board = false }: { board?: boolean }) {
  return <div className={board ? 'space-y-3' : 'space-y-3'} role="status" aria-label="正在加载申请记录" aria-busy="true">{Array.from({ length: board ? 3 : 5 }, (_, index) => <div key={index} aria-hidden="true" className="min-h-28 animate-pulse rounded-[24px] border border-border bg-surface p-5"><div className="h-4 w-2/5 rounded bg-muted" /><div className="mt-4 h-4 w-3/5 rounded bg-muted" /><div className="mt-5 h-3 w-1/3 rounded bg-muted" /></div>)}</div>
}

export function CompanyApplications() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const filters = useMemo(() => readFilters(searchParams), [searchParams])
  const view = searchParams.get('view') === 'board' ? 'board' : 'table'
  const pageNo = readPage(searchParams.get('pageNo'))
  const [keywordInput, setKeywordInput] = useState(filters.keyword)
  const [items, setItems] = useState<PageResult<JobApplication>>()
  const [boardItems, setBoardItems] = useState<BoardResult>({})
  const [boardErrors, setBoardErrors] = useState<BoardError>({})
  const [positions, setPositions] = useState<RecruitmentJob[]>([])
  const [positionsError, setPositionsError] = useState('')
  const [loading, setLoading] = useState(false)
  const [boardLoading, setBoardLoading] = useState(false)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => setKeywordInput(filters.keyword), [filters.keyword])

  function updateParams(changes: Record<string, string | number | undefined>, resetPage = true) {
    setSearchParams(current => {
      const next = new URLSearchParams(current)
      Object.entries(changes).forEach(([key, value]) => {
        if (value === undefined || String(value).trim() === '') next.delete(key)
        else next.set(key, String(value))
      })
      if (resetPage && !Object.prototype.hasOwnProperty.call(changes, 'pageNo')) next.set('pageNo', '1')
      return next
    }, { replace: true })
  }

  useEffect(() => {
    let active = true
    request<PageResult<RecruitmentJob>>('/v1/company/recruitment/positions?pageNo=1&pageSize=100')
      .then(result => active && setPositions(result.records))
      .catch(reason => active && setPositionsError(reason instanceof Error ? reason.message : '岗位筛选加载失败'))
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (view !== 'table') return
    let active = true
    setLoading(true)
    setError('')
    request<PageResult<JobApplication>>(`/v1/company/recruitment/applications${buildQuery(filters, pageNo, TABLE_PAGE_SIZE)}`)
      .then(result => active && setItems(result))
      .catch(reason => active && setError(reason instanceof Error ? reason.message : '候选人流程加载失败'))
      .finally(() => active && setLoading(false))
    return () => { active = false }
  }, [filters, pageNo, reloadKey, view])

  useEffect(() => {
    if (view !== 'board') return
    let active = true
    setBoardLoading(true)
    setBoardItems({})
    setBoardErrors({})
    void Promise.all(stages.map(async stage => {
      if (filters.status && filters.status !== stage) {
        return { stage, result: { records: [], total: 0, pageNo, pageSize: BOARD_PAGE_SIZE } as PageResult<JobApplication> }
      }
      try {
        const result = await request<PageResult<JobApplication>>(`/v1/company/recruitment/applications${buildQuery(filters, pageNo, BOARD_PAGE_SIZE, stage)}`)
        return { stage, result }
      } catch (reason) {
        return { stage, error: reason instanceof Error ? reason.message : '该阶段加载失败' }
      }
    })).then(results => {
      if (!active) return
      const nextItems: BoardResult = {}
      const nextErrors: BoardError = {}
      results.forEach(result => {
        if ('result' in result) nextItems[result.stage] = result.result
        else nextErrors[result.stage] = result.error
      })
      setBoardItems(nextItems)
      setBoardErrors(nextErrors)
    }).finally(() => active && setBoardLoading(false))
    return () => { active = false }
  }, [filters, pageNo, reloadKey, view])

  const positionOptions = useMemo(() => [{ value: '', label: '全部岗位' }, ...positions.map(position => ({ value: position.id, label: position.name }))], [positions])
  const activeFilterCount = [filters.keyword, filters.status, filters.positionId, filters.minMatchScore, filters.maxMatchScore, filters.from, filters.to, filters.interviewStatus].filter(Boolean).length

  function clearFilters() {
    setSearchParams(current => {
      const next = new URLSearchParams()
      const currentView = current.get('view')
      if (currentView) next.set('view', currentView)
      next.set('sort', filters.sort || 'LATEST')
      next.set('pageNo', '1')
      return next
    }, { replace: true })
  }

  return <div className="space-y-6">
    <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between"><div><p className="text-sm font-bold text-[var(--accent)]">招聘流程中心</p><h1 className="mt-2 text-3xl font-black tracking-[-.04em]">候选人进展</h1><p className="mt-2 max-w-2xl text-muted-foreground">集中处理新投递、AI 面试、企业评估和线下面试，把每一位候选人的下一步放在眼前。</p></div><div className="flex flex-wrap gap-2"><Button type="button" variant={view === 'table' ? 'primary' : 'secondary'} className="h-10 px-4" onClick={() => updateParams({ view: 'table' })}><Table2 className="h-4 w-4" />表格</Button><Button type="button" variant={view === 'board' ? 'primary' : 'secondary'} className="h-10 px-4" onClick={() => updateParams({ view: 'board' })}><Trello className="h-4 w-4" />阶段看板</Button><Button type="button" variant="secondary" className="h-10 px-4" onClick={() => setReloadKey(value => value + 1)}><RefreshCw className="h-4 w-4" />刷新</Button></div></header>

    <Card className="p-4 sm:p-5"><div className="flex flex-wrap items-center justify-between gap-3"><div className="flex items-center gap-2"><Filter className="h-4 w-4 text-[var(--accent)]" /><h2 className="font-black">筛选与排序</h2>{activeFilterCount > 0 && <Badge tone="info">{activeFilterCount} 项筛选</Badge>}</div><button type="button" onClick={clearFilters} className="text-sm font-semibold text-[var(--accent)] hover:underline">清除筛选</button></div><div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4"><label className="md:col-span-2"><span className="text-xs font-bold text-muted-foreground">搜索候选人、岗位或申请编号</span><span className="mt-2 flex h-11 items-center gap-2 rounded-2xl border border-border bg-background px-3 focus-within:border-[var(--accent)] focus-within:ring-2 focus-within:ring-[var(--brand)]/20"><Search className="h-4 w-4 shrink-0 text-muted-foreground" /><input className="min-w-0 flex-1 bg-transparent text-sm outline-none" value={keywordInput} onChange={event => setKeywordInput(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') updateParams({ keyword: keywordInput.trim() }) }} placeholder="姓名、岗位或申请编号" /></span></label><label><span className="text-xs font-bold text-muted-foreground">岗位</span><ResponsiveSelect ariaLabel="筛选岗位" value={filters.positionId} onValueChange={value => updateParams({ positionId: value })} options={positionOptions} searchable className="mt-2 w-full" /></label><label><span className="text-xs font-bold text-muted-foreground">当前阶段</span><ResponsiveSelect ariaLabel="筛选当前阶段" value={filters.status} onValueChange={value => updateParams({ status: value })} options={statusOptions} className="mt-2 w-full" /></label><label><span className="text-xs font-bold text-muted-foreground">面试状态</span><ResponsiveSelect ariaLabel="筛选面试状态" value={filters.interviewStatus} onValueChange={value => updateParams({ interviewStatus: value })} options={interviewStatusOptions} searchable className="mt-2 w-full" /></label><label><span className="text-xs font-bold text-muted-foreground">排序</span><ResponsiveSelect ariaLabel="选择排序方式" value={filters.sort} onValueChange={value => updateParams({ sort: value })} options={sortOptions} className="mt-2 w-full" /></label><label><span className="text-xs font-bold text-muted-foreground">匹配度下限</span><input className={`mt-2 ${inputClass}`} type="number" min="0" max="100" value={filters.minMatchScore} onChange={event => updateParams({ minMatchScore: event.target.value })} placeholder="0" /></label><label><span className="text-xs font-bold text-muted-foreground">匹配度上限</span><input className={`mt-2 ${inputClass}`} type="number" min="0" max="100" value={filters.maxMatchScore} onChange={event => updateParams({ maxMatchScore: event.target.value })} placeholder="100" /></label><label><span className="text-xs font-bold text-muted-foreground">投递开始日期</span><input className={`mt-2 ${inputClass}`} type="date" value={filters.from} onChange={event => updateParams({ from: event.target.value })} /></label><label><span className="text-xs font-bold text-muted-foreground">投递结束日期</span><input className={`mt-2 ${inputClass}`} type="date" value={filters.to} onChange={event => updateParams({ to: event.target.value })} /></label></div>{positionsError && <p className="mt-3 text-xs text-muted-foreground">岗位筛选暂不可用：{positionsError}</p>}</Card>

    {error && <div role="alert" className="flex items-start justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/70 dark:bg-rose-950/30 dark:text-rose-200"><span>{error}</span><button type="button" className="font-semibold underline" onClick={() => setReloadKey(value => value + 1)}>重试</button></div>}
    {view === 'table' ? <TableView items={items} loading={loading} pageNo={pageNo} onPageChange={page => updateParams({ pageNo: page }, false)} onOpen={id => navigate({ pathname: `/company/applications/${id}`, search: searchParams.toString() ? `?${searchParams.toString()}` : '' })} /> : <BoardView items={boardItems} errors={boardErrors} loading={boardLoading} pageNo={pageNo} onPageChange={page => updateParams({ pageNo: page }, false)} onOpen={id => navigate({ pathname: `/company/applications/${id}`, search: searchParams.toString() ? `?${searchParams.toString()}` : '' })} />}
  </div>
}

function TableView({ items, loading, pageNo, onPageChange, onOpen }: { items?: PageResult<JobApplication>; loading: boolean; pageNo: number; onPageChange: (page: number) => void; onOpen: (id: string) => void }) {
  if (loading && !items) return <ApplicationSkeleton />
  if (!items) return <EmptyState title="暂时无法加载候选人流程" description="请稍后重试，筛选条件会保留。" />
  if (!items.records.length) return <EmptyState title="没有符合条件的候选人" description="可以放宽筛选条件，或等待新的申请进入流程中心。" />
  return <Card className="overflow-hidden p-0"><div className="hidden overflow-x-auto md:block"><table className="w-full table-fixed text-left text-sm"><colgroup><col className="w-[20%]" /><col className="w-[18%]" /><col className="w-[11%]" /><col className="w-[16%]" /><col className="w-[22%]" /><col className="w-[13%]" /></colgroup><thead className="bg-muted/55 text-xs text-muted-foreground"><tr><th className="px-4 py-3">候选人</th><th className="px-4 py-3">岗位</th><th className="px-4 py-3">匹配度</th><th className="px-4 py-3">阶段 / 面试</th><th className="px-4 py-3">最近活动 / 下一步</th><th className="px-4 py-3 text-right">操作</th></tr></thead><tbody>{items.records.map(item => <ApplicationRow key={item.id} item={item} onOpen={() => onOpen(item.id)} />)}</tbody></table></div><div className="grid gap-3 p-3 md:hidden">{items.records.map(item => <ApplicationCard key={item.id} item={item} onOpen={() => onOpen(item.id)} />)}</div><div className="p-4 pt-0"><Pagination pageNo={pageNo} pageSize={items.pageSize} total={items.total} onChange={onPageChange} /></div></Card>
}

function BoardView({ items, errors, loading, pageNo, onPageChange, onOpen }: { items: BoardResult; errors: BoardError; loading: boolean; pageNo: number; onPageChange: (page: number) => void; onOpen: (id: string) => void }) {
  if (loading && !Object.keys(items).length && !Object.keys(errors).length) return <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">{stages.slice(0, 4).map(stage => <Card key={stage} className="p-4"><div className="mb-4 h-5 w-2/5 animate-pulse rounded bg-muted" /><ApplicationSkeleton board /></Card>)}</div>
  const canNext = stages.some(stage => { const result = items[stage]; return Boolean(result && result.total > pageNo * result.pageSize) })
  return <div className="space-y-4"><div className="grid gap-4 xl:grid-cols-4">{stages.map(stage => { const result = items[stage]; const meta = statusMeta(stage); return <Card key={stage} className="min-w-0 p-3 sm:p-4"><header className="flex items-center justify-between gap-3 border-b border-border pb-3"><div className="flex min-w-0 items-center gap-2"><span className={`h-2.5 w-2.5 shrink-0 rounded-full ${stage === 'REJECTED' ? 'bg-rose-500' : stage === 'HIRED' ? 'bg-emerald-500' : stage === 'AI_INTERVIEWING' ? 'bg-[var(--accent)]' : 'bg-[var(--warning-foreground)]'}`} /><h2 className="truncate font-black">{meta.label}</h2></div><Badge tone={meta.tone}>{result?.total ?? '—'}</Badge></header>{errors[stage] ? <p role="alert" className="mt-3 rounded-2xl bg-rose-50 p-3 text-xs leading-5 text-rose-700 dark:bg-rose-950/30 dark:text-rose-200">{errors[stage]}</p> : result && result.records.length ? <div className="mt-3 space-y-3">{result.records.map(item => <button key={item.id} type="button" onClick={() => onOpen(item.id)} className="w-full rounded-2xl border border-border bg-background p-3 text-left transition hover:border-[var(--accent)]/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]"><div className="flex items-start justify-between gap-2"><span className="min-w-0 truncate font-bold">{item.candidateName}</span><strong className="shrink-0 text-sm text-[var(--accent)]">{item.matchScore == null ? '—' : `${item.matchScore}%`}</strong></div><p className="mt-1 truncate text-xs text-muted-foreground">{item.positionName}</p><p className="mt-3 text-xs text-muted-foreground">{item.nextStep || '查看申请详情'}</p><p className="mt-2 text-[11px] text-muted-foreground">{formatDateTime(item.recentActivityAt || item.updatedAt)}</p></button>)}</div> : <div className="grid min-h-28 place-items-center py-6 text-center text-xs text-muted-foreground"><FileSearch className="mb-2 h-5 w-5" />暂无候选人</div>}</Card> })}</div><div className="flex justify-end"><Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo <= 1} onClick={() => onPageChange(pageNo - 1)}><ChevronLeft className="h-4 w-4" />上一组</Button><span className="px-3 py-2 text-sm text-muted-foreground">第 {pageNo} 组</span><Button type="button" variant="secondary" className="h-9 px-3" disabled={!canNext} onClick={() => onPageChange(pageNo + 1)}>下一组<ChevronRight className="h-4 w-4" /></Button></div></div>
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return <Card className="grid min-h-56 place-items-center text-center"><div><BriefcaseBusiness className="mx-auto h-8 w-8 text-muted-foreground" /><h2 className="mt-3 font-bold">{title}</h2><p className="mt-1 text-sm text-muted-foreground">{description}</p></div></Card>
}
