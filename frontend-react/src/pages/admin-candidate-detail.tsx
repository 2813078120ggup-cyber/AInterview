import {
  AlertTriangle,
  ArrowLeft,
  BriefcaseBusiness,
  CalendarCheck2,
  CircleUserRound,
  Clock3,
  FileChartColumn,
  FileText,
  Gauge,
  Loader2,
  Mail,
  Phone,
  ShieldCheck,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { buttonClassName } from '@/components/ui/button-styles'
import { Card } from '@/components/ui/card'
import { request, requestBlob } from '@/lib/api'
import type { AdminCandidateProfile } from '@/lib/admin'
import { interviewStatusText, interviewStatusTone } from '@/lib/interview-status'
import { applicationStatusMeta, type ApplicationStatus } from '@/lib/recruitment'

const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

const resumeStatusMeta: Record<string, { label: string; tone: 'default' | 'success' | 'warning' | 'danger' | 'info' }> = {
  MANUAL: { label: '手动维护', tone: 'default' },
  PENDING: { label: '等待解析', tone: 'warning' },
  PROCESSING: { label: '正在解析', tone: 'info' },
  SUCCESS: { label: '解析完成', tone: 'success' },
  FAILED: { label: '解析失败', tone: 'danger' },
}

const loginMethodLabels: Record<string, string> = { PASSWORD: '密码', SMS: '短信验证码', EMAIL: '邮箱验证码' }

function formatDateTime(value?: string | null) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '暂无记录' : dateTimeFormatter.format(date)
}

function applicationMeta(status: string) {
  return applicationStatusMeta[status as ApplicationStatus] ?? { label: status || '未知状态', tone: 'default' as const }
}

function scoreText(score?: number | null) {
  return score == null ? '—' : Number(score).toFixed(Number(score) % 1 === 0 ? 0 : 1)
}

