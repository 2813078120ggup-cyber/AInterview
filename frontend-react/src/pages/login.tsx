import { ArrowRight, Bot, Loader2, Send } from 'lucide-react'
import { FormEvent, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AuthPasswordField } from '@/components/auth-password-field'
import { AuthBrandBackground } from '@/components/auth-brand-background'
import { AuthTopBar } from '@/components/auth-top-bar'
import { ImageCaptcha, type ImageCaptchaHandle } from '@/components/image-captcha'
import { Button } from '@/components/ui/button'
import { authFieldClass, emailPattern, identifyContact, phonePattern, usernamePattern, usePersistentCooldown } from '@/lib/auth-form'
import { request, type CaptchaChallenge } from '@/lib/api'
import { postLoginDestination } from '@/lib/navigation'
import { establish } from '@/lib/session'

type Login = {
  token: string
  refreshToken: string
  user: { id: string; username: string; realName: string; roles: string[]; companyId?: string }
}

type LoginMethod = 'password' | 'code'

const loginCodeCooldownKey = 'interviewos_login_code_cooldown_until'

export function LoginPage() {
  const nav = useNavigate()
  const [searchParams] = useSearchParams()
  const requestedDestination = searchParams.get('next')
  const authSearch = requestedDestination ? `?next=${encodeURIComponent(requestedDestination)}` : ''
  const [loginMethod, setLoginMethod] = useState<LoginMethod>('password')
  const [busy, setBusy] = useState(false)
  const [sendingCode, setSendingCode] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const [passwordVisible, setPasswordVisible] = useState(false)
  const [loginIdentifier, setLoginIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [loginTarget, setLoginTarget] = useState('')
  const [loginCode, setLoginCode] = useState('')
  const [passwordCaptcha, setPasswordCaptcha] = useState<CaptchaChallenge | null>(null)
  const [passwordCaptchaCode, setPasswordCaptchaCode] = useState('')
  const [loginCodeCaptcha, setLoginCodeCaptcha] = useState<CaptchaChallenge | null>(null)
  const [loginCodeCaptchaCode, setLoginCodeCaptchaCode] = useState('')
  const passwordCaptchaRef = useRef<ImageCaptchaHandle>(null)
  const loginCodeCaptchaRef = useRef<ImageCaptchaHandle>(null)
  const loginCooldown = usePersistentCooldown(loginCodeCooldownKey)
  const disabled = busy || sendingCode
  const passwordLoginReady = Boolean(loginIdentifier.trim() && password && passwordCaptcha?.challengeId && passwordCaptchaCode.trim())
  const loginCodeSendReady = Boolean(loginTarget.trim() && loginCodeCaptcha?.challengeId && loginCodeCaptchaCode.trim())
  const codeLoginReady = Boolean(loginTarget.trim() && loginCode.trim())
  const loginReady = loginMethod === 'password' ? passwordLoginReady : codeLoginReady

  function changeMethod(method: LoginMethod) {
    setLoginMethod(method)
    setError('')
    setStatus('')
  }

  function openRegistration() {
    const source = loginMethod === 'code' ? loginTarget.trim() : loginIdentifier.trim()
    const channel = identifyContact(source)
    nav(`/register${authSearch}`, {
      state: channel === 'sms' ? { phone: source } : channel === 'email' ? { email: source } : undefined,
    })
  }

  function openPasswordReset() {
    const source = loginMethod === 'code' ? loginTarget.trim() : loginIdentifier.trim()
    nav(`/forgot-password${authSearch}`, { state: identifyContact(source) ? { target: source } : undefined })
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError('')
    setStatus('')
    let passwordRequestStarted = false
    try {
      if (loginMethod === 'code') {
        const target = loginTarget.trim()
        if (!identifyContact(target)) throw new Error('请输入正确的手机号或邮箱')
        if (!loginCode.trim()) throw new Error('请输入验证码')
        const result = await request<Login>('/v1/auth/login/code', {
          method: 'POST',
          body: JSON.stringify({ target, verificationCode: loginCode.trim() }),
        })
        establish(result.token, result.refreshToken, result.user)
        nav(postLoginDestination(result.user.roles, requestedDestination), { replace: true })
        return
      }

      const identifier = loginIdentifier.trim()
      if (!usernamePattern.test(identifier) && !phonePattern.test(identifier) && !emailPattern.test(identifier)) throw new Error('请输入正确的用户名、手机号或邮箱')
      if (!password) throw new Error('请输入密码')
      if (!passwordCaptcha?.challengeId || !passwordCaptchaCode.trim()) throw new Error('请输入图形验证码')
      passwordRequestStarted = true
      const result = await request<Login>('/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username: identifier, password, captchaChallengeId: passwordCaptcha.challengeId, captchaCode: passwordCaptchaCode.trim() }),
      })
      establish(result.token, result.refreshToken, result.user)
      nav(postLoginDestination(result.user.roles, requestedDestination), { replace: true })
    } catch (reason) {
      if (passwordRequestStarted) {
        setPasswordCaptcha(null)
        setPasswordCaptchaCode('')
        passwordCaptchaRef.current?.reset()
      }
      const message = reason instanceof Error ? reason.message : '登录失败，请稍后重试。'
      if (loginMethod === 'code' && message.includes('还未注册')) {
        openRegistration()
        return
      }
      setError(message)
    } finally {
      setBusy(false)
    }
  }

  async function sendLoginCode() {
    setError('')
    setStatus('')
    const target = loginTarget.trim()
    if (!identifyContact(target)) {
      setError('请输入正确的手机号或邮箱')
      return
    }
    if (!loginCodeCaptcha?.challengeId || !loginCodeCaptchaCode.trim()) {
      setError('请输入图形验证码')
      return
    }

    setSendingCode(true)
    let requestStarted = false
    try {
      requestStarted = true
      await request('/v1/auth/login/code/send', {
        method: 'POST',
        body: JSON.stringify({ target, captchaChallengeId: loginCodeCaptcha.challengeId, captchaCode: loginCodeCaptchaCode.trim() }),
      })
      loginCooldown.start()
      setStatus('验证码已发送，请在有效期内填写。')
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : '验证码发送失败，请稍后重试。'
      if (message.includes('发送过于频繁')) loginCooldown.start()
      if (message.includes('还未注册')) {
        openRegistration()
        return
      }
      setError(message)
    } finally {
      if (requestStarted) {
        setLoginCodeCaptcha(null)
        setLoginCodeCaptchaCode('')
        loginCodeCaptchaRef.current?.reset()
      }
      setSendingCode(false)
    }
  }

  return <main className="auth-login-shell min-h-dvh text-foreground">
    <AuthBrandBackground />
    <AuthTopBar sectionLabel="账户登录" />
    <div className="auth-login-content relative z-[1] grid place-items-center px-4 py-8 sm:px-6">
    <section className="auth-login-card auth-login-card-enter w-full max-w-[420px] rounded-[28px] border border-border bg-surface p-5 sm:p-8">
      <div role="tablist" aria-label="登录方式" className="auth-login-tabs relative flex border-b border-border">
        <span aria-hidden className={`auth-tab-indicator ${loginMethod === 'code' ? 'auth-tab-indicator-code' : ''}`} />
        <button type="button" role="tab" aria-selected={loginMethod === 'password'} onClick={() => changeMethod('password')} disabled={disabled} className={`relative z-[1] min-h-11 flex-1 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-55 ${loginMethod === 'password' ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'}`}>密码登录</button>
        <button type="button" role="tab" aria-selected={loginMethod === 'code'} onClick={() => changeMethod('code')} disabled={disabled} className={`relative z-[1] min-h-11 flex-1 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-55 ${loginMethod === 'code' ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'}`}>验证码登录</button>
      </div>

      <header key={`copy:${loginMethod}`} className="auth-copy-enter mt-7 text-center">
        <div className="flex items-center justify-center gap-3">
          <span className="grid h-10 w-10 place-items-center rounded-xl bg-[var(--primary)] text-[var(--primary-foreground)]"><Bot aria-hidden="true" className="h-[18px] w-[18px]" /></span>
          <div className="text-left">
            <h1 className="text-xl font-black tracking-[-0.035em]">AInterview</h1>
            <p className="mt-0.5 text-sm font-semibold text-[var(--accent)]">每一面，都算数。</p>
          </div>
        </div>
        <p className="mx-auto mt-2 max-w-[34ch] text-sm leading-6 text-muted-foreground">{loginMethod === 'code' ? '请输入已注册的手机号或邮箱，系统将自动选择验证渠道。' : '请输入账户信息，安全进入 AInterview 工作空间。'}</p>
      </header>

      <form key={loginMethod} className="auth-panel-enter mt-5 space-y-3" onSubmit={submit}>
        {loginMethod === 'password' ? <>
          <label className="sr-only" htmlFor="login-identifier">用户名 / 手机号 / 邮箱</label>
          <input id="login-identifier" value={loginIdentifier} onChange={event => setLoginIdentifier(event.target.value)} className={authFieldClass} placeholder="用户名 / 手机号 / 邮箱" autoComplete="username" aria-label="用户名 / 手机号 / 邮箱" required />
          <AuthPasswordField id="login-password" label="密码" placeholder="密码" value={password} visible={passwordVisible} disabled={disabled} autoComplete="current-password" onChange={setPassword} onToggle={() => setPasswordVisible(value => !value)} />
          <ImageCaptcha ref={passwordCaptchaRef} purpose="PASSWORD_LOGIN" value={passwordCaptchaCode} onChange={setPasswordCaptchaCode} onChallengeChange={setPasswordCaptcha} disabled={disabled} />
        </> : <>
          <label className="sr-only" htmlFor="login-target">手机号 / 邮箱</label>
          <input id="login-target" value={loginTarget} onChange={event => setLoginTarget(event.target.value)} className={authFieldClass} placeholder="手机号 / 邮箱" autoComplete="username" aria-label="手机号 / 邮箱" required />
          <ImageCaptcha ref={loginCodeCaptchaRef} purpose="LOGIN_CODE_SEND" value={loginCodeCaptchaCode} onChange={setLoginCodeCaptchaCode} onChallengeChange={setLoginCodeCaptcha} disabled={disabled} />
          <div className="flex gap-2">
            <label className="sr-only" htmlFor="login-code">验证码</label>
            <input id="login-code" value={loginCode} onChange={event => setLoginCode(event.target.value)} className={`${authFieldClass} min-w-0 flex-1`} placeholder="短信 / 邮箱验证码" autoComplete="one-time-code" inputMode="numeric" maxLength={6} aria-label="短信 / 邮箱验证码" required />
            <Button type="button" variant="secondary" className="min-w-28 shrink-0 px-3" onClick={() => void sendLoginCode()} disabled={disabled || loginCooldown.remaining > 0 || !loginCodeSendReady}>
              {sendingCode ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
              {sendingCode ? '发送中' : loginCooldown.remaining > 0 ? `${loginCooldown.remaining}s` : '发送验证码'}
            </Button>
          </div>
        </>}
        {error && <p role="alert" className="text-sm leading-6 text-[var(--danger-foreground)]">{error}</p>}
        {status && <p role="status" className="text-sm leading-6 text-[var(--success-foreground)]">{status}</p>}
        <Button type="submit" className="h-12 w-full rounded-2xl" disabled={disabled || !loginReady}>
          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowRight className="h-4 w-4" />}
          {busy ? '正在登录…' : '登录'}
        </Button>
        <div className="flex items-center justify-center gap-3 pt-1 text-sm">
          <button type="button" onClick={openPasswordReset} className="font-semibold text-[var(--accent)] transition hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]">忘记密码</button>
          <span aria-hidden className="text-border">|</span>
          <button type="button" onClick={openRegistration} className="font-semibold text-[var(--accent)] transition hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]">立即注册</button>
        </div>
      </form>
    </section>
    </div>
  </main>
}
