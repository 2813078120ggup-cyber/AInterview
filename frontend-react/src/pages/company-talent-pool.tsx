import { ChevronLeft, ChevronRight, RefreshCw, Search, SlidersHorizontal, Tag, UserRound, UsersRound } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'
import { appendQuery, formatDateTime, type PageResult, type RecruitmentJob, type TalentPoolCandidate, type TalentPoolQuery, type TalentPoolTag } from '@/lib/recruitment'

const pageSize = 12
const sortOptions = [
  { value: 'UPDATED', label: '最近更新' },
  { value: 'LAST_CONTACTED', label: '最近联系' },
  { value: 'NAME', label: '候选人姓名' },
  { value: 'APPLICATIONS', label: '申请数量' },
]
const emptyDraft = { keyword: '', skill: '', lastContactFrom: '', lastContactTo: '' }

function pageNumber(value: string | null) {
  const parsed = Number(value || '1')
  return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : 1
}

export function CompanyTalentPool() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const pageNo = pageNumber(searchParams.get('pageNo'))
  const query = useMemo<TalentPoolQuery>(() => ({
    keyword: searchParams.get('keyword') || undefined,
    tagId: searchParams.get('tagId') || undefined,
    skill: searchParams.get('skill') || undefined,
    positionId: searchParams.get('positionId') || undefined,
    lastContactFrom: searchParams.get('lastContactFrom') || undefined,
    lastContactTo: searchParams.get('lastContactTo') || undefined,
    sort: searchParams.get('sort') || 'UPDATED',
  }), [searchParams])
  const [draft, setDraft] = useState(emptyDraft)
  const [result, setResult] = useState<PageResult<TalentPoolCandidate>>()
  const [tags, setTags] = useState<TalentPoolTag[]>([])
  const [positions, setPositions] = useState<RecruitmentJob[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filtersError, setFiltersError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => setDraft({
    keyword: query.keyword || '',
    skill: query.skill || '',
    lastContactFrom: query.lastContactFrom || '',
    lastContactTo: query.lastContactTo || '',
  }), [query.keyword, query.skill, query.lastContactFrom, query.lastContactTo])

  const loadFilters = useCallback(async () => {
    const [tagResult, positionResult] = await Promise.allSettled([
      request<TalentPoolTag[]>('/v1/company/recruitment/talent-pool/tags'),
      request<PageResult<RecruitmentJob>>('/v1/company/recruitment/positions?pageNo=1&pageSize=100'),
    ])
    const errors: string[] = []
    if (tagResult.status === 'fulfilled') setTags(tagResult.value)
    else errors.push('标签')
    if (positionResult.status === 'fulfilled') setPositions(positionResult.value.records)
    else errors.push('岗位')
    setFiltersError(errors.length ? `${errors.join('、')}筛选暂时不可用` : '')
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setResult(await request<PageResult<TalentPoolCandidate>>(`/v1/company/recruitment/talent-pool${appendQuery({ ...query, pageNo, pageSize })}`))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '人才库加载失败')
    } finally { setLoading(false) }
  }, [pageNo, query])

  useEffect(() => { void loadFilters() }, [loadFilters])
  useEffect(() => { void load() }, [load, reloadKey])

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

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    updateParams({ ...draft, tagId: searchParams.get('tagId') || undefined, positionId: searchParams.get('positionId') || undefined })
  }

  function resetFilters() {
    setDraft(emptyDraft)
    setSearchParams(current => {
      const next = new URLSearchParams(current)
      ;['keyword', 'tagId', 'skill', 'positionId', 'lastContactFrom', 'lastContactTo', 'pageNo'].forEach(key => next.delete(key))
      return next
    }, { replace: true })
  }

  const totalPages = Math.max(1, Math.ceil((result?.total || 0) / pageSize))
  const hasFilters = Object.values(query).some(Boolean)
  const tagOptions = [{ value: '', label: '全部标签' }, ...tags.map(tag => ({ value: tag.id, label: tag.name }))]
  const positionOptions = [{ value: '', label: '全部岗位' }, ...positions.map(position => ({ value: position.id, label: position.name }))]

  return <div className="min-w-0 space-y-6">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0"><p className="text-sm font-bold text-[var(--accent)]">招聘协作</p><h1 className="mt-2 break-words text-3xl font-black tracking-[-.04em]">企业人才库</h1><p className="mt-2 max-w-2xl text-muted-foreground">把值得持续联系的人沉淀下来，围绕申请历史、标签和最近联系安排下一步。</p></div>
      <Button type="button" onClick={() => updateParams({ pageNo: 1 }, false)}><RefreshCw className="h-4 w-4" />刷新</Button>
    </header>

    <Card className="p-4 sm:p-5"><form className="grid min-w-0 gap-3 md:grid-cols-2 xl:grid-cols-[minmax(220px,1.5fr)_repeat(3,minmax(140px,1fr))_auto]" onSubmit={applyFilters}>
      <label className="flex h-12 min-w-0 items-center gap-3 rounded-full border border-border bg-background px-4 focus-within:border-[var(--accent)] focus-within:ring-2 focus-within:ring-[var(--accent)]/20 md:col-span-2 xl:col-span-1"><Search className="h-4 w-4 shrink-0 text-muted-foreground" /><span className="sr-only">搜索人才库</span><input className="min-w-0 flex-1 bg-transparent text-sm outline-none" value={draft.keyword} onChange={event => setDraft(current => ({ ...current, keyword: event.target.value }))} placeholder="搜索姓名、联系方式或岗位" /></label>
      <ResponsiveSelect ariaLabel="人才库标签" value={searchParams.get('tagId') || ''} onValueChange={tagId => updateParams({ tagId })} options={tagOptions} searchable disabled={!tags.length && Boolean(filtersError)} />
      <ResponsiveSelect ariaLabel="历史申请岗位" value={searchParams.get('positionId') || ''} onValueChange={positionId => updateParams({ positionId })} options={positionOptions} searchable disabled={!positions.length && Boolean(filtersError)} />
      <label className="flex h-12 min-w-0 items-center rounded-full border border-border bg-background px-4"><span className="mr-2 shrink-0 text-xs text-muted-foreground">技能</span><input className="min-w-0 flex-1 bg-transparent text-sm outline-none" value={draft.skill} onChange={event => setDraft(current => ({ ...current, skill: event.target.value }))} placeholder="例如 Java" /></label>
      <div className="flex gap-2 md:col-span-2 xl:col-span-1 xl:justify-end"><Button type="submit" className="flex-1 xl:flex-none">筛选</Button><Button type="button" variant="secondary" className="px-4" onClick={resetFilters} aria-label="重置人才库筛选"><SlidersHorizontal className="h-4 w-4" /><span className="hidden sm:inline">重置</span></Button></div>
      <div className="grid gap-3 sm:grid-cols-2 md:col-span-2"><label className="flex h-11 min-w-0 items-center rounded-2xl border border-border bg-background px-3"><span className="mr-2 shrink-0 text-xs text-muted-foreground">联系自</span><input type="date" className="min-w-0 flex-1 bg-transparent text-sm outline-none" value={draft.lastContactFrom} onChange={event => setDraft(current => ({ ...current, lastContactFrom: event.target.value }))} /></label><label className="flex h-11 min-w-0 items-center rounded-2xl border border-border bg-background px-3"><span className="mr-2 shrink-0 text-xs text-muted-foreground">联系至</span><input type="date" className="min-w-0 flex-1 bg-transparent text-sm outline-none" value={draft.lastContactTo} onChange={event => setDraft(current => ({ ...current, lastContactTo: event.target.value }))} /></label></div>
    </form><div className="mt-3 flex flex-wrap items-center gap-3"><ResponsiveSelect ariaLabel="人才库排序" value={query.sort || 'UPDATED'} onValueChange={sort => updateParams({ sort })} options={sortOptions} className="sm:w-44" />{filtersError && <p className="text-xs text-amber-700 dark:text-amber-300" role="status">{filtersError}</p>}</div></Card>

    {error && <Card className="border-rose-200 bg-rose-50/80 p-4 text-sm text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/30" role="alert"><span className="break-words">{error}</span><Button type="button" variant="secondary" className="ml-3 h-9 px-3" onClick={() => setReloadKey(value => value + 1)}>重试</Button></Card>}
    {loading && !result ? <LoadingState /> : !result?.records.length ? <EmptyState hasFilters={hasFilters} onReset={resetFilters} /> : <>
      <Card className="hidden overflow-hidden p-0 md:block"><div className="overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm"><thead className="bg-muted/55 text-xs text-muted-foreground"><tr><th className="px-5 py-4">候选人</th><th className="px-5 py-4">标签 / 最近联系</th><th className="px-5 py-4">当前企业申请</th><th className="px-5 py-4">最近活动</th><th className="px-5 py-4 text-right">操作</th></tr></thead><tbody className="divide-y divide-border">{result.records.map(candidate => <CandidateRow key={candidate.poolId} candidate={candidate} onOpen={() => navigate(`/company/talent-pool/${candidate.candidateId}`)} />)}</tbody></table></div></Card>
      <div className="grid gap-3 md:hidden">{result.records.map(candidate => <CandidateCard key={candidate.poolId} candidate={candidate} onOpen={() => navigate(`/company/talent-pool/${candidate.candidateId}`)} />)}</div>
      <Pagination pageNo={pageNo} totalPages={totalPages} onChange={next => updateParams({ pageNo: next }, false)} />
    </>}
  </div>
}

