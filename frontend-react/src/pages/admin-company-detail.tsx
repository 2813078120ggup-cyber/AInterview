import { ArrowLeft, Building2, Check, Edit3, Loader2, Mail, Phone, Power, ShieldCheck, UserPlus, UsersRound, X } from 'lucide-react'
import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import type { AdminCompany, AdminCompanyDetail, AdminCompanyMember } from '@/lib/admin'

type ProfileForm = {
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
type MemberForm = { username: string; password: string; realName: string; email: string; phone: string; roleCodes: string[] }

const roleOptions = [
  { code: 'COMPANY_ADMIN', label: '企业管理员', description: '管理企业资料、成员与全部招聘能力' },
  { code: 'COMPANY_RECRUITER', label: '招聘专员', description: '管理岗位、申请、面试和报告' },
  { code: 'COMPANY_INTERVIEWER', label: '面试官', description: '读取被授权候选人与面试并提交评价' },
]
const inputClass = 'mt-2 h-11 w-full rounded-2xl border border-border bg-background px-3.5 text-sm font-normal outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/15'
const emptyMember: MemberForm = { username: '', password: '', realName: '', email: '', phone: '', roleCodes: ['COMPANY_RECRUITER'] }

function statusLabel(status: number) { return status === 1 ? '已启用' : '已停用' }
function formatDate(value?: string | null) { return value ? value.replace('T', ' ').slice(0, 16) : '—' }
function profileFrom(company: AdminCompany): ProfileForm {
  return {
    name: company.name, shortName: company.shortName || '', industry: company.industry || '', companySize: company.companySize || '',
    city: company.city || '', description: company.description || '', websiteUrl: company.websiteUrl || '',
    recruitmentContactName: company.recruitmentContactName || '', recruitmentContactEmail: company.recruitmentContactEmail || '',
    recruitmentContactPhone: company.recruitmentContactPhone || '',
  }
}
function roleLabel(code: string) { return roleOptions.find(item => item.code === code)?.label || code }

export function AdminCompanyDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [detail, setDetail] = useState<AdminCompanyDetail | null>(null)
  const [members, setMembers] = useState<AdminCompanyMember[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editOpen, setEditOpen] = useState(false)
  const [memberOpen, setMemberOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [statusBusy, setStatusBusy] = useState(false)
  const [form, setForm] = useState<ProfileForm | null>(null)
  const [memberForm, setMemberForm] = useState<MemberForm>(emptyMember)

  const load = useCallback(async () => {
    if (!id) return
    setLoading(true)
    try {
      const [nextDetail, nextMembers] = await Promise.all([
        request<AdminCompanyDetail>(`/v1/admin/companies/${id}`),
        request<AdminCompanyMember[]>(`/v1/admin/companies/${id}/members`),
      ])
      setDetail(nextDetail)
      setMembers(nextMembers)
      setForm(profileFrom(nextDetail.company))
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '企业详情加载失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => { void load() }, [load])

  async function updateProfile() {
    if (!id || !form || !form.name.trim()) { setError('企业名称不能为空。'); return }
    setSaving(true)
    try {
      const next = await request<AdminCompanyDetail>(`/v1/admin/companies/${id}`, { method: 'PUT', body: JSON.stringify(form) })
      setDetail(next)
      setForm(profileFrom(next.company))
      setEditOpen(false)
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '企业资料保存失败，请稍后重试。')
    } finally {
      setSaving(false)
    }
  }

  async function toggleStatus() {
    if (!id || !detail || statusBusy) return
    const nextStatus = detail.company.status === 1 ? 0 : 1
    setStatusBusy(true)
    try {
      const next = await request<AdminCompanyDetail>(`/v1/admin/companies/${id}/status`, { method: 'PUT', body: JSON.stringify({ status: nextStatus, confirm: false }) })
      setDetail(next)
      setError('')
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : '企业状态更新失败，请稍后重试。'
      if (nextStatus === 0 && message.includes('停用前') && window.confirm(`${message}\n\n确认继续停用吗？进行中面试和历史申请不会被删除。`)) {
        try {
          const next = await request<AdminCompanyDetail>(`/v1/admin/companies/${id}/status`, { method: 'PUT', body: JSON.stringify({ status: 0, confirm: true }) })
          setDetail(next)
          setError('企业已停用，历史招聘数据保留。')
        } catch (retryReason) {
          setError(retryReason instanceof Error ? retryReason.message : '企业停用失败，请稍后重试。')
        }
      } else {
        setError(message)
      }
    } finally {
      setStatusBusy(false)
    }
  }

  function toggleRole(code: string) {
    setMemberForm(current => ({ ...current, roleCodes: current.roleCodes.includes(code) ? current.roleCodes.filter(item => item !== code) : [...current.roleCodes, code] }))
  }

  async function createMember() {
    if (!id || !memberForm.username.trim() || !memberForm.password || !memberForm.realName.trim() || !memberForm.phone.trim()) {
      setError('请填写成员账号、初始密码、姓名和手机号。')
      return
    }
    if (!memberForm.roleCodes.length) { setError('至少选择一个企业角色。'); return }
    setSaving(true)
    try {
      await request(`/v1/admin/companies/${id}/members`, { method: 'POST', body: JSON.stringify(memberForm) })
      setMemberOpen(false)
      setMemberForm(emptyMember)
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '企业成员创建失败，请稍后重试。')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <Card className="flex items-center justify-center gap-2 p-16 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" />正在加载企业治理信息…</Card>
  if (!detail) return <div className="space-y-5"><Button type="button" variant="secondary" onClick={() => navigate('/admin/companies')}><ArrowLeft className="h-4 w-4" />返回企业列表</Button><Card className="p-10 text-center text-muted-foreground">{error || '企业不存在。'}</Card></div>

  const { company, overview } = detail
  return <div className="space-y-6">
    <div><Link to="/admin/companies" className="inline-flex min-h-10 items-center gap-2 rounded-full px-1 text-sm font-semibold text-muted-foreground transition hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]"><ArrowLeft className="h-4 w-4" />返回企业台账</Link></div>
    <header className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
      <div className="flex min-w-0 items-start gap-4"><span className="grid h-14 w-14 shrink-0 place-items-center rounded-[22px] bg-[var(--accent-soft)] text-[var(--accent)]"><Building2 className="h-7 w-7" /></span><div className="min-w-0"><p className="text-sm font-semibold text-[var(--accent)]">租户治理 · {company.companyCode}</p><div className="mt-2 flex flex-wrap items-center gap-3"><h1 className="break-words text-3xl font-bold tracking-tight sm:text-4xl">{company.name}</h1><Badge tone={company.status === 1 ? 'success' : 'default'}>{statusLabel(company.status)}</Badge></div><p className="mt-3 max-w-3xl text-muted-foreground">{company.industry || '未填写行业'} · {company.city || '未填写城市'} · {company.companySize || '未填写规模'}</p></div></div>
      <div className="grid grid-cols-2 gap-2 sm:flex"><Button type="button" variant="secondary" className="h-10" onClick={() => { setForm(profileFrom(company)); setEditOpen(true) }}><Edit3 className="h-4 w-4" />编辑资料</Button><Button type="button" variant={company.status === 1 ? 'danger' : 'primary'} className="h-10" onClick={() => void toggleStatus()} disabled={statusBusy}>{statusBusy ? <Loader2 className="h-4 w-4 animate-spin" /> : company.status === 1 ? <Power className="h-4 w-4" /> : <Check className="h-4 w-4" />}{company.status === 1 ? '停用企业' : '启用企业'}</Button></div>
    </header>

    {error && <div className="flex items-start justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm leading-6 text-rose-700 dark:border-rose-900/60 dark:bg-rose-950/30 dark:text-rose-200"><span>{error}</span><Button type="button" variant="ghost" className="h-8 w-8 shrink-0 rounded-full px-0" onClick={() => setError('')} aria-label="关闭提示"><X className="h-4 w-4" /></Button></div>}

    <section aria-labelledby="company-overview-title"><div className="mb-3 flex items-end justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">企业负荷</p><h2 id="company-overview-title" className="mt-1 text-xl font-bold">招聘概览</h2></div><span className="text-xs text-muted-foreground">数据库聚合 · 实时读取</span></div><div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4"><Card className="p-4"><p className="text-sm text-muted-foreground">招聘中岗位</p><strong className="mt-2 block text-3xl tabular-nums">{overview.recruitingPositionCount}</strong><p className="mt-1 text-xs text-muted-foreground">停用检查会关注这些岗位</p></Card><Card className="p-4"><p className="text-sm text-muted-foreground">历史申请</p><strong className="mt-2 block text-3xl tabular-nums">{overview.applicationCount}</strong><p className="mt-1 text-xs text-muted-foreground">停用后继续保留</p></Card><Card className="p-4"><p className="text-sm text-muted-foreground">企业成员</p><strong className="mt-2 block text-3xl tabular-nums">{overview.memberCount}</strong><p className="mt-1 text-xs text-muted-foreground">包括已停用成员</p></Card><Card className="border-[var(--warning)]/60 bg-[var(--warning)]/30 p-4"><p className="text-sm text-[var(--warning-foreground)]">进行中面试</p><strong className="mt-2 block text-3xl tabular-nums text-[var(--warning-foreground)]">{overview.inProgressInterviewCount}</strong><p className="mt-1 text-xs text-[var(--warning-foreground)]">停用前需要显式确认</p></Card></div></section>

    <div className="grid gap-5 xl:grid-cols-[minmax(0,1.2fr)_minmax(320px,.8fr)]">
      <Card className="p-5 sm:p-6"><div className="flex items-start justify-between gap-4"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">企业资料</p><h2 className="mt-1 text-xl font-bold">基本信息</h2></div><span className="text-xs text-muted-foreground">更新于 {formatDate(company.updatedAt)}</span></div><dl className="mt-6 grid gap-x-6 gap-y-5 sm:grid-cols-2"><Info label="企业简称" value={company.shortName} /><Info label="官网" value={company.websiteUrl} link={company.websiteUrl} /><Info label="招聘联系人" value={company.recruitmentContactName} /><Info label="联系人邮箱" value={company.recruitmentContactEmail} icon={<Mail className="h-3.5 w-3.5" />} /><Info label="联系人手机" value={company.recruitmentContactPhone} icon={<Phone className="h-3.5 w-3.5" />} /><Info label="创建时间" value={formatDate(company.createdAt)} /></dl><div className="mt-6 rounded-2xl bg-muted/50 p-4"><p className="text-xs font-semibold text-muted-foreground">企业简介</p><p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-foreground/85">{company.description || '暂无企业简介。'}</p></div></Card>
      <Card className="p-5 sm:p-6"><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">风险提示</p><h2 className="mt-1 text-xl font-bold">停用前检查</h2><div className="mt-5 space-y-3">{overview.recruitingPositionCount || overview.inProgressInterviewCount ? <><div className="flex items-start gap-3 rounded-2xl border border-[var(--warning)]/60 bg-[var(--warning)]/30 p-4"><ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-[var(--warning-foreground)]" /><p className="text-sm leading-6 text-[var(--warning-foreground)]">当前有 {overview.recruitingPositionCount} 个招聘中岗位和 {overview.inProgressInterviewCount} 场进行中面试。停用不会删除历史数据，但需要在确认提示中明确继续。</p></div><p className="text-xs leading-5 text-muted-foreground">停用后企业成员不能登录企业工作区，岗位不会再接受新的有效投递；已有申请、面试和审计记录保留。</p></> : <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/30 dark:text-emerald-200">当前没有招聘中岗位或进行中面试，停用风险较低。</div>}</div></Card>
    </div>

    <Card className="overflow-hidden p-0"><div className="flex flex-col gap-3 border-b border-border p-5 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">成员与权限</p><h2 className="mt-1 text-xl font-bold">企业成员</h2><p className="mt-2 text-sm text-muted-foreground">企业管理员可在企业端继续管理成员；此处用于平台级创建与分配管理员。</p></div><Button type="button" className="h-10 w-full sm:w-auto" onClick={() => { setMemberForm(emptyMember); setMemberOpen(true) }}><UserPlus className="h-4 w-4" />创建成员</Button></div><table className="mobile-card-table text-left text-sm"><thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground"><tr><th className="px-5 py-4">成员</th><th className="px-5 py-4">角色</th><th className="px-5 py-4">联系方式</th><th className="px-5 py-4">最近登录</th><th className="px-5 py-4">状态</th></tr></thead><tbody>{members.map(member => <tr key={member.id} className="border-b border-border/70 last:border-0 hover:bg-muted/30"><td data-label="成员" className="px-5 py-4"><div className="flex items-center gap-3"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-[var(--info)] text-[var(--info-foreground)]"><UsersRound className="h-4 w-4" /></span><span className="min-w-0"><strong className="block truncate">{member.realName}</strong><span className="mt-1 block truncate text-xs text-muted-foreground">{member.username}</span></span></div></td><td data-label="角色" className="px-5 py-4"><div className="flex flex-wrap gap-1.5">{member.roles.map(role => <Badge key={role} tone={role === 'COMPANY_ADMIN' ? 'warning' : 'default'}>{roleLabel(role)}</Badge>)}</div></td><td data-label="联系方式" className="px-5 py-4 text-xs leading-5 text-muted-foreground">{member.email || '未填写邮箱'}<br />{member.phone || '未填写手机'}</td><td data-label="最近登录" className="px-5 py-4 text-muted-foreground">{formatDate(member.lastLoginAt)}</td><td data-label="状态" className="px-5 py-4"><Badge tone={member.status === 1 ? 'success' : 'default'}>{member.status === 1 ? '已启用' : '已停用'}</Badge></td></tr>)}{!members.length && <tr><td data-mobile-full colSpan={5} className="p-12 text-center text-muted-foreground">该企业还没有成员，请先创建企业管理员。</td></tr>}</tbody></table></Card>

    {editOpen && form && <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="edit-company-title"><div className="mx-auto my-4 max-w-3xl rounded-[28px] bg-surface p-5 shadow-2xl sm:my-10 sm:p-7"><div className="flex items-start justify-between gap-4"><div><p className="text-sm font-semibold text-[var(--accent)]">企业资料</p><h2 id="edit-company-title" className="mt-1 text-2xl font-bold">编辑企业资料</h2><p className="mt-2 text-sm text-muted-foreground">企业编码不可修改，避免历史业务引用失效。</p></div><Button type="button" variant="ghost" className="h-10 w-10 shrink-0 rounded-full px-0" onClick={() => setEditOpen(false)} aria-label="关闭编辑企业对话框"><X className="h-5 w-5" /></Button></div><div className="mt-6 grid gap-4 sm:grid-cols-2"><Field label="企业名称" value={form.name} onChange={value => setForm({ ...form, name: value })} /><Field label="简称" value={form.shortName} onChange={value => setForm({ ...form, shortName: value })} /><Field label="行业" value={form.industry} onChange={value => setForm({ ...form, industry: value })} /><Field label="规模" value={form.companySize} onChange={value => setForm({ ...form, companySize: value })} /><Field label="城市" value={form.city} onChange={value => setForm({ ...form, city: value })} /><Field label="官网" value={form.websiteUrl} onChange={value => setForm({ ...form, websiteUrl: value })} /><Field label="招聘联系人" value={form.recruitmentContactName} onChange={value => setForm({ ...form, recruitmentContactName: value })} /><Field label="联系人邮箱" value={form.recruitmentContactEmail} onChange={value => setForm({ ...form, recruitmentContactEmail: value })} /><Field label="联系人手机" value={form.recruitmentContactPhone} onChange={value => setForm({ ...form, recruitmentContactPhone: value })} /><label className="text-sm font-semibold sm:col-span-2">企业简介<textarea value={form.description} onChange={event => setForm({ ...form, description: event.target.value })} className="mt-2 min-h-28 w-full rounded-2xl border border-border bg-background px-3.5 py-3 text-sm font-normal outline-none focus:border-[var(--accent)]" /></label></div><div className="mt-7 grid grid-cols-2 gap-3 sm:flex sm:justify-end"><Button type="button" variant="secondary" onClick={() => setEditOpen(false)} disabled={saving}>取消</Button><Button type="button" onClick={() => void updateProfile()} disabled={saving}>{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}保存资料</Button></div></div></div>}
    {memberOpen && <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="create-member-title"><div className="mx-auto my-4 max-w-2xl rounded-[28px] bg-surface p-5 shadow-2xl sm:my-10 sm:p-7"><div className="flex items-start justify-between gap-4"><div><p className="text-sm font-semibold text-[var(--accent)]">成员入口</p><h2 id="create-member-title" className="mt-1 text-2xl font-bold">创建企业成员</h2><p className="mt-2 text-sm text-muted-foreground">密码只在创建请求中提交，之后不会回显。</p></div><Button type="button" variant="ghost" className="h-10 w-10 shrink-0 rounded-full px-0" onClick={() => setMemberOpen(false)} aria-label="关闭创建成员对话框"><X className="h-5 w-5" /></Button></div><div className="mt-6 grid gap-4 sm:grid-cols-2"><Field label="姓名" value={memberForm.realName} onChange={value => setMemberForm({ ...memberForm, realName: value })} /><Field label="账号" value={memberForm.username} onChange={value => setMemberForm({ ...memberForm, username: value })} /><Field label="初始密码" value={memberForm.password} type="password" onChange={value => setMemberForm({ ...memberForm, password: value })} /><Field label="手机号" value={memberForm.phone} onChange={value => setMemberForm({ ...memberForm, phone: value })} /><Field label="邮箱" value={memberForm.email} onChange={value => setMemberForm({ ...memberForm, email: value })} /><div className="sm:col-span-2"><p className="text-sm font-semibold">企业角色</p><div className="mt-2 grid gap-2 sm:grid-cols-3">{roleOptions.map(role => { const checked = memberForm.roleCodes.includes(role.code); return <button type="button" key={role.code} onClick={() => toggleRole(role.code)} className={`rounded-2xl border p-3 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] ${checked ? 'border-[var(--accent)] bg-[var(--accent-soft)]' : 'border-border bg-background hover:bg-muted'}`} aria-pressed={checked}><span className="flex items-center justify-between gap-2 text-sm font-semibold">{role.label}{checked && <Check className="h-4 w-4 text-[var(--accent)]" />}</span><span className="mt-1 block text-xs leading-5 text-muted-foreground">{role.description}</span></button> })}</div></div></div><div className="mt-7 grid grid-cols-2 gap-3 sm:flex sm:justify-end"><Button type="button" variant="secondary" onClick={() => setMemberOpen(false)} disabled={saving}>取消</Button><Button type="button" onClick={() => void createMember()} disabled={saving}>{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <UserPlus className="h-4 w-4" />}创建成员</Button></div></div></div>}
  </div>
}

function Info({ label, value, link, icon }: { label: string; value?: string | null; link?: string | null; icon?: ReactNode }) {
  return <div><dt className="text-xs font-semibold text-muted-foreground">{label}</dt><dd className="mt-1 flex min-w-0 items-center gap-1.5 break-words text-sm">{icon}{link ? <a href={link} target="_blank" rel="noreferrer" className="break-all text-[var(--accent)] underline-offset-2 hover:underline">{value}</a> : value || '未填写'}</dd></div>
}

function Field({ label, value, onChange, type = 'text' }: { label: string; value: string; onChange: (value: string) => void; type?: string }) {
  return <label className="text-sm font-semibold">{label}<input type={type} value={value} onChange={event => onChange(event.target.value)} className={inputClass} /></label>
}
