import { useEffect, useState } from 'react'

const THEME_STORAGE_KEY = 'interviewos-theme'

function storedDarkMode() {
  if (typeof window === 'undefined') return false
  return window.localStorage.getItem(THEME_STORAGE_KEY) === 'dark'
}

export function applyTheme(dark: boolean) {
  document.documentElement.classList.toggle('dark', dark)
  window.localStorage.setItem(THEME_STORAGE_KEY, dark ? 'dark' : 'light')
}

export function initializeTheme() {
  applyTheme(storedDarkMode())
}

export function useTheme() {
  const [dark, setDark] = useState(storedDarkMode)
  useEffect(() => applyTheme(dark), [dark])
  return { dark, toggleTheme: () => setDark(value => !value) }
}
