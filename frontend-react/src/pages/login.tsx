import {
  motion,
  useMotionTemplate,
  useMotionValue,
  useReducedMotion,
  useSpring,
  useTransform,
} from 'framer-motion'
import {
  ArrowRight,
  BarChart3,
  Bot,
  BrainCircuit,
  CheckCircle2,
  Eye,
  EyeOff,
  FileChartColumn,
  LockKeyhole,
  Mail,
  MessageSquareMore,
  Mic,
  Phone,
  RotateCcw,
  ShieldCheck,
  UserRound,
  Video,
  Waves,
} from 'lucide-react'
import { FormEvent, MouseEvent, useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { request } from '@/lib/api'
import { establish } from '@/lib/session'

type Login = {
  token: string
  refreshToken: string
  user: { id: string; username: string; realName: string; roles: string[] }
}

const fieldClass =
  'mt-2 h-12 w-full rounded-[18px] border border-border bg-surface/72 px-4 text-sm outline-none transition duration-200 placeholder:text-muted-foreground/55 hover:border-[color-mix(in_srgb,var(--accent)_36%,var(--border))] focus:border-[var(--primary)] focus:bg-surface focus:shadow-[0_0_0_5px_color-mix(in_srgb,var(--accent)_12%,transparent)]'

const features = [
  { title: '智能追问', desc: '根据回答深入提问', icon: MessageSquareMore },
  { title: '评测报告', desc: '提供评分与改进建议', icon: FileChartColumn },
  { title: '断点续练', desc: '自动保存面试进度', icon: RotateCcw },
]

const usernamePattern = /^[A-Za-z][A-Za-z0-9_]{3,31}$/
const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d)[!-~]{8,64}$/
const usernameRule = '4–32 位，英文开头，仅限英文、数字和下划线'
const passwordRule = '8–64 位，须包含字母和数字；不支持中文或空格'
const verificationCodeCooldownSeconds = 60
const registerCodeCooldownKey = 'interviewos_register_code_cooldown_until'
const loginCodeCooldownKey = 'interviewos_login_code_cooldown_until'

function readCooldownDeadline(key: string) {
  const value = Number(localStorage.getItem(key))
  return Number.isFinite(value) && value > Date.now() ? value : 0
}

function usePersistentCooldown(key: string) {
  const [deadline, setDeadline] = useState(() => readCooldownDeadline(key))
  const [remaining, setRemaining] = useState(() => Math.max(0, Math.ceil((deadline - Date.now()) / 1000)))

  useEffect(() => {
    function tick() {
      const seconds = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
      setRemaining(seconds)
      if (!seconds && deadline) {
        localStorage.removeItem(key)
        setDeadline(0)
      }
    }
    tick()
    if (!deadline) return
    const timer = window.setInterval(tick, 250)
    return () => window.clearInterval(timer)
  }, [deadline, key])

  useEffect(() => {
    function sync(event: StorageEvent) {
      if (event.key === key) setDeadline(readCooldownDeadline(key))
    }
    window.addEventListener('storage', sync)
    return () => window.removeEventListener('storage', sync)
  }, [key])

  const start = useCallback(() => {
    const nextDeadline = Date.now() + verificationCodeCooldownSeconds * 1000
    localStorage.setItem(key, String(nextDeadline))
    setDeadline(nextDeadline)
  }, [key])

  return { remaining, start }
}

const particles = [
  [8, 74, 3, 9, 0],
  [88, 18, 2, 11, 1.2],
  [76, 38, 4, 8, 2.4],
  [12, 58, 2, 10, 1.8],
  [24, 24, 3, 12, 3.4],
  [66, 84, 2, 9, 0.6],
  [92, 50, 3, 11, 2.8],
  [46, 10, 2, 10, 4.4],
  [52, 70, 4, 8, 1],
  [32, 52, 2, 12, 5.2],
]

