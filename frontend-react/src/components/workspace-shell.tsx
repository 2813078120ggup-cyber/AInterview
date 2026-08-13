import * as Dialog from '@radix-ui/react-dialog'
import { Bot, ChevronDown, LogOut, Menu, Moon, Sun, UserRound, X } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { NotificationCenter } from '@/components/notification-center'
import { PageTransition } from '@/components/page-transition'
import { Button } from '@/components/ui/button'
import { request, requestBlob } from '@/lib/api'
import { clearSession, profile, PROFILE_UPDATED_EVENT, updateLocalProfile, type Profile } from '@/lib/session'
import { useTheme } from '@/lib/theme'
import {
  buildContextualPath,
  domainsFor,
  matchWorkspaceRoute,
  secondaryItemsFor,
  type WorkspaceAudience,
  type WorkspaceDomain,
  type WorkspaceRouteItem,
} from '@/components/workspace-navigation'
import { cn } from '@/lib/utils'

const audienceLabels: Record<WorkspaceAudience, { brand: string; role: string; home: string }> = {
  candidate: { brand: '候选人工作台', role: '候选人端', home: '/workspace' },
  company: { brand: '企业招聘工作台', role: '企业端', home: '/company' },
  admin: { brand: '平台运营工作台', role: '超级管理员端', home: '/admin/workspace' },
}

function WorkspaceBrand({ audience, onNavigate, compact = false }: { audience: WorkspaceAudience; onNavigate?: () => void; compact?: boolean }) {
  const copy = audienceLabels[audience]
  return <Link to={copy.home} onClick={onNavigate} className={cn('flex min-w-0 items-center gap-3', compact ? 'px-1 py-2' : 'px-1 py-3')}>
    <span className={cn('grid shrink-0 place-items-center rounded-xl bg-[var(--primary)] text-[var(--primary-foreground)]', compact ? 'h-9 w-9' : 'h-10 w-10')}>
      <Bot className={compact ? 'h-4 w-4' : 'h-5 w-5'} aria-hidden="true" />
    </span>
    <span className="min-w-0">
      <strong className={cn('block truncate tracking-[-.02em]', compact ? 'text-base' : 'text-lg')}>AInterview</strong>
      {!compact && <span className="block truncate text-xs text-muted-foreground">{copy.brand}</span>}
    </span>
  </Link>
}

function WorkspaceDomainNavigation({ audience, currentDomain, onNavigate, mobile = false }: { audience: WorkspaceAudience; currentDomain: WorkspaceDomain; onNavigate?: () => void; mobile?: boolean }) {
  return <nav aria-label={`${audienceLabels[audience].role}业务域`} className={cn('flex min-w-0', mobile ? 'grid grid-cols-2 gap-1 border-y border-border py-3' : 'items-center gap-1')}>
    {domainsFor(audience).map(domain => {
      const active = currentDomain.key === domain.key
      const content = <span className={cn(
        'relative flex min-h-10 items-center justify-center whitespace-nowrap px-3 text-sm font-semibold transition-colors duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]',
        mobile ? 'rounded-lg px-3 py-2' : 'px-3 lg:px-4',
        active ? 'text-foreground' : 'text-muted-foreground hover:text-foreground',
        !domain.path && 'cursor-not-allowed opacity-55',
      )} title={!domain.path ? `${domain.label}功能暂未开放` : undefined}>
        {domain.label}
        {active && <span className={cn('absolute bg-[var(--accent)]', mobile ? 'inset-x-3 bottom-0 h-0.5' : 'inset-x-3 bottom-0 h-0.5')} aria-hidden="true" />}
      </span>
      if (!domain.path) return <span key={domain.key} aria-disabled="true">{content}</span>
      return <Link key={domain.key} to={domain.path} onClick={onNavigate} aria-current={active ? 'page' : undefined}>{content}</Link>
    })}
  </nav>
}

