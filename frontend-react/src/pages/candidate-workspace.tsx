import type { LucideIcon } from 'lucide-react'
import {
  AlertCircle,
  ArrowRight,
  BarChart3,
  BookOpen,
  CalendarCheck2,
  CalendarClock,
  CalendarDays,
  ChartNoAxesCombined,
  CircleGauge,
  Clock3,
  FileChartColumn,
  NotebookPen,
  Play,
  RefreshCw,
  Target,
  TrendingDown,
  TrendingUp,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { type Interview, type PracticeBank, request } from '@/lib/api'
import {
  canEnterInterview,
  canViewReport,
  INTERVIEW_STATUS,
  interviewStatusText,
  interviewStatusTone,
  isReportPending,
} from '@/lib/interview-status'
import { isPracticeInterview } from '@/lib/interviewer-styles'

type Trend = {
  interviewId: string
  interviewTitle: string
  scheduledAt: string
  totalScore: number
  professionalScore: number
  expressionScore: number
  logicScore: number
  adaptabilityScore: number
}

type ScoreChanges = Omit<Trend, 'interviewId' | 'interviewTitle' | 'scheduledAt'>

type Summary = {
  reportCount: number
  latest?: Trend
  previous?: Trend
  changeFromPrevious?: ScoreChanges
  trends: Trend[]
}

type ScoreDimension = {
  key: keyof Pick<Trend, 'professionalScore' | 'expressionScore' | 'logicScore' | 'adaptabilityScore'>
  label: string
  guidance: string
}

type StatItem = {
  label: string
  value: string
  hint: string
  icon: LucideIcon
}

const scoreDimensions: ScoreDimension[] = [
  { key: 'professionalScore', label: '专业能力', guidance: '围绕核心知识点补充依据，并结合项目情境说明取舍。' },
  { key: 'expressionScore', label: '表达能力', guidance: '先给结论，再分点说明背景、行动与结果。' },
  { key: 'logicScore', label: '逻辑思维', guidance: '使用清晰的分析框架，说明判断条件和推导过程。' },
  { key: 'adaptabilityScore', label: '应变能力', guidance: '面对追问时先确认问题，再补充边界与替代方案。' },
]

const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: 'numeric',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

const shortDateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
})

function timestamp(value?: string) {
  const result = value ? new Date(value).getTime() : Number.NaN
  return Number.isFinite(result) ? result : 0
}

function dateTimeText(value?: string) {
  const valueTimestamp = timestamp(value)
  return valueTimestamp ? dateTimeFormatter.format(valueTimestamp) : '时间待确认'
}

function shortDateText(value?: string) {
  const valueTimestamp = timestamp(value)
  return valueTimestamp ? shortDateFormatter.format(valueTimestamp) : '--/--'
}

function signedScore(value?: number) {
  const score = Number(value ?? 0)
  return `${score > 0 ? '+' : ''}${score.toFixed(1)}`
}

function scoreValue(value?: number) {
  const score = Number(value ?? 0)
  if (!Number.isFinite(score)) return 0
  return Math.min(100, Math.max(0, score))
}

function isInCurrentWeek(value: string) {
  const valueTimestamp = timestamp(value)
  if (!valueTimestamp) return false
  const now = new Date()
  const mondayOffset = (now.getDay() + 6) % 7
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - mondayOffset)
  const end = new Date(start)
  end.setDate(start.getDate() + 7)
  return valueTimestamp >= start.getTime() && valueTimestamp < end.getTime()
}

function actionPriority(item: Interview) {
  const practice = isPracticeInterview(item.remark)
  if (item.status === INTERVIEW_STATUS.IN_PROGRESS) return practice ? 1 : 0
  if (item.status === INTERVIEW_STATUS.PENDING) return practice ? 3 : 2
  return 9
}

