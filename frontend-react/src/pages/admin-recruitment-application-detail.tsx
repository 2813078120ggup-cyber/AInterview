import { ArrowLeft, Bot, BriefcaseBusiness, CheckCircle2, Clock3, FileWarning, Loader2, RefreshCw, ShieldCheck, TimerReset, UserRound } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import type { AdminRecruitmentDetail, AdminRecruitmentTask } from '@/lib/admin'

function formatDate(value?: string | null) { return value ? value.replace('T', ' ').slice(0, 16) : '—' }
function taskTone(status?: string | null): 'default' | 'success' | 'warning' | 'danger' | 'info' { return status === 'FAILED' ? 'danger' : status === 'SUCCESS' ? 'success' : status === 'RUNNING' ? 'info' : status === 'PENDING' ? 'warning' : 'default' }
function taskLabel(status?: string | null) { return status === 'FAILED' ? '失败' : status === 'SUCCESS' ? '成功' : status === 'RUNNING' ? '执行中' : status === 'PENDING' ? '排队中' : status || '未创建' }
function interviewLabel(status?: number | null) { return status === 7 ? '技术失败' : status === 6 ? '报告就绪' : status === 5 ? '报告生成中' : status === 2 ? '已完成' : status === 3 ? '已取消' : status === 1 ? '进行中' : status === 0 ? '待开始' : '未关联' }

export function AdminRecruitmentApplicationDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const [detail, setDetail] = useState<AdminRecruitmentDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retrying, setRetrying] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!id) return
    setLoading(true)
    setError('')
    try { setDetail(await request<AdminRecruitmentDetail>(`/v1/admin/recruitment/applications/${id}`)) }
    catch (reason) { setError(reason instanceof Error ? reason.message : '申请关联加载失败，请稍后重试。') }
    finally { setLoading(false) }
  }, [id])
  useEffect(() => { void load() }, [load])

  async function retry(task: AdminRecruitmentTask) {
    if (!task.retryable || !window.confirm('确认重试这个技术任务吗？不会修改申请阶段、报告发布状态或企业决定。')) return
    setRetrying(task.id)
    try { await request(`/v1/admin/recruitment/tasks/${task.id}/retry`, { method: 'POST', body: JSON.stringify({ confirm: true }) }); await load() }
    catch (reason) { setError(reason instanceof Error ? reason.message : '技术任务重试失败，请稍后重试。') }
    finally { setRetrying(null) }
  }

  const app = detail?.application
  const from = (location.state as { from?: string } | null)?.from ?? ''
  return <div className="space-y-6">
    <div className="flex flex-wrap items-center justify-between gap-3"><Button type="button" variant="ghost" className="px-3" onClick={() => navigate(`/admin/recruitment${from}`)}><ArrowLeft className="h-4 w-4" />返回招聘运营</Button><Button type="button" variant="secondary" onClick={() => void load()} disabled={loading}><RefreshCw className="h-4 w-4" />刷新关联</Button></div>
    {loading && <Card className="flex items-center justify-center gap-2 p-16 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" />正在加载申请关联…</Card>}
    {error && <Card className="border-[var(--danger)]/30 bg-[var(--danger)]/5 text-sm text-[var(--danger)]">{error}</Card>}
    {!loading && app && <>
      <header className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between"><div className="min-w-0"><p className="text-sm font-semibold text-[var(--accent)]">平台异常定位 · {app.applicationNo}</p><h1 className="mt-2 truncate text-3xl font-bold tracking-tight sm:text-4xl">{app.candidate.name}</h1><p className="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 text-muted-foreground"><span>{app.company.name}</span><span>·</span><span>{app.position.name}</span><span>·</span><span>{app.statusLabel}</span></p></div><div className="flex flex-wrap gap-2"><Badge tone={app.stale ? 'warning' : app.status === 'HIRED' ? 'success' : app.status === 'REJECTED' ? 'danger' : 'default'}>{app.stale ? '长时间未推进' : app.statusLabel}</Badge><Badge tone="info">只读观测</Badge></div></header>

      <Card className="border-[var(--accent)]/25 bg-[var(--accent-soft)]/35"><div className="flex items-start gap-3"><ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-[var(--accent)]" /><div><strong>平台边界</strong><p className="mt-1 text-sm leading-6 text-muted-foreground">超级管理员可以定位企业、候选人和技术任务的关联，但不会在此页面推进阶段、录用、淘汰或发布报告。</p></div></div></Card>

      <section className="grid gap-4 md:grid-cols-3"><Card><div className="flex items-center gap-3"><span className="grid h-10 w-10 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><BriefcaseBusiness className="h-5 w-5" /></span><div><p className="text-xs text-muted-foreground">企业 / 岗位</p><strong className="block truncate">{app.company.name}</strong></div></div><p className="mt-5 text-sm text-muted-foreground">{app.position.secondary || '未填写部门'} · 岗位 ID #{app.position.id}</p><p className="mt-1 text-xs text-muted-foreground">企业编码 {app.company.code || '—'}</p></Card><Card><div className="flex items-center gap-3"><span className="grid h-10 w-10 place-items-center rounded-2xl bg-[var(--info)] text-[var(--info-foreground)]"><UserRound className="h-5 w-5" /></span><div><p className="text-xs text-muted-foreground">候选人关联</p><strong className="block truncate">{app.candidate.name}</strong></div></div><p className="mt-5 text-sm text-muted-foreground">账号 {app.candidate.username}</p><p className="mt-1 text-xs text-muted-foreground">候选人 ID #{app.candidate.id} · 不展示联系方式和简历原文</p></Card><Card><div className="flex items-center gap-3"><span className="grid h-10 w-10 place-items-center rounded-2xl bg-[var(--warning)] text-[var(--warning-foreground)]"><Clock3 className="h-5 w-5" /></span><div><p className="text-xs text-muted-foreground">申请时间</p><strong className="block">{formatDate(app.submittedAt)}</strong></div></div><p className="mt-5 text-sm text-muted-foreground">最近更新 {formatDate(app.updatedAt)}</p><p className="mt-1 text-xs text-muted-foreground">匹配度 {app.matchScore == null ? '未形成' : app.matchScore}</p></Card></section>

      <section className="grid gap-5 xl:grid-cols-[1.15fr_.85fr]"><Card><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">任务关联</p><h2 className="mt-2 text-xl font-bold">异常在哪里</h2></div><Bot className="h-5 w-5 text-[var(--accent)]" /></div><div className="mt-6 grid gap-3"><TaskRow title="岗位匹配" task={app.matchTask} retry={retry} retrying={retrying} /><TaskRow title="报告生成" task={app.interview?.reportTask} retry={retry} retrying={retrying} /></div><p className="mt-5 text-xs leading-5 text-muted-foreground">任务状态来自服务端持久化记录；不返回 Prompt、Provider 原始响应、密钥、内部堆栈或快照内容。</p></Card><Card><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">面试 / 报告</p>{app.interview ? <div className="mt-5"><div className="flex items-center justify-between gap-3"><strong>面试 #{app.interview.id}</strong><Badge tone={app.interview.status === 7 ? 'danger' : app.interview.status === 6 ? 'success' : 'info'}>{interviewLabel(app.interview.status)}</Badge></div><dl className="mt-5 grid gap-3 text-sm"><div className="flex justify-between gap-4"><dt className="text-muted-foreground">类型</dt><dd>{app.interview.type || '—'}</dd></div><div className="flex justify-between gap-4"><dt className="text-muted-foreground">预约时间</dt><dd>{formatDate(app.interview.scheduledAt)}</dd></div><div className="flex justify-between gap-4"><dt className="text-muted-foreground">开始 / 结束</dt><dd>{formatDate(app.interview.startedAt)} / {formatDate(app.interview.endedAt)}</dd></div><div className="flex justify-between gap-4"><dt className="text-muted-foreground">报告状态</dt><dd>{app.interview.reportStatus == null ? '未生成' : app.interview.reportStatus === 1 ? '已发布' : '仅内部可见'}</dd></div></dl></div> : <div className="mt-6 rounded-2xl bg-muted/50 p-5 text-sm text-muted-foreground">当前申请尚未关联面试。</div>}</Card></section>

      <Card><div className="flex items-center gap-2"><CheckCircle2 className="h-5 w-5 text-[var(--accent)]" /><h2 className="text-xl font-bold">申请阶段历史</h2></div><div className="mt-5 grid gap-3">{detail?.statusHistory.map(item => <div key={item.id} className="flex flex-wrap items-center gap-3 rounded-2xl border border-border/70 px-4 py-3 text-sm"><span className="grid h-8 w-8 place-items-center rounded-xl bg-muted"><Clock3 className="h-4 w-4 text-muted-foreground" /></span><span className="font-semibold">{item.fromStatus || '初始'} → {item.toStatus}</span><span className="text-xs text-muted-foreground">{formatDate(item.createdAt)} · 操作人 #{item.operatorId || '—'}</span></div>)}{!detail?.statusHistory.length && <p className="rounded-2xl bg-muted/50 p-5 text-sm text-muted-foreground">暂无阶段历史记录。</p>}</div><p className="mt-5 flex items-center gap-2 text-xs text-muted-foreground"><FileWarning className="h-3.5 w-3.5" />历史记录仅用于定位链路，不提供替代企业执行决定的入口。</p></Card>
    </>}
  </div>
}

function TaskRow({ title, task, retry, retrying }: { title: string; task?: AdminRecruitmentTask | null; retry: (task: AdminRecruitmentTask) => void; retrying: string | null }) {
  return <div className="flex flex-col gap-3 rounded-2xl border border-border/70 bg-background/45 p-4 sm:flex-row sm:items-center sm:justify-between"><div className="flex min-w-0 items-center gap-3"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-muted"><Bot className="h-4 w-4 text-muted-foreground" /></span><div className="min-w-0"><strong className="block">{title}</strong><span className="mt-1 block truncate text-xs text-muted-foreground">{task ? `任务 #${task.id} · 尝试 ${task.attempts ?? 0}/${task.maxAttempts ?? '—'}` : '尚未创建任务'}</span></div></div><div className="flex items-center gap-2"><Badge tone={taskTone(task?.status)}>{taskLabel(task?.status)}</Badge>{task?.retryable && <Button type="button" variant="secondary" className="h-9 px-3 text-xs" onClick={() => retry(task)} disabled={retrying === task.id}>{retrying === task.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <TimerReset className="h-3.5 w-3.5" />}受控重试</Button>}</div></div>
}
