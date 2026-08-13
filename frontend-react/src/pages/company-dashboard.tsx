import {
  AlertCircle,
  ArrowRight,
  BriefcaseBusiness,
  Building2,
  CalendarClock,
  ClipboardList,
  Clock3,
  FileWarning,
  MapPin,
  RefreshCw,
  SearchCheck,
  XCircle,
  type LucideIcon,
} from 'lucide-react'
import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import { applicationStatusMeta, formatDateTime, positionStatusMeta, type ApplicationStatus } from '@/lib/recruitment'

type DashboardSummary = {
  companyId: string | number
  companyName: string
  companyShortName?: string
  city?: string
  publishedPositions: number
  draftPositions: number
  totalApplications: number
  pendingApplications: number
  todayInterviews: number
  overdueItems: number
  hiredApplications: number
  averageMatchScore: number
  lastUpdatedAt?: string
}

type DashboardActionItem = {
  actionType: string
  applicationId: string | number
  interviewId?: string | number
  candidateName?: string
  positionName?: string
  status?: string
  matchStatus?: string
  dueAt?: string
  createdAt?: string
}

type DashboardActionGroup = {
  actionType: string
  label: string
  description: string
  count: number
  items: DashboardActionItem[]
}

type ActionCenter = {
  groups: DashboardActionGroup[]
  total: number
  generatedAt?: string
}

type UpcomingInterview = {
  source: string
  interviewId?: string | number
  applicationId: string | number
  candidateName?: string
  positionName?: string
  scheduledAt?: string
  durationMinutes?: number
  status?: string
  location?: string
}

type FunnelStage = {
  status: ApplicationStatus
  label: string
  count: number
  percentage: number
}

type PositionAnalytics = {
  positionId: string | number
  positionName: string
  recruitmentStatus: keyof typeof positionStatusMeta
  applicationCount: number
  pendingCount: number
  hiredCount: number
  averageMatchScore: number
}

type DashboardData = {
  summary?: DashboardSummary
  actions?: ActionCenter
  upcoming?: UpcomingInterview[]
  funnel?: FunnelStage[]
  positions?: PositionAnalytics[]
}

type SectionKey = keyof DashboardData

const sectionKeys: SectionKey[] = ['summary', 'actions', 'upcoming', 'funnel', 'positions']

const actionMeta: Record<string, { icon: LucideIcon }> = {
  NEW_APPLICATION: { icon: ClipboardList },
  MATCH_FAILED: { icon: XCircle },
  AI_INTERVIEW_REVIEW: { icon: SearchCheck },
  REPORT_TIMEOUT: { icon: FileWarning },
  OFFLINE_CONFIRMATION: { icon: CalendarClock },
}

const funnelColors: Record<ApplicationStatus, string> = {
  SUBMITTED: 'bg-[var(--info)]',
  AI_INTERVIEW_PENDING: 'bg-[var(--warning)]',
  AI_INTERVIEWING: 'bg-[var(--accent)]',
  UNDER_REVIEW: 'bg-[var(--primary)]',
  OFFLINE_INTERVIEW: 'bg-[var(--accent)]',
  REJECTED: 'bg-[var(--danger)]',
  HIRED: 'bg-[var(--success)]',
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}

function SectionHeading({ eyebrow, title, description, action }: {
  eyebrow?: string
  title: string
  description?: string
  action?: ReactNode
}) {
  return <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
    <div>
      {eyebrow && <p className="text-xs font-bold uppercase tracking-[.14em] text-[var(--accent)]">{eyebrow}</p>}
      <h2 className="mt-1 text-xl font-black tracking-[-.03em]">{title}</h2>
      {description && <p className="mt-1 text-sm leading-6 text-muted-foreground">{description}</p>}
    </div>
    {action}
  </div>
}