export function WorkspaceUserMenu({ audience, current, profileTo, onLogout }: { audience: WorkspaceAudience; current: Profile | null; profileTo?: string; onLogout: () => void }) {
  const [open, setOpen] = useState(false)
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null)
  const rootRef = useRef<HTMLDivElement>(null)
  const initials = current?.realName?.trim().slice(0, 1) || (audience === 'company' ? '企' : audience === 'admin' ? '管' : '我')

  useEffect(() => {
    let active = true
    let objectUrl: string | null = null
    setAvatarUrl(null)
    if (!current?.avatarAvailable) return () => { active = false }

    void requestBlob('/v1/account/avatar/content').then(blob => {
      if (!active) return
      objectUrl = URL.createObjectURL(blob)
      setAvatarUrl(objectUrl)
    }).catch(() => {
      // The initials remain the safe fallback when the protected media request fails.
    })

    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [current?.id, current?.avatarAvailable])

  useEffect(() => {
    if (!open) return
    const closeOnOutside = (event: PointerEvent) => {
      if (event.target instanceof Node && !rootRef.current?.contains(event.target)) setOpen(false)
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
        rootRef.current?.querySelector<HTMLButtonElement>('button')?.focus()
      }
    }
    document.addEventListener('pointerdown', closeOnOutside)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOnOutside)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open])

  return <div ref={rootRef} className="relative">
    <Button
      type="button"
      variant="ghost"
      className="h-10 gap-1 rounded-full px-1.5 pl-1"
      aria-label="打开用户菜单"
      aria-expanded={open}
      aria-haspopup="menu"
      onClick={() => setOpen(value => !value)}
    >
      <span className="grid h-8 w-8 shrink-0 place-items-center overflow-hidden rounded-full bg-[var(--info)] text-sm font-bold text-[var(--info-foreground)]">
        {avatarUrl ? <img src={avatarUrl} alt="" className="h-full w-full object-cover" /> : initials}
      </span>
      <ChevronDown className={cn('hidden h-4 w-4 text-muted-foreground transition-transform sm:block', open && 'rotate-180')} aria-hidden="true" />
    </Button>
    {open && <div role="menu" aria-label="用户菜单" className="absolute right-0 top-[calc(100%+0.5rem)] z-[80] w-[min(17rem,calc(100vw-2rem))] overflow-hidden rounded-2xl border border-border bg-surface p-2 text-left shadow-[0_18px_48px_rgba(42,31,20,0.16)] dark:shadow-[0_22px_54px_rgba(0,0,0,0.4)]">
      <div className="rounded-xl bg-muted/60 px-3 py-3">
        <p className="truncate text-sm font-bold">{current?.realName || '当前用户'}</p>
        <p className="mt-1 truncate text-xs text-muted-foreground">{current?.username || '未读取账号'} · {audienceLabels[audience].role}</p>
      </div>
      {profileTo && <NavLink role="menuitem" to={profileTo} onClick={() => setOpen(false)} className="mt-2 flex min-h-10 items-center gap-2 rounded-xl px-3 text-sm font-semibold text-muted-foreground transition hover:bg-muted hover:text-foreground">
        <UserRound className="h-4 w-4" aria-hidden="true" />账户设置
      </NavLink>}
      <button role="menuitem" type="button" onClick={() => { setOpen(false); onLogout() }} className="mt-1 flex min-h-10 w-full items-center gap-2 rounded-xl px-3 text-sm font-semibold text-muted-foreground transition hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]">
        <LogOut className="h-4 w-4" aria-hidden="true" />退出登录
      </button>
    </div>}
  </div>
}

export function WorkspaceGlobalNav({ audience, currentDomain, onOpenNavigation, dark, toggleTheme, onLogout }: { audience: WorkspaceAudience; currentDomain: WorkspaceDomain; onOpenNavigation: () => void; dark: boolean; toggleTheme: () => void; onLogout: () => void }) {
  const [current, setCurrent] = useState<Profile | null>(() => profile())
  const profileTo = audience === 'candidate' ? buildContextualPath('/candidate/settings', currentDomain.key) : undefined

  useEffect(() => {
    const syncLocalProfile = () => setCurrent(profile())
    window.addEventListener(PROFILE_UPDATED_EVENT, syncLocalProfile)

    void request<{ realName: string; avatarAvailable: boolean }>('/v1/account/profile').then(account => {
      updateLocalProfile({ realName: account.realName, avatarAvailable: account.avatarAvailable })
      syncLocalProfile()
    }).catch(() => {
      // The stored login profile remains available when the profile endpoint is temporarily unavailable.
    })

    return () => window.removeEventListener(PROFILE_UPDATED_EVENT, syncLocalProfile)
  }, [])

  return <header className="fixed inset-x-0 top-0 z-30 border-b border-border bg-background">
    <div className="mx-auto flex h-16 w-full max-w-[1440px] items-center gap-3 px-4 sm:px-6 lg:h-[4.5rem] lg:px-8">
      <Button type="button" variant="ghost" className="h-10 w-10 shrink-0 rounded-full px-0 lg:hidden" aria-label={`打开${audienceLabels[audience].role}导航`} onClick={onOpenNavigation}>
        <Menu className="h-5 w-5" aria-hidden="true" />
      </Button>
      <WorkspaceBrand audience={audience} compact />
      <div className="ml-2 hidden min-w-0 flex-1 lg:block"><WorkspaceDomainNavigation audience={audience} currentDomain={currentDomain} /></div>
      <div className="ml-auto flex items-center gap-1 sm:gap-2">
        <Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={toggleTheme} aria-label={dark ? '切换为浅色模式' : '切换为深色模式'}>
          {dark ? <Sun className="h-4 w-4" aria-hidden="true" /> : <Moon className="h-4 w-4" aria-hidden="true" />}
        </Button>
        <NotificationCenter role={audience === 'candidate' ? 'candidate' : 'admin'} />
        <WorkspaceUserMenu audience={audience} current={current} profileTo={profileTo} onLogout={onLogout} />
      </div>
    </div>
  </header>
}

