import { Activity, ArrowRight, Clock3, FileCode2, Link2, RefreshCw, RotateCcw, Server, ShieldCheck, Timer, Workflow } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { ProviderTestStatus, type ProviderTestResult } from '@/components/provider-test-status'
import { Button } from '@/components/ui/button'
import { buttonClassName } from '@/components/ui/button-styles'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import type { AdminAiOperationsOverview, AdminAiOperationsProvider, AdminAiOperationsTask } from '@/lib/admin'

function dateText(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

function numberText(value?: number | null) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

function statusLabel(value: string) {
  return value === 'SUCCESS' ? '成功' : value === 'FAILED' ? '失败' : value === 'RUNNING' ? '运行中' : value === 'PENDING' ? '排队中' : value
}

function statusTone(value: string): 'success' | 'danger' | 'warning' | 'info' | 'default' {
  return value === 'SUCCESS' ? 'success' : value === 'FAILED' ? 'danger' : value === 'RUNNING' ? 'info' : value === 'PENDING' ? 'warning' : 'default'
}

function persistedTestResult(item: AdminAiOperationsProvider): ProviderTestResult | undefined {
  if (!item.lastTestState || !item.lastTestMessage || item.lastTestLatencyMs == null) return undefined
  return {
    success: item.lastTestState === 'SUCCESS',
    state: item.lastTestState,
    statusCode: item.lastTestStatusCode ?? null,
    latencyMs: item.lastTestLatencyMs,
    message: item.lastTestMessage,
    testedAt: item.lastTestedAt,
  }
}

function ProviderCard({ item, busy, result, onTest }: { item: AdminAiOperationsProvider; busy: boolean; result?: ProviderTestResult; onTest: () => void }) {
  const displayResult = result ?? persistedTestResult(item)
  return <Card className="min-w-0 p-5">
    <div className="flex items-start justify-between gap-3">
      <div className="flex min-w-0 items-center gap-3">
        <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><Server className="h-5 w-5" /></span>
        <div className="min-w-0"><h3 className="break-words font-bold">{item.name}</h3><p className="mt-1 break-all font-mono text-xs text-muted-foreground">{item.code}</p></div>
      </div>
      <Badge tone={item.state === 'UP' ? 'success' : item.state === 'CONFIGURED' || item.state === 'ATTENTION' ? 'warning' : item.state === 'DISABLED' ? 'default' : 'danger'}>{item.stateLabel}</Badge>
    </div>
    <dl className="mt-5 grid gap-2 text-sm"><div className="flex justify-between gap-4"><dt className="text-muted-foreground">类型</dt><dd>{item.kind}</dd></div><div className="flex justify-between gap-4"><dt className="text-muted-foreground">模型 / 能力</dt><dd className="max-w-[60%] break-words text-right">{item.model || '—'}</dd></div></dl>
    <div className="mt-5 flex flex-wrap gap-2"><Badge tone={item.enabled ? 'success' : 'default'}>{item.enabled ? '已启用' : '已停用'}</Badge>{item.textDefault && <Badge tone="info">文字默认</Badge>}{item.voiceDefault && <Badge tone="warning">语音默认</Badge>}</div>
    <div className="mt-5 flex flex-wrap gap-2"><Button type="button" variant="secondary" size="compact" onClick={onTest} disabled={busy || !item.enabled}>{busy ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Activity className="h-4 w-4" />}{busy ? '测试中…' : '测试 Provider'}</Button><Link to="/admin/settings" className={buttonClassName({ variant: 'ghost', size: 'compact', className: 'text-muted-foreground hover:text-foreground' })}>配置详情<ArrowRight className="h-4 w-4" /></Link></div>
    <ProviderTestStatus result={displayResult} />
  </Card>
}

function TaskRow({ item, onRetry, retrying }: { item: AdminAiOperationsTask; onRetry: () => void; retrying: boolean }) {
  return <article className="flex min-w-0 flex-col gap-3 rounded-2xl border border-border/80 bg-background/50 p-4 sm:flex-row sm:items-start sm:justify-between"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><strong className="break-all">任务 #{item.id}</strong><Badge tone={statusTone(item.status)}>{statusLabel(item.status)}</Badge><span className="text-xs text-muted-foreground">{item.taskType}</span></div><p className="mt-2 text-sm text-muted-foreground">{item.business?.label ?? '尚未关联可展示的业务记录'} · 尝试 {item.attempts ?? 0}/{item.maxAttempts ?? 0}</p><p className="mt-1 text-xs text-muted-foreground">更新时间：{dateText(item.finishedAt || item.startedAt || item.scheduledAt)}</p></div>{item.retryable && <Button type="button" variant="secondary" className="h-10 shrink-0" onClick={onRetry} disabled={retrying}>{retrying ? <RefreshCw className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />}受控重试</Button>}</article>
}

export function AdminAiOperations() {
  const [data, setData] = useState<AdminAiOperationsOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [testingProvider, setTestingProvider] = useState('')
  const [providerTestResults, setProviderTestResults] = useState<Record<string, ProviderTestResult>>({})
  const [retryingTask, setRetryingTask] = useState('')

  const load = useCallback(async () => {
    setLoading(true); setError('')
    try { setData(await request<AdminAiOperationsOverview>('/v1/admin/ai-operations/overview')) } catch (reason) { setError(reason instanceof Error ? reason.message : 'AI 运行概览加载失败，请稍后重试。') } finally { setLoading(false) }
  }, [])

  useEffect(() => { void load() }, [load])

  async function testProvider(item: AdminAiOperationsProvider) {
    if (testingProvider) return
    setTestingProvider(item.id); setMessage('')
    setProviderTestResults(previous => ({
      ...previous,
      [item.id]: { success: false, state: 'TESTING', statusCode: null, latencyMs: 0, message: '正在连接服务并检查健康状态，请稍候…' },
    }))
    try {
      const result = await request<ProviderTestResult>(`/v1/admin/ai-providers/${item.id}/test`, { method: 'POST' })
      setProviderTestResults(previous => ({ ...previous, [item.id]: result }))
      await load()
    } catch (reason) {
      setProviderTestResults(previous => ({
        ...previous,
        [item.id]: { success: false, state: 'FAILED', statusCode: null, latencyMs: 0, message: reason instanceof Error ? reason.message : 'Provider 测试失败，请稍后重试。' },
      }))
    } finally { setTestingProvider('') }
  }

  async function retryTask(item: AdminAiOperationsTask) {
    if (!window.confirm(`确定对任务 #${item.id} 执行受控重试吗？系统会复用原任务和去重键。`)) return
    setRetryingTask(item.id); setMessage('')
    try { await request(`/v1/admin/ai-operations/tasks/${item.id}/retry`, { method: 'POST', body: JSON.stringify({ confirm: true }) }); setMessage(`任务 #${item.id} 已提交受控重试。`); await load() } catch (reason) { setMessage(reason instanceof Error ? reason.message : '任务重试失败，请稍后重试。') } finally { setRetryingTask('') }
  }

  return <div className="space-y-6">
    <header className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between"><div><p className="text-sm font-semibold text-[var(--accent)]">AI 运行链路</p><h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">AI 中心</h1><p className="mt-3 max-w-3xl text-muted-foreground">把业务记录、异步任务、生成请求、Provider、Prompt 版本和业务结果放在同一条可追踪链路里。</p></div><Button type="button" variant="secondary" className="h-10" onClick={() => void load()} disabled={loading}><RefreshCw className={loading ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} />刷新 AI 状态</Button></header>
    {message && <div className="rounded-2xl border border-[var(--accent)]/25 bg-[var(--accent-soft)] px-4 py-3 text-sm text-[var(--accent)]">{message}</div>}
    {error && <Card className="border-[var(--danger)]/30 bg-[var(--danger)]/5 text-sm text-[var(--danger)]">{error}</Card>}
    <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5"><Card><p className="text-sm text-muted-foreground">24 小时调用</p><strong className="mt-3 block text-3xl">{loading ? '…' : numberText(data?.ai.total)}</strong><p className="mt-2 text-xs text-muted-foreground">{data?.ai.windowLabel ?? '服务端聚合窗口'}</p></Card><Card><p className="text-sm text-muted-foreground">成功率</p><strong className="mt-3 block text-3xl">{loading ? '…' : `${data?.ai.total ? Math.round(data.ai.success * 1000 / data.ai.total) / 10 : 0}%`}</strong><p className="mt-2 text-xs text-muted-foreground">仅统计生成请求状态</p></Card><Card><p className="text-sm text-muted-foreground">AI 任务积压</p><strong className="mt-3 block text-3xl">{loading ? '…' : numberText(data?.tasks.backlog)}</strong><p className="mt-2 text-xs text-muted-foreground">排队与执行中</p></Card><Card><p className="text-sm text-muted-foreground">报告积压</p><strong className="mt-3 block text-3xl">{loading ? '…' : numberText(data?.tasks.reportBacklog)}</strong><p className="mt-2 text-xs text-muted-foreground">自动评估任务</p></Card><Card><p className="text-sm text-muted-foreground">平均耗时</p><strong className="mt-3 block text-3xl">{loading ? '…' : `${numberText(data?.ai.averageLatencyMs)} ms`}</strong><p className="mt-2 text-xs text-muted-foreground">不含业务结果正文</p></Card></section>

    <Card><div className="flex items-start justify-between gap-4"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">Trace map</p><h2 className="mt-2 text-xl font-bold">一条 AI 请求如何回到业务</h2></div><Workflow className="h-5 w-5 text-[var(--accent)]" /></div><div className="mt-6 grid gap-2 md:grid-cols-6">{['业务记录', 'AI task', 'generation request', 'Provider / model', 'Prompt version', '业务结果'].map((item, index) => <div key={item} className="flex items-center gap-2"><div className="min-w-0 flex-1 rounded-2xl border border-border bg-background/60 px-3 py-3 text-center text-sm font-semibold">{item}</div>{index < 5 && <ArrowRight className="hidden h-4 w-4 shrink-0 text-muted-foreground md:block" />}</div>)}</div><p className="mt-4 text-xs leading-5 text-muted-foreground">关联页只展示 ID、状态、模型、Prompt 代码与版本，不展示完整简历、回答、输入输出正文、密钥或 Provider 原始响应。</p></Card>

    <section><div className="mb-4 flex flex-wrap items-end justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">Provider / model</p><h2 className="mt-2 text-xl font-bold">服务配置与探测</h2></div><Link to="/admin/settings" className="inline-flex items-center gap-2 text-sm font-semibold text-[var(--accent)]">管理配置<ArrowRight className="h-4 w-4" /></Link></div><div className="grid gap-4 xl:grid-cols-3">{data?.providers.map(item => <ProviderCard key={item.id} item={item} busy={testingProvider === item.id} result={providerTestResults[item.id]} onTest={() => void testProvider(item)} />)}</div>{!loading && !data?.providers.length && <Card className="text-sm text-muted-foreground">暂无 Provider 配置。</Card>}</section>

    <section className="grid gap-5 xl:grid-cols-[.9fr_1.1fr]"><Card><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">Prompt version</p><h2 className="mt-2 text-xl font-bold">当前 Prompt 版本</h2></div><FileCode2 className="h-5 w-5 text-[var(--accent)]" /></div><div className="mt-5 divide-y divide-border">{data?.prompts.map(item => <Link key={item.code} to={`/admin/prompt-templates/${encodeURIComponent(item.code)}`} className="flex items-center justify-between gap-3 py-3 transition hover:text-[var(--accent)]"><span className="min-w-0"><strong className="block break-words">{item.name}</strong><span className="mt-1 block break-all font-mono text-xs text-muted-foreground">{item.code} · v{item.version}</span></span><Badge tone={item.active ? 'success' : 'default'}>{item.active ? '启用' : '未启用'}</Badge></Link>)}</div>{!loading && !data?.prompts.length && <p className="mt-5 text-sm text-muted-foreground">暂无 Prompt 版本。</p>}</Card><Card><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">Async tasks</p><h2 className="mt-2 text-xl font-bold">异步任务状态</h2></div><Timer className="h-5 w-5 text-[var(--accent)]" /></div><div className="mt-5 space-y-3">{data?.recentTasks.map(item => <TaskRow key={item.id} item={item} retrying={retryingTask === item.id} onRetry={() => void retryTask(item)} />)}</div>{!loading && !data?.recentTasks.length && <p className="mt-5 text-sm text-muted-foreground">暂无异步任务。</p>}</Card></section>

    <Card><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">Generation request</p><h2 className="mt-2 text-xl font-bold">最近 AI 调用</h2></div><Link2 className="h-5 w-5 text-[var(--accent)]" /></div><div className="mt-5 overflow-x-auto"><table className="mobile-card-table text-left text-sm"><thead className="bg-muted/50 text-xs text-muted-foreground"><tr><th className="px-4 py-3">状态 / 类型</th><th className="px-4 py-3">Provider / Prompt</th><th className="px-4 py-3">关联</th><th className="px-4 py-3">性能</th><th className="px-4 py-3">追踪</th></tr></thead><tbody className="divide-y divide-border">{data?.recentCalls.map(item => <tr key={item.id} className="align-top"><td data-label="状态 / 类型" className="px-4 py-3"><Badge tone={statusTone(item.status)}>{statusLabel(item.status)}</Badge><p className="mt-2 font-semibold">{item.generationType}</p></td><td data-label="Provider / Prompt" className="px-4 py-3"><p>{item.provider} / {item.model}</p><p className="mt-1 break-all text-xs text-muted-foreground">{item.promptCode ?? '未绑定'}{item.promptVersion ? ` · v${item.promptVersion}` : ''}</p></td><td data-label="关联" className="px-4 py-3 text-xs text-muted-foreground"><p>任务：{item.taskId ?? '—'}</p><p>面试：{item.interviewId ?? '—'}</p></td><td data-label="性能" className="px-4 py-3"><p className="inline-flex items-center gap-1.5"><Clock3 className="h-4 w-4" />{item.latencyMs == null ? '—' : `${item.latencyMs} ms`}</p><p className="mt-1 text-xs text-muted-foreground">Token {numberText(item.totalTokens)}</p></td><td data-label="追踪" className="px-4 py-3"><Link to={`/admin/ai-operations/traces/generations/${item.id}`} className="inline-flex items-center gap-1.5 font-semibold text-[var(--accent)]">查看链路<ArrowRight className="h-4 w-4" /></Link></td></tr>)}</tbody></table></div>{!loading && !data?.recentCalls.length && <p className="mt-5 text-sm text-muted-foreground">暂无生成请求。</p>}</Card>
    <p className="flex items-center gap-2 text-xs leading-5 text-muted-foreground"><ShieldCheck className="h-4 w-4 shrink-0" />最近更新时间：{dateText(data?.generatedAt)}。AI 失败任务不会自动改变招聘申请决定。</p>
  </div>
}