function PanelMessage({ kind, message, onRetry }: { kind: 'loading' | 'empty' | 'error'; message: string; onRetry?: () => void }) {
  if (kind === 'loading') {
    return <div className="flex min-h-28 items-center justify-center text-sm text-muted-foreground"><RefreshCw className="mr-2 h-4 w-4 animate-spin" />正在读取企业数据…</div>
  }
  return <div className="flex min-h-28 flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-border px-4 text-center text-sm text-muted-foreground">
    {kind === 'error' ? <AlertCircle className="h-5 w-5 text-[var(--danger)]" /> : <ClipboardList className="h-5 w-5 text-muted-foreground" />}
    <span>{message}</span>
    {kind === 'error' && onRetry && <Button variant="secondary" className="h-9 px-4 text-xs" onClick={onRetry}>重试</Button>}
  </div>
}

function Metric({ label, value, detail, icon: Icon, tone = 'default' }: {
  label: string
  value: number
  detail: string
  icon: LucideIcon
  tone?: 'default' | 'warning' | 'danger'
}) {
  const toneClass = tone === 'danger' ? 'text-[var(--danger)]' : tone === 'warning' ? 'text-[var(--warning-foreground)]' : 'text-foreground'
  return <div className="border-l border-border pl-4 first:border-l-0 first:pl-0 sm:pl-5">
    <div className="flex items-center gap-2 text-xs font-semibold text-muted-foreground"><Icon className="h-4 w-4 text-[var(--accent)]" />{label}</div>
    <p className={`mt-2 text-3xl font-black tracking-[-.05em] ${toneClass}`}>{value}</p>
    <p className="mt-1 text-xs leading-5 text-muted-foreground">{detail}</p>
  </div>
}

