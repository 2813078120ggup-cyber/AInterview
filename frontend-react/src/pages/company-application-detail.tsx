import * as Dialog from '@radix-ui/react-dialog'
import * as pdfjsLib from 'pdfjs-dist'
import type { PDFDocumentProxy, RenderTask } from 'pdfjs-dist'
import workerSrc from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { AlertTriangle, ArrowLeft, BookmarkPlus, CalendarClock, CheckCircle2, ChevronLeft, ChevronRight, CircleDot, Clock3, Download, ExternalLink, FileText, Loader2, Mail, Minus, Phone, Play, Plus, RotateCcw, ShieldCheck, Sparkles, UserRound, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState, type Dispatch, type FormEvent, type KeyboardEvent, type ReactNode, type SetStateAction } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { RecruitmentMatchEvaluation } from '@/components/recruitment-match-evaluation'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request, requestBlob } from '@/lib/api'
import { applicationStatusMeta, formatDateTime, type ApplicationInterview, type ApplicationStatus, type ApplicationStatusTransition, type ApplicationTimelineEvent, type CompanyReportDetail, type CompanyResumeAnalysis, type InterviewQuestionBank, type JobApplication, type MatchEvaluation, type PageResult, type TalentPoolMembership } from '@/lib/recruitment'

pdfjsLib.GlobalWorkerOptions.workerSrc = workerSrc

type InviteForm = { scheduledAt: string; durationMinutes: string; interviewType: string; location: string; meetingUrl: string; contactName: string; contactPhone: string; note: string }
type AiInviteForm = { scheduledAt: string; durationMinutes: string; type: string; questionBankId: string; questionCount: string; interviewerStyle: string; remark: string }
type DetailTab = 'overview' | 'profile' | 'match' | 'interview' | 'report' | 'timeline'
type TabState<T> = { loaded: boolean; loading: boolean; data?: T; error: string }

const emptyInvite: InviteForm = { scheduledAt: '', durationMinutes: '60', interviewType: 'ONSITE', location: '', meetingUrl: '', contactName: '', contactPhone: '', note: '' }
const emptyAiInvite: AiInviteForm = { scheduledAt: '', durationMinutes: '30', type: 'tech', questionBankId: '', questionCount: '5', interviewerStyle: 'big-tech', remark: '' }
const inputClass = 'mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 text-sm outline-none focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--brand)]/20'
const tabItems: { value: DetailTab; label: string }[] = [
  { value: 'overview', label: '概览' },
  { value: 'profile', label: '简历与画像' },
  { value: 'match', label: '岗位匹配' },
  { value: 'interview', label: '面试' },
  { value: 'report', label: '评估报告' },
  { value: 'timeline', label: '时间线' },
]

function stageMeta(status: string) {
  return applicationStatusMeta[status as ApplicationStatus] ?? { label: '处理中', tone: 'default' as const }
}

function interviewMeta(status?: string) {
  const labels: Record<string, { label: string; tone: 'default' | 'success' | 'warning' | 'danger' | 'info' }> = {
    NONE: { label: '未安排面试', tone: 'default' }, AI_PENDING: { label: 'AI 待开始', tone: 'warning' }, AI_IN_PROGRESS: { label: 'AI 面试中', tone: 'info' }, AI_COMPLETED: { label: 'AI 已完成', tone: 'success' }, AI_CANCELLED: { label: 'AI 已取消', tone: 'danger' }, OFFLINE_SCHEDULED: { label: '线下已安排', tone: 'warning' }, OFFLINE_COMPLETED: { label: '线下已完成', tone: 'success' }, OFFLINE_CANCELLED: { label: '线下已取消', tone: 'danger' },
  }
  return labels[status || 'NONE'] ?? { label: status || '未安排面试', tone: 'default' as const }
}

function readTab(value: string | null): DetailTab {
  return tabItems.some(item => item.value === value) ? value as DetailTab : 'overview'
}

function tabState<T>(): TabState<T> {
  return { loaded: false, loading: false, error: '' }
}

