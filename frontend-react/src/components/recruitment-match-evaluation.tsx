import { History, Sparkles } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { formatDateTime, matchStatusMeta, type MatchEvaluation, type PageResult } from '@/lib/recruitment'

type RecruitmentMatchEvaluationProps = {
  evaluation?: MatchEvaluation
  history?: PageResult<MatchEvaluation>
  historyLoading?: boolean
  historyError?: string
}

export function RecruitmentMatchEvaluation({ evaluation, history, historyLoading = false, historyError }: RecruitmentMatchEvaluationProps) {
  if (!evaluation) return null
  const status = evaluationStatusMeta(evaluation.status)
  return <section aria-labelledby="match-evaluation-title" className="rounded-3xl border border-border bg-background p-5">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <div className="flex items-center gap-2"><Sparkles className="h-5 w-5 text-[var(--accent)]" aria-hidden="true" /><h2 id="match-evaluation-title" className="font-black">岗位匹配依据</h2></div>
        <p className="mt-1 text-xs leading-5 text-muted-foreground">最终分由规则分 60% 与 AI 证据分 40% 组成，仅用于辅助筛选。</p>
      </div>
      <Badge tone={status.tone}>{status.label}</Badge>
    </div>
    <div className="mt-5 grid gap-3 sm:grid-cols-3">
      <ScoreMetric label="最终匹配度" value={evaluation.finalScore} emphasis />
      <ScoreMetric label="规则分 · 60%" value={evaluation.ruleScore} />
      <ScoreMetric label="AI 证据分 · 40%" value={evaluation.aiScore} />
    </div>
    {evaluation.summary && <p className="mt-4 rounded-2xl bg-surface p-4 text-sm leading-6 text-muted-foreground">{evaluation.summary}</p>}
    <div className="mt-4 grid gap-4 lg:grid-cols-2">
      <EvidenceGroup label="规则命中技能" values={evaluation.ruleMatchedSkills} tone="info" />
      <EvidenceGroup label="AI 识别技能" values={evaluation.matchedSkills} tone="info" />
      <EvidenceGroup label="优势" values={evaluation.strengths} tone="success" />
      <EvidenceGroup label="待核实差距" values={[...(evaluation.gaps || []), ...(evaluation.risks || [])]} tone="warning" />
      <EvidenceGroup label="简历证据" values={evaluation.evidence} tone="default" />
    </div>
    <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-border pt-4 text-xs text-muted-foreground">
      <span>评估版本 v{evaluation.evaluationVersion}</span><span>简历版本 v{evaluation.resumeVersion}</span>
      {evaluation.confidence && <span>置信度：{evaluation.confidence}</span>}
      {evaluation.recommendation && <span className="font-bold text-[var(--accent)]">建议：{evaluation.recommendation}</span>}
    </div>
    <details className="mt-4 rounded-2xl border border-border bg-surface px-4 py-3">
      <summary className="flex min-h-8 cursor-pointer list-none items-center gap-2 text-sm font-bold [&::-webkit-details-marker]:hidden"><History className="h-4 w-4 text-muted-foreground" aria-hidden="true" />历史评估{history ? `（${history.total} 轮）` : ''}</summary>
      <div className="mt-3 space-y-2" aria-busy={historyLoading}>
        {historyLoading && <p className="text-xs text-muted-foreground">正在加载历史评估…</p>}
        {historyError && <p role="alert" className="text-xs text-rose-700 dark:text-rose-200">{historyError}</p>}
        {!historyLoading && !historyError && history?.records.map(item => <HistoryRow key={item.id} evaluation={item} />)}
        {!historyLoading && !historyError && history && !history.records.length && <p className="text-xs text-muted-foreground">暂无其他评估版本。</p>}
      </div>
    </details>
  </section>
}

function ScoreMetric({ label, value, emphasis = false }: { label: string; value?: number; emphasis?: boolean }) {
  return <div className={emphasis ? 'rounded-2xl bg-[var(--accent-soft)] p-4' : 'rounded-2xl bg-surface p-4'}><p className="text-xs text-muted-foreground">{label}</p><strong className={emphasis ? 'mt-2 block text-3xl font-black tabular-nums text-[var(--accent)]' : 'mt-2 block text-2xl font-black tabular-nums'}>{value == null ? '—' : `${value}%`}</strong></div>
}

function EvidenceGroup({ label, values, tone }: { label: string; values?: string[]; tone: 'default' | 'success' | 'warning' | 'info' }) {
  if (!values?.length) return null
  return <div><p className="text-xs font-bold text-muted-foreground">{label}</p><div className="mt-2 flex flex-wrap gap-2">{values.slice(0, 8).map(value => <Badge key={`${label}-${value}`} tone={tone}>{value}</Badge>)}</div></div>
}

function HistoryRow({ evaluation }: { evaluation: MatchEvaluation }) {
  const status = evaluationStatusMeta(evaluation.status)
  return <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-background px-3 py-2 text-xs"><span className="font-bold">v{evaluation.evaluationVersion} · {formatDateTime(evaluation.createdAt)}</span><span className="flex items-center gap-2"><Badge tone={status.tone}>{status.label}</Badge><strong className="tabular-nums text-[var(--accent)]">{evaluation.finalScore == null ? '—' : `${evaluation.finalScore}%`}</strong></span></div>
}

function evaluationStatusMeta(status?: string) {
  if (status === 'SUCCESS') return { label: '评估完成', tone: 'success' as const }
  if (status === 'PROCESSING') return { label: '评估中', tone: 'info' as const }
  if (status === 'FAILED') return { label: '评估失败', tone: 'danger' as const }
  return matchStatusMeta.MANUAL
}