export function CompanyDashboard() {
  const navigate = useNavigate()
  const [data, setData] = useState<DashboardData>({})
  const [errors, setErrors] = useState<Partial<Record<SectionKey, string>>>({})
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)

  const loadDashboard = useCallback(async (initial = false) => {
    if (initial) setLoading(true)
    else setRefreshing(true)

    const results = await Promise.allSettled([
      request<DashboardSummary>('/v1/company/recruitment/dashboard/summary'),
      request<ActionCenter>('/v1/company/recruitment/dashboard/actions'),
      request<UpcomingInterview[]>('/v1/company/recruitment/dashboard/upcoming-interviews'),
      request<FunnelStage[]>('/v1/company/recruitment/analytics/funnel'),
      request<PositionAnalytics[]>('/v1/company/recruitment/analytics/positions'),
    ] as const)

    const nextData: DashboardData = {}
    const nextErrors: Partial<Record<SectionKey, string>> = {}
    const assign = <K extends SectionKey>(key: K, result: PromiseSettledResult<DashboardData[K]>, fallback: string) => {
      if (result.status === 'fulfilled') nextData[key] = result.value
      else nextErrors[key] = errorMessage(result.reason, fallback)
    }
    assign('summary', results[0], '招聘概览暂时不可用')
    assign('actions', results[1], '行动中心暂时不可用')
    assign('upcoming', results[2], '面试安排暂时不可用')
    assign('funnel', results[3], '招聘漏斗暂时不可用')
    assign('positions', results[4], '岗位效果暂时不可用')

    setData(previous => ({ ...previous, ...nextData }))
    setErrors(nextErrors)
    setLoading(false)
    setRefreshing(false)
  }, [])

  useEffect(() => {
    void loadDashboard(true)
  }, [loadDashboard])

  const hasData = sectionKeys.some(key => data[key] !== undefined)
  const fullError = !loading && !hasData && sectionKeys.every(key => Boolean(errors[key]))
  const partialError = !fullError && Object.keys(errors).length > 0
  const retry = () => void loadDashboard(!hasData)
  const summary = data.summary
  const lastUpdatedAt = summary?.lastUpdatedAt ?? data.actions?.generatedAt

  if (fullError) {
    return <div className="space-y-6">
      <PageIntro summary={summary} onRefresh={retry} refreshing={refreshing} navigate={navigate} />
      <Card className="p-8"><PanelMessage kind="error" message="企业工作台暂时无法加载，请稍后重试。" onRetry={retry} /></Card>
    </div>
  }

  return <div className="space-y-6">
    <PageIntro summary={summary} onRefresh={retry} refreshing={refreshing} navigate={navigate} />

    {partialError && <div role="status" className="flex items-start gap-3 rounded-2xl border border-[var(--warning)]/40 bg-[var(--warning)]/10 px-4 py-3 text-sm text-foreground">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-[var(--warning-foreground)]" />
      <span>部分数据加载失败，已保留可用内容。{Object.values(errors).find(Boolean)}</span>
    </div>}

    <Card className="p-5 sm:p-6">
      {loading && !summary ? <div className="grid gap-6 sm:grid-cols-2 xl:grid-cols-4"><PanelMessage kind="loading" message="正在汇总企业招聘数据…" /><PanelMessage kind="loading" message="正在汇总企业招聘数据…" /><PanelMessage kind="loading" message="正在汇总企业招聘数据…" /><PanelMessage kind="loading" message="正在汇总企业招聘数据…" /></div> : summary ? <div className="grid gap-6 sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="招聘中岗位" value={summary.publishedPositions} detail={`${summary.draftPositions} 个岗位仍在草稿中`} icon={BriefcaseBusiness} />
        <Metric label="待处理申请" value={summary.pendingApplications} detail={`累计收到 ${summary.totalApplications} 份申请`} icon={ClipboardList} tone={summary.pendingApplications > 0 ? 'warning' : 'default'} />
        <Metric label="今日面试" value={summary.todayInterviews} detail="AI 与线下面试安排" icon={CalendarClock} />
        <Metric label="超时事项" value={summary.overdueItems} detail={summary.overdueItems > 0 ? '需要尽快处理' : '目前没有逾期事项'} icon={Clock3} tone={summary.overdueItems > 0 ? 'danger' : 'default'} />
      </div> : <PanelMessage kind="error" message={errors.summary ?? '招聘概览暂时不可用'} onRetry={retry} />}
    </Card>

    <Card className="p-5 sm:p-6">
      <SectionHeading eyebrow="待处理事项" title="处理队列" description="按优先级处理需要人工判断的申请和面试事项。" action={<Badge tone="info">{data.actions?.total ?? 0} 项待处理</Badge>} />
      {loading && !data.actions ? <PanelMessage kind="loading" message="正在读取待办事项…" /> : errors.actions && !data.actions ? <PanelMessage kind="error" message={errors.actions} onRetry={retry} /> : data.actions?.groups.every(group => group.count === 0) ? <PanelMessage kind="empty" message="今天没有需要立即处理的招聘事项。" /> : <div className="mt-6 grid gap-3 lg:grid-cols-5">
        {(data.actions?.groups ?? []).map(group => <ActionGroup key={group.actionType} group={group} onOpen={() => navigate('/company/applications')} />)}
      </div>}
    </Card>

    <div className="grid gap-6 xl:grid-cols-[1.15fr_.85fr]">
      <Card className="p-5 sm:p-6">
        <SectionHeading eyebrow="面试安排" title="今日与近期面试" description="按时间查看未来 7 天的面试安排。" action={<Button variant="ghost" className="h-9 px-3 text-xs" onClick={() => navigate('/company/interviews')}>查看面试<ArrowRight className="h-3.5 w-3.5" /></Button>} />
        {loading && !data.upcoming ? <PanelMessage kind="loading" message="正在读取面试安排…" /> : errors.upcoming && !data.upcoming ? <PanelMessage kind="error" message={errors.upcoming} onRetry={retry} /> : !data.upcoming?.length ? <PanelMessage kind="empty" message="未来 7 天暂无面试安排。" /> : <div className="mt-5 divide-y divide-border">{data.upcoming.map(interview => <UpcomingRow key={`${interview.source}-${interview.interviewId ?? interview.applicationId}`} interview={interview} />)}</div>}
      </Card>

      <Card className="p-5 sm:p-6">
        <SectionHeading eyebrow="阶段分布" title="招聘漏斗" description="按当前企业申请数据统计各阶段人数。" />
        {loading && !data.funnel ? <PanelMessage kind="loading" message="正在计算招聘漏斗…" /> : errors.funnel && !data.funnel ? <PanelMessage kind="error" message={errors.funnel} onRetry={retry} /> : !data.funnel?.some(stage => stage.count > 0) ? <PanelMessage kind="empty" message="还没有可展示的申请漏斗数据。" /> : <div className="mt-5 space-y-4">{data.funnel?.map(stage => <FunnelRow key={stage.status} stage={stage} />)}</div>}
      </Card>
    </div>

    <Card className="p-5 sm:p-6">
      <SectionHeading eyebrow="岗位表现" title="岗位效果排行" description="按申请量展示岗位表现，支持评估招聘进度。" action={<Button variant="secondary" className="h-9 px-4 text-xs" onClick={() => navigate('/company/positions')}>岗位管理<ArrowRight className="h-3.5 w-3.5" /></Button>} />
      {loading && !data.positions ? <PanelMessage kind="loading" message="正在读取岗位效果…" /> : errors.positions && !data.positions ? <PanelMessage kind="error" message={errors.positions} onRetry={retry} /> : !data.positions?.length ? <PanelMessage kind="empty" message="还没有已发布岗位的效果数据。" /> : <div className="mt-5 overflow-x-auto"><div className="min-w-[640px] divide-y divide-border">{data.positions.map((position, index) => <PositionRow key={position.positionId} position={position} rank={index + 1} />)}</div></div>}
    </Card>

    <div className="flex flex-col gap-2 border-t border-border pt-4 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
      <span>数据最后更新：{lastUpdatedAt ? formatDateTime(lastUpdatedAt) : loading ? '读取中…' : '暂无时间'}</span>
      <button type="button" className="inline-flex items-center gap-1 self-start font-semibold text-[var(--accent)] hover:underline sm:self-auto" onClick={retry} disabled={refreshing}>
        <RefreshCw className={`h-3.5 w-3.5 ${refreshing ? 'animate-spin' : ''}`} />手动刷新
      </button>
    </div>
  </div>
}

