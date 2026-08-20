import { BriefcaseBusiness, ChevronLeft, ChevronRight, MapPin, Plus, Search, SlidersHorizontal } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect, type ResponsiveSelectOption } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'
import { approvalStatusMeta, appendQuery, formatDateTime, positionStatusMeta, salaryLabel, type PageResult, type RecruitmentJob } from '@/lib/recruitment'

type PositionFilters = {
  keyword: string
  status: string
  city: string
  department: string
}

const emptyFilters: PositionFilters = { keyword: '', status: '', city: '', department: '' }
const statusOptions: ResponsiveSelectOption[] = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '招聘中' },
  { value: 'CLOSED', label: '已关闭' },
]

export function CompanyPositions() {
  const navigate = useNavigate()
  const [items, setItems] = useState<PageResult<RecruitmentJob>>()
  const [filters, setFilters] = useState(emptyFilters)
  const [draftFilters, setDraftFilters] = useState(emptyFilters)
  const [pageNo, setPageNo] = useState(1)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const result = await request<PageResult<RecruitmentJob>>(`/v1/company/recruitment/positions${appendQuery({
        pageNo,
        pageSize: 10,
        keyword: filters.keyword,
        status: filters.status,
        city: filters.city,
        department: filters.department,
      })}`)
      setItems(result)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '岗位加载失败')
    } finally {
      setLoading(false)
    }
  }, [filters, pageNo])

  useEffect(() => { void load() }, [load])

  const totalPages = useMemo(() => Math.max(1, Math.ceil((items?.total ?? 0) / (items?.pageSize || 10))), [items])

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFilters({ ...draftFilters, keyword: draftFilters.keyword.trim(), city: draftFilters.city.trim(), department: draftFilters.department.trim() })
    setPageNo(1)
  }

  function resetFilters() {
    setDraftFilters(emptyFilters)
    setFilters(emptyFilters)
    setPageNo(1)
  }

  return <div className="space-y-6">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p className="text-sm font-bold text-[var(--accent)]">招聘管理</p>
        <h1 className="mt-2 text-3xl font-black tracking-[-.04em]">岗位管理</h1>
        <p className="mt-2 max-w-2xl text-muted-foreground">HR 创建招聘需求并绑定编制、成本中心和预算；超级管理员批准后岗位才会发布。</p>
      </div>
      <Button onClick={() => navigate('/company/positions/new')}><Plus className="h-4 w-4" />新建岗位</Button>
    </header>

    <Card className="p-4 sm:p-5">
      <form className="grid gap-3 md:grid-cols-2 xl:grid-cols-[minmax(220px,1.5fr)_repeat(3,minmax(140px,1fr))_auto]" onSubmit={applyFilters}>
        <label className="flex h-12 min-w-0 items-center gap-3 rounded-full border border-border bg-background px-4 focus-within:border-[var(--accent)] focus-within:ring-2 focus-within:ring-[var(--accent)]/20 md:col-span-2 xl:col-span-1">
          <Search className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <span className="sr-only">搜索岗位</span>
          <input className="min-w-0 flex-1 bg-transparent text-sm outline-none" value={draftFilters.keyword} onChange={event => setDraftFilters({ ...draftFilters, keyword: event.target.value })} placeholder="搜索岗位名称、编码或技能" />
        </label>
        <ResponsiveSelect ariaLabel="岗位状态" value={draftFilters.status} onValueChange={status => setDraftFilters({ ...draftFilters, status })} options={statusOptions} />
        <FilterInput label="工作城市" value={draftFilters.city} onChange={city => setDraftFilters({ ...draftFilters, city })} placeholder="例如 北京" />
        <FilterInput label="所属部门" value={draftFilters.department} onChange={department => setDraftFilters({ ...draftFilters, department })} placeholder="例如 研发部" />
        <div className="flex gap-2 md:col-span-2 xl:col-span-1 xl:justify-end">
          <Button type="submit" className="flex-1 xl:flex-none">筛选</Button>
          <Button type="button" variant="secondary" className="px-4" onClick={resetFilters} aria-label="重置筛选"><SlidersHorizontal className="h-4 w-4" /><span className="hidden sm:inline">重置</span></Button>
        </div>
      </form>
    </Card>

    {error && <Card className="border-rose-200 bg-rose-50/80 p-4 text-sm text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/30" role="alert">{error}<Button type="button" variant="secondary" className="ml-3 h-9 px-3" onClick={() => void load()}>重试</Button></Card>}

    {loading && !items ? <LoadingState /> : !items?.records.length ? <EmptyState hasFilters={Boolean(filters.keyword || filters.status || filters.city || filters.department)} onCreate={() => navigate('/company/positions/new')} /> : <>
      <Card className="hidden overflow-hidden p-0 md:block">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[880px] text-left text-sm">
            <thead className="bg-muted/55 text-xs text-muted-foreground"><tr><th className="px-5 py-4">岗位</th><th className="px-5 py-4">地点 / 薪资</th><th className="px-5 py-4">状态</th><th className="px-5 py-4">更新时间</th><th className="px-5 py-4 text-right">操作</th></tr></thead>
            <tbody className="divide-y divide-border">{items.records.map(job => <tr key={job.id} className="transition hover:bg-muted/30">
              <td className="px-5 py-4"><p className="font-bold">{job.name}</p><p className="mt-1 text-xs text-muted-foreground">{job.positionCode} · {job.department || '未设置部门'}</p></td>
              <td className="px-5 py-4 text-muted-foreground"><span className="inline-flex items-center gap-1"><MapPin className="h-3.5 w-3.5" />{job.city || '地点面议'}</span><span className="mx-2 text-border">·</span><span className="font-semibold text-foreground">{salaryLabel(job)}</span></td>
              <td className="px-5 py-4"><div className="flex flex-wrap gap-2"><StatusBadge status={job.recruitmentStatus} /><ApprovalBadge job={job} /></div></td>
              <td className="px-5 py-4 text-muted-foreground">{formatDateTime(job.updatedAt)}</td>
              <td className="px-5 py-4 text-right"><Button type="button" variant="secondary" className="h-9 px-3" onClick={() => navigate(`/company/positions/${job.id}`)}>查看岗位</Button></td>
            </tr>)}</tbody>
          </table>
        </div>
      </Card>
      <div className="grid gap-3 md:hidden">{items.records.map(job => <Card key={job.id} className="p-4">
        <div className="flex items-start justify-between gap-3"><span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><BriefcaseBusiness className="h-5 w-5" /></span><div className="flex flex-wrap justify-end gap-2"><StatusBadge status={job.recruitmentStatus} /><ApprovalBadge job={job} /></div></div>
        <h2 className="mt-4 text-lg font-black">{job.name}</h2><p className="mt-1 text-xs text-muted-foreground">{job.positionCode} · {job.department || '未设置部门'}</p>
        <p className="mt-4 text-sm text-muted-foreground"><MapPin className="mr-1 inline h-4 w-4" />{job.city || '地点面议'}<span className="mx-2 text-border">·</span><strong className="text-foreground">{salaryLabel(job)}</strong></p>
        <Button type="button" variant="secondary" className="mt-4 w-full" onClick={() => navigate(`/company/positions/${job.id}`)}>查看岗位详情</Button>
      </Card>)}</div>
      <Pagination pageNo={pageNo} totalPages={totalPages} onChange={setPageNo} />
    </>}
  </div>
}

