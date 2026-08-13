import * as Dialog from '@radix-ui/react-dialog'
import { AlertTriangle, BriefcaseBusiness, Building2, CalendarClock, CheckCircle2, ChevronLeft, ChevronRight, Circle, FileText, Loader2, MapPin, RefreshCw, RotateCcw, Search, Sparkles, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { RecruitmentMatchEvaluation } from '@/components/recruitment-match-evaluation'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'
import { applicationStatusMeta, appendQuery, formatDateTime, matchStatusMeta, type ApplicationStatus, type JobApplication, type MatchEvaluation, type MatchStatus, type PageResult } from '@/lib/recruitment'

const PAGE_SIZE = 8

const statusOptions = [
  { value: '', label: '全部进度' },
  ...Object.entries(applicationStatusMeta).map(([value, meta]) => ({ value, label: meta.label })),
]

const resumeStatusMeta = {
  MANUAL: { label: '已确认', tone: 'success' as const },
  PENDING: { label: '等待解析', tone: 'warning' as const },
  PROCESSING: { label: '解析中', tone: 'info' as const },
  SUCCESS: { label: '解析完成', tone: 'success' as const },
  FAILED: { label: '解析失败', tone: 'danger' as const },
}

const nextStepMeta: Record<ApplicationStatus, string> = {
  SUBMITTED: '等待企业查看',
  AI_INTERVIEW_PENDING: '准备进入 AI 面试',
  AI_INTERVIEWING: '继续完成 AI 面试',
  UNDER_REVIEW: '企业正在评估',
  OFFLINE_INTERVIEW: '确认面试安排',
  REJECTED: '查看申请详情',
  HIRED: '查看录用进展',
}

function getApplicationStatusMeta(status: string) {
  return applicationStatusMeta[status as ApplicationStatus] ?? { label: '申请处理中', tone: 'default' as const }
}

function getMatchStatusMeta(status?: string) {
  return matchStatusMeta[status as MatchStatus] ?? matchStatusMeta.MANUAL
}

function ApplicationSkeleton() {
  return <div role="status" aria-label="正在加载申请记录" aria-busy="true" className="space-y-3">
    {Array.from({ length: 4 }, (_, index) => <div key={index} aria-hidden="true" className="min-h-32 animate-pulse rounded-[24px] border border-border bg-surface p-5">
      <div className="flex gap-4"><div className="h-12 w-12 rounded-2xl bg-muted" /><div className="min-w-0 flex-1"><div className="h-5 w-2/5 rounded bg-muted" /><div className="mt-3 h-4 w-1/4 rounded bg-muted" /><div className="mt-6 h-4 w-3/5 rounded bg-muted" /></div><div className="h-6 w-16 rounded-full bg-muted" /></div>
    </div>)}
  </div>
}

function ApplicationCard({ item, onOpen }: { item: JobApplication; onOpen: () => void }) {
  const statusMeta = getApplicationStatusMeta(item.status)
  const matchMeta = getMatchStatusMeta(item.matchStatus)

  return <Card className="overflow-hidden p-0 shadow-none transition-colors hover:border-[var(--accent)]/70">
    <button type="button" aria-label={`查看${item.positionName}的申请详情`} onClick={onOpen} className="block min-h-36 w-full p-4 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--brand)] sm:p-5">
      <div className="flex items-start gap-4">
        <span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><Building2 className="h-5 w-5" aria-hidden="true" /></span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
            <div className="min-w-0"><h2 className="break-words text-lg font-black tracking-[-.02em]">{item.positionName}</h2><p className="mt-1 truncate text-sm font-semibold text-muted-foreground">{item.companyName}</p></div>
            <Badge tone={statusMeta.tone}>{statusMeta.label}</Badge>
          </div>
          <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-muted-foreground">
            <span>{item.applicationNo}</span><span>投递于 {formatDateTime(item.submittedAt)}</span>
            {item.matchScore != null && <span className="font-bold text-[var(--accent)]">匹配度 {item.matchScore}%</span>}
            {item.matchStatus && <Badge tone={matchMeta.tone} className="px-2 py-0.5">{matchMeta.label}</Badge>}
          </div>
          <div className="mt-4 flex items-center justify-between gap-3 border-t border-border pt-3 text-sm"><span className="min-w-0 truncate text-muted-foreground">下一步：{nextStepMeta[item.status] || '查看申请详情'}</span><ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" /></div>
        </div>
      </div>
    </button>
  </Card>
}

export function CandidateApplications() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [items, setItems] = useState<PageResult<JobApplication>>()
  const [searchInput, setSearchInput] = useState(searchParams.get('keyword') ?? '')
  const [selected, setSelected] = useState<JobApplication>()
  const [matchEvaluation, setMatchEvaluation] = useState<MatchEvaluation>()
  const [matchHistory, setMatchHistory] = useState<PageResult<MatchEvaluation>>()
  const [matchHistoryLoading, setMatchHistoryLoading] = useState(false)
  const [matchHistoryError, setMatchHistoryError] = useState('')
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [retryingMatch, setRetryingMatch] = useState(false)
  const [enteringInterview, setEnteringInterview] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [actionMessage, setActionMessage] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const navigate = useNavigate()

  const keyword = searchParams.get('keyword') ?? ''
  const status = searchParams.get('status') ?? ''
  const pageNoValue = Number(searchParams.get('pageNo') ?? '1')
  const pageNo = Number.isFinite(pageNoValue) && pageNoValue > 0 ? Math.floor(pageNoValue) : 1
  const activeFilterCount = [keyword, status].filter(Boolean).length

  useEffect(() => { setSearchInput(keyword) }, [keyword])

  useEffect(() => {
    let active = true
    setItems(undefined)
    setLoadError('')
    setActionMessage('')
    const query = appendQuery({ keyword, status, pageNo, pageSize: PAGE_SIZE })
    request<PageResult<JobApplication>>(`/v1/recruitment/applications${query}`)
      .then(result => active && setItems(result))
      .catch(reason => active && setLoadError(reason instanceof Error ? reason.message : '申请记录加载失败，请重试。'))
    return () => { active = false }
  }, [keyword, pageNo, reloadKey, status])

  const selectedId = selected?.id
  const selectedMatchStatus = selected?.matchStatus

  useEffect(() => {
    if (!selectedId || !['PENDING', 'PROCESSING'].includes(selectedMatchStatus || '')) return
    const timer = window.setInterval(() => {
      void Promise.allSettled([
        request<JobApplication>(`/v1/recruitment/applications/${selectedId}`),
        request<MatchEvaluation>(`/v1/recruitment/applications/${selectedId}/match`),
        request<PageResult<MatchEvaluation>>(`/v1/recruitment/applications/${selectedId}/match/history?pageNo=1&pageSize=5`),
      ]).then(([detailResult, evaluationResult, historyResult]) => {
        if (detailResult.status === 'fulfilled') {
          setSelected(detailResult.value)
          setItems(current => current ? { ...current, records: current.records.map(item => item.id === detailResult.value.id ? detailResult.value : item) } : current)
        } else setDetailError(detailResult.reason instanceof Error ? detailResult.reason.message : '匹配状态刷新失败。')
        if (evaluationResult.status === 'fulfilled') setMatchEvaluation(evaluationResult.value)
        if (historyResult.status === 'fulfilled') { setMatchHistory(historyResult.value); setMatchHistoryError('') }
      })
    }, 3000)
    return () => window.clearInterval(timer)
  }, [selectedId, selectedMatchStatus])

  function updateQuery(name: string, value: string, resetPage = true) {
    setSearchParams(current => {
      const next = new URLSearchParams(current)
      if (value) next.set(name, value)
      else next.delete(name)
      if (resetPage) next.set('pageNo', '1')
      return next
    })
  }

  function clearFilters() {
    setSearchParams({})
  }

  function changePage(nextPage: number) {
    setSearchParams(current => {
      const next = new URLSearchParams(current)
      next.set('pageNo', String(nextPage))
      return next
    })
    const behavior = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'
    window.scrollTo({ top: 0, behavior })
  }

  async function openDetail(id: string) {
    setLoadingDetail(true)
    setDetailError('')
    setMatchEvaluation(undefined)
    setMatchHistory(undefined)
    setMatchHistoryError('')
    setMatchHistoryLoading(true)
    try {
      const [detailResult, evaluationResult, historyResult] = await Promise.allSettled([
        request<JobApplication>(`/v1/recruitment/applications/${id}`),
        request<MatchEvaluation>(`/v1/recruitment/applications/${id}/match`),
        request<PageResult<MatchEvaluation>>(`/v1/recruitment/applications/${id}/match/history?pageNo=1&pageSize=5`),
      ])
      if (detailResult.status === 'rejected') throw detailResult.reason
      setSelected(detailResult.value)
      if (evaluationResult.status === 'fulfilled') setMatchEvaluation(evaluationResult.value)
      if (historyResult.status === 'fulfilled') setMatchHistory(historyResult.value)
      else setMatchHistoryError(historyResult.reason instanceof Error ? historyResult.reason.message : '历史评估加载失败。')
    } catch (reason) {
      setDetailError(reason instanceof Error ? reason.message : '申请详情加载失败，请重试。')
    } finally { setMatchHistoryLoading(false); setLoadingDetail(false) }
  }

  function syncApplication(next: JobApplication) {
    setSelected(next)
    setItems(current => current ? { ...current, records: current.records.map(item => item.id === next.id ? next : item) } : current)
  }

  async function retryMatch() {
    if (!selected) return
    setRetryingMatch(true)
    setDetailError('')
    setActionMessage('')
    try {
      syncApplication(await request<JobApplication>(`/v1/recruitment/applications/${selected.id}/match/retry`, { method: 'POST' }))
      setMatchEvaluation(undefined)
      setActionMessage('已重新提交 AI 匹配分析，结果会自动刷新。')
    } catch (reason) {
      setDetailError(reason instanceof Error ? reason.message : '匹配重试失败，请稍后重试。')
    } finally {
      setRetryingMatch(false)
    }
  }

  async function enterAiInterview() {
    if (!selected?.interview) return
    setEnteringInterview(true)
    setDetailError('')
    try {
      if (selected.interview.status === 0) await request(`/v1/interviews/${selected.interview.id}/start`, { method: 'POST' })
      navigate(`/candidate/interviews/${selected.interview.id}/room`)
    } catch (reason) {
      setDetailError(reason instanceof Error ? reason.message : '当前还不在 AI 面试开始时间窗口内。')
    } finally {
      setEnteringInterview(false)
    }
  }

  const readyForPagination = items !== undefined
  const totalPages = items ? Math.max(1, Math.ceil(items.total / (items.pageSize || PAGE_SIZE))) : 1
  const matchDetails = selected ? parseMatchDetails(selected.matchDetails) : undefined
  const selectedStatusMeta = selected ? getApplicationStatusMeta(selected.status) : undefined
  const selectedMatchMeta = selected ? getMatchStatusMeta(selected.matchStatus) : undefined

  return <div className="space-y-6">
    <header className="rounded-[24px] border border-border bg-surface p-5 shadow-[0_12px_36px_rgba(20,18,17,.04)] sm:p-7">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0"><p className="text-sm font-bold text-[var(--accent)]">申请记录</p><h1 className="mt-2 text-3xl font-black tracking-[-.04em] sm:text-4xl">我的申请</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground sm:text-base">查看申请阶段、面试安排和企业复核进度。</p></div>
        <Link to="/jobs" className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-full border border-border px-4 text-sm font-semibold transition hover:border-[var(--accent)] hover:bg-[var(--accent-soft)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] focus-visible:ring-offset-2 focus-visible:ring-offset-background">继续找岗位</Link>
      </div>

      <form className="mt-6 grid gap-3 lg:grid-cols-[minmax(0,1fr)_12rem_auto]" aria-label="筛选我的申请" onSubmit={event => { event.preventDefault(); updateQuery('keyword', searchInput.trim()) }}>
        <label className="flex h-12 min-w-0 items-center gap-3 rounded-2xl border border-border bg-background px-4 transition focus-within:border-[var(--accent)] focus-within:ring-2 focus-within:ring-[var(--brand)]/25"><Search className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" /><span className="sr-only">搜索岗位名称</span><input name="keyword" autoComplete="off" value={searchInput} onChange={event => setSearchInput(event.target.value)} className="min-w-0 flex-1 bg-transparent text-base outline-none" placeholder="搜索岗位名称…" /><Button type="submit" className="h-10 shrink-0 px-4"><Search className="h-4 w-4" aria-hidden="true" />搜索</Button></label>
        <ResponsiveSelect ariaLabel="筛选申请进度" value={status} onValueChange={value => updateQuery('status', value)} options={statusOptions} className="w-full" />
        <Button type="button" variant="ghost" className="h-12 justify-center px-4 text-muted-foreground hover:text-foreground" onClick={clearFilters} disabled={!activeFilterCount}>清除筛选</Button>
      </form>

      <div role="status" aria-live="polite" aria-atomic="true" className="mt-5 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-border pt-4 text-sm text-muted-foreground"><span>{items ? `共 ${items.total} 条申请` : '正在加载申请记录…'}</span>{activeFilterCount > 0 && <span className="text-[var(--accent)]">已启用 {activeFilterCount} 项筛选</span>}</div>
    </header>

    {actionMessage && <p role="status" className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200"><CheckCircle2 className="mr-2 inline h-4 w-4" aria-hidden="true" />{actionMessage}</p>}
    {loadError && <div role="alert" className="flex flex-col gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-200 sm:flex-row sm:items-center sm:justify-between"><span>{loadError}</span><Button type="button" variant="secondary" className="h-10 shrink-0" onClick={() => setReloadKey(value => value + 1)}><RefreshCw className="h-4 w-4" aria-hidden="true" />重试</Button></div>}
    {detailError && !selected && <p role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-200">{detailError}</p>}

    {loadError && !items ? <Card className="grid min-h-64 place-items-center text-center shadow-none"><div className="max-w-sm"><AlertTriangle className="mx-auto h-8 w-8 text-[var(--danger-foreground)]" aria-hidden="true" /><h2 className="mt-4 text-xl font-bold">申请记录暂时不可用</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">请重试，或稍后再回来查看申请进度。</p><Button type="button" variant="secondary" className="mt-5" onClick={() => setReloadKey(value => value + 1)}><RefreshCw className="h-4 w-4" aria-hidden="true" />重新加载</Button></div></Card>
      : !items ? <ApplicationSkeleton />
        : !items.records.length ? <Card className="grid min-h-64 place-items-center text-center shadow-none"><div className="max-w-sm"><BriefcaseBusiness className="mx-auto h-8 w-8 text-muted-foreground" aria-hidden="true" /><h2 className="mt-4 text-xl font-bold">暂无匹配的申请</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">{activeFilterCount ? '换一个岗位名称或清除筛选条件后再试。' : '在岗位大厅投递后，申请进度会显示在这里。'}</p><div className="mt-5 flex flex-wrap justify-center gap-2"><Button type="button" variant="secondary" onClick={clearFilters} disabled={!activeFilterCount}>查看全部申请</Button><Link to="/jobs" className="inline-flex min-h-11 items-center justify-center rounded-full border border-border px-4 text-sm font-semibold transition hover:border-[var(--accent)] hover:bg-[var(--accent-soft)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]">去岗位大厅</Link></div></div></Card>
          : <>
            <div className="space-y-3" role="list" aria-busy={!readyForPagination} aria-label="申请记录">{items.records.map(item => <div key={item.id} role="listitem"><ApplicationCard item={item} onOpen={() => void openDetail(item.id)} /></div>)}</div>
            {totalPages > 1 && <nav aria-label="申请分页" className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5"><p className="text-sm text-muted-foreground">第 {pageNo} / {totalPages} 页</p><div className="flex items-center gap-2"><Button type="button" variant="secondary" className="h-10 px-3" disabled={pageNo <= 1} onClick={() => changePage(pageNo - 1)}><ChevronLeft className="h-4 w-4" aria-hidden="true" />上一页</Button><Button type="button" variant="secondary" className="h-10 px-3" disabled={pageNo >= totalPages} onClick={() => changePage(pageNo + 1)}>下一页<ChevronRight className="h-4 w-4" aria-hidden="true" /></Button></div></nav>}
          </>}

    {loadingDetail && <div role="status" aria-live="polite" className="fixed inset-0 z-[89] grid place-items-center bg-black/20"><div className="flex items-center gap-3 rounded-2xl bg-surface px-5 py-4 text-sm font-semibold shadow-xl"><Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" />正在打开申请详情…</div></div>}

    <Dialog.Root open={Boolean(selected)} onOpenChange={open => { if (!open) { setSelected(undefined); setDetailError('') } }}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-[90] bg-black/45 backdrop-blur-sm" />
        <Dialog.Content className="safe-area-bottom fixed inset-x-3 bottom-3 top-3 z-[91] mx-auto max-w-3xl overflow-y-auto overscroll-contain rounded-[24px] border border-border bg-surface p-5 shadow-2xl focus:outline-none sm:inset-x-6 sm:p-8 lg:bottom-auto lg:top-1/2 lg:max-h-[88vh] lg:-translate-y-1/2">
          {selected && <>
            <div className="flex items-start justify-between gap-4"><div className="min-w-0"><p className="truncate text-sm font-bold text-[var(--accent)]">{selected.companyName}</p><Dialog.Title className="mt-2 break-words text-2xl font-black tracking-[-.03em] sm:text-3xl">{selected.positionName}</Dialog.Title><Dialog.Description className="mt-2 text-sm text-muted-foreground">申请编号 {selected.applicationNo}</Dialog.Description></div><Dialog.Close aria-label="关闭申请详情" className="grid h-11 w-11 shrink-0 place-items-center rounded-full transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]"><X className="h-5 w-5" aria-hidden="true" /></Dialog.Close></div>
            {detailError && <p role="alert" className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-200">{detailError}</p>}
            <div className="mt-6 grid gap-4 sm:grid-cols-2">
              <section className="rounded-3xl border border-border bg-background p-5" aria-live="polite"><Sparkles className="h-5 w-5 text-[var(--accent)]" aria-hidden="true" /><p className="mt-3 text-xs text-muted-foreground">当前进度</p><div className="mt-2 flex flex-wrap gap-2"><Badge tone={selectedStatusMeta?.tone}>{selectedStatusMeta?.label}</Badge><Badge tone={selectedMatchMeta?.tone}>{selectedMatchMeta?.label}</Badge></div>{selected.matchScore != null && <p className="mt-4 text-3xl font-black tabular-nums">{selected.matchScore}<span className="text-sm text-muted-foreground">% 匹配度</span></p>}<p className="mt-3 text-sm leading-6 text-muted-foreground">{selected.matchSummary || '企业正在审核你的申请。'}</p>{selected.matchError && <p className="mt-3 rounded-2xl bg-rose-50 p-3 text-xs leading-5 text-rose-700 dark:bg-rose-950/30 dark:text-rose-200"><AlertTriangle className="mr-1 inline h-4 w-4" aria-hidden="true" />{selected.matchError}</p>}{selected.matchStatus === 'FAILED' && <Button type="button" variant="secondary" className="mt-4" disabled={retryingMatch} onClick={() => void retryMatch()}>{retryingMatch ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <RotateCcw className="h-4 w-4" aria-hidden="true" />}重新分析</Button>}</section>
              <section className="rounded-3xl border border-border bg-background p-5"><FileText className="h-5 w-5 text-[var(--accent)]" aria-hidden="true" /><p className="mt-3 text-xs text-muted-foreground">投递简历</p><div className="mt-2 flex flex-wrap items-center gap-2"><h2 className="font-bold">{selected.resume?.title || '账户基础资料'}</h2>{selected.resume?.parseStatus && <Badge tone={resumeStatusMeta[selected.resume.parseStatus]?.tone || 'default'}>{resumeStatusMeta[selected.resume.parseStatus]?.label || selected.resume.parseStatus}</Badge>}</div><p className="mt-2 text-sm leading-6 text-muted-foreground">{selected.resume?.summary || selected.resume?.fileName || '未关联独立简历文件'}</p></section>
            </div>
            {matchEvaluation ? <div className="mt-4"><RecruitmentMatchEvaluation evaluation={matchEvaluation} history={matchHistory} historyLoading={matchHistoryLoading} historyError={matchHistoryError} /></div> : matchDetails && <section className="mt-4 rounded-3xl border border-border bg-background p-5"><h2 className="font-bold">岗位匹配依据</h2><p className="mt-1 text-xs text-muted-foreground">AI 结果只用于辅助筛选，最终以企业面试与人工审核为准。</p><MatchList label="已匹配技能" values={matchDetails.matchedSkills} tone="info" /><MatchList label="优势" values={matchDetails.strengths} tone="success" /><MatchList label="待核实差距" values={[...(matchDetails.gaps || []), ...(matchDetails.risks || [])]} tone="warning" /></section>}
            {selected.offlineInterview && <section className="mt-4 rounded-3xl border border-[var(--accent)]/30 bg-[var(--accent-soft)] p-5"><div className="flex items-center gap-2 font-bold"><CalendarClock className="h-5 w-5 text-[var(--accent)]" aria-hidden="true" />面试邀请</div><p className="mt-3 text-lg font-black">{formatDateTime(selected.offlineInterview.scheduledAt)} · {selected.offlineInterview.durationMinutes} 分钟</p><div className="mt-2 space-y-1 text-sm text-muted-foreground">{selected.offlineInterview.location && <p><MapPin className="mr-1 inline h-4 w-4" aria-hidden="true" />{selected.offlineInterview.location}</p>}{selected.offlineInterview.meetingUrl && <p><a className="font-semibold text-[var(--accent)] underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]" href={selected.offlineInterview.meetingUrl} target="_blank" rel="noreferrer">打开视频会议链接</a></p>}{selected.offlineInterview.contactName && <p>联系人：{selected.offlineInterview.contactName} {selected.offlineInterview.contactPhone}</p>}{selected.offlineInterview.note && <p>{selected.offlineInterview.note}</p>}</div></section>}
            {selected.interview && <section className="mt-4 rounded-3xl border border-[var(--brand)]/30 bg-[var(--brand)]/10 p-5"><div className="flex items-center gap-2 font-bold"><Sparkles className="h-5 w-5 text-[var(--accent)]" aria-hidden="true" />AI 面试安排</div><p className="mt-3 text-lg font-black">{formatDateTime(selected.interview.scheduledAt)} · {selected.interview.duration} 分钟</p><p className="mt-2 text-sm text-muted-foreground">{selected.interview.title} · {selected.interview.type === 'hr' ? 'HR 面试' : selected.interview.type === 'comprehensive' ? '综合面试' : '技术面试'}</p>{selected.interview.status === 6 ? <Button type="button" className="mt-4" onClick={() => navigate(`/candidate/interviews/${selected.interview?.id}/report`)}>查看 AI 面试报告</Button> : <Button type="button" className="mt-4" disabled={enteringInterview} onClick={() => void enterAiInterview()}>{enteringInterview ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Sparkles className="h-4 w-4" aria-hidden="true" />}{selected.interview.status === 1 ? '继续 AI 面试' : '进入 AI 面试'}</Button>}</section>}
            <section className="mt-6"><h2 className="font-bold">申请时间线</h2>{selected.history.length ? <ol className="mt-4 space-y-0">{selected.history.map((entry, index) => <li key={`${entry.toStatus}-${entry.createdAt}`} className="relative flex gap-4 pb-5 last:pb-0"><span className="relative z-10 mt-1 grid h-6 w-6 shrink-0 place-items-center rounded-full bg-[var(--accent-soft)] text-[var(--accent)]"><Circle className="h-2.5 w-2.5 fill-current" aria-hidden="true" /></span>{index < selected.history.length - 1 && <span className="absolute bottom-0 left-[11px] top-6 w-px bg-border" aria-hidden="true" />}<div className="min-w-0"><p className="font-semibold">{getApplicationStatusMeta(entry.toStatus).label}</p><p className="mt-1 text-xs text-muted-foreground">{formatDateTime(entry.createdAt)} · {entry.operatorName}</p>{entry.note && <p className="mt-2 text-sm leading-6 text-muted-foreground">{entry.note}</p>}</div></li>)}</ol> : <p className="mt-3 text-sm text-muted-foreground">暂无状态变更记录。</p>}</section>
            <div className="mt-6 flex justify-end"><Dialog.Close asChild><Button type="button" variant="secondary">关闭</Button></Dialog.Close></div>
          </>}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  </div>
}

type MatchDetails = { matchedSkills?: string[]; strengths?: string[]; gaps?: string[]; risks?: string[] }

function parseMatchDetails(raw?: string): MatchDetails | undefined {
  if (!raw) return undefined
  try { return JSON.parse(raw) as MatchDetails } catch { return undefined }
}

function MatchList({ label, values, tone }: { label: string; values?: string[]; tone: 'info' | 'success' | 'warning' }) {
  if (!values?.length) return null
  return <div className="mt-4"><p className="text-xs font-bold text-muted-foreground">{label}</p><ul className="mt-2 flex flex-wrap gap-2">{values.slice(0, 8).map(value => <li key={`${label}-${value}`}><Badge tone={tone}>{value}</Badge></li>)}</ul></div>
}
