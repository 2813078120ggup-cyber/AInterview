import { ArrowRight, BarChart3, CalendarRange, Info, Loader2, RefreshCw } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { appendQuery, formatDateTime } from '@/lib/recruitment'
import { request } from '@/lib/api'
import type { CompanyAnalyticsOverview } from '@/lib/company'

const statuses = ['SUBMITTED', 'AI_INTERVIEW_PENDING', 'AI_INTERVIEWING', 'UNDER_REVIEW', 'OFFLINE_INTERVIEW', 'REJECTED', 'HIRED']
const statusTone: Record<string, 'default' | 'success' | 'warning' | 'danger' | 'info'> = { SUBMITTED: 'default', AI_INTERVIEW_PENDING: 'warning', AI_INTERVIEWING: 'info', UNDER_REVIEW: 'info', OFFLINE_INTERVIEW: 'warning', REJECTED: 'danger', HIRED: 'success' }

function defaultFrom() { const value = new Date(); value.setDate(value.getDate() - 29); return value.toISOString().slice(0, 10) }
function defaultTo() { return new Date().toISOString().slice(0, 10) }
function errorMessage(reason: unknown) { return reason instanceof Error && reason.message ? reason.message : '招聘分析暂时不可用，请稍后重试。' }

export function CompanyAnalytics() {
  const [params, setParams] = useSearchParams()
  const [from, setFrom] = useState(params.get('from') || defaultFrom())
  const [to, setTo] = useState(params.get('to') || defaultTo())
  const [data, setData] = useState<CompanyAnalyticsOverview>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  const query = useMemo(() => appendQuery({ from, to }), [from, to])
  const load = useCallback(async (refresh = false) => { if (refresh) setRefreshing(true); else setLoading(true); setError(''); try { setData(await request<CompanyAnalyticsOverview>(`/v1/company/recruitment/analytics/overview${query}`)) } catch (reason) { setError(errorMessage(reason)) } finally { setLoading(false); setRefreshing(false) } }, [query])
  useEffect(() => { void load() }, [load])
  const applyRange = (event: React.FormEvent) => { event.preventDefault(); setParams({ from, to }) }

  return <div className="space-y-6">
    <header className="flex flex-col gap-5 border-b border-border pb-6 lg:flex-row lg:items-end lg:justify-between"><div><p className="text-xs font-bold uppercase tracking-[.14em] text-[var(--accent)]">数据分析</p><h1 className="mt-2 text-3xl font-black tracking-[-.05em]">招聘分析</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">按统计周期查看招聘阶段、处理效率和岗位表现。数据用于运营分析，不代表因果结论。</p></div><form className="flex flex-wrap items-end gap-2" onSubmit={applyRange}><DateField label="开始" value={from} onChange={setFrom} /><DateField label="结束" value={to} onChange={setTo} /><Button variant="secondary" type="submit"><CalendarRange className="h-4 w-4" />应用范围</Button><Button variant="ghost" type="button" className="h-11 w-11 px-0" aria-label="刷新招聘分析" onClick={() => void load(true)} disabled={refreshing}><RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} /></Button></form></header>
    {error && <Card className="p-5"><div className="flex flex-wrap items-center justify-between gap-3 text-sm"><span className="text-[var(--danger)]">{error}</span><Button variant="secondary" className="h-9 px-3 text-xs" onClick={() => void load(true)}>重试</Button></div></Card>}
    {loading && !data ? <Card className="flex min-h-72 items-center justify-center text-sm text-muted-foreground"><Loader2 className="mr-2 h-5 w-5 animate-spin" />正在计算企业范围数据…</Card> : data && <>
      <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground"><Badge tone="info">统计范围 {data.from} 至 {data.to}</Badge><span>申请样本 {data.sampleSize} 份</span>{data.lowSample && <span className="inline-flex items-center gap-1 text-[var(--warning-foreground)]"><Info className="h-4 w-4" />样本较少，趋势仅作参考</span>}</div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5"><Metric label="申请数" value={data.applicationCount} detail="按投递时间计入" /><Metric label="平均初筛" value={`${data.averageInitialScreeningHours}h`} detail="投递至首次企业评估" /><Metric label="平均进入面试" value={`${data.averageTimeToInterviewHours}h`} detail="投递至首个面试阶段" /><Metric label="平均招聘周期" value={`${data.averageHiringCycleDays}d`} detail="投递至录用" /><Metric label="录用率" value={`${data.hireRate}%`} detail="录用申请 / 申请样本" /></div>
      <div className="grid gap-6 xl:grid-cols-[1.1fr_.9fr]"><Card className="p-5 sm:p-7"><Section title="阶段漏斗" description="按申请是否到达过该阶段统计，转化率以本周期申请样本为分母。" /><div className="mt-6 space-y-4">{statuses.map(status => { const stage = data.funnel.find(item => item.status === status); return <div key={status}><div className="flex flex-wrap items-center justify-between gap-2"><div className="flex items-center gap-2"><Badge tone={statusTone[status]}>{stage?.label || status}</Badge><span className="text-sm font-semibold">{stage?.count ?? 0} 份</span></div><span className="text-xs text-muted-foreground">{stage?.conversionRate ?? 0}%</span></div><div className="mt-2 h-2 overflow-hidden rounded-full bg-muted"><div className="h-full rounded-full bg-[var(--accent)]" style={{ width: `${Math.min(stage?.conversionRate ?? 0, 100)}%` }} /></div></div> })}</div></Card><Card className="p-5 sm:p-7"><Section title="匹配分分布" description="仅统计本周期内有匹配分的申请，不代表录用概率。" /><div className="mt-6 space-y-4">{data.matchScoreDistribution.map(bucket => <div key={bucket.key}><div className="flex justify-between text-sm"><span className="font-semibold">{bucket.label}</span><span className="text-muted-foreground">{bucket.count} 份 · {bucket.percentage}%</span></div><div className="mt-2 h-2 overflow-hidden rounded-full bg-muted"><div className="h-full rounded-full bg-[var(--primary)]" style={{ width: `${Math.min(bucket.percentage, 100)}%` }} /></div></div>)}</div>{!data.matchScoreDistribution.some(item => item.count > 0) && <p className="mt-5 text-sm text-muted-foreground">当前周期没有可用匹配分。</p>}</Card></div>
      <Card className="p-5 sm:p-7"><Section title="招聘效率摘要" description="转化指标只描述数据中的共同变化，不推断因果关系。" /><div className="mt-5 grid gap-4 sm:grid-cols-2"><Metric label="面试转化率" value={`${data.interviewConversionRate}%`} detail="进入面试阶段 / 申请样本" /><Metric label="录用率" value={`${data.hireRate}%`} detail="已录用 / 申请样本" /></div></Card>
      <div className="flex flex-col gap-3 border-t border-border pt-4 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between"><span>数据生成时间：{formatDateTime(data.generatedAt)} · 查询已按当前企业范围聚合</span><Link className="inline-flex items-center gap-1 font-semibold text-[var(--accent)] hover:underline" to={`/company/analytics/positions${query}`}>查看岗位效果<ArrowRight className="h-3.5 w-3.5" /></Link></div>
    </>}
  </div>
}

function DateField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) { return <label className="text-xs font-semibold text-muted-foreground">{label}<input type="date" className="mt-1 block h-11 rounded-xl border border-border bg-background px-3 text-sm font-normal text-foreground outline-none focus:border-[var(--accent)]" value={value} onChange={event => onChange(event.target.value)} /></label> }
function Metric({ label, value, detail }: { label: string; value: string | number; detail: string }) { return <div className="rounded-2xl border border-border bg-background p-4"><p className="text-xs font-semibold text-muted-foreground">{label}</p><p className="mt-2 text-2xl font-black tracking-[-.04em] tabular-nums">{value}</p><p className="mt-1 text-xs leading-5 text-muted-foreground">{detail}</p></div> }
function Section({ title, description }: { title: string; description: string }) { return <div><div className="flex items-center gap-2"><BarChart3 className="h-4 w-4 text-[var(--accent)]" /><h2 className="text-lg font-bold">{title}</h2></div><p className="mt-1 text-sm leading-6 text-muted-foreground">{description}</p></div> }
