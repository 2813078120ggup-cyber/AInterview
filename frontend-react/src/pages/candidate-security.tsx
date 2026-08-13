import * as Dialog from '@radix-ui/react-dialog'
import { Activity, AlertCircle, Check, Clock3, Eye, EyeOff, History, KeyRound, Laptop, Loader2, LogOut, MonitorSmartphone, RefreshCw, ShieldCheck, Smartphone, Tablet, Wifi, X } from 'lucide-react'
import type { FormEvent, ReactNode } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AccountSettingsNavigation } from '@/components/account-settings-navigation'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import { clearSession, rotateSessionTokens } from '@/lib/session'

type ChangePasswordResponse = {
  accessToken: string
  refreshToken: string
  sessionBehavior: string
}

type AccountSession = {
  sessionId: string
  current: boolean
  deviceType: 'DESKTOP' | 'MOBILE' | 'TABLET' | 'UNKNOWN'
  browser: string
  operatingSystem: string
  maskedIp: string | null
  createdAt: string
  lastActiveAt: string
  expiresAt: string
}

type SecurityEvent = {
  eventType: string
  result: 'SUCCESS' | 'FAILURE' | 'DENIED'
  summary: string
  maskedIp: string | null
  deviceSummary: string | null
  createdAt: string
}

type PageResult<T> = {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

let inFlightSessionsRequest: Promise<AccountSession[]> | null = null
let inFlightSecurityEventsRequest: Promise<PageResult<SecurityEvent>> | null = null

function fetchAccountSessions() {
  if (!inFlightSessionsRequest) {
    inFlightSessionsRequest = request<AccountSession[]>('/v1/account/sessions')
      .finally(() => { inFlightSessionsRequest = null })
  }
  return inFlightSessionsRequest
}

function fetchSecurityEvents(pageNo = 1) {
  const path = `/v1/account/security-events?pageNo=${pageNo}&pageSize=15`
  if (pageNo !== 1) return request<PageResult<SecurityEvent>>(path)
  if (!inFlightSecurityEventsRequest) {
    inFlightSecurityEventsRequest = request<PageResult<SecurityEvent>>(path)
      .finally(() => { inFlightSecurityEventsRequest = null })
  }
  return inFlightSecurityEventsRequest
}

const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d)[!-~]{8,64}$/
const passwordRule = '8–64 位，须包含至少一个英文字母和一个数字；仅支持半角可打印字符，不允许中文或空格。'
const fieldClass = 'mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 pr-12 text-sm outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20'

