import { Activity, BookOpen, Bot, CalendarDays, Code2, FileCode2, FileText, History, LayoutDashboard, LogOut, Menu, MessageSquareWarning, Moon, Settings2, Sun, UserRound, X } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { NotificationCenter } from '@/components/notification-center'
import { PageTransition } from '@/components/page-transition'
import { Button } from '@/components/ui/button'
import { clearSession, profile } from '@/lib/session'
import { useTheme } from '@/lib/theme'
import { cn } from '@/lib/utils'

const nav = [
  ['/admin/workspace', '工作台', LayoutDashboard],
  ['/admin/interviews', '面试管理', CalendarDays],
  ['/admin/candidates', '候选人', UserRound],
  ['/admin/question-banks', '题库管理', BookOpen],
  ['/admin/learning-resources', '学习资料', FileText],
  ['/admin/algorithm/problems', '算法题目', Code2],
  ['/admin/tickets', '反馈工单', MessageSquareWarning],
  ['/admin/prompt-templates', '提示词版本', FileCode2],
  ['/admin/ai-generations', 'AI 调用审计', Activity],
  ['/admin/audit-logs', '操作日志', History],
  ['/admin/settings', '系统设置', Settings2],
] as const

function Brand({ onNavigate }: { onNavigate?: () => void }) {
  return <NavLink to="/admin/workspace" onClick={onNavigate} className="flex min-w-0 items-center gap-3 px-1 py-3">
    <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-[linear-gradient(135deg,var(--brand),var(--brand-pink))] text-white shadow-[0_12px_30px_rgba(109,93,252,.24)]">
      <Bot className="h-5 w-5" />
    </span>
    <div className="min-w-0">
      <strong className="block truncate text-lg">AInterview</strong>
      <p className="truncate text-xs text-muted-foreground">智能面试管理平台</p>
    </div>
  </NavLink>
}

function AdminNavigation({ onNavigate }: { onNavigate?: () => void }) {
  return <nav aria-label="管理端主导航" className="space-y-1.5">
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

export function AdminPageShell({ children }: { children: ReactNode }) {
  const { dark, toggleTheme } = useTheme()
  const current = profile()
  const navigate = useNavigate()
  const initials = current?.realName?.trim().slice(0, 1) || '管'
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false)
  function logout() { clearSession(); navigate('/login', { replace: true }) }

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

  return <div className="min-h-screen bg-background">
    <aside className="fixed inset-y-0 left-0 z-40 hidden w-72 flex-col border-r border-border bg-surface/92 p-5 backdrop-blur lg:flex">
      <Brand />
      <AdminNavigation />
      <div className="mt-auto border-t border-border px-2 pt-4 text-xs text-muted-foreground">管理端 · v2.4.1</div>
    </aside>

    {mobileNavigationOpen && <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="管理端导航">
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
        <AdminNavigation onNavigate={() => setMobileNavigationOpen(false)} />
        <div className="mt-auto border-t border-border pt-3">
          <Button type="button" variant="ghost" className="w-full justify-start px-4" onClick={logout}>
            <LogOut className="h-4 w-4" />退出登录
          </Button>
          <p className="px-4 pt-3 text-xs text-muted-foreground">管理端 · v2.4.1</p>
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
          <NavLink to="/admin/workspace" className="truncate text-base font-bold">AInterview</NavLink>
        </div>
        <div className="flex items-center gap-1 sm:gap-2">
          <Button variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={toggleTheme} aria-label={dark ? '切换为浅色模式' : '切换为深色模式'}>{dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}</Button>
          <NotificationCenter role="admin" />
          <Button variant="ghost" className="hidden h-10 w-10 rounded-full px-0 sm:inline-flex" onClick={logout} aria-label="退出登录" title="退出登录"><LogOut className="h-4 w-4" /></Button>
          <span title={current?.realName || '管理员'} className="grid h-10 w-10 place-items-center rounded-full bg-[var(--warning)] text-sm font-bold text-[var(--warning-foreground)]">{initials}</span>
        </div>
      </header>
      <PageTransition>
        <div className="[&>div>aside]:hidden [&>div>main]:!min-h-0 [&>div>main]:!pl-0 [&>div>main>header]:hidden">{children}</div>
      </PageTransition>
    </main>
  </div>
}
