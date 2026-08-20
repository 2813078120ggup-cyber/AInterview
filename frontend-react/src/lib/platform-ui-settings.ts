import { createContext, useContext } from 'react'

export type PlatformUiSettings = {
  mouseFollowerEnabled: boolean
}

export type PlatformUiSettingsContextValue = {
  settings: PlatformUiSettings
  loading: boolean
  saving: boolean
  updateMouseFollowerEnabled: (enabled: boolean) => Promise<boolean>
}

export const defaultPlatformUiSettings: PlatformUiSettings = { mouseFollowerEnabled: true }

export const PlatformUiSettingsContext = createContext<PlatformUiSettingsContextValue | null>(null)

export function normalizePlatformUiSettings(value: Partial<PlatformUiSettings> | null | undefined): PlatformUiSettings {
  return { mouseFollowerEnabled: value?.mouseFollowerEnabled !== false }
}

export function usePlatformUiSettings() {
  const context = useContext(PlatformUiSettingsContext)
  if (!context) throw new Error('usePlatformUiSettings must be used within PlatformUiSettingsProvider')
  return context
}
