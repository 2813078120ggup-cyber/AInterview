import { removeCachedAvatar } from '@/lib/avatar-cache'

export type Profile={id:string;username:string;realName:string;roles:string[];companyId?:string;avatarAvailable?:boolean;avatarRevision?:number}
const profileKey='ai_interview_profile'
export const PROFILE_UPDATED_EVENT = 'ai-interview-profile-updated'

function notifyProfileUpdated(){
  if(typeof window !== 'undefined') window.dispatchEvent(new Event(PROFILE_UPDATED_EVENT))
}

export function profile(){try{return JSON.parse(localStorage.getItem(profileKey)||'null') as Profile|null}catch{return null}}
export function establish(token:string,refreshToken:string,user:Profile){localStorage.setItem('access_token',token);localStorage.setItem('refresh_token',refreshToken);localStorage.setItem(profileKey,JSON.stringify(user));notifyProfileUpdated()}
export function rotateSessionTokens(token:string,refreshToken:string){localStorage.setItem('access_token',token);localStorage.setItem('refresh_token',refreshToken)}
export function updateLocalProfile(patch: Partial<Profile>){const current=profile();if(!current)return;localStorage.setItem(profileKey,JSON.stringify({...current,...patch}));notifyProfileUpdated()}
export function updateLocalAvatar(available:boolean){const current=profile();if(!current)return;if(!available)removeCachedAvatar(current.id);updateLocalProfile({avatarAvailable:available,avatarRevision:Date.now()})}
export function clearSession(){const current=profile();removeCachedAvatar(current?.id);localStorage.removeItem('access_token');localStorage.removeItem('refresh_token');localStorage.removeItem(profileKey);notifyProfileUpdated()}