function contextualTo(audience: WorkspaceAudience, item: WorkspaceRouteItem, domain: WorkspaceDomain) {
  return audience === 'candidate' && (item.path === '/candidate/tickets' || item.path === '/candidate/settings' || item.path === '/users')
    ? buildContextualPath(item.path, domain.key)
    : item.path
}

export function WorkspaceContextSidebar({ audience, currentDomain, onNavigate, mobile = false }: { audience: WorkspaceAudience; currentDomain: WorkspaceDomain; onNavigate?: () => void; mobile?: boolean }) {
  const location = useLocation()
  const current = matchWorkspaceRoute(audience, location.pathname, location.search)
  return <aside className={cn(
    mobile
      ? 'safe-area-bottom flex min-h-0 flex-1 flex-col overflow-y-auto px-1 py-5'
      : 'fixed inset-y-0 left-0 top-[4.5rem] z-20 hidden w-64 flex-col border-r border-border bg-surface px-5 py-7 lg:flex',
  )}>
    <p className="px-3 text-xs font-semibold uppercase tracking-[.16em] text-muted-foreground">{currentDomain.label}</p>
    {currentDomain.items.length ? <nav aria-label={`${currentDomain.label}页面`} className="mt-3 space-y-1">
      {currentDomain.items.map(item => <WorkspaceSidebarLink key={item.path} item={item} domain={currentDomain} audience={audience} active={current.item?.path === item.path && current.domain.key === currentDomain.key} onNavigate={onNavigate} />)}
    </nav> : <p className="mt-3 rounded-xl bg-muted/50 px-3 py-3 text-sm leading-6 text-muted-foreground">该业务域的页面正在接入，现有业务入口保持不变。</p>}
    {secondaryItemsFor(audience).length > 0 && <div className="mt-auto border-t border-border pt-5">
      <nav aria-label="账户与支持" className="space-y-1">
        {secondaryItemsFor(audience).map(item => <WorkspaceSidebarLink key={item.path} item={item} domain={currentDomain} audience={audience} active={current.item?.path === item.path && current.domain.key === currentDomain.key} onNavigate={onNavigate} />)}
      </nav>
      <div className="mt-5 flex items-center gap-2 px-3 text-xs text-muted-foreground"><UserRound className="h-3.5 w-3.5" aria-hidden="true" /><span className="truncate">{profile()?.realName || '当前用户'} · {audienceLabels[audience].role}</span></div>
    </div>}
  </aside>
}

function WorkspaceSidebarLink({ item, domain, audience, active, onNavigate }: { item: WorkspaceRouteItem; domain: WorkspaceDomain; audience: WorkspaceAudience; active: boolean; onNavigate?: () => void }) {
  const Icon = item.icon
  return <NavLink to={contextualTo(audience, item, domain)} end={item.end} onClick={onNavigate} className={cn('group flex min-h-11 items-center gap-3 border-l-2 px-3 py-2.5 text-sm transition-colors duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--accent)]', active ? 'border-[var(--accent)] bg-[var(--accent-soft)] text-foreground' : 'border-transparent text-muted-foreground hover:border-border hover:bg-muted/70 hover:text-foreground')} aria-current={active ? 'page' : undefined}>
    <Icon className={cn('h-4 w-4 shrink-0', active ? 'text-[var(--accent)]' : 'text-muted-foreground group-hover:text-foreground')} aria-hidden="true" />
    <span className="min-w-0"><span className="block font-semibold">{item.label}</span><span className="mt-0.5 block truncate text-xs text-muted-foreground">{item.description}</span></span>
  </NavLink>
}

