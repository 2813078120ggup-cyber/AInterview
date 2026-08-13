import { ArrowLeft, CalendarCheck2, CalendarDays, FileChartColumn, Gauge, Mail, Phone } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import { interviewStatusText, interviewStatusTone } from '@/lib/interview-status'

type User = { id: string; username: string; realName: string; email?: string; phone?: string; status: number; createdAt?: string }
type InterviewRow = { id: string; title: string; candidateId: string; scheduledAt: string; duration: number; status: number }
type ReportItem = { reportId: string; interviewId: string; interviewTitle: string; candidateName: string; scheduledAt: string; totalScore: number; professionalScore: number; expressionScore: number; logicScore: number; adaptabilityScore: number; status: number }
type Page<T> = { records: T[]; total: number }
const dateText = (value?: string) => value?.replace('T', ' ').slice(0, 16) || '-'

export function AdminCandidateDetail() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [user, setUser] = useState<User>()
  const [interviews, setInterviews] = useState<InterviewRow[]>([])
  const [reports, setReports] = useState<ReportItem[]>([])
  const [error, setError] = useState('')
  useEffect(() => { void Promise.all([request<User>(`/v1/users/${id}`), request<InterviewRow[]>('/v1/interviews'), request<Page<ReportItem>>('/v1/reports/page?pageNo=1&pageSize=300')]).then(([candidate, allInterviews, allReports]) => { setUser(candidate); setInterviews(allInterviews.filter(item => String(item.candidateId) === id)); setReports(allReports.records.filter(item => allInterviews.some(interview => String(interview.candidateId) === id && String(interview.id) === String(item.interviewId)))) }).catch(reason => setError(reason instanceof Error ? reason.message : '候选人详情加载失败，请稍后重试。')) }, [id])
  const latest = reports[0]
  const points = useMemo(() => reports.slice().reverse().map((item, index) => ({ x: 40 + index * (reports.length > 1 ? 520 / (reports.length - 1) : 0), y: 170 - item.totalScore * 1.35, score: item.totalScore })), [reports])
  if (error) return <Card>{error}</Card>
  if (!user) return <Card>正在加载候选人详情…</Card>
  return <div className="space-y-6">
    <Button type="button" variant="ghost" className="mb-5 -ml-3 h-9 px-3 text-sm text-muted-foreground hover:text-foreground" onClick={() => navigate('/admin/candidates')}><ArrowLeft className="h-4 w-4" />返回候选人列表</Button>
    <section className="soft-emphasis-panel overflow-hidden rounded-[24px] p-5 sm:rounded-[30px] sm:p-8"><p className="text-sm font-semibold text-white/55">候选人档案</p><h1 className="mt-2 break-words text-3xl font-bold sm:text-4xl">{user.realName}</h1><p className="mt-2 break-words text-sm leading-6 text-white/60 sm:text-base">@{user.username} · {user.phone || '未填写手机号'} · {user.email || '未填写邮箱'}</p><div className="mt-5"><Badge tone={user.status === 1 ? 'success' : 'warning'}>{user.status === 1 ? '正常' : '停用'}</Badge></div></section>
    <div className="mt-6 grid gap-4 md:grid-cols-2">
      <Card><Phone className="h-5 w-5 text-[var(--accent)]" /><p className="mt-4 text-sm text-muted-foreground">注册手机号</p><strong className="mt-1 block break-all text-lg">{user.phone || '-'}</strong></Card>
      <Card><Mail className="h-5 w-5 text-[var(--accent)]" /><p className="mt-4 text-sm text-muted-foreground">注册邮箱</p><strong className="mt-1 block break-all text-lg">{user.email || '未填写'}</strong></Card>
    </div>
    <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><Card><CalendarCheck2 className="h-5 w-5 text-[var(--accent)]" /><p className="mt-4 text-sm text-muted-foreground">历史面试</p><strong className="mt-1 block text-3xl">{interviews.length}</strong></Card><Card><FileChartColumn className="h-5 w-5 text-[var(--accent)]" /><p className="mt-4 text-sm text-muted-foreground">评测报告</p><strong className="mt-1 block text-3xl">{reports.length}</strong></Card><Card><Gauge className="h-5 w-5 text-[var(--accent)]" /><p className="mt-4 text-sm text-muted-foreground">最新综合分</p><strong className="mt-1 block text-3xl">{latest?.totalScore ?? '-'}</strong></Card><Card><CalendarDays className="h-5 w-5 text-[var(--accent)]" /><p className="mt-4 text-sm text-muted-foreground">最近面试</p><strong className="mt-1 block text-lg">{dateText(latest?.scheduledAt)}</strong></Card></div>
    <div className="mt-6 grid gap-6 xl:grid-cols-[1.1fr_.9fr]"><Card><h2 className="font-bold">能力趋势</h2><svg viewBox="0 0 600 190" className="mt-5 h-44 w-full sm:h-56"><polyline points={points.map(point => `${point.x},${point.y}`).join(' ')} fill="none" stroke="var(--accent)" strokeWidth="4" strokeLinecap="round" />{points.map((point, index) => <g key={index}><circle cx={point.x} cy={point.y} r="6" fill="var(--surface)" stroke="var(--accent)" strokeWidth="3" /><text x={point.x} y={point.y - 14} textAnchor="middle" className="fill-foreground text-[12px] font-bold">{point.score}</text></g>)}</svg></Card><Card className="p-0"><div className="border-b border-border p-5"><h2 className="font-bold">报告列表</h2></div><div className="divide-y divide-border">{reports.map(item => <button type="button" key={item.reportId} onClick={() => navigate(`/admin/interviews?reportInterviewId=${item.interviewId}`)} className="flex min-h-16 w-full items-center justify-between gap-3 px-4 py-4 text-left transition hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--accent)] sm:px-5"><div className="min-w-0"><strong className="block truncate">{item.interviewTitle}</strong><p className="mt-1 text-xs text-muted-foreground">{dateText(item.scheduledAt)}</p></div><strong className="shrink-0 text-[var(--accent)]">{item.totalScore}</strong></button>)}{!reports.length && <p className="p-10 text-center text-sm text-muted-foreground">暂无评测报告</p>}</div></Card></div>
    <Card className="mt-6 p-0"><div className="border-b border-border p-5"><h2 className="font-bold">历史面试</h2></div><div className="divide-y divide-border">{interviews.map(item => <button type="button" key={item.id} onClick={() => navigate(`/admin/interviews/${item.id}/review`)} className="flex min-h-16 w-full items-center justify-between gap-3 px-4 py-4 text-left transition hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--accent)] sm:px-5"><div className="min-w-0"><strong className="block truncate">{item.title}</strong><p className="mt-1 text-xs text-muted-foreground">{dateText(item.scheduledAt)} · {item.duration} 分钟</p></div><Badge className="shrink-0" tone={interviewStatusTone(item.status)}>{interviewStatusText[item.status] ?? '未知状态'}</Badge></button>)}</div></Card>
  </div>
}
