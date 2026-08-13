import { ArrowLeft, BarChart3, ChevronLeft, ChevronRight, Loader2, RefreshCw } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { appendQuery, positionStatusMeta } from '@/lib/recruitment'
import { request } from '@/lib/api'
import type { PageResult } from '@/lib/recruitment'
import type { CompanyPositionAnalytics } from '@/lib/company'

function defaultFrom() { const value = new Date(); value.setDate(value.getDate() - 29); return value.toISOString().slice(0, 10) }
function defaultTo() { return new Date().toISOString().slice(0, 10) }
function errorMessage(reason: unknown) { return reason instanceof Error && reason.message ? reason.message : '岗位分析暂时不可用，请稍后重试。' }

export function CompanyAnalyticsPositions() {
  const [params, setParams] = useSearchParams()
  const [from, setFrom] = useState(params.get('from') || defaultFrom())
  const [to, setTo] = useState(params.get('to') || defaultTo())
  const [pageNo, setPageNo] = useState(Number(params.get('pageNo') || 1))
  const [data, setData] = useState<PageResult<CompanyPositionAnalytics>>({ records: [], total: 0, pageNo: 1, pageSize: 20 })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  const query = useMemo(() => appendQuery({ from, to, pageNo, pageSize: 20 }), [from, to, pageNo])
  const load = useCallback(async (refresh = false) => { if (refresh) setRefreshing(true); else setLoading(true); setError(''); try { setData(await request<PageResult<CompanyPositionAnalytics>>(`/v1/company/recruitment/analytics/positions/page${query}`)) } catch (reason) { setError(errorMessage(reason)) } finally { setLoading(false); setRefreshing(false) } }, [query])
  useEffect(() => { void load() }, [load])
  const apply = (event: React.FormEvent) => { event.preventDefault(); setPageNo(1); setParams({ from, to, pageNo: '1' }) }
  const totalPages = Math.max(1, Math.ceil(data.total / data.pageSize))

  return <div className="space-y-6"><header className="flex flex-col gap-5 border-b border-border pb-6 lg:flex-row lg:items-end lg:justify-between"><div><Link to={`/company/analytics${appendQuery({ from, to })}`} className="inline-flex items-center gap-1 text-sm font-semibold text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" />返回招聘分析</Link><p className="mt-4 text-xs font-bold uppercase tracking-[.14em] text-[var(--accent)]">Data / Positions</p><h1 className="mt-2 text-3xl font-black tracking-[-.05em]">岗位效果</h1><p className="mt-2 text-sm leading-6 text-muted-foreground">按当前企业、统计范围和数据库聚合结果比较岗位表现，不加载全部申请到前端。</p></div><form className="flex flex-wrap items-end gap-2" onSubmit={apply}><DateField label="开始" value={from} onChange={setFrom} /><DateField label="结束" value={to} onChange={setTo} /><Button variant="secondary" type="submit">应用范围</Button><Button variant="ghost" type="button" className="h-11 w-11 px-0" aria-label="刷新岗位效果" onClick={() => void load(true)} disabled={refreshing}><RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} /></Button></form></header>
    {error && <Card className="p-5"><div className="flex flex-wrap items-center justify-between gap-3 text-sm"><span className="text-[var(--danger)]">{error}</span><Button variant="secondary" className="h-9 px-3 text-xs" onClick={() => void load(true)}>重试</Button></div></Card>}
    <Card className="p-5 sm:p-7"><div className="flex items-start gap-3"><span className="grid h-10 w-10 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><BarChart3 className="h-5 w-5" /></span><div><h2 className="text-lg font-bold">{from} 至 {to}</h2><p className="mt-1 text-sm text-muted-foreground">共 {data.total} 个有效岗位，指标中的申请均按投递时间计入。</p></div></div>{loading ? <div className="flex min-h-48 items-center justify-center text-sm text-muted-foreground"><Loader2 className="mr-2 h-5 w-5 animate-spin" />正在读取岗位聚合…</div> : data.records.length === 0 ? <div className="flex min-h-48 items-center justify-center text-sm text-muted-foreground">当前范围没有岗位数据。</div> : <><div className="mt-6 hidden overflow-x-auto md:block"><table className="w-full min-w-[760px] text-left text-sm"><thead className="border-b border-border text-xs text-muted-foreground"><tr><th className="pb-3 font-semibold">岗位</th><th className="pb-3 font-semibold">申请</th><th className="pb-3 font-semibold">平均匹配</th><th className="pb-3 font-semibold">面试</th><th className="pb-3 font-semibold">录用</th><th className="pb-3 font-semibold">转化</th></tr></thead><tbody className="divide-y divide-border">{data.records.map(row => <PositionRow key={row.positionId} row={row} />)}</tbody></table></div><div className="mt-5 space-y-3 md:hidden">{data.records.map(row => <MobilePositionRow key={row.positionId} row={row} />)}</div></>}{data.total > 0 && <div className="mt-6 flex items-center justify-between border-t border-border pt-4 text-xs text-muted-foreground"><span>第 {data.pageNo} / {totalPages} 页</span><div className="flex gap-2"><Button variant="secondary" className="h-9 w-9 px-0" disabled={pageNo <= 1 || loading} onClick={() => setPageNo(value => value - 1)} aria-label="上一页"><ChevronLeft className="h-4 w-4" /></Button><Button variant="secondary" className="h-9 w-9 px-0" disabled={pageNo >= totalPages || loading} onClick={() => setPageNo(value => value + 1)} aria-label="下一页"><ChevronRight className="h-4 w-4" /></Button></div></div>}</Card>
  </div>
}

