import {
  AlertCircle,
  ArrowRight,
  BookOpenCheck,
  BrainCircuit,
  CalendarCheck2,
  CalendarClock,
  CalendarDays,
  CheckCircle2,
  Clock3,
  Database,
  FileClock,
  ListTodo,
  PlayCircle,
  RefreshCw,
  Settings2,
  Users,
  type LucideIcon,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request, type Interview } from '@/lib/api'
import { INTERVIEW_STATUS, interviewStatusText, interviewStatusTone } from '@/lib/interview-status'

type ProviderSnapshot = {
  id: string
  name: string
  kind: string
  enabled: boolean
  textDefault: boolean
}

type Metric = {
  label: string
  value: number
  description: string
  icon: LucideIcon
  tone: string
}

type AttentionItem = {
  title: string
  description: string
  count?: number
  to: string
  icon: LucideIcon
  tone: string
}

const reportAvailableStatuses = new Set<number>([
  INTERVIEW_STATUS.COMPLETED,
  INTERVIEW_STATUS.REPORT_READY,
  INTERVIEW_STATUS.PASSED,
  INTERVIEW_STATUS.FAILED,
])

const finishedStatuses = new Set<number>([
  ...reportAvailableStatuses,
  INTERVIEW_STATUS.REPORT_GENERATING,
])

function dateKey(value: string | Date) {
  const date = typeof value === 'string' ? new Date(value) : value
  if (Number.isNaN(date.getTime())) return ''
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function interviewTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 16)
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}

function updateTime(value: Date | null) {
  if (!value) return '尚未同步'
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(value)
}