export function CandidateSecurity() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [visible, setVisible] = useState({ current: false, next: false, confirm: false })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const passwordValid = useMemo(() => passwordPattern.test(newPassword), [newPassword])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    setSuccess('')
    if (!passwordValid) {
      setError(`新密码不符合规则：${passwordRule}`)
      return
    }
    if (newPassword !== confirmPassword) {
      setError('两次输入的新密码不一致。')
      return
    }
    if (currentPassword === newPassword) {
      setError('新密码不能与当前密码相同。')
      return
    }
    const refreshToken = localStorage.getItem('refresh_token')
    if (!refreshToken) {
      setError('当前登录会话缺少刷新凭据，请退出后重新登录。')
      return
    }
    setBusy(true)
    try {
      const result = await request<ChangePasswordResponse>('/v1/account/password/change', {
        method: 'POST',
        body: JSON.stringify({ currentPassword, newPassword, refreshToken }),
      })
      rotateSessionTokens(result.accessToken, result.refreshToken)
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setSuccess(result.sessionBehavior || '密码已更新。当前设备继续登录，其他设备已退出。')
    } catch (reason) {
      setCurrentPassword('')
      setError(reason instanceof Error ? reason.message : '密码更新失败，新密码尚未保存。')
    } finally {
      setBusy(false)
    }
  }

  return <div className="max-w-5xl space-y-6">
    <header className="flex flex-col gap-3 border-b border-border/70 pb-6">
      <p className="text-xs font-bold uppercase tracking-[.16em] text-[var(--accent)]">Account / Security</p>
      <h1 className="text-balance font-serif text-3xl font-semibold tracking-[-.04em] text-[var(--accent)] sm:text-4xl">账户设置</h1>
      <p className="max-w-2xl text-sm leading-6 text-muted-foreground">管理个人资料、联系方式和登录安全。</p>
    </header>

    <AccountSettingsNavigation />

    <div aria-live="polite" aria-atomic="true">
      {success && <Notice tone="success" icon={<Check className="h-4 w-4" aria-hidden="true" />}>{success}</Notice>}
      {error && <Notice tone="danger" icon={<AlertCircle className="h-4 w-4" aria-hidden="true" />}>{error}</Notice>}
    </div>

    <Card className="overflow-hidden p-0">
      <div className="border-b border-border bg-[var(--surface-soft)] px-5 py-6 sm:px-7">
        <div className="flex items-start gap-3">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><KeyRound className="h-5 w-5" aria-hidden="true" /></span>
          <div><h2 className="text-xl font-bold">更新登录密码</h2><p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">完成后旧 Access Token 和旧 Refresh Token 会失效。当前设备会接收新的登录凭据，其他设备将退出登录。</p></div>
        </div>
      </div>
      <form className="p-5 sm:p-7" onSubmit={event => void submit(event)}>
        <div className="max-w-xl space-y-5">
          <PasswordField id="current-password" label="当前密码" value={currentPassword} visible={visible.current} autoComplete="current-password" disabled={busy} onChange={setCurrentPassword} onToggle={() => setVisible(value => ({ ...value, current: !value.current }))} />
          <PasswordField id="new-password" label="新密码" value={newPassword} visible={visible.next} autoComplete="new-password" disabled={busy} onChange={setNewPassword} onToggle={() => setVisible(value => ({ ...value, next: !value.next }))} describedBy="new-password-rule" />
          <p id="new-password-rule" className="-mt-3 text-xs leading-5 text-muted-foreground">{passwordRule}</p>
          <PasswordField id="confirm-password" label="确认新密码" value={confirmPassword} visible={visible.confirm} autoComplete="new-password" disabled={busy} onChange={setConfirmPassword} onToggle={() => setVisible(value => ({ ...value, confirm: !value.confirm }))} />
        </div>
        <div className="mt-7 flex flex-col gap-3 border-t border-border pt-5 sm:flex-row sm:items-center sm:justify-between">
          <p className="flex items-start gap-2 text-xs leading-5 text-muted-foreground"><ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-[var(--accent)]" aria-hidden="true" />平台不会记录密码、密码强度或密码 Hash。</p>
          <Button type="submit" disabled={busy || !currentPassword || !newPassword || !confirmPassword}>{busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <KeyRound className="h-4 w-4" aria-hidden="true" />}{busy ? '正在更新…' : '更新密码'}</Button>
        </div>
      </form>
    </Card>

    <AccountSecurityEvents />
    <AccountSessions />
  </div>
}

