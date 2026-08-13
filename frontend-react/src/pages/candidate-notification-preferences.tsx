import { AlertCircle, BellRing, Check, CircleOff, Loader2, Mail, MessageSquareText, RefreshCw, Save, ShieldCheck, Smartphone } from 'lucide-react'
import type { ReactNode } from 'react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { AccountSettingsNavigation } from '@/components/account-settings-navigation'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ApiError, request } from '@/lib/api'

type ChannelAvailability = {
  siteAvailable: boolean
  emailAvailable: boolean
  emailUnavailableReason: string | null
  smsAvailable: boolean
  smsUnavailableReason: string | null
}

type NotificationPreference = {
  eventType: string
  label: string
  description: string
  group: string
  siteEnabled: boolean
  emailEnabled: boolean
  smsEnabled: boolean
  siteForced: boolean
  emailForced: boolean
  sitePolicyReason: string | null
  emailPolicyReason: string | null
  version: number
}

type NotificationPreferences = {
  channels: ChannelAvailability
  preferences: NotificationPreference[]
}

type Channel = 'siteEnabled' | 'emailEnabled' | 'smsEnabled'

let inFlightPreferencesRequest: Promise<NotificationPreferences> | null = null

function fetchPreferences() {
  if (!inFlightPreferencesRequest) {
    inFlightPreferencesRequest = request<NotificationPreferences>('/v1/account/notification-preferences')
      .finally(() => { inFlightPreferencesRequest = null })
  }
  return inFlightPreferencesRequest
}

export function CandidateNotificationPreferences() {
  const [confirmed, setConfirmed] = useState<NotificationPreferences | null>(null)
  const [draft, setDraft] = useState<NotificationPreferences | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [conflict, setConflict] = useState(false)

  const load = useCallback(async () => {
    if (document.visibilityState === 'hidden') return
    setLoading(true)
    setError('')
    setConflict(false)
    try {
      const result = await fetchPreferences()
      setConfirmed(result)
      setDraft(result)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '通知偏好暂时无法加载。')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let active = true
    let loaded = false
    const loadWhenVisible = () => {
      if (loaded || document.visibilityState !== 'visible') return
      loaded = true
      void fetchPreferences().then(result => {
        if (!active) return
        setConfirmed(result)
        setDraft(result)
      }).catch(reason => {
        if (active) setError(reason instanceof Error ? reason.message : '通知偏好暂时无法加载。')
      }).finally(() => {
        if (active) setLoading(false)
      })
    }
    loadWhenVisible()
    document.addEventListener('visibilitychange', loadWhenVisible)
    return () => {
      active = false
      document.removeEventListener('visibilitychange', loadWhenVisible)
    }
  }, [])

  const dirty = useMemo(() => confirmed !== null && draft !== null
    && JSON.stringify(confirmed.preferences) !== JSON.stringify(draft.preferences), [confirmed, draft])

  const groups = useMemo(() => {
    const result = new Map<string, NotificationPreference[]>()
    draft?.preferences.forEach(item => result.set(item.group, [...(result.get(item.group) ?? []), item]))
    return [...result.entries()]
  }, [draft])

  function toggle(eventType: string, channel: Channel) {
    setSuccess('')
    setConflict(false)
    setDraft(current => current ? {
      ...current,
      preferences: current.preferences.map(item => item.eventType === eventType
        ? { ...item, [channel]: !item[channel] }
        : item),
    } : current)
  }

  async function save() {
    if (!draft || saving || !dirty) return
    setSaving(true)
    setError('')
    setSuccess('')
    setConflict(false)
    try {
      const result = await request<NotificationPreferences>('/v1/account/notification-preferences', {
        method: 'PUT',
        body: JSON.stringify({
          preferences: draft.preferences.map(item => ({
            eventType: item.eventType,
            siteEnabled: item.siteEnabled,
            emailEnabled: item.emailEnabled,
            smsEnabled: item.smsEnabled,
            version: item.version,
          })),
        }),
      })
      setConfirmed(result)
      setDraft(result)
      setSuccess('通知偏好已保存，后续通知将按服务端确认的设置发送。')
    } catch (reason) {
      setDraft(confirmed)
      if (reason instanceof ApiError && reason.status === 409) {
        setConflict(true)
        setError('通知偏好已在其他会话中更新。当前未保存改动已撤销，请重新加载后再修改。')
      } else {
        setError(reason instanceof Error ? `${reason.message} 当前未保存改动已恢复为最近一次服务端确认值。` : '保存失败，已恢复最近一次服务端确认值。')
      }
    } finally {
      setSaving(false)
    }
  }

  return <div className="max-w-5xl space-y-6">
    <header className="flex flex-col gap-3 border-b border-border/70 pb-6">
      <p className="text-xs font-bold uppercase tracking-[.16em] text-[var(--accent)]">Account / Notifications</p>
      <h1 className="text-balance font-serif text-3xl font-semibold tracking-[-.04em] text-[var(--accent)] sm:text-4xl">账户设置</h1>
      <p className="max-w-2xl text-sm leading-6 text-muted-foreground">管理个人资料、联系方式和登录安全。</p>
    </header>

    <AccountSettingsNavigation />

    <div aria-live="polite" aria-atomic="true" className="space-y-3">
      {success && <Notice tone="success" icon={<Check className="h-4 w-4" aria-hidden="true" />}>{success}</Notice>}
      {error && <Notice tone="danger" icon={<AlertCircle className="h-4 w-4" aria-hidden="true" />}>{error}</Notice>}
    </div>

    {loading && !draft ? <LoadingState /> : !draft ? <FullError onRetry={() => void load()} /> : <>
      <ChannelSummary channels={draft.channels} />

      <div className="space-y-8">
        {groups.map(([group, preferences]) => <section key={group} aria-labelledby={`notification-group-${group}`}>
          <div className="mb-3 flex items-end justify-between gap-3 px-1">
            <div><p className="text-xs font-bold uppercase tracking-[.14em] text-[var(--accent)]">Delivery rules</p><h2 id={`notification-group-${group}`} className="mt-1 font-serif text-2xl font-semibold tracking-[-.03em]">{group}</h2></div>
            <span className="text-xs text-muted-foreground">{preferences.length} 类事件</span>
          </div>
          <div className="space-y-3">{preferences.map(item => <PreferenceCard key={item.eventType} item={item} channels={draft.channels} saving={saving} onToggle={toggle} />)}</div>
        </section>)}
      </div>

      <Card className="sticky bottom-3 z-20 flex flex-col gap-4 border-border/95 bg-surface/95 p-4 backdrop-blur sm:flex-row sm:items-center sm:justify-between sm:p-5">
        <div className="min-w-0"><p className="font-semibold">{dirty ? '存在未保存的通知偏好' : '当前偏好已与服务端同步'}</p><p className="mt-1 text-xs leading-5 text-muted-foreground">通知中心保持现有行为；关闭某个可选渠道后，服务端将不再通过该渠道发送对应事件。</p></div>
        <div className="flex shrink-0 flex-col gap-2 sm:flex-row">
          {conflict && <Button type="button" variant="secondary" disabled={saving || loading} onClick={() => void load()}><RefreshCw className="h-4 w-4" aria-hidden="true" />重新加载</Button>}
          <Button type="button" disabled={saving || !dirty} onClick={() => void save()}>{saving ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Save className="h-4 w-4" aria-hidden="true" />}{saving ? '正在保存…' : '保存通知偏好'}</Button>
        </div>
      </Card>
    </>}
  </div>
}

