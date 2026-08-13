import { Check, Loader2, Plus, ShieldCheck, UserRound, UsersRound, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import { formatDateTime } from '@/lib/recruitment'
import { profile } from '@/lib/session'
import type { CompanyTeamMember } from '@/lib/company'

const roleMeta = {
  COMPANY_ADMIN: { label: '企业管理员', tone: 'success' as const, description: '企业资料、招聘全流程与成员权限。', permissions: '全部企业权限' },
  COMPANY_RECRUITER: { label: '招聘专员', tone: 'info' as const, description: '岗位、申请、面试和报告处理。', permissions: '岗位、申请、面试、报告、数据' },
  COMPANY_INTERVIEWER: { label: '面试官', tone: 'warning' as const, description: '只查看被授权候选人/面试并提交评价。', permissions: '授权面试、候选人读取、面试评价' },
}
const roleCodes = Object.keys(roleMeta) as (keyof typeof roleMeta)[]
type Draft = { username: string; password: string; realName: string; email: string; phone: string; roleCodes: string[] }
const blank: Draft = { username: '', password: '', realName: '', email: '', phone: '', roleCodes: ['COMPANY_RECRUITER'] }

function errorMessage(reason: unknown) { return reason instanceof Error && reason.message ? reason.message : '团队数据暂时不可用，请稍后重试。' }

export function CompanyTeam() {
  const isAdmin = profile()?.roles.includes('COMPANY_ADMIN') ?? false
  const [members, setMembers] = useState<CompanyTeamMember[]>([])
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState('')
  const [error, setError] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [draft, setDraft] = useState<Draft>(blank)
  const [roleDrafts, setRoleDrafts] = useState<Record<string, string>>({})

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const value = await request<CompanyTeamMember[]>('/v1/company/team')
      setMembers(value)
      setRoleDrafts(Object.fromEntries(value.map(member => [member.id, member.roles[0] ?? 'COMPANY_RECRUITER'])))
    } catch (reason) { setError(errorMessage(reason)) } finally { setLoading(false) }
  }, [])
  useEffect(() => { void load() }, [load])

  const adminCount = useMemo(() => members.filter(member => member.status === 1 && member.roles.includes('COMPANY_ADMIN')).length, [members])
  const updateDraft = (key: keyof Draft, value: string) => setDraft(previous => ({ ...previous, [key]: key === 'roleCodes' ? [value] : value }))
  const create = async (event: React.FormEvent) => {
    event.preventDefault(); setBusyId('create'); setError('')
    try { await request('/v1/company/team', { method: 'POST', body: JSON.stringify(draft) }); setDraft(blank); setShowCreate(false); await load() } catch (reason) { setError(errorMessage(reason)) } finally { setBusyId('') }
  }
  const saveRole = async (member: CompanyTeamMember) => {
    const roleCode = roleDrafts[member.id] ?? member.roles[0]
    if (!roleCode || roleCode === member.roles[0]) return
    setBusyId(`role-${member.id}`); setError('')
    try { await request(`/v1/company/team/${member.id}/roles`, { method: 'PUT', body: JSON.stringify({ roleCodes: [roleCode] }) }); await load() } catch (reason) { setError(errorMessage(reason)) } finally { setBusyId('') }
  }
  const toggleStatus = async (member: CompanyTeamMember) => {
    setBusyId(`status-${member.id}`); setError('')
    try { await request(`/v1/company/team/${member.id}/status`, { method: 'PUT', body: JSON.stringify({ status: member.status === 1 ? 0 : 1 }) }); await load() } catch (reason) { setError(errorMessage(reason)) } finally { setBusyId('') }
  }

  return <div className="space-y-6">
    <header className="flex flex-col gap-4 border-b border-border pb-6 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-xs font-bold uppercase tracking-[.14em] text-[var(--accent)]">Organization / Team</p><h1 className="mt-2 text-3xl font-black tracking-[-.05em]">团队成员</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">让每个人只看到自己该处理的招聘工作。成员管理始终由服务端校验企业边界和团队权限。</p></div>{isAdmin && <Button onClick={() => setShowCreate(value => !value)}>{showCreate ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}{showCreate ? '收起表单' : '创建成员'}</Button>}</header>
    {error && <div role="alert" className="rounded-2xl border border-[var(--danger)]/40 bg-[var(--danger)]/10 px-4 py-3 text-sm">{error}<button type="button" className="ml-3 font-semibold text-[var(--accent)] hover:underline" onClick={() => void load()}>重试</button></div>}

    {showCreate && isAdmin && <Card className="p-5 sm:p-7"><div className="flex items-start gap-3"><span className="grid h-10 w-10 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><UserRound className="h-5 w-5" /></span><div><h2 className="text-lg font-bold">创建企业成员</h2><p className="mt-1 text-sm text-muted-foreground">成员自动绑定当前企业，不能通过表单指定其他 companyId。</p></div></div><form className="mt-6 grid gap-4 md:grid-cols-2" onSubmit={create}><Field label="登录名" value={draft.username} onChange={value => updateDraft('username', value)} required /><Field label="初始密码" value={draft.password} onChange={value => updateDraft('password', value)} required type="password" /><Field label="姓名" value={draft.realName} onChange={value => updateDraft('realName', value)} required /><Field label="手机号" value={draft.phone} onChange={value => updateDraft('phone', value)} required /><Field label="邮箱" value={draft.email} onChange={value => updateDraft('email', value)} type="email" /><label className="block text-sm font-semibold">初始角色<select className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 text-sm font-normal outline-none focus:border-[var(--accent)]" value={draft.roleCodes[0]} onChange={event => updateDraft('roleCodes', event.target.value)}>{roleCodes.map(code => <option key={code} value={code}>{roleMeta[code].label}</option>)}</select></label><div className="flex justify-end gap-2 md:col-span-2"><Button type="button" variant="ghost" onClick={() => setShowCreate(false)}>取消</Button><Button type="submit" disabled={busyId === 'create'}>{busyId === 'create' && <Loader2 className="h-4 w-4 animate-spin" />}创建成员</Button></div></form></Card>}

    <Card className="p-5 sm:p-7"><div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"><div className="flex items-start gap-3"><span className="grid h-10 w-10 place-items-center rounded-xl bg-[var(--info)] text-foreground"><UsersRound className="h-5 w-5" /></span><div><h2 className="text-lg font-bold">成员列表</h2><p className="mt-1 text-sm text-muted-foreground">{members.length} 位成员 · {adminCount} 位启用中的企业管理员</p></div></div><Badge tone={adminCount > 0 ? 'success' : 'danger'}>{adminCount > 0 ? '管理员保护正常' : '需要管理员'}</Badge></div>
      {loading ? <div className="flex min-h-40 items-center justify-center text-sm text-muted-foreground"><Loader2 className="mr-2 h-5 w-5 animate-spin" />正在读取团队…</div> : members.length === 0 ? <div className="flex min-h-40 items-center justify-center text-sm text-muted-foreground">当前企业还没有可展示的成员。</div> : <div className="mt-6 space-y-3">{members.map(member => <MemberRow key={member.id} member={member} isAdmin={isAdmin} role={roleDrafts[member.id] ?? member.roles[0]} onRoleChange={value => setRoleDrafts(previous => ({ ...previous, [member.id]: value }))} onRoleSave={() => void saveRole(member)} onToggle={() => void toggleStatus(member)} busy={busyId.includes(member.id)} />)}</div>}
    </Card>

    <Card className="p-5 sm:p-7"><div className="flex items-start gap-3"><ShieldCheck className="mt-0.5 h-5 w-5 text-[var(--accent)]" /><div><h2 className="text-lg font-bold">角色与权限说明</h2><p className="mt-1 text-sm text-muted-foreground">权限由后端角色映射决定，页面上的说明只是帮助理解，不是安全边界。</p></div></div><div className="mt-5 grid gap-3 md:grid-cols-3">{roleCodes.map(code => <div key={code} className="rounded-2xl border border-border bg-background p-4"><div className="flex items-center justify-between gap-2"><p className="font-bold">{roleMeta[code].label}</p><Badge tone={roleMeta[code].tone}>{code.replace('COMPANY_', '')}</Badge></div><p className="mt-2 text-sm leading-6 text-muted-foreground">{roleMeta[code].description}</p><p className="mt-3 text-xs font-semibold text-[var(--accent)]">{roleMeta[code].permissions}</p></div>)}</div><p className="mt-5 text-xs leading-5 text-muted-foreground">最后管理员保护：停用或移除最后一位启用中的企业管理员会被服务端拒绝；跨企业成员也会以资源不存在响应返回。</p></Card>
  </div>
}