function PageIntro({ summary, onRefresh, refreshing, navigate }: { summary?: DashboardSummary; onRefresh: () => void; refreshing: boolean; navigate: ReturnType<typeof useNavigate> }) {
  return <header className="border-b border-border pb-6">
    <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
      <div>
        <div className="flex items-center gap-2 text-sm font-semibold text-[var(--accent)]"><Building2 className="h-4 w-4" />{summary?.companyName ?? '企业招聘工作台'}</div>
        <h1 className="mt-2 max-w-3xl text-3xl font-black tracking-[-.05em] sm:text-4xl">招聘总览</h1>
        <p className="mt-3 max-w-2xl text-sm leading-7 text-muted-foreground">{summary ? `汇总 ${summary.companyShortName || summary.companyName} 的岗位、申请、面试和待处理事项。` : '正在加载企业招聘概览。'}</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" onClick={() => navigate('/company/positions')}>岗位管理</Button>
        <Button onClick={() => navigate('/company/applications')}>申请管理<ArrowRight className="h-4 w-4" /></Button>
        <Button variant="ghost" className="h-11 w-11 px-0" aria-label="刷新工作台" onClick={onRefresh} disabled={refreshing}><RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} /></Button>
      </div>
    </div>
  </header>
}

function ActionGroup({ group, onOpen }: { group: DashboardActionGroup; onOpen: () => void }) {
  const meta = actionMeta[group.actionType] ?? { icon: ClipboardList }
  const Icon = meta.icon
  const firstItem = group.items[0]
  return <button type="button" className="group min-h-44 rounded-2xl border border-border bg-background p-4 text-left transition hover:-translate-y-0.5 hover:border-[var(--accent)] hover:shadow-[0_10px_25px_rgba(20,18,17,.07)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]" onClick={onOpen}>
    <div className="flex items-start justify-between gap-3"><span className="grid h-10 w-10 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><Icon className="h-5 w-5" /></span><span className="text-2xl font-black tracking-[-.04em]">{group.count}</span></div>
    <h3 className="mt-5 text-sm font-bold">{group.label}</h3>
    <p className="mt-1 text-xs leading-5 text-muted-foreground">{group.description}</p>
    <div className="mt-4 border-t border-border pt-3 text-xs text-muted-foreground">{firstItem ? <><span className="font-semibold text-foreground">{firstItem.candidateName || '候选人'}</span><span className="mx-1">·</span>{firstItem.positionName || '岗位'}<span className="mt-1 block">{formatDateTime(firstItem.dueAt)}</span></> : group.count > 0 ? '还有待处理事项' : '当前没有事项'}</div>
  </button>
}

