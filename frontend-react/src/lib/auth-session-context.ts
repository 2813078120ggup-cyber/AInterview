import { createContext, useContext } from 'react'
import type { Profile } from '@/lib/session'

export type AuthSessionStatus = 'loading' | 'anonymous' | 'authenticated' | 'error'

export type AuthSessionValue = {
  status: AuthSessionStatus
  user: Profile | null
  retry: () => Promise<void>
}

export const AuthSessionContext = createContext<AuthSessionValue>({
  status: 'anonymous',
  user: null,
  retry: async () => {},
})

export function useAuthSession() {
  return useContext(AuthSessionContext)
}