function PositionRow({ row }: { row: CompanyPositionAnalytics }) { const status = positionStatusMeta[row.recruitmentStatus as keyof typeof positionStatusMeta] ?? positionStatusMeta.DRAFT; return <tr><td className="py-4"><Link className="font-bold hover:text-[var(--accent)]" to={`/company/positions/${row.positionId}`}>{row.positionName}</Link><Badge tone={status.tone} className="ml-2">{status.label}</Badge></td><td className="py-4 font-semibold tabular-nums">{row.applicationCount}</td><td className="py-4 tabular-nums">{row.averageMatchScore || 0}%</td><td className="py-4 tabular-nums">{row.interviewCount} <span className="text-xs text-muted-foreground">({row.interviewConversionRate}%)</span></td><td className="py-4 tabular-nums">{row.hiredCount}</td><td className="py-4 tabular-nums">{row.hireRate}%</td></tr> }
function MobilePositionRow({ row }: { row: CompanyPositionAnalytics }) { const status = positionStatusMeta[row.recruitmentStatus as keyof typeof positionStatusMeta] ?? positionStatusMeta.DRAFT; return <div className="rounded-2xl border border-border bg-background p-4"><div className="flex items-start justify-between gap-3"><Link className="break-words font-bold" to={`/company/positions/${row.positionId}`}>{row.positionName}</Link><Badge tone={status.tone}>{status.label}</Badge></div><div className="mt-4 grid grid-cols-2 gap-3 text-sm"><Stat label="申请" value={row.applicationCount} /><Stat label="平均匹配" value={`${row.averageMatchScore || 0}%`} /><Stat label="面试" value={`${row.interviewCount} · ${row.interviewConversionRate}%`} /><Stat label="录用" value={`${row.hiredCount} · ${row.hireRate}%`} /></div></div> }
function Stat({ label, value }: { label: string; value: string | number }) { return <div><p className="text-xs text-muted-foreground">{label}</p><p className="mt-1 font-bold tabular-nums">{value}</p></div> }
function DateField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) { return <label className="text-xs font-semibold text-muted-foreground">{label}<input type="date" className="mt-1 block h-11 rounded-xl border border-border bg-background px-3 text-sm font-normal text-foreground outline-none focus:border-[var(--accent)]" value={value} onChange={event => onChange(event.target.value)} /></label> }