function FilterInput({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (value: string) => void; placeholder: string }) {
  return <label className="flex h-12 min-w-0 items-center rounded-full border border-border bg-background px-4 focus-within:border-[var(--accent)] focus-within:ring-2 focus-within:ring-[var(--accent)]/20"><span className="sr-only">{label}</span><input className="min-w-0 flex-1 bg-transparent text-sm outline-none" value={value} onChange={event => onChange(event.target.value)} placeholder={placeholder} /></label>
}

function StatusBadge({ status }: { status: RecruitmentJob['recruitmentStatus'] }) {
  const meta = positionStatusMeta[status] || positionStatusMeta.DRAFT
  return <Badge tone={meta.tone}>{meta.label}</Badge>
}

function ApprovalBadge({ job }: { job: RecruitmentJob }) {
  if (job.frozen) return <Badge tone="danger">招聘冻结</Badge>
  const meta = approvalStatusMeta[job.approvalStatus] || approvalStatusMeta.DRAFT
  return <Badge tone={meta.tone}>{meta.label}</Badge>
}

function Pagination({ pageNo, totalPages, onChange }: { pageNo: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  return <nav aria-label="岗位分页" className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5"><p className="text-sm text-muted-foreground">第 {pageNo} / {totalPages} 页</p><div className="flex items-center gap-2"><Button type="button" variant="secondary" className="h-10 px-3" disabled={pageNo <= 1} onClick={() => onChange(pageNo - 1)}><ChevronLeft className="h-4 w-4" />上一页</Button><Button type="button" variant="secondary" className="h-10 px-3" disabled={pageNo >= totalPages} onClick={() => onChange(pageNo + 1)}>下一页<ChevronRight className="h-4 w-4" /></Button></div></nav>
}

function LoadingState() { return <Card className="grid min-h-64 place-items-center"><div className="text-center"><div className="mx-auto h-6 w-6 animate-spin rounded-full border-2 border-[var(--accent)] border-t-transparent" /><p className="mt-3 text-sm text-muted-foreground">正在加载岗位…</p></div></Card> }

function EmptyState({ hasFilters, onCreate }: { hasFilters: boolean; onCreate: () => void }) {
  return <Card className="grid min-h-64 place-items-center text-center"><div><BriefcaseBusiness className="mx-auto h-8 w-8 text-muted-foreground" /><h2 className="mt-3 font-bold">{hasFilters ? '没有符合条件的岗位' : '还没有岗位'}</h2><p className="mt-1 text-sm text-muted-foreground">{hasFilters ? '尝试调整筛选条件。' : '新建第一个企业招聘岗位。'}</p>{!hasFilters && <Button className="mt-5" onClick={onCreate}><Plus className="h-4 w-4" />新建岗位</Button>}</div></Card>
}
