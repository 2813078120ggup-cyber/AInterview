import { ArrowLeft, ArrowRight, Building2, CheckCircle2, Loader2, Send } from 'lucide-react'
import { FormEvent, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { AuthFlowShell } from '@/components/auth-flow-shell'
import { AuthPasswordField } from '@/components/auth-password-field'
import { ImageCaptcha, type ImageCaptchaHandle } from '@/components/image-captcha'
import { Button } from '@/components/ui/button'
import { authFieldClass, emailPattern, passwordPattern, phonePattern, usernamePattern, usePersistentCooldown } from '@/lib/auth-form'
import { request, type CaptchaChallenge } from '@/lib/api'

const registerCodeCooldownKey = 'interviewos_register_code_cooldown_until'
const personalRegistrationSteps = ['验证手机号', '填写账户信息', '注册成功'] as const
const companyRegistrationSteps = ['验证联系人', '完善企业资料', '注册成功'] as const

type RegistrationLocationState = { phone?: string; email?: string }
type RegistrationMode = 'personal' | 'enterprise'
type CompanyRegisterResponse = { companyId: number; companyCode: string; admin: { realName: string; roles: string[]; companyId?: number } }

export function CandidateRegistrationPage() {
  const nav = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const locationState = location.state as RegistrationLocationState | null
  const [mode, setMode] = useState<RegistrationMode>(() => searchParams.get('type') === 'company' ? 'enterprise' : 'personal')
  const [step, setStep] = useState(1)
  const [form, setForm] = useState(() => ({
    phone: locationState?.phone ?? '',
    verificationCode: '',
    realName: '',
    username: '',
    password: '',
    email: locationState?.email ?? '',
  }))
  const [companyForm, setCompanyForm] = useState({
    companyName: '', shortName: '', industry: '', companySize: '', city: '', websiteUrl: '',
    description: '', legalRepresentative: '', businessLicenseNo: '',
  })
  const [captcha, setCaptcha] = useState<CaptchaChallenge | null>(null)
  const [captchaCode, setCaptchaCode] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const [sendingCode, setSendingCode] = useState(false)
  const [busy, setBusy] = useState(false)
  const [passwordVisible, setPasswordVisible] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const [companyCode, setCompanyCode] = useState('')
  const captchaRef = useRef<ImageCaptchaHandle>(null)
  const cooldown = usePersistentCooldown(registerCodeCooldownKey)
  const next = searchParams.get('next')
  const loginHref = next ? `/login?next=${encodeURIComponent(next)}` : '/login'
  const registrationCodeReady = Boolean(form.phone.trim() && captcha?.challengeId && captchaCode.trim())
  const registrationVerificationReady = Boolean(form.phone.trim() && codeSent && form.verificationCode.trim())
  const registrationProfileReady = Boolean(form.realName.trim() && form.username.trim() && form.password)
  const enterpriseProfileReady = Boolean(
    form.realName.trim() && form.username.trim() && form.password
      && companyForm.companyName.trim() && companyForm.industry.trim()
      && companyForm.companySize.trim() && companyForm.city.trim(),
  )
  const registrationSteps = mode === 'enterprise' ? companyRegistrationSteps : personalRegistrationSteps

  function switchMode(nextMode: RegistrationMode) {
    if (nextMode === mode || busy || sendingCode) return
    setMode(nextMode)
    setStep(1)
    setError('')
    setStatus('')
    setCompanyCode('')
    setCodeSent(false)
    setCaptcha(null)
    setCaptchaCode('')
    captchaRef.current?.reset()
  }

  async function sendCode() {
    setError('')
    setStatus('')
    const phone = form.phone.trim()
    if (!phonePattern.test(phone)) {
      setError('请输入正确的 11 位手机号')
      return
    }
    if (!captcha?.challengeId || !captchaCode.trim()) {
      setError('请输入图形验证码')
      return
    }

    setSendingCode(true)
    let requestStarted = false
    try {
      requestStarted = true
      await request('/v1/auth/register/code', {
        method: 'POST',
        body: JSON.stringify({ phone, captchaChallengeId: captcha.challengeId, captchaCode: captchaCode.trim() }),
      })
      cooldown.start()
      setCodeSent(true)
      setStatus('短信验证码已发送，请在有效期内填写。')
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
      setSendingCode(false)
    }
  }

  function continueToProfile(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (!phonePattern.test(form.phone.trim())) {
      setError('请输入正确的 11 位手机号')
      return
    }
    if (!codeSent || !form.verificationCode.trim()) {
      setError('请先获取并填写短信验证码')
      return
    }
    setStep(2)
  }

  async function createAccount(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (!form.realName.trim()) {
      setError('请输入姓名')
      return
    }
    if (!usernamePattern.test(form.username.trim())) {
      setError('用户名须以字母开头，并使用 4-32 位字母、数字或下划线')
      return
    }
    if (!passwordPattern.test(form.password)) {
      setError('密码须为 8-64 位，并同时包含字母和数字')
      return
    }
    if (form.email.trim() && !emailPattern.test(form.email.trim())) {
      setError('请输入正确的邮箱地址，或将邮箱留空')
      return
    }
    if (mode === 'enterprise') {
      if (!companyForm.companyName.trim() || !companyForm.industry.trim() || !companyForm.companySize.trim() || !companyForm.city.trim()) {
        setError('请完整填写企业全称、所属行业、企业规模和所在城市')
        return
      }
      if (companyForm.websiteUrl.trim() && !/^https?:\/\/[^\s]+$/i.test(companyForm.websiteUrl.trim())) {
        setError('企业官网地址应以 http:// 或 https:// 开头')
        return
      }
    }

    setBusy(true)
    try {
      const payload = mode === 'enterprise' ? {
        username: form.username.trim(),
        password: form.password,
        realName: form.realName.trim(),
        ...(form.email.trim() ? { email: form.email.trim() } : {}),
        phone: form.phone.trim(),
        verificationCode: form.verificationCode.trim(),
        companyName: companyForm.companyName.trim(),
        ...(companyForm.shortName.trim() ? { shortName: companyForm.shortName.trim() } : {}),
        industry: companyForm.industry.trim(),
        companySize: companyForm.companySize.trim(),
        city: companyForm.city.trim(),
        ...(companyForm.websiteUrl.trim() ? { websiteUrl: companyForm.websiteUrl.trim() } : {}),
        ...(companyForm.description.trim() ? { description: companyForm.description.trim() } : {}),
        ...(companyForm.legalRepresentative.trim() ? { legalRepresentative: companyForm.legalRepresentative.trim() } : {}),
        ...(companyForm.businessLicenseNo.trim() ? { businessLicenseNo: companyForm.businessLicenseNo.trim() } : {}),
      } : {
        username: form.username.trim(),
        password: form.password,
        realName: form.realName.trim(),
        phone: form.phone.trim(),
        verificationCode: form.verificationCode.trim(),
        ...(form.email.trim() ? { email: form.email.trim() } : {}),
      }
      const result = await request<CompanyRegisterResponse | unknown>(mode === 'enterprise' ? '/v1/auth/company/register' : '/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      if (mode === 'enterprise') setCompanyCode((result as CompanyRegisterResponse).companyCode ?? '')
      setForm(previous => ({ ...previous, password: '', verificationCode: '' }))
      setStep(3)
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : '注册失败，请稍后重试。'
      setError(message)
      if (message.includes('验证码')) setStep(1)
    } finally {
      setBusy(false)
    }
  }

  return <AuthFlowShell title={mode === 'enterprise' ? '企业 HR 注册' : '个人用户注册'} description={mode === 'enterprise' ? '创建企业租户并配置首个 HR 管理账号，资料提交后即可使用企业工作区。' : '先验证常用手机号，再完善账户资料。邮箱可以稍后补充。'} steps={registrationSteps} currentStep={step}>
    <div role="tablist" aria-label="注册类型" className="mb-7 grid grid-cols-2 border-b border-border">
      <button type="button" role="tab" aria-selected={mode === 'personal'} onClick={() => switchMode('personal')} className={`min-h-11 border-b-2 text-sm font-semibold transition-colors ${mode === 'personal' ? 'border-[var(--accent)] text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'}`}>个人用户注册</button>
      <button type="button" role="tab" aria-selected={mode === 'enterprise'} onClick={() => switchMode('enterprise')} className={`min-h-11 border-b-2 text-sm font-semibold transition-colors ${mode === 'enterprise' ? 'border-[var(--accent)] text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'}`}><Building2 className="mr-1.5 inline h-4 w-4" />企业 HR 注册</button>
    </div>
    {step === 1 && <form className="space-y-4" onSubmit={continueToProfile}>
      <div>
        <h2 className="text-xl font-bold">{mode === 'enterprise' ? '验证企业联系人' : '验证常用手机号'}</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">{mode === 'enterprise' ? '请使用 HR 联系人的手机号完成验证，该号码将作为企业账号的安全联系方式。' : '该手机号将用于登录、验证码接收与账户找回。'}</p>
      </div>
      <label className="sr-only" htmlFor="register-phone">手机号</label>
      <input id="register-phone" value={form.phone} onChange={event => {
        setForm(previous => ({ ...previous, phone: event.target.value, verificationCode: '' }))
        setCodeSent(false)
        setStatus('')
      }} className={authFieldClass} placeholder="手机号" autoComplete="tel" inputMode="tel" maxLength={11} aria-label="手机号" disabled={sendingCode} required />
      <ImageCaptcha ref={captchaRef} purpose="REGISTER_CODE_SEND" value={captchaCode} onChange={setCaptchaCode} onChallengeChange={setCaptcha} disabled={sendingCode} />
      <div className="flex gap-2">
        <label className="sr-only" htmlFor="register-code">短信验证码</label>
        <input id="register-code" value={form.verificationCode} onChange={event => setForm(previous => ({ ...previous, verificationCode: event.target.value }))} className={`${authFieldClass} min-w-0 flex-1`} placeholder="短信验证码" autoComplete="one-time-code" inputMode="numeric" maxLength={6} aria-label="短信验证码" disabled={sendingCode} required />
        <Button type="button" variant="secondary" className="min-w-28 shrink-0 px-3" onClick={() => void sendCode()} disabled={sendingCode || cooldown.remaining > 0 || !registrationCodeReady}>
          {sendingCode ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
          {sendingCode ? '发送中' : cooldown.remaining > 0 ? `${cooldown.remaining}s` : '发送验证码'}
        </Button>
      </div>
      {error && <p role="alert" className="text-sm leading-6 text-[var(--danger-foreground)]">{error}</p>}
      {status && <p role="status" className="text-sm leading-6 text-[var(--success-foreground)]">{status}</p>}
      <Button type="submit" className="h-12 w-full rounded-2xl" disabled={sendingCode || !registrationVerificationReady}>
        继续填写
        <ArrowRight className="h-4 w-4" />
      </Button>
      <p className="text-center text-sm text-muted-foreground">已有账户？ <Link to={loginHref} className="font-semibold text-[var(--accent)] hover:text-foreground">返回登录</Link></p>
    </form>}

    {step === 2 && mode === 'personal' && <form className="space-y-4" onSubmit={createAccount}>
      <div>
        <h2 className="text-xl font-bold">填写账户信息</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">已填写手机号 {form.phone}，创建账户时会完成验证码校验。</p>
      </div>
      <label className="sr-only" htmlFor="register-real-name">姓名</label>
      <input id="register-real-name" value={form.realName} onChange={event => setForm(previous => ({ ...previous, realName: event.target.value }))} className={authFieldClass} placeholder="姓名" autoComplete="name" aria-label="姓名" disabled={busy} required />
      <label className="sr-only" htmlFor="register-username">用户名</label>
      <input id="register-username" value={form.username} onChange={event => setForm(previous => ({ ...previous, username: event.target.value }))} className={authFieldClass} placeholder="用户名，4-32 位字母、数字或下划线" autoComplete="username" aria-label="用户名" disabled={busy} required />
      <AuthPasswordField id="register-password" label="密码" placeholder="密码，至少 8 位且包含字母和数字" value={form.password} visible={passwordVisible} disabled={busy} onChange={value => setForm(previous => ({ ...previous, password: value }))} onToggle={() => setPasswordVisible(value => !value)} />
      <label className="sr-only" htmlFor="register-email">邮箱（选填）</label>
      <input id="register-email" type="email" value={form.email} onChange={event => setForm(previous => ({ ...previous, email: event.target.value }))} className={authFieldClass} placeholder="邮箱（选填）" autoComplete="email" aria-label="邮箱（选填）" disabled={busy} />
      {error && <p role="alert" className="text-sm leading-6 text-[var(--danger-foreground)]">{error}</p>}
      <div className="grid gap-3 sm:grid-cols-[auto_1fr]">
        <Button type="button" variant="secondary" className="h-12 rounded-2xl" onClick={() => { setError(''); setStep(1) }} disabled={busy}>
          <ArrowLeft className="h-4 w-4" />
          修改手机号
        </Button>
        <Button type="submit" className="h-12 rounded-2xl" disabled={busy || !registrationProfileReady}>
          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowRight className="h-4 w-4" />}
          {busy ? '正在创建…' : '创建账户'}
        </Button>
      </div>
    </form>}

    {step === 2 && mode === 'enterprise' && <form className="space-y-4" onSubmit={createAccount}>
      <div>
        <h2 className="text-xl font-bold">完善企业资料与 HR 账号</h2>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">请填写真实企业信息。提交后将创建独立企业租户，并为联系人分配企业管理员权限。</p>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="sr-only" htmlFor="company-name">企业全称</label>
        <input id="company-name" value={companyForm.companyName} onChange={event => setCompanyForm(previous => ({ ...previous, companyName: event.target.value }))} className={authFieldClass} placeholder="企业全称" aria-label="企业全称" maxLength={160} disabled={busy} required />
        <label className="sr-only" htmlFor="company-short-name">企业简称</label>
        <input id="company-short-name" value={companyForm.shortName} onChange={event => setCompanyForm(previous => ({ ...previous, shortName: event.target.value }))} className={authFieldClass} placeholder="企业简称（选填）" aria-label="企业简称（选填）" maxLength={80} disabled={busy} />
        <label className="sr-only" htmlFor="company-industry">所属行业</label>
        <input id="company-industry" value={companyForm.industry} onChange={event => setCompanyForm(previous => ({ ...previous, industry: event.target.value }))} className={authFieldClass} placeholder="所属行业" aria-label="所属行业" maxLength={96} disabled={busy} required />
        <label className="sr-only" htmlFor="company-size">企业规模</label>
        <input id="company-size" value={companyForm.companySize} onChange={event => setCompanyForm(previous => ({ ...previous, companySize: event.target.value }))} className={authFieldClass} placeholder="企业规模，如 100-499 人" aria-label="企业规模" maxLength={48} disabled={busy} required />
        <label className="sr-only" htmlFor="company-city">所在城市</label>
        <input id="company-city" value={companyForm.city} onChange={event => setCompanyForm(previous => ({ ...previous, city: event.target.value }))} className={authFieldClass} placeholder="所在城市" aria-label="所在城市" maxLength={96} disabled={busy} required />
        <label className="sr-only" htmlFor="company-license">统一社会信用代码</label>
        <input id="company-license" value={companyForm.businessLicenseNo} onChange={event => setCompanyForm(previous => ({ ...previous, businessLicenseNo: event.target.value }))} className={authFieldClass} placeholder="统一社会信用代码（选填）" aria-label="统一社会信用代码（选填）" maxLength={64} disabled={busy} />
      </div>
      <label className="sr-only" htmlFor="company-website">企业官网</label>
      <input id="company-website" value={companyForm.websiteUrl} onChange={event => setCompanyForm(previous => ({ ...previous, websiteUrl: event.target.value }))} className={authFieldClass} placeholder="企业官网（选填，如 https://example.com）" aria-label="企业官网（选填）" maxLength={512} disabled={busy} />
      <label className="sr-only" htmlFor="company-legal-representative">法定代表人</label>
      <input id="company-legal-representative" value={companyForm.legalRepresentative} onChange={event => setCompanyForm(previous => ({ ...previous, legalRepresentative: event.target.value }))} className={authFieldClass} placeholder="法定代表人（选填）" aria-label="法定代表人（选填）" maxLength={64} disabled={busy} />
      <label className="sr-only" htmlFor="company-description">企业简介</label>
      <textarea id="company-description" value={companyForm.description} onChange={event => setCompanyForm(previous => ({ ...previous, description: event.target.value }))} className={`${authFieldClass} h-24 resize-y py-3`} placeholder="企业简介（选填）" aria-label="企业简介（选填）" maxLength={2000} disabled={busy} />
      <div className="border-t border-border pt-4">
        <p className="mb-3 text-sm font-semibold text-foreground">首个 HR 管理账号</p>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="sr-only" htmlFor="company-admin-name">联系人姓名</label>
          <input id="company-admin-name" value={form.realName} onChange={event => setForm(previous => ({ ...previous, realName: event.target.value }))} className={authFieldClass} placeholder="HR 联系人姓名" autoComplete="name" aria-label="HR 联系人姓名" disabled={busy} required />
          <label className="sr-only" htmlFor="company-admin-username">登录用户名</label>
          <input id="company-admin-username" value={form.username} onChange={event => setForm(previous => ({ ...previous, username: event.target.value }))} className={authFieldClass} placeholder="登录用户名" autoComplete="username" aria-label="登录用户名" disabled={busy} required />
        </div>
      </div>
      <AuthPasswordField id="company-admin-password" label="登录密码" placeholder="登录密码，至少 8 位且包含字母和数字" value={form.password} visible={passwordVisible} disabled={busy} onChange={value => setForm(previous => ({ ...previous, password: value }))} onToggle={() => setPasswordVisible(value => !value)} />
      <label className="sr-only" htmlFor="company-admin-email">工作邮箱</label>
      <input id="company-admin-email" type="email" value={form.email} onChange={event => setForm(previous => ({ ...previous, email: event.target.value }))} className={authFieldClass} placeholder="工作邮箱（选填）" autoComplete="email" aria-label="工作邮箱（选填）" disabled={busy} />
      {error && <p role="alert" className="text-sm leading-6 text-[var(--danger-foreground)]">{error}</p>}
      <div className="grid gap-3 sm:grid-cols-[auto_1fr]">
        <Button type="button" variant="secondary" className="h-12 rounded-2xl" onClick={() => { setError(''); setStep(1) }} disabled={busy}>
          <ArrowLeft className="h-4 w-4" />
          返回验证
        </Button>
        <Button type="submit" className="h-12 rounded-2xl" disabled={busy || !enterpriseProfileReady}>
          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowRight className="h-4 w-4" />}
          {busy ? '正在创建企业…' : '创建企业账号'}
        </Button>
      </div>
    </form>}

    {step === 3 && <div className="py-4 text-center">
      <span className="mx-auto grid h-16 w-16 place-items-center rounded-full bg-[var(--success)] text-[var(--success-foreground)]"><CheckCircle2 className="h-8 w-8" /></span>
      <h2 className="mt-6 text-2xl font-bold">{mode === 'enterprise' ? '企业 HR 账号创建成功' : '账户创建成功'}</h2>
      <p className="mx-auto mt-3 max-w-[38ch] text-sm leading-6 text-muted-foreground">{mode === 'enterprise' ? <>企业租户已创建，首个 HR 账号可使用用户名、手机号或邮箱登录。{companyCode && <span className="mt-2 block font-semibold text-foreground">企业识别码：{companyCode}</span>}</> : '现在可以使用用户名、手机号或邮箱登录 AInterview。'}</p>
      <Button type="button" className="mt-7 h-12 w-full rounded-2xl" onClick={() => nav(loginHref, { replace: true })}>返回登录</Button>
    </div>}
  </AuthFlowShell>
}
