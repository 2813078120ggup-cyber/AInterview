import { FileChartColumn, NotebookPen, RefreshCw } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'

import { ReportDetailView, type ReportDetailData } from '@/components/report-detail-view'
import { Button } from '@/components/ui/button'
import { request } from '@/lib/api'
import { exportReportPdf } from '@/lib/report-export'

function ReportLoadingSkeleton() {
  return <div className="mx-auto max-w-6xl space-y-6 px-4 py-5 sm:px-6 sm:py-6" role="status" aria-label="评测报告加载中">
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="space-y-3"><div className="h-4 w-24 motion-safe:animate-pulse rounded bg-muted" /><div className="h-10 w-56 motion-safe:animate-pulse rounded bg-muted" /><div className="h-4 w-72 max-w-full motion-safe:animate-pulse rounded bg-muted" /></div>
      <div className="h-11 w-36 motion-safe:animate-pulse rounded-full bg-muted" />
    </div>
    <div className="min-h-56 motion-safe:animate-pulse rounded-[28px] bg-muted" />
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {Array.from({ length: 4 }, (_, index) => <div key={index} className="h-40 motion-safe:animate-pulse rounded-[24px] bg-muted" />)}
    </div>
    <span className="sr-only">正在加载评测报告，请稍候。</span>
  </div>
}

export function CandidateReport() {
  const { id = '' } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [report, setReport] = useState<ReportDetailData>()
  const [loading, setLoading] = useState(true)
  const [retrying, setRetrying] = useState(false)
  const [error, setError] = useState('')
  const autoPrint = searchParams.get('print') === '1'

  const load = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true)
      setReport(undefined)
    }
    try {
      setReport(await request<ReportDetailData>(`/v1/interviews/${id}/report`))
      setRetrying(false)
      setError('')
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : '评测报告尚未生成。'
      setRetrying(message.includes('尚未生成') || message.includes('生成中'))
      setError(message)
    } finally {
      if (!silent) setLoading(false)
    }
  }, [id])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!retrying || report) return
    const timer = window.setTimeout(() => void load(true), 5000)
    return () => window.clearTimeout(timer)
  }, [load, report, retrying])

  useEffect(() => {
    if (!report || !autoPrint) return
    const timer = window.setTimeout(() => {
      exportReportPdf(`AInterview-${id}-候选人评测报告`)
    }, 260)
    return () => window.clearTimeout(timer)
  }, [autoPrint, id, report])

  if (loading) return <ReportLoadingSkeleton />

  if (!report) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-12 text-center sm:py-16">
        <span className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]">
          <FileChartColumn aria-hidden="true" />
        </span>
        <h1 className="mt-5 text-2xl font-bold">{retrying ? '评测报告生成中' : '暂时无法查看评测报告'}</h1>
        <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-muted-foreground">
          {retrying ? '系统正在评估作答并整理优势、改进项与行动建议，本页将自动刷新。' : '报告可能尚未发布，或当前暂时无法读取。你可以稍后重试，或返回面试大厅查看其他记录。'}
        </p>
        {error && <p className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800" role={retrying ? 'status' : 'alert'}>{error}</p>}
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <Button type="button" variant="secondary" onClick={() => navigate('/candidate/interviews')}>返回面试大厅</Button>
          <Button type="button" variant="secondary" onClick={() => navigate(`/candidate/reflections?interviewId=${id}`)}>
            <NotebookPen className="h-4 w-4" aria-hidden="true" />记录心得
          </Button>
          <Button type="button" aria-busy={loading} onClick={() => void load()}><RefreshCw className="h-4 w-4" aria-hidden="true" />刷新状态</Button>
        </div>
      </div>
    )
  }

  return (
    <ReportDetailView
      report={report}
      title="面试评测报告"
      heading="面试评测报告"
      meta={`报告编号：${id}`}
      exportTitle={`AInterview-${id}-候选人评测报告`}
      backLabel="返回面试大厅"
      onBack={() => navigate('/candidate/interviews')}
      trainingPlanEndpoint={`/v1/interviews/${id}/report/training-plan`}
      extraActions={<>
        <Button type="button" variant="secondary" onClick={() => navigate(`/candidate/reflections?interviewId=${id}`)}>
          <NotebookPen className="h-4 w-4" aria-hidden="true" />记录心得
        </Button>
        <Button type="button" variant="secondary" onClick={() => navigate('/candidate/reports')}>能力趋势</Button>
      </>}
    />
  )
}