function MemberRow({ member, isAdmin, role, onRoleChange, onRoleSave, onToggle, busy }: { member: CompanyTeamMember; isAdmin: boolean; role?: string; onRoleChange: (value: string) => void; onRoleSave: () => void; onToggle: () => void; busy: boolean }) {
  const meta = roleMeta[(member.roles[0] ?? 'COMPANY_RECRUITER') as keyof typeof roleMeta] ?? roleMeta.COMPANY_RECRUITER
  return <div className="flex flex-col gap-4 rounded-2xl border border-border bg-background p-4 lg:flex-row lg:items-center lg:justify-between"><div className="flex min-w-0 items-start gap-3"><div className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-[var(--muted)] text-sm font-black">{member.realName?.slice(0, 1) || '成'}</div><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><p className="break-words font-bold">{member.realName || member.username}</p><Badge tone={member.status === 1 ? 'success' : 'default'}>{member.status === 1 ? '启用' : '停用'}</Badge></div><p className="mt-1 truncate text-sm text-muted-foreground">@{member.username} · {member.email || member.phone || '暂无联系方式'}</p><p className="mt-1 text-xs text-muted-foreground">创建于 {formatDateTime(member.createdAt)}</p></div></div><div className="flex flex-col gap-2 sm:flex-row sm:items-center"><Badge tone={meta.tone}>{meta.label}</Badge>{isAdmin && <><select aria-label={`${member.realName} 的角色`} className="h-10 rounded-xl border border-border bg-surface px-3 text-sm" value={role} onChange={event => onRoleChange(event.target.value)}>{roleCodes.map(code => <option key={code} value={code}>{roleMeta[code].label}</option>)}</select><Button variant="secondary" className="h-10 px-4 text-xs" onClick={onRoleSave} disabled={busy || role === member.roles[0]}>{busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Check className="h-3.5 w-3.5" />}保存角色</Button><Button variant="ghost" className="h-10 px-3 text-xs" onClick={onToggle} disabled={busy}>{member.status === 1 ? '停用' : '启用'}</Button></>}</div></div>
}

function Field({ label, value, onChange, required, type = 'text' }: { label: string; value: string; onChange: (value: string) => void; required?: boolean; type?: string }) { return <label className="block text-sm font-semibold">{label}{required && <span className="ml-1 text-[var(--danger)]">*</span>}<input type={type} required={required} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 text-sm font-normal outline-none focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20" value={value} onChange={event => onChange(event.target.value)} /></label> }