export function LoginPage() {
  const nav = useNavigate()
  const reduceMotion = useReducedMotion()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [busy, setBusy] = useState(false)
  const [sendingCode, setSendingCode] = useState(false)
  const [error, setError] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loginMethod, setLoginMethod] = useState<'password' | 'code'>('password')
  const [loginChannel, setLoginChannel] = useState<'sms' | 'email'>('sms')
  const [loginTarget, setLoginTarget] = useState('')
  const [loginCode, setLoginCode] = useState('')
  const [form, setForm] = useState({ username: '', password: '', realName: '', email: '', phone: '', verificationCode: '' })
  const registerCooldown = usePersistentCooldown(registerCodeCooldownKey)
  const loginCooldown = usePersistentCooldown(loginCodeCooldownKey)

  const pointerX = useMotionValue(50)
  const pointerY = useMotionValue(50)
  const smoothX = useSpring(pointerX, { stiffness: 90, damping: 22, mass: 0.3 })
  const smoothY = useSpring(pointerY, { stiffness: 90, damping: 22, mass: 0.3 })
  const tiltX = useTransform(smoothY, [0, 100], [5, -5])
  const tiltY = useTransform(smoothX, [0, 100], [-5, 5])

  const mouseGlow = useMotionTemplate`radial-gradient(circle at ${smoothX}% ${smoothY}%, rgba(155,104,71,.18), transparent 22rem)`

  function updatePointer(event: MouseEvent<HTMLElement>) {
    if (reduceMotion) return
    const rect = event.currentTarget.getBoundingClientRect()
    pointerX.set(((event.clientX - rect.left) / rect.width) * 100)
    pointerY.set(((event.clientY - rect.top) / rect.height) * 100)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      if (mode === 'register') {
        if (!usernamePattern.test(form.username)) {
          setError(`用户名格式不正确：${usernameRule}`)
          return
        }
        if (!passwordPattern.test(form.password)) {
          setError(`密码格式不正确：${passwordRule}`)
          return
        }
        if (!/^1\d{10}$/.test(form.phone.trim())) {
          setError('请输入正确的 11 位手机号')
          return
        }
        if (!form.verificationCode.trim()) {
          setError('请输入验证码')
          return
        }
        await request('/v1/auth/register', { method: 'POST', body: JSON.stringify(form) })
        setMode('login')
        setForm(previous => ({ ...previous, password: '' }))
        return
      }
      if (loginMethod === 'code') {
        const target = loginTarget.trim()
        if (loginChannel === 'sms' && !/^1\d{10}$/.test(target)) throw new Error('请输入正确的 11 位手机号')
        if (loginChannel === 'email' && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(target)) throw new Error('请输入正确的邮箱')
        if (!loginCode.trim()) throw new Error('请输入验证码')
        const result = await request<Login>('/v1/auth/login/code', {
          method: 'POST',
          body: JSON.stringify({ channel: loginChannel, target, verificationCode: loginCode.trim() }),
        })
        establish(result.token, result.refreshToken, result.user)
        nav(result.user.roles.includes('ADMIN') ? '/admin/interviews' : '/candidate/interviews', { replace: true })
        return
      }
      const result = await request<Login>('/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username: form.username, password: form.password }),
      })
      establish(result.token, result.refreshToken, result.user)
      nav(result.user.roles.includes('ADMIN') ? '/admin/interviews' : '/candidate/interviews', { replace: true })
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : '登录失败，请检查账号信息。'
      if (mode === 'login' && loginMethod === 'code' && message.includes('还未注册')) {
        setForm(previous => loginChannel === 'sms' ? { ...previous, phone: loginTarget.trim() } : { ...previous, email: loginTarget.trim() })
        setMode('register')
        setError('该账号尚未注册，请先完成注册。')
      } else {
        setError(message)
      }
    } finally {
      setBusy(false)
    }
  }

  async function sendRegisterCode() {
    setError('')
    const phone = form.phone.trim()
    if (!/^1\d{10}$/.test(phone)) {
      setError('请输入正确的 11 位手机号')
      return
    }
    setSendingCode(true)
    try {
      await request('/v1/auth/register/code', { method: 'POST', body: JSON.stringify({ phone, email: form.email.trim() || undefined }) })
      registerCooldown.start()
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : '验证码发送失败，请稍后重试。'
      if (message.includes('发送过于频繁')) registerCooldown.start()
      setError(message)
    } finally {
      setSendingCode(false)
    }
  }

  async function sendLoginCode() {
    setError('')
    const target = loginTarget.trim()
    if (loginChannel === 'sms' && !/^1\d{10}$/.test(target)) {
      setError('请输入正确的 11 位手机号')
      return
    }
    if (loginChannel === 'email' && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(target)) {
      setError('请输入正确的邮箱')
      return
    }
    setSendingCode(true)
    try {
      await request('/v1/auth/login/code/send', { method: 'POST', body: JSON.stringify({ channel: loginChannel, target }) })
      loginCooldown.start()
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : '验证码发送失败，请稍后重试。'
      if (message.includes('发送过于频繁')) loginCooldown.start()
      if (message.includes('还未注册')) {
        setForm(previous => loginChannel === 'sms' ? { ...previous, phone: target } : { ...previous, email: target })
        setMode('register')
        setError('该账号尚未注册，请先完成注册。')
      } else {
        setError(message)
      }
    } finally {
      setSendingCode(false)
    }
  }

  return (
    <main
      onMouseMove={updatePointer}
      className="login-page-shell relative min-h-dvh overflow-hidden bg-[#f5f3ee] p-2 text-foreground sm:p-6 lg:p-8"
    >
      <motion.div
        aria-hidden
        className="pointer-events-none absolute inset-0"
        style={{ background: reduceMotion ? 'transparent' : mouseGlow }}
        animate={reduceMotion ? undefined : { opacity: [0.62, 0.9, 0.62] }}
        transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }}
      />
      <div className="pointer-events-none absolute left-[4%] top-[5%] h-72 w-72 rounded-full bg-[var(--brand)]/12 blur-3xl" />
      <div className="pointer-events-none absolute bottom-[8%] right-[10%] h-80 w-80 rounded-full bg-[var(--brand-pink)]/10 blur-3xl" />

      <section className="relative mx-auto grid min-h-[calc(100dvh-1rem)] w-full max-w-6xl gap-5 rounded-[22px] border border-border bg-[#efede7]/80 p-2 shadow-[0_26px_90px_rgba(20,18,17,.10)] backdrop-blur sm:min-h-[calc(100dvh-3rem)] sm:rounded-[28px] sm:p-4 lg:grid-cols-[1.12fr_.88fr] lg:p-5">
        <motion.div
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.55, ease: 'easeOut' }}
          className="login-template-panel relative hidden overflow-hidden rounded-[24px] border border-[#ddd7cd] bg-[linear-gradient(165deg,#fbfaf6_0%,#eee7dd_100%)] p-8 lg:grid lg:grid-rows-[auto_auto_minmax(310px,1fr)_auto] lg:gap-5"
          style={reduceMotion ? undefined : { rotateX: tiltX, rotateY: tiltY, transformPerspective: 1400 }}
        >
          <div className="absolute inset-0 login-particle-layer">
            {particles.map(([left, top, size, duration, delay], index) => (
              <span
                key={index}
                className="login-particle"
                style={{
                  left: `${left}%`,
                  top: `${top}%`,
                  width: size,
                  height: size,
                  animationDuration: `${duration}s`,
                  animationDelay: `${delay}s`,
                }}
              />
            ))}
          </div>

          <div className="relative z-10 flex items-center gap-4">
            <span className="grid h-12 w-12 place-items-center rounded-[18px] bg-[#151412] text-white shadow-[0_18px_48px_rgba(20,18,17,.16)]">
              <Bot className="h-5 w-5" />
            </span>
            <div>
              <strong className="text-xl leading-none tracking-[-0.03em]">AInterview</strong>
              <p className="mt-1.5 text-sm text-muted-foreground">智能多模态面试评测平台</p>
            </div>
          </div>

          <div className="relative z-10 mt-1 max-w-[620px]">
            <motion.p
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.12, duration: 0.4 }}
              className="text-xs font-bold uppercase tracking-[0.22em] text-[var(--accent)]"
            >
              智能面试工作空间
            </motion.p>
            <motion.h1
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.18, duration: 0.48 }}
              className="login-hero-title mt-5 max-w-[620px]"
            >
              <span className="block text-[clamp(3.25rem,4.7vw,5.2rem)] font-black leading-[0.98] tracking-[-0.065em]">
                每一面，
              </span>
              <span className="login-hero-keyword mt-2 block text-[clamp(3.25rem,4.7vw,5.2rem)] font-black leading-[0.98] tracking-[-0.065em]">
                都算数。
              </span>
            </motion.h1>
            <motion.p
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.25, duration: 0.45 }}
              className="mt-6 max-w-[540px] text-base leading-8 text-muted-foreground"
            >
              通过智能追问、语音作答、能力画像与评测报告，提升面试准备效率。
            </motion.p>
          </div>

          <div className="relative z-10 grid min-h-[310px] place-items-center py-1">
            <div className="login-ai-stage w-full max-w-[440px]">
              <span className="login-pulse-ring" />
              <span className="login-pulse-ring delay-1000" />
              <span className="login-pulse-ring delay-2000" />
              <span className="login-dashed-orbit" />
              <span className="login-solid-orbit" />
              <svg className="login-neural-svg" viewBox="0 0 260 260" aria-hidden>
                <line x1="130" y1="130" x2="205" y2="65" className="login-neural-line" />
                <line x1="130" y1="130" x2="205" y2="195" className="login-neural-line delay-200" />
                <line x1="130" y1="130" x2="55" y2="195" className="login-neural-line delay-500" />
                <line x1="130" y1="130" x2="55" y2="65" className="login-neural-line delay-700" />
              </svg>
              <span className="login-ai-core">
                <BrainCircuit className="h-6 w-6" />
              </span>
              {[Mic, Video, FileChartColumn, BarChart3].map((Icon, index) => (
                <span key={index} className="login-orbit-icon" style={{ animationDelay: `${index * -2.75}s` }}>
                  <Icon className="h-4 w-4" />
                </span>
              ))}
              <div className="login-waveform">
                {Array.from({ length: 11 }).map((_, index) => (
                  <span key={index} className="login-wave-bar" style={{ animationDelay: `${index * 0.08}s` }} />
                ))}
              </div>
            </div>
          </div>

          <div className="relative z-10 grid grid-cols-3 gap-3">
            {features.map(({ title, desc, icon: Icon }, index) => (
              <motion.div
                key={title}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.36 + index * 0.08, duration: 0.38 }}
                whileHover={reduceMotion ? undefined : { y: -6, scale: 1.018 }}
                whileTap={reduceMotion ? undefined : { scale: 0.985 }}
                className="login-magnetic-card login-feature-card rounded-2xl border border-[#ded8ce] bg-white/72 p-3 shadow-[0_10px_34px_rgba(20,18,17,.05)] backdrop-blur"
              >
                <div className="flex h-full items-center gap-3">
                  <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-[#f0ece5] text-[var(--accent)]">
                    <Icon className="h-4 w-4" />
                  </span>
                  <div className="min-w-0">
                    <p className="truncate font-bold leading-5">{title}</p>
                    <p className="mt-0.5 truncate whitespace-nowrap text-[11px] leading-5 text-muted-foreground">{desc}</p>
                  </div>
                </div>
              </motion.div>
            ))}
          </div>
        </motion.div>

        <section className="relative grid min-h-[calc(100dvh-2rem)] place-items-center px-0 py-5 sm:min-h-[calc(100dvh-4rem)] sm:px-6 sm:py-8 lg:min-h-0">
          <motion.form
            onSubmit={submit}
            initial={{ opacity: 0, y: 18, scale: 0.985 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            transition={{ delay: 0.08, duration: 0.48, ease: 'easeOut' }}
            className="relative w-full max-w-md rounded-[22px] border border-[#ddd7cd] bg-white/86 p-5 shadow-[0_22px_70px_rgba(20,18,17,.10)] backdrop-blur-xl sm:rounded-[28px] sm:p-8"
          >
            <motion.div
              aria-hidden
              className="pointer-events-none absolute -inset-px rounded-[28px] opacity-70"
              style={{
                background:
                  'linear-gradient(145deg, rgba(255,255,255,.72), transparent 34%, rgba(155,104,71,.10))',
              }}
            />
            <div className="relative">
              <div className="flex flex-col items-start gap-3 min-[400px]:flex-row min-[400px]:items-center min-[400px]:justify-between">
                <div className="flex items-center gap-3 lg:hidden">
                  <span className="grid h-11 w-11 place-items-center rounded-2xl bg-[#151412] text-white">
                    <Bot className="h-5 w-5" />
                  </span>
                  <div>
                    <strong>AInterview</strong>
                    <p className="text-xs text-muted-foreground">智能面试评测平台</p>
                  </div>
                </div>
                <span className="inline-flex items-center gap-2 rounded-full border border-border bg-[#f5f3ee] px-3 py-1.5 text-xs font-semibold text-muted-foreground min-[400px]:ml-auto">
                  <span className="login-status-dot" />
                  AI 服务可用
                </span>
              </div>

              <div className="mt-8">
                <motion.p
                  key={`${mode}-eyebrow`}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="text-xs font-bold uppercase tracking-[0.12em] text-[var(--accent)]"
                >
                  AInterview 账户
                </motion.p>
                <motion.h2
                  key={`${mode}-title`}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="mt-2 text-3xl font-black tracking-[-0.035em]"
                >
                  {mode === 'login' ? '登录工作空间' : '创建候选人账户'}
                </motion.h2>
                <p className="mt-2 text-sm leading-6 text-muted-foreground">
                  {mode === 'login'
                    ? '系统将根据账户角色进入对应工作空间。'
                    : '新账户默认进入候选人端，可进行模拟练习并查看报告。'}
                </p>
              </div>

              <div className="mt-6 grid grid-cols-2 rounded-full border border-border bg-[#f4f0e9] p-1 text-sm font-bold">
                {(['login', 'register'] as const).map(item => (
                  <button
                    key={item}
                    type="button"
                    onClick={() => {
                      setMode(item)
                      setError('')
                    }}
                    className={`relative rounded-full px-4 py-2.5 transition ${
                      mode === item ? 'text-white shadow-[0_12px_28px_rgba(20,18,17,.13)]' : 'text-muted-foreground hover:text-foreground'
                    }`}
                  >
                    {mode === item && (
                      <motion.span
                        layoutId="login-mode-pill"
                        className="absolute inset-0 rounded-full bg-[#151412]"
                        transition={{ type: 'spring', stiffness: 360, damping: 32 }}
                      />
                    )}
                    <span className="relative">{item === 'login' ? '账号登录' : '候选人注册'}</span>
                  </button>
                ))}
              </div>

              {mode === 'login' && <div className="mt-4 grid grid-cols-2 rounded-2xl border border-border bg-[#f3efe8] p-1 text-sm font-bold">
                <button type="button" onClick={() => { setLoginMethod('password'); setError('') }} className={`rounded-xl px-3 py-2.5 transition ${loginMethod === 'password' ? 'bg-[#151412] text-white shadow-sm' : 'text-muted-foreground hover:text-foreground'}`}>账号密码</button>
                <button type="button" onClick={() => { setLoginMethod('code'); setError('') }} className={`rounded-xl px-3 py-2.5 transition ${loginMethod === 'code' ? 'bg-[#151412] text-white shadow-sm' : 'text-muted-foreground hover:text-foreground'}`}>验证码登录</button>
              </div>}

              {error && (
                <motion.p
                  initial={{ opacity: 0, y: -6 }}
                  animate={{ opacity: 1, y: 0 }}
                  role="alert"
                  className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
                >
                  {error}
                </motion.p>
              )}

              <div className="mt-6 space-y-5">
                {mode === 'register' && (
                  <label className="block text-sm font-bold">
                    姓名
                    <div className="relative">
                      <UserRound className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                      <input
                        value={form.realName}
                        onChange={event => setForm({ ...form, realName: event.target.value })}
                        className={`${fieldClass} pl-11`}
                        required
                        placeholder="请输入真实姓名"
                      />
                    </div>
                  </label>
                )}

                {(mode === 'register' || loginMethod === 'password') && <>
                <label className="block text-sm font-bold">
                  用户名
                  <div className="relative">
                    <UserRound className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                    <input
                      value={form.username}
                      onChange={event => setForm({ ...form, username: event.target.value })}
                      className={`${fieldClass} pl-11`}
                      required
                      maxLength={mode === 'register' ? 32 : undefined}
                      pattern={mode === 'register' ? '[A-Za-z][A-Za-z0-9_]{3,31}' : undefined}
                      autoComplete="username"
                      placeholder="例如 candidate_liu"
                    />
                  </div>
                  {mode === 'register' && <p className="mt-2 text-xs font-normal leading-5 text-muted-foreground">{usernameRule}</p>}
                </label>

                <label className="block text-sm font-bold">
                  密码
                  <div className="relative">
                    <LockKeyhole className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={form.password}
                      onChange={event => setForm({ ...form, password: event.target.value })}
                      className={`${fieldClass} pl-11 pr-12`}
                      required
                      minLength={8}
                      maxLength={mode === 'register' ? 64 : undefined}
                      autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                      placeholder={mode === 'register' ? '请输入安全密码' : '请输入密码'}
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(value => !value)}
                      className="absolute right-3 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-full text-muted-foreground transition hover:bg-muted hover:text-foreground"
                      aria-label={showPassword ? '隐藏密码' : '显示密码'}
                    >
                      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  {mode === 'register' && <p className="mt-2 text-xs font-normal leading-5 text-muted-foreground">{passwordRule}</p>}
                </label>
                </>}

                {mode === 'login' && loginMethod === 'code' && <>
                  <div className="grid grid-cols-2 gap-2 rounded-2xl border border-border bg-[#f3efe8] p-1 text-xs font-bold">
                    {(['sms', 'email'] as const).map(channel => <button key={channel} type="button" onClick={() => setLoginChannel(channel)} className={`h-10 rounded-xl transition ${loginChannel === channel ? 'bg-[#151412] text-white' : 'text-muted-foreground hover:bg-white/70 hover:text-foreground'}`}>{channel === 'sms' ? '短信验证码' : '邮箱验证码'}</button>)}
                  </div>
                  <label className="block text-sm font-bold">
                    {loginChannel === 'sms' ? '手机号' : '邮箱'}
                    <div className="relative">
                      {loginChannel === 'sms' ? <Phone className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" /> : <Mail className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />}
                      <input type={loginChannel === 'email' ? 'email' : 'tel'} value={loginTarget} onChange={event => setLoginTarget(event.target.value)} className={`${fieldClass} pl-11`} autoComplete={loginChannel === 'email' ? 'email' : 'tel'} placeholder={loginChannel === 'sms' ? '请输入注册手机号' : '请输入注册邮箱'} />
                    </div>
                  </label>
                  <label className="block text-sm font-bold">
                    验证码
                    <div className="mt-2 flex gap-2">
                      <input value={loginCode} onChange={event => setLoginCode(event.target.value)} className="h-12 min-w-0 flex-1 rounded-[18px] border border-border bg-surface px-4 text-sm outline-none focus:border-[var(--primary)]" maxLength={6} autoComplete="one-time-code" placeholder="请输入验证码" />
                      <Button type="button" className="h-12 min-w-28 shrink-0 rounded-[18px] px-4" disabled={sendingCode || loginCooldown.remaining > 0} onClick={() => void sendLoginCode()}>{sendingCode ? '发送中' : loginCooldown.remaining > 0 ? `${loginCooldown.remaining}s 后重发` : '发送验证码'}</Button>
                    </div>
                  </label>
                </>}

                {mode === 'register' && (
                  <label className="block text-sm font-bold">
                    手机号
                    <div className="relative">
                      <Phone className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                      <input
                        value={form.phone}
                        onChange={event => setForm({ ...form, phone: event.target.value })}
                        className={`${fieldClass} pl-11`}
                        required
                        maxLength={11}
                        pattern="1\d{10}"
                        placeholder="用于接收注册验证码"
                      />
                    </div>
                  </label>
                )}

                {mode === 'register' && (
                  <label className="block text-sm font-bold">
                    邮箱（可选）
                    <div className="relative">
                      <Mail className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                      <input
                        type="email"
                        value={form.email}
                        onChange={event => setForm({ ...form, email: event.target.value })}
                        className={`${fieldClass} pl-11`}
                        placeholder="用于后续找回和通知"
                      />
                    </div>
                  </label>
                )}

                {mode === 'register' && (
                  <div className="rounded-2xl border border-border bg-[#f3efe8] p-3">
                    <div className="flex gap-2">
                      <input
                        value={form.verificationCode}
                        onChange={event => setForm({ ...form, verificationCode: event.target.value })}
                        className="h-11 min-w-0 flex-1 rounded-xl border border-border bg-white px-4 text-sm outline-none focus:border-[var(--accent)]"
                        required
                        maxLength={6}
                        placeholder="请输入验证码"
                      />
                      <button
                        type="button"
                        onClick={() => void sendRegisterCode()}
                        disabled={sendingCode || registerCooldown.remaining > 0}
                        className="h-11 min-w-28 shrink-0 rounded-xl bg-[#151412] px-4 text-sm font-bold text-white disabled:opacity-60"
                      >
                        {sendingCode ? '发送中' : registerCooldown.remaining > 0 ? `${registerCooldown.remaining}s 后重发` : '发送验证码'}
                      </button>
                    </div>
                  </div>
                )}
              </div>

              <Button className="login-primary-button group mt-7 h-12 w-full rounded-[18px] bg-[#151412] text-white shadow-[0_16px_38px_rgba(20,18,17,.16)] transition hover:-translate-y-0.5 hover:bg-[#24211d]" disabled={busy}>
                {busy ? '正在处理…' : mode === 'register' ? '创建账户' : loginMethod === 'code' ? '验证码登录' : '登录工作空间'}
                {busy ? <Waves className="h-4 w-4 animate-pulse" /> : <ArrowRight className="h-4 w-4 transition group-hover:translate-x-1" />}
              </Button>

              <div className="mt-6 flex items-start gap-3 rounded-2xl border border-border bg-[#f3efe8] px-4 py-3 text-xs leading-5 text-muted-foreground">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-[var(--accent)]" />
                <span>{mode === 'login' && loginMethod === 'code' ? '验证码仅用于已注册账户；未注册手机号或邮箱将转至注册流程。' : '账户信息将安全校验，登录后进入对应工作空间。'}</span>
              </div>

              <div className="mt-4 grid gap-2 text-[11px] font-semibold text-muted-foreground min-[400px]:grid-cols-3">
                {['模型服务可用', '支持语音作答', '自动生成报告'].map(text => (
                  <span key={text} className="inline-flex items-center justify-center gap-1 rounded-full border border-border bg-white/60 px-2 py-2">
                    <CheckCircle2 className="h-3.5 w-3.5 text-[var(--accent)]" />
                    {text}
                  </span>
                ))}
              </div>
            </div>
          </motion.form>
        </section>
      </section>
    </main>
  )
}
