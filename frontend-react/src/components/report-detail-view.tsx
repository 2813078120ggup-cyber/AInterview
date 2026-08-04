import { AlertTriangle, ArrowLeft, BarChart3, CalendarDays, CheckCircle2, Download, ListChecks, Target, TrendingUp, X } from 'lucide-react'
import { useState, type ReactNode } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request, type TrainingPlan } from '@/lib/api'
import { exportReportPdf } from '@/lib/report-export'

export type ReportDetailData = {
  totalScore: number
  professionalScore: number
  expressionScore: number
  logicScore: number
  adaptabilityScore: number
  summary: string
  strengths: string
  weaknesses: string
  improvementSuggestions: string
  status: number
  questionCount?: number
  reliabilityWarning?: string
}

const dimensions: Array<[keyof ReportDetailData, string, string]> = [
  ['professionalScore', '专业能力', '核心知识、岗位技能与方案完整度'],
  ['expressionScore', '表达能力', '结构化表达、沟通清晰度与说服力'],
  ['logicScore', '逻辑思维', '问题拆解、推理链路与边界意识'],
  ['adaptabilityScore', '应变能力', '追问场景下的临场反应与调整能力'],
]

function scoreLevel(score: number) {
  if (score >= 90) return '优秀'
  if (score >= 80) return '良好'
  if (score >= 70) return '达标'
  if (score >= 60) return '待加强'
  return '需重点提升'
}

function overallAssessment(score: number) {
  if (score >= 60) return '表现达到岗位基础要求'
  if (score >= 40) return '表现尚未达到岗位基础要求'
  return '表现明显未达到岗位基础要求'
}

type ReportDetailViewProps = {
  report: ReportDetailData
  title: string
  eyebrow?: string
  heading?: string
  meta?: string
  exportTitle: string
  backLabel?: string
  onBack?: () => void
  onClose?: () => void
  onExport?: () => void
  extraActions?: ReactNode
  trainingPlanEndpoint?: string
}

