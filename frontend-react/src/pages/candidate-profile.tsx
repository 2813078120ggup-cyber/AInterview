import * as Dialog from '@radix-ui/react-dialog'
import { AlertCircle, Check, Clock3, ImagePlus, KeyRound, Loader2, Mail, Phone, Save, ShieldCheck, Trash2, Upload, UserRound, X } from 'lucide-react'
import type { FormEvent, ReactNode } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useBeforeUnload, useLocation } from 'react-router-dom'
import { AdminConfirmDialog } from '@/components/admin-confirm-dialog'
import { AccountSettingsNavigation } from '@/components/account-settings-navigation'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ApiError, request, requestBlob, upload } from '@/lib/api'
import { cacheAvatarBlob, readCachedAvatar, removeCachedAvatar } from '@/lib/avatar-cache'
import { formatDateTime } from '@/lib/recruitment'
import { profile, rotateSessionTokens, updateLocalAvatar, updateLocalProfile } from '@/lib/session'

type AccountProfile = {
  id: string
  username: string
  realName: string
  accountType: string
  accountStatus: number
  avatarAvailable: boolean
  email?: string | null
  emailMasked?: string | null
  emailVerified: boolean
  phone?: string | null
  phoneMasked?: string | null
  phoneVerified: boolean
  availableLoginMethods: string[]
  lastLoginAt?: string | null
  createdAt?: string | null
  version: number
}

type PageState = 'loading' | 'ready' | 'full-error'
type SaveState = 'idle' | 'saving' | 'success' | 'conflict'
type AvatarAction = 'idle' | 'uploading' | 'deleting'
type ContactChannel = 'phone' | 'email'
type ContactBusy = 'sending' | 'saving' | null

type ChangeCodeResponse = { cooldownSeconds: number; expiresInSeconds: number }
type ContactChangeResponse = { profile: AccountProfile; accessToken: string; refreshToken: string }

const MAX_AVATAR_BYTES = 2 * 1024 * 1024
const AVATAR_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const AVATAR_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp']

function errorMessage(reason: unknown, fallback = '账户资料暂时无法读取，请稍后重试。') {
  return reason instanceof Error && reason.message ? reason.message : fallback
}

function accountTypeLabel(value: string) {
  if (value === 'CANDIDATE') return '候选人账号'
  if (value === 'COMPANY') return '企业账号'
  if (value === 'ADMIN') return '管理员账号'
  return '平台账号'
}

function loginMethodLabel(value: string) {
  if (value === 'PASSWORD') return '账号密码'
  if (value === 'SMS') return '短信验证码'
  if (value === 'EMAIL') return '邮箱验证码'
  return value
}