function compareActionPriority(left: Interview, right: Interview) {
  const rankDifference = actionPriority(left) - actionPriority(right)
  if (rankDifference) return rankDifference
  if (left.status === INTERVIEW_STATUS.IN_PROGRESS) return timestamp(right.scheduledAt) - timestamp(left.scheduledAt)

  const now = Date.now()
  const leftFuture = timestamp(left.scheduledAt) >= now
  const rightFuture = timestamp(right.scheduledAt) >= now
  if (leftFuture !== rightFuture) return leftFuture ? -1 : 1
  return leftFuture
    ? timestamp(left.scheduledAt) - timestamp(right.scheduledAt)
    : timestamp(right.scheduledAt) - timestamp(left.scheduledAt)
}

function WorkspaceSkeleton() {
  return <div className="space-y-6" aria-label="正在加载候选人工作台">
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(310px,.75fr)]">
      <div className="h-72 animate-pulse rounded-[28px] bg-muted" />
      <div className="h-72 animate-pulse rounded-[28px] bg-muted" />
    </div>
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {Array.from({ length: 4 }, (_, index) => <div key={index} className="h-32 animate-pulse rounded-[24px] bg-muted" />)}
    </div>
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(330px,.65fr)]">
      <div className="h-80 animate-pulse rounded-[24px] bg-muted" />
      <div className="h-80 animate-pulse rounded-[24px] bg-muted" />
    </div>
  </div>
}