function CandidateRow({ candidate, onOpen }: { candidate: TalentPoolCandidate; onOpen: () => void }) {
  return <tr className="align-top transition hover:bg-muted/25"><td className="px-5 py-4"><button type="button" className="flex min-w-0 items-center gap-3 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]" onClick={onOpen}><span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><UserRound className="h-4 w-4" /></span><span className="min-w-0"><strong className="block max-w-52 truncate">{candidate.candidateName || '未命名候选人'}</strong><span className="mt-1 block max-w-52 truncate text-xs text-muted-foreground">{candidate.email || candidate.phone || '联系方式未授权或未填写'}</span></span></button></td><td className="px-5 py-4"><div className="flex max-w-64 flex-wrap gap-1.5">{candidate.tags.length ? candidate.tags.map(tag => <Badge key={tag.id} tone="info">{tag.name}</Badge>) : <span className="text-muted-foreground">未添加标签</span>}</div><p className="mt-2 text-xs text-muted-foreground">最近联系 {formatDateTime(candidate.lastContactedAt)}</p></td><td className="px-5 py-4"><strong>{candidate.applicationCount}</strong><span className="ml-1 text-muted-foreground">次申请</span><p className="mt-1 max-w-48 truncate text-xs text-muted-foreground">最近岗位申请于 {formatDateTime(candidate.lastApplicationAt)}</p></td><td className="px-5 py-4 text-muted-foreground">{formatDateTime(candidate.lastActivityAt || candidate.updatedAt)}</td><td className="px-5 py-4 text-right"><Button type="button" variant="secondary" className="h-9 px-3" onClick={onOpen}>打开档案<ChevronRight className="h-4 w-4" /></Button></td></tr>
}