export function AdminWorkspace() {
  const [items, setItems] = useState<Interview[]>([])
  const [providers, setProviders] = useState<ProviderSnapshot[]>([])
  const [providerState, setProviderState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    setProviderState('loading')

    const [interviewResult, providerResult] = await Promise.allSettled([
      request<Interview[]>('/v1/interviews'),
      request<ProviderSnapshot[]>('/v1/admin/ai-providers'),
    ])

    if (interviewResult.status === 'fulfilled') {
      setItems(interviewResult.value)
      setLastUpdatedAt(new Date())
    } else {
      setError(interviewResult.reason instanceof Error
        ? interviewResult.reason.message
        : '工作台数据加载失败，请稍后重试。')
    }

    if (providerResult.status === 'fulfilled') {
      setProviders(providerResult.value)
      setProviderState('ready')
    } else {
      setProviderState('error')
    }

    setLoading(false)
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const now = new Date()
  const today = dateKey(now)
  const pending = items.filter(item => item.status === INTERVIEW_STATUS.PENDING)
  const inProgress = items.filter(item => item.status === INTERVIEW_STATUS.IN_PROGRESS)
  const reportGenerating = items.filter(item => item.status === INTERVIEW_STATUS.REPORT_GENERATING)
  const todayItems = items.filter(item => dateKey(item.scheduledAt) === today)
  const overdue = pending.filter(item => {
    const scheduledAt = new Date(item.scheduledAt).getTime()
    return Number.isFinite(scheduledAt) && scheduledAt < now.getTime()
  })
  const textProvider = providers.find(item => item.enabled && item.textDefault)
  const providerNeedsAttention = providerState === 'ready' && !textProvider
  const attentionCount = overdue.length + reportGenerating.length + (providerNeedsAttention ? 1 : 0)

  const metrics = useMemo<Metric[]>(() => [
    {
      label: '全部面试',
      value: items.length,
      description: '当前账号可见的全部记录',
      icon: CalendarDays,
      tone: 'bg-[#f3eadf] text-[#7d4929]',
    },
    {
      label: '今日安排',
      value: todayItems.length,
      description: `${todayItems.filter(item => item.status === INTERVIEW_STATUS.PENDING).length} 场等待开始`,
      icon: CalendarCheck2,
      tone: 'bg-[#eef2e6] text-[#59613b]',
    },
    {
      label: '进行中',
      value: inProgress.length,
      description: inProgress.length ? '建议持续关注面试进度' : '当前没有进行中的面试',
      icon: PlayCircle,
      tone: 'bg-[#edf2f6] text-[#465d70]',
    },
    {
      label: '待处理',
      value: attentionCount,
      description: attentionCount ? '包含逾期、报告与配置事项' : '当前没有需要处理的事项',
      icon: ListTodo,
      tone: attentionCount ? 'bg-[#fff1df] text-[#9a5b20]' : 'bg-[#edf3e8] text-[#50633f]',
    },
  ], [attentionCount, inProgress, items.length, todayItems])

  const attentionItems = useMemo<AttentionItem[]>(() => {
    const result: AttentionItem[] = []

    if (overdue.length) {
      result.push({
        title: '预约已过仍待开始',
        description: '请确认候选人到场情况或调整面试状态。',
        count: overdue.length,
        to: '/admin/interviews',
        icon: AlertCircle,
        tone: 'bg-rose-50 text-rose-700',
      })
    }

    if (inProgress.length) {
      result.push({
        title: '正在进行的面试',
        description: '查看当前进度，及时处理异常中断。',
        count: inProgress.length,
        to: '/admin/interviews',
        icon: PlayCircle,
        tone: 'bg-emerald-50 text-emerald-700',
      })
    }

    if (reportGenerating.length) {
      result.push({
        title: '报告正在生成',
        description: '生成时间过长时可前往面试管理重试。',
        count: reportGenerating.length,
        to: '/admin/interviews',
        icon: FileClock,
        tone: 'bg-amber-50 text-amber-700',
      })
    }

    if (providerNeedsAttention) {
      result.push({
        title: '文字大模型未设为默认',
        description: '提问、评分与报告生成需要默认文字服务。',
        to: '/admin/settings',
        icon: BrainCircuit,
        tone: 'bg-amber-50 text-amber-700',
      })
    }

    return result
  }, [inProgress.length, overdue.length, providerNeedsAttention, reportGenerating.length])

  const nearby = useMemo(() => {
    // eslint-disable-next-line react-hooks/purity -- 仪表盘“即将开始”排序需要当前时间，每次渲染重新计算可接受
    const current = Date.now()
    return [...items]
      .filter(item => item.status !== INTERVIEW_STATUS.CANCELLED)
      .sort((a, b) => {
        const aTime = new Date(a.scheduledAt).getTime()
        const bTime = new Date(b.scheduledAt).getTime()
        const aFuture = aTime >= current
        const bFuture = bTime >= current
        if (aFuture !== bFuture) return aFuture ? -1 : 1
        return aFuture ? aTime - bTime : bTime - aTime
      })
      .slice(0, 6)
  }, [items])

  const trend = useMemo(() => Array.from({ length: 7 }, (_, index) => {
    const date = new Date()
    date.setHours(0, 0, 0, 0)
    date.setDate(date.getDate() - (6 - index))
    const key = dateKey(date)
    const scheduled = items.filter(item => dateKey(item.scheduledAt) === key && item.status !== INTERVIEW_STATUS.CANCELLED)
    return {
      key,
      label: `${date.getMonth() + 1}.${date.getDate()}`,
      weekday: ['日', '一', '二', '三', '四', '五', '六'][date.getDay()],
      total: scheduled.length,
      completed: scheduled.filter(item => finishedStatuses.has(item.status)).length,
    }
  }), [items])
  const trendMax = Math.max(1, ...trend.map(item => item.total))

  const reportReadyCount = items.filter(item => reportAvailableStatuses.has(item.status)).length

  return <div className="mx-auto max-w-[1680px] p-4 sm:p-5 lg:p-8">
    <section className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
      <div>
        <p className="text-sm font-semibold text-[var(--accent)]">管理工作台</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight">集中管理面试评测</h1>
        <p className="mt-2 text-muted-foreground">掌握今日安排、待处理事项和面试运行状态。</p>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <div className="mr-1 hidden text-right text-xs text-muted-foreground sm:block">
          <p>最后同步</p>
          <p className="mt-1 font-medium text-foreground">{updateTime(lastUpdatedAt)}</p>
        </div>
        <Button variant="secondary" className="h-10 px-4" disabled={loading} onClick={() => void load()}>
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          刷新数据
        </Button>
        <Link to="/admin/interviews" className="inline-flex h-10 items-center justify-center gap-2 rounded-full bg-[var(--primary)] px-5 text-sm font-semibold text-[var(--primary-foreground)] shadow-[0_10px_28px_rgba(21,20,18,.16)] transition hover:-translate-y-0.5">
          进入面试管理
          <ArrowRight className="h-4 w-4" />
        </Link>
      </div>
    </section>

    {error && <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
      <span>{error}</span>
      <button className="font-semibold underline-offset-4 hover:underline" onClick={() => void load()}>重新加载</button>
    </div>}

    <section className="mt-7 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {metrics.map(({ label, value, description, icon: Icon, tone }, index) => <Card key={label} motionDelay={index * 0.04}>
        <div className="flex items-start justify-between gap-4">
          <span className="text-sm text-muted-foreground">{label}</span>
          <span className={`grid h-10 w-10 place-items-center rounded-2xl ${tone}`}><Icon className="h-4 w-4" /></span>
        </div>
        <strong className="mt-5 block text-3xl tracking-tight">{loading ? '—' : value}</strong>
        <p className="mt-2 text-xs leading-5 text-muted-foreground">{description}</p>
      </Card>)}
    </section>

    <section className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(360px,.8fr)]">
      <Card className="overflow-hidden p-0">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-4">
          <div>
            <p className="text-xs font-semibold text-[var(--accent)]">面试日程</p>
            <h2 className="mt-1 font-bold">今日与近期面试</h2>
          </div>
          <div className="flex items-center gap-3">
            <Badge tone={todayItems.length ? 'info' : 'default'}>今日 {todayItems.length} 场</Badge>
            <Link className="text-sm font-semibold text-[var(--accent)] hover:text-foreground" to="/admin/interviews">查看全部</Link>
          </div>
        </div>
        <div className="divide-y divide-border">
          {loading
            ? <p className="p-10 text-center text-sm text-muted-foreground">正在同步面试日程…</p>
            : nearby.length
              ? nearby.map(item => <Link key={item.id} to={`/admin/interviews/${item.id}/review`} className="group flex items-center justify-between gap-4 px-5 py-4 transition hover:bg-muted/40">
                <div className="flex min-w-0 items-center gap-4">
                  <span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-muted text-muted-foreground transition group-hover:bg-[var(--accent-soft)] group-hover:text-[var(--accent)]"><Clock3 className="h-4 w-4" /></span>
                  <div className="min-w-0">
                    <strong className="block truncate">{item.title}</strong>
                    <p className="mt-1 text-xs text-muted-foreground">{interviewTime(item.scheduledAt)} · {item.duration} 分钟</p>
                  </div>
                </div>
                <Badge tone={interviewStatusTone(item.status)}>{interviewStatusText[item.status] ?? '未知状态'}</Badge>
              </Link>)
              : <div className="p-10 text-center">
                <CalendarClock className="mx-auto h-7 w-7 text-muted-foreground" />
                <p className="mt-4 font-semibold">暂无面试安排</p>
                <p className="mt-2 text-sm text-muted-foreground">创建面试后，近期日程会显示在这里。</p>
                <Link className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-[var(--accent)]" to="/admin/interviews">创建面试 <ArrowRight className="h-4 w-4" /></Link>
              </div>}
        </div>
      </Card>

      <Card className="p-0">
        <div className="border-b border-border px-5 py-4">
          <p className="text-xs font-semibold text-[var(--accent)]">行动中心</p>
          <div className="mt-1 flex items-center justify-between gap-3">
            <h2 className="font-bold">待处理事项</h2>
            <Badge tone={attentionItems.length ? 'warning' : 'success'}>{attentionItems.length ? `${attentionItems.length} 类` : '已清空'}</Badge>
          </div>
        </div>
        <div className="space-y-3 p-4">
          {loading
            ? <p className="p-6 text-center text-sm text-muted-foreground">正在检查待处理事项…</p>
            : attentionItems.length
              ? attentionItems.map(({ title, description, count, to, icon: Icon, tone }) => <Link key={title} to={to} className="group flex gap-3 rounded-2xl border border-border/80 p-3.5 transition hover:border-[var(--accent)] hover:bg-muted/35">
                <span className={`grid h-10 w-10 shrink-0 place-items-center rounded-2xl ${tone}`}><Icon className="h-4 w-4" /></span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center justify-between gap-3">
                    <strong className="text-sm">{title}</strong>
                    {count !== undefined && <span className="text-sm font-semibold text-[var(--accent)]">{count}</span>}
                  </div>
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">{description}</p>
                </div>
                <ArrowRight className="mt-3 h-4 w-4 shrink-0 text-muted-foreground transition group-hover:translate-x-0.5 group-hover:text-foreground" />
              </Link>)
              : <div className="rounded-2xl bg-[#eef3e8] px-5 py-8 text-center text-[#50633f]">
                <CheckCircle2 className="mx-auto h-7 w-7" />
                <p className="mt-3 font-semibold">当前运行平稳</p>
                <p className="mt-1 text-xs leading-5 opacity-80">没有逾期面试、积压报告或模型配置事项。</p>
              </div>}
        </div>
      </Card>
    </section>

    <section className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1.25fr)_minmax(420px,.85fr)]">
      <Card>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-xs font-semibold text-[var(--accent)]">运行趋势</p>
            <h2 className="mt-1 font-bold">近 7 天面试概览</h2>
            <p className="mt-1 text-sm text-muted-foreground">按预约日期统计面试安排与已结束数量。</p>
          </div>
          <div className="flex items-center gap-4 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1.5"><i className="h-2.5 w-2.5 rounded-full bg-[#d9c8b8]" />已安排</span>
            <span className="inline-flex items-center gap-1.5"><i className="h-2.5 w-2.5 rounded-full bg-[#7d4929]" />已结束</span>
          </div>
        </div>
        <div className="mt-7 grid h-48 grid-cols-7 items-end gap-2 sm:gap-4" aria-label="近 7 天面试趋势">
          {trend.map(day => <div key={day.key} className="flex h-full min-w-0 flex-col justify-end text-center">
            <div className="mb-2 text-xs font-semibold text-foreground">{day.total || ''}</div>
            <div className="relative mx-auto flex h-32 w-full max-w-10 items-end justify-center overflow-hidden rounded-t-xl bg-muted/45">
              <span className="absolute inset-x-0 bottom-0 rounded-t-xl bg-[#d9c8b8] transition-all" style={{ height: `${Math.max(day.total ? 10 : 0, (day.total / trendMax) * 100)}%` }} />
              <span className="absolute inset-x-[28%] bottom-0 rounded-t-lg bg-[#7d4929] transition-all" style={{ height: `${Math.max(day.completed ? 8 : 0, (day.completed / trendMax) * 100)}%` }} />
            </div>
            <p className="mt-2 truncate text-xs text-muted-foreground">周{day.weekday}</p>
            <p className="mt-1 truncate text-[11px] text-muted-foreground/75">{day.label}</p>
          </div>)}
        </div>
      </Card>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-1">
        <Card>
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-semibold text-[var(--accent)]">服务状态</p>
              <h2 className="mt-1 font-bold">系统运行概览</h2>
            </div>
            <span className={`h-2.5 w-2.5 rounded-full ${error || providerState === 'error' ? 'bg-amber-500' : 'bg-emerald-500'}`} />
          </div>
          <div className="mt-5 divide-y divide-border rounded-2xl border border-border/80 px-4">
            <div className="flex items-center justify-between gap-4 py-3 text-sm">
              <span className="inline-flex items-center gap-2 text-muted-foreground"><Database className="h-4 w-4" />面试数据</span>
              <span className="font-semibold">{error ? '读取异常' : loading ? '同步中' : '已同步'}</span>
            </div>
            <div className="flex items-center justify-between gap-4 py-3 text-sm">
              <span className="inline-flex items-center gap-2 text-muted-foreground"><BrainCircuit className="h-4 w-4" />文字模型</span>
              <span className="max-w-40 truncate font-semibold">{providerState === 'loading' ? '检查中' : providerState === 'error' ? '状态未知' : textProvider?.name || '待配置'}</span>
            </div>
            <div className="flex items-center justify-between gap-4 py-3 text-sm">
              <span className="inline-flex items-center gap-2 text-muted-foreground"><FileClock className="h-4 w-4" />报告任务</span>
              <span className="font-semibold">{reportGenerating.length} 个生成中</span>
            </div>
            <div className="flex items-center justify-between gap-4 py-3 text-sm">
              <span className="inline-flex items-center gap-2 text-muted-foreground"><CheckCircle2 className="h-4 w-4" />可查看报告</span>
              <span className="font-semibold">{reportReadyCount} 份</span>
            </div>
          </div>
        </Card>

        <Card>
          <p className="text-xs font-semibold text-[var(--accent)]">常用功能</p>
          <h2 className="mt-1 font-bold">快捷入口</h2>
          <div className="mt-5 grid grid-cols-2 gap-3">
            {([
              ['/admin/interviews', '面试管理', CalendarClock],
              ['/admin/candidates', '候选人', Users],
              ['/admin/question-banks', '题库管理', BookOpenCheck],
              ['/admin/settings', '系统设置', Settings2],
            ] as Array<[string, string, LucideIcon]>).map(([to, label, Icon]) => <Link key={to} to={to} className="group rounded-2xl border border-border/80 p-3.5 transition hover:-translate-y-0.5 hover:border-[var(--accent)] hover:bg-muted/35">
              <span className="grid h-9 w-9 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><Icon className="h-4 w-4" /></span>
              <span className="mt-3 flex items-center justify-between gap-2 text-sm font-semibold">{label}<ArrowRight className="h-3.5 w-3.5 text-muted-foreground transition group-hover:translate-x-0.5" /></span>
            </Link>)}
          </div>
        </Card>
      </div>
    </section>
  </div>
}