function AccountSessions() {
  const navigate = useNavigate()
  const [sessions, setSessions] = useState<AccountSession[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [pending, setPending] = useState<{ kind: 'one'; session: AccountSession } | { kind: 'others' } | null>(null)
  const [revoking, setRevoking] = useState(false)

  const loadSessions = useCallback(async (manual = false) => {
    if (document.visibilityState === 'hidden') return
    if (manual) setRefreshing(true)
    else setLoading(true)
    setError('')
    try {
      const result = await fetchAccountSessions()
      setSessions(result)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '登录设备暂时无法加载。')
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [])

  useEffect(() => {
    let loaded = false
    let active = true
    const loadWhenVisible = () => {
      if (!loaded && document.visibilityState === 'visible') {
        loaded = true
        void fetchAccountSessions().then(result => {
          if (active) setSessions(result)
        }).catch(reason => {
          if (active) setError(reason instanceof Error ? reason.message : '登录设备暂时无法加载。')
        }).finally(() => {
          if (active) setLoading(false)
        })
      }
    }
    loadWhenVisible()
    document.addEventListener('visibilitychange', loadWhenVisible)
    return () => {
      active = false
      document.removeEventListener('visibilitychange', loadWhenVisible)
    }
  }, [])

  const orderedSessions = useMemo(() => [...sessions].sort((left, right) => Number(right.current) - Number(left.current)), [sessions])
  const otherCount = sessions.filter(session => !session.current).length

  async function confirmRevoke() {
    if (!pending) return
    setRevoking(true)
    setError('')
    setSuccess('')
    try {
      if (pending.kind === 'others') {
        await request<void>('/v1/account/sessions/others', { method: 'DELETE' })
        setSessions(current => current.filter(session => session.current))
        setSuccess('其他设备已退出登录。')
        setPending(null)
        return
      }
      const target = pending.session
      await request<void>(`/v1/account/sessions/${encodeURIComponent(target.sessionId)}`, { method: 'DELETE' })
      if (target.current) {
        clearSession()
        navigate('/login', { replace: true })
        return
      }
      setSessions(current => current.filter(session => session.sessionId !== target.sessionId))
      setSuccess('所选设备已退出登录。')
      setPending(null)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '设备退出失败，原设备列表已保留。')
    } finally {
      setRevoking(false)
    }
  }

  return <Card className="overflow-hidden p-0">
    <div className="flex flex-col gap-4 border-b border-border bg-[var(--surface-soft)] px-5 py-6 sm:flex-row sm:items-start sm:justify-between sm:px-7">
      <div className="flex items-start gap-3">
        <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><MonitorSmartphone className="h-5 w-5" aria-hidden="true" /></span>
        <div><h2 className="text-xl font-bold">登录设备</h2><p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">查看仍可刷新登录凭据的设备。撤销后，该设备已有的访问令牌最多可继续使用到短期过期。</p></div>
      </div>
      <Button type="button" variant="secondary" className="shrink-0" disabled={loading || refreshing || document.visibilityState === 'hidden'} onClick={() => void loadSessions(true)}>
        <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} aria-hidden="true" />手动刷新
      </Button>
    </div>

    <div className="space-y-5 p-5 sm:p-7">
      <div aria-live="polite" aria-atomic="true" className="space-y-3">
        {success && <Notice tone="success" icon={<Check className="h-4 w-4" aria-hidden="true" />}>{success}</Notice>}
        {error && <Notice tone="danger" icon={<AlertCircle className="h-4 w-4" aria-hidden="true" />}>{error} <button type="button" className="font-semibold underline underline-offset-4" onClick={() => void loadSessions(true)}>重试</button></Notice>}
      </div>

      {loading && sessions.length === 0 ? <div className="grid min-h-36 place-items-center rounded-2xl border border-dashed border-border bg-background/55 text-sm text-muted-foreground"><span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />正在读取登录设备…</span></div>
        : orderedSessions.length === 0 ? <div className="rounded-2xl border border-dashed border-border bg-background/55 px-5 py-8 text-center"><MonitorSmartphone className="mx-auto h-6 w-6 text-muted-foreground" aria-hidden="true" /><h3 className="mt-3 font-semibold">暂无可管理的登录设备</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">现有会话可能已经撤销或过期，请重新登录后刷新。</p></div>
          : <div className="space-y-3" aria-label="登录设备列表">{orderedSessions.map(session => <SessionCard key={session.sessionId} session={session} disabled={revoking} onRevoke={() => setPending({ kind: 'one', session })} />)}</div>}

      {orderedSessions.length > 0 && <div className="flex flex-col gap-3 border-t border-border pt-5 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-xs leading-5 text-muted-foreground">{otherCount === 0 ? '目前只有当前设备保持登录。' : `另有 ${otherCount} 台设备保持登录，可一次性退出。`}</p>
        <Button type="button" variant="secondary" disabled={otherCount === 0 || revoking} onClick={() => setPending({ kind: 'others' })}><LogOut className="h-4 w-4" aria-hidden="true" />退出其他设备</Button>
      </div>}
    </div>

    <RevokeSessionDialog pending={pending} busy={revoking} onOpenChange={open => !open && !revoking && setPending(null)} onConfirm={() => void confirmRevoke()} />
  </Card>
}

function SessionCard({ session, disabled, onRevoke }: { session: AccountSession; disabled: boolean; onRevoke: () => void }) {
  const DeviceIcon = session.deviceType === 'MOBILE' ? Smartphone : session.deviceType === 'TABLET' ? Tablet : session.deviceType === 'DESKTOP' ? Laptop : MonitorSmartphone
  return <article className={`rounded-2xl border p-4 sm:p-5 ${session.current ? 'border-[var(--accent)]/45 bg-[var(--accent-soft)]/55' : 'border-border bg-background/60'}`}>
    <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex min-w-0 items-start gap-3">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl border border-border bg-surface text-[var(--accent)]"><DeviceIcon className="h-5 w-5" aria-hidden="true" /></span>
        <div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h3 className="break-words font-semibold">{session.browser} · {session.operatingSystem}</h3>{session.current && <Badge tone="success">当前设备</Badge>}</div><p className="mt-1 text-xs text-muted-foreground">{deviceLabel(session.deviceType)}</p></div>
      </div>
      <Button type="button" variant={session.current ? 'danger' : 'secondary'} className="w-full shrink-0 sm:w-auto" disabled={disabled} onClick={onRevoke}><LogOut className="h-4 w-4" aria-hidden="true" />{session.current ? '退出当前设备' : '退出此设备'}</Button>
    </div>
    <dl className="mt-4 grid gap-3 border-t border-border/70 pt-4 text-xs sm:grid-cols-2 lg:grid-cols-4">
      <SessionMeta icon={<Wifi className="h-3.5 w-3.5" />} label="登录 IP" value={session.maskedIp || '未记录'} />
      <SessionMeta icon={<Clock3 className="h-3.5 w-3.5" />} label="首次登录" value={formatServerTime(session.createdAt)} />
      <SessionMeta icon={<Clock3 className="h-3.5 w-3.5" />} label="最近活动" value={formatServerTime(session.lastActiveAt)} />
      <SessionMeta icon={<ShieldCheck className="h-3.5 w-3.5" />} label="刷新凭据到期" value={formatServerTime(session.expiresAt)} />
    </dl>
  </article>
}