export function WorkspaceMobileDrawer({ audience, currentDomain, open, onOpenChange, onLogout }: { audience: WorkspaceAudience; currentDomain: WorkspaceDomain; open: boolean; onOpenChange: (open: boolean) => void; onLogout: () => void }) {
  return <Dialog.Root open={open} onOpenChange={onOpenChange}>
    <Dialog.Portal>
      <Dialog.Overlay className="fixed inset-0 z-50 bg-black/45 backdrop-blur-[2px]" />
      <Dialog.Content className="safe-area-bottom fixed inset-y-0 left-0 z-[51] flex w-[min(22rem,calc(100vw-2rem))] flex-col overflow-y-auto border-r border-border bg-surface p-5 shadow-2xl focus:outline-none" aria-describedby="workspace-mobile-drawer-description">
        <div className="flex items-center justify-between gap-3">
          <WorkspaceBrand audience={audience} onNavigate={() => onOpenChange(false)} />
          <Dialog.Close asChild><Button type="button" variant="ghost" className="h-11 w-11 shrink-0 rounded-full px-0" aria-label="关闭导航"><X className="h-5 w-5" aria-hidden="true" /></Button></Dialog.Close>
        </div>
        <Dialog.Title className="sr-only">{audienceLabels[audience].role}导航</Dialog.Title>
        <Dialog.Description id="workspace-mobile-drawer-description" className="sr-only">选择业务域和当前业务域内的页面。</Dialog.Description>
        <div className="mt-5"><WorkspaceDomainNavigation audience={audience} currentDomain={currentDomain} onNavigate={() => onOpenChange(false)} mobile /></div>
        <WorkspaceContextSidebar audience={audience} currentDomain={currentDomain} onNavigate={() => onOpenChange(false)} mobile />
        <button type="button" className="mt-4 flex min-h-11 w-full items-center gap-2 rounded-xl border-t border-border px-3 pt-4 text-sm font-semibold text-muted-foreground transition hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]" onClick={onLogout}>
          <LogOut className="h-4 w-4" aria-hidden="true" />退出登录
        </button>
      </Dialog.Content>
    </Dialog.Portal>
  </Dialog.Root>
}

export function WorkspacePageHeader({ audience, currentDomain }: { audience: WorkspaceAudience; currentDomain: WorkspaceDomain }) {
  const location = useLocation()
  const meta = useMemo(() => matchWorkspaceRoute(audience, location.pathname, location.search), [audience, location.pathname, location.search])
  return <header className="mb-6 flex min-h-8 items-center justify-between gap-4 border-b border-border/70 pb-4">
    <div className="min-w-0"><p className="truncate text-xs font-bold uppercase tracking-[.16em] text-[var(--accent)]">{currentDomain.label}</p><p className="mt-1 truncate text-sm font-semibold text-muted-foreground">{meta.item?.label || currentDomain.description}</p></div>
    <p className="hidden shrink-0 text-xs text-muted-foreground sm:block">{audienceLabels[audience].role}</p>
  </header>
}

export function WorkspaceShell({ audience, children, showPageHeader = true }: { audience: WorkspaceAudience; children: ReactNode; showPageHeader?: boolean }) {
  const { dark, toggleTheme } = useTheme()
  const location = useLocation()
  const navigate = useNavigate()
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false)
  const current = matchWorkspaceRoute(audience, location.pathname, location.search)

  useEffect(() => setMobileNavigationOpen(false), [location.pathname])

  async function logout() {
    const refreshToken = localStorage.getItem('refresh_token')
    let serverLogoutConfirmed = true
    if (refreshToken) {
      try {
        await request<void>('/v1/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken }) })
      } catch {
        serverLogoutConfirmed = false
      }
    }
    clearSession()
    if (!serverLogoutConfirmed) window.alert('本机已清理会话，但服务端会话撤销状态未确认。请稍后重新登录并检查账户安全。')
    navigate('/login', { replace: true })
  }

  return <div className={cn('workspace-shell min-h-dvh bg-background', audience === 'admin' && 'admin-workspace-shell')}>
    <WorkspaceGlobalNav audience={audience} currentDomain={current.domain} onOpenNavigation={() => setMobileNavigationOpen(true)} dark={dark} toggleTheme={toggleTheme} onLogout={logout} />
    <WorkspaceMobileDrawer audience={audience} currentDomain={current.domain} open={mobileNavigationOpen} onOpenChange={setMobileNavigationOpen} onLogout={logout} />
    <WorkspaceContextSidebar audience={audience} currentDomain={current.domain} />
    <main className="min-h-dvh pt-16 lg:pl-64 lg:pt-[4.5rem]">
      <div className="mx-auto w-full max-w-[1440px] px-4 pb-20 pt-6 sm:px-6 lg:px-8 lg:pt-8">
        {showPageHeader && <WorkspacePageHeader audience={audience} currentDomain={current.domain} />}
        <PageTransition>{children}</PageTransition>
      </div>
    </main>
  </div>
}
