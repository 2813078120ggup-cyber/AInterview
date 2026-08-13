import { Check, KeyRound, Loader2, LockKeyhole, Save, ShieldCheck, UsersRound, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import type { AdminPermission, AdminRole } from '@/lib/admin'

function errorMessage(reason: unknown, fallback: string) { return reason instanceof Error ? reason.message : fallback }

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
      const [nextRoles, nextPermissions] = await Promise.all([request<AdminRole[]>('/v1/roles'), request<AdminPermission[]>('/v1/roles/permissions')])
      setRoles(nextRoles)
      setPermissions(nextPermissions)
      setSelectedId(current => current && nextRoles.some(role => role.id === current) ? current : nextRoles[0]?.id || '')
      setError('')
    } catch (reason) { setError(errorMessage(reason, '角色权限加载失败，请稍后重试。')) }
    finally { setLoading(false) }
  }, [])

  useEffect(() => { void load() }, [load])
  const selectedRole = roles.find(role => role.id === selectedId)
  useEffect(() => { setSelectedPermissionIds(selectedRole?.permissionIds ?? []) }, [selectedRole])

  const groupedPermissions = useMemo(() => {
    const groups = new Map<string, AdminPermission[]>()
    permissions.forEach(permission => groups.set(permission.resourceType || '其他', [...(groups.get(permission.resourceType || '其他') ?? []), permission]))
    return Array.from(groups.entries()).sort(([left], [right]) => left.localeCompare(right))
  }, [permissions])

  function togglePermission(permissionId: string) {
    setSelectedPermissionIds(current => current.includes(permissionId) ? current.filter(item => item !== permissionId) : [...current, permissionId])
  }

  async function savePermissions() {
    if (!selectedRole) return
    if (selectedRole.affectedUserCount > 0 && !window.confirm(`此角色当前影响 ${selectedRole.affectedUserCount} 个用户，保存后权限会立即影响这些账号。确认继续吗？`)) return
    setSaving(true)
    try {
      const next = await request<AdminRole>(`/v1/roles/${selectedRole.id}/permissions`, {
        method: 'PUT',
        body: JSON.stringify({ permissionIds: selectedPermissionIds, version: selectedRole.version, confirmImpact: true }),
      })
      setRoles(current => current.map(role => role.id === next.id ? next : role))
      setError('')
    } catch (reason) { setError(errorMessage(reason, '权限保存失败，可能是版本已过期，请刷新后重试。')) }
    finally { setSaving(false) }
  }

  if (loading) return <Card className="flex items-center justify-center gap-2 p-16 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" />正在加载角色权限</Card>

  return <div className="space-y-6">
    <header className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between"><div><p className="text-sm font-semibold text-[var(--accent)]">平台 · 授权模型</p><h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">角色权限</h1><p className="mt-3 max-w-3xl text-muted-foreground">系统角色只读保护；权限变更由服务端版本校验，并显示当前受影响用户数。</p></div><div className="flex items-center gap-2 text-xs text-muted-foreground"><KeyRound className="h-4 w-4 text-[var(--accent)]" />{roles.length} 个角色 · {permissions.length} 项权限</div></header>
    {error && <div className="flex items-start justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-200"><span>{error}</span><Button type="button" variant="ghost" className="h-8 w-8 shrink-0 rounded-full px-0" onClick={() => setError('')} aria-label="关闭提示"><X className="h-4 w-4" /></Button></div>}
    <div className="grid gap-5 xl:grid-cols-[minmax(250px,.32fr)_minmax(0,1fr)]"><Card className="p-3 sm:p-4"><div className="flex items-center gap-2 px-2 pb-3"><ShieldCheck className="h-4 w-4 text-[var(--accent)]" /><h2 className="font-bold">角色列表</h2></div><div className="space-y-2">{roles.map(role => <button type="button" key={role.id} onClick={() => setSelectedId(role.id)} className={`w-full rounded-2xl border p-4 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] ${role.id === selectedId ? 'border-[var(--accent)] bg-[var(--accent-soft)]' : 'border-border bg-background hover:bg-muted'}`}><div className="flex items-start justify-between gap-3"><span className="min-w-0"><strong className="block break-words text-sm">{role.roleName}</strong><span className="mt-1 block break-all text-xs text-muted-foreground">{role.roleCode}</span></span>{role.protectedRole && <LockKeyhole className="h-4 w-4 shrink-0 text-muted-foreground" />}</div><div className="mt-3 flex flex-wrap items-center gap-2"><Badge tone={role.protectedRole ? 'warning' : 'default'}>{role.protectedRole ? '系统角色 · 受保护' : '自定义角色'}</Badge><span className="inline-flex items-center gap-1 text-xs text-muted-foreground"><UsersRound className="h-3.5 w-3.5" />{role.affectedUserCount} 个用户</span></div></button>)}{!roles.length && <p className="p-6 text-center text-sm text-muted-foreground">暂无角色。</p>}</div></Card>
      <Card className="overflow-hidden p-0"><div className="flex flex-col gap-4 border-b border-border p-5 sm:flex-row sm:items-start sm:justify-between sm:p-7"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">权限矩阵</p><div className="mt-1 flex flex-wrap items-center gap-2"><h2 className="text-xl font-bold">{selectedRole?.roleName || '选择角色'}</h2>{selectedRole?.protectedRole && <Badge tone="warning">受保护</Badge>}</div><p className="mt-2 text-sm text-muted-foreground">{selectedRole?.description || '选择一个角色查看其权限。'}</p></div>{selectedRole && <Button type="button" onClick={() => void savePermissions()} disabled={saving}><Save className="h-4 w-4" />{saving ? '保存中' : '保存权限'}</Button>}</div>{selectedRole && <div className="border-b border-border bg-muted/35 px-5 py-3 text-sm text-muted-foreground sm:px-7"><span className="font-semibold text-foreground">影响提示：</span>当前角色绑定 {selectedRole.affectedUserCount} 个用户，点击保存前会再次确认。版本号 v{selectedRole.version}</div>}<div className="space-y-5 p-5 sm:p-7">{groupedPermissions.map(([resourceType, items]) => <section key={resourceType} aria-labelledby={`permission-group-${resourceType}`}><div className="mb-3 flex items-center gap-2"><span className="grid h-8 w-8 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><KeyRound className="h-4 w-4" /></span><h3 id={`permission-group-${resourceType}`} className="font-bold">{resourceType}</h3><span className="text-xs text-muted-foreground">{items.length} 项</span></div><div className="grid gap-2 md:grid-cols-2">{items.map(permission => { const active = selectedPermissionIds.includes(permission.id); return <label key={permission.id} className={`flex min-h-16 cursor-pointer items-start gap-3 rounded-2xl border p-3 transition ${active ? 'border-[var(--accent)] bg-[var(--accent-soft)]' : 'border-border bg-background hover:bg-muted'}`}><input type="checkbox" checked={active} onChange={() => togglePermission(permission.id)} className="mt-1 h-4 w-4 accent-[var(--accent)]" /><span className="min-w-0"><span className="flex flex-wrap items-center gap-2 text-sm font-semibold">{permission.permissionName}{active && <Check className="h-3.5 w-3.5 text-[var(--accent)]" />}</span><span className="mt-1 block break-all text-xs text-muted-foreground">{permission.permissionCode}</span>{permission.description && <span className="mt-1 block text-xs leading-5 text-muted-foreground">{permission.description}</span>}</span></label> })}</div></section>)}{!groupedPermissions.length && <div className="py-12 text-center text-sm text-muted-foreground">暂无权限定义。</div>}</div></Card></div>
  </div>
}
