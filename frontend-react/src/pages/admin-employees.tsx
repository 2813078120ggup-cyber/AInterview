import { ChevronLeft, ChevronRight, Loader2, Search, ShieldCheck, UserCog, UserPlus, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { AdminAccountRowActions } from '@/components/admin/admin-row-actions'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'
import type { AdminRole, AdminUser } from '@/lib/admin'
import { roleAssignmentDomain } from '@/lib/role-assignment'

type Page<T> = { records: T[]; total: number; pageNo: number; pageSize: number }

function formatDate(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '暂无'
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error ? reason.message : fallback
}

export function AdminEmployees() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const keyword = searchParams.get('keyword') ?? ''
  const roleCode = searchParams.get('roleCode') ?? ''
  const status = searchParams.get('status') ?? ''
  const pageNo = Math.max(1, Number(searchParams.get('pageNo') ?? '1') || 1)
  const pageSize = 20
  const [draftKeyword, setDraftKeyword] = useState(keyword)
  const [page, setPage] = useState<Page<AdminUser>>({ records: [], total: 0, pageNo, pageSize })
  const [roles, setRoles] = useState<AdminRole[]>([])
  const [loading, setLoading] = useState(true)
  const [metaLoading, setMetaLoading] = useState(true)
  const [busyId, setBusyId] = useState('')
  const [error, setError] = useState('')

  useEffect(() => setDraftKeyword(keyword), [keyword])

  useEffect(() => {
    let active = true
    setMetaLoading(true)
    request<AdminRole[]>('/v1/roles')
      .then((rows) => { if (active) setRoles(rows) })
      .catch((reason) => { if (active) setError(errorMessage(reason, '员工角色加载失败，请稍后重试。')) })
      .finally(() => { if (active) setMetaLoading(false) })
    return () => { active = false }
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: String(pageSize) })
      if (keyword) params.set('keyword', keyword)
      if (roleCode) params.set('roleCode', roleCode)
      if (status) params.set('status', status)
      setPage(await request<Page<AdminUser>>(`/v1/admin/employees?${params.toString()}`))
      setError('')
    } catch (reason) {
      setError(errorMessage(reason, '员工列表加载失败，请稍后重试。'))
    } finally {
      setLoading(false)
    }
  }, [keyword, pageNo, roleCode, status])

  useEffect(() => { void load() }, [load])

  const platformRoles = useMemo(
    () => roles.filter((role) => role.status === 1 && roleAssignmentDomain(role.roleCode) === 'platform'),
    [roles],
  )
  const roleOptions = useMemo(
    () => platformRoles.map((role) => ({ value: role.roleCode, label: `${role.roleName} · ${role.roleCode}` })),
    [platformRoles],
  )
  const roleNames = useMemo(() => new Map(roles.map((role) => [role.roleCode, role.roleName])), [roles])
  const totalPages = Math.max(1, Math.ceil(page.total / pageSize))

  function updateQuery(next: Record<string, string | number | undefined>) {
    const params = new URLSearchParams(searchParams)
    Object.entries(next).forEach(([key, value]) => {
      if (value === undefined || value === '') params.delete(key)
      else params.set(key, String(value))
    })
    if (!('pageNo' in next)) params.set('pageNo', '1')
    setSearchParams(params)
  }

  async function toggleStatus(employee: AdminUser) {
    const nextStatus = employee.status === 1 ? 0 : 1
    setBusyId(employee.id)
    try {
      await request(`/v1/users/${employee.id}/status`, { method: 'PUT', body: JSON.stringify({ status: nextStatus }) })
      await load()
    } catch (reason) {
      setError(errorMessage(reason, '员工状态更新失败，请稍后重试。'))
    } finally {
      setBusyId('')
    }
  }

  return <div className="space-y-6">
    <header className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
      <div className="min-w-0">
        <p className="text-sm font-semibold text-[var(--accent)]">用户 · 平台团队</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">员工管理</h1>
        <p className="mt-3 max-w-3xl text-muted-foreground">集中查看平台管理员、人力资源、面试官及自定义平台岗位。候选人和企业成员不会进入该名册。</p>
      </div>
      <Button type="button" variant="secondary" onClick={() => navigate('/admin/users')}>
        <UserPlus className="h-4 w-4" />前往账号创建
      </Button>
    </header>

    {error && <div className="flex items-start justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-200">
      <span>{error}</span>
      <Button type="button" variant="ghost" className="h-8 w-8 shrink-0 rounded-full px-0" onClick={() => setError('')} aria-label="关闭提示"><X className="h-4 w-4" /></Button>
    </div>}

    <Card className="p-4 sm:p-5">
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_220px_150px_auto]">
        <label className="flex h-11 min-w-0 items-center gap-2 rounded-full border border-border bg-background px-4">
          <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
          <span className="sr-only">搜索员工</span>
          <input value={draftKeyword} onChange={(event) => setDraftKeyword(event.target.value)} onKeyDown={(event) => event.key === 'Enter' && updateQuery({ keyword: draftKeyword.trim() })} className="min-w-0 flex-1 bg-transparent text-sm outline-none" placeholder="姓名、账号或邮箱" />
        </label>
        <ResponsiveSelect ariaLabel="员工角色" value={roleCode} onValueChange={(value) => updateQuery({ roleCode: value })} options={[{ value: '', label: '全部平台角色' }, ...roleOptions]} disabled={metaLoading} />
        <ResponsiveSelect ariaLabel="员工状态" value={status} onValueChange={(value) => updateQuery({ status: value })} options={[{ value: '', label: '全部状态' }, { value: '1', label: '已启用' }, { value: '0', label: '已停用' }]} />
        <Button type="button" variant="secondary" className="h-11" onClick={() => updateQuery({ keyword: draftKeyword.trim() })}>搜索</Button>
      </div>
      <div className="mt-4 flex items-start gap-2 rounded-2xl bg-muted/45 px-4 py-3 text-xs leading-5 text-muted-foreground">
        <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-[var(--accent)]" />
        员工身份由服务端按角色域校验；存在候选人或企业角色的冲突账号会被隔离在“用户与账号”中处理。
      </div>
    </Card>

    <Card className="overflow-hidden p-0">
      <div className="flex items-center justify-between gap-3 border-b border-border p-5">
        <div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">平台员工名册</p><h2 className="mt-1 text-xl font-bold">账号与职责</h2></div>
        <span className="text-xs text-muted-foreground">共 {page.total} 名员工</span>
      </div>
      {loading ? <div className="flex items-center justify-center gap-2 p-16 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" />正在加载员工</div> : <>
        <table className="mobile-card-table text-left text-sm">
          <thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground"><tr><th className="px-5 py-4">员工</th><th className="px-5 py-4">平台职责</th><th className="px-5 py-4">状态</th><th className="px-5 py-4">最近登录</th><th className="px-5 py-4">加入时间</th><th className="px-5 py-4 text-right">操作</th></tr></thead>
          <tbody>{page.records.map((employee) => <tr key={employee.id} className="border-b border-border/70 last:border-0 hover:bg-muted/30">
            <td data-label="员工" className="px-5 py-4"><Link to={`/admin/employees/${employee.id}`} className="flex min-w-0 items-center gap-3 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]"><span className="grid h-10 w-10 shrink-0 place-items-center overflow-hidden rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]">{employee.avatarUrl ? <img src={employee.avatarUrl} alt="" className="h-full w-full object-cover" /> : <UserCog className="h-4 w-4" />}</span><span className="min-w-0"><strong className="block break-words">{employee.realName || '未填写姓名'}</strong><span className="mt-1 block truncate text-xs text-muted-foreground">@{employee.username}</span></span></Link></td>
            <td data-label="平台职责" className="px-5 py-4"><div className="flex flex-wrap gap-1.5">{employee.roles.map((role) => <Badge key={role} tone={role === 'ADMIN' ? 'warning' : 'default'}>{roleNames.get(role) || role}</Badge>)}</div></td>
            <td data-label="状态" className="px-5 py-4"><Badge tone={employee.status === 1 ? 'success' : 'default'}>{employee.status === 1 ? '已启用' : '已停用'}</Badge></td>
            <td data-label="最近登录" className="px-5 py-4 text-muted-foreground">{formatDate(employee.lastLoginAt)}</td>
            <td data-label="加入时间" className="px-5 py-4 text-muted-foreground">{formatDate(employee.createdAt)}</td>
            <td data-label="操作" className="px-5 py-4 text-right"><AdminAccountRowActions detailTo={`/admin/employees/${employee.id}`} subjectLabel={`员工“${employee.realName || employee.username}”`} active={employee.status === 1} busy={busyId === employee.id} onToggleStatus={() => void toggleStatus(employee)} disableDescription={employee.roles.includes('ADMIN') ? '该员工包含管理员职责。停用后将无法登录平台，历史业务数据仍会保留。' : undefined} /></td>
          </tr>)}{!page.records.length && <tr><td data-mobile-full colSpan={6} className="p-14 text-center text-muted-foreground">没有符合条件的平台员工。</td></tr>}</tbody>
        </table>
        <div className="flex flex-col gap-3 border-t border-border px-5 py-4 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between"><span>第 {pageNo} / {totalPages} 页</span><div className="flex gap-2"><Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo <= 1} onClick={() => updateQuery({ pageNo: pageNo - 1 })}><ChevronLeft className="h-4 w-4" />上一页</Button><Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo >= totalPages} onClick={() => updateQuery({ pageNo: pageNo + 1 })}>下一页<ChevronRight className="h-4 w-4" /></Button></div></div>
      </>}
    </Card>
  </div>
}