function OverviewTrendChart({ trends, onStart }: { trends: Trend[]; onStart: () => void }) {
  const visibleTrends = trends.slice(-6)
  const chart = useMemo(() => {
    const width = 680
    const height = 230
    const paddingX = 46
    const paddingTop = 30
    const paddingBottom = 40
    if (visibleTrends.length < 4) return { width, height, points: [], line: '', area: '', min: 0, max: 100 }

    const scores = visibleTrends.map(item => scoreValue(item.totalScore))
    const min = Math.max(0, Math.min(...scores) - 8)
    const max = Math.min(100, Math.max(...scores) + 8)
    const range = Math.max(1, max - min)
    const step = (width - paddingX * 2) / (visibleTrends.length - 1)
    const points = visibleTrends.map((item, index) => ({
      ...item,
      score: scoreValue(item.totalScore),
      x: paddingX + step * index,
      y: paddingTop + ((max - scoreValue(item.totalScore)) / range) * (height - paddingTop - paddingBottom),
    }))
    const line = points.map(point => `${point.x},${point.y}`).join(' ')
    const baseY = height - paddingBottom
    const area = `M ${points[0].x} ${baseY} L ${points.map(point => `${point.x} ${point.y}`).join(' L ')} L ${points.at(-1)?.x} ${baseY} Z`
    return { width, height, points, line, area, min, max }
  }, [visibleTrends])

  if (!visibleTrends.length) {
    return <div className="mt-6 grid min-h-56 place-items-center rounded-[22px] border border-dashed border-border bg-background/55 px-6 text-center">
      <div className="max-w-sm">
        <span className="mx-auto grid h-11 w-11 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]">
          <ChartNoAxesCombined className="h-5 w-5" />
        </span>
        <h3 className="mt-4 text-base font-semibold">完成首场评测，建立能力基线</h3>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">报告生成后，这里会展示综合得分变化和最近训练结果。</p>
        <Button className="mt-5 h-10" onClick={onStart}><Play className="h-4 w-4" />开始面试</Button>
      </div>
    </div>
  }

  if (visibleTrends.length < 4) {
    return <div className="mt-6 grid gap-3 sm:grid-cols-3">
      {visibleTrends.map((item, index) => {
        const score = scoreValue(item.totalScore)
        return <article key={item.interviewId} className="rounded-2xl border border-border bg-background/55 p-4">
          <div className="flex items-center justify-between gap-3">
            <span className="text-xs text-muted-foreground">第 {index + 1} 次</span>
            <strong className="text-xl">{Math.round(score)}</strong>
          </div>
          <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-muted">
            <div className="h-full rounded-full bg-[var(--accent)]" style={{ width: `${score}%` }} />
          </div>
          <p className="mt-3 truncate text-xs font-medium" title={item.interviewTitle}>{item.interviewTitle}</p>
          <p className="mt-1 text-xs text-muted-foreground">{shortDateText(item.scheduledAt)}</p>
        </article>
      })}
    </div>
  }

  return <figure className="mt-5">
    <svg
      viewBox={`0 0 ${chart.width} ${chart.height}`}
      className="h-60 w-full"
      role="img"
      aria-labelledby="workspace-trend-title workspace-trend-description"
    >
      <title id="workspace-trend-title">最近六次面试综合得分趋势</title>
      <desc id="workspace-trend-description">{visibleTrends.map(item => `${shortDateText(item.scheduledAt)} ${Math.round(scoreValue(item.totalScore))} 分`).join('，')}</desc>
      <defs>
        <linearGradient id="candidateWorkspaceTrendArea" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="var(--accent)" stopOpacity=".2" />
          <stop offset="100%" stopColor="var(--accent)" stopOpacity="0" />
        </linearGradient>
      </defs>
      {[0, .5, 1].map(ratio => {
        const y = 30 + ratio * 160
        const label = Math.round(chart.max - ratio * (chart.max - chart.min))
        return <g key={ratio}>
          <line x1="46" x2={chart.width - 46} y1={y} y2={y} stroke="currentColor" strokeOpacity=".1" strokeDasharray="4 7" />
          <text x="4" y={y + 4} className="fill-muted-foreground text-[11px]">{label}</text>
        </g>
      })}
      <path d={chart.area} fill="url(#candidateWorkspaceTrendArea)" />
      <polyline points={chart.line} fill="none" stroke="var(--accent)" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" />
      {chart.points.map(point => <g key={point.interviewId}>
        <circle cx={point.x} cy={point.y} r="6" fill="var(--surface)" stroke="var(--accent)" strokeWidth="3" />
        <text x={point.x} y={point.y - 15} textAnchor="middle" className="fill-foreground text-[12px] font-bold">{Math.round(point.score)}</text>
        <text x={point.x} y={chart.height - 10} textAnchor="middle" className="fill-muted-foreground text-[11px]">{shortDateText(point.scheduledAt)}</text>
        <title>{point.interviewTitle}：{Math.round(point.score)} 分</title>
      </g>)}
    </svg>
    <ol className="sr-only" aria-label="最近六次面试综合得分明细">
      {visibleTrends.map(item => <li key={item.interviewId}>
        {shortDateText(item.scheduledAt)}，{item.interviewTitle}，{Math.round(scoreValue(item.totalScore))} 分
      </li>)}
    </ol>
  </figure>
}

