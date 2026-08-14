import { AlertTriangle, CheckCircle2, Loader2 } from 'lucide-react'

export type ProviderTestState = 'TESTING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT'

export type ProviderTestResult = {
  success: boolean
  state: ProviderTestState
  statusCode: number | null
  latencyMs: number
  message: string
  testedAt?: string | null
}

export function ProviderTestStatus({ result }: { result?: ProviderTestResult }) {
  if (!result) return null

  const testing = result.state === 'TESTING'
  const success = result.state === 'SUCCESS' || result.success
  const title = testing ? '正在测试 Provider' : result.state === 'TIMEOUT' ? 'Provider 测试超时' : success ? 'Provider 测试成功' : 'Provider 测试失败'
  const details = testing
    ? result.message
    : [result.statusCode ? `HTTP ${result.statusCode}` : '', `${result.latencyMs} ms`, result.message,
        result.testedAt ? `测试于 ${new Date(result.testedAt).toLocaleString('zh-CN', { hour12: false })}` : ''].filter(Boolean).join(' · ')
  const tone = testing
    ? 'border-border bg-muted/45 text-foreground'
    : success
      ? 'border-[var(--success-foreground)]/25 bg-[var(--success)] text-[var(--success-foreground)]'
      : 'border-[var(--danger-foreground)]/25 bg-[var(--danger)] text-[var(--danger-foreground)]'

  return <div className={`mt-4 flex min-w-0 items-start gap-3 rounded-2xl border px-4 py-3 text-sm ${tone}`} role="status" aria-live="polite" data-testid="provider-test-status">
    {testing
      ? <Loader2 className="mt-0.5 h-4 w-4 shrink-0 animate-spin" />
      : success
        ? <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
        : <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />}
    <span className="min-w-0"><strong className="block font-semibold">{title}</strong><span className="mt-1 block break-words leading-5 opacity-85">{details}</span></span>
  </div>
}