function ChannelSummary({ channels }: { channels: ChannelAvailability }) {
  return <Card className="overflow-hidden p-0">
    <div className="border-b border-border bg-[var(--surface-soft)] px-5 py-5 sm:px-7"><div className="flex items-start gap-3"><span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><BellRing className="h-5 w-5" aria-hidden="true" /></span><div><h2 className="text-xl font-bold">通知送达渠道</h2><p className="mt-1 text-sm leading-6 text-muted-foreground">站内信始终可用；邮件和短信必须同时满足联系方式已验证且渠道 Provider 可用。</p></div></div></div>
    <div className="grid gap-px bg-border sm:grid-cols-3">
      <ChannelStatus icon={<MessageSquareText className="h-4 w-4" />} label="站内信" available={channels.siteAvailable} reason="通过 AInterview 通知中心送达" />
      <ChannelStatus icon={<Mail className="h-4 w-4" />} label="邮件" available={channels.emailAvailable} reason={channels.emailAvailable ? '邮箱已验证，邮件渠道可用' : channels.emailUnavailableReason ?? '邮件渠道暂不可用'} />
      <ChannelStatus icon={<Smartphone className="h-4 w-4" />} label="短信" available={channels.smsAvailable} reason={channels.smsAvailable ? '手机号已验证，短信渠道可用' : channels.smsUnavailableReason ?? '短信渠道暂不可用'} />
    </div>
  </Card>
}

function ChannelStatus({ icon, label, available, reason }: { icon: ReactNode; label: string; available: boolean; reason: string }) {
  return <div className="bg-surface px-5 py-4 sm:px-6"><div className="flex items-center justify-between gap-2"><span className="flex items-center gap-2 font-semibold">{icon}{label}</span><Badge tone={available ? 'success' : 'warning'}>{available ? '可用' : '暂不可用'}</Badge></div><p className="mt-2 text-xs leading-5 text-muted-foreground">{reason}</p></div>
}