function UpcomingRow({ interview }: { interview: UpcomingInterview }) {
  return <div className="flex gap-4 py-4 first:pt-0 last:pb-0">
    <div className="flex w-12 shrink-0 flex-col items-center"><span className="text-sm font-black text-foreground">{interview.scheduledAt ? new Date(interview.scheduledAt).getDate() : '—'}</span><span className="text-[10px] text-muted-foreground">{interview.scheduledAt ? new Intl.DateTimeFormat('zh-CN', { month: 'short' }).format(new Date(interview.scheduledAt)) : ''}</span><span className="mt-2 h-full w-px bg-border" /></div>
    <div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><p className="font-bold">{interview.candidateName || '候选人'}</p><Badge tone={interview.source === 'AI' ? 'info' : 'warning'}>{interview.source === 'AI' ? 'AI 面试' : '线下面试'}</Badge></div><p className="mt-1 truncate text-sm text-muted-foreground">{interview.positionName || '未命名岗位'}</p><div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground"><span className="inline-flex items-center gap-1"><Clock3 className="h-3.5 w-3.5" />{formatDateTime(interview.scheduledAt)}</span><span>{interview.durationMinutes || 0} 分钟</span>{interview.location && <span className="inline-flex items-center gap-1"><MapPin className="h-3.5 w-3.5" />{interview.location}</span>}</div></div>
  </div>
}

function FunnelRow({ stage }: { stage: FunnelStage }) {
  const meta = applicationStatusMeta[stage.status]
  return <div><div className="flex items-center justify-between gap-3 text-sm"><span className="font-semibold">{stage.label || meta?.label || stage.status}</span><span className="text-muted-foreground">{stage.count} <span className="text-xs">({stage.percentage}%)</span></span></div><div className="mt-2 h-2 overflow-hidden rounded-full bg-muted"><div className={`h-full rounded-full ${funnelColors[stage.status]}`} style={{ width: `${Math.min(Math.max(stage.percentage, 0), 100)}%` }} /></div></div>
}

function PositionRow({ position, rank }: { position: PositionAnalytics; rank: number }) {
  const status = positionStatusMeta[position.recruitmentStatus] ?? positionStatusMeta.DRAFT
  return <div className="grid grid-cols-[2rem_minmax(12rem,1fr)_6rem_6rem_6rem_7rem] items-center gap-3 py-4 first:pt-0 last:pb-0"><span className="text-sm font-black text-muted-foreground">{String(rank).padStart(2, '0')}</span><div className="min-w-0"><p className="truncate font-bold">{position.positionName}</p><Badge tone={status.tone} className="mt-1">{status.label}</Badge></div><div><p className="text-lg font-black">{position.applicationCount}</p><p className="text-[11px] text-muted-foreground">申请</p></div><div><p className="text-lg font-black">{position.pendingCount}</p><p className="text-[11px] text-muted-foreground">待处理</p></div><div><p className="text-lg font-black">{position.hiredCount}</p><p className="text-[11px] text-muted-foreground">录用</p></div><div><p className="text-lg font-black">{position.averageMatchScore || 0}%</p><p className="text-[11px] text-muted-foreground">平均匹配度</p></div></div>
}
