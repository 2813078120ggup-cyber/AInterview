import { Bell, CheckCheck, Inbox, Send, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { notificationEvent } from '@/lib/notifications'
import { request } from '@/lib/api'
import { cn } from '@/lib/utils'

type NotificationCenterProps = {
  role: 'admin' | 'candidate'
}

type SiteNotification = {
  id: string
  notificationType: string
  title: string
  content: string
  businessType?: string
  businessId?: string
  read: boolean
  createdAt: string
}

type NotificationPage = {
  records: SiteNotification[]
  total: number
  pageNo: number
  pageSize: number
}

function shortDate(value?: string) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

export function NotificationCenter({ role }: NotificationCenterProps) {
  const navigate = useNavigate()
  const rootRef = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<SiteNotification[]>([])

  async function refresh() {
    try {
      const result = await request<NotificationPage>('/v1/notifications?pageNo=1&pageSize=50')
      setItems(result.records)
    } catch {
      // The notification bell should not make the surrounding workspace fail.
    }
  }
  const closePanel = () => {
    setOpen(false)
    window.requestAnimationFrame(() => triggerRef.current?.focus())
  }

  useEffect(() => {
    refresh()
    const onChange = () => refresh()
    const onFocus = () => refresh()
    window.addEventListener(notificationEvent, onChange)
    window.addEventListener('focus', onFocus)
    const timer = window.setInterval(refresh, 20000)
    return () => {
      window.removeEventListener(notificationEvent, onChange)
      window.removeEventListener('focus', onFocus)
      window.clearInterval(timer)
    }
  }, [])

  useEffect(() => {
    if (!open) return

    const closeOnOutsidePointer = (event: PointerEvent) => {
      const target = event.target
      if (target instanceof Node && (rootRef.current?.contains(target) || panelRef.current?.contains(target))) return
      closePanel()
    }

    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closePanel()
    }

    const shouldLockPage = window.matchMedia('(max-width: 639px)').matches
    const previousOverflow = document.body.style.overflow
    if (shouldLockPage) document.body.style.overflow = 'hidden'
    document.addEventListener('pointerdown', closeOnOutsidePointer)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      if (shouldLockPage) document.body.style.overflow = previousOverflow
      document.removeEventListener('pointerdown', closeOnOutsidePointer)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [open])

  const visibleItems = items

  const unreadCount = visibleItems.filter(item => !item.read).length

  async function markRead(item: SiteNotification) {
    if (!item.read) {
      await request(`/v1/notifications/${item.id}/read`, { method: 'PUT' })
      setItems(previous => previous.map(current => current.id === item.id ? { ...current, read: true } : current))
    }
    if (!item.businessId) return
    if (item.businessType === 'FEEDBACK_TICKET') {
      navigate(role === 'admin' ? `/admin/tickets/${item.businessId}` : `/candidate/tickets/${item.businessId}`)
    } else if (item.businessType === 'INTERVIEW') {
      navigate(role === 'admin' ? `/admin/interviews/${item.businessId}` : `/candidate/interviews/${item.businessId}/room`)
    }
  }

  async function markAllRead() {
    await request('/v1/notifications/read-all', { method: 'PUT' })
    setItems(previous => previous.map(item => ({ ...item, read: true })))
  }

  return (
    <div ref={rootRef} className="relative">
      <Button
        ref={triggerRef}
        variant="ghost"
        className="relative h-10 w-10 rounded-full px-0"
        aria-label="通知"
        aria-expanded={open}
        aria-controls="notification-center-panel"
        onClick={() => open ? closePanel() : setOpen(true)}
      >
        <Bell className="h-4 w-4" />
        {unreadCount > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#b77a54] px-1 text-[10px] font-bold text-white">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </Button>

      {open && createPortal(
        <div className="fixed inset-0 z-[70] flex items-end sm:items-start sm:justify-end sm:p-4 sm:pt-20 lg:pr-8">
          <button
            type="button"
            aria-label="关闭通知中心"
            className="absolute inset-0 h-full w-full cursor-default bg-black/40 backdrop-blur-[2px] sm:bg-transparent sm:backdrop-blur-none"
            onClick={closePanel}
          />
          <section
            ref={panelRef}
            id="notification-center-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="notification-center-title"
            className="safe-area-bottom relative z-10 flex max-h-[min(86dvh,680px)] w-full flex-col overflow-hidden rounded-t-[28px] border border-border bg-surface/98 text-foreground shadow-[0_24px_70px_rgba(42,31,20,0.18)] backdrop-blur-xl sm:max-h-[calc(100dvh-6rem)] sm:w-[min(390px,calc(100vw-32px))] sm:rounded-[28px] dark:shadow-[0_28px_80px_rgba(0,0,0,0.42)]"
          >
            <header className="flex items-start justify-between gap-4 border-b border-border px-5 py-4 sm:p-5">
              <div className="min-w-0">
                <p className="text-xs font-bold uppercase tracking-[0.18em] text-[var(--accent)]">
                  {role === 'admin' ? '已发送通知' : '消息通知'}
                </p>
                <div className="mt-1 flex min-w-0 items-center gap-2">
                  <h3 id="notification-center-title" className="truncate text-xl font-black text-foreground">通知中心</h3>
                  {unreadCount > 0 && <span className="shrink-0 rounded-full bg-[var(--accent-soft)] px-2 py-0.5 text-xs font-semibold text-[var(--accent)]">{unreadCount} 条未读</span>}
                </div>
                <p className="mt-1 text-sm leading-5 text-muted-foreground">
                  {role === 'admin' ? '查看近期发送的面试通知。' : '查看面试安排与练习提醒。'}
                </p>
              </div>
              <Button
                type="button"
                variant="ghost"
                className="h-11 w-11 shrink-0 rounded-full px-0"
                aria-label="关闭"
                onClick={closePanel}
              >
                <X className="h-5 w-5" />
              </Button>
            </header>

            {visibleItems.length > 0 && (
              <div className="flex min-h-12 items-center justify-between gap-3 border-b border-border bg-muted/35 px-5 py-2.5">
                <span className="text-xs text-muted-foreground">共 {visibleItems.length} 条通知</span>
                {unreadCount > 0 && <Button
                  variant="ghost"
                  className="h-9 shrink-0 rounded-full px-3 text-xs"
                  onClick={markAllRead}
                >
                  <CheckCheck className="h-4 w-4" />
                  全部标为已读
                </Button>}
              </div>
            )}

            <div className="min-h-0 flex-1 overscroll-contain overflow-y-auto p-3 sm:max-h-[min(480px,calc(100dvh-16rem))]">
              {visibleItems.length === 0 ? (
                <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
                  <span className="flex h-14 w-14 items-center justify-center rounded-3xl bg-muted text-[var(--accent)]">
                    <Inbox className="h-6 w-6" />
                  </span>
                  <p className="mt-4 text-base font-black text-foreground">暂无通知</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {role === 'admin' ? '可在面试管理中向候选人发送通知。' : '新的面试通知将在此显示。'}
                  </p>
                </div>
              ) : (
                <div className="space-y-2">
                  {visibleItems.map(item => {
                    const unread = !item.read
                    return (
                      <button
                        key={item.id}
                        type="button"
                        aria-label={`${unread ? '未读通知' : '已读通知'}：${item.title}`}
                        className={cn(
                          'w-full min-w-0 rounded-[22px] border p-4 text-left transition duration-200 hover:-translate-y-0.5 hover:shadow-[0_16px_36px_rgba(42,31,20,0.12)] dark:hover:shadow-[0_18px_42px_rgba(0,0,0,0.36)]',
                          unread ? 'border-[var(--accent)]/40 bg-[var(--accent-soft)]/80 dark:bg-[var(--accent)]/10' : 'border-border bg-background/70 hover:bg-muted/60',
                        )}
                        onClick={() => markRead(item)}
                      >
                        <div className="flex min-w-0 items-start justify-between gap-3">
                          <div className="min-w-0 flex-1">
                            <div className="flex min-w-0 items-center gap-2">
                              {unread && <span className="h-2 w-2 shrink-0 rounded-full bg-[var(--accent)]" aria-hidden="true" />}
                              <p className="min-w-0 break-words font-black leading-6 text-foreground">{item.title}</p>
                            </div>
                            <p className="mt-1 line-clamp-4 break-words text-sm leading-6 text-muted-foreground sm:line-clamp-3">{item.content}</p>
                          </div>
                          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-muted text-[var(--accent)]">
                            <Send className="h-4 w-4" />
                          </span>
                        </div>
                        <div className="mt-3 grid gap-1.5 border-t border-border/70 pt-3 text-xs text-muted-foreground">
                          {item.businessType && <span className="break-words">来源：{item.businessType === 'FEEDBACK_TICKET' ? '反馈工单' : item.businessType === 'INTERVIEW' ? '面试通知' : item.businessType}</span>}
                          <time dateTime={item.createdAt}>{shortDate(item.createdAt)}</time>
                        </div>
                      </button>
                    )
                  })}
                </div>
              )}
            </div>
          </section>
        </div>,
        document.body,
      )}
    </div>
  )
}
