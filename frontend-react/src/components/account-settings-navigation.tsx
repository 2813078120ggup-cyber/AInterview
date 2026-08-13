import { BellRing, KeyRound, UserRound } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'

const items = [
  { path: '/candidate/settings/profile', label: '个人资料', icon: UserRound },
  { path: '/candidate/settings/security', label: '登录安全', icon: KeyRound },
  { path: '/candidate/settings/notifications', label: '通知偏好', icon: BellRing },
]

export function AccountSettingsNavigation() {
  const location = useLocation()
  return <nav aria-label="账户设置分区" className="flex max-w-full gap-2 overflow-x-auto rounded-2xl border border-border bg-[var(--surface-soft)] p-1.5">
    {items.map(({ path, label, icon: Icon }) => {
      const active = location.pathname === path
      return <Link key={path} to={`${path}${location.search}`} aria-current={active ? 'page' : undefined} className={`inline-flex h-10 shrink-0 items-center justify-center gap-2 rounded-xl px-4 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] ${active ? 'bg-surface text-foreground shadow-sm' : 'text-muted-foreground hover:bg-surface/70 hover:text-foreground'}`}><Icon className="h-4 w-4" aria-hidden="true" />{label}</Link>
    })}
  </nav>
}
