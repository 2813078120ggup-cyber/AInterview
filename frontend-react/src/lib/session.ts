import { removeCachedAvatar } from '@/lib/avatar-cache'

export type Profile = {
  id: string
  username: string
  realName: string
  roles: string[]
  companyId?: string
  avatarAvailable?: boolean
  avatarRevision?: number
}

const profileKey = 'ai_interview_profile'
export const PROFILE_UPDATED_EVENT = 'ai-interview-profile-updated'

function notifyProfileUpdated() {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(PROFILE_UPDATED_EVENT))
}

function readCachedProfile(): Profile | null {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(profileKey) || 'null')
    if (!value || typeof value !== 'object') return null
    const candidate = value as Partial<Profile> & { id?: string | number; companyId?: string | number }
    const id = typeof candidate.id === 'number' ? String(candidate.id) : candidate.id
    const companyId = typeof candidate.companyId === 'number' ? String(candidate.companyId) : candidate.companyId
    if (!id || typeof candidate.username !== 'string' || !Array.isArray(candidate.roles)) return null
    return { ...candidate, id, companyId, roles: candidate.roles.filter((role): role is string => typeof role === 'string') } as Profile
  } catch {
    return null
  }
}

/**
 * The profile cache is only useful once an access token exists. This keeps a
 * stale profile from making the router treat a logged-out browser as signed in.
 * The authoritative startup check is performed by AuthSessionProvider.
 */
export function profile(): Profile | null {
  if (typeof window === 'undefined' || !localStorage.getItem('access_token')) return null
  return readCachedProfile()
}

/** Store the server-provided profile, replacing stale role/company data. */
export function setCachedProfile(user: Profile) {
  localStorage.setItem(profileKey, JSON.stringify(user))
  notifyProfileUpdated()
}

export function establish(token: string, refreshToken: string, user: Profile) {
  localStorage.setItem('access_token', token)
  localStorage.setItem('refresh_token', refreshToken)
  setCachedProfile(user)
}

export function rotateSessionTokens(token: string, refreshToken: string, user?: Profile) {
  localStorage.setItem('access_token', token)
  localStorage.setItem('refresh_token', refreshToken)
  if (user) setCachedProfile(user)
}

export function updateLocalProfile(patch: Partial<Profile>) {
  const current = profile()
  if (!current) return
  setCachedProfile({ ...current, ...patch })
}

export function updateLocalAvatar(available: boolean) {
  const current = profile()
  if (!current) return
  if (!available) removeCachedAvatar(current.id)
  updateLocalProfile({ avatarAvailable: available, avatarRevision: Date.now() })
}

export function clearSession() {
  const current = readCachedProfile()
  if (current) removeCachedAvatar(current.id)
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem(profileKey)
  notifyProfileUpdated()
}