export function CandidateProfile() {
  const location = useLocation()
  const localProfile = profile()
  const [account, setAccount] = useState<AccountProfile | null>(null)
  const [draftName, setDraftName] = useState(localProfile?.realName ?? '')
  const [pageState, setPageState] = useState<PageState>('loading')
  const [loadingError, setLoadingError] = useState('')
  const [saveError, setSaveError] = useState('')
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [avatarUrl, setAvatarUrl] = useState<string | null>(() => readCachedAvatar(localProfile?.id))
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string | null>(null)
  const [avatarFile, setAvatarFile] = useState<File | null>(null)
  const [avatarLoading, setAvatarLoading] = useState(false)
  const [avatarAction, setAvatarAction] = useState<AvatarAction>('idle')
  const [avatarError, setAvatarError] = useState('')
  const [avatarSuccess, setAvatarSuccess] = useState('')
  const [confirmAvatarDelete, setConfirmAvatarDelete] = useState(false)
  const [contactDialog, setContactDialog] = useState<ContactChannel | null>(null)
  const [contactTarget, setContactTarget] = useState('')
  const [contactCode, setContactCode] = useState('')
  const [contactPassword, setContactPassword] = useState('')
  const [contactError, setContactError] = useState('')
  const [contactStatus, setContactStatus] = useState('')
  const [contactUnavailable, setContactUnavailable] = useState(false)
  const [contactBusy, setContactBusy] = useState<ContactBusy>(null)
  const [contactNow, setContactNow] = useState(() => Date.now())
  const [contactCooldownUntil, setContactCooldownUntil] = useState<Record<ContactChannel, number>>({ phone: 0, email: 0 })
  const fileInputRef = useRef<HTMLInputElement>(null)
  const objectUrlsRef = useRef<Set<string>>(new Set())
  const avatarRequestIdRef = useRef(0)
  const mountedRef = useRef(true)

  const releaseObjectUrl = useCallback((url: string | null) => {
    if (!url) return
    URL.revokeObjectURL(url)
    objectUrlsRef.current.delete(url)
  }, [])

  useEffect(() => () => {
    mountedRef.current = false
    objectUrlsRef.current.forEach(url => URL.revokeObjectURL(url))
    objectUrlsRef.current.clear()
  }, [])

  useEffect(() => {
    if (!contactDialog) return
    const updateNow = () => {
      if (document.visibilityState === 'visible') setContactNow(Date.now())
    }
    const interval = window.setInterval(updateNow, 1000)
    document.addEventListener('visibilitychange', updateNow)
    updateNow()
    return () => {
      window.clearInterval(interval)
      document.removeEventListener('visibilitychange', updateNow)
    }
  }, [contactDialog])

  const loadAvatar = useCallback(async (available: boolean) => {
    const requestId = ++avatarRequestIdRef.current
    setAvatarError('')
    if (!available) {
      removeCachedAvatar(localProfile?.id)
      setAvatarUrl(previous => {
        releaseObjectUrl(previous)
        return null
      })
      setAvatarLoading(false)
      return
    }
    setAvatarLoading(true)
    try {
      const blob = await requestBlob('/v1/account/avatar/content')
      const nextUrl = URL.createObjectURL(blob)
      objectUrlsRef.current.add(nextUrl)
      if (!mountedRef.current || requestId !== avatarRequestIdRef.current) {
        releaseObjectUrl(nextUrl)
        return
      }
      setAvatarUrl(previous => {
        releaseObjectUrl(previous)
        return nextUrl
      })
      if (localProfile?.id) void cacheAvatarBlob(localProfile.id, blob)
    } catch (reason) {
      if (requestId === avatarRequestIdRef.current) setAvatarError(errorMessage(reason, '头像暂时无法加载，账户资料仍可用。'))
    } finally {
      if (requestId === avatarRequestIdRef.current) setAvatarLoading(false)
    }
  }, [localProfile?.id, releaseObjectUrl])

  const load = useCallback(async () => {
    setPageState('loading')
    setLoadingError('')
    try {
      const next = await request<AccountProfile>('/v1/account/profile')
      setAccount(next)
      setDraftName(next.realName)
      updateLocalProfile({ realName: next.realName, avatarAvailable: next.avatarAvailable })
      setPageState('ready')
      setSaveError('')
      setSaveState('idle')
      void loadAvatar(next.avatarAvailable)
    } catch (reason) {
      setLoadingError(errorMessage(reason))
      setPageState('full-error')
    }
  }, [loadAvatar])

  useEffect(() => { void load() }, [load])

  const normalizedDraftName = draftName.trim()
  const hasUnsavedChanges = Boolean(account && normalizedDraftName !== account.realName)
  useBeforeUnload(event => {
    if (hasUnsavedChanges) event.preventDefault()
  }, { capture: true })

  const missingContact = useMemo(() => {
    if (!account) return []
    return [!account.phone && '手机号', !account.email && '邮箱'].filter(Boolean) as string[]
  }, [account])

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!account || saveState === 'saving') return
    if (!normalizedDraftName) {
      setSaveError('姓名不能全为空白。')
      setSaveState('idle')
      return
    }
    if (normalizedDraftName.length > 64) {
      setSaveError('姓名不能超过 64 个字符。')
      setSaveState('idle')
      return
    }
    setSaveState('saving')
    setSaveError('')
    try {
      const next = await request<AccountProfile>('/v1/account/profile', {
        method: 'PUT',
        body: JSON.stringify({ realName: draftName, version: account.version }),
      })
      setAccount(next)
      setDraftName(next.realName)
      updateLocalProfile({ realName: next.realName })
      setSaveState('success')
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 409) {
        setSaveState('conflict')
        setSaveError('服务端资料已经变化。为避免覆盖最新内容，请重新加载后再保存。')
      } else {
        setSaveState('idle')
        setSaveError(errorMessage(reason))
      }
    }
  }

  function chooseAvatar(file: File | undefined) {
    if (!file) return
    setAvatarError('')
    setAvatarSuccess('')
    const extension = file.name.toLowerCase().split('.').pop() ?? ''
    if (file.size > MAX_AVATAR_BYTES) {
      setAvatarError('头像不能超过 2MB。')
      return
    }
    if (!AVATAR_EXTENSIONS.includes(extension) || !AVATAR_MIME_TYPES.includes(file.type)) {
      setAvatarError('请选择 JPEG、PNG 或 WebP 图片。')
      return
    }
    const nextPreviewUrl = URL.createObjectURL(file)
    objectUrlsRef.current.add(nextPreviewUrl)
    setAvatarPreviewUrl(previous => {
      releaseObjectUrl(previous)
      return nextPreviewUrl
    })
    setAvatarFile(file)
  }

  async function uploadAvatar() {
    if (!avatarFile || avatarAction !== 'idle') return
    setAvatarAction('uploading')
    setAvatarError('')
    setAvatarSuccess('')
    try {
      const formData = new FormData()
      formData.append('file', avatarFile)
      const next = await upload<AccountProfile>('/v1/account/avatar', formData)
      setAccount(next)
      updateLocalAvatar(next.avatarAvailable)
      setAvatarFile(null)
      setAvatarPreviewUrl(previous => {
        releaseObjectUrl(previous)
        return null
      })
      setAvatarSuccess('头像已更新。')
      void loadAvatar(next.avatarAvailable)
    } catch (reason) {
      setAvatarError(errorMessage(reason, '头像上传失败，请保留当前头像后重试。'))
    } finally {
      setAvatarAction('idle')
    }
  }

  async function deleteAvatar() {
    if (!account?.avatarAvailable || avatarAction !== 'idle') return
    setAvatarAction('deleting')
    avatarRequestIdRef.current += 1
    setAvatarError('')
    setAvatarSuccess('')
    try {
      const next = await request<AccountProfile>('/v1/account/avatar', { method: 'DELETE' })
      setAccount(next)
      updateLocalAvatar(next.avatarAvailable)
      setAvatarFile(null)
      setAvatarPreviewUrl(previous => {
        releaseObjectUrl(previous)
        return null
      })
      setAvatarUrl(previous => {
        releaseObjectUrl(previous)
        return null
      })
      setAvatarSuccess('头像已删除。')
      setConfirmAvatarDelete(false)
    } catch (reason) {
      setAvatarError(errorMessage(reason, '头像删除失败，当前头像未改变。'))
    } finally {
      setAvatarAction('idle')
    }
  }

  function openContactDialog(channel: ContactChannel) {
    if (!account) return
    setContactDialog(channel)
    setContactTarget(channel === 'phone' ? account.phone ?? '' : account.email ?? '')
    setContactCode('')
    setContactPassword('')
    setContactError('')
    setContactStatus('')
    setContactUnavailable(false)
  }

  function closeContactDialog(open: boolean) {
    if (contactBusy) return
    if (!open) setContactDialog(null)
  }

  const contactCooldownRemaining = contactDialog
    ? Math.max(0, Math.ceil((contactCooldownUntil[contactDialog] - contactNow) / 1000))
    : 0

  async function sendContactCode() {
    if (!contactDialog || contactBusy) return
    setContactBusy('sending')
    setContactError('')
    setContactStatus('')
    setContactUnavailable(false)
    try {
      const next = await request<ChangeCodeResponse>(`/v1/account/${contactDialog}/code`, {
        method: 'POST',
        body: JSON.stringify({ target: contactTarget }),
      })
      setContactCooldownUntil(previous => ({ ...previous, [contactDialog]: Date.now() + next.cooldownSeconds * 1000 }))
      setContactStatus(`验证码已发送，有效期约 ${Math.ceil(next.expiresInSeconds / 60)} 分钟。`)
    } catch (reason) {
      setContactUnavailable(reason instanceof ApiError && reason.status === 503)
      setContactError(reason instanceof ApiError && reason.status === 503 ? '当前渠道暂不可用，请稍后重试。' : errorMessage(reason, '验证码发送失败，请稍后重试。'))
    } finally {
      setContactBusy(null)
    }
  }

  async function saveContact() {
    if (!contactDialog || !account || contactBusy) return
    const refreshToken = localStorage.getItem('refresh_token')
    if (!refreshToken) {
      setContactError('当前登录状态已失效，请重新登录后再试。')
      return
    }
    setContactBusy('saving')
    setContactError('')
    setContactStatus('')
    try {
      const next = await request<ContactChangeResponse>(`/v1/account/${contactDialog}`, {
        method: 'PUT',
        body: JSON.stringify({ target: contactTarget, verificationCode: contactCode, currentPassword: contactPassword, refreshToken, version: account.version }),
      })
      rotateSessionTokens(next.accessToken, next.refreshToken)
      setAccount(next.profile)
      setDraftName(next.profile.realName)
      setContactDialog(null)
      setContactStatus('联系方式已更新，当前设备会话已安全轮换。')
    } catch (reason) {
      setContactError(reason instanceof ApiError && reason.status === 409 ? '该联系方式不可用，请更换后重试。' : errorMessage(reason, '联系方式更新失败，原联系方式未改变。'))
    } finally {
      setContactBusy(null)
    }
  }

  const avatarDisplayUrl = avatarPreviewUrl ?? avatarUrl
  const avatarBusy = avatarAction !== 'idle'

  if (pageState === 'loading') {
    return <div className="grid min-h-72 place-items-center rounded-[24px] border border-border bg-surface px-6 text-center text-sm text-muted-foreground"><div><Loader2 className="mx-auto h-6 w-6 animate-spin text-[var(--accent)]" aria-hidden="true" /><p className="mt-3">正在读取账户资料…</p><p className="mt-1 text-xs">以服务端资料为准，请稍候。</p></div></div>
  }

  if (pageState === 'full-error') {
    return <Card className="grid min-h-72 place-items-center p-6 text-center"><div className="max-w-md"><AlertCircle className="mx-auto h-8 w-8 text-[var(--danger-foreground)]" aria-hidden="true" /><h1 className="mt-4 text-xl font-bold">账户资料暂时不可用</h1><p role="alert" className="mt-2 text-sm leading-6 text-muted-foreground">{loadingError}</p><Button type="button" variant="secondary" className="mt-5" onClick={() => void load()}>重新加载</Button></div></Card>
  }

  if (!account) return null

  return <div className="max-w-5xl space-y-6">
    <header className="flex flex-col gap-3 border-b border-border/70 pb-6">
      <p className="text-xs font-bold uppercase tracking-[.16em] text-[var(--accent)]">Account / Profile</p>
      <h1 className="text-balance font-serif text-[var(--accent)] text-3xl font-semibold tracking-[-.04em] sm:text-4xl">账户设置</h1>
      <p className="max-w-2xl text-sm leading-6 text-muted-foreground">管理个人资料、联系方式和登录安全。</p>
    </header>

    <AccountSettingsNavigation />

    <div aria-live="polite" aria-atomic="true" className="space-y-3">
      {saveState === 'success' && <Notice tone="success" icon={<Check className="h-4 w-4" aria-hidden="true" />}>资料已保存，当前页面已同步最新版本。</Notice>}
      {saveState === 'conflict' && <Notice tone="warning" icon={<AlertCircle className="h-4 w-4" aria-hidden="true" />}>{saveError}<Button type="button" variant="ghost" className="ml-2 h-8 px-2 text-xs" onClick={() => void load()}>重新加载</Button></Notice>}
      {saveState === 'idle' && saveError && <Notice tone="danger" icon={<AlertCircle className="h-4 w-4" aria-hidden="true" />}>{saveError}</Notice>}
      {missingContact.length > 0 && <Notice tone="info" icon={<ShieldCheck className="h-4 w-4" aria-hidden="true" />}>联系方式未完整设置：{missingContact.join('、')}。可在下方完成绑定。</Notice>}
      {!contactDialog && contactStatus && <Notice tone="success" icon={<Check className="h-4 w-4" aria-hidden="true" />}>{contactStatus}</Notice>}
    </div>

    <Card className="overflow-hidden p-0">
      <div className="border-b border-border bg-[var(--surface-soft)] px-5 py-6 sm:px-7 sm:py-8">
        <div className="flex min-w-0 flex-col gap-5 sm:flex-row sm:items-center sm:gap-6">
          <div className="grid h-24 w-24 shrink-0 place-items-center overflow-hidden rounded-[24px] border border-[var(--accent)]/20 bg-[var(--accent-soft)] text-[var(--accent)] sm:h-28 sm:w-28" aria-label={account.avatarAvailable ? '已有头像' : '暂无头像'}>
            {avatarDisplayUrl ? <img src={avatarDisplayUrl} alt={`${account.realName || '候选人'}的头像`} className="h-full w-full object-cover" /> : <UserRound className="h-9 w-9" aria-hidden="true" />}
          </div>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="max-w-full break-words text-2xl font-bold tracking-[-.03em]">{account.realName || '未设置姓名'}</h2>
              <Badge tone="info">{accountTypeLabel(account.accountType)}</Badge>
            </div>
            <p className="mt-2 break-all text-sm text-muted-foreground">@{account.username}</p>
            <p className="mt-3 flex items-center gap-2 text-xs text-muted-foreground"><span className="h-2 w-2 rounded-full bg-emerald-500" aria-hidden="true" />账号状态：{account.accountStatus === 1 ? '正常' : '已停用'}</p>
          </div>
        </div>
        <div className="mt-6 flex flex-col gap-3 border-t border-border/70 pt-5 sm:flex-row sm:items-center sm:justify-between">
          <div className="min-w-0">
            <p className="text-sm font-semibold">头像</p>
            <p className="mt-1 text-xs leading-5 text-muted-foreground">JPEG、PNG 或 WebP，最大 2MB。预览仅用于确认视觉效果，不会在服务端永久裁切。</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <input ref={fileInputRef} type="file" accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp" className="sr-only" aria-label="选择头像文件" onChange={event => { chooseAvatar(event.target.files?.[0]); event.currentTarget.value = '' }} />
            <Button type="button" variant="secondary" disabled={avatarBusy} onClick={() => fileInputRef.current?.click()}><ImagePlus className="h-4 w-4" aria-hidden="true" />{account.avatarAvailable ? '替换头像' : '选择头像'}</Button>
            {avatarFile && <Button type="button" disabled={avatarBusy} onClick={() => void uploadAvatar()}><Upload className="h-4 w-4" aria-hidden="true" />{avatarAction === 'uploading' ? '上传中…' : '上传头像'}</Button>}
            {account.avatarAvailable && <Button type="button" variant="danger" disabled={avatarBusy} onClick={() => setConfirmAvatarDelete(true)}><Trash2 className="h-4 w-4" aria-hidden="true" />删除头像</Button>}
          </div>
        </div>
        {avatarLoading && <p className="mt-3 text-xs text-muted-foreground" role="status">正在加载头像…</p>}
        {avatarError && <div className="mt-4"><Notice tone="danger" icon={<AlertCircle className="h-4 w-4" aria-hidden="true" />}>{avatarError}</Notice></div>}
        {avatarSuccess && <div className="mt-4"><Notice tone="success" icon={<Check className="h-4 w-4" aria-hidden="true" />}>{avatarSuccess}</Notice></div>}
      </div>
      <div className="grid gap-0 divide-y divide-border sm:grid-cols-2 sm:divide-x sm:divide-y-0">
        <InfoRow label="头像" value={account.avatarAvailable ? '已设置' : '暂无头像'} />
        <InfoRow label="账户类型" value={accountTypeLabel(account.accountType)} />
      </div>
    </Card>

    <form className="space-y-6" onSubmit={event => void save(event)}>
      <Card className="p-5 sm:p-7">
        <div className="flex items-start gap-3"><span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><UserRound className="h-5 w-5" aria-hidden="true" /></span><div><h2 className="text-lg font-bold">个人资料</h2><p className="mt-1 text-sm leading-6 text-muted-foreground">姓名会用于工作台、面试记录和需要展示本人身份的业务场景。</p></div></div>
        <div className="mt-6 grid gap-5 md:grid-cols-2">
          <label className="block text-sm font-semibold md:col-span-2" htmlFor="account-real-name">姓名<input id="account-real-name" name="realName" autoComplete="name" maxLength={64} value={draftName} onChange={event => { setDraftName(event.target.value); setSaveState('idle'); setSaveError('') }} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 text-sm font-normal outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20" aria-describedby="account-real-name-help" /><span id="account-real-name-help" className="mt-2 block text-xs font-normal text-muted-foreground">1–64 个字符，保存时会自动去除首尾空格。</span></label>
          <ReadOnlyField label="用户名" value={account.username} longValue />
          <ReadOnlyField label="资料版本" value={`v${account.version}`} />
        </div>
        <div className="mt-6 flex flex-col gap-3 border-t border-border pt-5 sm:flex-row sm:items-center sm:justify-between"><p className="text-xs text-muted-foreground">{hasUnsavedChanges ? '有未保存修改' : '资料以服务端返回的版本为准'}</p><Button type="submit" disabled={saveState === 'saving' || !hasUnsavedChanges}><Save className="h-4 w-4" aria-hidden="true" />{saveState === 'saving' ? '保存中…' : '保存资料'}</Button></div>
      </Card>
    </form>

    <Card className="p-5 sm:p-7">
      <div className="flex items-start gap-3"><span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[var(--info)] text-[var(--info-foreground)]"><ShieldCheck className="h-5 w-5" aria-hidden="true" /></span><div><h2 className="text-lg font-bold">联系方式与登录安全</h2><p className="mt-1 text-sm leading-6 text-muted-foreground">联系方式只显示脱敏结果；验证状态和登录方式由服务端资料决定。</p></div></div>
      <div className="mt-6 grid gap-3 md:grid-cols-2">
        <ContactRow icon={<Phone className="h-4 w-4" aria-hidden="true" />} label="手机号" value={account.phoneMasked} verified={account.phoneVerified} action={<Button type="button" variant="ghost" className="h-8 px-2 text-xs" onClick={() => openContactDialog('phone')}>{account.phone ? (account.phoneVerified ? '更换' : '验证') : '绑定'}</Button>} />
        <ContactRow icon={<Mail className="h-4 w-4" aria-hidden="true" />} label="邮箱" value={account.emailMasked} verified={account.emailVerified} action={<Button type="button" variant="ghost" className="h-8 px-2 text-xs" onClick={() => openContactDialog('email')}>{account.email ? (account.emailVerified ? '更换' : '验证') : '绑定'}</Button>} />
      </div>
      <div className="mt-6 border-t border-border pt-5"><p className="text-xs font-bold uppercase tracking-[.14em] text-muted-foreground">联系方式用途</p><div className="mt-3 flex flex-wrap gap-2"><Badge tone="default">验证码登录</Badge><Badge tone="default">招聘联系</Badge><Badge tone="default">面试提醒</Badge><Badge tone="default">安全通知</Badge></div><p className="mt-3 text-xs leading-5 text-muted-foreground">仅已验证渠道可用于验证码登录；其他用途以服务端实际送达能力为准。</p><p className="mt-5 text-xs font-bold uppercase tracking-[.14em] text-muted-foreground">可用登录方式</p><div className="mt-3 flex flex-wrap gap-2">{account.availableLoginMethods.length ? account.availableLoginMethods.map(method => <Badge key={method} tone="default">{loginMethodLabel(method)}</Badge>) : <span className="text-sm text-muted-foreground">暂无可用登录方式</span>}</div></div>
      <div className="mt-6 flex flex-col gap-3 border-t border-border pt-5 sm:flex-row sm:items-center sm:justify-between"><div><p className="text-sm font-semibold">登录密码</p><p className="mt-1 text-xs leading-5 text-muted-foreground">更新密码会撤销其他设备会话，并为当前设备轮换登录凭据。</p></div><Link to={`/candidate/settings/security${location.search}`} className="inline-flex h-11 items-center justify-center gap-2 rounded-full border border-border bg-surface px-5 text-sm font-semibold text-foreground shadow-[0_8px_26px_rgba(20,18,17,.06)] transition hover:-translate-y-0.5 hover:border-[var(--accent)] hover:bg-[var(--accent-soft)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]"><KeyRound className="h-4 w-4" aria-hidden="true" />更新登录密码</Link></div>
    </Card>

    <Card className="p-5 sm:p-7">
      <div className="grid gap-5 sm:grid-cols-2"><MetaRow icon={<Clock3 className="h-4 w-4" aria-hidden="true" />} label="注册时间" value={formatDateTime(account.createdAt ?? undefined)} /><MetaRow icon={<Clock3 className="h-4 w-4" aria-hidden="true" />} label="最近登录" value={formatDateTime(account.lastLoginAt ?? undefined)} /></div>
    </Card>
    <ContactDialog channel={contactDialog} target={contactTarget} code={contactCode} password={contactPassword} error={contactError} status={contactStatus} unavailable={contactUnavailable} cooldownRemaining={contactCooldownRemaining} busy={contactBusy} onOpenChange={closeContactDialog} onTargetChange={setContactTarget} onCodeChange={setContactCode} onPasswordChange={setContactPassword} onSendCode={() => void sendContactCode()} onSubmit={() => void saveContact()} />
    {confirmAvatarDelete && <AdminConfirmDialog title="删除当前头像？" description="删除后将解除当前账户的头像绑定，原始媒体按系统生命周期处理。此操作不会删除你的账户资料。" confirmLabel="确认删除" danger busy={avatarAction === 'deleting'} onClose={() => { if (!avatarBusy) setConfirmAvatarDelete(false) }} onConfirm={() => void deleteAvatar()} />}
  </div>
}