export function CandidateWorkspaceOverview() {
  const navigate = useNavigate()
  const [interviews, setInterviews] = useState<Interview[]>([])
  const [banks, setBanks] = useState<PracticeBank[]>([])
  const [summary, setSummary] = useState<Summary>()
  const [loading, setLoading] = useState(true)
  const [loadVersion, setLoadVersion] = useState(0)
  const [busyAction, setBusyAction] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    void Promise.allSettled([
      request<Interview[]>('/v1/interviews'),
      request<PracticeBank[]>('/v1/interviews/practice/banks'),
      request<Summary>('/v1/reports/my/summary'),
    ]).then(results => {
      if (cancelled) return
      const [interviewResult, bankResult, summaryResult] = results
      if (interviewResult.status === 'fulfilled') setInterviews(interviewResult.value)
      if (bankResult.status === 'fulfilled') setBanks(bankResult.value)
      if (summaryResult.status === 'fulfilled') setSummary(summaryResult.value)

      const unavailable = [
        interviewResult.status === 'rejected' ? '面试安排' : '',
        bankResult.status === 'rejected' ? '练习题库' : '',
        summaryResult.status === 'rejected' ? '能力报告' : '',
      ].filter(Boolean)
      setError(unavailable.length ? `${unavailable.join('、')}暂时无法加载，其他可用内容已保留。` : '')
    }).finally(() => {
      if (!cancelled) setLoading(false)
    })
    return () => { cancelled = true }
  }, [loadVersion])

  const actionableInterviews = useMemo(
    () => interviews.filter(item => canEnterInterview(item.status)).sort(compareActionPriority),
    [interviews],
  )
  const nextInterview = actionableInterviews[0]
  const nextBank = banks[0]
  const recentInterviews = useMemo(
    () => [...interviews].sort((left, right) => timestamp(right.scheduledAt) - timestamp(left.scheduledAt)).slice(0, 4),
    [interviews],
  )
  const thisWeekPracticeCount = interviews.filter(item => isPracticeInterview(item.remark) && isInCurrentWeek(item.scheduledAt)).length
  const inProgressCount = interviews.filter(item => item.status === INTERVIEW_STATUS.IN_PROGRESS).length
  const latestScore = summary?.latest ? Math.round(scoreValue(summary.latest.totalScore)) : null
  const scoreChange = summary?.previous ? summary.changeFromPrevious?.totalScore : undefined
  const focus = summary?.latest
    ? [...scoreDimensions].sort((left, right) => scoreValue(summary.latest?.[left.key]) - scoreValue(summary.latest?.[right.key]))[0]
    : undefined
  const focusScore = focus && summary?.latest ? scoreValue(summary.latest[focus.key]) : 0
  const focusChange = focus && summary?.previous ? summary.changeFromPrevious?.[focus.key] : undefined
  const hasInitialData = Boolean(interviews.length || banks.length || summary)

  const stats: StatItem[] = [
    {
      label: '本周练习',
      value: String(thisWeekPracticeCount).padStart(2, '0'),
      hint: banks.length ? `${banks.length} 个题库可用` : '周一至今',
      icon: CalendarCheck2,
    },
    {
      label: '待完成面试',
      value: String(actionableInterviews.length).padStart(2, '0'),
      hint: inProgressCount ? `${inProgressCount} 场进行中` : actionableInterviews.length ? '按计划完成' : '当前无待办',
      icon: Clock3,
    },
    {
      label: '评测报告',
      value: String(summary?.reportCount ?? 0).padStart(2, '0'),
      hint: summary?.reportCount ? '可查看详细反馈' : '等待首次评测',
      icon: FileChartColumn,
    },
    {
      label: '最新能力分',
      value: latestScore === null ? '--' : String(latestScore),
      hint: scoreChange === undefined ? '尚未建立基线' : `较上次 ${signedScore(scoreChange)}`,
      icon: CircleGauge,
    },
  ]

  async function enterInterview(item: Interview) {
    setBusyAction(item.id)
    setError('')
    try {
      if (item.status === INTERVIEW_STATUS.PENDING) {
        await request(`/v1/interviews/${item.id}/start`, { method: 'POST' })
      }
      navigate(`/candidate/interviews/${item.id}/room`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '无法进入面试，请稍后重试。')
    } finally {
      setBusyAction('')
    }
  }

  async function startPractice() {
    if (!nextBank) {
      navigate('/candidate/interviews')
      return
    }
    setBusyAction('new-practice')
    setError('')
    try {
      const result = await request<Interview>('/v1/interviews/practice', {
        method: 'POST',
        body: JSON.stringify({
          questionBankId: nextBank.id,
          questionCount: 5,
          duration: 30,
          interviewerStyle: 'big-tech',
        }),
      })
      navigate(`/candidate/interviews/${result.id}/room`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '模拟面试创建失败，请稍后重试。')
    } finally {
      setBusyAction('')
    }
  }

  function primaryAction() {
    if (nextInterview) {
      void enterInterview(nextInterview)
      return
    }
    void startPractice()
  }

  function openInterview(item: Interview) {
    if (canViewReport(item.status)) {
      navigate(`/candidate/interviews/${item.id}/report`)
      return
    }
    if (canEnterInterview(item.status)) {
      void enterInterview(item)
      return
    }
    navigate('/candidate/interviews')
  }

  return <div className="space-y-6">
    <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
      <div>
        <p className="text-sm font-semibold text-[var(--accent)]">候选人工作台</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight md:text-4xl">智答千面，志达万里</h1>
        <p className="mt-3 max-w-2xl text-muted-foreground">集中查看待办安排、训练重点与能力变化。</p>
      </div>
      <Button onClick={() => navigate('/candidate/interviews')}>
        进入面试大厅 <ArrowRight className="h-4 w-4" />
      </Button>
    </header>

    {error && <div role="alert" className="flex flex-col gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/30 dark:text-rose-200 sm:flex-row sm:items-center">
      <span className="flex min-w-0 flex-1 items-center gap-2"><AlertCircle className="h-4 w-4 shrink-0" />{error}</span>
      <Button variant="ghost" className="h-9 self-start px-3 text-current sm:self-auto" onClick={() => setLoadVersion(value => value + 1)}>
        <RefreshCw className="h-4 w-4" />重新加载
      </Button>
    </div>}

    {loading && !hasInitialData ? <WorkspaceSkeleton /> : <>
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(310px,.75fr)]">
        <Card className="soft-emphasis-panel kozi-dot-grid overflow-hidden p-7 md:p-8">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="flex items-center gap-2 text-sm font-semibold text-[var(--accent)]"><Target className="h-4 w-4" />下一步行动</p>
            <Badge tone={nextInterview?.status === INTERVIEW_STATUS.IN_PROGRESS ? 'success' : 'info'}>
              {nextInterview?.status === INTERVIEW_STATUS.IN_PROGRESS ? '进行中' : nextInterview ? '待开始' : nextBank ? '可开始' : '待安排'}
            </Badge>
          </div>
          <h2 className="mt-7 max-w-2xl text-2xl font-bold leading-tight md:text-3xl">
            {nextInterview?.title ?? (nextBank ? `${nextBank.name} · 模拟练习` : '当前没有待完成的面试')}
          </h2>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">
            {nextInterview
              ? nextInterview.status === INTERVIEW_STATUS.IN_PROGRESS
                ? '面试进度已保存，可从当前题目继续。'
                : '请按计划进入面试，并提前确认麦克风与摄像头状态。'
              : nextBank
                ? '从可用题库开始一场 5 题练习，完成后查看针对性评测。'
                : '前往面试大厅查看安排，或联系管理员配置练习题库。'}
          </p>

          <div className="mt-6 flex flex-wrap gap-3 text-sm text-muted-foreground">
            <span className="flex min-h-10 items-center gap-2 rounded-full border border-border bg-surface/75 px-4">
              <CalendarClock className="h-4 w-4 text-[var(--accent)]" />
              {nextInterview ? dateTimeText(nextInterview.scheduledAt) : nextBank ? '现在可开始' : '暂无时间安排'}
            </span>
            <span className="flex min-h-10 items-center gap-2 rounded-full border border-border bg-surface/75 px-4">
              <Clock3 className="h-4 w-4 text-[var(--accent)]" />
              {nextInterview ? `${nextInterview.duration} 分钟` : nextBank ? '约 30 分钟' : '时长待确认'}
            </span>
            <span className="flex min-h-10 items-center gap-2 rounded-full border border-border bg-surface/75 px-4">
              <BookOpen className="h-4 w-4 text-[var(--accent)]" />
              {nextInterview
                ? isPracticeInterview(nextInterview.remark) ? '模拟练习' : '正式面试'
                : nextBank ? `${nextBank.questionCount} 题可选` : '题库待配置'}
            </span>
          </div>

          <div className="mt-7 flex flex-col gap-3 sm:flex-row">
            <Button disabled={Boolean(busyAction) || (!nextInterview && !nextBank)} onClick={primaryAction}>
              <Play className="h-4 w-4" />
              {busyAction
                ? '正在准备…'
                : nextInterview?.status === INTERVIEW_STATUS.IN_PROGRESS
                  ? '继续面试'
                  : nextInterview ? '开始面试' : nextBank ? '开始模拟练习' : '暂无可用面试'}
            </Button>
            <Button variant="secondary" onClick={() => navigate('/candidate/calendar')}>
              <CalendarDays className="h-4 w-4" />查看面试日历
            </Button>
          </div>
        </Card>

        <Card className="flex min-h-72 flex-col">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-semibold text-[var(--accent)]">训练重点</p>
              <h2 className="mt-1 text-xl font-bold">本次优先提升</h2>
            </div>
            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]">
              <Target className="h-5 w-5" />
            </span>
          </div>

          {focus && summary?.latest ? <>
            <div className="mt-7 flex items-end justify-between gap-3">
              <div>
                <p className="text-sm text-muted-foreground">建议关注</p>
                <h3 className="mt-1 text-2xl font-bold">{focus.label}</h3>
              </div>
              <strong className="text-3xl">{Math.round(focusScore)}</strong>
            </div>
            <div className="mt-4 h-2 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label={`${focus.label}得分`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(focusScore)}>
              <div className="h-full rounded-full bg-[var(--accent)]" style={{ width: `${focusScore}%` }} />
            </div>
            <p className="mt-5 text-sm leading-6 text-muted-foreground">{focus.guidance}</p>
            <div className="mt-auto flex items-center justify-between gap-3 border-t border-border pt-5">
              <span className="text-xs text-muted-foreground">
                {focusChange === undefined ? '首份能力基线' : `较上次 ${signedScore(focusChange)}`}
              </span>
              <Button variant="ghost" className="h-9 px-3" onClick={() => navigate('/reports')}>
                查看能力报告 <ArrowRight className="h-4 w-4" />
              </Button>
            </div>
          </> : <div className="mt-7 flex flex-1 flex-col justify-center rounded-2xl border border-dashed border-border bg-background/55 p-5 text-center">
            <CircleGauge className="mx-auto h-7 w-7 text-[var(--accent)]" />
            <h3 className="mt-4 font-semibold">完成评测后生成训练重点</h3>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">系统会从专业、表达、逻辑和应变四个维度给出建议。</p>
            <Button variant="secondary" className="mx-auto mt-5 h-10" onClick={() => navigate('/library')}>选择专项练习</Button>
          </div>}
        </Card>
      </div>

      <section aria-label="候选人训练概览" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {stats.map((item, index) => {
          const Icon = item.icon
          return <Card key={item.label} motionDelay={index * .04}>
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm text-muted-foreground">{item.label}</p>
                <strong className="mt-4 block text-3xl tracking-tight">{item.value}</strong>
              </div>
              <span className="grid h-10 w-10 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]">
                <Icon className="h-5 w-5" />
              </span>
            </div>
            <p className="mt-3 text-xs text-muted-foreground">{item.hint}</p>
          </Card>
        })}
      </section>

      <div className="grid min-w-0 gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(330px,.65fr)]">
        <Card className="min-w-0">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <p className="text-sm font-semibold text-[var(--accent)]">能力变化</p>
              <h2 className="mt-1 text-xl font-bold">最近评测趋势</h2>
              <p className="mt-1 text-sm text-muted-foreground">按面试时间展示最近六次综合得分。</p>
            </div>
            {summary?.latest && <div className="flex items-center gap-2 rounded-full bg-muted px-3 py-2 text-sm">
              {Number(scoreChange ?? 0) >= 0
                ? <TrendingUp className="h-4 w-4 text-[var(--success-foreground)]" />
                : <TrendingDown className="h-4 w-4 text-[var(--danger-foreground)]" />}
              <span>最近 {Math.round(scoreValue(summary.latest.totalScore))} 分</span>
            </div>}
          </div>
          <OverviewTrendChart trends={summary?.trends ?? []} onStart={() => navigate('/candidate/interviews')} />
        </Card>

        <Card className="min-w-0 p-0">
          <div className="flex items-center justify-between border-b border-border px-5 py-5">
            <div>
              <p className="text-sm font-semibold text-[var(--accent)]">近期动态</p>
              <h2 className="mt-1 text-xl font-bold">面试与练习</h2>
            </div>
            <Button variant="ghost" className="h-9 px-3" onClick={() => navigate('/candidate/interviews')}>全部记录</Button>
          </div>

          <div className="divide-y divide-border">
            {recentInterviews.map(item => {
              const availableAction = canEnterInterview(item.status) || canViewReport(item.status)
              return <article key={item.id} className="flex items-center gap-3 px-5 py-4">
                <span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-muted text-[var(--accent)]">
                  {isPracticeInterview(item.remark) ? <BookOpen className="h-4 w-4" /> : <CalendarClock className="h-4 w-4" />}
                </span>
                <div className="min-w-0 flex-1">
                  <h3 className="truncate text-sm font-semibold" title={item.title}>{item.title}</h3>
                  <p className="mt-1 text-xs text-muted-foreground">{dateTimeText(item.scheduledAt)} · {item.duration} 分钟</p>
                </div>
                {availableAction
                  ? <Button
                      variant="ghost"
                      className="h-10 shrink-0 px-3"
                      disabled={busyAction === item.id}
                      onClick={() => openInterview(item)}
                    >
                      {busyAction === item.id ? '处理中' : canViewReport(item.status) ? '报告' : item.status === INTERVIEW_STATUS.IN_PROGRESS ? '继续' : '开始'}
                      <ArrowRight className="h-4 w-4" />
                    </Button>
                  : <Badge tone={interviewStatusTone(item.status)}>
                      {isReportPending(item.status) ? '报告生成中' : interviewStatusText[item.status] ?? '未知状态'}
                    </Badge>}
              </article>
            })}
            {!recentInterviews.length && <div className="px-5 py-10 text-center">
              <CalendarDays className="mx-auto h-7 w-7 text-[var(--accent)]" />
              <p className="mt-3 text-sm font-semibold">暂无面试记录</p>
              <p className="mt-1 text-xs text-muted-foreground">创建练习后会在这里显示最新进度。</p>
            </div>}
          </div>

          <div className="grid grid-cols-2 border-t border-border bg-muted/25 p-2 sm:grid-cols-4">
            <button type="button" onClick={() => navigate('/candidate/calendar')} className="flex min-h-16 flex-col items-center justify-center gap-1 rounded-xl text-xs font-medium transition hover:bg-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]">
              <CalendarDays className="h-4 w-4 text-[var(--accent)]" />日历
            </button>
            <button type="button" onClick={() => navigate('/candidate/reflections')} className="flex min-h-16 flex-col items-center justify-center gap-1 rounded-xl text-xs font-medium transition hover:bg-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]">
              <NotebookPen className="h-4 w-4 text-[var(--accent)]" />面试心得
            </button>
            <button type="button" onClick={() => navigate('/library')} className="flex min-h-16 flex-col items-center justify-center gap-1 rounded-xl text-xs font-medium transition hover:bg-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]">
              <BookOpen className="h-4 w-4 text-[var(--accent)]" />专项练习
            </button>
            <button type="button" onClick={() => navigate('/reports')} className="flex min-h-16 flex-col items-center justify-center gap-1 rounded-xl text-xs font-medium transition hover:bg-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]">
              <BarChart3 className="h-4 w-4 text-[var(--accent)]" />能力报告
            </button>
          </div>
        </Card>
      </div>
    </>}
  </div>
}
