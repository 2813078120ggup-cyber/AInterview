import { Activity, AlertTriangle, BadgeDollarSign, Ban, CheckCircle2, FlaskConical, Gauge, Loader2, RefreshCw, ShieldCheck, UserCheck } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { AdminConfirmDialog } from '@/components/admin-confirm-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import type { AiGovernanceOverview, AiGovernancePolicy, AiGovernanceSuite } from '@/lib/ai-governance'
import { policyPayload } from '@/lib/ai-governance'
import { request } from '@/lib/api'

const inputClass = 'mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 text-sm tabular-nums outline-none focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--brand)]/20'

function dateText(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
}

function money(value?: number | null) {
  return `$${Number(value ?? 0).toFixed(4)}`
}

function statusTone(status?: string): 'success' | 'danger' | 'warning' | 'info' | 'default' {
  return status === 'PASSED' || status === 'ALLOWED' || status === 'READY' ? 'success'
    : status === 'FAILED' || status === 'BLOCKED' || status === 'STOPPED' ? 'danger'
      : status === 'RUNNING' ? 'info' : 'warning'
}

function readinessText(status: AiGovernanceOverview['readiness']) {
  return status === 'READY' ? '允许上线' : status === 'STOPPED' ? '已紧急停用' : '评测门禁阻断中'
}

function PercentBar({ value, limit }: { value: number; limit: number }) {
  const ratio = limit <= 0 ? 100 : Math.min(100, Math.round(value * 100 / limit))
  return <div className="mt-3"><div className="h-2 overflow-hidden rounded-full bg-muted"><div className={'h-full rounded-full ' + (ratio >= 90 ? 'bg-[var(--danger-foreground)]' : ratio >= 70 ? 'bg-[var(--warning-foreground)]' : 'bg-[var(--success-foreground)]')} style={{ width: `${ratio}%` }} /></div><p className="mt-2 text-xs tabular-nums text-muted-foreground">已使用 {ratio}%</p></div>
}

function SuiteCard({ suite, busy, onRun }: { suite: AiGovernanceSuite; busy: boolean; onRun: () => void }) {
  const run = suite.latestRun
  const running = run?.status === 'RUNNING'
  return <article className="rounded-2xl border border-border bg-background/55 p-4">
    <div className="flex items-start justify-between gap-3"><div className="min-w-0"><p className="break-all font-mono text-[11px] text-muted-foreground">{suite.promptCode}</p><h3 className="mt-1 font-bold">{suite.name}</h3></div><Badge tone={statusTone(running ? 'RUNNING' : suite.gateReady ? 'PASSED' : 'FAILED')}>{running ? '运行中' : suite.gateReady ? '门禁有效' : run?.status === 'PASSED' ? '证据已失效' : run?.status === 'FAILED' ? '未通过' : '未执行'}</Badge></div>
    <p className="mt-3 text-sm leading-6 text-muted-foreground">{suite.description}</p>
    <dl className="mt-4 grid grid-cols-2 gap-3 text-sm"><div><dt className="text-xs text-muted-foreground">启用用例</dt><dd className="mt-1 font-semibold tabular-nums">{suite.caseCount}</dd></div><div><dt className="text-xs text-muted-foreground">通过率</dt><dd className="mt-1 font-semibold tabular-nums">{run?.passRate == null ? '—' : `${run.passRate}%`}</dd></div><div><dt className="text-xs text-muted-foreground">最大漂移</dt><dd className="mt-1 font-semibold tabular-nums">{run?.maximumScoreDrift ?? '—'}</dd></div><div><dt className="text-xs text-muted-foreground">公平性差值</dt><dd className="mt-1 font-semibold tabular-nums">{run?.maximumFairnessGap ?? '—'}</dd></div></dl>
    <p className="mt-3 break-all text-xs text-muted-foreground">当前目标：{suite.targetProvider || '未配置'} / {suite.targetModel || '未配置'} · Prompt v{suite.targetPromptVersion ?? '—'}</p>
    {run?.model && <p className="mt-1 break-all text-xs text-muted-foreground">最近证据：{run.provider} / {run.model} · Prompt v{run.promptVersion}</p>}
    {run?.failureSummary && <p className="mt-3 rounded-xl bg-[var(--danger)] px-3 py-2 text-xs leading-5 text-[var(--danger-foreground)]">{run.failureSummary}</p>}
    <Button type="button" variant="secondary" className="mt-4 w-full" disabled={busy || run?.status === 'RUNNING'} onClick={onRun}>{busy || run?.status === 'RUNNING' ? <Loader2 className="h-4 w-4 animate-spin" /> : <FlaskConical className="h-4 w-4" />}{run?.status === 'RUNNING' ? '评测执行中' : '运行回归评测'}</Button>
  </article>
}

