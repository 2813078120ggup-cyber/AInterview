import { ArrowLeft, ArrowRight, CheckCircle2, KeyRound, Loader2, Send, ShieldCheck } from 'lucide-react'
import { FormEvent, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { AuthFlowShell } from '@/components/auth-flow-shell'
import { AuthPasswordField } from '@/components/auth-password-field'
import { ImageCaptcha, type ImageCaptchaHandle } from '@/components/image-captcha'
import { Button } from '@/components/ui/button'
import { authFieldClass, identifyContact, passwordPattern, usePersistentCooldown } from '@/lib/auth-form'
import { request, type CaptchaChallenge } from '@/lib/api'

const passwordResetCooldownKey = 'interviewos_password_reset_code_cooldown_until'
const passwordResetSteps = ['验证账户', '设置新密码', '重置完成'] as const

type PasswordResetCodeResponse = {
  accepted: boolean
  cooldownSeconds: number
  expiresInSeconds: number
  message: string
}

type PasswordResetVerifyResponse = { resetToken: string; expiresInSeconds: number }
type PasswordResetResponse = { sessionBehavior: string }
type ResetLocationState = { target?: string }

export function ForgotPasswordPage() {
  const nav = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const locationState = location.state as ResetLocationState | null
  const [step, setStep] = useState(1)
  const [target, setTarget] = useState(locationState?.target ?? '')
  const [verificationCode, setVerificationCode] = useState('')
  const [captcha, setCaptcha] = useState<CaptchaChallenge | null>(null)
  const [captchaCode, setCaptchaCode] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [resetToken, setResetToken] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordVisible, setPasswordVisible] = useState(false)
  const [confirmVisible, setConfirmVisible] = useState(false)
  const [sending, setSending] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const [completionMessage, setCompletionMessage] = useState('')
  const captchaRef = useRef<ImageCaptchaHandle>(null)
  const cooldown = usePersistentCooldown(passwordResetCooldownKey)
  const next = searchParams.get('next')
  const loginHref = next ? `/login?next=${encodeURIComponent(next)}` : '/login'
  const resetCodeReady = Boolean(target.trim() && captcha?.challengeId && captchaCode.trim())
  const accountVerificationReady = Boolean(target.trim() && codeSent && verificationCode.trim())
  const passwordResetReady = Boolean(resetToken && password && confirmPassword)

  async function sendResetCode() {
    setError('')
    setStatus('')
    const normalizedTarget = target.trim()
    const channel = identifyContact(normalizedTarget)
    if (!channel) {
      setError('请输入正确的手机号或邮箱')
      return
    }
    if (!captcha?.challengeId || !captchaCode.trim()) {
      setError('请输入图形验证码')
      return
    }

    setSending(true)
    let requestStarted = false
    try {
      requestStarted = true
      const result = await request<PasswordResetCodeResponse>('/v1/auth/password/reset/code', {
        method: 'POST',
        body: JSON.stringify({ channel, target: normalizedTarget, captchaChallengeId: captcha.challengeId, captchaCode: captchaCode.trim() }),
      })
      cooldown.start(result.cooldownSeconds)
      setCodeSent(true)
      setStatus(result.message)
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : '验证码发送失败，请稍后重试。'
      if (message.includes('发送过于频繁')) cooldown.start()
      setError(message)
    } finally {
      if (requestStarted) {
        setCaptcha(null)
        setCaptchaCode('')
        captchaRef.current?.reset()
      }
      setSending(false)
    }
  }

  async function verifyAccount(event: FormEvent) {
    event.preventDefault()
    setError('')
    const normalizedTarget = target.trim()
    const channel = identifyContact(normalizedTarget)
    if (!channel) {
      setError('请输入正确的手机号或邮箱')
      return
    }
    if (!codeSent || !verificationCode.trim()) {
      setError('请先获取并填写验证码')
      return
    }

    setBusy(true)
    try {
      const result = await request<PasswordResetVerifyResponse>('/v1/auth/password/reset/verify', {
        method: 'POST',
        body: JSON.stringify({ channel, target: normalizedTarget, verificationCode: verificationCode.trim() }),
      })
      setResetToken(result.resetToken)
      setVerificationCode('')
      setStatus('')
      setStep(2)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '账户验证失败，请重新获取验证码。')
    } finally {
      setBusy(false)
    }
  }

  async function completeReset(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (!passwordPattern.test(password)) {
      setError('新密码须为 8-64 位，并同时包含字母和数字')
      return
    }
    if (password !== confirmPassword) {
      setError('两次输入的密码不一致')
      return
    }
    if (!resetToken) {
      setError('账户验证已失效，请重新验证')
      setStep(1)
      return
    }

    setBusy(true)
    try {
      const result = await request<PasswordResetResponse>('/v1/auth/password/reset/complete', {
        method: 'POST',
        body: JSON.stringify({ resetToken, newPassword: password }),
      })
      setPassword('')
      setConfirmPassword('')
      setResetToken('')
      setCompletionMessage(result.sessionBehavior)
      setStep(3)
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : '密码重置失败，请稍后重试。'
      setError(message)
      if (message.includes('验证已失效') || message.includes('重新验证')) setStep(1)
    } finally {
      setBusy(false)
    }
  }

  return <AuthFlowShell title="找回密码" description="验证已绑定的手机号或邮箱后，重新设置账户登录密码。" steps={passwordResetSteps} currentStep={step}>
    {step === 1 && <form className="space-y-4" onSubmit={verifyAccount}>
      <div>
        <h2 className="text-xl font-bold">验证账户</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">请输入已绑定的手机号或邮箱。系统会先完成身份验证，再允许设置新密码。</p>
      </div>
      <label className="sr-only" htmlFor="reset-target">手机号 / 邮箱</label>
      <input id="reset-target" value={target} onChange={event => { setTarget(event.target.value); setCodeSent(false); setVerificationCode(''); setStatus('') }} className={authFieldClass} placeholder="手机号 / 邮箱" autoComplete="username" aria-label="手机号 / 邮箱" disabled={sending || busy} required />
      <ImageCaptcha ref={captchaRef} purpose="PASSWORD_RESET_CODE_SEND" value={captchaCode} onChange={setCaptchaCode} onChallengeChange={setCaptcha} disabled={sending || busy} />
      <div className="flex gap-2">
        <label className="sr-only" htmlFor="reset-code">短信 / 邮箱验证码</label>
        <input id="reset-code" value={verificationCode} onChange={event => setVerificationCode(event.target.value)} className={`${authFieldClass} min-w-0 flex-1`} placeholder="短信 / 邮箱验证码" autoComplete="one-time-code" inputMode="numeric" maxLength={6} aria-label="短信 / 邮箱验证码" disabled={sending || busy} required />
        <Button type="button" variant="secondary" className="min-w-28 shrink-0 px-3" onClick={() => void sendResetCode()} disabled={sending || busy || cooldown.remaining > 0 || !resetCodeReady}>
          {sending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
          {sending ? '发送中' : cooldown.remaining > 0 ? `${cooldown.remaining}s` : '发送验证码'}
        </Button>
      </div>
      {error && <p role="alert" className="text-sm leading-6 text-[var(--danger-foreground)]">{error}</p>}
      {status && <p role="status" className="text-sm leading-6 text-muted-foreground">{status}</p>}
      <Button type="submit" className="h-12 w-full rounded-2xl" disabled={busy || !accountVerificationReady}>
        {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ShieldCheck className="h-4 w-4" />}
        {busy ? '正在验证…' : '验证并继续'}
      </Button>
      <p className="text-center text-sm text-muted-foreground">已记起密码？ <Link to={loginHref} className="font-semibold text-[var(--accent)] hover:text-foreground">返回登录</Link></p>
    </form>}

    {step === 2 && <form className="space-y-4" onSubmit={completeReset}>
      <div>
        <span className="mb-4 grid h-11 w-11 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><KeyRound className="h-5 w-5" /></span>
        <h2 className="text-xl font-bold">设置新密码</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">新密码不能与当前密码相同。重置后，其他设备上的登录会话将失效。</p>
      </div>
      <AuthPasswordField id="reset-password" label="新密码" placeholder="新密码，至少 8 位且包含字母和数字" value={password} visible={passwordVisible} disabled={busy} onChange={setPassword} onToggle={() => setPasswordVisible(value => !value)} />
      <AuthPasswordField id="reset-confirm-password" label="确认新密码" placeholder="再次输入新密码" value={confirmPassword} visible={confirmVisible} disabled={busy} onChange={setConfirmPassword} onToggle={() => setConfirmVisible(value => !value)} />
      {error && <p role="alert" className="text-sm leading-6 text-[var(--danger-foreground)]">{error}</p>}
      <div className="grid gap-3 sm:grid-cols-[auto_1fr]">
        <Button type="button" variant="secondary" className="h-12 rounded-2xl" onClick={() => { setError(''); setStep(1) }} disabled={busy}>
          <ArrowLeft className="h-4 w-4" />
          重新验证
        </Button>
        <Button type="submit" className="h-12 rounded-2xl" disabled={busy || !passwordResetReady}>
          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowRight className="h-4 w-4" />}
          {busy ? '正在保存…' : '保存新密码'}
        </Button>
      </div>
    </form>}

    {step === 3 && <div className="py-4 text-center">
      <span className="mx-auto grid h-16 w-16 place-items-center rounded-full bg-[var(--success)] text-[var(--success-foreground)]"><CheckCircle2 className="h-8 w-8" /></span>
      <h2 className="mt-6 text-2xl font-bold">密码重置完成</h2>
      <p className="mx-auto mt-3 max-w-[38ch] text-sm leading-6 text-muted-foreground">{completionMessage || '请使用新密码重新登录。'}</p>
      <Button type="button" className="mt-7 h-12 w-full rounded-2xl" onClick={() => nav(loginHref, { replace: true })}>返回登录</Button>
    </div>}
  </AuthFlowShell>
}
