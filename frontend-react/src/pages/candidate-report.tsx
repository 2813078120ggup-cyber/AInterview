import { FileChartColumn, NotebookPen, RefreshCw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'

import { ReportDetailView, type ReportDetailData } from '@/components/report-detail-view'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'
import { exportReportPdf } from '@/lib/report-export'

export function CandidateReport() {
  const { id = '' } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [report, setReport] = useState<ReportDetailData>()
  const [loading, setLoading] = useState(true)
  const [retrying, setRetrying] = useState(false)
  const [error, setError] = useState('')
  const autoPrint = searchParams.get('print') === '1'

  async function load(silent = false) {
    if (!silent) setLoading(true)
    try {
      setReport(await request<ReportDetailData>(`/v1/interviews/${id}/report`))
      setRetrying(false)
      setError('')
    } catch (reason) {
      setRetrying(true)
      setError(reason instanceof Error ? reason.message : '评测报告尚未生成。')
    } finally {
      if (!silent) setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [id])

  useEffect(() => {
    if (!retrying || report) return
    const timer = window.setTimeout(() => void load(true), 5000)
    return () => window.clearTimeout(timer)
  }, [retrying, report])

  useEffect(() => {
    if (!report || !autoPrint) return
    const timer = window.setTimeout(() => {
      exportReportPdf(`AInterview-${id}-候选人评测报告`)
    }, 260)
    return () => window.clearTimeout(timer)
  }, [autoPrint, id, report])

  if (loading) return <Card>正在加载评测报告…</Card>

  if (!report) {
    return (
      <div className="mx-auto max-w-2xl py-16 text-center">
        <span className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]">
          <FileChartColumn />
        </span>
        <h1 className="mt-5 text-2xl font-bold">评测报告生成中</h1>
        <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-muted-foreground">
          系统正在评估作答并整理优势、改进项与行动建议。本页将自动刷新。
        </p>
        {error && <p className="mt-4 text-sm text-amber-700">{error}</p>}
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <Button variant="secondary" onClick={() => navigate('/candidate/interviews')}>返回面试大厅</Button>
          <Button variant="secondary" onClick={() => navigate(`/candidate/reflections?interviewId=${id}`)}>
            <NotebookPen className="h-4 w-4" />记录心得
          </Button>
          <Button onClick={() => void load()}><RefreshCw className="h-4 w-4" />刷新状态</Button>
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
        <Button variant="secondary" onClick={() => navigate(`/candidate/reflections?interviewId=${id}`)}>
          <NotebookPen className="h-4 w-4" />记录心得
        </Button>
        <Button variant="secondary" onClick={() => navigate('/candidate/reports')}>能力趋势</Button>
      </>}
    />
  )
}