function SessionMeta({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return <div className="min-w-0"><dt className="flex items-center gap-1.5 text-muted-foreground">{icon}{label}</dt><dd className="mt-1 break-words font-medium text-foreground">{value}</dd></div>
}

function AccountSecurityEvents() {
  const [events, setEvents] = useState<SecurityEvent[]>([])
  const [pageNo, setPageNo] = useState(1)
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')

  const applyPage = useCallback((page: PageResult<SecurityEvent>, append: boolean) => {
    setEvents(current => append ? [...current, ...page.records] : page.records)
    setPageNo(page.pageNo)
    setTotal(page.total)
  }, [])

  const loadFirstPage = useCallback(async (manual = false) => {
    if (document.visibilityState === 'hidden') return
    if (manual) setRefreshing(true)
    else setLoading(true)
    setError('')
    try {
      applyPage(await fetchSecurityEvents(1), false)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '安全活动暂时无法加载。')
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [applyPage])

  useEffect(() => {
    let loaded = false
    let active = true
    const loadWhenVisible = () => {
      if (!loaded && document.visibilityState === 'visible') {
        loaded = true
        void fetchSecurityEvents(1).then(page => {
          if (active) applyPage(page, false)
        }).catch(reason => {
          if (active) setError(reason instanceof Error ? reason.message : '安全活动暂时无法加载。')
        }).finally(() => {
          if (active) setLoading(false)
        })
      }
    }
    loadWhenVisible()
    document.addEventListener('visibilitychange', loadWhenVisible)
    return () => {
      active = false
      document.removeEventListener('visibilitychange', loadWhenVisible)
    }
  }, [applyPage])

  async function loadMore() {
    if (loadingMore || events.length >= total || document.visibilityState === 'hidden') return
    setLoadingMore(true)
    setError('')
    try {
      applyPage(await fetchSecurityEvents(pageNo + 1), true)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '更多安全活动加载失败，已有记录已保留。')
    } finally {
      setLoadingMore(false)
    }
  }

  return <Card className="overflow-hidden p-0">
    <div className="flex flex-col gap-4 border-b border-border bg-[var(--surface-soft)] px-5 py-6 sm:flex-row sm:items-start sm:justify-between sm:px-7">
      <div className="flex items-start gap-3">
        <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><History className="h-5 w-5" aria-hidden="true" /></span>
        <div><h2 className="text-xl font-bold">最近安全活动</h2><p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">查看登录、敏感资料变更和设备退出记录。时间与结果均来自服务端审计。</p></div>
      </div>
      <Button type="button" variant="secondary" className="shrink-0" disabled={loading || refreshing || document.visibilityState === 'hidden'} onClick={() => void loadFirstPage(true)}>
        <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} aria-hidden="true" />刷新活动
      </Button>
    </div>

    <div className="space-y-5 p-5 sm:p-7" aria-busy={loading || refreshing || loadingMore}>
      {error && events.length > 0 && <Notice tone="danger" icon={<AlertCircle className="h-4 w-4" aria-hidden="true" />}>{error} <button type="button" className="font-semibold underline underline-offset-4" onClick={() => void loadFirstPage(true)}>重试</button></Notice>}

      {loading && events.length === 0 ? <div className="grid min-h-36 place-items-center rounded-2xl border border-dashed border-border bg-background/55 text-sm text-muted-foreground"><span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />正在读取安全活动…</span></div>
        : error && events.length === 0 ? <div role="alert" className="rounded-2xl border border-[var(--danger-foreground)]/25 bg-[var(--danger)] px-5 py-8 text-center text-[var(--danger-foreground)]"><AlertCircle className="mx-auto h-6 w-6" aria-hidden="true" /><h3 className="mt-3 font-semibold">安全活动加载失败</h3><p className="mt-1 text-sm leading-6">{error}</p><Button type="button" variant="secondary" className="mt-4" onClick={() => void loadFirstPage(true)}><RefreshCw className="h-4 w-4" aria-hidden="true" />重新加载</Button></div>
          : events.length === 0 ? <div className="rounded-2xl border border-dashed border-border bg-background/55 px-5 py-8 text-center"><Activity className="mx-auto h-6 w-6 text-muted-foreground" aria-hidden="true" /><h3 className="mt-3 font-semibold">暂无安全活动</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">完成登录或账户安全操作后，相关记录会显示在这里。</p></div>
          : <ol className="relative space-y-0" aria-label="账户安全活动列表">{events.map((event, index) => <SecurityEventItem key={`${event.createdAt}-${event.eventType}-${index}`} event={event} last={index === events.length - 1} />)}</ol>}

      {events.length > 0 && <div className="flex flex-col gap-3 border-t border-border pt-5 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-xs leading-5 text-muted-foreground">已显示 {events.length} 条，共 {total} 条安全活动。</p>
        {events.length < total && <Button type="button" variant="secondary" disabled={loadingMore || document.visibilityState === 'hidden'} onClick={() => void loadMore()}>{loadingMore ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Clock3 className="h-4 w-4" aria-hidden="true" />}{loadingMore ? '正在加载…' : '加载更多'}</Button>}
      </div>}
    </div>
  </Card>
}

