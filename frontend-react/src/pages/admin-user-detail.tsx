import { AlertTriangle, ArrowLeft, Building2, Loader2, Power, Save, ShieldCheck, UserRound, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { RoleAssignmentPicker } from '@/components/admin/role-assignment-picker'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import type { AdminRole, AdminUser } from '@/lib/admin'
import { evaluateRoleSelection, sameRoleSelection } from '@/lib/role-assignment'

function formatDate(value?: string | null) { return value ? value.replace('T', ' ').slice(0, 16) : '暂无' }
function errorMessage(reason: unknown, fallback: string) { return reason instanceof Error ? reason.message : fallback }

export function AdminUserDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [user, setUser] = useState<AdminUser | null>(null)
  const [roles, setRoles] = useState<AdminRole[]>([])
  const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const [nextUser, nextRoles] = await Promise.all([request<AdminUser>(`/v1/users/${id}`), request<AdminRole[]>('/v1/roles')])
      setUser(nextUser)
      setRoles(nextRoles)
      setSelectedRoleIds(nextUser.roleIds)
      setError('')
    } catch (reason) { setError(errorMessage(reason, '用户详情加载失败，请稍后重试。')) }
    finally { setLoading(false) }
  }, [id])

  useEffect(() => { void load() }, [load])

  const roleSelection = useMemo(
    () => evaluateRoleSelection(roles, selectedRoleIds, user?.companyId),
    [roles, selectedRoleIds, user?.companyId],
  )
  const rolesChanged = user ? !sameRoleSelection(selectedRoleIds, user.roleIds) : false

  function toggleRole(roleId: string) {
    setSelectedRoleIds(current => current.includes(roleId) ? current.filter(item => item !== roleId) : [...current, roleId])
  }

  async function saveRoles() {
    if (!id || !roleSelection.valid) { setError(roleSelection.message); return }
    setSaving(true)
    try {
      const next = await request<AdminUser>(`/v1/users/${id}/roles`, { method: 'PUT', body: JSON.stringify({ roleIds: selectedRoleIds }) })
      setUser(next)
      setSelectedRoleIds(next.roleIds)
      setError('')
    } catch (reason) { setError(errorMessage(reason, '角色保存失败，请稍后重试。')) }
    finally { setSaving(false) }
  }

  async function toggleStatus() {
    if (!id || !user) return
    const nextStatus = user.status === 1 ? 0 : 1
    if (nextStatus === 0 && user.roles.includes('ADMIN') && !window.confirm('确认停用这个超级管理员账号吗？')) return
    setBusy(true)
    try { await request(`/v1/users/${id}/status`, { method: 'PUT', body: JSON.stringify({ status: nextStatus }) }); await load() }
    catch (reason) { setError(errorMessage(reason, '状态更新失败，请稍后重试。')) }
    finally { setBusy(false) }
  }

  if (loading) return <Card className="flex items-center justify-center gap-2 p-16 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" />正在加载用户详情</Card>
  if (!user) return <div className="space-y-5"><Button type="button" variant="secondary" onClick={() => navigate('/admin/users')}><ArrowLeft className="h-4 w-4" />返回用户列表</Button><Card className="p-12 text-center text-muted-foreground">{error || '用户不存在。'}</Card></div>

  return <div className="space-y-6">
    <Link to="/admin/users" className="inline-flex min-h-10 items-center gap-2 rounded-full px-1 text-sm font-semibold text-muted-foreground transition hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]"><ArrowLeft className="h-4 w-4" />返回用户列表</Link>
    <header className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between"><div className="flex min-w-0 items-start gap-4"><span className="grid h-14 w-14 shrink-0 place-items-center rounded-[22px] bg-[var(--accent-soft)] text-[var(--accent)]"><UserRound className="h-7 w-7" /></span><div className="min-w-0"><p className="text-sm font-semibold text-[var(--accent)]">用户与权限</p><div className="mt-2 flex flex-wrap items-center gap-3"><h1 className="break-words text-3xl font-bold tracking-tight sm:text-4xl">{user.realName || user.username}</h1><Badge tone={user.status === 1 ? 'success' : 'default'}>{user.status === 1 ? '启用' : '停用'}</Badge></div><p className="mt-3 break-all text-sm text-muted-foreground">@{user.username} · {user.email || user.phone || '暂无联系方式'}</p></div></div><Button type="button" variant={user.status === 1 ? 'danger' : 'primary'} onClick={() => void toggleStatus()} disabled={busy}>{busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Power className="h-4 w-4" />}{user.status === 1 ? '停用账号' : '启用账号'}</Button></header>
    {error && <div className="flex items-start justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-200"><span>{error}</span><Button type="button" variant="ghost" className="h-8 w-8 shrink-0 rounded-full px-0" onClick={() => setError('')} aria-label="关闭提示"><X className="h-4 w-4" /></Button></div>}
    <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(320px,.8fr)]"><Card className="p-5 sm:p-7"><div className="flex items-start justify-between gap-4"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">身份信息</p><h2 className="mt-1 text-xl font-bold">账号概览</h2></div><Badge tone={user.roles.includes('ADMIN') ? 'warning' : 'default'}>{user.roles.includes('ADMIN') ? '超级管理员' : '普通账号'}</Badge></div><dl className="mt-6 grid gap-x-6 gap-y-5 sm:grid-cols-2"><Info label="账号" value={`@${user.username}`} /><Info label="姓名" value={user.realName} /><Info label="邮箱" value={user.email} /><Info label="手机号" value={user.phone} /><Info label="最近登录" value={formatDate(user.lastLoginAt)} /><Info label="创建时间" value={formatDate(user.createdAt)} /><div className="sm:col-span-2"><dt className="text-xs font-semibold text-muted-foreground">所属企业</dt><dd className="mt-1">{user.companyId ? <Link to={`/admin/companies/${user.companyId}`} className="inline-flex items-center gap-2 text-sm font-semibold text-[var(--accent)] hover:underline"><Building2 className="h-4 w-4" />{user.companyName || `企业 ${user.companyId}`}</Link> : <span className="text-sm text-muted-foreground">平台账号，不绑定企业</span>}</dd></div></dl></Card>
      <Card className="p-5 sm:p-7"><div className="flex items-start gap-3"><ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-[var(--accent)]" /><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">安全边界</p><h2 className="mt-1 text-xl font-bold">身份域由服务端执行</h2><p className="mt-3 text-sm leading-6 text-muted-foreground">候选人身份独占；企业成员只能使用所属企业角色；平台人员角色可按职责组合。最后一个平台或企业管理员均受保护，角色变化会立即使该用户原登录会话失效。</p></div></div></Card></div>
    <Card className="p-5 sm:p-7">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">角色分配</p>
          <div className="mt-1 flex flex-wrap items-center gap-2"><h2 className="text-xl font-bold">调整角色</h2><Badge tone={roleSelection.valid ? 'info' : 'danger'}>{roleSelection.label}</Badge></div>
          <p className="mt-2 text-sm text-muted-foreground">先确定账号身份域，再在该域内组合职责；已选中的冲突角色始终允许取消。</p>
        </div>
        <Button type="button" onClick={() => void saveRoles()} disabled={saving || !rolesChanged || !roleSelection.valid}>
          {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
          {saving ? '保存中' : '保存角色'}
        </Button>
      </div>
      {!roleSelection.valid && <div className="mt-5 flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-100" role="alert"><AlertTriangle className="mt-1 h-4 w-4 shrink-0" /><span><strong className="font-semibold">当前组合不能保存：</strong>{roleSelection.message}</span></div>}
      <div className="mt-6"><RoleAssignmentPicker roles={roles} selectedRoleIds={selectedRoleIds} companyId={user.companyId} onToggle={toggleRole} /></div>
    </Card>
  </div>
}

function Info({ label, value }: { label: string; value?: string | null }) {
  return <div><dt className="text-xs font-semibold text-muted-foreground">{label}</dt><dd className="mt-1 break-words text-sm">{value || '未填写'}</dd></div>
}