export function AdminAiGovernance() {
  const [data, setData] = useState<AiGovernanceOverview | null>(null)
  const [form, setForm] = useState<AiGovernancePolicy | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [emergencyDialog, setEmergencyDialog] = useState(false)
  const [emergencyReason, setEmergencyReason] = useState('')
  const [tenantCompanyId, setTenantCompanyId] = useState('')
  const [tenantEnabled, setTenantEnabled] = useState(false)

  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true)
    setError('')
    try {
      const result = await request<AiGovernanceOverview>('/v1/admin/ai-recruitment-governance/overview')
      setData(result)
      setForm(current => current && current.version === result.globalPolicy.version ? current : result.globalPolicy)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '招聘 AI 治理状态加载失败。') }
    finally { if (!quiet) setLoading(false) }
  }, [])

  useEffect(() => { void load() }, [load])
  useEffect(() => {
    if (!data?.suites.some(item => item.latestRun?.status === 'RUNNING')) return
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') void load(true) }, 4000)
    return () => window.clearInterval(timer)
  }, [data?.suites, load])

  const selectedTenant = useMemo(() => data?.tenantPolicies.find(item => String(item.companyId) === tenantCompanyId), [data?.tenantPolicies, tenantCompanyId])
  useEffect(() => { if (selectedTenant) setTenantEnabled(selectedTenant.aiEnabled) }, [selectedTenant])

  async function savePolicy() {
    if (!form) return
    setBusy('policy'); setMessage(''); setError('')
    try {
      await request('/v1/admin/ai-recruitment-governance/policy/global', { method: 'PUT', body: JSON.stringify(policyPayload(form)) })
      setMessage('全局治理策略已保存；新调用会立即使用更新后的门禁。')
      await load(true)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '全局治理策略保存失败。') }
    finally { setBusy('') }
  }

  async function setEmergency() {
    if (!data) return
    const enabled = !data.globalPolicy.emergencyStop
    setBusy('emergency'); setError('')
    try {
      await request('/v1/admin/ai-recruitment-governance/emergency-stop', { method: 'POST', body: JSON.stringify({ enabled, reason: enabled ? emergencyReason : '解除紧急停用', confirm: true, version: data.globalPolicy.version }) })
      setMessage(enabled ? '招聘 AI 已紧急停用，新的招聘模型调用将被阻断。' : '紧急停用已解除，调用仍需通过评测与预算门禁。')
      setEmergencyDialog(false); setEmergencyReason('')
      await load(true)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '紧急停用操作失败。') }
    finally { setBusy('') }
  }

  async function runSuite(suite: AiGovernanceSuite) {
    setBusy(`suite-${suite.id}`); setError(''); setMessage('')
    try {
      await request(`/v1/admin/ai-recruitment-governance/evaluation-suites/${suite.id}/runs`, { method: 'POST' })
      setMessage(`${suite.name}已进入评测队列。评测只保存断言、分数和响应摘要，不保存模型原文。`)
      await load(true)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '评测启动失败。') }
    finally { setBusy('') }
  }

  async function saveTenant() {
    if (!data || !/^\d+$/.test(tenantCompanyId)) { setError('请输入有效的企业 ID。'); return }
    const base = selectedTenant ?? data.globalPolicy
    setBusy('tenant'); setError('')
    try {
      await request(`/v1/admin/ai-recruitment-governance/policy/companies/${tenantCompanyId}`, { method: 'PUT', body: JSON.stringify({ ...policyPayload(base), aiEnabled: tenantEnabled, version: selectedTenant?.version ?? 0 }) })
      setMessage(`企业 #${tenantCompanyId} 的招聘 AI 策略已${tenantEnabled ? '启用' : '停用'}。`)
      await load(true)
    } catch (reason) { setError(reason instanceof Error ? reason.message : '企业治理策略保存失败。') }
    finally { setBusy('') }
  }

  const policy = form
  const readiness = data?.readiness ?? 'BLOCKED'
  return <div className="space-y-7">
    <header className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between"><div><p className="text-sm font-semibold text-[var(--accent)]">招聘 AI 上线门禁</p><h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">AI 招聘治理</h1><p className="mt-3 max-w-3xl text-sm leading-6 text-muted-foreground">统一控制简历分析、岗位匹配和招聘面试评分。任何模型结果都不能绕过评测、预算、脱敏与人工复核。</p></div><div className="flex flex-wrap gap-2"><Button type="button" variant="secondary" onClick={() => void load()} disabled={loading}><RefreshCw className={loading ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} />刷新证据</Button><Button type="button" variant={data?.globalPolicy.emergencyStop ? 'secondary' : 'danger'} onClick={() => setEmergencyDialog(true)} disabled={!data}>{data?.globalPolicy.emergencyStop ? <ShieldCheck className="h-4 w-4" /> : <Ban className="h-4 w-4" />}{data?.globalPolicy.emergencyStop ? '解除紧急停用' : '紧急停用'}</Button></div></header>

    {message && <div role="status" className="rounded-2xl bg-[var(--success)] px-4 py-3 text-sm text-[var(--success-foreground)]"><CheckCircle2 className="mr-2 inline h-4 w-4" />{message}</div>}
    {error && <div role="alert" className="rounded-2xl bg-[var(--danger)] px-4 py-3 text-sm text-[var(--danger-foreground)]"><AlertTriangle className="mr-2 inline h-4 w-4" />{error}</div>}

    <Card className={'overflow-hidden p-0 ' + (readiness === 'READY' ? 'border-[var(--success-foreground)]/25' : 'border-[var(--danger-foreground)]/25')}>
      <div className="grid lg:grid-cols-[minmax(0,1.35fr)_minmax(320px,.65fr)]"><div className="p-6 sm:p-8"><div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-semibold uppercase tracking-[.16em] text-muted-foreground">Go / no-go</p><h2 className="mt-3 text-3xl font-bold">{readinessText(readiness)}</h2><p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">{readiness === 'READY' ? '三类合成评测对当前 Provider、模型和 Prompt 版本均在有效期内通过。' : readiness === 'STOPPED' ? data?.globalPolicy.emergencyReason || '紧急停用开关正在阻断全部招聘 AI 调用。' : '至少一个当前模型门禁缺少有效证据；生产招聘调用将保持阻断。'}</p></div><Badge tone={statusTone(readiness)}>{readiness}</Badge></div><div className="mt-7 grid gap-2 sm:grid-cols-4">{[
        { icon: ShieldCheck, label: '敏感字段', value: policy?.sensitiveDataMode === 'BLOCK_ON_DETECTION' ? '检测即阻断' : '发送前脱敏' },
        { icon: FlaskConical, label: '评测门禁', value: `${data?.suites.filter(item => item.gateReady).length ?? 0}/${data?.suites.length ?? 0} 有效` },
        { icon: BadgeDollarSign, label: '成本预算', value: `${money(data?.globalCost.todayUsd)} / 日` },
        { icon: UserCheck, label: '人工复核', value: `${(data?.pendingMatchReviews ?? 0) + (data?.pendingReportReviews ?? 0)} 待处理` },
      ].map(item => <div key={item.label} className="rounded-2xl bg-muted/60 p-3"><item.icon className="h-4 w-4 text-[var(--accent)]" /><p className="mt-3 text-xs text-muted-foreground">{item.label}</p><p className="mt-1 text-sm font-semibold">{item.value}</p></div>)}</div></div><div className="border-t border-border bg-muted/35 p-6 lg:border-l lg:border-t-0"><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">Review queue</p><div className="mt-5 space-y-4"><div><p className="text-sm text-muted-foreground">匹配结果待复核</p><strong className="mt-1 block text-3xl tabular-nums">{data?.pendingMatchReviews ?? '—'}</strong></div><div><p className="text-sm text-muted-foreground">面试报告待复核</p><strong className="mt-1 block text-3xl tabular-nums">{data?.pendingReportReviews ?? '—'}</strong></div></div><p className="mt-6 text-xs leading-5 text-muted-foreground">AI 不会自动改变申请状态；待复核结果会阻止安排面试或形成最终结论。</p></div></div>
    </Card>

    <section><div className="mb-4"><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">Evaluation evidence</p><h2 className="mt-2 text-2xl font-bold">评测、回归与公平性门禁</h2></div><div className="grid gap-4 xl:grid-cols-3">{data?.suites.map(suite => <SuiteCard key={suite.id} suite={suite} busy={busy === `suite-${suite.id}`} onRun={() => void runSuite(suite)} />)}</div>{!loading && !data?.suites.length && <Card className="text-sm text-muted-foreground">尚未配置招聘 AI 评测集。</Card>}</section>

    <section className="grid gap-5 xl:grid-cols-[1.25fr_.75fr]">
      <Card className="p-5 sm:p-6"><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">Global policy</p><h2 className="mt-2 text-2xl font-bold">不可绕过的全局底线</h2></div><Gauge className="h-5 w-5 text-[var(--accent)]" /></div>{policy && <div className="mt-6 space-y-6"><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3"><label className="text-sm font-semibold">模型调用<ResponsiveSelect ariaLabel="全局模型调用状态" value={policy.aiEnabled ? 'enabled' : 'disabled'} onValueChange={value => setForm({ ...policy, aiEnabled: value === 'enabled' })} options={[{ value: 'enabled', label: '启用（仍受门禁控制）' }, { value: 'disabled', label: '全局停用' }]} className="mt-2 w-full" /></label><label className="text-sm font-semibold">敏感字段策略<ResponsiveSelect ariaLabel="敏感字段策略" value={policy.sensitiveDataMode} onValueChange={value => setForm({ ...policy, sensitiveDataMode: value as AiGovernancePolicy['sensitiveDataMode'] })} options={[{ value: 'REDACT', label: '发送前脱敏' }, { value: 'BLOCK_ON_DETECTION', label: '检测到即阻断' }]} className="mt-2 w-full" /></label><label className="text-sm font-semibold">人工复核策略<ResponsiveSelect ariaLabel="人工复核策略" value={policy.humanReviewMode} onValueChange={value => setForm({ ...policy, humanReviewMode: value as AiGovernancePolicy['humanReviewMode'] })} options={[{ value: 'ALL', label: '全部结果必须复核' }, { value: 'LOW_CONFIDENCE', label: '中低置信度复核' }, { value: 'ADVERSE_ONLY', label: '低分结果复核' }]} className="mt-2 w-full" /></label></div><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><PolicyNumber label="评测有效期（天）" value={policy.evaluationValidDays} min={1} max={365} onChange={value => setForm({ ...policy, evaluationValidDays: value })} /><PolicyNumber label="最低通过率（%）" value={policy.minimumPassRate} min={0} max={100} onChange={value => setForm({ ...policy, minimumPassRate: value })} /><PolicyNumber label="最大分数漂移" value={policy.maximumScoreDrift} min={0} max={100} onChange={value => setForm({ ...policy, maximumScoreDrift: value })} /><PolicyNumber label="最大公平性差值" value={policy.maximumFairnessGap} min={0} max={100} onChange={value => setForm({ ...policy, maximumFairnessGap: value })} /><PolicyNumber label="低分阈值" value={policy.adverseScoreThreshold} min={0} max={100} onChange={value => setForm({ ...policy, adverseScoreThreshold: value })} /><PolicyNumber label="日成本上限（USD）" value={policy.dailyCostLimitUsd} min={0} step="0.01" onChange={value => setForm({ ...policy, dailyCostLimitUsd: value })} /><PolicyNumber label="月成本上限（USD）" value={policy.monthlyCostLimitUsd} min={0} step="0.01" onChange={value => setForm({ ...policy, monthlyCostLimitUsd: value })} /><PolicyNumber label="单次 Token 上限" value={policy.perRequestTokenLimit} min={256} max={1000000} onChange={value => setForm({ ...policy, perRequestTokenLimit: value })} /></div><div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-5"><p className="text-xs text-muted-foreground">策略版本 {policy.version} · 更新于 {dateText(policy.updatedAt)}</p><Button type="button" disabled={busy === 'policy'} onClick={() => void savePolicy()}>{busy === 'policy' ? <Loader2 className="h-4 w-4 animate-spin" /> : <ShieldCheck className="h-4 w-4" />}保存全局策略</Button></div></div>}</Card>
      <div className="space-y-5"><Card className="p-5 sm:p-6"><div className="flex items-start justify-between"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">Budget ledger</p><h2 className="mt-2 text-xl font-bold">成本上限</h2></div><BadgeDollarSign className="h-5 w-5 text-[var(--accent)]" /></div><div className="mt-5"><p className="text-sm text-muted-foreground">今日</p><p className="mt-1 text-xl font-bold tabular-nums">{money(data?.globalCost.todayUsd)} <span className="text-sm font-normal text-muted-foreground">/ {money(data?.globalCost.dailyLimitUsd)}</span></p><PercentBar value={data?.globalCost.todayUsd ?? 0} limit={data?.globalCost.dailyLimitUsd ?? 0} /></div><div className="mt-6"><p className="text-sm text-muted-foreground">本月</p><p className="mt-1 text-xl font-bold tabular-nums">{money(data?.globalCost.monthUsd)} <span className="text-sm font-normal text-muted-foreground">/ {money(data?.globalCost.monthlyLimitUsd)}</span></p><PercentBar value={data?.globalCost.monthUsd ?? 0} limit={data?.globalCost.monthlyLimitUsd ?? 0} /></div><p className="mt-5 text-xs leading-5 text-muted-foreground">调用前按输入长度与最大输出预留预算，完成后用 Provider Token 用量结算；失败请求释放预留。</p></Card><Card className="p-5 sm:p-6"><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">Tenant opt-out</p><h2 className="mt-2 text-xl font-bold">企业停用策略</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">企业策略只能收紧全局底线。输入企业 ID 后可立即停用该租户招聘 AI。</p><label className="mt-5 block text-sm font-semibold">企业 ID<input className={inputClass} inputMode="numeric" value={tenantCompanyId} onChange={event => setTenantCompanyId(event.target.value.replace(/\D/g, ''))} placeholder="例如 1001" /></label><label className="mt-4 block text-sm font-semibold">租户模型调用<ResponsiveSelect ariaLabel="企业模型调用状态" value={tenantEnabled ? 'enabled' : 'disabled'} onValueChange={value => setTenantEnabled(value === 'enabled')} options={[{ value: 'disabled', label: '停用该企业' }, { value: 'enabled', label: '启用（继承全局门禁）' }]} className="mt-2 w-full" /></label>{selectedTenant && <p className="mt-3 text-xs text-muted-foreground">已存在租户策略，当前版本 {selectedTenant.version}。</p>}<Button type="button" className="mt-5 w-full" variant={tenantEnabled ? 'secondary' : 'danger'} disabled={busy === 'tenant'} onClick={() => void saveTenant()}>{busy === 'tenant' ? <Loader2 className="h-4 w-4 animate-spin" /> : tenantEnabled ? <ShieldCheck className="h-4 w-4" /> : <Ban className="h-4 w-4" />}{tenantEnabled ? '保存租户策略' : '停用该企业招聘 AI'}</Button></Card></div>
    </section>

    <Card className="p-5 sm:p-6"><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">Decision log</p><h2 className="mt-2 text-2xl font-bold">最近治理决策</h2><p className="mt-2 text-sm text-muted-foreground">只记录范围、决策和原因，不保存简历、回答、Prompt 或模型原文。</p></div><Activity className="h-5 w-5 text-[var(--accent)]" /></div><div className="mt-5 divide-y divide-border">{data?.recentEvents.map(item => <article key={item.id} className="grid gap-2 py-4 sm:grid-cols-[120px_1fr_auto] sm:items-center"><div><Badge tone={statusTone(item.decision)}>{item.decision === 'ALLOWED' ? '允许' : item.decision === 'BLOCKED' ? '阻断' : '已变更'}</Badge></div><div className="min-w-0"><p className="font-semibold">{item.summary}</p><p className="mt-1 break-all text-xs text-muted-foreground">{item.reasonCode || item.eventType}{item.companyId ? ` · 企业 #${item.companyId}` : ' · 全局'}</p></div><time className="text-xs text-muted-foreground">{dateText(item.createdAt)}</time></article>)}</div>{!loading && !data?.recentEvents.length && <p className="mt-5 text-sm text-muted-foreground">暂无治理决策记录。</p>}</Card>

    {emergencyDialog && data && <AdminConfirmDialog title={data.globalPolicy.emergencyStop ? '解除招聘 AI 紧急停用？' : '立即停用全部招聘 AI？'} description={data.globalPolicy.emergencyStop ? '解除后仍不会绕过模型评测、预算、脱敏和人工复核门禁。' : '开启后，简历分析、岗位匹配和招聘面试评分的新模型调用会立即被阻断；已保存的业务数据不会删除。'} confirmLabel={data.globalPolicy.emergencyStop ? '解除停用' : '立即停用'} danger={!data.globalPolicy.emergencyStop} busy={busy === 'emergency'} onClose={() => setEmergencyDialog(false)} onConfirm={() => void setEmergency()}>{!data.globalPolicy.emergencyStop && <label className="mt-5 block text-sm font-semibold">停用原因<textarea autoFocus required maxLength={500} value={emergencyReason} onChange={event => setEmergencyReason(event.target.value)} className="mt-2 min-h-24 w-full rounded-2xl border border-border bg-background p-3 text-sm outline-none focus:border-[var(--accent)]" placeholder="例如：发现评测漂移，暂停招聘模型调用并开展复核" /></label>}</AdminConfirmDialog>}
  </div>
}

function PolicyNumber({ label, value, min, max, step = '1', onChange }: { label: string; value: number; min?: number; max?: number; step?: string; onChange: (value: number) => void }) {
  return <label className="text-sm font-semibold">{label}<input type="number" value={value} min={min} max={max} step={step} onChange={event => onChange(Number(event.target.value))} className={inputClass} /></label>
}
