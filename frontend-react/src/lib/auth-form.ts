import { useCallback, useEffect, useState } from 'react'

export type ContactChannel = 'sms' | 'email'

export const authFieldClass = 'h-12 w-full rounded-2xl border border-border bg-surface px-4 text-sm outline-none transition placeholder:text-muted-foreground/60 hover:border-[color-mix(in_srgb,var(--accent)_36%,var(--border))] focus:border-[var(--accent)] focus:ring-4 focus:ring-[var(--accent)]/10 disabled:cursor-not-allowed disabled:opacity-60'
export const usernamePattern = /^[A-Za-z][A-Za-z0-9_]{3,31}$/
export const passwordPattern = /^(?=.*[A-Za-z])(?=.*\d)[!-~]{8,64}$/
export const emailPattern = /^[^@\s]+@[^@\s]+\.[^@\s]+$/
export const phonePattern = /^1\d{10}$/

const verificationCodeCooldownSeconds = 60

function readCooldownDeadline(key: string) {
  const value = Number(localStorage.getItem(key))
  return Number.isFinite(value) && value > Date.now() ? value : 0
}

export function usePersistentCooldown(key: string) {
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

  const start = useCallback((seconds = verificationCodeCooldownSeconds) => {
    const nextDeadline = Date.now() + seconds * 1000
    localStorage.setItem(key, String(nextDeadline))
    setDeadline(nextDeadline)
  }, [key])

  return { remaining, start }
}

export function identifyContact(value: string): ContactChannel | null {
  if (phonePattern.test(value)) return 'sms'
  if (emailPattern.test(value)) return 'email'
  return null
}