export function CompanyApplicationDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const activeTab = readTab(searchParams.get('tab'))
  const listSearch = useMemo(() => {
    const next = new URLSearchParams(searchParams)
    next.delete('tab')
    return next.toString()
  }, [searchParams])
  const [selected, setSelected] = useState<JobApplication>()
  const [profileState, setProfileState] = useState<TabState<CompanyResumeAnalysis>>(tabState)
  const [matchState, setMatchState] = useState<TabState<MatchEvaluation>>(tabState)
  const [matchHistory, setMatchHistory] = useState<TabState<PageResult<MatchEvaluation>>>(tabState)
  const [interviewState, setInterviewState] = useState<TabState<ApplicationInterview>>(tabState)
  const [reportState, setReportState] = useState<TabState<CompanyReportDetail>>(tabState)
  const [timelineState, setTimelineState] = useState<TabState<ApplicationTimelineEvent[]>>(tabState)
  const [talentPoolState, setTalentPoolState] = useState<{ loading: boolean; data?: TalentPoolMembership; error: string }>({ loading: false, error: '' })
  const [reviewNote, setReviewNote] = useState('')
  const [invite, setInvite] = useState<InviteForm>(emptyInvite)
  const [inviteOpen, setInviteOpen] = useState(false)
  const [aiInvite, setAiInvite] = useState<AiInviteForm>(emptyAiInvite)
  const [aiInviteOpen, setAiInviteOpen] = useState(false)
  const [questionBanks, setQuestionBanks] = useState<InterviewQuestionBank[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [reportBusy, setReportBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const loadDetail = useCallback(async () => {
    if (!id) return
    setLoading(true)
    setError('')
    try {
      const detail = await request<JobApplication>(`/v1/company/recruitment/applications/${id}`)
      setSelected(detail)
      setReviewNote(detail.reviewNote || '')
    } catch (reason) {
      setSelected(undefined)
      setError(reason instanceof Error ? reason.message : '申请详情加载失败')
    } finally { setLoading(false) }
  }, [id])

  useEffect(() => { void loadDetail() }, [loadDetail])

  const loadTalentPoolMembership = useCallback(async () => {
    if (!selected?.candidateId) return
    setTalentPoolState(current => ({ ...current, loading: true, error: '' }))
    try {
      const data = await request<TalentPoolMembership>(`/v1/company/recruitment/talent-pool/candidates/${selected.candidateId}/membership`)
      setTalentPoolState({ loading: false, data, error: '' })
    } catch (reason) {
      setTalentPoolState({ loading: false, error: reason instanceof Error ? reason.message : '人才库状态加载失败' })
    }
  }, [selected?.candidateId])

  useEffect(() => { void loadTalentPoolMembership() }, [loadTalentPoolMembership])

  async function addToTalentPool() {
    if (!selected) return
    setBusy(true); setError(''); setMessage('')
    try {
      const data = await request<TalentPoolMembership>(`/v1/company/recruitment/talent-pool/candidates/${selected.candidateId}`, { method: 'POST' })
      setTalentPoolState({ loading: false, data, error: '' })
      setMessage('候选人已加入人才库，可以继续添加企业备注和标签。')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '加入人才库失败')
    } finally { setBusy(false) }
  }

  const loadProfile = useCallback(async (force = false) => {
    if (!id || (profileState.loaded && !force)) return
    setProfileState(current => ({ ...current, loading: true, error: '' }))
    try {
      const data = await request<CompanyResumeAnalysis>(`/v1/company/recruitment/applications/${id}/resume/analysis`)
      setProfileState({ loaded: true, loading: false, data, error: '' })
    } catch (reason) {
      setProfileState({ loaded: true, loading: false, error: reason instanceof Error ? reason.message : '简历画像加载失败' })
    }
  }, [id, profileState.loaded])

  const retryProfileAnalysis = useCallback(async () => {
    if (!id) return
    setProfileState(current => ({ ...current, loading: true, error: '' }))
    try {
      await request(`/v1/company/recruitment/applications/${id}/resume/analysis/retry`, { method: 'POST' })
      await loadProfile(true)
    } catch (reason) {
      setProfileState({ loaded: true, loading: false, error: reason instanceof Error ? reason.message : '解析重试失败，请稍后重试。' })
    }
  }, [id, loadProfile])

  useEffect(() => {
    const status = profileState.data?.status
    if (activeTab !== 'profile' || !status || !['PENDING', 'PROCESSING'].includes(status)) return
    let timer: number | undefined
    const schedule = () => {
      if (document.visibilityState !== 'visible') return
      timer = window.setTimeout(() => { void loadProfile(true) }, 5000)
    }
    const onVisibilityChange = () => {
      if (timer !== undefined) window.clearTimeout(timer)
      timer = undefined
      schedule()
    }
    document.addEventListener('visibilitychange', onVisibilityChange)
    schedule()
    return () => {
      if (timer !== undefined) window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [activeTab, loadProfile, profileState.data?.status])

  const loadMatch = useCallback(async (force = false) => {
    if (!id || (matchState.loaded && matchHistory.loaded && !force)) return
    setMatchState(current => ({ ...current, loading: true, error: '' }))
    setMatchHistory(current => ({ ...current, loading: true, error: '' }))
    const [evaluationResult, historyResult] = await Promise.allSettled([
      request<MatchEvaluation>(`/v1/company/recruitment/applications/${id}/match`),
      request<PageResult<MatchEvaluation>>(`/v1/company/recruitment/applications/${id}/match/history?pageNo=1&pageSize=5`),
    ])
    if (evaluationResult.status === 'fulfilled') setMatchState({ loaded: true, loading: false, data: evaluationResult.value, error: '' })
    else setMatchState({ loaded: true, loading: false, error: evaluationResult.reason instanceof Error ? evaluationResult.reason.message : '岗位匹配加载失败' })
    if (historyResult.status === 'fulfilled') setMatchHistory({ loaded: true, loading: false, data: historyResult.value, error: '' })
    else setMatchHistory({ loaded: true, loading: false, error: historyResult.reason instanceof Error ? historyResult.reason.message : '匹配历史加载失败' })
  }, [id, matchHistory.loaded, matchState.loaded])

  const loadInterview = useCallback(async (force = false) => {
    if (!id || (interviewState.loaded && !force)) return
    setInterviewState(current => ({ ...current, loading: true, error: '' }))
    try {
      const data = await request<ApplicationInterview>(`/v1/company/recruitment/applications/${id}/interview`)
      setInterviewState({ loaded: true, loading: false, data, error: '' })
    } catch (reason) {
      setInterviewState({ loaded: true, loading: false, error: reason instanceof Error ? reason.message : '面试信息加载失败' })
    }
  }, [id, interviewState.loaded])

  const loadReport = useCallback(async (force = false) => {
    if (!id || (reportState.loaded && !force)) return
    setReportState(current => ({ ...current, loading: true, error: '' }))
    try {
      const data = await request<CompanyReportDetail>(`/v1/company/recruitment/applications/${id}/report`)
      setReportState({ loaded: true, loading: false, data, error: '' })
    } catch (reason) {
      setReportState({ loaded: true, loading: false, error: reason instanceof Error ? reason.message : '评估报告加载失败' })
    }
  }, [id, reportState.loaded])

  useEffect(() => {
    const status = reportState.data?.reportStatus
    if (activeTab !== 'report' || !status || !['PENDING', 'RUNNING'].includes(status)) return
    let timer: number | undefined
    const schedule = () => {
      if (document.visibilityState !== 'visible') return
      timer = window.setTimeout(() => { void loadReport(true) }, 5000)
    }
    const onVisibilityChange = () => {
      if (timer !== undefined) window.clearTimeout(timer)
      timer = undefined
      schedule()
    }
    document.addEventListener('visibilitychange', onVisibilityChange)
    schedule()
    return () => {
      if (timer !== undefined) window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [activeTab, loadReport, reportState.data?.reportStatus])

  async function reportAction(action: 'retry' | 'publish') {
    if (!selected) return
    if (action === 'publish' && !window.confirm('发布后候选人将可以查看这份报告，HR 内部查看不会自动发布。确认发布吗？')) return
    setReportBusy(true); setError(''); setMessage('')
    try {
      const data = await request<CompanyReportDetail>(`/v1/company/recruitment/applications/${selected.id}/report/${action}`, { method: 'POST' })
      setReportState({ loaded: true, loading: false, data, error: '' })
      setMessage(action === 'publish' ? '报告已发布给候选人。' : '已重新提交报告生成任务。')
      setTimelineState(tabState())
    } catch (reason) {
      setReportState(current => ({ ...current, loaded: true, loading: false, error: reason instanceof Error ? reason.message : '报告操作失败，请稍后重试。' }))
    } finally { setReportBusy(false) }
  }

  const loadTimeline = useCallback(async (force = false) => {
    if (!id || (timelineState.loaded && !force)) return
    setTimelineState(current => ({ ...current, loading: true, error: '' }))
    try {
      const data = await request<ApplicationTimelineEvent[]>(`/v1/company/recruitment/applications/${id}/timeline`)
      setTimelineState({ loaded: true, loading: false, data, error: '' })
    } catch (reason) {
      setTimelineState({ loaded: true, loading: false, error: reason instanceof Error ? reason.message : '时间线加载失败' })
    }
  }, [id, timelineState.loaded])

  useEffect(() => {
    if (activeTab === 'profile') void loadProfile()
    if (activeTab === 'match') void loadMatch()
    if (activeTab === 'interview') void loadInterview()
    if (activeTab === 'report') void loadReport()
    if (activeTab === 'timeline') void loadTimeline()
  }, [activeTab, loadInterview, loadMatch, loadProfile, loadReport, loadTimeline])

  function selectTab(tab: DetailTab) {
    setSearchParams(current => {
      const next = new URLSearchParams(current)
      if (tab === 'overview') next.delete('tab')
      else next.set('tab', tab)
      return next
    }, { replace: true })
  }

  function onTabKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
    event.preventDefault()
    const nextIndex = event.key === 'ArrowLeft' ? (index + tabItems.length - 1) % tabItems.length
      : event.key === 'ArrowRight' ? (index + 1) % tabItems.length
        : event.key === 'Home' ? 0 : tabItems.length - 1
    const next = tabItems[nextIndex].value
    selectTab(next)
    window.setTimeout(() => document.getElementById(`application-tab-${next}`)?.focus(), 0)
  }

  async function applyTransition(transition: ApplicationStatusTransition) {
    if (!selected) return
    if (transition.requiresNote && !reviewNote.trim()) {
      setError(`转为“${transition.label}”前请填写审核备注或变更原因。`)
      selectTab('overview')
      return
    }
    if (transition.status === 'REJECTED' || transition.status === 'HIRED') {
      const confirmed = window.confirm(`即将把 ${selected.candidateName} 标记为“${transition.label}”。该操作会进入终态，确认继续吗？`)
      if (!confirmed) return
    }
    setBusy(true); setError(''); setMessage('')
    try {
      const detail = await request<JobApplication>(`/v1/company/recruitment/applications/${selected.id}/status`, { method: 'PUT', body: JSON.stringify({ status: transition.status, note: reviewNote.trim() || undefined }) })
      setSelected(detail)
      setReviewNote(detail.reviewNote || reviewNote)
      setMessage(`“${detail.candidateName}”已进入${stageMeta(detail.status).label}。`)
      setTimelineState(tabState())
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '阶段推进失败，请刷新后重试')
    } finally { setBusy(false) }
  }

  async function triggerTransition(transition: ApplicationStatusTransition) {
    if (transition.status === 'AI_INTERVIEW_PENDING' && !selected?.interviewId) return openAiInvite()
    if (transition.status === 'OFFLINE_INTERVIEW' && !selected?.offlineInterview) { setInviteOpen(true); return }
    await applyTransition(transition)
  }

  async function retryMatch() {
    if (!selected) return
    setBusy(true); setError(''); setMessage('')
    try {
      const detail = await request<JobApplication>(`/v1/company/recruitment/applications/${selected.id}/match/retry`, { method: 'POST' })
      setSelected(detail); setMatchState(tabState()); setMatchHistory(tabState()); setMessage('已重新提交岗位匹配分析。')
    } catch (reason) { setError(reason instanceof Error ? reason.message : '匹配重试失败') } finally { setBusy(false) }
  }

  async function openAiInvite() {
    if (!selected) return
    setBusy(true); setError('')
    try {
      const banks = await request<InterviewQuestionBank[]>('/v1/company/recruitment/interview-question-banks')
      setQuestionBanks(banks)
      setAiInvite(current => ({ ...current, questionBankId: current.questionBankId || banks[0]?.id || '' }))
      setAiInviteOpen(true)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '公开题库加载失败') } finally { setBusy(false) }
  }

  async function sendAiInvite(event: FormEvent) {
    event.preventDefault()
    if (!selected) return
    setBusy(true); setError('')
    try {
      const detail = await request<JobApplication>(`/v1/company/recruitment/applications/${selected.id}/ai-interview`, { method: 'POST', body: JSON.stringify({ ...aiInvite, durationMinutes: Number(aiInvite.durationMinutes), questionCount: Number(aiInvite.questionCount), questionBankId: aiInvite.questionBankId }) })
      setSelected(detail); setAiInviteOpen(false); setAiInvite(emptyAiInvite); setInterviewState(tabState()); setTimelineState(tabState()); setMessage(`AI 面试已安排并通知${detail.candidateName}。`)
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'AI 面试安排失败') } finally { setBusy(false) }
  }

  async function sendOfflineInvite(event: FormEvent) {
    event.preventDefault()
    if (!selected) return
    setBusy(true); setError('')
    try {
      const detail = await request<JobApplication>(`/v1/company/recruitment/applications/${selected.id}/offline-interview`, { method: 'POST', body: JSON.stringify({ ...invite, durationMinutes: Number(invite.durationMinutes) }) })
      setSelected(detail); setInviteOpen(false); setInvite(emptyInvite); setInterviewState(tabState()); setTimelineState(tabState()); setMessage(`线下面试邀请已发送给${detail.candidateName}。`)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '面试邀请发送失败') } finally { setBusy(false) }
  }

  function goBack() {
    navigate({ pathname: '/company/applications', search: listSearch ? `?${listSearch}` : '' })
  }

  const availableTransitions = selected?.allowedTransitions ?? []
  const mainAction = availableTransitions[0]

  if (loading && !selected) return <div className="grid min-h-64 place-items-center"><Loader2 className="h-7 w-7 animate-spin text-muted-foreground" /></div>
  if (!selected) return <Card className="grid min-h-64 place-items-center text-center"><div><FileText className="mx-auto h-8 w-8 text-muted-foreground" /><h1 className="mt-3 text-xl font-black">申请详情不可用</h1><p className="mt-2 text-sm text-muted-foreground">{error || '申请不存在或已被移除。'}</p><div className="mt-5 flex flex-wrap justify-center gap-2"><Button type="button" variant="secondary" onClick={goBack}>返回流程中心</Button><Button type="button" onClick={() => void loadDetail()}>重试</Button></div></div></Card>

  const stage = stageMeta(selected.status)
  const interview = interviewMeta(selected.interviewStatus)

  return <div className="space-y-5">
    <header className="rounded-[28px] border border-border/80 bg-surface p-5 shadow-[0_16px_40px_rgba(20,18,17,.05)] sm:p-7">
      <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
        <div className="min-w-0">
          <Button type="button" variant="ghost" className="-ml-3 h-9 px-3" onClick={goBack}><ArrowLeft className="h-4 w-4" />返回流程中心</Button>
          <div className="mt-5 flex items-start gap-3"><span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><UserRound className="h-5 w-5" /></span><div className="min-w-0"><p className="break-words text-sm font-bold text-[var(--accent)]">{selected.positionName}</p><h1 className="mt-1 break-words text-3xl font-black tracking-[-.04em] sm:text-4xl">{selected.candidateName}</h1><p className="mt-2 break-words text-sm text-muted-foreground">{selected.applicationNo} · 投递于 {formatDateTime(selected.submittedAt)}</p></div></div>
        </div>
        <div className="flex flex-col gap-3 xl:min-w-[280px] xl:items-end"><div className="flex flex-wrap gap-2 xl:justify-end"><Badge tone={stage.tone}>{stage.label}</Badge><Badge tone={interview.tone}>{interview.label}</Badge><span className="rounded-full bg-[var(--accent-soft)] px-3 py-1.5 text-sm font-black tabular-nums text-[var(--accent)]">匹配 {selected.matchScore == null ? '待评估' : `${selected.matchScore}%`}</span></div>{mainAction ? <Button type="button" className="w-full sm:w-auto" disabled={busy} onClick={() => void triggerTransition(mainAction)}><Sparkles className="h-4 w-4" />{mainAction.label}{mainAction.requiresNote ? '（需备注）' : ''}</Button> : <span className="text-sm font-semibold text-muted-foreground">流程已结束，无待处理动作</span>}</div>
      </div>
      <div className="mt-6 grid gap-3 border-t border-border pt-4 text-sm sm:grid-cols-3"><MetaItem label="当前阶段" value={stage.label} /><MetaItem label="最近活动" value={formatDateTime(selected.recentActivityAt || selected.updatedAt)} /><MetaItem label="主要下一步" value={selected.nextStep || '查看申请详情'} /></div>
    </header>

    {error && <div role="alert" className="flex items-start justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/70 dark:bg-rose-950/30 dark:text-rose-200"><span className="min-w-0 break-words">{error}</span><button type="button" className="shrink-0 font-semibold underline" onClick={() => { setError(''); void loadDetail() }}>重试</button></div>}
    {message && <p role="status" className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 dark:border-emerald-900/70 dark:bg-emerald-950/30 dark:text-emerald-100"><CheckCircle2 className="mr-2 inline h-4 w-4" />{message}</p>}

    <div role="tablist" aria-label="申请详情标签" className="flex gap-1 overflow-x-auto rounded-2xl border border-border bg-surface p-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
      {tabItems.map((item, index) => <button id={`application-tab-${item.value}`} key={item.value} type="button" role="tab" aria-selected={activeTab === item.value} aria-controls={`application-panel-${item.value}`} tabIndex={activeTab === item.value ? 0 : -1} onClick={() => selectTab(item.value)} onKeyDown={event => onTabKeyDown(event, index)} className={'min-h-11 shrink-0 rounded-xl px-3 text-sm font-semibold transition sm:px-4 ' + (activeTab === item.value ? 'bg-[var(--accent)] text-white shadow-sm' : 'text-muted-foreground hover:bg-muted hover:text-foreground')}>{item.label}</button>)}
    </div>

    <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_300px]">
      <main id={`application-panel-${activeTab}`} role="tabpanel" aria-labelledby={`application-tab-${activeTab}`} tabIndex={-1} className="min-w-0">
        {activeTab === 'overview' && <OverviewTab selected={selected} reviewNote={reviewNote} setReviewNote={setReviewNote} transitions={availableTransitions} busy={busy} onTransition={triggerTransition} talentPool={talentPoolState} onAddToTalentPool={addToTalentPool} />}
        {activeTab === 'profile' && <ProfileTab selected={selected} state={profileState} retry={() => void loadProfile(true)} retryAnalysis={() => void retryProfileAnalysis()} />}
        {activeTab === 'match' && <MatchTab selected={selected} evaluation={matchState} history={matchHistory} busy={busy} retry={() => void loadMatch(true)} retryMatch={() => void retryMatch()} />}
        {activeTab === 'interview' && <InterviewTab state={interviewState} retry={() => void loadInterview(true)} />}
        {activeTab === 'report' && <ReportTab state={reportState} retry={() => void loadReport(true)} actionBusy={reportBusy} onRetry={() => void reportAction('retry')} onPublish={() => void reportAction('publish')} />}
        {activeTab === 'timeline' && <TimelineTab state={timelineState} retry={() => void loadTimeline(true)} />}
      </main>
      <aside className="h-fit space-y-4 lg:sticky lg:top-6">
        <ActionCard selected={selected} transitions={availableTransitions} reviewNote={reviewNote} setReviewNote={setReviewNote} busy={busy} onTransition={triggerTransition} />
        <Card className="p-5"><div className="flex items-center gap-2"><Clock3 className="h-5 w-5 text-muted-foreground" /><h2 className="font-black">最近活动</h2></div><p className="mt-3 text-sm text-muted-foreground">{formatDateTime(selected.recentActivityAt || selected.updatedAt)}</p><p className="mt-2 break-words text-sm leading-6">下一步：{selected.nextStep || '查看申请详情'}</p></Card>
      </aside>
    </div>

    <AiInviteDialog open={aiInviteOpen} setOpen={setAiInviteOpen} form={aiInvite} setForm={setAiInvite} questionBanks={questionBanks} busy={busy} onSubmit={sendAiInvite} />
    <OfflineInviteDialog open={inviteOpen} setOpen={setInviteOpen} form={invite} setForm={setInvite} busy={busy} onSubmit={sendOfflineInvite} />
  </div>
}