function SecurityEventItem({ event, last }: { event: SecurityEvent; last: boolean }) {
  const status = securityEventStatus(event.result)
  return <li className="relative grid grid-cols-[2.25rem_minmax(0,1fr)] gap-3 pb-5 last:pb-0 sm:grid-cols-[2.75rem_minmax(0,1fr)] sm:gap-4">
    {!last && <span className="absolute bottom-0 left-[1.09375rem] top-9 w-px bg-border sm:left-[1.34375rem] sm:top-10" aria-hidden="true" />}
    <span className={`relative z-10 grid h-9 w-9 place-items-center rounded-full border sm:h-11 sm:w-11 ${status.markerClass}`} aria-hidden="true"><ShieldCheck className="h-4 w-4 sm:h-5 sm:w-5" /></span>
    <article className="min-w-0 rounded-2xl border border-border bg-background/60 p-4 sm:p-5">
      <div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold">{securityEventTitle(event.eventType)}</h3><Badge tone={status.tone}>{status.label}</Badge></div>
      <p className="mt-2 text-sm leading-6 text-muted-foreground">{event.summary}</p>
      <dl className="mt-3 flex flex-wrap gap-x-5 gap-y-2 border-t border-border/70 pt-3 text-xs">
        <div><dt className="sr-only">发生时间</dt><dd className="flex items-center gap-1.5 font-medium text-foreground"><Clock3 className="h-3.5 w-3.5 text-muted-foreground" aria-hidden="true" />{formatServerTime(event.createdAt)}</dd></div>
        <div><dt className="sr-only">设备</dt><dd className="break-words text-muted-foreground">{event.deviceSummary || '未记录设备'}</dd></div>
        <div><dt className="sr-only">IP 地址</dt><dd className="text-muted-foreground">{event.maskedIp || '未记录 IP'}</dd></div>
      </dl>
    </article>
  </li>
}

function securityEventTitle(eventType: string) {
  return ({
    PASSWORD_LOGIN: '密码登录',
    VERIFICATION_CODE_LOGIN: '验证码登录',
    NEW_SESSION: '新会话创建',
    PASSWORD_CHANGED: '修改登录密码',
    PASSWORD_RESET: '重置登录密码',
    PHONE_CHANGED: '修改手机号',
    EMAIL_CHANGED: '修改邮箱',
    AVATAR_CHANGED: '修改头像',
    PROFILE_CHANGED: '修改基本资料',
    SESSION_REVOKED: '退出登录设备',
    OTHER_SESSIONS_REVOKED: '退出其他设备',
    ACCOUNT_STATUS_CHANGED: '账号状态变更',
  } as Record<string, string>)[eventType] || '账户安全活动'
}

