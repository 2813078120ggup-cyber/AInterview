import {
  BarChart3,
  BookOpen,
  Bot,
  CalendarDays,
  CalendarRange,
  Code2,
  LayoutDashboard,
  LogOut,
  Menu,
  MessageSquareWarning,
  Moon,
  NotebookPen,
  Sun,
  UserRound,
  X,
} from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { NotificationCenter } from '@/components/notification-center'
import { PageTransition } from '@/components/page-transition'
import { Button } from '@/components/ui/button'
import { clearSession, profile } from '@/lib/session'
import { useTheme } from '@/lib/theme'
import { cn } from '@/lib/utils'

const nav = [
  ['/workspace', '工作台', LayoutDashboard],
  ['/candidate/interviews', '面试大厅', CalendarDays],
  ['/candidate/calendar', '面试日历', CalendarRange],
  ['/algorithm', '算法练习', Code2],
  ['/library', '专项练习', BookOpen],
  ['/reports', '能力报告', BarChart3],
  ['/candidate/reflections', '面试心得', NotebookPen],
  ['/candidate/tickets', '问题反馈', MessageSquareWarning],
  ['/users', '账户中心', UserRound],
] as const

function Brand({ onNavigate }: { onNavigate?: () => void }) {
  return <NavLink to="/workspace" onClick={onNavigate} className="flex min-w-0 items-center gap-3 px-1 py-3">
    <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-[linear-gradient(135deg,var(--brand),var(--brand-pink))] text-white shadow-[0_12px_30px_rgba(109,93,252,.24)]">
      <Bot className="h-5 w-5" />
    </span>
    <div className="min-w-0">
      <strong className="block truncate text-lg">AInterview</strong>
      <p className="truncate text-xs text-muted-foreground">智能面试评测平台</p>
    </div>
  </NavLink>
}

function CandidateNavigation({ onNavigate }: { onNavigate?: () => void }) {
  return <nav aria-label="候选人主导航" className="space-y-1.5">
    {nav.map(([to, label, Icon]) => <NavLink
      key={to}
      to={to}
      onClick={onNavigate}
      className={({ isActive }) => cn(
        'flex min-h-12 items-center gap-3 rounded-[18px] px-4 py-3 text-sm font-semibold transition duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]',
        isActive
          ? 'bg-[var(--primary)] text-[var(--primary-foreground)] shadow-[0_14px_30px_rgba(20,18,17,.16)]'
          : 'text-muted-foreground hover:bg-muted hover:text-foreground',
      )}
    >
      <Icon className="h-4 w-4" />{label}
    </NavLink>)}
  </nav>
}

export function CandidatePageShell({ children }: { children: ReactNode }) {
  const { dark, toggleTheme } = useTheme()
  const navigate = useNavigate()
  const current = profile()
  const initials = current?.realName?.trim().slice(0, 1) || '我'
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false)

  useEffect(() => {
    if (!mobileNavigationOpen) return
    const previousOverflow = document.body.style.overflow
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMobileNavigationOpen(false)
    }
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [mobileNavigationOpen])

  function logout() {
    clearSession()
    navigate('/login', { replace: true })
  }

  return <div className="min-h-screen bg-background">
    <aside className="fixed inset-y-0 left-0 z-40 hidden w-72 flex-col border-r border-border bg-surface/92 p-5 backdrop-blur lg:flex">
      <Brand />
      <CandidateNavigation />
      <div className="mt-auto border-t border-border px-2 pt-4 text-xs text-muted-foreground">候选人端 · v2.4.0</div>
    </aside>

    {mobileNavigationOpen && <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="候选人导航">
      <button
        type="button"
        aria-label="关闭导航遮罩"
        className="absolute inset-0 h-full w-full cursor-default bg-black/45 backdrop-blur-sm"
        onClick={() => setMobileNavigationOpen(false)}
      />
      <aside className="safe-area-bottom absolute inset-y-0 left-0 flex w-[min(20rem,calc(100vw-3rem))] flex-col overflow-y-auto border-r border-border bg-surface p-5 shadow-2xl">
        <div className="flex items-center justify-between gap-3">
          <Brand onNavigate={() => setMobileNavigationOpen(false)} />
          <Button
            type="button"
            variant="ghost"
            className="h-11 w-11 shrink-0 rounded-full px-0"
            aria-label="关闭导航"
            onClick={() => setMobileNavigationOpen(false)}
          >
            <X className="h-5 w-5" />
          </Button>
        </div>
        <CandidateNavigation onNavigate={() => setMobileNavigationOpen(false)} />
        <div className="mt-auto border-t border-border pt-3">
          <Button type="button" variant="ghost" className="w-full justify-start px-4" onClick={logout}>
            <LogOut className="h-4 w-4" />退出登录
          </Button>
          <p className="px-4 pt-3 text-xs text-muted-foreground">候选人端 · v2.4.0</p>
        </div>
      </aside>
    </div>}

    <main className="min-h-screen lg:pl-72">
      <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-border bg-background/88 px-4 backdrop-blur-xl sm:px-6 lg:h-20 lg:justify-end lg:px-8">
        <div className="flex min-w-0 items-center gap-2 lg:hidden">
          <Button
            type="button"
            variant="ghost"
            className="h-11 w-11 shrink-0 rounded-full px-0"
            aria-label="打开导航"
            onClick={() => setMobileNavigationOpen(true)}
          >
            <Menu className="h-5 w-5" />
          </Button>
          <NavLink to="/workspace" className="truncate text-base font-bold">AInterview</NavLink>
        </div>
        <div className="flex items-center gap-1 sm:gap-2">
          <Button variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={toggleTheme} aria-label={dark ? '切换为浅色模式' : '切换为深色模式'}>
            {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </Button>
          <NotificationCenter role="candidate" />
          <Button variant="ghost" className="hidden h-10 w-10 rounded-full px-0 sm:inline-flex" onClick={logout} aria-label="退出登录" title="退出登录">
            <LogOut className="h-4 w-4" />
          </Button>
          <NavLink
            to="/users"
            aria-label="进入账户中心"
            title={`${current?.realName || '候选人'} · 账户中心`}
            className="grid h-10 w-10 place-items-center rounded-full bg-[var(--info)] text-sm font-bold text-[var(--info-foreground)] transition hover:ring-2 hover:ring-[var(--brand)] hover:ring-offset-2 hover:ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] focus-visible:ring-offset-2 focus-visible:ring-offset-background"
          >
            {initials}
          </NavLink>
        </div>
      </header>
      <div className="mx-auto max-w-7xl p-4 pb-24 sm:p-6 sm:pb-24 lg:p-10">
        <PageTransition>{children}</PageTransition>
      </div>
    </main>
  </div>
}
