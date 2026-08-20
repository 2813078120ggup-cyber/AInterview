import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Loader2, LogOut, RefreshCw, ShieldAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ApiError, fetchCurrentProfile } from '@/lib/api'
import { AuthSessionContext, type AuthSessionValue } from '@/lib/auth-session-context'
import { clearSession, profile, PROFILE_UPDATED_EVENT, setCachedProfile, type Profile } from '@/lib/session'

type SessionState =
  | { kind: 'checking' }
  | { kind: 'anonymous' }
  | { kind: 'authenticated' }
  | { kind: 'error'; message: string }

function initialState(): SessionState {
  if (typeof window !== 'undefined' && localStorage.getItem('access_token')) return { kind: 'checking' }
  return { kind: 'anonymous' }
}

function validationError(reason: unknown): string {
  if (reason instanceof ApiError && reason.status >= 500) return '认证服务暂时不可用，请稍后重试。'
  if (reason instanceof ApiError && reason.status === 0) return '暂时无法连接认证服务，请检查网络后重试。'
  return '登录会话暂时无法确认，请重试或退出后重新登录。'
}

export function AuthSessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionState>(initialState)
  const [user, setUser] = useState<Profile | null>(null)
  const validationPromiseRef = useRef<Promise<unknown> | null>(null)
  const validationAttemptRef = useRef(0)

  const validate = useCallback((force = false) => {
    if (force) validationPromiseRef.current = null
    if (validationPromiseRef.current) return validationPromiseRef.current

    if (!localStorage.getItem('access_token')) {
      setState({ kind: 'anonymous' })
      return null
    }

    const attempt = ++validationAttemptRef.current
    setState({ kind: 'checking' })
    const request = fetchCurrentProfile()
    validationPromiseRef.current = request
    void request.then(authoritativeProfile => {
      if (validationAttemptRef.current !== attempt) return
      setCachedProfile(authoritativeProfile)
      setUser(authoritativeProfile)
      setState({ kind: 'authenticated' })
    }).catch(reason => {
      if (validationAttemptRef.current !== attempt) return
      if (reason instanceof ApiError && reason.status === 401) {
        clearSession()
        setUser(null)
        setState({ kind: 'anonymous' })
        return
      }
      setState({ kind: 'error', message: validationError(reason) })
    })
    return request
  }, [])

  useEffect(() => {
    // The promise ref deduplicates React StrictMode's development-only effect
    // replay without adding global listeners or issuing a second /me request.
    void validate()
  }, [validate])

  useEffect(() => {
    const syncSessionProfile = () => {
      const current = profile()
      setUser(current)
      setState(current ? { kind: 'authenticated' } : { kind: 'anonymous' })
    }
    window.addEventListener(PROFILE_UPDATED_EVENT, syncSessionProfile)
    return () => window.removeEventListener(PROFILE_UPDATED_EVENT, syncSessionProfile)
  }, [])

  const retry = useCallback(async () => {
    await validate(true)
  }, [validate])
  const exit = () => {
    clearSession()
    window.location.replace('/login')
  }

  if (state.kind === 'checking') {
    return <div className="grid min-h-dvh place-items-center bg-background px-6 text-center" role="status" aria-live="polite"><div><Loader2 className="mx-auto h-8 w-8 animate-spin text-[var(--accent)]" aria-hidden="true" /><p className="mt-4 text-base font-semibold">正在验证登录会话…</p><p className="mt-2 text-sm text-muted-foreground">确认账号权限后进入对应工作空间。</p></div></div>
  }

  if (state.kind === 'error') {
    return <div className="grid min-h-dvh place-items-center bg-background px-6"><div className="w-full max-w-md rounded-3xl border border-border bg-surface p-7 text-center shadow-sm"><ShieldAlert className="mx-auto h-9 w-9 text-[var(--danger-foreground)]" aria-hidden="true" /><h1 className="mt-4 text-xl font-bold">会话校验失败</h1><p className="mt-2 text-sm leading-6 text-muted-foreground" role="alert">{state.message}</p><div className="mt-6 flex flex-col justify-center gap-3 sm:flex-row"><Button type="button" onClick={() => void retry()}><RefreshCw className="h-4 w-4" aria-hidden="true" />重新验证</Button><Button type="button" variant="secondary" onClick={exit}><LogOut className="h-4 w-4" aria-hidden="true" />退出登录</Button></div></div></div>
  }

  const value: AuthSessionValue = { status: state.kind, user, retry }
  return <AuthSessionContext.Provider value={value}>{children}</AuthSessionContext.Provider>
}
