import { Eye, Loader2, Power, PowerOff, Search, UserPlus, UserRound, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { recordAuditLog } from '@/lib/audit-log'
import { request } from '@/lib/api'
import { profile } from '@/lib/session'

type User = { id: string; username: string; realName: string; email?: string; phone?: string; status: number; roles: string[]; lastLoginAt?: string; createdAt?: string }
type Role = { id: string; roleCode: string; roleName: string }
type Page<T> = { records: T[]; total: number }

const usernamePattern = /^[A-Za-z][A-Za-z0-9_]{3,31}$/
const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d)[!-~]{8,64}$/

export function AdminCandidates() {
  const nav = useNavigate()
  const [items, setItems] = useState<User[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState('')
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [updatingId, setUpdatingId] = useState('')
  const [form, setForm] = useState({ username: '', password: '', realName: '', email: '', phone: '' })

  async function load() {
    setLoading(true)
    try {
      const [page, roleList] = await Promise.all([
        request<Page<User>>(`/v1/users?pageNo=1&pageSize=100&keyword=${encodeURIComponent(keyword)}${status ? `&status=${status}` : ''}`),
        request<Role[]>('/v1/roles'),
      ])
      setItems(page.records.filter(user => user.roles.includes('CANDIDATE')))
      setRoles(roleList)
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '候选人列表加载失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])

  async function toggle(user: User) {
    setUpdatingId(user.id)
    try {
      const nextStatus = user.status === 1 ? 0 : 1
      await request(`/v1/users/${user.id}/status`, { method: 'PUT', body: JSON.stringify({ status: nextStatus }) })
      recordAuditLog({ module: '候选人管理', action: nextStatus === 1 ? '启用候选人' : '停用候选人', operator: profile()?.realName ?? '管理员', target: user.realName, detail: `账号 ${user.username} 状态变更为 ${nextStatus === 1 ? '启用' : '停用'}` })
      setItems(previous => previous.map(item => item.id === user.id ? { ...item, status: nextStatus } : item))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '候选人状态更新失败，请稍后重试。')
    } finally {
      setUpdatingId('')
    }
  }

  async function create() {
    const candidate = roles.find(role => role.roleCode === 'CANDIDATE')
    if (!candidate) { setError('系统未配置候选人角色。'); return }
    if (!form.username || !form.password || !form.realName || !form.phone) { setError('请填写账号、初始密码、姓名和手机号。'); return }
    if (!usernamePattern.test(form.username)) { setError('账号须为 4–32 位，以英文字母开头，仅包含英文字母、数字或下划线。'); return }
    if (!passwordPattern.test(form.password)) { setError('初始密码须为 8–64 位，包含字母和数字，可使用半角符号。'); return }
    if (!/^1\d{10}$/.test(form.phone.trim())) { setError('请输入有效的 11 位手机号。'); return }
    setSaving(true)
    try {
      const user = await request<User>('/v1/users', { method: 'POST', body: JSON.stringify({ ...form, roleIds: [candidate.id] }) })
      recordAuditLog({ module: '候选人管理', action: '创建候选人', operator: profile()?.realName ?? '管理员', target: user.realName, detail: `创建候选人账号 ${user.username}` })
      setItems(previous => [user, ...previous])
      setOpen(false)
      setForm({ username: '', password: '', realName: '', email: '', phone: '' })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '候选人创建失败，请稍后重试。')
    } finally {
      setSaving(false)
    }
  }

  return <div className="mx-auto max-w-7xl p-4 sm:p-6 lg:p-10">
    <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
      <div><p className="text-sm font-semibold text-[var(--accent)]">候选人档案</p><h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">候选人管理</h1><p className="mt-3 max-w-2xl text-muted-foreground">管理候选人账户、状态与面试记录。</p></div>
      <Button onClick={() => setOpen(true)}><UserPlus className="h-4 w-4" />新增候选人</Button>
    </header>
    {error && <p className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}
    <Card className="mt-7 p-0">
      <div className="flex flex-col gap-3 border-b border-border p-5 md:flex-row"><label className="flex h-12 flex-1 items-center gap-2 rounded-full border border-border bg-surface px-4"><Search className="h-4 w-4 text-muted-foreground" /><input value={keyword} onChange={event => setKeyword(event.target.value)} onKeyDown={event => event.key === 'Enter' && void load()} className="w-full bg-transparent text-sm outline-none" placeholder="搜索姓名、账号、邮箱或手机号" /></label><ResponsiveSelect
        ariaLabel="选择状态"
        value={status}
        onValueChange={setStatus}
        className="w-full md:w-36"
        options={[
          { value: "", label: "全部状态" },
          { value: "1", label: "已启用" },
          { value: "0", label: "已停用" },
        ]}
      /><Button type="button" variant="secondary" className="h-9 px-4" onClick={() => void load()}>搜索</Button></div>
      {loading ? <p className="p-12 text-center text-sm text-muted-foreground">正在加载候选人列表…</p> : <table className="mobile-card-table text-left text-sm">
        <thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground"><tr><th className="px-5 py-4">候选人</th><th className="px-5 py-4">手机号</th><th className="px-5 py-4">邮箱</th><th className="px-5 py-4">最近登录</th><th className="px-5 py-4">状态</th><th className="px-5 py-4 text-right">操作</th></tr></thead>
        <tbody>{items.map(user => <tr key={user.id} className="border-b border-border/70 last:border-0 hover:bg-muted/30">
          <td data-label="候选人" className="px-5 py-5"><div className="flex items-center gap-3"><span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><UserRound className="h-4 w-4" /></span><div className="min-w-0 text-left"><strong className="block truncate">{user.realName}</strong><p className="mt-1 truncate text-xs text-muted-foreground">{user.username}</p></div></div></td>
          <td data-label="手机号" className="break-all px-5 py-5 text-muted-foreground">{user.phone || '-'}</td>
          <td data-label="邮箱" className="break-all px-5 py-5 text-muted-foreground">{user.email || '未填写'}</td>
          <td data-label="最近登录" className="px-5 py-5 text-muted-foreground">{user.lastLoginAt?.replace('T', ' ').slice(0, 16) || '从未登录'}</td>
          <td data-label="状态" className="px-5 py-5"><Badge tone={user.status === 1 ? 'success' : 'default'}>{user.status === 1 ? '已启用' : '已停用'}</Badge></td>
          <td data-label="操作" className="px-5 py-5 text-right"><div className="flex justify-end gap-2"><Button type="button" variant="secondary" className="h-9 gap-1 whitespace-nowrap px-3 text-xs" onClick={() => nav(`/admin/candidates/${user.id}`)}><Eye className="h-3.5 w-3.5" />查看</Button><Button type="button" variant="secondary" className="h-9 gap-1 whitespace-nowrap px-3 text-xs shadow-[0_6px_18px_rgba(20,18,17,.04)]" disabled={updatingId === user.id} aria-busy={updatingId === user.id} onClick={() => void toggle(user)}>{updatingId === user.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : user.status === 1 ? <PowerOff className="h-3.5 w-3.5" /> : <Power className="h-3.5 w-3.5" />}{updatingId === user.id ? '处理中…' : user.status === 1 ? '停用' : '启用'}</Button></div></td>
        </tr>)}{!items.length && <tr><td data-mobile-full colSpan={6} className="p-12 text-center text-muted-foreground">暂无候选人记录</td></tr>}</tbody>
      </table>}
    </Card>
    {open && <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="new-candidate-title"><div className="mx-auto my-4 max-w-lg rounded-[24px] bg-surface p-5 shadow-2xl sm:my-12 sm:rounded-[30px] sm:p-7"><div className="flex justify-between"><div><p className="text-sm font-semibold text-[var(--accent)]">新建账户</p><h2 id="new-candidate-title" className="mt-1 text-2xl font-bold">新增候选人</h2></div><Button type="button" variant="ghost" className="h-10 w-10 shrink-0 rounded-full px-0" onClick={() => setOpen(false)} aria-label="关闭新增候选人对话框"><X className="h-5 w-5" /></Button></div><div className="mt-6 grid gap-4 sm:grid-cols-2">{([
      ['realName', '姓名', '刘洋'],
      ['username', '账号', 'candidate_liu'],
      ['password', '初始密码', '8-64 位，字母+数字'],
      ['email', '邮箱', 'name@example.com'],
      ['phone', '手机号', '必填，11 位手机号'],
    ] as const).map(([key, label, placeholder]) => <label key={key} className="text-sm font-semibold">{label}<input type={key === 'password' ? 'password' : 'text'} value={form[key]} onChange={event => setForm({ ...form, [key]: event.target.value })} maxLength={key === 'username' ? 32 : key === 'password' ? 64 : undefined} pattern={key === 'username' ? '[A-Za-z][A-Za-z0-9_]{3,31}' : undefined} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" placeholder={placeholder} /></label>)}</div><p className="mt-4 text-xs leading-5 text-muted-foreground">账号以英文字母开头，可使用英文字母、数字和下划线；初始密码为 8–64 位，须包含字母和数字。</p><div className="mt-7 grid grid-cols-2 gap-3 sm:flex sm:justify-end"><Button variant="secondary" onClick={() => setOpen(false)}>取消</Button><Button disabled={saving} onClick={() => void create()}>{saving ? '正在创建…' : '创建候选人'}</Button></div></div></div>}
  </div>
}
