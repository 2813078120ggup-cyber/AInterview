import {
  BookOpenCheck,
  BriefcaseBusiness,
  CalendarCheck2,
  Check,
  CircleUserRound,
  KeyRound,
  Loader2,
  LockKeyhole,
  Save,
  ShieldCheck,
  UsersRound,
  X,
} from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type ComponentType } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import type { AdminPermission, AdminRole } from '@/lib/admin'

type CandidateCapability = {
  title: string
  description: string
  examples: string
  icon: ComponentType<{ className?: string }>
}

const candidateCapabilities: CandidateCapability[] = [
  {
    title: '求职与投递',
    description: '浏览公开岗位，维护本人简历并跟进自己的申请进度。',
    examples: '岗位大厅 · 我的简历 · 我的申请',
    icon: BriefcaseBusiness,
  },
  {
    title: '面试与报告',
    description: '进入分配给本人的面试任务、提交作答并查看本人报告。',
    examples: '面试任务 · 面试日历 · 能力报告',
    icon: CalendarCheck2,
  },
  {
    title: '练习与成长',
    description: '使用算法练习、专项训练和学习资料沉淀个人能力。',
    examples: '算法练习 · 专项训练 · 学习资料',
    icon: BookOpenCheck,
  },
  {
    title: '本人账户',
    description: '只管理自己的资料、安全、通知偏好、反馈和面试复盘。',
    examples: '账户设置 · 问题反馈 · 面试复盘',
    icon: CircleUserRound,
  },
]

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error ? reason.message : fallback
}

