import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { request } from '@/lib/api'
import { defaultPlatformUiSettings, normalizePlatformUiSettings, PlatformUiSettingsContext, type PlatformUiSettings } from '@/lib/platform-ui-settings'

export function PlatformUiSettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<PlatformUiSettings>(defaultPlatformUiSettings)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const loadPromiseRef = useRef<Promise<PlatformUiSettings> | null>(null)
  const savingRef = useRef(false)

  useEffect(() => {
    let active = true
    loadPromiseRef.current ??= request<PlatformUiSettings>('/v1/platform/ui-settings')
    void loadPromiseRef.current
      .then(next => {
        if (active) setSettings(normalizePlatformUiSettings(next))
      })
      .catch(() => {
        // The default keeps the existing interaction available when the setting service is unavailable.
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  const updateMouseFollowerEnabled = useCallback(async (enabled: boolean) => {
    if (savingRef.current) return settings.mouseFollowerEnabled
    savingRef.current = true
    setSaving(true)
    try {
      const next = await request<PlatformUiSettings>('/v1/admin/platform/ui-settings', {
        method: 'PUT',
        body: JSON.stringify({ mouseFollowerEnabled: enabled }),
      })
      const normalized = normalizePlatformUiSettings(next)
      setSettings(normalized)
      return normalized.mouseFollowerEnabled
    } finally {
      savingRef.current = false
      setSaving(false)
    }
  }, [settings.mouseFollowerEnabled])

  const value = useMemo(() => ({ settings, loading, saving, updateMouseFollowerEnabled }), [loading, saving, settings, updateMouseFollowerEnabled])
  return <PlatformUiSettingsContext.Provider value={value}>{children}</PlatformUiSettingsContext.Provider>
}