export function ReportDetailView({
  report,
  title,
  eyebrow = '面试评测',
  heading = '面试评测报告',
  meta,
  exportTitle,
  backLabel,
  onBack,
  onClose,
  onExport,
  extraActions,
  trainingPlanEndpoint,
}: ReportDetailViewProps) {
  const scores = dimensions.map(([key]) => Number(report[key]))
  const average = Math.round(scores.reduce((sum, value) => sum + value, 0) / scores.length)
  const assessment = overallAssessment(Number(report.totalScore))
  const [plan, setPlan] = useState<TrainingPlan>()
  const [planOpen, setPlanOpen] = useState(false)
  const [planLoading, setPlanLoading] = useState(false)
  const [planError, setPlanError] = useState('')

  async function generateTrainingPlan() {
    if (!trainingPlanEndpoint) return
    setPlanOpen(true)
    if (plan) return
    setPlanLoading(true)
    setPlanError('')
    try {
      setPlan(await request<TrainingPlan>(trainingPlanEndpoint, { method: 'POST' }))
    } catch (reason) {
      setPlanError(reason instanceof Error ? reason.message : '提升计划生成失败，请稍后重试。')
    } finally {
      setPlanLoading(false)
    }
  }

  return (
    <div data-print-root className="report-print-root mx-auto max-w-6xl space-y-5 px-4 py-5 sm:space-y-6 sm:px-6 sm:py-6">
      <article className="report-paper-root print-only">
        <section className="report-paper-cover">
          <div>
            <p className="report-paper-eyebrow">AInterview 面试评测报告</p>
            <h1>{title}</h1>
            {meta && <p className="report-paper-meta">{meta}</p>}
          </div>
          <div className="report-paper-score">
            <strong>{report.totalScore}</strong>
            <span>综合得分 / 100</span>
          </div>
        </section>

        <section className="report-paper-summary">
          <div>
            <p className="report-paper-label">综合结论</p>
            <h2>综合评测：{assessment}</h2>
          </div>
          <p>{report.summary}</p>
          {report.reliabilityWarning && <p><strong>结果可信度提示：</strong>{report.reliabilityWarning}</p>}
        </section>

        <section className="report-paper-section">
          <div className="report-paper-section-title">
            <p>能力评分</p>
            <span>按四项核心能力维度统计</span>
          </div>
          <table className="report-paper-score-table">
            <thead>
              <tr>
                <th>维度</th>
                <th>得分</th>
                <th>等级</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>
              {dimensions.map(([key, label, note]) => (
                <tr key={key}>
                  <td>{label}</td>
                  <td><strong>{report[key]}</strong></td>
                  <td>{scoreLevel(Number(report[key]))}</td>
                  <td>{note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="report-paper-section">
          <div className="report-paper-section-title">
            <p>能力画像</p>
            <span>平均分 {average}</span>
          </div>
          <div className="report-paper-bars">
            {dimensions.map(([key, label]) => (
              <div className="report-paper-bar" key={key}>
                <div>
                  <span>{label}</span>
                  <strong>{report[key]}</strong>
                </div>
                <i><b style={{ width: `${report[key]}%` }} /></i>
              </div>
            ))}
          </div>
        </section>

        <section className="report-paper-takeaways">
          <article>
            <h3>优势表现</h3>
            <p>{report.strengths}</p>
          </article>
          <article>
            <h3>待提升项</h3>
            <p>{report.weaknesses}</p>
          </article>
          <article>
            <h3>行动建议</h3>
            <p>{report.improvementSuggestions}</p>
          </article>
        </section>

        <footer className="report-paper-footer">
          <span>AInterview AI 多模态智能模拟面试评测平台</span>
          <span>本报告由系统自动生成，仅用于学习评估与面试复盘。</span>
        </footer>
      </article>

      <div className="report-screen-root space-y-6">
      <header className="no-print flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          {onBack && (
            <button className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground" onClick={onBack}>
              <ArrowLeft className="h-4 w-4" />
              {backLabel || '返回'}
            </button>
          )}
          <p className="text-sm font-semibold text-[var(--accent)]">{eyebrow}</p>
          <h1 className="mt-1 text-3xl font-bold">{heading}</h1>
          {meta && <p className="mt-2 text-sm text-muted-foreground">{meta}</p>}
        </div>
        <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
          {extraActions}
          {trainingPlanEndpoint && (
            <Button variant="secondary" onClick={() => void generateTrainingPlan()} disabled={planLoading}>
              <ListChecks className="h-4 w-4" />
              {planLoading ? '正在生成…' : plan ? '查看提升计划' : '生成提升计划'}
            </Button>
          )}
          <Button variant="secondary" onClick={() => onExport ? onExport() : exportReportPdf(exportTitle)}>
            <Download className="h-4 w-4" />
            导出 PDF
          </Button>
          {onClose && (
            <button onClick={onClose} className="grid h-10 w-10 place-items-center rounded-full border border-border bg-surface hover:bg-muted" aria-label="关闭报告">
              <X className="h-5 w-5" />
            </button>
          )}
        </div>
      </header>

      {report.reliabilityWarning && (
        <div className="no-print flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-5 py-4 text-amber-950" role="status">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-700" />
          <div>
            <p className="font-semibold">结果可信度提示</p>
            <p className="mt-1 text-sm leading-6">{report.reliabilityWarning}</p>
          </div>
        </div>
      )}

      <section className="report-print-hero soft-emphasis-panel print-section overflow-hidden rounded-[28px] px-6 py-7 shadow-xl sm:px-9">
        <div className="grid gap-7 md:grid-cols-[1fr_auto] md:items-center">
          <div>
            <Badge tone={report.status === 1 ? 'success' : 'warning'}>{report.status === 1 ? '报告已生成' : '报告草稿'}</Badge>
            <h2 className="mt-5 text-2xl font-bold">综合评测：{assessment}</h2>
            <p className="mt-3 max-w-2xl leading-7 text-white/85">{report.summary}</p>
          </div>
          <div className="mx-auto grid h-36 w-36 place-items-center rounded-full border-8 border-[var(--border)]/40 bg-white/10 text-center shadow-[0_0_0_12px_rgba(255,255,255,.05)] sm:h-40 sm:w-40 md:mx-0">
            <div>
              <strong className="text-5xl tracking-tight">{report.totalScore}</strong>
              <span className="mt-1 block text-xs text-white/75">综合得分 / 100</span>
            </div>
          </div>
        </div>
      </section>

      <div className="report-print-score-grid grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {dimensions.map(([key, label, note]) => (
          <Card key={key} className="print-card">
            <p className="text-sm text-muted-foreground">{label}</p>
            <div className="mt-3 flex items-end justify-between">
              <strong className="text-3xl">{report[key]}</strong>
              <span className="text-xs text-muted-foreground">/ 100</span>
            </div>
            <div className="mt-4 h-2 overflow-hidden rounded-full bg-muted">
              <div className="h-full rounded-full bg-gradient-to-r from-[var(--accent)] to-[var(--brand-pink)]" style={{ width: `${report[key]}%` }} />
            </div>
            <p className="mt-3 text-xs text-muted-foreground">{note}</p>
          </Card>
        ))}
      </div>

      <div className="report-print-detail-grid grid gap-6 lg:grid-cols-[.9fr_1.1fr]">
        <Card className="report-print-profile print-card">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-semibold text-[var(--accent)]">能力画像</p>
              <h2 className="mt-1 text-xl font-bold">能力分布</h2>
            </div>
            <span className="inline-flex items-center gap-1 text-sm text-muted-foreground">
              <BarChart3 className="h-4 w-4" />
              平均 {average}
            </span>
          </div>
          <div className="mt-8 space-y-5">
            {dimensions.map(([key, label]) => (
              <div key={key}>
                <div className="mb-2 flex justify-between text-sm">
                  <span>{label}</span>
                  <strong>{report[key]}</strong>
                </div>
                <div className="h-3 overflow-hidden rounded-full bg-muted">
                  <div className="h-full rounded-full bg-gradient-to-r from-[var(--primary)] to-[var(--brand-pink)]" style={{ width: `${report[key]}%` }} />
                </div>
              </div>
            ))}
          </div>
        </Card>

        <Card className="report-print-takeaways print-card">
          <p className="text-sm font-semibold text-[var(--accent)]">评测摘要</p>
          <h2 className="mt-1 text-xl font-bold">下一步提升重点</h2>
          <div className="mt-6 space-y-4">
            <article className="rounded-2xl border border-[var(--border)] bg-[var(--accent-soft)]/70 p-4">
              <h3 className="flex items-center gap-2 font-semibold text-[var(--accent)]">
                <CheckCircle2 className="h-4 w-4" />
                优势分析
              </h3>
              <p className="mt-2 text-sm leading-6 text-[var(--foreground)]/80">{report.strengths}</p>
            </article>
            <article className="rounded-2xl border border-amber-100 bg-amber-50/70 p-4">
              <h3 className="font-semibold text-amber-800">可提升项</h3>
              <p className="mt-2 text-sm leading-6 text-amber-950/80">{report.weaknesses}</p>
            </article>
            <article className="rounded-2xl border border-[var(--border)] bg-[var(--accent-soft)]/70 p-4">
              <h3 className="flex items-center gap-2 font-semibold text-[var(--accent)]">
                <TrendingUp className="h-4 w-4" />
                行动建议
              </h3>
              <p className="mt-2 text-sm leading-6 text-[var(--foreground)]/80">{report.improvementSuggestions}</p>
            </article>
          </div>
        </Card>
      </div>

      {planOpen && trainingPlanEndpoint && (
        <Card className="no-print overflow-hidden">
          <div className="flex flex-col gap-3 border-b border-border pb-5 md:flex-row md:items-start md:justify-between">
            <div>
              <p className="text-sm font-semibold text-[var(--accent)]">专项提升计划</p>
              <h2 className="mt-1 text-2xl font-bold">7 天提升计划</h2>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">根据能力短板生成专项训练安排。</p>
            </div>
            <Button variant="secondary" onClick={() => setPlanOpen(false)}>收起计划</Button>
          </div>
          {planLoading && <p className="py-8 text-sm text-muted-foreground">正在根据评测结果生成提升计划…</p>}
          {planError && <p className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{planError}</p>}
          {plan && (
            <div className="mt-6 space-y-6">
              <div className="rounded-[24px] border border-[var(--border)] bg-[linear-gradient(135deg,var(--accent-soft),var(--surface))] p-5">
                <div className="flex items-start gap-3">
                  <span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-[var(--surface)] text-[var(--accent)] shadow-sm">
                    <Target className="h-5 w-5" />
                  </span>
                  <div>
                    <p className="text-xs font-semibold tracking-[.12em] text-[var(--accent)]">{plan.generationMethod === 'ai' ? 'AI 生成' : '规则生成'}</p>
                    <h3 className="mt-2 text-lg font-bold">{plan.priority}</h3>
                    <p className="mt-2 text-sm text-muted-foreground">训练周期：{plan.durationDays || 7} 天</p>
                  </div>
                </div>
              </div>
              <div className="grid gap-4 lg:grid-cols-[1fr_1.25fr]">
                <div className="space-y-4">
                  <article className="rounded-[22px] border border-border p-4">
                    <h3 className="font-bold">训练重点</h3>
                    <div className="mt-3 flex flex-wrap gap-2">
                      {plan.focusAreas.map(item => <Badge key={item} tone="info">{item}</Badge>)}
                    </div>
                  </article>
                  <article className="rounded-[22px] border border-border p-4">
                    <h3 className="font-bold">推荐题库 / 方向</h3>
                    <ul className="mt-3 space-y-2 text-sm text-muted-foreground">
                      {plan.recommendedBanks.map(item => <li key={item}>• {item}</li>)}
                    </ul>
                  </article>
                  <article className="rounded-[22px] border border-border p-4">
                    <h3 className="font-bold">达成标准</h3>
                    <ul className="mt-3 space-y-2 text-sm text-muted-foreground">
                      {plan.successCriteria.map(item => <li key={item}>• {item}</li>)}
                    </ul>
                  </article>
                </div>
                <div className="rounded-[24px] border border-border p-4">
                  <h3 className="flex items-center gap-2 font-bold"><CalendarDays className="h-4 w-4" />每日训练安排</h3>
                  <div className="mt-4 space-y-3">
                    {plan.dailyPlan.map(day => (
                      <article key={`${day.day}-${day.title}`} className="rounded-2xl bg-muted/50 p-4">
                        <p className="text-xs font-semibold text-[var(--accent)]">第 {day.day} 天</p>
                        <h4 className="mt-1 font-bold">{day.title}</h4>
                        <ul className="mt-2 space-y-1 text-sm leading-6 text-muted-foreground">
                          {day.tasks.map(task => <li key={task}>• {task}</li>)}
                        </ul>
                      </article>
                    ))}
                  </div>
                </div>
              </div>
              <article className="rounded-[22px] border border-border p-4">
                <h3 className="font-bold">推荐模拟方式</h3>
                <div className="mt-3 grid gap-2 md:grid-cols-3">
                  {plan.interviewDrills.map(item => <div key={item} className="rounded-2xl bg-muted px-4 py-3 text-sm">{item}</div>)}
                </div>
              </article>
            </div>
          )}
        </Card>
      )}
      </div>
    </div>
  )
}