function MetaItem({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0"><p className="text-xs font-bold text-muted-foreground">{label}</p><p className="mt-1 break-words font-semibold">{value}</p></div>
}

function OverviewTab({ selected, reviewNote, setReviewNote, transitions, busy, onTransition, talentPool, onAddToTalentPool }: { selected: JobApplication; reviewNote: string; setReviewNote: (value: string) => void; transitions: ApplicationStatusTransition[]; busy: boolean; onTransition: (transition: ApplicationStatusTransition) => Promise<void>; talentPool: { loading: boolean; data?: TalentPoolMembership; error: string }; onAddToTalentPool: () => Promise<void> }) {
  const stage = stageMeta(selected.status)
  return <div className="space-y-4">
    <Card className="p-5 sm:p-6"><SectionHeading icon={<UserRound className="h-5 w-5" />} title="候选人联系方式" description="企业范围内可见的联系信息；缺失字段会明确标注。" /><div className="mt-5 grid gap-3 sm:grid-cols-2"><ContactItem icon={<Mail className="h-4 w-4" />} value={selected.candidateEmail || '未填写邮箱'} /><ContactItem icon={<Phone className="h-4 w-4" />} value={selected.candidatePhone || '未填写手机'} /></div></Card>
    <Card className="p-5 sm:p-6"><SectionHeading icon={<FileText className="h-5 w-5" />} title="当前申请" description="申请关系、投递信息和候选人留言。" /><div className="mt-5 grid gap-4 sm:grid-cols-2"><MetaItem label="岗位" value={selected.positionName} /><MetaItem label="申请编号" value={selected.applicationNo} /><MetaItem label="投递时间" value={formatDateTime(selected.submittedAt)} /><MetaItem label="当前阶段" value={stage.label} /></div><div className="mt-5 rounded-2xl bg-muted p-4"><p className="text-xs font-bold text-muted-foreground">候选人留言</p><p className="mt-2 break-words text-sm leading-6">{selected.candidateMessage || '候选人未填写留言。'}</p></div></Card>
    <Card className="p-5 sm:p-6"><SectionHeading icon={<Clock3 className="h-5 w-5" />} title="最近活动" description="申请摘要不包含录制媒体和简历原文；完整事件请打开时间线。" /><div className="mt-5 grid gap-4 sm:grid-cols-2"><MetaItem label="最近更新时间" value={formatDateTime(selected.recentActivityAt || selected.updatedAt)} /><MetaItem label="系统建议" value={selected.nextStep || '查看申请详情'} /></div></Card>
    <TalentPoolCard candidateId={selected.candidateId} state={talentPool} busy={busy} onAdd={onAddToTalentPool} />
    <Card className="p-5 sm:p-6"><SectionHeading icon={<CheckCircle2 className="h-5 w-5" />} title="HR 审核备注与下一步" description="状态推进只能使用后端返回的允许动作。" /><label className="mt-5 block text-sm font-bold">审核备注 / 变更原因<textarea value={reviewNote} onChange={event => setReviewNote(event.target.value)} className="mt-2 min-h-28 w-full rounded-2xl border border-border bg-background p-3 text-sm outline-none focus:border-[var(--accent)]" placeholder="进入评估、拒绝或录用时填写原因" /></label><div className="mt-4 flex flex-wrap gap-2">{transitions.length ? transitions.map(transition => <Button key={transition.status} type="button" variant={transition.status === 'REJECTED' ? 'danger' : 'secondary'} disabled={busy} onClick={() => void onTransition(transition)}>{transition.label}{transition.requiresNote ? '（需备注）' : ''}</Button>) : <p className="rounded-2xl bg-muted p-3 text-sm text-muted-foreground">该申请已进入终态，无需继续流转。</p>}</div></Card>
  </div>
}

function TalentPoolCard({ candidateId, state, busy, onAdd }: { candidateId: string; state: { loading: boolean; data?: TalentPoolMembership; error: string }; busy: boolean; onAdd: () => Promise<void> }) {
  if (state.loading) return <Card className="p-5 sm:p-6"><div className="flex items-center gap-3 text-sm text-muted-foreground"><BookmarkPlus className="h-5 w-5 animate-pulse text-[var(--accent)]" />正在检查人才库状态…</div></Card>
  if (state.error) return <Card className="p-5 sm:p-6"><SectionHeading icon={<BookmarkPlus className="h-5 w-5" />} title="企业人才库" description="人才库状态暂时无法读取。" /><p className="mt-4 break-words text-sm text-rose-700 dark:text-rose-300">{state.error}</p></Card>
  if (state.data?.active) return <Card className="border-[var(--accent)]/30 bg-[var(--accent-soft)]/35 p-5 sm:p-6"><div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between"><div className="flex items-start gap-3"><BookmarkPlus className="mt-0.5 h-5 w-5 shrink-0 text-[var(--accent)]" /><div><h2 className="font-black">已在企业人才库</h2><p className="mt-1 text-sm text-muted-foreground">可以在协作档案中维护共享备注、标签和最近联系时间。</p></div></div><Link className="inline-flex h-11 items-center justify-center gap-2 rounded-full bg-[var(--accent)] px-5 text-sm font-semibold text-white transition hover:opacity-90" to={`/company/talent-pool/${candidateId}`}>打开协作档案<ExternalLink className="h-4 w-4" /></Link></div></Card>
  return <Card className="p-5 sm:p-6"><div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between"><div className="flex items-start gap-3"><BookmarkPlus className="mt-0.5 h-5 w-5 shrink-0 text-[var(--accent)]" /><div><h2 className="font-black">加入企业人才库</h2><p className="mt-1 text-sm text-muted-foreground">保留当前申请，并为后续招聘协作建立企业私有档案。</p></div></div><Button type="button" variant="secondary" disabled={busy} onClick={() => void onAdd()}>加入人才库</Button></div></Card>
}

function ProfileTab({ selected, state, retry, retryAnalysis }: { selected: JobApplication; state: TabState<CompanyResumeAnalysis>; retry: () => void; retryAnalysis: () => void }) {
  if (state.loading) return <TabLoading label="正在加载简历画像…" />
  if (state.error) return <TabError message={state.error} retry={retry} />
  if (!selected.resume) return <EmptyPanel icon={<FileText className="h-7 w-7" />} title="暂无可用简历画像" description="该申请没有关联简历，或候选人尚未完成结构化解析。" />
  const profile = state.data
  const status = profile?.status || selected.resume.parseStatus || (selected.resume.mediaId ? 'PENDING' : 'NOT_AVAILABLE')
  if (status === 'NOT_AVAILABLE') return <EmptyPanel icon={<FileText className="h-7 w-7" />} title="暂无可用简历画像" description="该申请没有可用的结构化解析结果；历史申请摘要仍会保留。" />
  const statusMeta = resumeParseMeta(status)
  const isFailed = status === 'FAILED'
  return <div className="space-y-4">
    <Card className="p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3"><SectionHeading icon={<FileText className="h-5 w-5" />} title={selected.resume.title || '候选人简历'} description={`${selected.resume.fileName || '未提供文件名'} · 解析版本 v${profile?.analysisVersion || selected.resume.parseVersion || 1}`} /><Badge tone={statusMeta.tone}>{statusMeta.label}</Badge></div>
      <div className="mt-5 rounded-2xl bg-muted p-4"><p className="text-xs font-bold text-muted-foreground">结构化摘要</p><p className="mt-2 break-words text-sm leading-6">{selected.resume.summary || (status === 'PROCESSING' || status === 'PENDING' ? '简历已安全保存，正在提取结构化画像。' : '暂无结构化摘要。')}</p></div>
      <div className="mt-5"><p className="text-xs font-bold text-muted-foreground">技能</p><div className="mt-2 flex flex-wrap gap-2">{profile?.skills.length ? profile.skills.map(skill => <Badge key={skill} tone="info">{skill}</Badge>) : <span className="text-sm text-muted-foreground">暂无结构化技能标签。</span>}</div></div>
      <ResumeFilePanel applicationId={String(selected.id)} fileName={selected.resume.fileName} mediaId={selected.resume.mediaId} />
    </Card>
    {isFailed && <Card className="border-rose-200 bg-rose-50/70 p-5 dark:border-rose-900/70 dark:bg-rose-950/20"><div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between"><div className="flex items-start gap-3"><AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-rose-600" /><div><h2 className="font-black text-rose-900 dark:text-rose-100">解析未完成</h2><p className="mt-1 text-sm leading-6 text-rose-800/80 dark:text-rose-200/80">内部失败详情不会展示给企业账号。可以安全地重新提交解析任务。</p></div></div><Button type="button" variant="secondary" onClick={retryAnalysis}><RotateCcw className="h-4 w-4" />重新解析</Button></div></Card>}
    <Card className="p-5 sm:p-6"><SectionHeading icon={<UserRound className="h-5 w-5" />} title="结构化画像" description={status === 'SUCCESS' ? '以下内容来自允许展示的简历事实摘要，不包含解析原文。' : '解析完成后，结构化画像会显示在这里。'} />{profile && status === 'SUCCESS' && <div className="mt-5 space-y-5"><TagGroup label="工作经历" values={profile.workExperience} /><ProjectGroup projects={profile.projects} /><TagGroup label="教育经历" values={profile.education} /><TagGroup label="优势" values={profile.strengths} tone="success" /><TagGroup label="风险" values={profile.risks} tone="warning" /><TagGroup label="推荐追问方向" values={profile.followUpDirections} tone="info" /></div>}{profile && status !== 'SUCCESS' && <p className="mt-5 rounded-2xl bg-muted p-4 text-sm leading-6 text-muted-foreground">{status === 'FAILED' ? '解析失败，已隐藏内部错误。' : '画像正在生成，当前页面会在可见时自动检查状态。'}</p>}</Card>
  </div>
}

function ProjectGroup({ projects }: { projects: CompanyResumeAnalysis['projects'] }) {
  return <div><p className="text-xs font-bold text-muted-foreground">项目经历</p>{projects.length ? <div className="mt-2 grid gap-3 md:grid-cols-2">{projects.map(project => <div key={`${project.name}-${project.role || ''}`} className="rounded-2xl bg-muted p-4"><p className="break-words font-bold">{project.name}</p>{project.role && <p className="mt-1 break-words text-xs text-muted-foreground">{project.role}</p>}{project.evidence && <p className="mt-3 break-words text-sm leading-6 text-muted-foreground">{project.evidence}</p>}</div>)}</div> : <p className="mt-2 text-sm text-muted-foreground">暂无项目经历。</p>}</div>
}

function resumeParseMeta(status: string): { label: string; tone: 'default' | 'success' | 'warning' | 'danger' | 'info' } {
  return ({ PENDING: { label: '等待解析', tone: 'warning' }, PROCESSING: { label: '解析中', tone: 'info' }, SUCCESS: { label: '已完成', tone: 'success' }, FAILED: { label: '解析失败', tone: 'danger' } } as const)[status as 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'] || { label: '状态未知', tone: 'default' }
}

function resumeFileKind(fileName?: string): 'pdf' | 'docx' | 'txt' | 'other' {
  const value = (fileName || '').toLowerCase()
  if (value.endsWith('.pdf')) return 'pdf'
  if (value.endsWith('.docx')) return 'docx'
  if (value.endsWith('.txt') || value.endsWith('.md')) return 'txt'
  return 'other'
}

function ResumeFilePanel({ applicationId, fileName, mediaId }: { applicationId: string; fileName?: string; mediaId?: string }) {
  const kind = resumeFileKind(fileName)
  const [pdf, setPdf] = useState<PDFDocumentProxy>()
  const [blobUrl, setBlobUrl] = useState('')
  const [pageNumber, setPageNumber] = useState(1)
  const [scale, setScale] = useState(1.05)
  const [pageSize, setPageSize] = useState({ width: 0, height: 0 })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const blobUrlRef = useRef('')
  const pdfRef = useRef<PDFDocumentProxy | undefined>(undefined)
  const contentPath = `/v1/company/recruitment/applications/${applicationId}/resume/content`

  useEffect(() => () => {
    if (blobUrlRef.current) URL.revokeObjectURL(blobUrlRef.current)
    if (pdfRef.current) void pdfRef.current.cleanup()
  }, [])

  const loadPdf = useCallback(async () => {
    if (!mediaId) throw new Error('resume-unavailable')
    const blob = await requestBlob(contentPath)
    const url = URL.createObjectURL(blob)
    try {
      const loaded = await pdfjsLib.getDocument({ data: new Uint8Array(await blob.arrayBuffer()) }).promise
      if (blobUrlRef.current) URL.revokeObjectURL(blobUrlRef.current)
      if (pdfRef.current) void pdfRef.current.cleanup()
      blobUrlRef.current = url
      pdfRef.current = loaded
      setBlobUrl(url)
      setPdf(loaded)
      setPageNumber(1)
      return url
    } catch (reason) {
      URL.revokeObjectURL(url)
      throw reason
    }
  }, [contentPath, mediaId])

  useEffect(() => {
    let cancelled = false
    let renderTask: RenderTask | undefined
    async function renderPage() {
      if (!pdf || !canvasRef.current) return
      try {
        const page = await pdf.getPage(pageNumber)
        if (cancelled || !canvasRef.current) return
        const viewport = page.getViewport({ scale })
        const canvas = canvasRef.current
        const context = canvas.getContext('2d')
        if (!context) throw new Error('canvas-unavailable')
        const pixelRatio = window.devicePixelRatio || 1
        canvas.width = Math.ceil(viewport.width * pixelRatio)
        canvas.height = Math.ceil(viewport.height * pixelRatio)
        canvas.style.width = `${viewport.width}px`
        canvas.style.height = `${viewport.height}px`
        setPageSize({ width: viewport.width, height: viewport.height })
        renderTask = page.render({ canvas, canvasContext: context, viewport, transform: pixelRatio !== 1 ? [pixelRatio, 0, 0, pixelRatio, 0, 0] : undefined })
        await renderTask.promise
      } catch (reason) {
        if (!cancelled && !(reason instanceof Error && reason.name === 'RenderingCancelledException')) setError('PDF 页面渲染失败，请重试。')
      }
    }
    void renderPage()
    return () => { cancelled = true; renderTask?.cancel() }
  }, [pdf, pageNumber, scale])

  async function previewPdf() {
    setLoading(true); setError('')
    try { await loadPdf() } catch { setError('PDF 文件暂时无法打开，已隐藏内部错误。') } finally { setLoading(false) }
  }

  async function downloadOriginal() {
    if (!mediaId) return
    setLoading(true); setError('')
    try {
      const blob = await requestBlob(contentPath)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = fileName || 'resume'
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      window.setTimeout(() => URL.revokeObjectURL(url), 0)
    } catch { setError('原文件暂时无法下载，已隐藏内部错误。') } finally { setLoading(false) }
  }

  async function openOriginal() {
    if (kind !== 'pdf') return downloadOriginal()
    setLoading(true); setError('')
    try {
      const url = blobUrl || await loadPdf()
      const opened = window.open(url, '_blank', 'noopener,noreferrer')
      if (!opened) setError('浏览器阻止了新窗口，请使用当前页面预览。')
    } catch { setError('原文件暂时无法打开，已隐藏内部错误。') } finally { setLoading(false) }
  }

  if (!mediaId) return <div className="mt-5 rounded-2xl border border-dashed border-border p-4 text-sm text-muted-foreground">该历史申请没有可用的简历文件，结构化画像仍按申请快照展示。</div>
  return <div className="mt-5 border-t border-border pt-5"><div className="flex flex-wrap items-center justify-between gap-3"><div className="min-w-0"><p className="text-xs font-bold text-muted-foreground">原文件</p><p className="mt-1 truncate text-sm font-semibold">{fileName || '未提供文件名'} <span className="font-normal text-muted-foreground">· {kind === 'pdf' ? 'PDF 在线预览' : kind === 'docx' ? 'DOCX 结构化内容 + 受保护下载' : kind === 'txt' ? 'TXT 结构化内容 + 受保护下载' : '受保护下载'}</span></p></div><div className="flex flex-wrap gap-2">{kind === 'pdf' && <Button type="button" variant="secondary" disabled={loading} onClick={() => void previewPdf()}>{loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileText className="h-4 w-4" />}在线预览</Button>}<Button type="button" variant="ghost" disabled={loading} onClick={() => void openOriginal()}>{kind === 'pdf' ? <ExternalLink className="h-4 w-4" /> : <Download className="h-4 w-4" />}{kind === 'pdf' ? '打开原文件' : '受保护下载'}</Button></div></div>{error && <p role="alert" className="mt-3 rounded-2xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs leading-5 text-rose-700 dark:border-rose-900/70 dark:bg-rose-950/30 dark:text-rose-200">{error}</p>}{kind === 'docx' && <p className="mt-3 rounded-2xl bg-muted p-3 text-xs leading-5 text-muted-foreground">DOCX 第一阶段不伪装成网页 Word 预览；上方结构化画像用于快速审阅，原文件通过企业私有接口受保护下载。</p>}{kind === 'txt' && <p className="mt-3 rounded-2xl bg-muted p-3 text-xs leading-5 text-muted-foreground">TXT 原文不在 HR 页面直接展开，避免把完整简历文本混入画像视图；需要核验时使用受保护下载。</p>}{pdf && <div className="mt-4 overflow-hidden rounded-2xl border border-border"><div className="flex flex-wrap items-center justify-between gap-2 border-b border-border bg-surface px-3 py-2"><div className="flex items-center gap-1"><Button type="button" variant="ghost" className="h-9 w-9 px-0" disabled={pageNumber <= 1} onClick={() => setPageNumber(value => Math.max(1, value - 1))} aria-label="上一页"><ChevronLeft className="h-4 w-4" /></Button><span className="min-w-20 text-center text-xs font-semibold">第 {pageNumber} / {pdf.numPages} 页</span><Button type="button" variant="ghost" className="h-9 w-9 px-0" disabled={pageNumber >= pdf.numPages} onClick={() => setPageNumber(value => Math.min(pdf.numPages, value + 1))} aria-label="下一页"><ChevronRight className="h-4 w-4" /></Button></div><div className="flex items-center gap-1"><Button type="button" variant="ghost" className="h-9 w-9 px-0" onClick={() => setScale(value => Math.max(.7, Number((value - .1).toFixed(2))))} aria-label="缩小"><Minus className="h-4 w-4" /></Button><span className="w-14 text-center text-xs text-muted-foreground">{Math.round(scale * 100)}%</span><Button type="button" variant="ghost" className="h-9 w-9 px-0" onClick={() => setScale(value => Math.min(2.2, Number((value + .1).toFixed(2))))} aria-label="放大"><Plus className="h-4 w-4" /></Button></div></div><div className="overflow-auto bg-muted p-3 sm:p-6"><div className="mx-auto w-fit max-w-none bg-white shadow-[0_18px_50px_rgba(40,32,20,.16)]" style={{ width: pageSize.width || 0, minHeight: pageSize.height || 240 }}><canvas ref={canvasRef} className="block" aria-label="简历 PDF 页面" /></div></div></div>}</div>
}

function MatchTab({ selected, evaluation, history, busy, retry, retryMatch }: { selected: JobApplication; evaluation: TabState<MatchEvaluation>; history: TabState<PageResult<MatchEvaluation>>; busy: boolean; retry: () => void; retryMatch: () => void }) {
  if (evaluation.loading || history.loading) return <TabLoading label="正在加载岗位匹配与历史评估…" />
  if (evaluation.error && !evaluation.data) return <TabError message={evaluation.error} retry={retry} />
  if (!evaluation.data) return <Card className="p-5"><MatchStatusCard selected={selected} busy={busy} retryMatch={retryMatch} /></Card>
  return <div className="space-y-4"><RecruitmentMatchEvaluation evaluation={evaluation.data} history={history.data} historyLoading={history.loading} historyError={history.error} /><div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">{evaluation.error && <span className="text-rose-700 dark:text-rose-200">当前评估加载部分失败：{evaluation.error}</span>}{evaluation.data.status === 'FAILED' && <Button type="button" variant="secondary" disabled={busy} onClick={retryMatch}><RotateCcw className="h-4 w-4" />重新分析</Button>}</div></div>
}

function InterviewTab({ state, retry }: { state: TabState<ApplicationInterview>; retry: () => void }) {
  if (state.loading) return <TabLoading label="正在加载面试安排…" />
  if (state.error) return <TabError message={state.error} retry={retry} />
  const data = state.data
  if (!data?.interview && !data?.offlineInterview) return <EmptyPanel icon={<CalendarClock className="h-7 w-7" />} title="暂无面试安排" description="当前申请还没有 AI 面试或线下面试记录。" />
  return <div className="space-y-4">{data.interview && <Card className="border-[var(--brand)]/30 bg-[var(--brand)]/10 p-5 sm:p-6"><SectionHeading icon={<Sparkles className="h-5 w-5 text-[var(--accent)]" />} title="AI 面试" description={data.interview.type === 'hr' ? 'HR 面试' : data.interview.type === 'comprehensive' ? '综合面试' : '技术面试'} /><div className="mt-5 grid gap-4 sm:grid-cols-2"><MetaItem label="面试标题" value={data.interview.title} /><MetaItem label="预约时间" value={formatDateTime(data.interview.scheduledAt)} /><MetaItem label="面试时长" value={`${data.interview.duration} 分钟`} /><MetaItem label="状态" value={aiInterviewStatus(data.interview.status)} /></div><Link className="mt-5 inline-flex min-h-10 items-center font-bold text-[var(--accent)] underline" to={`/company/interviews/AI-${data.interview.id}`}>进入面试中心详情</Link></Card>}{data.offlineInterview && <Card className="border-[var(--accent)]/30 bg-[var(--accent-soft)] p-5 sm:p-6"><SectionHeading icon={<CalendarClock className="h-5 w-5 text-[var(--accent)]" />} title="线下面试" description={offlineType(data.offlineInterview.interviewType)} /><div className="mt-5 grid gap-4 sm:grid-cols-2"><MetaItem label="预约时间" value={formatDateTime(data.offlineInterview.scheduledAt)} /><MetaItem label="时长" value={`${data.offlineInterview.durationMinutes} 分钟`} /><MetaItem label="地点" value={data.offlineInterview.location || '未填写地点'} /><MetaItem label="联系人" value={data.offlineInterview.contactName || '未填写联系人'} /></div>{data.offlineInterview.meetingUrl && <a href={data.offlineInterview.meetingUrl} target="_blank" rel="noreferrer" className="mt-5 inline-flex min-h-11 items-center font-bold text-[var(--accent)] underline">打开会议链接</a>}<Link className="mt-5 inline-flex min-h-10 items-center font-bold text-[var(--accent)] underline" to={`/company/interviews/OFFLINE-${data.offlineInterview.id}`}>进入面试中心详情</Link>{data.offlineInterview.note && <p className="mt-4 break-words rounded-2xl bg-surface/70 p-4 text-sm leading-6">备注：{data.offlineInterview.note}</p>}</Card>}</div>
}

function ReportTab({ state, retry, actionBusy, onRetry, onPublish }: { state: TabState<CompanyReportDetail>; retry: () => void; actionBusy: boolean; onRetry: () => void; onPublish: () => void }) {
  if (state.loading) return <TabLoading label="正在加载评估报告…" />
  const noReport = state.error && ['报告尚未生成', '报告不存在', '面试不存在'].some(marker => state.error.includes(marker))
  if (noReport) return <EmptyPanel icon={<FileText className="h-7 w-7" />} title="暂无评估报告" description={state.error} />
  if (state.error) return <TabError message={state.error} retry={retry} />
  if (!state.data) return <EmptyPanel icon={<FileText className="h-7 w-7" />} title="暂无评估报告" description="面试结束并完成报告生成后，报告会出现在这里。" />
  const report = state.data
  const statusMeta = companyReportStatusMeta(report.reportStatus)
  const hasReport = report.reportStatus === 'READY' || report.reportStatus === 'PUBLISHED'
  return <div className="space-y-4">
    <Card className="p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <SectionHeading icon={<FileText className="h-5 w-5" />} title="评估报告" description={report.reportStatus === 'PUBLISHED' ? `已发布于 ${formatDateTime(report.publishedAt)}` : hasReport ? '报告仅对企业内部可见，尚未发布给候选人。' : '报告生成状态会在此处持续更新。'} />
        <Badge tone={statusMeta.tone}>{statusMeta.label}</Badge>
      </div>
      <div className="mt-5 flex flex-wrap items-center gap-2">
        {report.reportStatus === 'READY' && <Button type="button" disabled={actionBusy} onClick={onPublish}><ShieldCheck className="h-4 w-4" />发布给候选人</Button>}
        {report.canRetry && <Button type="button" variant="secondary" disabled={actionBusy} onClick={onRetry}>{actionBusy ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />}受控重试</Button>}
        {report.reportStatus === 'READY' && <span className="text-xs text-muted-foreground">内部查看不会自动发布。</span>}
      </div>
      {!hasReport && <div className="mt-5 rounded-2xl border border-border bg-muted/60 p-4"><p className="font-bold">{statusMeta.label}</p><p className="mt-1 text-sm leading-6 text-muted-foreground">{report.taskMessage || '面试已经结束，但报告还没有可展示的结果。请稍后刷新或使用受控重试。'}</p>{report.taskAttempts != null && <p className="mt-2 text-xs text-muted-foreground">已尝试 {report.taskAttempts} 次</p>}</div>}
      {hasReport && <div className="mt-5 grid gap-3 sm:grid-cols-3"><ScoreBox label="综合得分" value={report.totalScore} emphasis /><ScoreBox label="专业能力" value={report.professionalScore} /><ScoreBox label="表达能力" value={report.expressionScore} /><ScoreBox label="逻辑思维" value={report.logicScore} /><ScoreBox label="适应能力" value={report.adaptabilityScore} /><MetaItem label="有效题目数" value={`${report.questionCount} 道`} /></div>}
      {hasReport && report.reliabilityWarning && <p className="mt-4 rounded-2xl bg-amber-50 p-4 text-sm leading-6 text-amber-800 dark:bg-amber-950/30 dark:text-amber-100"><AlertTriangle className="mr-2 inline h-4 w-4" />{report.reliabilityWarning}</p>}
    </Card>
    {hasReport && <Card className="p-5 sm:p-6"><TextBlock label="综合总结" value={report.summary} empty="暂无综合总结。" /><div className="mt-5 grid gap-5 md:grid-cols-3"><TextBlock label="优势" value={report.strengths} empty="暂无优势说明。" /><TextBlock label="待提升项" value={report.weaknesses} empty="暂无待提升项。" /><TextBlock label="改进建议" value={report.improvementSuggestions} empty="暂无改进建议。" /></div></Card>}
    {!!report.questionReviews.length && <Card className="p-5 sm:p-6"><SectionHeading icon={<CheckCircle2 className="h-5 w-5" />} title="题目、回答与评分" description="只展示企业复核所需的结构化内容，不返回 Prompt、Provider 响应或原始 JSON。" /><div className="mt-5 space-y-4">{report.questionReviews.map(item => <QuestionReview key={item.id} item={item} />)}</div></Card>}
    {report.recording && <RecordingTimeline recording={report.recording} />}
  </div>
}

function companyReportStatusMeta(status: CompanyReportDetail['reportStatus']) {
  return ({
    NOT_AVAILABLE: { label: '暂无报告任务', tone: 'default' as const },
    PENDING: { label: '等待生成', tone: 'warning' as const },
    RUNNING: { label: '生成中', tone: 'info' as const },
    FAILED: { label: '生成失败', tone: 'danger' as const },
    READY: { label: '内部可查看', tone: 'warning' as const },
    PUBLISHED: { label: '已发布给候选人', tone: 'success' as const },
  }[status] || { label: '处理中', tone: 'info' as const })
}

function QuestionReview({ item }: { item: CompanyReportDetail['questionReviews'][number] }) {
  const evaluation = item.evaluation
  return <article className="rounded-2xl border border-border bg-background p-4 sm:p-5"><div className="flex items-start justify-between gap-3"><div className="min-w-0"><p className="text-xs font-bold text-muted-foreground">第 {item.sequenceNo || '—'} 题{item.questionType ? ` · ${item.questionType}` : ''}</p><p className="mt-2 break-words font-bold leading-6">{item.question}</p></div>{evaluation?.overallScore != null && <span className="shrink-0 rounded-full bg-[var(--accent-soft)] px-3 py-1 text-sm font-black text-[var(--accent)]">{evaluation.overallScore} 分</span>}</div><div className="mt-4 rounded-xl bg-muted p-3"><p className="text-xs font-bold text-muted-foreground">候选人回答</p><p className="mt-1 whitespace-pre-wrap break-words text-sm leading-6">{item.answer}</p>{item.answeredAt && <p className="mt-2 text-xs text-muted-foreground">提交于 {formatDateTime(item.answeredAt)}</p>}</div>{item.followUps.length > 0 && <div className="mt-4"><p className="text-xs font-bold text-muted-foreground">AI 追问</p><ul className="mt-2 space-y-2">{item.followUps.map((followUp, index) => <li key={`${item.id}-follow-${index}`} className="rounded-xl border border-border px-3 py-2 text-sm leading-6">{followUp}</li>)}</ul></div>}{evaluation && <div className="mt-4 grid gap-2 text-xs sm:grid-cols-4"><ScoreBox label="专业" value={evaluation.professionalScore} /><ScoreBox label="表达" value={evaluation.expressionScore} /><ScoreBox label="逻辑" value={evaluation.logicScore} /><ScoreBox label="适应" value={evaluation.adaptabilityScore} /></div>}{evaluation?.comment && <p className="mt-4 break-words text-sm leading-6 text-muted-foreground">评语：{evaluation.comment}</p>}</article>
}

function RecordingTimeline({ recording }: { recording: NonNullable<CompanyReportDetail['recording']> }) {
  const [mediaUrl, setMediaUrl] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => () => { if (mediaUrl) URL.revokeObjectURL(mediaUrl) }, [mediaUrl])
  async function openSegment(path: string) {
    setLoading(true); setError('')
    try {
      const blob = await requestBlob(path)
      if (mediaUrl) URL.revokeObjectURL(mediaUrl)
      setMediaUrl(URL.createObjectURL(blob))
    } catch (reason) { setError(reason instanceof Error ? reason.message : '录制加载失败，请稍后重试。') } finally { setLoading(false) }
  }
  return <Card className="p-5 sm:p-6"><SectionHeading icon={<ShieldCheck className="h-5 w-5" />} title="受保护录制时间线" description="媒体分段仅通过企业授权接口读取，不把 Token 放入 URL。" /><div className="mt-5 flex flex-wrap gap-2 text-sm"><Badge tone="info">{recording.mode === 'VIDEO' ? '视频录制' : recording.mode === 'AUDIO' ? '音频录制' : '文字面试'}</Badge><Badge tone={recording.status === 'COMPLETED' ? 'success' : 'warning'}>{recording.status === 'COMPLETED' ? '录制已完成' : recording.status}</Badge>{recording.endedAt && <span className="self-center text-xs text-muted-foreground">结束于 {formatDateTime(recording.endedAt)}</span>}</div>{recording.segments.length === 0 && recording.events.length === 0 && <p className="mt-4 rounded-2xl bg-muted p-4 text-sm text-muted-foreground">暂无可用录制分段或时间轴事件。</p>}{recording.segments.length > 0 && <ol className="mt-5 space-y-3">{recording.segments.map(segment => <li key={segment.id} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border p-3"><div><p className="font-bold">分段 {segment.segmentNo}</p><p className="mt-1 text-xs text-muted-foreground">{formatOffset(segment.startedOffsetMs)} – {formatOffset(segment.endedOffsetMs)} · {segment.contentType}</p></div><Button type="button" variant="secondary" className="h-10" disabled={loading} onClick={() => void openSegment(segment.contentPath)}>{loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}打开分段</Button></li>)}</ol>}{mediaUrl && <div className="mt-5 overflow-hidden rounded-2xl border border-border bg-black p-2">{recording.mode === 'VIDEO' ? <video controls className="max-h-[420px] w-full" src={mediaUrl} /> : <audio controls className="w-full" src={mediaUrl} />}</div>}{error && <p role="alert" className="mt-3 text-sm text-rose-700 dark:text-rose-200">{error}</p>}{recording.events.length > 0 && <div className="mt-5 border-t border-border pt-5"><p className="text-xs font-bold text-muted-foreground">事件时间线</p><ol className="mt-3 space-y-2">{recording.events.map(event => <li key={event.id} className="flex gap-3 text-sm"><span className="w-16 shrink-0 tabular-nums text-xs text-muted-foreground">{formatOffset(event.offsetMs)}</span><span className="min-w-0 break-words"><strong>{recordingEventLabel(event.eventType)}</strong>{event.content && <span className="text-muted-foreground"> · {event.content}</span>}</span></li>)}</ol></div>}</Card>
}

function formatOffset(value: number) {
  const totalSeconds = Math.max(0, Math.floor(value / 1000))
  return `${String(Math.floor(totalSeconds / 60)).padStart(2, '0')}:${String(totalSeconds % 60).padStart(2, '0')}`
}

function recordingEventLabel(value: string) {
  return ({ QUESTION_STARTED: '题目开始', ANSWER_SUBMITTED: '回答提交', FOLLOW_UP: 'AI 追问', TRANSITION: '题目切换', QUESTION_COMPLETED: '题目完成', RECORDING_STARTED: '录制开始', RECORDING_STOPPED: '录制结束', RECORDING_ERROR: '录制异常' } as Record<string, string>)[value] || '面试事件'
}

function TimelineTab({ state, retry }: { state: TabState<ApplicationTimelineEvent[]>; retry: () => void }) {
  if (state.loading) return <TabLoading label="正在加载申请时间线…" />
  if (state.error) return <TabError message={state.error} retry={retry} />
  if (!state.data?.length) return <EmptyPanel icon={<Clock3 className="h-7 w-7" />} title="暂无时间线记录" description="申请活动发生后会按照时间顺序出现在这里。" />
  return <Card className="p-5 sm:p-6"><SectionHeading icon={<Clock3 className="h-5 w-5" />} title="申请时间线" description="展示申请、匹配、面试、报告与 HR 操作摘要。" /><ol className="mt-6 space-y-0">{state.data.map((event, index) => <TimelineItem key={event.id} event={event} last={index === state.data!.length - 1} />)}</ol></Card>
}

function TimelineItem({ event, last }: { event: ApplicationTimelineEvent; last: boolean }) {
  return <li className="relative flex gap-4 pb-6"><div className="flex w-6 shrink-0 justify-center"><span className={'relative z-10 grid h-6 w-6 place-items-center rounded-full ring-4 ring-surface ' + timelineTone(event.tone)}><CircleDot className="h-3.5 w-3.5" /></span>{!last && <span className="absolute left-3 top-6 h-full w-px bg-border" />}</div><div className="min-w-0 flex-1"><div className="flex flex-wrap items-baseline justify-between gap-2"><strong className="break-words">{event.title}</strong><time className="shrink-0 text-xs text-muted-foreground">{formatDateTime(event.occurredAt)}</time></div><p className="mt-1 break-words text-sm leading-6 text-muted-foreground">{event.description || '暂无补充说明。'}</p><p className="mt-2 text-xs font-semibold text-muted-foreground">操作人：{event.actorName || '系统'}</p></div></li>
}

function ActionCard({ selected, transitions, reviewNote, setReviewNote, busy, onTransition }: { selected: JobApplication; transitions: ApplicationStatusTransition[]; reviewNote: string; setReviewNote: (value: string) => void; busy: boolean; onTransition: (transition: ApplicationStatusTransition) => Promise<void> }) {
  return <Card className="p-5"><div className="flex items-center gap-2"><Sparkles className="h-5 w-5 text-[var(--accent)]" /><h2 className="font-black">下一步操作</h2></div><p className="mt-3 text-sm leading-6 text-muted-foreground">按钮由后端 `allowedTransitions` 决定，页面不会自行推断可流转阶段。</p><label className="mt-5 block text-sm font-bold">审核备注 / 变更原因<textarea value={reviewNote} onChange={event => setReviewNote(event.target.value)} className="mt-2 min-h-24 w-full rounded-2xl border border-border bg-background p-3 text-sm outline-none focus:border-[var(--accent)]" placeholder="进入评估、拒绝或录用时填写原因" /></label><div className="mt-4 space-y-2">{transitions.map(transition => <Button key={transition.status} type="button" variant={transition.status === 'REJECTED' ? 'danger' : 'primary'} className="w-full" disabled={busy} onClick={() => void onTransition(transition)}>{busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}{transition.label}{transition.requiresNote ? '（需备注）' : ''}</Button>)}{!transitions.length && <p className="rounded-2xl bg-muted p-3 text-sm text-muted-foreground">该申请已进入终态，无需继续流转。</p>}</div><p className="mt-4 text-xs leading-5 text-muted-foreground">当前申请：{selected.applicationNo}</p></Card>
}

function ContactItem({ icon, value }: { icon: ReactNode; value: string }) {
  return <p className="flex min-w-0 items-start gap-2 break-words text-sm"><span className="mt-0.5 shrink-0 text-muted-foreground">{icon}</span><span className="break-words">{value}</span></p>
}

function SectionHeading({ icon, title, description }: { icon: ReactNode; title: string; description?: string }) {
  return <div className="flex items-start gap-3"><span className="mt-0.5 shrink-0 text-[var(--accent)]">{icon}</span><div className="min-w-0"><h2 className="break-words font-black">{title}</h2>{description && <p className="mt-1 break-words text-sm leading-6 text-muted-foreground">{description}</p>}</div></div>
}

function TextBlock({ label, value, empty }: { label: string; value?: string; empty: string }) {
  return <div><p className="text-xs font-bold text-muted-foreground">{label}</p><p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-muted-foreground">{value || empty}</p></div>
}

function TagGroup({ label, values, tone = 'default' }: { label: string; values?: string[]; tone?: 'default' | 'success' | 'warning' | 'danger' | 'info' }) {
  return <div><p className="text-xs font-bold text-muted-foreground">{label}</p><div className="mt-2 flex flex-wrap gap-2">{values?.length ? values.map(value => <Badge key={`${label}-${value}`} tone={tone}>{value}</Badge>) : <span className="text-sm text-muted-foreground">暂无记录。</span>}</div></div>
}

function ScoreBox({ label, value, emphasis = false }: { label: string; value?: number; emphasis?: boolean }) {
  return <div className={emphasis ? 'rounded-2xl bg-[var(--accent-soft)] p-4' : 'rounded-2xl bg-muted p-4'}><p className="text-xs text-muted-foreground">{label}</p><strong className={emphasis ? 'mt-2 block text-3xl font-black tabular-nums text-[var(--accent)]' : 'mt-2 block text-2xl font-black tabular-nums'}>{value == null ? '—' : `${value}%`}</strong></div>
}

function MatchStatusCard({ selected, busy, retryMatch }: { selected: JobApplication; busy: boolean; retryMatch: () => void }) {
  const status = selected.matchStatus
  const label = status === 'PENDING' ? '等待 AI 分析' : status === 'PROCESSING' ? 'AI 分析中' : status === 'FAILED' ? '匹配分析失败' : '暂无匹配评估'
  return <div className="text-center"><Sparkles className="mx-auto h-8 w-8 text-[var(--accent)]" /><h2 className="mt-3 text-xl font-black">{label}</h2><p className="mx-auto mt-2 max-w-md text-sm leading-6 text-muted-foreground">{selected.matchSummary || selected.matchError || '岗位匹配结果将在任务完成后显示。'}</p>{status === 'FAILED' && <Button type="button" variant="secondary" className="mt-5" disabled={busy} onClick={retryMatch}><RotateCcw className="h-4 w-4" />重新分析</Button>}</div>
}

function TabLoading({ label }: { label: string }) {
  return <Card className="grid min-h-64 place-items-center p-6 text-center"><div><Loader2 className="mx-auto h-7 w-7 animate-spin text-muted-foreground" /><p className="mt-3 text-sm text-muted-foreground">{label}</p></div></Card>
}

function TabError({ message, retry, emptyHint = false }: { message: string; retry: () => void; emptyHint?: boolean }) {
  return <Card className="p-6"><div className="flex items-start gap-3 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-rose-700 dark:border-rose-900/70 dark:bg-rose-950/30 dark:text-rose-200"><AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" /><div className="min-w-0"><h2 className="font-black">{emptyHint ? '暂无可用数据' : '此标签加载失败'}</h2><p className="mt-1 break-words text-sm leading-6">{message}</p><Button type="button" variant="secondary" className="mt-4" onClick={retry}>重试</Button></div></div></Card>
}

function EmptyPanel({ icon, title, description }: { icon: ReactNode; title: string; description: string }) {
  return <Card className="grid min-h-64 place-items-center p-6 text-center"><div><span className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-muted text-muted-foreground">{icon}</span><h2 className="mt-4 text-xl font-black">{title}</h2><p className="mx-auto mt-2 max-w-md text-sm leading-6 text-muted-foreground">{description}</p></div></Card>
}

function timelineTone(tone?: string) {
  return tone === 'success' ? 'bg-emerald-600 text-white' : tone === 'danger' ? 'bg-rose-600 text-white' : tone === 'warning' ? 'bg-amber-500 text-white' : tone === 'info' ? 'bg-sky-600 text-white' : 'bg-[var(--accent)] text-white'
}

function aiInterviewStatus(status: number) {
  return ({ 0: '等待候选人进入', 1: '面试进行中', 2: '面试已完成', 3: '已取消', 5: '报告生成中', 6: '报告已就绪', 7: '处理失败' } as Record<number, string>)[status] || '处理中'
}

function offlineType(type?: string) {
  return type === 'VIDEO' ? '视频面试' : type === 'PHONE' ? '电话面试' : '现场面试'
}

function AiInviteDialog({ open, setOpen, form, setForm, questionBanks, busy, onSubmit }: { open: boolean; setOpen: (value: boolean) => void; form: AiInviteForm; setForm: Dispatch<SetStateAction<AiInviteForm>>; questionBanks: InterviewQuestionBank[]; busy: boolean; onSubmit: (event: FormEvent) => Promise<void> }) {
  return <Dialog.Root open={open} onOpenChange={setOpen}><Dialog.Portal><Dialog.Overlay className="fixed inset-0 z-[100] bg-black/55" /><Dialog.Content className="fixed inset-x-3 bottom-3 z-[101] mx-auto max-w-xl rounded-[28px] border border-border bg-surface p-5 shadow-2xl focus:outline-none sm:bottom-auto sm:top-1/2 sm:-translate-y-1/2 sm:p-7"><div className="flex items-start justify-between"><div><Dialog.Title className="text-2xl font-black">安排 AI 面试</Dialog.Title><Dialog.Description className="mt-2 text-sm text-muted-foreground">候选人会收到站内通知，并在预约时间进入 AI 面试间。</Dialog.Description></div><Dialog.Close aria-label="关闭 AI 面试安排" className="grid h-11 w-11 shrink-0 place-items-center rounded-full hover:bg-muted"><X className="h-5 w-5" /></Dialog.Close></div><form className="mt-5 space-y-4" onSubmit={onSubmit}><div className="grid gap-4 sm:grid-cols-2"><label className="text-sm font-bold">面试时间<input required type="datetime-local" className={inputClass} value={form.scheduledAt} onChange={event => setForm({ ...form, scheduledAt: event.target.value })} /></label><label className="text-sm font-bold">时长（分钟）<input required min="10" max="180" type="number" className={inputClass} value={form.durationMinutes} onChange={event => setForm({ ...form, durationMinutes: event.target.value })} /></label><label className="text-sm font-bold">面试类型<ResponsiveSelect ariaLabel="选择 AI 面试类型" value={form.type} onValueChange={value => setForm({ ...form, type: value })} options={[{ value: 'tech', label: '技术面试' }, { value: 'hr', label: 'HR 面试' }, { value: 'comprehensive', label: '综合面试' }]} className="mt-2 w-full" /></label><label className="text-sm font-bold">题目数量<input required min="1" max="20" type="number" className={inputClass} value={form.questionCount} onChange={event => setForm({ ...form, questionCount: event.target.value })} /></label></div><label className="block text-sm font-bold">公开题库<ResponsiveSelect ariaLabel="选择 AI 面试题库" value={form.questionBankId} onValueChange={value => setForm({ ...form, questionBankId: value })} options={[{ value: '', label: questionBanks.length ? '请选择题库' : '暂无可用公开题库' }, ...questionBanks.map(bank => ({ value: bank.id, label: bank.name }))]} className="mt-2 w-full" /></label><label className="block text-sm font-bold">面试官风格<ResponsiveSelect ariaLabel="选择面试官风格" value={form.interviewerStyle} onValueChange={value => setForm({ ...form, interviewerStyle: value })} options={[{ value: 'big-tech', label: '大厂技术风格' }, { value: 'gentle', label: '引导型' }, { value: 'pressure', label: '压力型' }, { value: 'project-deep', label: '项目深挖' }]} className="mt-2 w-full" /></label><label className="block text-sm font-bold">补充说明<textarea className="mt-2 min-h-20 w-full rounded-2xl border border-border bg-background p-3 text-sm outline-none" value={form.remark} onChange={event => setForm({ ...form, remark: event.target.value })} placeholder="例如：重点考察 Java 并发与微服务设计" /></label><div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end"><Dialog.Close asChild><Button type="button" variant="secondary">取消</Button></Dialog.Close><Button type="submit" disabled={busy || !form.questionBankId}>{busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}确认安排</Button></div></form></Dialog.Content></Dialog.Portal></Dialog.Root>
}

function OfflineInviteDialog({ open, setOpen, form, setForm, busy, onSubmit }: { open: boolean; setOpen: (value: boolean) => void; form: InviteForm; setForm: Dispatch<SetStateAction<InviteForm>>; busy: boolean; onSubmit: (event: FormEvent) => Promise<void> }) {
  return <Dialog.Root open={open} onOpenChange={setOpen}><Dialog.Portal><Dialog.Overlay className="fixed inset-0 z-[100] bg-black/55" /><Dialog.Content className="fixed inset-x-3 bottom-3 z-[101] mx-auto max-w-xl rounded-[28px] border border-border bg-surface p-5 shadow-2xl focus:outline-none sm:bottom-auto sm:top-1/2 sm:-translate-y-1/2 sm:p-7"><div className="flex items-start justify-between"><div><Dialog.Title className="text-2xl font-black">发送线下面试邀请</Dialog.Title><Dialog.Description className="mt-2 text-sm text-muted-foreground">候选人会收到站内通知，并在申请详情看到安排。</Dialog.Description></div><Dialog.Close aria-label="关闭线下面试安排" className="grid h-11 w-11 shrink-0 place-items-center rounded-full hover:bg-muted"><X className="h-5 w-5" /></Dialog.Close></div><form className="mt-5 space-y-4" onSubmit={onSubmit}><div className="grid gap-4 sm:grid-cols-2"><label className="text-sm font-bold">面试时间<input required type="datetime-local" className={inputClass} value={form.scheduledAt} onChange={event => setForm({ ...form, scheduledAt: event.target.value })} /></label><label className="text-sm font-bold">时长（分钟）<input required min="15" max="480" type="number" className={inputClass} value={form.durationMinutes} onChange={event => setForm({ ...form, durationMinutes: event.target.value })} /></label><label className="text-sm font-bold">面试形式<ResponsiveSelect ariaLabel="选择面试形式" value={form.interviewType} onValueChange={value => setForm({ ...form, interviewType: value })} options={[{ value: 'ONSITE', label: '现场面试' }, { value: 'VIDEO', label: '视频面试' }, { value: 'PHONE', label: '电话面试' }]} className="mt-2 w-full" /></label><label className="text-sm font-bold">联系人<input className={inputClass} value={form.contactName} onChange={event => setForm({ ...form, contactName: event.target.value })} /></label></div>{form.interviewType === 'ONSITE' && <label className="block text-sm font-bold">面试地点<input required className={inputClass} value={form.location} onChange={event => setForm({ ...form, location: event.target.value })} /></label>}{form.interviewType === 'VIDEO' && <label className="block text-sm font-bold">会议链接<input required type="url" className={inputClass} value={form.meetingUrl} onChange={event => setForm({ ...form, meetingUrl: event.target.value })} /></label>}<label className="block text-sm font-bold">联系电话<input className={inputClass} value={form.contactPhone} onChange={event => setForm({ ...form, contactPhone: event.target.value })} /></label><label className="block text-sm font-bold">补充说明<textarea className="mt-2 min-h-20 w-full rounded-2xl border border-border bg-background p-3 text-sm outline-none" value={form.note} onChange={event => setForm({ ...form, note: event.target.value })} /></label><div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end"><Dialog.Close asChild><Button type="button" variant="secondary">取消</Button></Dialog.Close><Button type="submit" disabled={busy}>{busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <CalendarClock className="h-4 w-4" />}发送邀请</Button></div></form></Dialog.Content></Dialog.Portal></Dialog.Root>
}
