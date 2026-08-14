import { Building2, ChevronLeft, ChevronRight, Loader2, Plus, Search, X } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { AdminRowActionLink } from '@/components/admin/admin-row-actions'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'
import type { AdminCompany } from '@/lib/admin'

type Page<T> = { records: T[]; total: number; pageNo: number; pageSize: number }
type CompanyForm = {
  companyCode: string
  name: string
  shortName: string
  industry: string
  companySize: string
  city: string
  description: string
  websiteUrl: string
  recruitmentContactName: string
  recruitmentContactEmail: string
  recruitmentContactPhone: string
}

const emptyForm: CompanyForm = {
  companyCode: '', name: '', shortName: '', industry: '', companySize: '', city: '', description: '', websiteUrl: '',
  recruitmentContactName: '', recruitmentContactEmail: '', recruitmentContactPhone: '',
}
const inputClass = 'mt-2 h-11 w-full rounded-2xl border border-border bg-background px-3.5 text-sm font-normal outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/15'

function statusLabel(status: number) { return status === 1 ? '已启用' : '已停用' }
function formatDate(value?: string | null) { return value ? value.replace('T', ' ').slice(0, 16) : '—' }

export function AdminCompanies() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const keyword = searchParams.get('keyword') ?? ''
  const status = searchParams.get('status') ?? ''
  const pageNo = Math.max(1, Number(searchParams.get('pageNo') ?? '1') || 1)
  const pageSize = 12
  const [draftKeyword, setDraftKeyword] = useState(keyword)
  const [page, setPage] = useState<Page<AdminCompany>>({ records: [], total: 0, pageNo, pageSize })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<CompanyForm>(emptyForm)

  useEffect(() => setDraftKeyword(keyword), [keyword])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) })
      if (keyword) params.set('keyword', keyword)
      if (status) params.set('status', status)
      const result = await request<Page<AdminCompany>>(`/v1/admin/companies?${params.toString()}`)
      setPage(result)
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '企业列表加载失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }, [keyword, pageNo, status])

  useEffect(() => { void load() }, [load])

  function updateQuery(next: { keyword?: string; status?: string; pageNo?: number }) {
    const nextParams = new URLSearchParams(searchParams)
    if (next.keyword !== undefined) {
      if (next.keyword) nextParams.set('keyword', next.keyword)
      else nextParams.delete('keyword')
    }
    if (next.status !== undefined) {
      if (next.status) nextParams.set('status', next.status)
      else nextParams.delete('status')
    }
    nextParams.set('pageNo', String(next.pageNo ?? 1))
    setSearchParams(nextParams)
  }

  async function create() {
    if (!form.companyCode.trim() || !form.name.trim()) { setError('请填写企业编码和企业名称。'); return }
    setSaving(true)
    try {
      const result = await request<{ company: AdminCompany }>('/v1/admin/companies', {
        method: 'POST', body: JSON.stringify(form),
      })
      setOpen(false)
      setForm(emptyForm)
      navigate(`/admin/companies/${result.company.id}`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '企业创建失败，请稍后重试。')
    } finally {
      setSaving(false)
    }
  }

  const totalPages = Math.max(1, Math.ceil(page.total / pageSize))

  return <div className="space-y-6">
    <header className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
      <div className="min-w-0">
        <p className="text-sm font-semibold text-[var(--accent)]">租户治理</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">企业管理</h1>
        <p className="mt-3 max-w-2xl text-muted-foreground">维护企业资料、招聘负荷和企业成员入口。停用只改变访问状态，历史招聘数据始终保留。</p>
      </div>
      <Button type="button" onClick={() => { setForm(emptyForm); setOpen(true) }}><Plus className="h-4 w-4" />创建企业</Button>
    </header>

    {error && <div className="flex items-start justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-200"><span>{error}</span><Button type="button" variant="ghost" className="h-8 w-8 shrink-0 rounded-full px-0" onClick={() => setError('')} aria-label="关闭提示"><X className="h-4 w-4" /></Button></div>}

    <div className="grid gap-3 sm:grid-cols-3">
      <Card className="p-4"><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">企业总数</p><strong className="mt-2 block text-3xl tabular-nums">{loading ? '…' : page.total}</strong><p className="mt-1 text-xs text-muted-foreground">符合当前筛选条件</p></Card>
      <Card className="p-4"><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">本页在招岗位</p><strong className="mt-2 block text-3xl tabular-nums">{loading ? '…' : page.records.reduce((sum, item) => sum + Number(item.recruitingPositionCount || 0), 0).toLocaleString('zh-CN')}</strong><p className="mt-1 text-xs text-muted-foreground">当前页企业的在招岗位合计</p></Card>
      <Card className="p-4"><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">本页申请</p><strong className="mt-2 block text-3xl tabular-nums">{loading ? '…' : page.records.reduce((sum, item) => sum + Number(item.applicationCount || 0), 0).toLocaleString('zh-CN')}</strong><p className="mt-1 text-xs text-muted-foreground">当前页企业的申请合计，历史记录保留</p></Card>
    </div>

    <Card className="overflow-hidden p-0">
      <div className="grid gap-3 border-b border-border p-4 md:grid-cols-[minmax(0,1fr)_180px_auto] md:items-center md:p-5">
        <label className="flex h-11 min-w-0 items-center gap-2 rounded-full border border-border bg-background px-4"><Search className="h-4 w-4 shrink-0 text-muted-foreground" /><span className="sr-only">搜索企业</span><input value={draftKeyword} onChange={event => setDraftKeyword(event.target.value)} onKeyDown={event => event.key === 'Enter' && updateQuery({ keyword: draftKeyword.trim() })} className="min-w-0 flex-1 bg-transparent text-sm outline-none" placeholder="搜索企业名称、简称或编码" /></label>
        <ResponsiveSelect ariaLabel="企业状态" value={status} onValueChange={value => updateQuery({ status: value })} options={[{ value: '', label: '全部状态' }, { value: '1', label: '已启用' }, { value: '0', label: '已停用' }]} />
        <Button type="button" variant="secondary" className="h-10 w-full px-4 md:w-auto" onClick={() => updateQuery({ keyword: draftKeyword.trim() })} disabled={loading}>搜索</Button>
      </div>
      {loading ? <div className="flex items-center justify-center gap-2 p-16 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" />正在加载企业台账…</div> : <>
        <table className="mobile-card-table text-left text-sm">
          <thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground"><tr><th className="px-5 py-4">企业</th><th className="px-5 py-4">招聘概览</th><th className="px-5 py-4">成员</th><th className="px-5 py-4">状态</th><th className="px-5 py-4">更新时间</th><th className="px-5 py-4 text-right">操作</th></tr></thead>
          <tbody>{page.records.map(company => <tr key={company.id} className="border-b border-border/70 last:border-0 hover:bg-muted/30">
            <td data-label="企业" className="px-5 py-4"><Link to={`/admin/companies/${company.id}`} className="flex min-w-0 items-center gap-3 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]"><span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><Building2 className="h-4 w-4" /></span><span className="min-w-0"><strong className="block truncate">{company.name}</strong><span className="mt-1 block truncate text-xs text-muted-foreground">{company.shortName || company.companyCode} · {company.city || '未填写城市'}</span></span></Link></td>
            <td data-label="招聘概览" className="px-5 py-4"><div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground"><span><strong className="text-foreground">{company.recruitingPositionCount}</strong> 个招聘中岗位</span><span><strong className="text-foreground">{company.applicationCount}</strong> 份申请</span></div></td>
            <td data-label="成员" className="px-5 py-4 text-muted-foreground">{company.memberCount} 人</td>
            <td data-label="状态" className="px-5 py-4"><Badge tone={company.status === 1 ? 'success' : 'default'}>{statusLabel(company.status)}</Badge></td>
            <td data-label="更新时间" className="px-5 py-4 text-muted-foreground">{formatDate(company.updatedAt)}</td>
            <td data-label="操作" className="px-5 py-4 text-right"><AdminRowActionLink to={`/admin/companies/${company.id}`} /></td>
          </tr>)}{!page.records.length && <tr><td data-mobile-full colSpan={6} className="p-14 text-center text-muted-foreground">暂无符合条件的企业。可以创建第一家企业，或清除筛选条件。</td></tr>}</tbody>
        </table>
        <div className="flex flex-col gap-3 border-t border-border px-5 py-4 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between"><span>第 {pageNo} / {totalPages} 页 · 共 {page.total} 家企业</span><div className="flex gap-2"><Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo <= 1} onClick={() => updateQuery({ pageNo: pageNo - 1 })}><ChevronLeft className="h-4 w-4" />上一页</Button><Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo >= totalPages} onClick={() => updateQuery({ pageNo: pageNo + 1 })}>下一页<ChevronRight className="h-4 w-4" /></Button></div></div>
      </>}
    </Card>

    {open && <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="create-company-title"><div className="mx-auto my-4 max-w-3xl rounded-[28px] bg-surface p-5 shadow-2xl sm:my-10 sm:p-7"><div className="flex items-start justify-between gap-4"><div><p className="text-sm font-semibold text-[var(--accent)]">新租户</p><h2 id="create-company-title" className="mt-1 text-2xl font-bold">创建企业</h2><p className="mt-2 text-sm text-muted-foreground">企业创建后默认启用；企业管理员可在详情页分配。</p></div><Button type="button" variant="ghost" className="h-10 w-10 shrink-0 rounded-full px-0" onClick={() => setOpen(false)} aria-label="关闭创建企业对话框"><X className="h-5 w-5" /></Button></div><div className="mt-6 grid gap-4 sm:grid-cols-2">
      <label className="text-sm font-semibold">企业编码<input value={form.companyCode} onChange={event => setForm({ ...form, companyCode: event.target.value })} maxLength={64} className={inputClass} placeholder="例如 ACME_TECH" /></label>
      <label className="text-sm font-semibold">企业名称<input value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} maxLength={160} className={inputClass} placeholder="企业全称" /></label>
      <label className="text-sm font-semibold">简称<input value={form.shortName} onChange={event => setForm({ ...form, shortName: event.target.value })} className={inputClass} placeholder="可选" /></label>
      <label className="text-sm font-semibold">行业<input value={form.industry} onChange={event => setForm({ ...form, industry: event.target.value })} className={inputClass} placeholder="例如 人工智能" /></label>
      <label className="text-sm font-semibold">规模<input value={form.companySize} onChange={event => setForm({ ...form, companySize: event.target.value })} className={inputClass} placeholder="例如 100-499人" /></label>
      <label className="text-sm font-semibold">城市<input value={form.city} onChange={event => setForm({ ...form, city: event.target.value })} className={inputClass} placeholder="例如 北京" /></label>
      <label className="text-sm font-semibold sm:col-span-2">官网<input value={form.websiteUrl} onChange={event => setForm({ ...form, websiteUrl: event.target.value })} className={inputClass} placeholder="https://" /></label>
      <label className="text-sm font-semibold sm:col-span-2">企业简介<textarea value={form.description} onChange={event => setForm({ ...form, description: event.target.value })} className="mt-2 min-h-24 w-full rounded-2xl border border-border bg-background px-3.5 py-3 text-sm font-normal outline-none focus:border-[var(--accent)]" placeholder="用于管理员识别企业和招聘概况" /></label>
      <label className="text-sm font-semibold">招聘联系人<input value={form.recruitmentContactName} onChange={event => setForm({ ...form, recruitmentContactName: event.target.value })} className={inputClass} /></label>
      <label className="text-sm font-semibold">联系人邮箱<input value={form.recruitmentContactEmail} onChange={event => setForm({ ...form, recruitmentContactEmail: event.target.value })} className={inputClass} /></label>
      <label className="text-sm font-semibold">联系人手机<input value={form.recruitmentContactPhone} onChange={event => setForm({ ...form, recruitmentContactPhone: event.target.value })} className={inputClass} /></label>
    </div><div className="mt-7 grid grid-cols-2 gap-3 sm:flex sm:justify-end"><Button type="button" variant="secondary" className="w-full sm:w-auto" onClick={() => setOpen(false)} disabled={saving}>取消</Button><Button type="button" className="w-full sm:w-auto" onClick={() => void create()} disabled={saving}>{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}创建企业</Button></div></div></div>}
  </div>
}