function CandidateCard({ candidate, onOpen }: { candidate: TalentPoolCandidate; onOpen: () => void }) {
  return <Card className="min-w-0 p-4"><div className="flex items-start justify-between gap-3"><span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><UsersRound className="h-5 w-5" /></span><span className="text-xs text-muted-foreground">{candidate.applicationCount} 次申请</span></div><h2 className="mt-4 break-words text-lg font-black">{candidate.candidateName || '未命名候选人'}</h2><p className="mt-1 break-words text-sm text-muted-foreground">{candidate.email || candidate.phone || '联系方式未授权或未填写'}</p><div className="mt-4 flex flex-wrap gap-1.5">{candidate.tags.length ? candidate.tags.map(tag => <Badge key={tag.id} tone="info">{tag.name}</Badge>) : <span className="text-xs text-muted-foreground"><Tag className="mr-1 inline h-3.5 w-3.5" />未添加标签</span>}</div><div className="mt-4 grid grid-cols-2 gap-3 border-t border-border pt-3 text-xs"><span className="text-muted-foreground">最近联系<strong className="mt-1 block text-foreground">{formatDateTime(candidate.lastContactedAt)}</strong></span><span className="text-right text-muted-foreground">最近申请<strong className="mt-1 block text-foreground">{formatDateTime(candidate.lastApplicationAt)}</strong></span></div><Button type="button" variant="secondary" className="mt-4 w-full" onClick={onOpen}>查看人才档案<ChevronRight className="h-4 w-4" /></Button></Card>
}

function Pagination({ pageNo, totalPages, onChange }: { pageNo: number; totalPages: number; onChange: (page: number) => void }) { if (totalPages <= 1) return null; return <nav aria-label="人才库分页" className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5 text-sm text-muted-foreground"><span>第 {pageNo} / {totalPages} 页</span><div className="flex items-center gap-2"><Button type="button" variant="secondary" className="h-10 px-3" disabled={pageNo <= 1} onClick={() => onChange(pageNo - 1)}><ChevronLeft className="h-4 w-4" />上一页</Button><Button type="button" variant="secondary" className="h-10 px-3" disabled={pageNo >= totalPages} onClick={() => onChange(pageNo + 1)}>下一页<ChevronRight className="h-4 w-4" /></Button></div></nav> }
function LoadingState() { return <Card className="grid min-h-64 place-items-center" role="status"><div className="text-center"><div className="mx-auto h-6 w-6 animate-spin rounded-full border-2 border-[var(--accent)] border-t-transparent" /><p className="mt-3 text-sm text-muted-foreground">正在加载企业人才库…</p></div></Card> }
function EmptyState({ hasFilters, onReset }: { hasFilters: boolean; onReset: () => void }) { return <Card className="grid min-h-64 place-items-center text-center"><div><UsersRound className="mx-auto h-8 w-8 text-muted-foreground" /><h2 className="mt-3 font-bold">{hasFilters ? '没有符合条件的人才' : '人才库还是空的'}</h2><p className="mt-1 text-sm text-muted-foreground">{hasFilters ? '调整筛选条件后再试试。' : '从候选人申请档案加入值得持续联系的人。'}</p>{hasFilters && <Button type="button" variant="secondary" className="mt-5" onClick={onReset}>清除筛选</Button>}</div></Card> }