export function AdminCandidateDetail() {
  const { id = '' } = useParams()
  const [profile, setProfile] = useState<AdminCandidateProfile>()
  const [avatarUrl, setAvatarUrl] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    request<AdminCandidateProfile>(`/v1/admin/candidates/${id}`)
      .then(result => { if (active) setProfile(result) })
      .catch(reason => { if (active) setError(reason instanceof Error ? reason.message : '候选人资料加载失败，请稍后重试。') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [id])

  useEffect(() => {
    if (!profile?.account.avatarAvailable) {
      setAvatarUrl('')
      return
    }
    let active = true
    let objectUrl = ''
    requestBlob(`/v1/admin/candidates/${id}/avatar`).then(blob => {
      if (!active) return
      objectUrl = URL.createObjectURL(blob)
      setAvatarUrl(objectUrl)
    }).catch(() => { if (active) setAvatarUrl('') })
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [id, profile?.account.avatarAvailable])

  const completeness = useMemo(() => {
    if (!profile) return 0
    const checks = [
      Boolean(profile.account.realName?.trim()),
      Boolean(profile.account.phone),
      Boolean(profile.account.email),
      profile.account.phoneVerified || profile.account.emailVerified,
      profile.overview.resumeCount > 0,
    ]
    return Math.round(checks.filter(Boolean).length / checks.length * 100)
  }, [profile])

  const scorePoints = useMemo(() => {
    if (!profile) return []
    const rows = profile.reports.filter(item => item.totalScore != null).slice().reverse()
    return rows.map((item, index) => ({
      x: 40 + index * (rows.length > 1 ? 520 / (rows.length - 1) : 0),
      y: Math.max(26, Math.min(166, 174 - Number(item.totalScore) * 1.4)),
      score: Number(item.totalScore),
    }))
  }, [profile])

  if (loading) return <CandidateDetailLoading />
  if (error || !profile) return <Card className="p-8 text-center"><p role="alert" className="text-sm text-rose-700">{error || '候选人资料不存在。'}</p><Button type="button" variant="secondary" className="mt-5" onClick={() => window.location.reload()}>重新加载</Button></Card>

  const { account, overview } = profile
  const displayName = account.realName || '未填写姓名'
  const initial = displayName.trim().slice(0, 1) || account.username.slice(0, 1).toUpperCase()

  return <div className="space-y-6">
    <header className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
      <div className="min-w-0">
        <Link to="/admin/candidates" className="inline-flex items-center gap-2 rounded-full text-sm font-semibold text-muted-foreground transition hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]"><ArrowLeft aria-hidden="true" className="h-4 w-4" />返回候选人档案</Link>
        <p className="mt-5 text-sm font-semibold text-[var(--accent)]">用户 · 候选人档案</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">候选人资料</h1>
        <p className="mt-3 max-w-3xl text-muted-foreground">统一查看账户资料、简历、岗位申请、面试与评测报告。</p>
      </div>
      <Link to={`/admin/users/${account.id}`} className={buttonClassName({ variant: 'secondary' })}><ShieldCheck aria-hidden="true" className="h-4 w-4" />账号与角色</Link>
    </header>

    {!account.identityConsistent && <div className="flex flex-col gap-4 rounded-[24px] border border-amber-300 bg-amber-50 px-5 py-4 text-amber-950 dark:border-amber-900/70 dark:bg-amber-950/30 dark:text-amber-100 sm:flex-row sm:items-center sm:justify-between" role="alert">
      <div className="flex items-start gap-3"><AlertTriangle aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0" /><div><strong className="block">检测到候选人身份冲突</strong><p className="mt-1 text-sm leading-6 opacity-80">当前账号同时绑定了 {account.roles.join('、')}。候选人身份应独立存在，请在账号详情中调整角色。</p></div></div>
      <Link to={`/admin/users/${account.id}`} className={buttonClassName({ variant: 'secondary', size: 'compact', className: 'shrink-0' })}>调整角色</Link>
    </div>}

    <Card className="relative overflow-hidden p-0">
      <div aria-hidden="true" className="absolute -right-16 -top-20 h-64 w-64 rounded-full bg-[var(--accent-soft)] opacity-70 blur-3xl" />
      <div className="relative grid gap-6 p-5 sm:p-7 xl:grid-cols-[minmax(0,1fr)_280px] xl:items-center">
        <div className="flex min-w-0 flex-col gap-5 sm:flex-row sm:items-center">
          <span className="grid h-20 w-20 shrink-0 place-items-center overflow-hidden rounded-[26px] border border-[var(--accent)]/15 bg-[var(--accent-soft)] text-2xl font-bold text-[var(--accent)] shadow-sm">
            {avatarUrl ? <img src={avatarUrl} alt={`${displayName}的头像`} className="h-full w-full object-cover" /> : initial}
          </span>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2"><h2 className="break-words text-2xl font-bold sm:text-3xl">{displayName}</h2><Badge tone={account.status === 1 ? 'success' : 'default'}>{account.status === 1 ? '已启用' : '已停用'}</Badge><Badge tone={account.identityConsistent ? 'info' : 'warning'}>{account.identityConsistent ? '候选人' : '身份冲突'}</Badge></div>
            <p className="mt-2 break-all text-sm text-muted-foreground">@{account.username} · 用户 ID {account.id}</p>
            <div className="mt-4 flex flex-wrap gap-2">{account.availableLoginMethods.map(method => <Badge key={method}>{loginMethodLabels[method] || method}</Badge>)}{!account.availableLoginMethods.length && <Badge tone="warning">无可用登录方式</Badge>}</div>
          </div>
        </div>
        <div className="rounded-[22px] border border-border/70 bg-background/75 p-4 backdrop-blur-sm">
          <div className="flex items-center justify-between gap-3"><span className="text-sm font-semibold">资料完整度</span><strong className="tabular-nums text-[var(--accent)]">{completeness}%</strong></div>
          <div className="mt-3 h-2 overflow-hidden rounded-full bg-muted"><span className="block h-full rounded-full bg-[var(--accent)] transition-[width]" style={{ width: `${completeness}%` }} /></div>
          <p className="mt-3 text-xs leading-5 text-muted-foreground">包含姓名、联系方式、验证状态和有效简历。</p>
        </div>
      </div>
      <div className="relative grid border-t border-border bg-muted/20 sm:grid-cols-2 xl:grid-cols-4">
        <ProfileField icon={Phone} label="手机号" value={account.phone || '未填写'} verified={account.phoneVerified} />
        <ProfileField icon={Mail} label="邮箱" value={account.email || '未填写'} verified={account.emailVerified} />
        <ProfileField icon={Clock3} label="最近登录" value={formatDateTime(account.lastLoginAt)} />
        <ProfileField icon={CircleUserRound} label="注册时间" value={formatDateTime(account.createdAt)} />
      </div>
    </Card>

    <Card className="p-0">
      <div className="border-b border-border p-5"><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">求职链路</p><h2 className="mt-1 text-xl font-bold">资料与进展</h2></div>
      <div className="grid sm:grid-cols-2 xl:grid-cols-4">
        <JourneyMetric icon={FileText} label="有效简历" value={overview.resumeCount} detail={overview.resumeCount > 0 ? '包含默认与历史版本' : '尚未创建简历'} />
        <JourneyMetric icon={BriefcaseBusiness} label="岗位申请" value={overview.applicationCount} detail={overview.applicationCount > 0 ? '已进入招聘流程' : '暂无岗位申请'} />
        <JourneyMetric icon={CalendarCheck2} label="面试记录" value={overview.interviewCount} detail={overview.interviewCount > 0 ? '包含全部面试状态' : '暂无面试记录'} />
        <JourneyMetric icon={FileChartColumn} label="评测报告" value={overview.reportCount} detail={overview.latestScore == null ? '暂无可用分数' : `最新综合分 ${scoreText(overview.latestScore)}`} />
      </div>
      <div className="border-t border-border px-5 py-3 text-xs text-muted-foreground">最近业务活动：{formatDateTime(overview.latestActivityAt)}</div>
    </Card>

    <div className="grid gap-6 xl:grid-cols-[1.05fr_.95fr]">
      <div className="space-y-6">
        <Card className="p-0">
          <SectionHeader eyebrow="候选人资料" title="简历" count={overview.resumeCount} />
          <div className="divide-y divide-border">{profile.resumes.map(resume => {
            const status = resumeStatusMeta[resume.parseStatus || ''] ?? { label: resume.parseStatus || '未知状态', tone: 'default' as const }
            return <article key={resume.id} className="p-5">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h3 className="break-words font-bold">{resume.title || '未命名简历'}</h3>{resume.defaultResume && <Badge tone="info">默认简历</Badge>}<Badge tone={status.tone}>{status.label}</Badge></div><p className="mt-1 break-all text-xs text-muted-foreground">{resume.fileName || '手动维护'} · 更新于 {formatDateTime(resume.updatedAt)}</p></div></div>
              {resume.summary && <p className="mt-4 text-sm leading-6 text-muted-foreground">{resume.summary}</p>}
              {resume.skills.length > 0 && <div className="mt-4 flex flex-wrap gap-2">{resume.skills.map(skill => <Badge key={skill}>{skill}</Badge>)}</div>}
            </article>
          })}{!profile.resumes.length && <EmptyState icon={FileText} title="尚未创建简历" description="候选人上传或维护简历后，这里会展示默认版本、解析状态与技能标签。" />}</div>
        </Card>

        <Card className="p-0">
          <SectionHeader eyebrow="招聘进展" title="岗位申请" count={overview.applicationCount} />
          <div className="divide-y divide-border">{profile.applications.map(application => {
            const meta = applicationMeta(application.status)
            return <Link key={application.id} to={`/admin/recruitment/applications/${application.id}`} className="flex min-h-20 items-start justify-between gap-4 px-5 py-4 transition hover:bg-muted/35 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--accent)]">
              <div className="min-w-0"><h3 className="truncate font-bold">{application.positionName}</h3><p className="mt-1 truncate text-sm text-muted-foreground">{application.companyName}</p><p className="mt-2 text-xs text-muted-foreground">{application.applicationNo} · 投递于 {formatDateTime(application.submittedAt)}</p></div>
              <div className="flex shrink-0 flex-col items-end gap-2"><Badge tone={meta.tone}>{meta.label}</Badge>{application.matchScore != null && <span className="text-xs font-semibold text-[var(--accent)]">匹配 {scoreText(application.matchScore)}</span>}</div>
            </Link>
          })}{!profile.applications.length && <EmptyState icon={BriefcaseBusiness} title="暂无岗位申请" description="候选人完成岗位投递后，这里会按最近活动展示企业、岗位和申请状态。" />}</div>
        </Card>
      </div>

      <div className="space-y-6">
        <Card>
          <div className="flex items-start justify-between gap-4"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">评测表现</p><h2 className="mt-1 text-xl font-bold">综合分趋势</h2></div><div className="text-right"><Gauge aria-hidden="true" className="ml-auto h-5 w-5 text-[var(--accent)]" /><strong className="mt-2 block text-2xl tabular-nums">{scoreText(overview.latestScore)}</strong></div></div>
          {scorePoints.length > 0 ? <svg viewBox="0 0 600 190" role="img" aria-label="候选人最近评测报告的综合得分趋势" className="mt-5 h-44 w-full">
            <path d="M40 174H560" stroke="var(--border)" strokeWidth="1" />
            {scorePoints.length > 1 && <polyline points={scorePoints.map(point => `${point.x},${point.y}`).join(' ')} fill="none" stroke="var(--accent)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />}
            {scorePoints.map((point, index) => <g key={`${point.x}-${index}`}><circle cx={point.x} cy={point.y} r="6" fill="var(--surface)" stroke="var(--accent)" strokeWidth="3" /><text x={point.x} y={point.y - 14} textAnchor="middle" className="fill-foreground text-[12px] font-bold">{scoreText(point.score)}</text></g>)}
          </svg> : <EmptyState icon={Gauge} title="暂无评测趋势" description="生成第一份评测报告后即可查看综合分变化。" compact />}
        </Card>

        <Card className="p-0">
          <SectionHeader eyebrow="评测结果" title="报告" count={overview.reportCount} />
          <div className="divide-y divide-border">{profile.reports.map(report => <Link key={report.id} to={`/admin/interviews?reportInterviewId=${report.interviewId}`} className="flex min-h-20 items-center justify-between gap-4 px-5 py-4 transition hover:bg-muted/35 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--accent)]"><div className="min-w-0"><h3 className="truncate font-bold">{report.interviewTitle}</h3><p className="mt-1 text-xs text-muted-foreground">{formatDateTime(report.scheduledAt || report.generatedAt)}</p></div><div className="shrink-0 text-right"><strong className="text-xl tabular-nums text-[var(--accent)]">{scoreText(report.totalScore)}</strong><p className="mt-1 text-[11px] text-muted-foreground">综合分</p></div></Link>)}{!profile.reports.length && <EmptyState icon={FileChartColumn} title="暂无评测报告" description="完成面试并生成报告后，可从这里快速进入报告详情。" compact />}</div>
        </Card>
      </div>
    </div>

    <Card className="p-0">
      <SectionHeader eyebrow="面试历程" title="面试记录" count={overview.interviewCount} />
      <div className="divide-y divide-border">{profile.interviews.map(interview => <Link key={interview.id} to={`/admin/interviews/${interview.id}/review`} className="grid min-h-20 gap-3 px-5 py-4 transition hover:bg-muted/35 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--accent)] sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center"><div className="min-w-0"><h3 className="truncate font-bold">{interview.title}</h3><p className="mt-1 text-xs text-muted-foreground">{interview.type || 'AI'} 面试</p></div><span className="text-sm text-muted-foreground">{formatDateTime(interview.scheduledAt)}{interview.duration ? ` · ${interview.duration} 分钟` : ''}</span><Badge className="justify-self-start sm:justify-self-end" tone={interviewStatusTone(interview.status)}>{interviewStatusText[interview.status] ?? '未知状态'}</Badge></Link>)}{!profile.interviews.length && <EmptyState icon={CalendarCheck2} title="暂无面试记录" description="创建面试后，这里会按时间展示面试状态和复盘入口。" />}</div>
    </Card>
  </div>
}

function ProfileField({ icon: Icon, label, value, verified }: { icon: typeof Phone; label: string; value: string; verified?: boolean }) {
  return <div className="min-w-0 border-b border-border p-5 last:border-b-0 sm:border-b-0 sm:border-r sm:last:border-r-0"><div className="flex items-center gap-2 text-xs font-semibold text-muted-foreground"><Icon aria-hidden="true" className="h-4 w-4 text-[var(--accent)]" />{label}{verified !== undefined && <span className={verified ? 'text-emerald-700' : 'text-amber-700'}>{verified ? '已验证' : '未验证'}</span>}</div><strong className="mt-2 block break-all text-sm">{value}</strong></div>
}

function JourneyMetric({ icon: Icon, label, value, detail }: { icon: typeof FileText; label: string; value: number; detail: string }) {
  return <div className="border-b border-border p-5 last:border-b-0 sm:[&:nth-child(odd)]:border-r xl:border-b-0 xl:border-r xl:last:border-r-0"><div className="flex items-center gap-2 text-sm font-semibold text-muted-foreground"><Icon aria-hidden="true" className="h-4 w-4 text-[var(--accent)]" />{label}</div><strong className="mt-3 block text-3xl tabular-nums">{value}</strong><p className="mt-1 text-xs text-muted-foreground">{detail}</p></div>
}

function SectionHeader({ eyebrow, title, count }: { eyebrow: string; title: string; count: number }) {
  return <div className="flex items-center justify-between gap-4 border-b border-border p-5"><div><p className="text-xs font-semibold uppercase tracking-[.14em] text-muted-foreground">{eyebrow}</p><h2 className="mt-1 text-xl font-bold">{title}</h2></div><Badge>{count} 项</Badge></div>
}

function EmptyState({ icon: Icon, title, description, compact = false }: { icon: typeof FileText; title: string; description: string; compact?: boolean }) {
  return <div className={compact ? 'py-8 text-center' : 'px-5 py-10 text-center'}><span className="mx-auto grid h-11 w-11 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><Icon aria-hidden="true" className="h-5 w-5" /></span><h3 className="mt-3 font-bold">{title}</h3><p className="mx-auto mt-1 max-w-md text-sm leading-6 text-muted-foreground">{description}</p></div>
}

function CandidateDetailLoading() {
  return <div className="space-y-6" aria-live="polite" aria-busy="true"><div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 aria-hidden="true" className="h-4 w-4 animate-spin" />正在加载候选人最新资料…</div><div className="h-64 animate-pulse rounded-[30px] bg-muted" /><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{Array.from({ length: 4 }).map((_, index) => <div key={index} className="h-32 animate-pulse rounded-[24px] bg-muted" />)}</div><div className="grid gap-6 xl:grid-cols-2"><div className="h-80 animate-pulse rounded-[30px] bg-muted" /><div className="h-80 animate-pulse rounded-[30px] bg-muted" /></div></div>
}
