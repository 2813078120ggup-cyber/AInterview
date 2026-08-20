import { clearSession, rotateSessionTokens, type Profile } from '@/lib/session'
import { loginPath } from '@/lib/navigation'

export const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api'
export class ApiError extends Error {
  readonly status: number
  readonly code?: number

  constructor(message: string, status: number, code?: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export type Interview = { id:string; title:string; scheduledAt:string; duration:number; status:number; remark?:string }
export type PracticeBank = { id:string; name:string; description?:string; questionCount:string }
export type TrainingDay = { day:number; title:string; tasks:string[] }
export type TrainingPlan = {
  priority:string
  durationDays:number
  focusAreas:string[]
  dailyPlan:TrainingDay[]
  recommendedBanks:string[]
  interviewDrills:string[]
  successCriteria:string[]
  generationMethod:string
}

type ApiEnvelope<T> = { data?: T; message?: string; code?: number }
type RefreshResponse = { token: string; refreshToken: string; user: Profile }
const authPathsWithoutRefresh = new Set([
  '/v1/auth/login',
  '/v1/auth/login/code',
  '/v1/auth/login/code/send',
  '/v1/auth/register',
  '/v1/auth/company/register',
  '/v1/auth/register/code',
  '/v1/auth/captcha/challenge',
  '/v1/auth/password/reset/code',
  '/v1/auth/password/reset/verify',
  '/v1/auth/password/reset/complete',
  '/v1/auth/password/reset',
  '/v1/auth/refresh',
])

export type CaptchaPurpose = 'PASSWORD_LOGIN' | 'LOGIN_CODE_SEND' | 'REGISTER_CODE_SEND' | 'PASSWORD_RESET_CODE_SEND'
export type CaptchaChallenge = { challengeId: string; imageDataUrl: string; expiresInSeconds: number }

export function createCaptchaChallenge(purpose: CaptchaPurpose) {
  return request<CaptchaChallenge>('/v1/auth/captcha/challenge', {
    method: 'POST',
    body: JSON.stringify({ purpose }),
  })
}

let refreshPromise: Promise<boolean> | null = null

function expireLocalSession() {
  clearSession()
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.replace(loginPath(`${window.location.pathname}${window.location.search}${window.location.hash}`))
  }
}

async function refreshAccessToken(): Promise<boolean> {
  if (refreshPromise) return refreshPromise
  const refreshToken = localStorage.getItem('refresh_token')
  if (!refreshToken) return false

  refreshPromise = fetch(`${baseUrl}/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  }).then(async response => {
    const body = await response.json().catch(() => ({})) as ApiEnvelope<RefreshResponse>
    if (!response.ok || !body.data?.token || !body.data.refreshToken || !body.data.user) return false
    rotateSessionTokens(body.data.token, body.data.refreshToken, body.data.user)
    return true
  }).catch(() => false).finally(() => {
    refreshPromise = null
  })

  return refreshPromise
}

/**
 * Validate the browser session at application startup. A single 401 is
 * recovered through the existing single-flight refresh path; only a failed
 * refresh or a second 401 is returned to the provider for session cleanup.
 */
export async function fetchCurrentProfile(): Promise<Profile> {
  const token = localStorage.getItem('access_token')
  if (!token) throw new ApiError('登录会话不存在', 401)

  const send = async () => {
    const currentToken = localStorage.getItem('access_token')
    if (!currentToken) throw new ApiError('登录会话不存在', 401)
    try {
      return await fetch(`${baseUrl}/v1/auth/me`, {
        method: 'GET',
        headers: { Authorization: `Bearer ${currentToken}` },
      })
    } catch {
      throw new ApiError('暂时无法连接认证服务', 0)
    }
  }

  let response = await send()
  if (response.status === 401) {
    // Reuse the existing single-flight refresh so a normally expired access
    // token does not log the user out when the refresh token is still valid.
    if (!await refreshAccessToken()) throw new ApiError('登录会话已失效', 401)
    response = await send()
  }

  const body = await response.json().catch(() => ({})) as ApiEnvelope<Profile>
  if (!response.ok) {
    throw new ApiError(body.message ?? '登录会话校验失败', response.status, body.code)
  }
  if (!body.data) throw new ApiError('认证服务返回了无效的会话资料', 502)
  return body.data
}

function requestHeaders(init: RequestInit, json: boolean) {
  const headers = new Headers(init.headers)
  const token = localStorage.getItem('access_token')
  if (json && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${token}`)
  return headers
}

async function authenticatedFetch(path: string, init: RequestInit = {}, json = true): Promise<Response> {
  const send = () => fetch(`${baseUrl}${path}`, { ...init, headers: requestHeaders(init, json) })
  let response = await send()
  const refreshable = response.status === 401 && !authPathsWithoutRefresh.has(path.split('?', 1)[0])
      && Boolean(localStorage.getItem('refresh_token'))
  if (!refreshable) return response

  if (!await refreshAccessToken()) {
    expireLocalSession()
    return response
  }
  response = await send()
  if (response.status === 401) expireLocalSession()
  return response
}

export async function request<T>(path:string, init:RequestInit = {}):Promise<T>{ const response=await authenticatedFetch(path,init); const body=await response.json().catch(()=>({})) as ApiEnvelope<T>; if(!response.ok) throw new ApiError(body.message ?? '请求失败，请稍后重试', response.status, body.code); return body.data as T }

export async function upload<T>(path:string, formData:FormData):Promise<T>{ const response=await authenticatedFetch(path,{method:'POST',body:formData},false); const body=await response.json().catch(()=>({})) as ApiEnvelope<T>; if(!response.ok) throw new ApiError(body.message ?? '上传失败，请稍后重试', response.status, body.code); return body.data as T }

export async function uploadMultipart<T>(path:string, formData:FormData):Promise<T>{ return upload<T>(path, formData) }

export async function requestBlob(path:string):Promise<Blob>{ const response=await authenticatedFetch(path,{},false); if(!response.ok){const body=await response.json().catch(()=>({})) as ApiEnvelope<never>;throw new ApiError(body.message??'媒体加载失败', response.status, body.code)} return response.blob() }
