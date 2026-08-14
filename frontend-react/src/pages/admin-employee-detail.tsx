import { AlertTriangle, ArrowLeft, Eye, KeyRound, Loader2, Power, Save, ShieldCheck, UserCog, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { RoleAssignmentPicker } from '@/components/admin/role-assignment-picker'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import type { AdminPermission, AdminRole, AdminUser } from '@/lib/admin'
import { evaluateRoleSelection, roleAssignmentDomain, sameRoleSelection } from '@/lib/role-assignment'

function formatDate(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '暂无'
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error ? reason.message : fallback
}

export function AdminEmployeeDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [employee, setEmployee] = useState<AdminUser | null>(null)
  const [roles, setRoles] = useState<AdminRole[]>([])
  const [permissions, setPermissions] = useState<AdminPermission[]>([])
  const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const [nextEmployee, nextRoles, nextPermissions] = await Promise.all([
        request<AdminUser>(`/v1/admin/employees/${id}`),
        request<AdminRole[]>('/v1/roles'),
        request<AdminPermission[]>('/v1/roles/permissions'),
      ])
      setEmployee(nextEmployee)
      setRoles(nextRoles)
      setPermissions(nextPermissions)
      const manageableRoleIds = new Set(
        nextRoles
          .filter((role) => role.status === 1 && roleAssignmentDomain(role.roleCode) === 'platform')
          .map((role) => role.id),
      )
      setSelectedRoleIds(nextEmployee.roleIds.filter((roleId) => manageableRoleIds.has(roleId)))
      setError('')
    } catch (reason) {
      setEmployee(null)
      setError(errorMessage(reason, '员工详情加载失败，请稍后重试。'))
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => { void load() }, [load])

  const platformRoles = useMemo(
    () => roles.filter((role) => role.status === 1 && roleAssignmentDomain(role.roleCode) === 'platform'),
    [roles],
  )
  const selectedRoles = useMemo(
    () => roles.filter((role) => selectedRoleIds.includes(role.id)),
    [roles, selectedRoleIds],
  )
  const roleSelection = useMemo(
    () => evaluateRoleSelection(platformRoles, selectedRoleIds, null),
    [platformRoles, selectedRoleIds],
  )
  const rolesChanged = employee ? !sameRoleSelection(selectedRoleIds, employee.roleIds) : false
  const effectivePermissions = useMemo(() => {
    const ids = new Set(selectedRoles.flatMap((role) => role.permissionIds))
    return permissions.filter((permission) => ids.has(permission.id))
  }, [permissions, selectedRoles])
  const groupedPermissions = useMemo(() => {
    const groups = new Map<string, AdminPermission[]>()
    effectivePermissions.forEach((permission) => {
      const key = permission.resourceType || '其他'
      groups.set(key, [...(groups.get(key) ?? []), permission])
    })
    return Array.from(groups.entries()).sort(([left], [right]) => left.localeCompare(right))
  }, [effectivePermissions])

  function toggleRole(roleId: string) {
    setSelectedRoleIds((current) => current.includes(roleId) ? current.filter((item) => item !== roleId) : [...current, roleId])
  }

  async function saveRoles() {
    if (!id || !roleSelection.valid) {
      setError(roleSelection.message)
      return
    }
    setSaving(true)
    try {
      const next = await request<AdminUser>(`/v1/users/${id}/roles`, {
        method: 'PUT',
        body: JSON.stringify({ roleIds: selectedRoleIds }),
      })
      setEmployee(next)
      setSelectedRoleIds(next.roleIds)
      setError('')
    } catch (reason) {
      setError(errorMessage(reason, '员工职责保存失败，请稍后重试。'))
    } finally {
      setSaving(false)
    }
  }

  async function toggleStatus() {
    if (!id || !employee) return
    const nextStatus = employee.status === 1 ? 0 : 1
    if (nextStatus === 0 && employee.roles.includes('ADMIN') && !window.confirm('停用管理员会影响其平台访问，确认继续吗？')) return
    setBusy(true)
    try {
      await request(`/v1/users/${id}/status`, { method: 'PUT', body: JSON.stringify({ status: nextStatus }) })
      await load()
    } catch (reason) {
      setError(errorMessage(reason, '员工状态更新失败，请稍后重试。'))
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Card className="flex items-center justify-center gap-2 p-16 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" />正在加载员工详情</Card>

  if (!employee) return <div className="space-y-5">
    <Button type="button" variant="secondary" onClick={() => navigate('/admin/employees')}><ArrowLeft className="h-4 w-4" />返回员工列表</Button>
    <Card className="p-12 text-center"><UserCog className="mx-auto h-9 w-9 text-muted-foreground" /><h1 className="mt-4 text-xl font-bold">未找到平台员工</h1><p className="mt-2 text-sm text-muted-foreground">{error || '该账号不属于平台员工身份域。'}</p></Card>
  </div>

  return <div className="space-y-6">
    <Link to="/admin/employees" className="inline-flex min-h-10 items-center gap-2 rounded-full px-1 text-sm font-semibold text-muted-foreground transition hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]"><ArrowLeft className="h-4 w-4" />返回员工列表</Link>

    <header className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
      <div className="flex min-w-0 items-start gap-4">
        <span className="grid h-16 w-16 shrink-0 place-items-center overflow-hidden rounded-[22px] bg-[var(--accent-soft)] text-[var(--accent)]">{employee.avatarUrl ? <img src={employee.avatarUrl} alt="" className="h-full w-full object-cover" /> : <UserCog className="h-7 w-7" />}</span>
        <div className="min-w-0">
          <p className="text-sm font-semibold text-[var(--accent)]">员工档案 · 平台职责</p>
          <div className="mt-2 flex flex-wrap items-center gap-3"><h1 className="break-words text-3xl font-bold tracking-tight sm:text-4xl">{employee.realName || employee.username}</h1><Badge tone={employee.status === 1 ? 'success' : 'default'}>{employee.status === 1 ? '在岗可用' : '账号停用'}</Badge></div>
          <p className="mt-3 break-all text-sm text-muted-foreground">@{employee.username} · {employee.email || employee.phone || '暂无联系方式'}</p>
        </div>
      </div>
      <div className="flex flex-wrap gap-3">
        <Button type="button" variant="secondary" onClick={() => navigate(`/admin/users/${employee.id}`)}><Eye className="h-4 w-4" />查看账号全景</Button>
        <Button type="button" variant={employee.status === 1 ? 'danger' : 'primary'} onClick={() => void toggleStatus()} disabled={busy}>{busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Power className="h-4 w-4" />}{employee.status === 1 ? '停用员工账号' : '启用员工账号'}</Button>
      </div>
    </header>

    {error && <div className="flex items-start justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-200"><span>{error}</span><Button type="button" variant="ghost" className="h-8 w-8 shrink-0 rounded-full px-0" onClick={() => setError('')} aria-label="关闭提示"><X className="h-4 w-4" /></Button></div>}

    <section className="grid gap-4 sm:grid-cols-3" aria-label="员工职责摘要">
      <Summary label="当前职责" value={`${selectedRoles.length} 个`} detail={selectedRoles.map((role) => role.roleName).join('、') || '尚未分配'} />
      <Summary label="生效权限" value={`${effectivePermissions.length} 项`} detail="按当前角色合并去重" />
      <Summary label="最近登录" value={employee.lastLoginAt ? formatDate(employee.lastLoginAt).slice(5) : '暂无'} detail={employee.status === 1 ? '账号当前可登录' : '账号当前已停用'} />
    </section>

    <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(320px,.72fr)]">
      <Card className="p-5 sm:p-7">
        <p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">基础档案</p>
        <h2 className="mt-1 text-xl font-bold">账号与联系信息</h2>
        <dl className="mt-6 grid gap-x-6 gap-y-5 sm:grid-cols-2"><Info label="姓名" value={employee.realName} /><Info label="账号" value={`@${employee.username}`} /><Info label="邮箱" value={employee.email} /><Info label="手机号" value={employee.phone} /><Info label="最近登录" value={formatDate(employee.lastLoginAt)} /><Info label="加入平台" value={formatDate(employee.createdAt)} /></dl>
      </Card>
      <Card className="p-5 sm:p-7">
        <div className="flex items-start gap-3"><ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-[var(--accent)]" /><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">身份边界</p><h2 className="mt-1 text-xl font-bold">平台员工</h2><p className="mt-3 text-sm leading-6 text-muted-foreground">该账号不绑定企业，只能组合平台角色。候选人和企业角色不会出现在此处；角色变更后，原有登录会话会立即失效。</p></div></div>
        <div className="mt-5 flex flex-wrap gap-2">{selectedRoles.map((role) => <Badge key={role.id} tone={role.roleCode === 'ADMIN' ? 'warning' : 'info'}>{role.roleName}</Badge>)}</div>
      </Card>
    </div>

    <Card className="p-5 sm:p-7">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">岗位职责</p><div className="mt-1 flex flex-wrap items-center gap-2"><h2 className="text-xl font-bold">分配平台角色</h2><Badge tone={roleSelection.valid ? 'info' : 'danger'}>{roleSelection.label}</Badge></div><p className="mt-2 text-sm text-muted-foreground">可组合管理员、人力资源、面试官及自定义平台角色；至少保留一个平台职责。</p></div>
        <Button type="button" onClick={() => void saveRoles()} disabled={saving || !rolesChanged || !roleSelection.valid}>{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}{saving ? '保存中' : '保存职责'}</Button>
      </div>
      {!roleSelection.valid && <div className="mt-5 flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-100" role="alert"><AlertTriangle className="mt-1 h-4 w-4 shrink-0" /><span><strong className="font-semibold">当前组合不能保存：</strong>{roleSelection.message}</span></div>}
      <div className="mt-6"><RoleAssignmentPicker roles={platformRoles} selectedRoleIds={selectedRoleIds} companyId={null} onToggle={toggleRole} /></div>
    </Card>

    <Card className="overflow-hidden p-0">
      <div className="border-b border-border p-5 sm:p-7"><div className="flex items-start gap-3"><KeyRound className="mt-0.5 h-5 w-5 shrink-0 text-[var(--accent)]" /><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">权限视图</p><h2 className="mt-1 text-xl font-bold">当前生效权限</h2><p className="mt-2 text-sm text-muted-foreground">由已选角色合并得出，仅用于确认影响范围；权限内容请在“角色与权限”中维护。</p></div></div></div>
      <div className="grid gap-5 p-5 md:grid-cols-2 sm:p-7">{groupedPermissions.map(([resourceType, items]) => <section key={resourceType} className="rounded-2xl border border-border p-4" aria-labelledby={`employee-permission-${resourceType}`}><div className="flex items-center justify-between gap-3"><h3 id={`employee-permission-${resourceType}`} className="font-bold">{resourceType}</h3><Badge>{items.length} 项</Badge></div><ul className="mt-4 space-y-3">{items.map((permission) => <li key={permission.id} className="flex items-start gap-3"><span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--accent)]" /><span className="min-w-0"><strong className="block text-sm">{permission.permissionName}</strong><span className="mt-1 block break-all text-xs text-muted-foreground">{permission.permissionCode}</span></span></li>)}</ul></section>)}{!groupedPermissions.length && <div className="py-10 text-center text-sm text-muted-foreground md:col-span-2">当前角色未配置可展示的权限。</div>}</div>
    </Card>
  </div>
}

function Summary({ label, value, detail }: { label: string; value: string; detail: string }) {
  return <Card className="p-5"><p className="text-xs font-semibold text-muted-foreground">{label}</p><p className="mt-2 text-2xl font-bold tracking-tight">{value}</p><p className="mt-2 truncate text-xs text-muted-foreground" title={detail}>{detail}</p></Card>
}

function Info({ label, value }: { label: string; value?: string | null }) {
  return <div><dt className="text-xs font-semibold text-muted-foreground">{label}</dt><dd className="mt-1 break-words text-sm">{value || '未填写'}</dd></div>
}