function securityEventStatus(result: SecurityEvent['result']): { label: string; tone: 'success' | 'warning' | 'danger'; markerClass: string } {
  if (result === 'SUCCESS') return { label: '成功', tone: 'success', markerClass: 'border-[var(--success-foreground)]/30 bg-[var(--success)] text-[var(--success-foreground)]' }
  if (result === 'DENIED') return { label: '已拒绝', tone: 'warning', markerClass: 'border-[var(--warning-foreground)]/30 bg-[var(--warning)] text-[var(--warning-foreground)]' }
  return { label: '失败', tone: 'danger', markerClass: 'border-[var(--danger-foreground)]/30 bg-[var(--danger)] text-[var(--danger-foreground)]' }
}

function RevokeSessionDialog({ pending, busy, onOpenChange, onConfirm }: { pending: { kind: 'one'; session: AccountSession } | { kind: 'others' } | null; busy: boolean; onOpenChange: (open: boolean) => void; onConfirm: () => void }) {
  const current = pending?.kind === 'one' && pending.session.current
  const title = pending?.kind === 'others' ? '退出所有其他设备？' : current ? '退出当前设备？' : '退出这台设备？'
  const description = pending?.kind === 'others' ? '当前设备会继续登录，其他设备将不能再刷新登录凭据。' : current ? '当前设备的 Refresh Token 将被撤销，并立即清理本机登录状态。' : '该设备将不能再刷新登录凭据，已有 Access Token 最多可使用到短期过期。'
  return <Dialog.Root open={pending !== null} onOpenChange={onOpenChange}><Dialog.Portal><Dialog.Overlay className="fixed inset-0 z-[100] bg-black/55" /><Dialog.Content className="fixed inset-x-3 bottom-3 z-[101] mx-auto max-w-lg rounded-[28px] border border-border bg-surface p-5 shadow-2xl focus:outline-none sm:bottom-auto sm:top-1/2 sm:-translate-y-1/2 sm:p-7"><div className="flex items-start justify-between gap-3"><div><Dialog.Title className="font-serif text-2xl font-semibold tracking-[-.03em]">{title}</Dialog.Title><Dialog.Description className="mt-2 text-sm leading-6 text-muted-foreground">{description}</Dialog.Description></div><Dialog.Close aria-label="关闭设备退出确认" disabled={busy} className="grid h-10 w-10 shrink-0 place-items-center rounded-full text-muted-foreground hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]"><X className="h-5 w-5" /></Dialog.Close></div><div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end"><Dialog.Close asChild><Button type="button" variant="secondary" disabled={busy}>取消</Button></Dialog.Close><Button type="button" variant="danger" disabled={busy} onClick={onConfirm}>{busy ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <LogOut className="h-4 w-4" aria-hidden="true" />}{busy ? '正在退出…' : '确认退出'}</Button></div></Dialog.Content></Dialog.Portal></Dialog.Root>
}

function deviceLabel(deviceType: AccountSession['deviceType']) {
  return ({ DESKTOP: '桌面设备', MOBILE: '移动设备', TABLET: '平板设备', UNKNOWN: '未知设备' } as const)[deviceType] || '未知设备'
}

function formatServerTime(value: string) {
  if (!value) return '未记录'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

function PasswordField({ id, label, value, visible, autoComplete, disabled, describedBy, onChange, onToggle }: { id: string; label: string; value: string; visible: boolean; autoComplete: string; disabled: boolean; describedBy?: string; onChange: (value: string) => void; onToggle: () => void }) {
  return <div><label className="block text-sm font-semibold" htmlFor={id}>{label}</label><div className="relative"><input id={id} type={visible ? 'text' : 'password'} value={value} onChange={event => onChange(event.target.value)} className={fieldClass} autoComplete={autoComplete} minLength={8} maxLength={64} disabled={disabled} aria-describedby={describedBy} required /><button type="button" onClick={onToggle} disabled={disabled} className="absolute right-2 top-[18px] grid h-8 w-8 place-items-center rounded-full text-muted-foreground transition hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]" aria-label={visible ? `隐藏${label}` : `显示${label}`}>{visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}</button></div></div>
}

function Notice({ tone, icon, children }: { tone: 'success' | 'danger'; icon: ReactNode; children: ReactNode }) {
  const classes = tone === 'success' ? 'border-[var(--success-foreground)]/30 bg-[var(--success)] text-[var(--success-foreground)]' : 'border-[var(--danger-foreground)]/30 bg-[var(--danger)] text-[var(--danger-foreground)]'
  return <div role={tone === 'danger' ? 'alert' : 'status'} className={`flex items-start gap-2 rounded-2xl border px-4 py-3 text-sm leading-6 ${classes}`}>{icon}<span>{children}</span></div>
}
