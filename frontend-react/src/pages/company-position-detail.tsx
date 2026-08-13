import { ArrowLeft, BarChart3, Copy, Edit3, ExternalLink, Loader2, MapPin, Megaphone, Users, UserRoundCheck, XCircle } from 'lucide-react'
import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import { formatDateTime, positionStatusMeta, salaryLabel, type PositionDetail, type RecruitmentJob } from '@/lib/recruitment'

export function CompanyPositionDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const [detail, setDetail] = useState<PositionDetail>()
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState((location.state as { message?: string } | null)?.message || '')

  const load = useCallback(async () => {
    if (!id) return
    setLoading(true)
    setError('')
    try {
      setDetail(await request<PositionDetail>(`/v1/company/recruitment/positions/${id}`))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '岗位详情加载失败')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => { void load() }, [load])

  async function changeStatus(status: 'PUBLISHED' | 'CLOSED') {
    if (!detail || !id) return
    const isClosing = status === 'CLOSED'
    const confirmation = isClosing ? '关闭后候选人将不能再投递，但历史申请会保留。确定关闭吗？' : '发布前会再次校验岗位完整性，确定发布吗？'
    if (!window.confirm(confirmation)) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      await request<RecruitmentJob>(`/v1/company/recruitment/positions/${id}/status`, { method: 'PUT', body: JSON.stringify({ status, note: isClosing ? 'HR 关闭招聘岗位' : undefined }) })
      setMessage(isClosing ? '岗位已关闭，历史申请仍然保留。' : '岗位已发布，候选人现在可以查看并投递。')
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '岗位状态更新失败')
    } finally {
      setBusy(false)
    }
  }

  async function clone() {
    if (!detail || !id || !window.confirm('将复制当前岗位内容并创建一个草稿，继续吗？')) return
    setBusy(true)
    setError('')
    try {
      const cloned = await request<RecruitmentJob>(`/v1/company/recruitment/positions/${id}/clone`, { method: 'POST' })
      navigate(`/company/positions/${cloned.id}`, { state: { message: '岗位副本已创建为草稿。' } })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '岗位复制失败')
    } finally {
      setBusy(false)
    }
  }

  if (loading && !detail) return <Card className="grid min-h-64 place-items-center"><Loader2 className="h-7 w-7 animate-spin text-[var(--accent)]" /></Card>
  if (error && !detail) return <Card className="border-rose-200 bg-rose-50/80 p-5 text-sm text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/30" role="alert">{error}<Button type="button" variant="secondary" className="ml-3 h-9 px-3" onClick={() => void load()}>重试</Button></Card>
  if (!detail) return null

  const { job, statistics } = detail
  const meta = positionStatusMeta[job.recruitmentStatus] || positionStatusMeta.DRAFT
  const isPublished = job.recruitmentStatus === 'PUBLISHED'

  return <div className="space-y-6">
    <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between"><div className="flex items-start gap-3"><Button type="button" variant="ghost" className="h-10 shrink-0 px-3" onClick={() => navigate('/company/positions')}><ArrowLeft className="h-4 w-4" />岗位列表</Button><div><p className="text-sm font-bold text-[var(--accent)]">岗位详情 · {job.positionCode}</p><h1 className="mt-2 break-words text-3xl font-black tracking-[-.04em]">{job.name}</h1><p className="mt-2 text-muted-foreground">{job.department || '未设置部门'} · 最后更新于 {formatDateTime(job.updatedAt)}</p></div></div><div className="flex flex-wrap gap-2 lg:justify-end"><Button type="button" variant="secondary" onClick={() => navigate(`/company/positions/${job.id}/edit`)}><Edit3 className="h-4 w-4" />编辑</Button><Button type="button" variant="secondary" onClick={() => void clone()} disabled={busy}><Copy className="h-4 w-4" />复制</Button>{isPublished ? <Button type="button" variant="danger" onClick={() => void changeStatus('CLOSED')} disabled={busy}><XCircle className="h-4 w-4" />关闭岗位</Button> : <Button type="button" onClick={() => void changeStatus('PUBLISHED')} disabled={busy}><Megaphone className="h-4 w-4" />发布岗位</Button>}</div></header>
    {message && <p className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/30 dark:text-emerald-200">{message}</p>}
    {error && <p role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-200">{error}</p>}

    <Card className="overflow-hidden p-0"><div className="border-b border-border bg-muted/30 p-5 sm:p-7"><div className="flex flex-wrap items-center gap-3"><Badge tone={meta.tone}>{meta.label}</Badge><span className="text-sm text-muted-foreground">{job.city || '地点面议'} · {salaryLabel(job)}</span></div><div className="mt-5 flex flex-wrap gap-2">{job.skillTags.map(tag => <span key={tag} className="rounded-full border border-border bg-surface px-3 py-1.5 text-xs font-semibold text-muted-foreground">{tag}</span>)}{!job.skillTags.length && <span className="text-sm text-muted-foreground">尚未添加技能标签</span>}</div></div><div className="grid gap-4 p-5 sm:grid-cols-2 lg:grid-cols-4"><Stat icon={<Users className="h-4 w-4" />} label="岗位申请" value={statistics.applicationCount} /><Stat icon={<BarChart3 className="h-4 w-4" />} label="平均匹配度" value={`${statistics.averageMatchScore.toFixed(1)}%`} /><Stat icon={<UserRoundCheck className="h-4 w-4" />} label="面试人数" value={statistics.interviewCount} /><Stat icon={<UserRoundCheck className="h-4 w-4" />} label="录用人数" value={statistics.hiredCount} /></div></Card>

    <div className="grid gap-5 xl:grid-cols-[minmax(0,1.1fr)_minmax(360px,.9fr)]"><Card><SectionHeading icon={<Megaphone className="h-4 w-4" />} title="候选人端预览" description="发布后候选人将在岗位大厅看到以下信息。" /><div className="mt-5 rounded-[20px] border border-border bg-background p-5"><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-[.16em] text-[var(--accent)]">{job.company.shortName || job.company.name}</p><h2 className="mt-2 text-xl font-black">{job.name}</h2><p className="mt-1 text-sm text-muted-foreground"><MapPin className="mr-1 inline h-3.5 w-3.5" />{job.city || '工作地点面议'} · {salaryLabel(job)}</p></div><Badge tone={meta.tone}>{meta.label}</Badge></div><PreviewText title="岗位介绍" value={job.description} /><PreviewText title="任职要求" value={job.requirements} /><div className="mt-5 flex items-center justify-between gap-3 border-t border-border pt-4"><span className="text-xs text-muted-foreground">{isPublished ? '候选人可在岗位大厅查看并投递' : '发布后才会进入候选人岗位大厅'}</span>{isPublished && <Button type="button" variant="secondary" className="h-9 px-3" onClick={() => navigate('/jobs')}><ExternalLink className="h-3.5 w-3.5" />打开岗位大厅</Button>}</div></div></Card><Card><SectionHeading icon={<BarChart3 className="h-4 w-4" />} title="岗位信息" description="用于复核发布内容和招聘周期。" /><dl className="mt-5 divide-y divide-border text-sm"><InfoRow label="岗位编码" value={job.positionCode} /><InfoRow label="部门" value={job.department || '未设置'} /><InfoRow label="经验要求" value={job.experienceRequirement || '不限'} /><InfoRow label="学历要求" value={job.educationRequirement || '不限'} /><InfoRow label="发布时间" value={formatDateTime(job.publishedAt)} /><InfoRow label="截止时间" value={formatDateTime(job.expiresAt)} /></dl></Card></div>
  </div>
}

function Stat({ icon, label, value }: { icon: ReactNode; label: string; value: number | string }) { return <div className="rounded-2xl border border-border bg-background p-4"><span className="flex h-8 w-8 items-center justify-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]">{icon}</span><p className="mt-4 text-xs text-muted-foreground">{label}</p><strong className="mt-1 block text-2xl font-black tabular-nums">{value}</strong></div> }
function SectionHeading({ icon, title, description }: { icon: ReactNode; title: string; description: string }) { return <div><div className="flex items-center gap-2 text-[var(--accent)]">{icon}<h2 className="text-lg font-black text-foreground">{title}</h2></div><p className="mt-1 text-sm text-muted-foreground">{description}</p></div> }
function PreviewText({ title, value }: { title: string; value?: string }) { return <div className="mt-5"><h3 className="text-sm font-bold">{title}</h3><p className="mt-2 whitespace-pre-line text-sm leading-7 text-muted-foreground">{value || '暂未填写'}</p></div> }
function InfoRow({ label, value }: { label: string; value: string }) { return <div className="flex items-center justify-between gap-4 py-3"><dt className="text-muted-foreground">{label}</dt><dd className="text-right font-semibold">{value}</dd></div> }
