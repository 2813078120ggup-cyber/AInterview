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
export async function request<T>(path:string, init:RequestInit = {}):Promise<T>{ const token=localStorage.getItem('access_token'); const response=await fetch(`${baseUrl}${path}`,{...init,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{}) ,...init.headers}}); const body=await response.json().catch(()=>({})); if(!response.ok) throw new ApiError(body.message ?? '请求失败，请稍后重试', response.status, body.code); return body.data as T }

export async function upload<T>(path:string, formData:FormData):Promise<T>{ const token=localStorage.getItem('access_token'); const response=await fetch(`${baseUrl}${path}`,{method:'POST',body:formData,headers:{...(token?{Authorization:`Bearer ${token}`}:{})}}); const body=await response.json().catch(()=>({})); if(!response.ok) throw new ApiError(body.message ?? '上传失败，请稍后重试', response.status, body.code); return body.data as T }

export async function uploadMultipart<T>(path:string, formData:FormData):Promise<T>{ return upload<T>(path, formData) }

export async function requestBlob(path:string):Promise<Blob>{ const token=localStorage.getItem('access_token'); const response=await fetch(`${baseUrl}${path}`,{headers:{...(token?{Authorization:`Bearer ${token}`}:{})}}); if(!response.ok){const body=await response.json().catch(()=>({}));throw new ApiError(body.message??'媒体加载失败', response.status, body.code)} return response.blob() }