function PreferenceCard({ item, channels, saving, onToggle }: { item: NotificationPreference; channels: ChannelAvailability; saving: boolean; onToggle: (eventType: string, channel: Channel) => void }) {
  return <article className="overflow-hidden rounded-[24px] border border-border/90 bg-surface shadow-[0_1px_2px_rgba(20,18,17,.04)]">
    <div className="flex flex-col gap-3 px-5 py-5 sm:px-6"><div className="flex flex-wrap items-center gap-2"><h3 className="text-lg font-bold">{item.label}</h3>{item.siteForced && <Badge tone="info">关键通知</Badge>}</div><p className="max-w-3xl text-sm leading-6 text-muted-foreground">{item.description}</p></div>
    <div className="grid gap-px border-t border-border bg-border md:grid-cols-3">
      <ChannelToggle icon={<MessageSquareText className="h-4 w-4" />} label="站内信" checked={item.siteEnabled} disabled={saving || item.siteForced} reason={item.sitePolicyReason} onChange={() => onToggle(item.eventType, 'siteEnabled')} />
      <ChannelToggle icon={<Mail className="h-4 w-4" />} label="邮件" checked={item.emailEnabled} disabled={saving || !channels.emailAvailable || item.emailForced} reason={!channels.emailAvailable ? channels.emailUnavailableReason : item.emailPolicyReason} onChange={() => onToggle(item.eventType, 'emailEnabled')} />
      <ChannelToggle icon={<Smartphone className="h-4 w-4" />} label="短信" checked={item.smsEnabled} disabled={saving || !channels.smsAvailable} reason={!channels.smsAvailable ? channels.smsUnavailableReason : null} onChange={() => onToggle(item.eventType, 'smsEnabled')} />
    </div>
  </article>
}

function ChannelToggle({ icon, label, checked, disabled, reason, onChange }: { icon: ReactNode; label: string; checked: boolean; disabled: boolean; reason: string | null; onChange: () => void }) {
  return <div className="min-w-0 bg-surface px-5 py-4 sm:px-6"><div className="flex items-center justify-between gap-4"><span className="flex min-w-0 items-center gap-2 text-sm font-semibold">{icon}{label}</span><button type="button" role="switch" aria-checked={checked} aria-label={`${label}${checked ? '已开启' : '已关闭'}`} disabled={disabled} onClick={onChange} onKeyDown={event => {
    if (event.key !== 'Enter' && event.key !== ' ') return
    event.preventDefault()
    onChange()
  }} className={`relative h-7 w-12 shrink-0 rounded-full border transition-colors duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] focus-visible:ring-offset-2 focus-visible:ring-offset-surface disabled:cursor-not-allowed disabled:opacity-65 ${checked ? 'border-[var(--accent)] bg-[var(--accent)]' : 'border-border bg-muted'}`}><span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow-sm transition-transform duration-200 ${checked ? 'translate-x-5' : 'translate-x-0.5'}`} /></button></div>{reason && <p className="mt-2 flex items-start gap-1.5 text-xs leading-5 text-muted-foreground">{checked ? <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[var(--accent)]" aria-hidden="true" /> : <CircleOff className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />}{reason}</p>}</div>
}

function LoadingState() {
  return <div className="grid min-h-72 place-items-center rounded-[24px] border border-dashed border-border bg-surface/60 text-sm text-muted-foreground"><span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />正在读取服务端通知偏好…</span></div>
}

function FullError({ onRetry }: { onRetry: () => void }) {
  return <Card className="grid min-h-72 place-items-center text-center"><div><AlertCircle className="mx-auto h-7 w-7 text-[var(--danger-foreground)]" aria-hidden="true" /><h2 className="mt-3 text-lg font-bold">无法读取通知偏好</h2><p className="mt-2 text-sm text-muted-foreground">页面没有使用本地假数据，请重新连接服务端后重试。</p><Button type="button" variant="secondary" className="mt-5" onClick={onRetry}><RefreshCw className="h-4 w-4" aria-hidden="true" />重新加载</Button></div></Card>
}

function Notice({ tone, icon, children }: { tone: 'success' | 'danger'; icon: ReactNode; children: ReactNode }) {
  const classes = tone === 'success' ? 'border-[var(--success-foreground)]/30 bg-[var(--success)] text-[var(--success-foreground)]' : 'border-[var(--danger-foreground)]/30 bg-[var(--danger)] text-[var(--danger-foreground)]'
  return <div role={tone === 'danger' ? 'alert' : 'status'} className={`flex items-start gap-2 rounded-2xl border px-4 py-3 text-sm leading-6 ${classes}`}>{icon}<span>{children}</span></div>
}
