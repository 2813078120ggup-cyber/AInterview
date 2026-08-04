import { CalendarCheck2, LogOut, ShieldCheck } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { CandidatePageShell } from '@/components/candidate-page-shell'
import { clearSession, profile } from '@/lib/session'

export function CandidateProfile() {
  const navigate = useNavigate()
  const user = profile()
  function logout() {
    clearSession()
    navigate('/login', { replace: true })
  }

  return <CandidatePageShell><div className="max-w-4xl space-y-6">
    <header><p className="text-sm font-semibold text-[var(--accent)]">账户信息</p><h1 className="mt-2 text-3xl font-bold">账户中心</h1><p className="mt-2 text-muted-foreground">查看账户资料和当前可用功能。</p></header>
    <Card className="overflow-hidden p-0">
      <div className="soft-emphasis-panel p-5 sm:p-7"><div className="flex items-center gap-4 sm:gap-5"><span className="grid h-14 w-14 shrink-0 place-items-center rounded-[20px] bg-white/15 text-xl font-bold sm:h-16 sm:w-16 sm:rounded-[22px] sm:text-2xl">{user?.realName?.slice(0, 1) || '我'}</span><div className="min-w-0"><h2 className="truncate text-2xl font-bold">{user?.realName || '候选人'}</h2><p className="mt-1 truncate text-white/75">@{user?.username}</p><div className="mt-3 flex flex-wrap gap-2">{user?.roles.map(role => <Badge key={role} tone="success">{role}</Badge>)}</div></div></div></div>
      <div className="grid gap-4 p-4 sm:grid-cols-2 sm:p-6"><div className="rounded-2xl bg-muted/60 p-4"><p className="text-sm text-muted-foreground">账户角色</p><p className="mt-2 flex items-center gap-2 font-semibold"><ShieldCheck className="h-4 w-4 shrink-0 text-[var(--accent)]" />候选人账户</p></div><div className="rounded-2xl bg-muted/60 p-4"><p className="text-sm text-muted-foreground">可用功能</p><p className="mt-2 flex items-center gap-2 font-semibold"><CalendarCheck2 className="h-4 w-4 shrink-0 text-[var(--accent)]" />模拟面试与能力报告</p></div></div>
    </Card>
    <Card><div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between"><div><h2 className="font-bold">账户安全</h2><p className="mt-1 text-sm text-muted-foreground">退出后需重新登录方可访问面试内容。</p></div><Button variant="secondary" className="w-full sm:w-auto" onClick={logout}><LogOut className="h-4 w-4" />退出登录</Button></div></Card>
  </div></CandidatePageShell>
}