export function AdminRoles() {
  const [roles, setRoles] = useState<AdminRole[]>([])
  const [permissions, setPermissions] = useState<AdminPermission[]>([])
  const [selectedId, setSelectedId] = useState('')
  const [selectedPermissionIds, setSelectedPermissionIds] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [nextRoles, nextPermissions] = await Promise.all([
        request<AdminRole[]>('/v1/roles'),
        request<AdminPermission[]>('/v1/roles/permissions'),
      ])
      setRoles(nextRoles)
      setPermissions(nextPermissions)
      setSelectedId((current) => current && nextRoles.some((role) => role.id === current) ? current : nextRoles[0]?.id || '')
      setError('')
    } catch (reason) {
      setError(errorMessage(reason, '角色权限加载失败，请稍后重试。'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])
  const selectedRole = roles.find((role) => role.id === selectedId)
  const isCandidate = selectedRole?.roleCode === 'CANDIDATE'

  useEffect(() => {
    setSelectedPermissionIds(selectedRole?.roleCode === 'CANDIDATE' ? [] : selectedRole?.permissionIds ?? [])
  }, [selectedRole])

  const groupedPermissions = useMemo(() => {
    const groups = new Map<string, AdminPermission[]>()
    permissions.forEach((permission) => {
      const key = permission.resourceType || '其他'
      groups.set(key, [...(groups.get(key) ?? []), permission])
    })
    return Array.from(groups.entries()).sort(([left], [right]) => left.localeCompare(right))
  }, [permissions])

  function togglePermission(permissionId: string) {
    setSelectedPermissionIds((current) => current.includes(permissionId)
      ? current.filter((item) => item !== permissionId)
      : [...current, permissionId])
  }

  async function savePermissions() {
    if (!selectedRole || selectedRole.roleCode === 'CANDIDATE') return
    if (selectedRole.affectedUserCount > 0 && !window.confirm(`此角色当前影响 ${selectedRole.affectedUserCount} 个用户，保存后权限会立即影响这些账号。确认继续吗？`)) return
    setSaving(true)
    try {
      const next = await request<AdminRole>(`/v1/roles/${selectedRole.id}/permissions`, {
        method: 'PUT',
        body: JSON.stringify({ permissionIds: selectedPermissionIds, version: selectedRole.version, confirmImpact: true }),
      })
      setRoles((current) => current.map((role) => role.id === next.id ? next : role))
      setError('')
    } catch (reason) {
      setError(errorMessage(reason, '权限保存失败，可能是版本已过期，请刷新后重试。'))
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <Card className="flex items-center justify-center gap-2 p-16 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" />正在加载角色权限</Card>

  return <div className="space-y-6">
    <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
      <div>
        <p className="text-sm font-semibold text-[var(--accent)]">平台 · 授权模型</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">角色权限</h1>
        <p className="mt-3 max-w-3xl text-muted-foreground">平台和企业角色按权限点授权；候选人使用固定身份能力，并始终受本人资源边界约束。</p>
      </div>
      <div className="flex items-center gap-2 text-xs text-muted-foreground"><KeyRound className="h-4 w-4 text-[var(--accent)]" />{roles.length} 个角色 · {permissions.length} 项后台权限</div>
    </header>

    {error && <div className="flex items-start justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-200"><span>{error}</span><Button type="button" variant="ghost" className="h-8 w-8 shrink-0 rounded-full px-0" onClick={() => setError('')} aria-label="关闭提示"><X className="h-4 w-4" /></Button></div>}

    <div className="grid gap-5 xl:grid-cols-[minmax(250px,.32fr)_minmax(0,1fr)]">
      <Card className="p-3 sm:p-4">
        <div className="flex items-center gap-2 px-2 pb-3"><ShieldCheck className="h-4 w-4 text-[var(--accent)]" /><h2 className="font-bold">角色列表</h2></div>
        <div className="space-y-2">
          {roles.map((role) => {
            const candidate = role.roleCode === 'CANDIDATE'
            return <button type="button" key={role.id} onClick={() => setSelectedId(role.id)} className={`w-full rounded-2xl border p-4 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] ${role.id === selectedId ? 'border-[var(--accent)] bg-[var(--accent-soft)]' : 'border-border bg-background hover:bg-muted'}`}>
              <div className="flex items-start justify-between gap-3"><span className="min-w-0"><strong className="block break-words text-sm">{role.roleName}</strong><span className="mt-1 block break-all text-xs text-muted-foreground">{role.roleCode}</span></span>{role.protectedRole && <LockKeyhole className="h-4 w-4 shrink-0 text-muted-foreground" />}</div>
              <div className="mt-3 flex flex-wrap items-center gap-2"><Badge tone="warning">{candidate ? '固定身份 · 无后台权限' : role.protectedRole ? '系统角色 · 受保护' : '自定义角色'}</Badge><span className="inline-flex items-center gap-1 text-xs text-muted-foreground"><UsersRound className="h-3.5 w-3.5" />{role.affectedUserCount} 个用户</span></div>
            </button>
          })}
          {!roles.length && <p className="p-6 text-center text-sm text-muted-foreground">暂无角色。</p>}
        </div>
      </Card>

      {isCandidate && selectedRole
        ? <CandidateBoundary role={selectedRole} />
        : <PermissionMatrix
            role={selectedRole}
            groupedPermissions={groupedPermissions}
            selectedPermissionIds={selectedPermissionIds}
            saving={saving}
            onToggle={togglePermission}
            onSave={() => void savePermissions()}
          />}
    </div>
  </div>
}

function CandidateBoundary({ role }: { role: AdminRole }) {
  return <Card className="overflow-hidden p-0">
    <div className="border-b border-border p-5 sm:p-7">
      <div className="flex items-start gap-4">
        <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><CircleUserRound className="h-5 w-5" /></span>
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">固定身份能力</p>
          <div className="mt-1 flex flex-wrap items-center gap-2"><h2 className="text-xl font-bold">候选人</h2><Badge tone="warning">不参与后台权限矩阵</Badge></div>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">候选人端展示哪些功能由产品路由和候选人身份决定，能访问哪些数据由服务端当前用户与资源归属校验决定，不读取 `company:*`、`admin:*`、`analytics:*` 或 `ai:execute` 等后台权限。</p>
        </div>
      </div>
    </div>

    <div className="border-b border-border bg-muted/35 px-5 py-3 text-sm text-muted-foreground sm:px-7">
      <span className="font-semibold text-foreground">影响范围：</span>{role.affectedUserCount} 个候选人账号 · 固定能力不可在此增删 · 后台权限数 0
    </div>

    <div className="p-5 sm:p-7">
      <div className="flex items-start gap-3 rounded-2xl border border-border bg-background px-4 py-4">
        <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-[var(--accent)]" />
        <div><h3 className="text-sm font-bold">授权原则：身份准入 + 本人数据</h3><p className="mt-1 text-xs leading-5 text-muted-foreground">候选人只能操作自己的简历、申请、面试、报告、训练记录和账户信息；不能因为角色表中出现后台权限而进入企业端或管理端。</p></div>
      </div>

      <section className="mt-6" aria-labelledby="candidate-capabilities-title">
        <div className="flex items-center justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">候选人端能力</p><h3 id="candidate-capabilities-title" className="mt-1 text-lg font-bold">产品内固定开放范围</h3></div><Badge tone="info">只读说明</Badge></div>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          {candidateCapabilities.map((capability) => {
            const Icon = capability.icon
            return <article key={capability.title} className="rounded-2xl border border-border bg-background p-4">
              <div className="flex items-start gap-3"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-muted text-muted-foreground"><Icon className="h-4 w-4" /></span><div className="min-w-0"><h4 className="text-sm font-bold">{capability.title}</h4><p className="mt-1 text-xs leading-5 text-muted-foreground">{capability.description}</p></div></div>
              <p className="mt-3 border-t border-border/70 pt-3 text-xs font-medium text-foreground/75">{capability.examples}</p>
            </article>
          })}
        </div>
      </section>
    </div>
  </Card>
}

type PermissionMatrixProps = {
  role?: AdminRole
  groupedPermissions: Array<[string, AdminPermission[]]>
  selectedPermissionIds: string[]
  saving: boolean
  onToggle: (permissionId: string) => void
  onSave: () => void
}

function PermissionMatrix({ role, groupedPermissions, selectedPermissionIds, saving, onToggle, onSave }: PermissionMatrixProps) {
  return <Card className="overflow-hidden p-0">
    <div className="flex flex-col gap-4 border-b border-border p-5 sm:flex-row sm:items-start sm:justify-between sm:p-7">
      <div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">后台权限矩阵</p><div className="mt-1 flex flex-wrap items-center gap-2"><h2 className="text-xl font-bold">{role?.roleName || '选择角色'}</h2>{role?.protectedRole && <Badge tone="warning">受保护</Badge>}</div><p className="mt-2 text-sm text-muted-foreground">{role?.description || '选择一个平台或企业角色查看其权限。'}</p></div>
      {role && <Button type="button" onClick={onSave} disabled={saving}><Save className="h-4 w-4" />{saving ? '保存中' : '保存权限'}</Button>}
    </div>
    {role && <div className="border-b border-border bg-muted/35 px-5 py-3 text-sm text-muted-foreground sm:px-7"><span className="font-semibold text-foreground">影响提示：</span>当前角色绑定 {role.affectedUserCount} 个用户，点击保存前会再次确认。版本号 v{role.version}</div>}
    <div className="space-y-5 p-5 sm:p-7">
      {groupedPermissions.map(([resourceType, items]) => <section key={resourceType} aria-labelledby={`permission-group-${resourceType}`}><div className="mb-3 flex items-center gap-2"><span className="grid h-8 w-8 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><KeyRound className="h-4 w-4" /></span><h3 id={`permission-group-${resourceType}`} className="font-bold">{resourceType}</h3><span className="text-xs text-muted-foreground">{items.length} 项</span></div><div className="grid gap-2 md:grid-cols-2">{items.map((permission) => { const active = selectedPermissionIds.includes(permission.id); return <label key={permission.id} className={`flex min-h-16 cursor-pointer items-start gap-3 rounded-2xl border p-3 transition ${active ? 'border-[var(--accent)] bg-[var(--accent-soft)]' : 'border-border bg-background hover:bg-muted'}`}><input type="checkbox" checked={active} onChange={() => onToggle(permission.id)} className="mt-1 h-4 w-4 accent-[var(--accent)]" /><span className="min-w-0"><span className="flex flex-wrap items-center gap-2 text-sm font-semibold">{permission.permissionName}{active && <Check className="h-3.5 w-3.5 text-[var(--accent)]" />}</span><span className="mt-1 block break-all text-xs text-muted-foreground">{permission.permissionCode}</span>{permission.description && <span className="mt-1 block text-xs leading-5 text-muted-foreground">{permission.description}</span>}</span></label> })}</div></section>)}
      {!groupedPermissions.length && <div className="py-12 text-center text-sm text-muted-foreground">暂无后台权限定义。</div>}
    </div>
  </Card>
}
