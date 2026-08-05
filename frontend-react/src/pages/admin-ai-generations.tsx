import { Activity, AlertCircle, CheckCircle2, Clock3, RefreshCw, Search, Timer, Zap } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'

type GenerationRecord = {
  id: string
  requestId: string
  taskId?: string
  interviewId?: string
  freeInterviewSessionId?: string
  generationType: string
  promptCode?: string
  promptVersion?: number
  provider: string
  model: string
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'
  latencyMs?: number
  inputChars: number
  outputChars: number
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  httpStatus?: number
  errorType?: string
  errorMessage?: string
  createdBy?: string
  startedAt: string
  finishedAt?: string
}

type PageResult<T> = { records: T[]; total: number; pageNo: number; pageSize: number }
type Summary = { total: number; success: number; failed: number; running: number; averageLatencyMs: number; totalTokens: number }

const generationNames: Record<string, string> = {
  OPENING: '模拟面试开场',
  FOLLOW_UP: '模拟面试追问',
  ANSWER_EVALUATION: '逐题评分',
  SIMULATION_REPORT: '模拟面试报告',
  RESUME_ANALYSIS: '简历分析',
  FREE_FOLLOW_UP: '自由面试追问',
  FREE_REPORT: '自由面试报告',
  TRAINING_PLAN: '提升计划',
  COACH_CHAT: 'AI 面试教练',
}

const generationTypes = Object.keys(generationNames)

