import { MonitorDot, MousePointer2, Palette, ShieldCheck, UsersRound } from 'lucide-react'
import { useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { usePlatformUiSettings } from '@/lib/platform-ui-settings'

type Feedback = 'idle' | 'saving' | 'saved' | 'error'

export function AdminThemeSettings() {
  const { settings, loading, saving, updateMouseFollowerEnabled } = usePlatformUiSettings()
  const [feedback, setFeedback] = useState<Feedback>('idle')

  async function toggleMouseFollower() {
    if (loading || saving) return
    setFeedback('saving')
    try {
      await updateMouseFollowerEnabled(!settings.mouseFollowerEnabled)
      setFeedback('saved')
    } catch {
      setFeedback('error')
    }
  }

  const currentLabel = loading ? '正在读取' : settings.mouseFollowerEnabled ? '已开启' : '已关闭'
  const statusLabel = feedback === 'saving' || saving ? '保存中…' : feedback === 'saved' ? '已保存' : feedback === 'error' ? '保存失败' : currentLabel

  return <div className="space-y-6">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p className="flex items-center gap-2 text-sm font-semibold text-[var(--accent)]"><Palette className="h-4 w-4" aria-hidden="true" />外观与交互</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">主题设置</h1>
        <p className="mt-3 max-w-2xl text-muted-foreground">统一管理工作区中的轻量交互动效，让面试信息传递保持清晰、安静且可控。</p>
      </div>
      <Badge tone={settings.mouseFollowerEnabled ? 'success' : 'default'}>{currentLabel}</Badge>
    </header>

    <Card className="max-w-3xl">
      <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 items-start gap-4">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><MousePointer2 className="h-5 w-5" aria-hidden="true" /></span>
          <div className="min-w-0">
            <h2 className="text-xl font-bold">鼠标跟随动画</h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">在桌面端显示克制的鼠标位置反馈，帮助确认当前交互焦点。关闭后立即停止跟随效果，不影响页面操作。</p>
          </div>
        </div>
        <button type="button" role="switch" aria-checked={settings.mouseFollowerEnabled} aria-label={`鼠标跟随动画${settings.mouseFollowerEnabled ? '已开启' : '已关闭'}`} disabled={loading || saving} onClick={() => void toggleMouseFollower()} className={`relative h-7 w-12 shrink-0 rounded-full border transition-colors duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] focus-visible:ring-offset-2 focus-visible:ring-offset-surface disabled:cursor-not-allowed disabled:opacity-65 ${settings.mouseFollowerEnabled ? 'border-[var(--accent)] bg-[var(--accent)]' : 'border-border bg-muted'}`}>
          <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white shadow-sm transition-transform duration-200 ${settings.mouseFollowerEnabled ? 'translate-x-5' : 'translate-x-0.5'}`} />
        </button>
      </div>

      <div className="mt-6 grid gap-3 border-t border-border pt-5 sm:grid-cols-[1fr_auto] sm:items-center">
        <div>
          <p className="text-xs font-bold uppercase tracking-[.14em] text-muted-foreground">当前状态</p>
          <p className="mt-2 flex items-center gap-2 text-sm font-semibold" aria-live="polite"><MonitorDot className="h-4 w-4 text-[var(--accent)]" aria-hidden="true" />{statusLabel}</p>
        </div>
        <div className="text-left sm:text-right">
          {feedback === 'error' ? <p role="alert" className="text-sm text-[var(--danger)]">保存失败，请检查网络后重试。</p> : <p aria-live="polite" className="text-sm text-muted-foreground">{feedback === 'saved' ? '设置已同步到平台。' : feedback === 'saving' || saving ? '正在保存设置…' : '切换后立即生效。'}</p>}
        </div>
      </div>

      <div className="mt-5 rounded-2xl border border-border bg-muted/45 p-4">
        <p className="flex items-center gap-2 text-xs font-bold uppercase tracking-[.14em] text-muted-foreground"><ShieldCheck className="h-4 w-4" aria-hidden="true" />生效范围</p>
        <div className="mt-3 flex flex-wrap gap-2"><Badge><UsersRound className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />候选人工作区</Badge><Badge><UsersRound className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />企业工作区</Badge><Badge><ShieldCheck className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />管理员工作区</Badge></div>
        <p className="mt-3 text-xs leading-5 text-muted-foreground">认证页和公开首页保持简洁，不显示鼠标跟随效果；减少动态效果设置仍会优先停用动画。</p>
      </div>
    </Card>
  </div>
}
