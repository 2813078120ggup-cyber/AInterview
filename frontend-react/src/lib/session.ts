export type Profile={id:string;username:string;realName:string;roles:string[]}
const profileKey='ai_interview_profile'
export function profile(){try{return JSON.parse(localStorage.getItem(profileKey)||'null') as Profile|null}catch{return null}}
export function establish(token:string,refreshToken:string,user:Profile){localStorage.setItem('access_token',token);localStorage.setItem('refresh_token',refreshToken);localStorage.setItem(profileKey,JSON.stringify(user))}
export function clearSession(){localStorage.removeItem('access_token');localStorage.removeItem('refresh_token');localStorage.removeItem(profileKey)}
