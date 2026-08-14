import {
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  CircleGauge,
  Edit3,
  Lightbulb,
  NotebookPen,
  Plus,
  RefreshCw,
  Sparkles,
  Target,
  TrendingDown,
  TrendingUp,
  X,
} from 'lucide-react'
import { useEffect, useEffectEvent, useMemo, useRef, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { type Interview, request } from '@/lib/api'
import { canWriteReflection } from '@/lib/interview-status'

type ReflectionView = {
  reflectionId: string
  interviewId: string
  interviewTitle: string
  scheduledAt: string
  selfScore: number
  confidenceLevel: number
  content: string
  highlights?: string
  improvements?: string
  actionPlan?: string
  aiScore?: number
  createdAt: string
  updatedAt: string
}

type ReflectionSummary = {
  reflectionCount: number
  averageSelfScore: number
  averageConfidenceLevel: number
  averageAiScore?: number
  latestSelfScore?: number
  changeFromPrevious?: number
  reflections: ReflectionView[]
}

type ReflectionForm = {
  interviewId: string
  selfScore: number
  confidenceLevel: number
  content: string
  highlights: string
  improvements: string
  actionPlan: string
}

const emptyForm: ReflectionForm = {
  interviewId: '',
  selfScore: 75,
  confidenceLevel: 3,
  content: '',
  highlights: '',
  improvements: '',
  actionPlan: '',
}

const confidenceLabels = ['需要提升', '略显紧张', '基本稳定', '较为从容', '非常自信']

function dateText(value?: string) {
  if (!value) return '时间待确认'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value.replace('T', ' ').slice(0, 16)
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(parsed)
}

function shortDateText(value?: string) {
  if (!value) return '--'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value.slice(5, 10)
  return `${parsed.getMonth() + 1}/${parsed.getDate()}`
}

function signedScore(value?: number) {
  if (value === undefined || value === null) return '尚无对比'
  if (value === 0) return '与上次持平'
  return `${value > 0 ? '+' : ''}${Math.round(value)} 分`
}

function toForm(reflection?: ReflectionView, interviewId = ''): ReflectionForm {
  if (!reflection) return { ...emptyForm, interviewId }
  return {
    interviewId: String(reflection.interviewId),
    selfScore: reflection.selfScore,
    confidenceLevel: reflection.confidenceLevel,
    content: reflection.content,
    highlights: reflection.highlights ?? '',
    improvements: reflection.improvements ?? '',
    actionPlan: reflection.actionPlan ?? '',
  }
}

function ReflectionTrend({ reflections }: { reflections: ReflectionView[] }) {
  const visible = reflections.slice(-6)

  if (visible.length < 4) {
    return <div className="mt-6 grid gap-3 sm:grid-cols-3">
      {visible.map(item => <article key={item.reflectionId} className="rounded-2xl border border-border bg-background/55 p-4">
        <p className="text-xs text-muted-foreground">{shortDateText(item.scheduledAt)}</p>
        <h3 className="mt-2 line-clamp-2 min-h-10 text-sm font-semibold">{item.interviewTitle}</h3>
        <div className="mt-4 flex items-end justify-between gap-3">
          <div>
            <span className="text-xs text-muted-foreground">自评分</span>
            <strong className="mt-1 block text-2xl">{Math.round(item.selfScore)}</strong>
          </div>
          <div className="text-right">
            <span className="text-xs text-muted-foreground">AI 评分</span>
            <strong className="mt-1 block text-lg">{item.aiScore === undefined || item.aiScore === null ? '--' : Math.round(item.aiScore)}</strong>
          </div>
        </div>
      </article>)}
    </div>
  }

  const width = 760
  const height = 260
  const padding = { top: 24, right: 26, bottom: 42, left: 42 }
  const plotWidth = width - padding.left - padding.right
  const plotHeight = height - padding.top - padding.bottom
  const point = (score: number, index: number) => ({
    x: padding.left + (plotWidth * index) / Math.max(visible.length - 1, 1),
    y: padding.top + plotHeight * (1 - Math.max(0, Math.min(100, score)) / 100),
  })
  const selfPoints = visible.map((item, index) => ({ ...point(item.selfScore, index), item }))
  const aiPoints = visible
    .map((item, index) => item.aiScore === undefined || item.aiScore === null ? null : ({ ...point(item.aiScore, index), item }))
    .filter((item): item is NonNullable<typeof item> => Boolean(item))
  const selfPath = selfPoints.map(item => `${item.x},${item.y}`).join(' ')
  const aiPath = aiPoints.map(item => `${item.x},${item.y}`).join(' ')

  return <figure className="mt-5" aria-labelledby="reflection-trend-title reflection-trend-description">
    <figcaption className="flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-muted-foreground">
      <span id="reflection-trend-description">最近六次面试的自评分与 AI 评分对比</span>
      <span className="inline-flex items-center gap-2"><i className="h-0.5 w-6 bg-[var(--accent)]" />自评分</span>
      <span className="inline-flex items-center gap-2"><i className="w-6 border-t-2 border-dashed border-[var(--brand)]" />AI 评分</span>
    </figcaption>
    <div className="mt-3 overflow-x-auto">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-labelledby="reflection-trend-title reflection-trend-description"
        className="min-w-[620px]"
      >
        <title id="reflection-trend-title">心得评分趋势</title>
        {[0, 25, 50, 75, 100].map(score => {
          const y = padding.top + plotHeight * (1 - score / 100)
          return <g key={score}>
            <line x1={padding.left} x2={width - padding.right} y1={y} y2={y} className="stroke-border" strokeDasharray="4 5" />
            <text x={padding.left - 10} y={y + 4} textAnchor="end" className="fill-muted-foreground text-[11px]">{score}</text>
          </g>
        })}
        <polyline points={selfPath} fill="none" stroke="var(--accent)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
        {aiPoints.length > 1 && <polyline points={aiPath} fill="none" stroke="var(--brand)" strokeWidth="2.5" strokeDasharray="8 7" strokeLinecap="round" strokeLinejoin="round" />}
        {selfPoints.map(({ x, y, item }) => <g key={`self-${item.reflectionId}`}>
          <circle cx={x} cy={y} r="5" fill="var(--surface)" stroke="var(--accent)" strokeWidth="3" />
          <text x={x} y={height - 12} textAnchor="middle" className="fill-muted-foreground text-[11px]">{shortDateText(item.scheduledAt)}</text>
          <title>{item.interviewTitle}：自评分 {Math.round(item.selfScore)} 分</title>
        </g>)}
        {aiPoints.map(({ x, y, item }) => <circle key={`ai-${item.reflectionId}`} cx={x} cy={y} r="4" fill="var(--brand)" />)}
      </svg>
    </div>
    <ol className="sr-only" aria-label="面试心得评分趋势明细">
      {visible.map(item => <li key={item.reflectionId}>
        {dateText(item.scheduledAt)}，{item.interviewTitle}，自评分 {Math.round(item.selfScore)} 分，
        AI 评分 {item.aiScore === undefined || item.aiScore === null ? '暂未生成' : `${Math.round(item.aiScore)} 分`}
      </li>)}
    </ol>
  </figure>
}

export function CandidateReflections() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [summary, setSummary] = useState<ReflectionSummary>()
  const [interviews, setInterviews] = useState<Interview[]>([])
  const [loading, setLoading] = useState(true)
  const [loadVersion, setLoadVersion] = useState(0)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [editorOpen, setEditorOpen] = useState(false)
  const [form, setForm] = useState<ReflectionForm>(emptyForm)
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const handledInterviewParam = useRef<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    void Promise.all([
      request<ReflectionSummary>('/v1/reflections/my/summary'),
      request<Interview[]>('/v1/interviews'),
    ]).then(([reflectionSummary, interviewList]) => {
      if (cancelled) return
      setSummary(reflectionSummary)
      setInterviews(interviewList)
    }).catch(reason => {
      if (!cancelled) setError(reason instanceof Error ? reason.message : '心得数据加载失败，请稍后重试。')
    }).finally(() => {
      if (!cancelled) setLoading(false)
    })
    return () => { cancelled = true }
  }, [loadVersion])

  const reflections = useMemo(() => summary?.reflections ?? [], [summary?.reflections])
  const eligibleInterviews = useMemo(
    () => interviews
      .filter(item => canWriteReflection(item.status))
      .sort((left, right) => new Date(right.scheduledAt).getTime() - new Date(left.scheduledAt).getTime()),
    [interviews],
  )
  const reflectionByInterview = useMemo(
    () => new Map(reflections.map(item => [String(item.interviewId), item])),
    [reflections],
  )
  const pendingInterviews = eligibleInterviews.filter(item => !reflectionByInterview.has(String(item.id)))

  function openEditor(interviewId?: string) {
    const selectedId = interviewId || pendingInterviews[0]?.id || eligibleInterviews[0]?.id
    if (!selectedId) {
      setError('完成一场面试后即可记录心得。')
      return
    }
    setForm(toForm(reflectionByInterview.get(String(selectedId)), String(selectedId)))
    setFormError('')
    setNotice('')
    setEditorOpen(true)
    handledInterviewParam.current = String(selectedId)
    setSearchParams({ interviewId: String(selectedId) }, { replace: true })
  }

  function closeEditor() {
    if (saving) return
    handledInterviewParam.current = searchParams.get('interviewId') ?? form.interviewId
    setEditorOpen(false)
    setFormError('')
    setSearchParams({}, { replace: true })
  }

  const openEditorEffect = useEffectEvent(openEditor)
  const closeEditorEffect = useEffectEvent(closeEditor)
  const requestedInterviewId = searchParams.get('interviewId')

  useEffect(() => {
    const interviewId = requestedInterviewId
    if (!interviewId) {
      handledInterviewParam.current = null
      return
    }
    if (loading || handledInterviewParam.current === interviewId) return
    handledInterviewParam.current = interviewId
    if (eligibleInterviews.some(item => String(item.id) === interviewId)) openEditorEffect(interviewId)
  }, [loading, requestedInterviewId, eligibleInterviews])

  useEffect(() => {
    if (!editorOpen) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeEditorEffect()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', onKeyDown)
    }
  }, [editorOpen])

  function chooseInterview(interviewId: string) {
    setForm(toForm(reflectionByInterview.get(interviewId), interviewId))
    setFormError('')
    handledInterviewParam.current = interviewId
    setSearchParams({ interviewId }, { replace: true })
  }

  function updateField<Key extends keyof ReflectionForm>(key: Key, value: ReflectionForm[Key]) {
    setForm(current => ({ ...current, [key]: value }))
  }

  async function saveReflection(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!form.interviewId) {
      setFormError('请选择已结束的面试。')
      return
    }
    if (!form.content.trim()) {
      setFormError('请填写本次面试的核心心得。')
      return
    }
    setSaving(true)
    setFormError('')
    try {
      await request(`/v1/interviews/${form.interviewId}/reflection`, {
        method: 'PUT',
        body: JSON.stringify({
          selfScore: form.selfScore,
          confidenceLevel: form.confidenceLevel,
          content: form.content.trim(),
          highlights: form.highlights.trim() || null,
          improvements: form.improvements.trim() || null,
          actionPlan: form.actionPlan.trim() || null,
        }),
      })
      setNotice(reflectionByInterview.has(form.interviewId) ? '心得已更新。' : '心得已保存，并计入成长趋势。')
      handledInterviewParam.current = form.interviewId
      setEditorOpen(false)
      setSearchParams({}, { replace: true })
      setLoadVersion(value => value + 1)
    } catch (reason) {
      setFormError(reason instanceof Error ? reason.message : '心得保存失败，请稍后重试。')
    } finally {
      setSaving(false)
    }
  }

  const completionRate = eligibleInterviews.length
    ? Math.round((reflections.length / eligibleInterviews.length) * 100)
    : 0
  const change = summary?.changeFromPrevious
  const ChangeIcon = (change ?? 0) >= 0 ? TrendingUp : TrendingDown

  return <div className="space-y-6">
    <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
      <div>
        <p className="text-sm font-semibold text-[var(--accent)]">面试复盘</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight md:text-4xl">留存所思，见证成长</h1>
        <p className="mt-3 max-w-2xl text-muted-foreground">结合自我感受与 AI 评测，沉淀经验并明确下一步行动。</p>
      </div>
      <Button disabled={!eligibleInterviews.length || loading} onClick={() => openEditor()}>
        <Plus className="h-4 w-4" />记录面试心得
      </Button>
    </header>

    {notice && <div role="status" aria-live="polite" className="flex items-center gap-2 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/30 dark:text-emerald-200">
      <CheckCircle2 className="h-4 w-4 shrink-0" />{notice}
    </div>}
    {error && <div role="alert" className="flex flex-col gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/30 dark:text-rose-200 sm:flex-row sm:items-center">
      <span className="min-w-0 flex-1">{error}</span>
      <Button variant="ghost" className="h-9 self-start px-3 text-current sm:self-auto" onClick={() => setLoadVersion(value => value + 1)}>
        <RefreshCw className="h-4 w-4" />重新加载
      </Button>
    </div>}

    {loading && !summary ? <div className="grid animate-pulse gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="正在加载心得数据">
      {[0, 1, 2, 3].map(item => <div key={item} className="h-32 rounded-[24px] border border-border bg-muted/60" />)}
    </div> : <>
      <section aria-label="心得概览" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card>
          <p className="text-sm text-muted-foreground">已记录心得</p>
          <strong className="mt-4 block text-3xl">{String(summary?.reflectionCount ?? 0).padStart(2, '0')}</strong>
          <p className="mt-3 text-xs text-muted-foreground">共 {eligibleInterviews.length} 场可复盘面试</p>
        </Card>
        <Card>
          <p className="text-sm text-muted-foreground">最新自评分</p>
          <strong className="mt-4 block text-3xl">{summary?.latestSelfScore ?? '--'}</strong>
          <p className="mt-3 flex items-center gap-1 text-xs text-muted-foreground">
            {change === undefined || change === null ? <CircleGauge className="h-3.5 w-3.5" /> : <ChangeIcon className="h-3.5 w-3.5" />}
            {signedScore(change)}
          </p>
        </Card>
        <Card>
          <p className="text-sm text-muted-foreground">平均信心程度</p>
          <strong className="mt-4 block text-3xl">{summary?.reflectionCount ? Number(summary.averageConfidenceLevel).toFixed(1) : '--'}<small className="ml-1 text-sm font-medium text-muted-foreground">/ 5</small></strong>
          <p className="mt-3 text-xs text-muted-foreground">来自面试后的主观感受</p>
        </Card>
        <Card>
          <p className="text-sm text-muted-foreground">AI 平均评分</p>
          <strong className="mt-4 block text-3xl">{summary?.averageAiScore === undefined || summary?.averageAiScore === null ? '--' : Math.round(summary.averageAiScore)}</strong>
          <p className="mt-3 text-xs text-muted-foreground">仅统计已发布的评测报告</p>
        </Card>
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.45fr)_minmax(300px,.55fr)]">
        <Card className="min-w-0">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <p className="text-sm font-semibold text-[var(--accent)]">成长可视化</p>
              <h2 className="mt-1 text-xl font-bold">自我感受与评测趋势</h2>
              <p className="mt-1 text-sm text-muted-foreground">对照两类评分，识别信心与实际表现之间的变化。</p>
            </div>
            {reflections.length > 0 && <Badge tone={change === undefined || change === null || change >= 0 ? 'success' : 'warning'}>{signedScore(change)}</Badge>}
          </div>
          {reflections.length
            ? <ReflectionTrend reflections={reflections} />
            : <div className="mt-6 flex min-h-56 flex-col items-center justify-center rounded-2xl border border-dashed border-border bg-background/55 p-6 text-center">
              <NotebookPen className="h-8 w-8 text-[var(--accent)]" />
              <h3 className="mt-4 font-semibold">完成首次复盘后生成趋势</h3>
              <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">记录自评分与信心程度后，系统会持续展示成长变化。</p>
              {eligibleInterviews.length > 0 && <Button variant="secondary" className="mt-5" onClick={() => openEditor()}>记录第一篇心得</Button>}
            </div>}
        </Card>

        <Card className="flex flex-col">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-sm font-semibold text-[var(--accent)]">复盘完成度</p>
              <h2 className="mt-1 text-xl font-bold">{completionRate}%</h2>
            </div>
            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]">
              <Target className="h-5 w-5" />
            </span>
          </div>
          <div className="mt-5 h-2 overflow-hidden rounded-full bg-muted" role="progressbar" aria-label="面试复盘完成度" aria-valuemin={0} aria-valuemax={100} aria-valuenow={completionRate}>
            <div className="h-full rounded-full bg-[var(--accent)] transition-all" style={{ width: `${completionRate}%` }} />
          </div>
          <p className="mt-3 text-sm text-muted-foreground">
            {pendingInterviews.length ? `还有 ${pendingInterviews.length} 场面试等待复盘。` : eligibleInterviews.length ? '所有已结束面试均已完成复盘。' : '完成面试后即可开始复盘。'}
          </p>
          <div className="mt-5 space-y-2">
            {pendingInterviews.slice(0, 3).map(item => <button
              key={item.id}
              type="button"
              onClick={() => openEditor(item.id)}
              className="flex w-full items-center justify-between gap-3 rounded-2xl border border-border px-4 py-3 text-left text-sm transition hover:border-[var(--accent)] hover:bg-[var(--accent-soft)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]"
            >
              <span className="min-w-0">
                <strong className="block truncate">{item.title}</strong>
                <span className="mt-1 block text-xs text-muted-foreground">{shortDateText(item.scheduledAt)}</span>
              </span>
              <ArrowRight className="h-4 w-4 shrink-0" />
            </button>)}
          </div>
          {!pendingInterviews.length && <div className="mt-6 flex flex-1 flex-col items-center justify-center rounded-2xl bg-muted/35 p-5 text-center">
            <CheckCircle2 className="h-7 w-7 text-[var(--accent)]" />
            <p className="mt-3 text-sm font-semibold">{eligibleInterviews.length ? '复盘记录完整' : '暂无待复盘面试'}</p>
          </div>}
        </Card>
      </div>

      <Card>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-sm font-semibold text-[var(--accent)]">复盘档案</p>
            <h2 className="mt-1 text-xl font-bold">面试心得记录</h2>
            <p className="mt-1 text-sm text-muted-foreground">按面试时间倒序展示，可随时补充与调整行动计划。</p>
          </div>
          <span className="text-sm text-muted-foreground">共 {reflections.length} 篇</span>
        </div>

        {reflections.length ? <div className="mt-6 space-y-4">
          {[...reflections].reverse().map(item => <article key={item.reflectionId} className="rounded-[22px] border border-border bg-background/45 p-5 md:p-6">
            <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone="info"><CalendarDays className="mr-1 h-3.5 w-3.5" />{dateText(item.scheduledAt)}</Badge>
                  <Badge>信心 {item.confidenceLevel}/5</Badge>
                </div>
                <h3 className="mt-3 text-lg font-bold">{item.interviewTitle}</h3>
              </div>
              <div className="flex items-center gap-4">
                <div className="text-center">
                  <strong className="block text-xl">{Math.round(item.selfScore)}</strong>
                  <span className="text-xs text-muted-foreground">自评分</span>
                </div>
                <div className="text-center">
                  <strong className="block text-xl">{item.aiScore === undefined || item.aiScore === null ? '--' : Math.round(item.aiScore)}</strong>
                  <span className="text-xs text-muted-foreground">AI 评分</span>
                </div>
                <Button variant="ghost" className="h-10 px-3" aria-label={`编辑${item.interviewTitle}的心得`} onClick={() => openEditor(String(item.interviewId))}>
                  <Edit3 className="h-4 w-4" />编辑
                </Button>
              </div>
            </div>
            <p className="mt-5 whitespace-pre-wrap text-sm leading-7 text-foreground/85">{item.content}</p>
            {(item.highlights || item.improvements || item.actionPlan) && <div className="mt-5 grid gap-3 md:grid-cols-3">
              {item.highlights && <div className="rounded-2xl bg-emerald-50 p-4 dark:bg-emerald-950/25">
                <p className="flex items-center gap-2 text-sm font-semibold text-emerald-800 dark:text-emerald-200"><Sparkles className="h-4 w-4" />表现亮点</p>
                <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{item.highlights}</p>
              </div>}
              {item.improvements && <div className="rounded-2xl bg-amber-50 p-4 dark:bg-amber-950/25">
                <p className="flex items-center gap-2 text-sm font-semibold text-amber-800 dark:text-amber-200"><Lightbulb className="h-4 w-4" />待改进项</p>
                <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{item.improvements}</p>
              </div>}
              {item.actionPlan && <div className="rounded-2xl bg-[var(--accent-soft)] p-4">
                <p className="flex items-center gap-2 text-sm font-semibold text-[var(--accent)]"><Target className="h-4 w-4" />下一步计划</p>
                <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{item.actionPlan}</p>
              </div>}
            </div>}
          </article>)}
        </div> : <div className="mt-6 rounded-2xl border border-dashed border-border py-12 text-center text-sm text-muted-foreground">
          暂无面试心得。完成面试后，记录当时的判断与感受会更有价值。
        </div>}
      </Card>
    </>}

    {editorOpen && <div className="fixed inset-0 z-50 grid items-end bg-black/45 p-0 backdrop-blur-sm sm:place-items-center sm:p-5" role="dialog" aria-modal="true" aria-labelledby="reflection-editor-title">
      <button type="button" className="absolute inset-0 h-full w-full cursor-default" aria-label="关闭心得编辑器" onClick={closeEditor} />
      <section className="relative z-10 max-h-[94vh] w-full max-w-3xl overflow-y-auto rounded-t-[28px] border border-border bg-surface p-5 shadow-2xl sm:rounded-[28px] sm:p-7">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-sm font-semibold text-[var(--accent)]">面试复盘</p>
            <h2 id="reflection-editor-title" className="mt-1 text-2xl font-bold">
              {reflectionByInterview.has(form.interviewId) ? '编辑面试心得' : '记录面试心得'}
            </h2>
            <p className="mt-2 text-sm text-muted-foreground">用具体事实回顾表现，并为下一次面试确定一个可执行目标。</p>
          </div>
          <Button type="button" variant="ghost" className="h-10 w-10 shrink-0 px-0" aria-label="关闭" onClick={closeEditor}>
            <X className="h-5 w-5" />
          </Button>
        </div>

        <form className="mt-7 space-y-6" onSubmit={saveReflection}>
          <label className="block">
            <span className="text-sm font-semibold">选择面试</span>
            <ResponsiveSelect
              ariaLabel="选择面试"
              value={form.interviewId}
              onValueChange={chooseInterview}
              className="mt-2 w-full"
              options={eligibleInterviews.map(item => ({ value: String(item.id), label: `${item.title} · ${shortDateText(item.scheduledAt)}` }))}
            />
          </label>

          <div className="grid gap-6 md:grid-cols-2">
            <div>
              <div className="flex items-center justify-between gap-3">
                <label htmlFor="reflection-score" className="text-sm font-semibold">本次自评分</label>
                <output htmlFor="reflection-score" className="rounded-full bg-[var(--accent-soft)] px-3 py-1 text-sm font-bold text-[var(--accent)]">{form.selfScore} 分</output>
              </div>
              <input
                id="reflection-score"
                type="range"
                min={0}
                max={100}
                step={1}
                value={form.selfScore}
                onChange={event => updateField('selfScore', Number(event.target.value))}
                className="mt-4 w-full accent-[var(--accent)]"
              />
              <div className="mt-1 flex justify-between text-xs text-muted-foreground"><span>需要加强</span><span>表现出色</span></div>
            </div>
            <fieldset>
              <legend className="text-sm font-semibold">面试时的信心程度</legend>
              <div className="mt-3 grid grid-cols-5 gap-2">
                {[1, 2, 3, 4, 5].map(level => <button
                  key={level}
                  type="button"
                  aria-pressed={form.confidenceLevel === level}
                  aria-label={`${level} 分，${confidenceLabels[level - 1]}`}
                  onClick={() => updateField('confidenceLevel', level)}
                  className={`min-h-11 rounded-xl border text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] ${form.confidenceLevel === level ? 'border-[var(--accent)] bg-[var(--accent-soft)] text-[var(--accent)]' : 'border-border hover:bg-muted'}`}
                >{level}</button>)}
              </div>
              <p className="mt-2 text-xs text-muted-foreground">{confidenceLabels[form.confidenceLevel - 1]}</p>
            </fieldset>
          </div>

          <label className="block">
            <span className="flex items-center justify-between gap-3 text-sm font-semibold">
              核心心得 <span className="text-xs font-normal text-muted-foreground">{form.content.length}/2000</span>
            </span>
            <textarea
              value={form.content}
              maxLength={2000}
              rows={5}
              required
              onChange={event => updateField('content', event.target.value)}
              placeholder="例如：这次在项目经历追问中，结论明确，但缺少量化结果。下次先用一句话概括，再按背景、行动和结果展开。"
              className="mt-2 w-full resize-y rounded-2xl border border-border bg-background px-4 py-3 text-sm leading-6 outline-none transition placeholder:text-muted-foreground/70 focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--brand)]/20"
            />
          </label>

          <div className="grid gap-4 md:grid-cols-3">
            {([
              ['highlights', '表现亮点', '哪些回答或处理方式值得保留？'],
              ['improvements', '待改进项', '哪些地方不够清晰或充分？'],
              ['actionPlan', '下一步计划', '下一次面试前完成什么？'],
            ] as const).map(([key, label, placeholder]) => <label key={key} className="block">
              <span className="flex items-center justify-between gap-2 text-sm font-semibold">
                {label}<span className="text-xs font-normal text-muted-foreground">{form[key].length}/1000</span>
              </span>
              <textarea
                value={form[key]}
                maxLength={1000}
                rows={4}
                onChange={event => updateField(key, event.target.value)}
                placeholder={placeholder}
                className="mt-2 w-full resize-y rounded-2xl border border-border bg-background px-4 py-3 text-sm leading-6 outline-none transition placeholder:text-muted-foreground/70 focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--brand)]/20"
              />
            </label>)}
          </div>

          {formError && <p role="alert" className="rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:bg-rose-950/30 dark:text-rose-200">{formError}</p>}
          <div className="flex flex-col-reverse gap-3 border-t border-border pt-5 sm:flex-row sm:justify-end">
            <Button type="button" variant="ghost" onClick={closeEditor}>取消</Button>
            <Button type="submit" disabled={saving}>
              <NotebookPen className="h-4 w-4" />{saving ? '正在保存…' : reflectionByInterview.has(form.interviewId) ? '保存修改' : '保存心得'}
            </Button>
          </div>
        </form>
      </section>
    </div>}
  </div>
}