function Notice({ tone, icon, children }: { tone: 'success' | 'warning' | 'danger' | 'info'; icon: ReactNode; children: ReactNode }) {
  const classes = { success: 'border-[var(--success-foreground)]/30 bg-[var(--success)] text-[var(--success-foreground)]', warning: 'border-[var(--warning-foreground)]/30 bg-[var(--warning)] text-[var(--warning-foreground)]', danger: 'border-[var(--danger-foreground)]/30 bg-[var(--danger)] text-[var(--danger-foreground)]', info: 'border-[var(--info-foreground)]/30 bg-[var(--info)] text-[var(--info-foreground)]' }
  return <div role={tone === 'danger' ? 'alert' : 'status'} className={`flex flex-wrap items-center gap-2 rounded-2xl border px-4 py-3 text-sm leading-6 ${classes[tone]}`}>{icon}<span>{children}</span></div>
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return <div className="px-5 py-4 sm:px-7"><p className="text-xs font-semibold text-muted-foreground">{label}</p><p className="mt-1 text-sm font-semibold">{value}</p></div>
}

function ReadOnlyField({ label, value, longValue = false }: { label: string; value: string; longValue?: boolean }) {
  return <div className="min-w-0"><p className="text-sm font-semibold">{label}</p><div title={value} className={`mt-2 flex min-h-12 items-center rounded-2xl border border-border bg-muted/55 px-4 text-sm text-muted-foreground ${longValue ? 'break-all' : ''}`}>{value}</div><p className="mt-2 text-xs text-muted-foreground">只读</p></div>
}

