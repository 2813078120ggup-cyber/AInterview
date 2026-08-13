import * as Dialog from '@radix-ui/react-dialog'
import { Building2, CalendarDays, CheckCircle2, ChevronLeft, ChevronRight, Clock3, GraduationCap, Loader2, MapPin, RefreshCw, Search, Send, Sparkles, WalletCards, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'
import { appendQuery, formatDateTime, positionStatusMeta, salaryLabel, type PageResult, type RecruitmentJob, type Resume } from '@/lib/recruitment'

const PAGE_SIZE = 9

const cityOptions = [
  { value: '', label: '全部城市' },
  { value: '北京', label: '北京' },
  { value: '上海', label: '上海' },
  { value: '深圳', label: '深圳' },
  { value: '杭州', label: '杭州' },
]

const jobTypeOptions = [
  { value: '', label: '全部类型' },
  { value: 'FULL_TIME', label: '全职' },
  { value: 'INTERNSHIP', label: '实习' },
  { value: 'PART_TIME', label: '兼职' },
]

const experienceOptions = [
  { value: '', label: '全部经验' },
  { value: '经验不限', label: '经验不限' },
  { value: '1-3年', label: '1-3年' },
  { value: '2-4年', label: '2-4年' },
  { value: '3-5年', label: '3-5年' },
  { value: '5年以上', label: '5年以上' },
]

const educationOptions = [
  { value: '', label: '全部学历' },
  { value: '大专及以上', label: '大专及以上' },
  { value: '本科及以上', label: '本科及以上' },
  { value: '硕士及以上', label: '硕士及以上' },
]

const salaryOptions = [
  { value: '', label: '最低薪资' },
  { value: '5', label: '5K 起' },
  { value: '10', label: '10K 起' },
  { value: '15', label: '15K 起' },
  { value: '20', label: '20K 起' },
  { value: '30', label: '30K 起' },
]

const jobTypeLabels: Record<string, string> = {
  FULL_TIME: '全职',
  INTERNSHIP: '实习',
  PART_TIME: '兼职',
}

function JobCardSkeleton() {
  return <div aria-hidden="true" className="min-h-[320px] animate-pulse rounded-[20px] border border-border bg-surface p-5 sm:p-6">
    <div className="flex items-start justify-between gap-3"><div className="h-12 w-12 rounded-2xl bg-muted" /><div className="h-6 w-16 rounded-full bg-muted" /></div>
    <div className="mt-6 h-6 w-3/5 rounded bg-muted" />
    <div className="mt-3 h-4 w-2/5 rounded bg-muted" />
    <div className="mt-5 grid grid-cols-2 gap-3"><div className="h-4 rounded bg-muted" /><div className="h-4 rounded bg-muted" /><div className="h-4 rounded bg-muted" /><div className="h-4 rounded bg-muted" /></div>
    <div className="mt-5 flex gap-2"><div className="h-6 w-14 rounded-md bg-muted" /><div className="h-6 w-20 rounded-md bg-muted" /><div className="h-6 w-16 rounded-md bg-muted" /></div>
    <div className="mt-8 flex items-center justify-between border-t border-border pt-4"><div className="h-6 w-24 rounded bg-muted" /><div className="h-10 w-24 rounded-full bg-muted" /></div>
  </div>
}

function JobsSkeleton() {
  return <div role="status" aria-label="正在加载岗位…" aria-busy="true" className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
    {Array.from({ length: 6 }, (_, index) => <JobCardSkeleton key={index} />)}
  </div>
}

function jobMeta(job: RecruitmentJob) {
  return [
    { label: job.city || '地点面议', Icon: MapPin },
    { label: job.experienceRequirement || '经验不限', Icon: Clock3 },
    { label: job.educationRequirement || '学历不限', Icon: GraduationCap },
    { label: job.publishedAt ? `发布于 ${formatDateTime(job.publishedAt)}` : '近期发布', Icon: CalendarDays },
  ]
}

export function CandidateJobHall() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [jobs, setJobs] = useState<PageResult<RecruitmentJob>>()
  const [resumes, setResumes] = useState<Resume[]>([])
  const [searchInput, setSearchInput] = useState(searchParams.get('keyword') ?? '')
  const [selected, setSelected] = useState<RecruitmentJob>()
  const [resumeId, setResumeId] = useState('')
  const [busy, setBusy] = useState<string>()
  const [message, setMessage] = useState('')
  const [actionError, setActionError] = useState('')
  const [loadError, setLoadError] = useState('')
  const [resumeError, setResumeError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  const keyword = searchParams.get('keyword') ?? ''
  const city = searchParams.get('city') ?? ''
  const experience = searchParams.get('experience') ?? ''
  const education = searchParams.get('education') ?? ''
  const jobType = searchParams.get('jobType') ?? ''
  const minSalary = searchParams.get('minSalary') ?? ''
  const pageNoValue = Number(searchParams.get('pageNo') ?? '1')
  const pageNo = Number.isFinite(pageNoValue) && pageNoValue > 0 ? Math.floor(pageNoValue) : 1
  const queryString = searchParams.toString()
  const activeFilterCount = [keyword, city, experience, education, jobType, minSalary].filter(Boolean).length

  useEffect(() => {
    setSearchInput(keyword)
  }, [keyword])

  useEffect(() => {
    let active = true
    setJobs(undefined)
    setLoadError('')
    setResumeError('')

    const query = appendQuery({
      keyword,
      city,
      experience,
      education,
      jobType,
      minSalary: minSalary ? Number(minSalary) : undefined,
      pageNo,
      pageSize: PAGE_SIZE,
    })

    Promise.allSettled([
      request<PageResult<RecruitmentJob>>(`/v1/recruitment/jobs${query}`),
      request<Resume[]>('/v1/recruitment/resumes'),
    ]).then(([jobResult, resumeResult]) => {
      if (!active) return

      if (jobResult.status === 'fulfilled') {
        setJobs(jobResult.value)
      } else {
        setLoadError(jobResult.reason instanceof Error ? jobResult.reason.message : '岗位加载失败，请重试。')
      }

      if (resumeResult.status === 'fulfilled') {
        setResumes(resumeResult.value)
        const ready = resumeResult.value.filter(item => item.parseStatus === 'SUCCESS' || item.parseStatus === 'MANUAL')
        setResumeId(current => current && ready.some(item => item.id === current) ? current : ready.find(item => item.defaultResume)?.id || ready[0]?.id || '')
      } else {
        setResumeError(resumeResult.reason instanceof Error ? resumeResult.reason.message : '简历状态暂时不可用。')
      }
    })

    return () => { active = false }
  }, [city, education, experience, jobType, keyword, minSalary, pageNo, queryString, reloadKey])

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
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  async function apply(job: RecruitmentJob) {
    setBusy(job.id)
    setActionError('')
    setMessage('')
    try {
      await request(`/v1/recruitment/jobs/${job.id}/applications`, {
        method: 'POST',
        body: JSON.stringify({ resumeId: resumeId || undefined }),
      })
      setJobs(current => current ? { ...current, records: current.records.map(item => item.id === job.id ? { ...item, applied: true } : item) } : current)
      setSelected(current => current?.id === job.id ? { ...current, applied: true } : current)
      setMessage(`已成功投递“${job.name}”，可在“我的申请”查看进度。`)
    } catch (reason) {
      setActionError(reason instanceof Error ? reason.message : '投递失败，请稍后重试。')
    } finally {
      setBusy(undefined)
    }
  }

  const readyResumes = resumes.filter(item => item.parseStatus === 'SUCCESS' || item.parseStatus === 'MANUAL')
  const resumeOptions = readyResumes.map(item => ({ value: item.id, label: `${item.title}${item.defaultResume ? '（默认）' : ''}` }))
  const totalPages = jobs ? Math.max(1, Math.ceil(jobs.total / (jobs.pageSize || PAGE_SIZE))) : 1
  const statusMeta = positionStatusMeta.PUBLISHED

  return <div className="space-y-6">
    <header className="rounded-[24px] border border-border bg-surface p-5 shadow-[0_12px_36px_rgba(20,18,17,.04)] sm:p-7">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0 max-w-3xl">
          <h1 className="text-3xl font-black tracking-[-.03em] text-pretty sm:text-4xl">岗位大厅</h1>
          <p className="mt-3 max-w-2xl text-sm leading-7 text-muted-foreground sm:text-base">按城市、经验、学历和薪资筛选岗位，查看职位要求并提交申请。</p>
        </div>
        <Link to="/applications" className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-full border border-border px-4 text-sm font-semibold transition hover:border-[var(--accent)] hover:bg-[var(--accent-soft)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] focus-visible:ring-offset-2 focus-visible:ring-offset-background">查看我的申请</Link>
      </div>

      <form className="mt-7" aria-label="搜索岗位" onSubmit={event => { event.preventDefault(); updateQuery('keyword', searchInput.trim()) }}>
        <label className="flex h-12 items-center gap-3 rounded-2xl border border-border bg-background px-4 transition focus-within:border-[var(--accent)] focus-within:ring-2 focus-within:ring-[var(--brand)]/25">
          <Search className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <span className="sr-only">搜索岗位、公司或技能</span>
          <input name="keyword" autoComplete="off" value={searchInput} onChange={event => setSearchInput(event.target.value)} className="min-w-0 flex-1 bg-transparent text-base outline-none" placeholder="搜索岗位、公司或技能…" />
          <Button type="submit" className="h-10 shrink-0 px-4"><Search className="h-4 w-4" aria-hidden="true" />搜索</Button>
        </label>

        <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
          <ResponsiveSelect ariaLabel="选择城市" value={city} onValueChange={value => updateQuery('city', value)} options={cityOptions} className="w-full" />
          <ResponsiveSelect ariaLabel="选择岗位类型" value={jobType} onValueChange={value => updateQuery('jobType', value)} options={jobTypeOptions} className="w-full" />
          <ResponsiveSelect ariaLabel="选择经验要求" value={experience} onValueChange={value => updateQuery('experience', value)} options={experienceOptions} className="w-full" />
          <ResponsiveSelect ariaLabel="选择学历要求" value={education} onValueChange={value => updateQuery('education', value)} options={educationOptions} className="w-full" />
          <ResponsiveSelect ariaLabel="选择最低薪资" value={minSalary} onValueChange={value => updateQuery('minSalary', value)} options={salaryOptions} className="w-full" />
          <Button type="button" variant="ghost" className="h-12 justify-center px-4 text-muted-foreground hover:text-foreground" onClick={clearFilters} disabled={!activeFilterCount}>清除筛选</Button>
        </div>
      </form>

      <div className="mt-5 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-border pt-4 text-sm text-muted-foreground">
        <span>{jobs ? `共 ${jobs.total} 个岗位` : '正在加载岗位…'}</span>
        {activeFilterCount > 0 && <span className="text-[var(--accent)]">已启用 {activeFilterCount} 项筛选</span>}
        {resumeError && <span role="status" className="text-[var(--warning-foreground)]">简历状态暂时不可用，仍可浏览岗位</span>}
      </div>
    </header>

    <div role="status" aria-live="polite" aria-atomic="true" className="space-y-2">
      {message && <p className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200"><CheckCircle2 className="mr-2 inline h-4 w-4" aria-hidden="true" />{message}</p>}
      {actionError && <p role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-200">{actionError}</p>}
    </div>

    {loadError ? <Card className="grid min-h-64 place-items-center text-center shadow-none">
      <div className="max-w-sm">
        <p className="text-lg font-bold">岗位暂时加载失败</p>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">{loadError} 你可以重试，或先调整筛选条件。</p>
        <Button type="button" variant="secondary" className="mt-5" onClick={() => setReloadKey(value => value + 1)}><RefreshCw className="h-4 w-4" aria-hidden="true" />重试</Button>
      </div>
    </Card> : !jobs ? <JobsSkeleton /> : !jobs.records.length ? <Card className="grid min-h-64 place-items-center text-center shadow-none">
      <div className="max-w-sm">
        <Search className="mx-auto h-8 w-8 text-muted-foreground" aria-hidden="true" />
        <h2 className="mt-4 text-xl font-bold">没有找到匹配岗位</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">换一个关键词，或清除部分筛选条件后再试。</p>
        <Button type="button" variant="secondary" className="mt-5" onClick={clearFilters} disabled={!activeFilterCount}>查看全部岗位</Button>
      </div>
    </Card> : <>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-busy={!jobs}>
        {jobs.records.map((job, index) => <Card key={job.id} motionDelay={Math.min(index * .03, .18)} aria-labelledby={`job-${job.id}`} className="group flex min-h-[320px] flex-col rounded-[20px] p-5 shadow-none transition-colors hover:border-[var(--accent)]/70 sm:p-6">
          <div className="flex items-start justify-between gap-3">
            {job.company.logoUrl ? <img src={job.company.logoUrl} width={48} height={48} loading="lazy" alt={`${job.company.name} logo`} className="h-12 w-12 shrink-0 rounded-2xl border border-border bg-surface object-contain p-2" /> : <div className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><Building2 className="h-5 w-5" aria-hidden="true" /></div>}
            <Badge tone={job.applied ? 'success' : statusMeta.tone}>{job.applied ? <><CheckCircle2 className="mr-1 h-3.5 w-3.5" aria-hidden="true" />已投递</> : statusMeta.label}</Badge>
          </div>
          <h2 id={`job-${job.id}`} className="mt-5 break-words text-xl font-black tracking-[-.02em]">{job.name}</h2>
          <p className="mt-1 min-w-0 truncate text-sm font-semibold text-muted-foreground">{job.company.shortName || job.company.name}{job.department ? ` · ${job.department}` : ''}</p>
          <div className="mt-4 flex items-baseline gap-2">
            <strong className="text-lg font-bold tabular-nums text-[var(--accent)]"><WalletCards className="mr-1 inline h-4 w-4" aria-hidden="true" />{salaryLabel(job)}</strong>
            <span className="text-xs text-muted-foreground">{jobTypeLabels[job.jobType] || job.jobType}</span>
          </div>
          <div className="mt-4 grid grid-cols-2 gap-x-3 gap-y-2 border-y border-border py-3 text-xs text-muted-foreground">
            {jobMeta(job).map(({ label, Icon }) => <span key={label} className="flex min-w-0 items-center gap-1.5"><Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" /><span className="min-w-0 truncate">{label}</span></span>)}
          </div>
          <div className="mt-4 flex min-h-7 flex-wrap gap-1.5">
            {job.skillTags.slice(0, 3).map(tag => <span key={tag} className="rounded-md border border-border px-2 py-1 text-xs text-muted-foreground">{tag}</span>)}
            {job.skillTags.length > 3 && <span className="px-1 py-1 text-xs text-muted-foreground">+{job.skillTags.length - 3}</span>}
          </div>
          <div className="mt-auto flex items-center justify-between gap-3 border-t border-border pt-4">
            <span className="text-xs text-muted-foreground">查看岗位详情与投递要求</span>
            <Button type="button" variant="secondary" className="h-10 shrink-0 px-4" onClick={() => { setSelected(job); setMessage(''); setActionError('') }}>查看详情</Button>
          </div>
        </Card>)}
      </div>

      {totalPages > 1 && <nav aria-label="岗位分页" className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5">
        <p className="text-sm text-muted-foreground">第 {pageNo} / {totalPages} 页</p>
        <div className="flex items-center gap-2">
          <Button type="button" variant="secondary" className="h-10 px-3" disabled={pageNo <= 1} onClick={() => changePage(pageNo - 1)}><ChevronLeft className="h-4 w-4" aria-hidden="true" />上一页</Button>
          <Button type="button" variant="secondary" className="h-10 px-3" disabled={pageNo >= totalPages} onClick={() => changePage(pageNo + 1)}>下一页<ChevronRight className="h-4 w-4" aria-hidden="true" /></Button>
        </div>
      </nav>}
    </>}

    <Dialog.Root open={Boolean(selected)} onOpenChange={open => !open && setSelected(undefined)}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-[90] bg-black/45 backdrop-blur-sm" />
        <Dialog.Content className="safe-area-bottom fixed inset-x-3 bottom-3 top-3 z-[91] mx-auto max-w-4xl overflow-y-auto overscroll-contain rounded-[24px] border border-border bg-surface p-5 shadow-2xl focus:outline-none sm:inset-x-6 sm:p-8 lg:bottom-auto lg:top-1/2 lg:max-h-[88vh] lg:-translate-y-1/2">
          {selected && <>
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0">
                <p className="truncate text-sm font-bold text-[var(--accent)]">{selected.company.name}</p>
                <Dialog.Title className="mt-2 break-words text-2xl font-black tracking-[-.03em] sm:text-3xl">{selected.name}</Dialog.Title>
                <Dialog.Description className="mt-2 text-sm leading-6 text-muted-foreground">{selected.department || '招聘团队'} · {jobTypeLabels[selected.jobType] || selected.jobType}</Dialog.Description>
              </div>
              <Dialog.Close aria-label="关闭岗位详情" className="grid h-11 w-11 shrink-0 place-items-center rounded-full transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]"><X className="h-5 w-5" aria-hidden="true" /></Dialog.Close>
            </div>

            <dl className="mt-6 grid grid-cols-2 gap-3 border-y border-border py-4 sm:grid-cols-5">
              <div><dt className="text-xs text-muted-foreground">薪资范围</dt><dd className="mt-1 font-bold tabular-nums text-[var(--accent)]">{salaryLabel(selected)}</dd></div>
              <div><dt className="text-xs text-muted-foreground">工作城市</dt><dd className="mt-1 font-semibold">{selected.city || '地点面议'}</dd></div>
              <div><dt className="text-xs text-muted-foreground">经验要求</dt><dd className="mt-1 font-semibold">{selected.experienceRequirement || '经验不限'}</dd></div>
              <div><dt className="text-xs text-muted-foreground">学历要求</dt><dd className="mt-1 font-semibold">{selected.educationRequirement || '学历不限'}</dd></div>
              <div><dt className="text-xs text-muted-foreground">发布时间</dt><dd className="mt-1 font-semibold">{selected.publishedAt ? formatDateTime(selected.publishedAt) : '近期发布'}</dd></div>
            </dl>

            <div className="mt-7 grid gap-8 lg:grid-cols-[minmax(0,1fr)_280px]">
              <div className="min-w-0 space-y-7">
                <section><h2 className="text-lg font-bold">岗位介绍</h2><p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-muted-foreground">{selected.description || '暂无岗位介绍'}</p></section>
                <section><h2 className="text-lg font-bold">任职要求</h2><p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-muted-foreground">{selected.requirements || '暂无补充要求'}</p></section>
                <section><h2 className="text-lg font-bold">核心技能</h2><div className="mt-3 flex flex-wrap gap-2">{selected.skillTags.length ? selected.skillTags.map(tag => <Badge key={tag}>{tag}</Badge>) : <p className="text-sm text-muted-foreground">暂无技能标签</p>}</div></section>
                {(selected.company.industry || selected.company.companySize || selected.company.city || selected.company.description) && <section><h2 className="text-lg font-bold">公司信息</h2><div className="mt-3 space-y-2 text-sm leading-6 text-muted-foreground"><p className="font-semibold text-foreground">{selected.company.name}</p>{(selected.company.industry || selected.company.companySize || selected.company.city) && <p>{[selected.company.industry, selected.company.companySize, selected.company.city].filter(Boolean).join(' · ')}</p>}{selected.company.description && <p className="whitespace-pre-wrap">{selected.company.description}</p>}</div></section>}
              </div>

              <aside className="h-fit rounded-2xl border border-border bg-background p-5 lg:sticky lg:top-4">
                <Sparkles className="h-5 w-5 text-[var(--accent)]" aria-hidden="true" />
                <h2 className="mt-3 text-lg font-bold">投递岗位</h2>
                <p className="mt-2 text-sm leading-6 text-muted-foreground">提交后将进行岗位匹配分析，最终结果以企业复核为准。</p>
                {resumeError && <p role="alert" className="mt-4 rounded-xl bg-[var(--warning)] px-3 py-2 text-xs leading-5 text-[var(--warning-foreground)]">简历状态暂时不可用，请稍后重试。</p>}
                {resumeOptions.length ? <div className="mt-4"><ResponsiveSelect ariaLabel="选择投递简历" value={resumeId} onValueChange={setResumeId} options={resumeOptions} className="w-full" /></div> : <p className="mt-4 rounded-xl bg-[var(--warning)] px-3 py-2 text-xs leading-5 text-[var(--warning-foreground)]">请先上传并完成一份可用简历，再继续投递。<Link to="/resumes" className="ml-1 font-bold underline underline-offset-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]">去简历中心</Link></p>}
                <Button type="button" className="mt-4 w-full" disabled={selected.applied || busy === selected.id || !resumeOptions.length} aria-busy={busy === selected.id} onClick={() => void apply(selected)}>
                  {busy === selected.id ? <><Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />投递中…</> : selected.applied ? <><CheckCircle2 className="h-4 w-4" aria-hidden="true" />已投递</> : <><Send className="h-4 w-4" aria-hidden="true" />立即投递</>}
                </Button>
              </aside>
            </div>
          </>}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  </div>
}
