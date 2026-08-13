import { Building2, ChevronLeft, ChevronRight, Eye, Loader2, Plus, Search, ShieldCheck, UserRound, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'
import type { AdminCompany, AdminRole, AdminUser } from '@/lib/admin'

type Page<T> = { records: T[]; total: number; pageNo: number; pageSize: number }
type UserForm = { username: string; password: string; realName: string; email: string; phone: string; companyId: string; roleIds: string[] }
const emptyForm: UserForm = { username: '', password: '', realName: '', email: '', phone: '', companyId: '', roleIds: [] }
const inputClass = 'mt-2 h-11 w-full rounded-2xl border border-border bg-background px-3.5 text-sm font-normal outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/15'
const companyRoleCodes = new Set(['COMPANY_ADMIN', 'COMPANY_RECRUITER', 'COMPANY_INTERVIEWER'])

function formatDate(value?: string | null) { return value ? value.replace('T', ' ').slice(0, 16) : '暂无' }
function statusLabel(status: number) { return status === 1 ? '启用' : '停用' }
function errorMessage(reason: unknown, fallback: string) { return reason instanceof Error ? reason.message : fallback }

export function AdminUsers() {
  const [searchParams, setSearchParams] = useSearchParams()
  const keyword = searchParams.get('keyword') ?? ''
  const roleCode = searchParams.get('roleCode') ?? ''
  const companyId = searchParams.get('companyId') ?? ''
  const status = searchParams.get('status') ?? ''
  const createdFrom = searchParams.get('createdFrom') ?? ''
  const createdTo = searchParams.get('createdTo') ?? ''
  const pageNo = Math.max(1, Number(searchParams.get('pageNo') ?? '1') || 1)
  const pageSize = 20
  const [draftKeyword, setDraftKeyword] = useState(keyword)
  const [page, setPage] = useState<Page<AdminUser>>({ records: [], total: 0, pageNo, pageSize })
  const [roles, setRoles] = useState<AdminRole[]>([])
  const [companies, setCompanies] = useState<AdminCompany[]>([])
  const [loading, setLoading] = useState(true)
  const [metaLoading, setMetaLoading] = useState(true)
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [busyId, setBusyId] = useState('')
  const [form, setForm] = useState<UserForm>(emptyForm)

  useEffect(() => setDraftKeyword(keyword), [keyword])

  const loadMeta = useCallback(async () => {
    setMetaLoading(true)
    try {
      const [roleRows, companyPage] = await Promise.all([
        request<AdminRole[]>('/v1/roles'),
        request<Page<AdminCompany>>('/v1/admin/companies?pageNo=1&pageSize=100'),
      ])
      setRoles(roleRows)
      setCompanies(companyPage.records)
    } catch (reason) {
      setError(errorMessage(reason, '筛选选项加载失败，请稍后重试。'))
    } finally { setMetaLoading(false) }
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) })
      if (keyword) params.set('keyword', keyword)
      if (roleCode) params.set('roleCode', roleCode)
      if (companyId) params.set('companyId', companyId)
      if (status) params.set('status', status)
      if (createdFrom) params.set('createdFrom', createdFrom)
      if (createdTo) params.set('createdTo', createdTo)
      setPage(await request<Page<AdminUser>>(`/v1/users?${params.toString()}`))
      setError('')
    } catch (reason) { setError(errorMessage(reason, '用户列表加载失败，请稍后重试。')) }
    finally { setLoading(false) }
  }, [companyId, createdFrom, createdTo, keyword, pageNo, roleCode, status])

  useEffect(() => { void loadMeta() }, [loadMeta])
  useEffect(() => { void load() }, [load])

  const roleOptions = useMemo(() => roles.map(role => ({ value: role.roleCode, label: `${role.roleName} · ${role.roleCode}` })), [roles])
  const companyOptions = useMemo(() => companies.map(company => ({ value: company.id, label: company.name })), [companies])
  const formRoles = useMemo(() => roles.filter(role => role.status === 1), [roles])

  function updateQuery(next: Record<string, string | number | undefined>) {
    const nextParams = new URLSearchParams(searchParams)
    Object.entries(next).forEach(([key, value]) => {
      if (value === undefined || value === '') nextParams.delete(key)
      else nextParams.set(key, String(value))
    })
    if (!('pageNo' in next)) nextParams.set('pageNo', '1')
    setSearchParams(nextParams)
  }

  function toggleRole(roleId: string) {
    setForm(current => ({ ...current, roleIds: current.roleIds.includes(roleId) ? current.roleIds.filter(item => item !== roleId) : [...current.roleIds, roleId] }))
  }

  async function createUser() {
    if (!form.username.trim() || !form.password || !form.realName.trim() || !form.phone.trim() || !form.roleIds.length) {
      setError('请填写账号、初始密码、姓名、手机号并至少选择一个角色。')
      return
    }
    const selectedRoles = roles.filter(role => form.roleIds.includes(role.id))
    const hasCompanyRole = selectedRoles.some(role => companyRoleCodes.has(role.roleCode))
    if (hasCompanyRole !== Boolean(form.companyId)) {
      setError('企业角色必须绑定企业；平台角色不能绑定企业。')
      return
    }
    setSaving(true)
    try {
      await request('/v1/users', { method: 'POST', body: JSON.stringify({ ...form, companyId: form.companyId || null }) })
      setOpen(false)
      setForm(emptyForm)
      await load()
    } catch (reason) { setError(errorMessage(reason, '用户创建失败，请稍后重试。')) }
    finally { setSaving(false) }
  }

  async function toggleStatus(user: AdminUser) {
    const nextStatus = user.status === 1 ? 0 : 1
    if (nextStatus === 0 && user.roles.includes('ADMIN') && !window.confirm('停用超级管理员会影响其平台访问，确认继续吗？')) return
    setBusyId(user.id)
    try { await request(`/v1/users/${user.id}/status`, { method: 'PUT', body: JSON.stringify({ status: nextStatus }) }); await load() }
    catch (reason) { setError(errorMessage(reason, '用户状态更新失败，请稍后重试。')) }
    finally { setBusyId('') }
  }

  const totalPages = Math.max(1, Math.ceil(page.total / pageSize))
  return <div className="space-y-6">
    <header className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
      <div className="min-w-0"><p className="text-sm font-semibold text-[var(--accent)]">平台 · 权限边界</p><h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">用户管理</h1><p className="mt-3 max-w-3xl text-muted-foreground">按服务端条件查找平台账号和企业成员。候选人业务档案仍在“候选人档案”中独立维护。</p></div>
      <Button type="button" onClick={() => { setForm(emptyForm); setOpen(true) }}><Plus className="h-4 w-4" />创建用户</Button>
    </header>
    {error && <div className="flex items-start justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-200"><span>{error}</span><Button type="button" variant="ghost" className="h-8 w-8 shrink-0 rounded-full px-0" onClick={() => setError('')} aria-label="关闭提示"><X className="h-4 w-4" /></Button></div>}
    <Card className="p-4 sm:p-5"><div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_180px_180px_140px]">
      <label className="flex h-11 min-w-0 items-center gap-2 rounded-full border border-border bg-background px-4"><Search className="h-4 w-4 shrink-0 text-muted-foreground" /><span className="sr-only">搜索用户</span><input value={draftKeyword} onChange={event => setDraftKeyword(event.target.value)} onKeyDown={event => event.key === 'Enter' && updateQuery({ keyword: draftKeyword.trim() })} className="min-w-0 flex-1 bg-transparent text-sm outline-none" placeholder="账号、姓名或邮箱" /></label>
      <ResponsiveSelect ariaLabel="角色" value={roleCode} onValueChange={value => updateQuery({ roleCode: value })} options={[{ value: '', label: '全部角色' }, ...roleOptions]} disabled={metaLoading} />
      <ResponsiveSelect ariaLabel="企业" value={companyId} onValueChange={value => updateQuery({ companyId: value })} options={[{ value: '', label: '全部企业' }, ...companyOptions]} disabled={metaLoading} searchable />
      <ResponsiveSelect ariaLabel="状态" value={status} onValueChange={value => updateQuery({ status: value })} options={[{ value: '', label: '全部状态' }, { value: '1', label: '启用' }, { value: '0', label: '停用' }]} />
    </div><div className="mt-3 grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end"><label className="text-xs font-semibold text-muted-foreground">创建时间从<input type="date" value={createdFrom} onChange={event => updateQuery({ createdFrom: event.target.value })} className={inputClass} /></label><label className="text-xs font-semibold text-muted-foreground">创建时间至<input type="date" value={createdTo} onChange={event => updateQuery({ createdTo: event.target.value })} className={inputClass} /></label><Button type="button" variant="secondary" className="h-11" onClick={() => updateQuery({ keyword: draftKeyword.trim() })}>搜索</Button></div></Card>
    <Card className="overflow-hidden p-0"><div className="flex items-center justify-between gap-3 border-b border-border p-5"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">服务端分页</p><h2 className="mt-1 text-xl font-bold">账号与成员</h2></div><span className="text-xs text-muted-foreground">共 {page.total} 个结果</span></div>{loading ? <div className="flex items-center justify-center gap-2 p-16 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" />正在加载用户</div> : <><table className="mobile-card-table text-left text-sm"><thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground"><tr><th className="px-5 py-4">账号与姓名</th><th className="px-5 py-4">角色</th><th className="px-5 py-4">企业</th><th className="px-5 py-4">状态</th><th className="px-5 py-4">最近登录</th><th className="px-5 py-4">创建时间</th><th className="px-5 py-4 text-right">操作</th></tr></thead><tbody>{page.records.map(user => <tr key={user.id} className="border-b border-border/70 last:border-0 hover:bg-muted/30"><td data-label="账号与姓名" className="px-5 py-4"><Link to={`/admin/users/${user.id}`} className="flex min-w-0 items-center gap-3 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]"><span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><UserRound className="h-4 w-4" /></span><span className="min-w-0"><strong className="block break-words">{user.realName || '未填写姓名'}</strong><span className="mt-1 block truncate text-xs text-muted-foreground">@{user.username}</span></span></Link></td><td data-label="角色" className="px-5 py-4"><div className="flex flex-wrap gap-1.5">{user.roles.map(role => <Badge key={role} tone={role === 'ADMIN' ? 'warning' : 'default'}>{role}</Badge>)}</div></td><td data-label="企业" className="px-5 py-4">{user.companyId ? <Link to={`/admin/companies/${user.companyId}`} className="inline-flex items-center gap-1.5 text-[var(--accent)] hover:underline"><Building2 className="h-3.5 w-3.5" />{user.companyName || `企业 ${user.companyId}`}</Link> : <span className="text-muted-foreground">平台账号</span>}</td><td data-label="状态" className="px-5 py-4"><Badge tone={user.status === 1 ? 'success' : 'default'}>{statusLabel(user.status)}</Badge></td><td data-label="最近登录" className="px-5 py-4 text-muted-foreground">{formatDate(user.lastLoginAt)}</td><td data-label="创建时间" className="px-5 py-4 text-muted-foreground">{formatDate(user.createdAt)}</td><td data-label="操作" className="px-5 py-4 text-right"><div className="flex justify-end gap-2"><Button type="button" variant="secondary" className="h-9 px-3 text-xs" onClick={() => window.location.assign(`/admin/users/${user.id}`)}><Eye className="h-3.5 w-3.5" />详情</Button><Button type="button" variant="ghost" className="h-9 px-3 text-xs" disabled={busyId === user.id} onClick={() => void toggleStatus(user)}>{busyId === user.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : user.status === 1 ? '停用' : '启用'}</Button></div></td></tr>)}{!page.records.length && <tr><td data-mobile-full colSpan={7} className="p-14 text-center text-muted-foreground">没有符合条件的用户。</td></tr>}</tbody></table><div className="flex flex-col gap-3 border-t border-border px-5 py-4 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between"><span>第 {pageNo} / {totalPages} 页</span><div className="flex gap-2"><Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo <= 1} onClick={() => updateQuery({ pageNo: pageNo - 1 })}><ChevronLeft className="h-4 w-4" />上一页</Button><Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo >= totalPages} onClick={() => updateQuery({ pageNo: pageNo + 1 })}>下一页<ChevronRight className="h-4 w-4" /></Button></div></div></>}</Card>
    {open && <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="create-user-title"><div className="mx-auto my-4 max-w-3xl rounded-[28px] bg-surface p-5 shadow-2xl sm:my-10 sm:p-7"><div className="flex items-start justify-between gap-4"><div><p className="text-sm font-semibold text-[var(--accent)]">平台用户</p><h2 id="create-user-title" className="mt-1 text-2xl font-bold">创建用户</h2><p className="mt-2 text-sm text-muted-foreground">企业角色必须绑定企业；密码只在本次请求中提交。</p></div><Button type="button" variant="ghost" className="h-10 w-10 shrink-0 rounded-full px-0" onClick={() => setOpen(false)} aria-label="关闭创建用户对话框"><X className="h-5 w-5" /></Button></div><div className="mt-6 grid gap-4 sm:grid-cols-2"><Field label="姓名" value={form.realName} onChange={value => setForm({ ...form, realName: value })} /><Field label="账号" value={form.username} onChange={value => setForm({ ...form, username: value })} /><Field label="初始密码" value={form.password} type="password" onChange={value => setForm({ ...form, password: value })} /><Field label="手机号" value={form.phone} onChange={value => setForm({ ...form, phone: value })} /><Field label="邮箱" value={form.email} type="email" onChange={value => setForm({ ...form, email: value })} /><ResponsiveSelect ariaLabel="绑定企业" value={form.companyId} onValueChange={value => setForm({ ...form, companyId: value })} options={[{ value: '', label: '平台账号（不绑定企业）' }, ...companyOptions]} searchable /><div className="sm:col-span-2"><p className="text-sm font-semibold">角色</p><div className="mt-2 grid gap-2 sm:grid-cols-2">{formRoles.map(role => { const checked = form.roleIds.includes(role.id); return <button type="button" key={role.id} onClick={() => toggleRole(role.id)} className={`flex min-h-12 items-center justify-between gap-3 rounded-2xl border px-4 py-3 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] ${checked ? 'border-[var(--accent)] bg-[var(--accent-soft)]' : 'border-border bg-background hover:bg-muted'}`} aria-pressed={checked}><span><strong className="block text-sm">{role.roleName}</strong><span className="text-xs text-muted-foreground">{role.roleCode}</span></span>{checked && <ShieldCheck className="h-4 w-4 text-[var(--accent)]" />}</button> })}</div></div></div><div className="mt-7 grid grid-cols-2 gap-3 sm:flex sm:justify-end"><Button type="button" variant="secondary" onClick={() => setOpen(false)} disabled={saving}>取消</Button><Button type="button" onClick={() => void createUser()} disabled={saving}>{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}创建用户</Button></div></div></div>}
  </div>
}

function Field({ label, value, onChange, type = 'text' }: { label: string; value: string; onChange: (value: string) => void; type?: string }) {
  return <label className="text-sm font-semibold">{label}<input type={type} value={value} onChange={event => onChange(event.target.value)} className={inputClass} /></label>
}
