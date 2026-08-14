import { Bell, CheckCircle2, Eye, FileChartColumn, MoreHorizontal, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AdminRowActionButton, AdminRowActionLink, AdminRowActions } from '@/components/admin/admin-row-actions'
import { Badge } from '@/components/ui/badge'
import { canViewReport, INTERVIEW_STATUS, interviewStatusText, interviewStatusTone, isReportPending } from '@/lib/interview-status'
import type { Candidate, InterviewRow, ReportItem } from './admin-interviews-api'

const dateText = (value?: string) => value?.replace('T', ' ').slice(0, 16) || '-'

type ActionTarget = { type: 'pass' | 'delete'; interview: InterviewRow }

type Props = {
  items: InterviewRow[]
  reports: Map<string, ReportItem>
  candidates: Map<string, Candidate>
  loading: boolean
  onNotice: (interview: InterviewRow) => void
  onAction: (target: ActionTarget) => void
  onReport: (report: ReportItem) => void
}

export function AdminInterviewList({ items, reports, candidates, loading, onNotice, onAction, onReport }: Props) {
  const [openActions, setOpenActions] = useState<string>()

  return (
    <div>
      <table className="mobile-card-table table-fixed text-left text-sm">
        <colgroup>
          <col className="w-[25%]" /><col className="w-[14%]" /><col className="w-[12%]" />
          <col className="w-[9%]" /><col className="w-[14%]" /><col className="w-[26%]" />
        </colgroup>
        <thead className="border-b border-border bg-muted/40 text-xs uppercase tracking-wide text-muted-foreground">
          <tr>
            <th className="px-5 py-4">面试主题</th><th className="px-5 py-4">候选人</th><th className="px-5 py-4">预约时间</th>
            <th className="px-5 py-4">状态</th><th className="px-5 py-4">报告</th><th className="px-5 py-4 text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          {loading ? <tr><td data-mobile-full className="px-5 py-12 text-center text-muted-foreground" colSpan={6}>正在加载面试数据…</td></tr>
            : items.length ? items.map(item => {
              const person = candidates.get(String(item.candidateId))
              const report = reports.get(String(item.id))
              return <tr key={item.id} className="border-b border-border/70 last:border-0 hover:bg-muted/30">
                <td data-label="面试主题" className="break-words px-5 py-5 font-semibold">{item.title}</td>
                <td data-label="候选人" className="px-5 py-5">
                  {person ? <Link to={`/admin/candidates/${person.id}`} className="rounded font-medium hover:text-[var(--accent)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]">{person.realName}</Link> : <span className="font-medium">候选人 #{item.candidateId}</span>}
                  <p className="mt-1 text-xs text-muted-foreground">{person?.username}</p>
                </td>
                <td data-label="预约时间" className="px-5 py-5 text-muted-foreground">{dateText(item.scheduledAt)}</td>
                <td data-label="状态" className="px-5 py-5"><Badge className="shrink-0" tone={interviewStatusTone(item.status)}>{interviewStatusText[item.status] ?? '未知状态'}</Badge></td>
                <td data-label="报告" className="px-5 py-5">
                  {report ? <Badge className="shrink-0" tone="success">已生成 · {report.totalScore} 分</Badge> : isReportPending(item.status) ? <Badge className="shrink-0" tone="warning">生成中</Badge> : <span className="whitespace-nowrap text-xs text-muted-foreground">面试结束后生成</span>}
                </td>
                <td data-label="操作" className="relative px-5 py-5 align-middle">
                  <AdminRowActions>
                    <AdminRowActionLink to={`/admin/interviews/${item.id}/review`} icon={Eye} />
                    <div className="relative">
                      <AdminRowActionButton label="更多" icon={MoreHorizontal} className="min-w-[88px]" onClick={() => setOpenActions(current => current === String(item.id) ? undefined : String(item.id))} />
                      {openActions === String(item.id) && <>
                        <button className="fixed inset-0 z-20 cursor-default" aria-label="关闭更多操作菜单" onClick={() => setOpenActions(undefined)} />
                        <div className="absolute right-0 top-11 z-30 w-40 overflow-hidden rounded-2xl border border-border bg-surface p-1.5 text-sm shadow-2xl">
                          <button className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left transition hover:bg-muted" onClick={() => { setOpenActions(undefined); onNotice(item) }}><Bell className="h-4 w-4" />发送通知</button>
                          {([INTERVIEW_STATUS.COMPLETED, INTERVIEW_STATUS.REPORT_READY, INTERVIEW_STATUS.FAILED] as number[]).includes(item.status) && <button className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left transition hover:bg-muted" onClick={() => { setOpenActions(undefined); onAction({ type: 'pass', interview: item }) }}><CheckCircle2 className="h-4 w-4" />标记通过</button>}
                          {report && canViewReport(item.status) && <button className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left transition hover:bg-muted" onClick={() => { setOpenActions(undefined); onReport(report) }}><FileChartColumn className="h-4 w-4" />查看报告</button>}
                          <button className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-rose-600 transition hover:bg-rose-50 dark:text-rose-200 dark:hover:bg-rose-400/10" onClick={() => { setOpenActions(undefined); onAction({ type: 'delete', interview: item }) }}><Trash2 className="h-4 w-4" />删除面试</button>
                        </div>
                      </>}
                    </div>
                  </AdminRowActions>
                </td>
              </tr>
            }) : <tr><td data-mobile-full colSpan={6} className="px-5 py-12 text-center text-muted-foreground">暂无符合条件的面试</td></tr>}
        </tbody>
      </table>
    </div>
  )
}
