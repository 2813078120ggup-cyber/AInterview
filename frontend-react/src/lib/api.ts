import { clearSession, rotateSessionTokens } from '@/lib/session'
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
type RefreshResponse = { token: string; refreshToken: string }
const authPathsWithoutRefresh = new Set([
  '/v1/auth/login',
  '/v1/auth/login/code',
  '/v1/auth/login/code/send',
  '/v1/auth/register',
  '/v1/auth/register/code',
  '/v1/auth/password/reset/code',
  '/v1/auth/password/reset',
  '/v1/auth/refresh',
])

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
    if (!response.ok || !body.data?.token || !body.data.refreshToken) return false
    rotateSessionTokens(body.data.token, body.data.refreshToken)
    return true
  }).catch(() => false).finally(() => {
    refreshPromise = null
  })

  return refreshPromise
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