function ContactRow({ icon, label, value, verified, action }: { icon: ReactNode; label: string; value?: string | null; verified: boolean; action: ReactNode }) {
  return <div className="min-w-0 rounded-2xl border border-border bg-background p-4"><div className="flex items-center justify-between gap-3"><span className="flex min-w-0 items-center gap-2 text-sm font-semibold"><span className="text-[var(--accent)]">{icon}</span>{label}</span><div className="flex shrink-0 items-center gap-1"><Badge tone={verified ? 'success' : 'default'}>{verified ? '已验证' : '未验证'}</Badge>{action}</div></div><p className="mt-3 break-all text-sm text-muted-foreground">{value || '未设置'}</p></div>
}

function ContactDialog({
  channel, target, code, password, error, status, unavailable, cooldownRemaining, busy,
  onOpenChange, onTargetChange, onCodeChange, onPasswordChange, onSendCode, onSubmit,
}: {
  channel: ContactChannel | null
  target: string
  code: string
  password: string
  error: string
  status: string
  unavailable: boolean
  cooldownRemaining: number
  busy: ContactBusy
  onOpenChange: (open: boolean) => void
  onTargetChange: (value: string) => void
  onCodeChange: (value: string) => void
  onPasswordChange: (value: string) => void
  onSendCode: () => void
  onSubmit: () => void
}) {
  const label = channel === 'phone' ? '手机号' : '邮箱'
  const placeholder = channel === 'phone' ? '请输入 11 位手机号' : 'name@example.com'
  return <Dialog.Root open={channel !== null} onOpenChange={onOpenChange}>
    <Dialog.Portal>
      <Dialog.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm" />
      <Dialog.Content className="fixed left-1/2 top-1/2 z-50 max-h-[min(720px,calc(100vh-32px))] w-[calc(100%-32px)] max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-[24px] border border-border bg-surface p-5 shadow-2xl focus:outline-none sm:p-7">
        <div className="flex items-start justify-between gap-4">
          <div><p className="text-sm font-semibold text-[var(--accent)]">敏感联系方式操作</p><Dialog.Title className="mt-1 text-2xl font-bold">{channel ? `${target ? '更换' : '验证'}${label}` : ''}</Dialog.Title><Dialog.Description className="mt-2 text-sm leading-6 text-muted-foreground">请输入新联系方式，完成验证码和当前密码校验。成功后其他设备会话将被撤销。</Dialog.Description></div>
          <Dialog.Close asChild><Button type="button" variant="ghost" className="h-10 w-10 shrink-0 rounded-full px-0" aria-label="关闭"><X className="h-5 w-5" /></Button></Dialog.Close>
        </div>
        <form className="mt-6 space-y-4" onSubmit={event => { event.preventDefault(); onSubmit() }}>
          <label className="block text-sm font-semibold" htmlFor="contact-target">{label}<input id="contact-target" value={target} onChange={event => onTargetChange(event.target.value)} autoComplete={channel === 'phone' ? 'tel' : 'email'} placeholder={placeholder} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 text-sm font-normal outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20" /></label>
          <div><label className="block text-sm font-semibold" htmlFor="contact-code">验证码</label><div className="mt-2 flex gap-2"><input id="contact-code" value={code} onChange={event => onCodeChange(event.target.value)} inputMode="numeric" autoComplete="one-time-code" maxLength={6} className="h-12 min-w-0 flex-1 rounded-2xl border border-border bg-background px-4 text-sm outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20" /><Button type="button" variant="secondary" className="shrink-0 px-3 text-xs sm:px-4" disabled={Boolean(busy) || cooldownRemaining > 0 || !target.trim()} onClick={onSendCode}>{busy === 'sending' ? '发送中…' : cooldownRemaining > 0 ? `${cooldownRemaining}s 后重发` : '发送验证码'}</Button></div></div>
          <label className="block text-sm font-semibold" htmlFor="contact-password">当前密码<input id="contact-password" type="password" value={password} onChange={event => onPasswordChange(event.target.value)} autoComplete="current-password" className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 text-sm outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20" /></label>
          <div className="rounded-2xl border border-border bg-[var(--surface-soft)] p-4"><p className="text-xs font-semibold">该联系方式将用于</p><div className="mt-3 flex flex-wrap gap-2"><Badge tone="default">验证码登录</Badge><Badge tone="default">招聘联系</Badge><Badge tone="default">面试提醒</Badge><Badge tone="default">安全通知</Badge></div></div>
          {status && <p role="status" className="text-sm text-[var(--success-foreground)]">{status}</p>}
          {unavailable && <p role="status" className="text-sm text-[var(--warning-foreground)]">当前渠道暂不可用，服务端未确认发送成功。</p>}
          {error && <p role="alert" className="text-sm text-[var(--danger-foreground)]">{error}</p>}
          <div className="flex justify-end gap-3 pt-2"><Dialog.Close asChild><Button type="button" variant="secondary" disabled={Boolean(busy)}>取消</Button></Dialog.Close><Button type="submit" disabled={Boolean(busy) || !target.trim() || !code.trim() || !password}><ShieldCheck className="h-4 w-4" aria-hidden="true" />{busy === 'saving' ? '验证中…' : '确认变更'}</Button></div>
        </form>
      </Dialog.Content>
    </Dialog.Portal>
  </Dialog.Root>
}

function MetaRow({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return <div className="flex min-w-0 items-start gap-3"><span className="mt-0.5 text-[var(--accent)]">{icon}</span><div className="min-w-0"><p className="text-xs font-semibold text-muted-foreground">{label}</p><p className="mt-1 break-words text-sm font-semibold">{value}</p></div></div>
}
