import {
  AlertTriangle,
  ArrowRight,
  Bot,
  Building2,
  CheckCircle2,
  CircleDot,
  Clock3,
  FileClock,
  Headphones,
  RefreshCw,
  ServerCog,
  TicketCheck,
  Users,
  Workflow,
  type LucideIcon,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import type { AdminWorkspaceAction, AdminWorkspaceMetrics, AdminWorkspaceSummary, AdminWorkerStatus } from '@/lib/admin'

type MetricDefinition = {
  key: keyof AdminWorkspaceMetrics
  label: string
  description: string
  icon: LucideIcon
  tone: string
}

const scaleMetrics: MetricDefinition[] = [
  { key: 'companyCount', label: '企业数量', description: '已启用企业租户', icon: Building2, tone: 'bg-[var(--accent-soft)] text-[var(--accent)]' },
  { key: 'activeUserCount', label: '活跃用户', description: '当前可登录账号', icon: Users, tone: 'bg-[var(--info)] text-[var(--info-foreground)]' },
  { key: 'recruitingPositionCount', label: '招聘中岗位', description: '已发布且有效的岗位', icon: Workflow, tone: 'bg-[var(--success)] text-[var(--success-foreground)]' },
  { key: 'weeklyApplicationCount', label: '本周申请', description: '周一至今日的新投递', icon: CircleDot, tone: 'bg-[var(--warning)] text-[var(--warning-foreground)]' },
]

const loadMetrics: MetricDefinition[] = [
  { key: 'inProgressInterviewCount', label: '进行中面试', description: '当前正在进行', icon: Headphones, tone: 'bg-[var(--info)] text-[var(--info-foreground)]' },
  { key: 'reportBacklogCount', label: '报告任务积压', description: '正在生成或等待处理', icon: FileClock, tone: 'bg-[var(--warning)] text-[var(--warning-foreground)]' },
  { key: 'aiFailedTaskCount', label: 'AI 失败任务', description: '可复核的失败任务', icon: Bot, tone: 'bg-[var(--danger)] text-[var(--danger-foreground)]' },
  { key: 'pendingTicketCount', label: '待处理工单', description: '待受理或处理中', icon: TicketCheck, tone: 'bg-[var(--accent-soft)] text-[var(--accent)]' },
]

const actionIcons: Record<string, LucideIcon> = {
  REPORT_BACKLOG: FileClock,
  AI_FAILED: Bot,
  WORKER_QUEUE: ServerCog,
  TICKETS: TicketCheck,
  SERVICE_ANOMALY: AlertTriangle,
}

function number(value: number | undefined) {
  return new Intl.NumberFormat('zh-CN').format(value ?? 0)
}

function shortTime(value?: string | null) {
  if (!value) return '尚未同步'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '刚刚'
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(date)
}

function dateRange(start?: string, end?: string) {
  if (!start || !end) return '本周'
  return `${start.replaceAll('-', '.')} — ${end.replaceAll('-', '.')}`
}

function metricCards(metrics: AdminWorkspaceMetrics, definitions: MetricDefinition[], loading: boolean) {
  return definitions.map(({ key, label, description, icon: Icon, tone }, index) => <Card key={key} className="p-4 sm:p-5" motionDelay={index * 0.035}>
    <div className="flex items-start justify-between gap-3">
      <div className="min-w-0">
        <p className="text-sm font-semibold text-muted-foreground">{label}</p>
        <strong className="mt-3 block text-[2rem] font-semibold tracking-[-.045em] tabular-nums sm:text-3xl">{loading ? '—' : number(metrics[key])}</strong>
      </div>
      <span className={`grid h-10 w-10 shrink-0 place-items-center rounded-2xl ${tone}`}><Icon className="h-4 w-4" aria-hidden="true" /></span>
    </div>
    <p className="mt-2 text-xs leading-5 text-muted-foreground">{description}</p>
  </Card>)
}

function actionTone(action: AdminWorkspaceAction) {
  if (action.severity === 'danger') return 'border-[color-mix(in_srgb,var(--danger-foreground)_22%,var(--border))] bg-[var(--danger)]'
  if (action.severity === 'warning') return 'border-[color-mix(in_srgb,var(--warning-foreground)_18%,var(--border))] bg-[var(--warning)]'
  return 'border-border bg-surface'
}

function workerTone(worker: AdminWorkerStatus) {
  if (worker.code === 'ATTENTION') return 'warning'
  if (worker.code === 'WORKING') return 'info'
  return 'success'
}

function ActionCard({ action }: { action: AdminWorkspaceAction }) {
  const Icon = actionIcons[action.type] ?? AlertTriangle
  return <Link to={action.targetPath} className={`group flex min-w-0 gap-3 rounded-2xl border p-4 transition hover:-translate-y-0.5 hover:border-[var(--accent)] ${actionTone(action)}`}>
    <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-surface/70 text-[var(--accent)]"><Icon className="h-4 w-4" aria-hidden="true" /></span>
    <span className="min-w-0 flex-1">
      <span className="flex items-start justify-between gap-3">
        <span className="font-semibold">{action.label}</span>
        <span className="shrink-0 text-lg font-bold tabular-nums">{number(action.count)}</span>
      </span>
      <span className="mt-1 block text-xs leading-5 text-muted-foreground">{action.description}</span>
      <span className="mt-2 block text-xs font-semibold text-foreground/75">处理建议：{action.recommendation}</span>
    </span>
    <ArrowRight className="mt-1 h-4 w-4 shrink-0 text-muted-foreground transition group-hover:translate-x-0.5 group-hover:text-foreground" aria-hidden="true" />
  </Link>
}

export function AdminWorkspace() {
  const [data, setData] = useState<AdminWorkspaceSummary | null>(null)
  const [state, setState] = useState<'loading' | 'ready' | 'error'>('loading')

  const load = useCallback(async () => {
    setState('loading')
    try {
      const result = await request<AdminWorkspaceSummary>('/v1/admin/workspace/summary')
      setData(result)
      setState('ready')
    } catch {
      setState('error')
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const actions = data?.actions ?? []
  const worker = data?.worker
  const hasActions = actions.length > 0
  const lastUpdated = shortTime(data?.generatedAt)
  const workerBadge = worker ? workerTone(worker) : 'default'
  const actionSummary = useMemo(() => hasActions ? `有 ${number(actions.length)} 类事项需要关注` : '当前没有需要立即处理的事项', [actions.length, hasActions])

  return <div className="space-y-8 pb-6">
    <section className="flex flex-col gap-5 border-b border-border/70 pb-6 md:flex-row md:items-end md:justify-between">
      <div className="min-w-0">
        <p className="text-xs font-bold uppercase tracking-[.18em] text-[var(--accent)]">平台 · 运行总览</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-[-.04em] sm:text-4xl">平台运行总览</h1>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">查看平台规模、业务负荷和服务状态，按优先级进入对应模块处理。</p>
      </div>
      <div className="flex shrink-0 flex-wrap items-center gap-3">
        <div className="text-right text-xs text-muted-foreground">
          <p>统计范围</p>
          <p className="mt-1 font-semibold text-foreground">{dateRange(data?.periodStart, data?.periodEnd)}</p>
        </div>
        <div className="text-right text-xs text-muted-foreground">
          <p>最后同步</p>
          <p className="mt-1 font-semibold text-foreground">{lastUpdated}</p>
        </div>
        <Button type="button" variant="secondary" className="h-10 px-4" disabled={state === 'loading'} onClick={() => void load()}>
          <RefreshCw className={`h-4 w-4 ${state === 'loading' ? 'animate-spin' : ''}`} aria-hidden="true" />刷新
        </Button>
      </div>
    </section>

    {state === 'error' && <section role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[color-mix(in_srgb,var(--danger-foreground)_22%,var(--border))] bg-[var(--danger)] px-4 py-3 text-sm text-[var(--danger-foreground)]">
      <span>平台摘要暂时无法加载，业务页面仍可从上方业务域进入。</span>
      <Button type="button" variant="ghost" className="h-9 px-3 text-sm" onClick={() => void load()}>重新加载</Button>
    </section>}

    <section aria-labelledby="platform-scale-heading">
      <div className="flex items-end justify-between gap-4">
        <div><p className="text-xs font-bold uppercase tracking-[.16em] text-[var(--accent)]">平台规模</p><h2 id="platform-scale-heading" className="mt-1 text-xl font-semibold">业务基础盘</h2></div>
        <span className="hidden text-xs text-muted-foreground sm:block">聚合查询 · 不加载明细列表</span>
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{metricCards(data?.metrics ?? {} as AdminWorkspaceMetrics, scaleMetrics, state === 'loading')}</div>
    </section>

    <section aria-labelledby="platform-load-heading">
      <div className="flex items-end justify-between gap-4">
        <div><p className="text-xs font-bold uppercase tracking-[.16em] text-[var(--accent)]">运行负荷</p><h2 id="platform-load-heading" className="mt-1 text-xl font-semibold">待处理事项</h2></div>
        <span className="hidden text-xs text-muted-foreground sm:block">失败任务仅代表技术状态，不代表候选人结果</span>
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">{metricCards(data?.metrics ?? {} as AdminWorkspaceMetrics, loadMetrics, state === 'loading')}</div>
    </section>

    <section className="grid gap-6 xl:grid-cols-[minmax(0,1.25fr)_minmax(320px,.75fr)]">
      <Card className="p-0" aria-labelledby="action-center-heading">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-4 sm:px-6">
          <div><p className="text-xs font-bold uppercase tracking-[.16em] text-[var(--accent)]">异常处理</p><h2 id="action-center-heading" className="mt-1 text-xl font-semibold">服务异常与处理队列</h2></div>
          <Badge tone={hasActions ? 'warning' : 'success'}>{state === 'loading' ? '检查中' : hasActions ? `${number(actions.length)} 类待处理` : '运行平稳'}</Badge>
        </div>
        <div className="space-y-3 p-4 sm:p-5">
          {state === 'loading' && <div className="rounded-2xl bg-muted/45 px-4 py-10 text-center text-sm text-muted-foreground">正在汇总平台状态…</div>}
          {state === 'ready' && !hasActions && <div className="rounded-2xl bg-[var(--success)] px-5 py-10 text-center text-[var(--success-foreground)]"><CheckCircle2 className="mx-auto h-7 w-7" aria-hidden="true" /><p className="mt-3 font-semibold">{actionSummary}</p><p className="mt-1 text-xs leading-5 opacity-80">系统当前没有积压报告、失败任务、待处理工单或明显服务异常。</p></div>}
          {state === 'ready' && actions.map(action => <ActionCard key={action.type} action={action} />)}
        </div>
      </Card>

      <div className="grid gap-6">
        <Card aria-labelledby="worker-status-heading">
          <div className="flex items-start justify-between gap-4">
            <div><p className="text-xs font-bold uppercase tracking-[.16em] text-[var(--accent)]">判题 Worker</p><h2 id="worker-status-heading" className="mt-1 text-xl font-semibold">任务队列观测</h2></div>
            {worker && <Badge tone={workerBadge}>{worker.label}</Badge>}
          </div>
          <div className="mt-5 flex items-start gap-3 rounded-2xl border border-border/80 bg-muted/35 p-4">
            <ServerCog className="mt-0.5 h-5 w-5 shrink-0 text-[var(--accent)]" aria-hidden="true" />
            <div className="min-w-0"><p className="font-semibold">{worker?.summary ?? '正在读取任务队列…'}</p><p className="mt-1 text-xs leading-5 text-muted-foreground">{worker?.recommendation ?? '请稍候。'}</p></div>
          </div>
          <div className="mt-4 grid grid-cols-2 gap-3 text-sm"><div className="rounded-xl border border-border/80 p-3"><span className="text-xs text-muted-foreground">排队中</span><strong className="mt-1 block text-lg tabular-nums">{worker ? number(worker.queuedCount) : '—'}</strong></div><div className="rounded-xl border border-border/80 p-3"><span className="text-xs text-muted-foreground">执行中</span><strong className="mt-1 block text-lg tabular-nums">{worker ? number(worker.runningCount) : '—'}</strong></div></div>
          <p className="mt-4 text-xs leading-5 text-muted-foreground">仅展示脱敏队列状态和处理建议，不展示数据库、Redis、Token 或内部异常详情。</p>
        </Card>

        <Card aria-labelledby="quick-links-heading">
          <div className="flex items-center justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-[.16em] text-[var(--accent)]">进入处理</p><h2 id="quick-links-heading" className="mt-1 text-xl font-semibold">常用模块</h2></div><Clock3 className="h-5 w-5 text-muted-foreground" aria-hidden="true" /></div>
          <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-1">
            {([['/admin/interviews', '面试与报告'], ['/admin/tickets', '反馈工单'], ['/admin/ai-generations', 'AI 调用审计'], ['/admin/audit-logs', '操作日志']] as const).map(([to, label]) => <Link key={to} to={to} className="group flex min-h-11 items-center justify-between gap-3 rounded-xl border border-border/80 px-3.5 text-sm font-semibold transition hover:border-[var(--accent)] hover:bg-muted/45"><span>{label}</span><ArrowRight className="h-4 w-4 text-muted-foreground transition group-hover:translate-x-0.5 group-hover:text-foreground" aria-hidden="true" /></Link>)}
          </div>
        </Card>
      </div>
    </section>
  </div>
}
