import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react'
import { ImageOff, Loader2, RefreshCw } from 'lucide-react'
import { createCaptchaChallenge, type CaptchaChallenge, type CaptchaPurpose } from '@/lib/api'
import { authFieldClass } from '@/lib/auth-form'

export type ImageCaptchaHandle = { reset: () => void }

type ImageCaptchaProps = {
  purpose: CaptchaPurpose
  value: string
  onChange: (value: string) => void
  onChallengeChange: (challenge: CaptchaChallenge | null) => void
  disabled?: boolean
}

const pngDataUrlPattern = /^data:image\/png;base64,[A-Za-z0-9+/=\r\n]+$/

export const ImageCaptcha = forwardRef<ImageCaptchaHandle, ImageCaptchaProps>(function ImageCaptcha({ purpose, value, onChange, onChallengeChange, disabled = false }, ref) {
  const [challenge, setChallenge] = useState<CaptchaChallenge | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const loadedPurpose = useRef<CaptchaPurpose | null>(null)
  const challengeRef = useRef<CaptchaChallenge | null>(null)
  const inFlight = useRef<Promise<void> | null>(null)
  const onChangeRef = useRef(onChange)
  const onChallengeChangeRef = useRef(onChallengeChange)

  useEffect(() => { onChangeRef.current = onChange }, [onChange])
  useEffect(() => { onChallengeChangeRef.current = onChallengeChange }, [onChallengeChange])

  const loadChallenge = useCallback(async (force = false) => {
    if (!force && loadedPurpose.current === purpose && challengeRef.current) return
    if (!force && inFlight.current) return inFlight.current
    setLoading(true)
    setError('')
    const request = createCaptchaChallenge(purpose).then(next => {
      if (!pngDataUrlPattern.test(next.imageDataUrl)) throw new Error('图形验证码图片格式无效，请刷新重试。')
      loadedPurpose.current = purpose
      challengeRef.current = next
      setChallenge(next)
      onChangeRef.current('')
      onChallengeChangeRef.current(next)
    }).catch(reason => {
      challengeRef.current = null
      setChallenge(null)
      onChallengeChangeRef.current(null)
      setError(reason instanceof Error ? reason.message : '图形验证码暂时不可用，请刷新重试。')
    }).finally(() => {
      inFlight.current = null
      setLoading(false)
    })
    inFlight.current = request
    return request
  }, [purpose])

  useEffect(() => {
    loadedPurpose.current = null
    challengeRef.current = null
    setChallenge(null)
    setError('')
    onChangeRef.current('')
    onChallengeChangeRef.current(null)
    void loadChallenge()
  }, [loadChallenge, purpose])

  const reset = useCallback(() => {
    loadedPurpose.current = null
    challengeRef.current = null
    setChallenge(null)
    setError('')
    onChangeRef.current('')
    onChallengeChangeRef.current(null)
    void loadChallenge(true)
  }, [loadChallenge])

  useImperativeHandle(ref, () => ({ reset }), [reset])

  const captchaFieldId = `${purpose}-captcha-code`

  return <div className="w-full">
    <div className={`${authFieldClass} flex items-center gap-2 focus-within:border-[var(--accent)] focus-within:ring-4 focus-within:ring-[var(--accent)]/10 ${disabled ? 'cursor-not-allowed opacity-60' : ''}`}>
      <label className="sr-only" htmlFor={captchaFieldId}>图形验证码</label>
      <input id={captchaFieldId} value={value} onChange={event => onChange(event.target.value.toUpperCase().slice(0, 4))} disabled={disabled || loading || !challenge} autoComplete="off" inputMode="text" maxLength={4} spellCheck={false} className="h-full min-w-0 flex-1 bg-transparent py-0 text-sm outline-none placeholder:text-muted-foreground/60 disabled:cursor-not-allowed" placeholder="图形验证码" aria-label="4位图形验证码" />
      <div className="flex h-9 w-[112px] shrink-0 items-center justify-center overflow-hidden border-l border-border pl-3" aria-live="polite">
        {loading ? <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground"><Loader2 className="h-3.5 w-3.5 animate-spin" />加载中</span> : challenge ? <img src={challenge.imageDataUrl} alt="图形验证码" className="h-8 w-auto max-w-full object-contain" /> : <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground"><ImageOff className="h-3.5 w-3.5" />加载失败</span>}
      </div>
      <button type="button" onClick={reset} disabled={disabled || loading} className="grid h-10 w-10 shrink-0 place-items-center rounded-xl text-muted-foreground transition hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] disabled:cursor-not-allowed disabled:opacity-50" aria-label="刷新图形验证码" title="看不清可点击刷新">
        <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
      </button>
    </div>
    {error && <p role="alert" className="mt-2 text-xs leading-5 text-[var(--danger-foreground)]">{error}</p>}
  </div>
})