function dateText(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

function durationText(value?: number) {
  if (value == null) return '-'
  return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} s`
}

function numberText(value?: number) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

export function AdminAiGenerations() {
  const [page, setPage] = useState<PageResult<GenerationRecord>>({ records: [], total: 0, pageNo: 1, pageSize: 20 })
  const [summary, setSummary] = useState<Summary>({ total: 0, success: 0, failed: 0, running: 0, averageLatencyMs: 0, totalTokens: 0 })
  const [status, setStatus] = useState('')
  const [generationType, setGenerationType] = useState('')
  const [promptCode, setPromptCode] = useState('')
  const [keyword, setKeyword] = useState('')
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const query = useMemo(() => {
    const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: '20' })
    if (status) params.set('status', status)
    if (generationType) params.set('generationType', generationType)
    if (promptCode.trim()) params.set('promptCode', promptCode.trim())
    if (keyword.trim()) params.set('keyword', keyword.trim())
    return params.toString()
  }, [generationType, keyword, pageNo, promptCode, status])

  useEffect(() => { void load() }, [query])

  async function load() {
    setLoading(true)
    setError('')
    try {
      const summaryQuery = query.replace(/(^|&)pageNo=[^&]*/g, '').replace(/(^|&)pageSize=[^&]*/g, '').replace(/^&/, '')
      const [records, totals] = await Promise.all([
        request<PageResult<GenerationRecord>>(`/v1/admin/ai-generations?${query}`),
        request<Summary>(`/v1/admin/ai-generations/summary${summaryQuery ? `?${summaryQuery}` : ''}`),
      ])
      setPage(records)
      setSummary(totals)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'AI 调用记录加载失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }

  function resetFilters() {
    setStatus('')
    setGenerationType('')
    setPromptCode('')
    setKeyword('')
    setPageNo(1)
  }

  const totalPages = Math.max(1, Math.ceil(page.total / page.pageSize))
  const successRate = summary.total ? Math.round(summary.success * 1000 / summary.total) / 10 : 0

  return <div className="mx-auto max-w-[1500px] p-4 sm:p-6 lg:p-10">
    <header className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
      <div>
        <p className="text-sm font-semibold text-[var(--accent)]">AI 调用监控</p>
        <h1 className="mt-2 text-3xl font-bold sm:text-4xl">AI 调用审计</h1>
        <p className="mt-3 max-w-3xl text-muted-foreground">查看模型、提示词版本、耗时、Token 用量与失败原因。</p>
      </div>
      <Button type="button" variant="secondary" className="h-9 px-4" onClick={() => void load()} disabled={loading}><RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />刷新记录</Button>
    </header>

    {error && <p className="mt-6 rounded-2xl border border-rose-200 bg-rose-50 px-5 py-4 text-sm text-rose-700">{error}</p>}

    <section className="mt-7 grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
      <Card><p className="flex items-center gap-2 text-sm text-muted-foreground"><Activity className="h-4 w-4" />调用总数</p><strong className="mt-3 block text-3xl">{numberText(summary.total)}</strong></Card>
      <Card><p className="flex items-center gap-2 text-sm text-muted-foreground"><CheckCircle2 className="h-4 w-4" />成功率</p><strong className="mt-3 block text-3xl">{successRate}%</strong></Card>
      <Card><p className="flex items-center gap-2 text-sm text-muted-foreground"><AlertCircle className="h-4 w-4" />失败</p><strong className="mt-3 block text-3xl">{numberText(summary.failed)}</strong></Card>
      <Card><p className="flex items-center gap-2 text-sm text-muted-foreground"><Timer className="h-4 w-4" />平均耗时</p><strong className="mt-3 block text-3xl">{durationText(summary.averageLatencyMs)}</strong></Card>
      <Card><p className="flex items-center gap-2 text-sm text-muted-foreground"><Zap className="h-4 w-4" />总 Token</p><strong className="mt-3 block text-3xl">{numberText(summary.totalTokens)}</strong></Card>
    </section>

    <section className="mt-7 overflow-hidden rounded-[24px] border border-border bg-surface shadow-[0_18px_45px_rgba(20,18,17,.045)]">
      <div className="grid gap-3 border-b border-border p-5 lg:grid-cols-[1fr_180px_220px_260px_auto]">
        <label className="flex h-11 items-center gap-2 rounded-xl border border-border bg-background px-3">
          <Search className="h-4 w-4 text-muted-foreground" />
          <input value={keyword} onChange={event => { setKeyword(event.target.value); setPageNo(1) }} placeholder="请求 ID、模型或错误" className="min-w-0 flex-1 bg-transparent text-sm outline-none" />
        </label>
        <ResponsiveSelect
          ariaLabel="选择状态"
          value={status}
          onValueChange={next => { setStatus(next); setPageNo(1) }}
          className="w-full lg:w-auto"
          options={[
            { value: "", label: "全部状态" },
            { value: "SUCCESS", label: "成功" },
            { value: "FAILED", label: "失败" },
            { value: "RUNNING", label: "运行中" },
          ]}
        />
        <ResponsiveSelect
          ariaLabel="选择任务类型"
          value={generationType}
          onValueChange={next => { setGenerationType(next); setPageNo(1) }}
          className="w-full lg:w-auto"
          options={[{ value: "", label: "全部任务类型" }, ...generationTypes.map(type => ({ value: type, label: generationNames[type] }))]}
        />
        <input value={promptCode} onChange={event => { setPromptCode(event.target.value); setPageNo(1) }} placeholder="提示词代码（精确匹配）" className="h-11 rounded-xl border border-border bg-background px-3 text-sm outline-none" />
        <Button type="button" variant="secondary" className="h-9 w-full px-4 lg:w-auto" onClick={resetFilters}>重置</Button>
      </div>

      <div>
        <table className="mobile-card-table text-left text-sm">
          <thead className="bg-muted/50 text-xs text-muted-foreground"><tr>
            <th className="px-5 py-3 font-semibold">状态 / 类型</th><th className="px-5 py-3 font-semibold">提示词与模型</th>
            <th className="px-5 py-3 font-semibold">关联对象</th><th className="px-5 py-3 font-semibold">性能</th>
            <th className="px-5 py-3 font-semibold">Token</th><th className="px-5 py-3 font-semibold">时间 / 请求 ID</th>
          </tr></thead>
          <tbody className="divide-y divide-border">
            {page.records.map(item => <tr key={item.id} className="align-top transition hover:bg-muted/25">
              <td data-label="状态 / 类型" className="px-5 py-4"><div><Badge tone={item.status === 'SUCCESS' ? 'success' : item.status === 'FAILED' ? 'danger' : 'warning'}>{item.status === 'SUCCESS' ? '成功' : item.status === 'FAILED' ? '失败' : '运行中'}</Badge><p className="mt-2 font-semibold">{generationNames[item.generationType] ?? item.generationType}</p>{item.httpStatus && <p className="mt-1 text-xs text-muted-foreground">HTTP {item.httpStatus}</p>}</div></td>
              <td data-label="提示词与模型" className="px-5 py-4"><div className="max-w-full overflow-hidden"><p className="break-all font-mono text-xs">{item.promptCode ?? '未绑定模板'}{item.promptVersion != null ? ` · v${item.promptVersion}` : ''}</p><p className="mt-2 break-words text-muted-foreground">{item.provider} / {item.model}</p>{item.errorMessage && <p className="mt-2 max-w-sm break-words text-xs leading-5 text-rose-600" title={item.errorMessage}>{item.errorType}: {item.errorMessage}</p>}</div></td>
              <td data-label="关联对象" className="px-5 py-4 text-xs leading-6 text-muted-foreground"><div className="break-all"><p>任务：{item.taskId ?? '-'}</p><p>面试：{item.interviewId ?? '-'}</p><p>自由会话：{item.freeInterviewSessionId ?? '-'}</p></div></td>
              <td data-label="性能" className="px-5 py-4"><div><p className="inline-flex items-center gap-1.5 font-semibold"><Clock3 className="h-4 w-4" />{durationText(item.latencyMs)}</p><p className="mt-2 text-xs text-muted-foreground">输入 {numberText(item.inputChars)} 字 / 输出 {numberText(item.outputChars)} 字</p></div></td>
              <td data-label="Token" className="px-5 py-4"><div><strong>{item.totalTokens == null ? '-' : numberText(item.totalTokens)}</strong><p className="mt-2 text-xs text-muted-foreground">提示 {numberText(item.promptTokens)} / 输出 {numberText(item.completionTokens)}</p></div></td>
              <td data-label="时间 / 请求 ID" className="px-5 py-4"><div className="max-w-full overflow-hidden"><p>{dateText(item.startedAt)}</p><p className="mt-2 max-w-52 truncate font-mono text-xs text-muted-foreground" title={item.requestId}>{item.requestId}</p></div></td>
            </tr>)}
          </tbody>
        </table>
      </div>
      {!loading && !page.records.length && <p className="p-12 text-center text-sm text-muted-foreground">暂无符合当前条件的 AI 调用记录。</p>}
      {loading && <p className="p-12 text-center text-sm text-muted-foreground">正在加载 AI 调用记录…</p>}

      <div className="flex flex-col gap-3 border-t border-border px-4 py-4 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between sm:px-5">
        <span>共 {numberText(page.total)} 条 · 第 {page.pageNo}/{totalPages} 页</span>
        <div className="grid grid-cols-2 gap-2 sm:flex"><Button variant="secondary" className="h-11 sm:h-9" disabled={pageNo <= 1 || loading} onClick={() => setPageNo(value => value - 1)}>上一页</Button><Button variant="secondary" className="h-11 sm:h-9" disabled={pageNo >= totalPages || loading} onClick={() => setPageNo(value => value + 1)}>下一页</Button></div>
      </div>
    </section>
  </div>
}
