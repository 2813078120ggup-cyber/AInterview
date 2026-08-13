import { RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { ReportDetailView } from '@/components/report-detail-view'
import { Button } from '@/components/ui/button'
import { adminInterviewsApi, type ReportDetail, type ReportItem } from './admin-interviews-api'

const dateText = (value?: string) => value?.replace('T', ' ').slice(0, 16) || '-'
type Props = { report: ReportItem; detail?: ReportDetail; loading: boolean; onClose: () => void; onRegenerated: () => Promise<void> }

export function AdminInterviewReportDrawer({ report, detail, loading, onClose, onRegenerated }: Props) {
  const [regenerating, setRegenerating] = useState(false)
  const [regenerateError, setRegenerateError] = useState('')

  const waitForVisible = () => {
    if (document.visibilityState === 'visible') return Promise.resolve()
    return new Promise<void>(resolve => {
      const handler = () => {
        if (document.visibilityState !== 'visible') return
        document.removeEventListener('visibilitychange', handler)
        resolve()
      }
      document.addEventListener('visibilitychange', handler)
    })
  }

  const regenerate = async () => {
    setRegenerating(true)
    setRegenerateError('')
    try {
      const task = await adminInterviewsApi.regenerateReport(report.interviewId)
      for (let attempt = 0; attempt < 90; attempt += 1) {
        await waitForVisible()
        const current = await adminInterviewsApi.aiTask(task.id)
        if (current.status === 'SUCCESS') {
          await onRegenerated()
          return
        }
        if (current.status === 'FAILED') throw new Error(current.errorMessage || '报告重新评分失败')
        await new Promise(resolve => window.setTimeout(resolve, 1000))
      }
      throw new Error('重新评分仍在处理中，请稍后刷新报告')
    } catch (reason) {
      setRegenerateError(reason instanceof Error ? reason.message : '报告重新评分失败')
    } finally {
      setRegenerating(false)
    }
  }

  return <div className="fixed inset-0 z-50 overflow-y-auto bg-[var(--primary)]/35 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-label="评测报告">
    <article className="mx-auto my-4 max-w-6xl rounded-[24px] bg-surface p-5 shadow-2xl sm:my-7 sm:rounded-[30px] sm:p-8">
      {regenerateError && <p className="mb-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{regenerateError}</p>}
      {loading || !detail ? <div className="py-20 text-center text-muted-foreground">正在加载报告详情…</div> : <ReportDetailView report={detail} title={report.interviewTitle} eyebrow={`${report.candidateName} · 面试评测`} heading={report.interviewTitle} meta={`候选人：${report.candidateName} · 面试时间：${dateText(report.scheduledAt)}`} exportTitle={`AInterview-${report.candidateName}-${report.interviewTitle}-评测报告`} onExport={() => window.open(`/candidate/interviews/${report.interviewId}/report?print=1`, '_blank', 'noopener,noreferrer')} trainingPlanEndpoint={`/v1/interviews/${report.interviewId}/report/training-plan`} extraActions={<Button variant="secondary" onClick={() => void regenerate()} disabled={regenerating}><RefreshCw className={`h-4 w-4 ${regenerating ? 'animate-spin' : ''}`} />{regenerating ? '正在重新评分…' : '按新规则重新评分'}</Button>} onClose={onClose} />}
    </article>
  </div>
}
