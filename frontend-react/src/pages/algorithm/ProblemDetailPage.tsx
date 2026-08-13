import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Bookmark, ClipboardList, Loader2, Play, RefreshCw, Send, Star, Terminal } from 'lucide-react'
import Markdown from 'react-markdown'
import { AlgorithmPageHeader } from '@/components/algorithm/algorithm-page'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { LazyCodeEditor } from '@/components/algorithm/LazyCodeEditor'
import { algorithmApi, type AlgorithmProblemDetail, type AlgorithmRunResponse, type AlgorithmSubmissionDetail, type AlgorithmSubmissionItem } from '@/lib/algorithm-api'
import { algorithmDifficultyMeta, algorithmStatusLabel, algorithmStatusTone, algorithmTerminalStatuses, difficultyLabel } from '@/lib/algorithm-status'
import { useTheme } from '@/lib/theme'

const sleep = (millis: number) => new Promise(resolve => window.setTimeout(resolve, millis))

export function ProblemDetailPage() {
  const nav = useNavigate()
  const { problemId } = useParams()
  const [problem, setProblem] = useState<AlgorithmProblemDetail>()
  const [code, setCode] = useState('')
  const [input, setInput] = useState('')
  const [running, setRunning] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [submissionTimedOut, setSubmissionTimedOut] = useState(false)
  const [runResult, setRunResult] = useState<AlgorithmRunResponse>()
  const [submission, setSubmission] = useState<AlgorithmSubmissionDetail>()
  const [error, setError] = useState('')
  const [note, setNote] = useState('')
  const [savingNote, setSavingNote] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const [activeTab, setActiveTab] = useState<'run' | 'history'>('run')
  const [history, setHistory] = useState<AlgorithmSubmissionItem[]>([])
  const { dark } = useTheme()
  const busy = running || submitting

  const id = Number(problemId)

  useEffect(() => {
    let disposed = false
    setLoaded(false)
    setError('')
    setRunResult(undefined)
    setSubmission(undefined)
    setSubmissionTimedOut(false)
    void algorithmApi.problem(id)
      .then(data => {
        if (disposed) return
        setProblem(data)
        const saved = localStorage.getItem(`algo-code-${data.id}`)
        setCode(saved ?? data.starterCode)
        setNote(data.note ?? '')
        setInput('')
        setLoaded(true)
      })
      .catch(reason => {
        if (!disposed) setError(reason instanceof Error ? reason.message : '题目加载失败')
      })
    void algorithmApi.submissions({ problemId: id, page: 1, pageSize: 6 })
      .then(result => { if (!disposed) setHistory(result.records) })
      .catch(() => undefined)
    return () => { disposed = true }
  }, [id])

  const handleRunRef = useRef<() => void>(() => undefined)

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.ctrlKey && event.key === 'Enter' && !busy) {
        event.preventDefault()
        void handleRunRef.current()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [busy])

  useEffect(() => {
    handleRunRef.current = handleRun
  })

  useEffect(() => {
    if (!problem) return
    const timer = window.setTimeout(() => {
      localStorage.setItem(`algo-code-${problem.id}`, code)
    }, 500)
    return () => window.clearTimeout(timer)
  }, [code, problem])

  async function handleRun() {
    if (!problem) return
    setRunning(true)
    setError('')
    setRunResult(undefined)
    setSubmission(undefined)
    setSubmissionTimedOut(false)
    try {
      setRunResult(await algorithmApi.run({ problemId: id, language: 'JAVA17', sourceCode: code, input }))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '运行失败，请稍后重试')
    } finally {
      setRunning(false)
    }
  }

  async function handleSubmit() {
    setSubmitting(true)
    setError('')
    setRunResult(undefined)
    setSubmission(undefined)
    setSubmissionTimedOut(false)
    try {
      const submissionId = await algorithmApi.submit({ problemId: id, language: 'JAVA17', sourceCode: code })
      let detail: AlgorithmSubmissionDetail | undefined
      let reachedTerminal = false
      for (let attempt = 0; attempt < 60; attempt++) {
        await sleep(1500)
        detail = await algorithmApi.submission(submissionId)
        setSubmission(detail)
        if (algorithmTerminalStatuses.has(detail.status)) {
          reachedTerminal = true
          break
        }
      }
      if (!reachedTerminal) {
        setSubmissionTimedOut(true)
        setError('判题等待时间较长，请前往提交记录查看最新状态')
      }
      void algorithmApi.problem(id).then(setProblem).catch(() => undefined)
      void algorithmApi.submissions({ problemId: id, page: 1, pageSize: 6 })
        .then(result => setHistory(result.records))
        .catch(() => undefined)
    } catch (reason) {
      setSubmissionTimedOut(true)
      setError(reason instanceof Error ? reason.message : '提交失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  async function toggleFavorite() {
    if (!problem) return
    try {
      await algorithmApi.favorite(problem.id, !problem.favorited)
      setProblem({ ...problem, favorited: !problem.favorited })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '操作失败')
    }
  }

  async function saveNote() {
    if (!problem) return
    setSavingNote(true)
    try {
      await algorithmApi.note(problem.id, note.trim())
      setProblem({ ...problem, note: note.trim() || undefined })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '笔记保存失败')
    } finally {
      setSavingNote(false)
    }
  }

  const verdict = useMemo(() => submission ?? runResult, [submission, runResult])

  return (
    <div className="space-y-6">
      <AlgorithmPageHeader
        title={problem?.title ?? '正在加载题目…'}
        description={problem ? `${problem.tags.map(tag => tag.name).join('、') || '未分类'} · 时间限制 ${problem.timeLimitMs}ms · 内存限制 ${problem.memoryLimitMb}MB · 已提交 ${problem.mySubmitCount} 次` : '正在准备题目内容与代码环境。'}
        backTo="/algorithm/problems"
        backLabel="返回题库"
        compact
        actions={problem ? <Button className="w-full sm:w-auto" variant={problem.favorited ? 'primary' : 'secondary'} onClick={() => void toggleFavorite()}>
            {problem.favorited ? <Star className="h-4 w-4 fill-current" /> : <Bookmark className="h-4 w-4" />}
            {problem.favorited ? '已收藏' : '收藏'}
          </Button> : undefined}
      />

      {problem && <div className="flex flex-wrap items-center gap-2">
        <Badge tone={algorithmDifficultyMeta[problem.difficulty]?.tone ?? 'default'}>{difficultyLabel(problem.difficulty)}</Badge>
        {problem.progressStatus === 'ACCEPTED' && <Badge tone="success">已通过</Badge>}
        {problem.progressStatus === 'ATTEMPTED' && <Badge tone="warning">尝试过</Badge>}
      </div>}

      {error && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
      {!loaded && !error && <p className="p-12 text-center text-sm text-muted-foreground">正在加载题目…</p>}

      {problem && <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.15fr)]">
        <Card className="p-5 sm:p-6">
          <div className="algorithm-md">
            <Markdown
              components={{
                h1: props => <h1 className="mt-4 text-xl font-bold first:mt-0" {...props} />,
                h2: props => <h2 className="mt-5 text-lg font-bold first:mt-0" {...props} />,
                h3: props => <h3 className="mt-4 font-bold" {...props} />,
                p: props => <p className="mt-2 leading-6 first:mt-0" {...props} />,
                ul: props => <ul className="mt-2 list-disc space-y-1 pl-5" {...props} />,
                ol: props => <ol className="mt-2 list-decimal space-y-1 pl-5" {...props} />,
                pre: props => <pre className="mt-2 overflow-x-auto rounded-xl bg-muted p-3 text-xs leading-5" {...props} />,
                code: props => <code className="rounded bg-muted px-1 py-0.5 text-[13px]" {...props} />,
                strong: props => <strong className="font-semibold" {...props} />,
                a: props => <a className="text-[var(--accent)] underline" {...props} />,
              }}
            >
              {problem.descriptionMd}
            </Markdown>
          </div>

          {problem.inputDescription && <section className="mt-5">
            <h2 className="font-bold">输入说明</h2>
            <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{problem.inputDescription}</p>
          </section>}
          {problem.outputDescription && <section className="mt-5">
            <h2 className="font-bold">输出说明</h2>
            <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{problem.outputDescription}</p>
          </section>}
          {problem.constraintsDescription && <section className="mt-5">
            <h2 className="font-bold">数据范围</h2>
            <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{problem.constraintsDescription}</p>
          </section>}

          {problem.sampleCases.length > 0 && <section className="mt-5">
            <h2 className="font-bold">示例</h2>
            {problem.sampleCases.map(sample => (
              <div key={sample.id} className="mt-3 grid gap-3 sm:grid-cols-2">
                <div>
                  <p className="text-xs font-semibold text-muted-foreground">输入</p>
                  <button
                    type="button"
                    title="点击填入自定义输入"
                    onClick={() => { setInput(sample.inputData); setActiveTab('run') }}
                    className="mt-1 block w-full cursor-pointer overflow-x-auto whitespace-pre rounded-xl bg-muted p-3 text-left text-xs leading-5 transition hover:border hover:border-[var(--accent)] hover:bg-[var(--accent-soft)]"
                  >
                    {sample.inputData}
                    <span className="mt-1 block text-[10px] font-semibold text-[var(--accent)]">点击填入运行输入</span>
                  </button>
                </div>
                <div>
                  <p className="text-xs font-semibold text-muted-foreground">输出</p>
                  <pre className="mt-1 overflow-x-auto rounded-xl bg-muted p-3 text-xs leading-5">{sample.expectedOutput}</pre>
                </div>
              </div>
            ))}
          </section>}

          {problem.hintContent && <section className="mt-5">
            <h2 className="font-bold">提示</h2>
            <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{problem.hintContent}</p>
          </section>}

          <section className="mt-6 border-t border-border pt-5">
            <h2 className="font-bold">我的笔记</h2>
            <textarea
              value={note}
              onChange={event => setNote(event.target.value)}
              rows={4}
              placeholder="记录你的解题思路、易错点…"
              className="mt-2 w-full rounded-xl border border-border bg-background p-3 text-sm outline-none transition focus:border-[var(--accent)]"
            />
            <Button variant="secondary" className="mt-2" disabled={savingNote} onClick={() => void saveNote()}>
              {savingNote ? <Loader2 className="h-4 w-4 animate-spin" /> : null}保存笔记
            </Button>
          </section>
        </Card>

        <Card className="overflow-hidden">
          <div className="flex items-center justify-between gap-3 border-b border-border p-4">
            <select value="JAVA17" disabled className="h-10 rounded-xl border border-border bg-background px-3 text-sm font-semibold outline-none disabled:opacity-70">
              <option value="JAVA17">Java 17</option>
            </select>
            <Button variant="ghost" onClick={() => setCode(problem.starterCode)}>
              <RefreshCw className="h-4 w-4" />重置代码
            </Button>
          </div>
          <LazyCodeEditor value={code} onChange={setCode} height={360} dark={dark} />

          <div className="flex items-center gap-1 border-t border-border bg-muted/30 px-3 py-2">
            <button
              type="button"
              onClick={() => setActiveTab('run')}
              className={`inline-flex h-9 items-center gap-1.5 rounded-full px-3 text-sm transition ${activeTab === 'run' ? 'bg-surface font-semibold shadow-sm' : 'text-muted-foreground hover:text-foreground'}`}
            >
              <Terminal className="h-4 w-4" />运行与提交
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('history')}
              className={`inline-flex h-9 items-center gap-1.5 rounded-full px-3 text-sm transition ${activeTab === 'history' ? 'bg-surface font-semibold shadow-sm' : 'text-muted-foreground hover:text-foreground'}`}
            >
              <ClipboardList className="h-4 w-4" />提交记录
              {history.length > 0 && <span className="text-xs text-muted-foreground">{history.length}</span>}
            </button>
          </div>

          {activeTab === 'run' ? <div className="p-4">
            <label className="text-xs font-semibold text-muted-foreground">自定义输入</label>
            <textarea
              value={input}
              onChange={event => setInput(event.target.value)}
              rows={3}
              placeholder="例如：3 5&#10;多行输入请直接换行"
              className="mt-1 w-full rounded-xl border border-border bg-background p-3 font-mono text-sm outline-none transition focus:border-[var(--accent)]"
            />
            <div className="mt-3 flex flex-col gap-2 sm:flex-row">
              <Button variant="secondary" className="flex-1" disabled={busy} onClick={() => void handleRun()}>
                {running ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="h-4 w-4" />}运行代码
              </Button>
              <Button className="flex-1" disabled={busy} onClick={() => void handleSubmit()}>
                {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}提交代码
              </Button>
            </div>

            {verdict && (
              <div className="mt-4 space-y-3">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={algorithmStatusTone(verdict.status)}>{algorithmStatusLabel(verdict.status)}</Badge>
                  {'passedCount' in verdict && verdict.totalCount > 0 && (
                    <span className="text-xs text-muted-foreground">通过 {verdict.passedCount}/{verdict.totalCount} 个用例</span>
                  )}
                  {verdict.executionTimeMs != null && <span className="text-xs text-muted-foreground">{verdict.executionTimeMs}ms</span>}
                </div>
                {'compileMessage' in verdict && verdict.compileMessage && (
                  <pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded-xl bg-rose-50 p-3 text-xs leading-5 text-rose-700">{verdict.compileMessage}</pre>
                )}
                {'runtimeMessage' in verdict && verdict.runtimeMessage && (
                  <pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded-xl bg-rose-50 p-3 text-xs leading-5 text-rose-700">{verdict.runtimeMessage}</pre>
                )}
                {'output' in verdict && verdict.output !== undefined && (
                  <div>
                    <p className="text-xs font-semibold text-muted-foreground">标准输出</p>
                    <pre className="mt-1 max-h-56 overflow-auto whitespace-pre-wrap rounded-xl bg-muted p-3 text-xs leading-5">{verdict.output || '（无输出）'}</pre>
                  </div>
                )}
                {'errorMessage' in verdict && verdict.errorMessage && (
                  <pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded-xl bg-rose-50 p-3 text-xs leading-5 text-rose-700">{verdict.errorMessage}</pre>
                )}
                {'caseResults' in verdict && verdict.caseResults.length > 0 && (
                  <div className="overflow-hidden rounded-xl border border-border">
                    <table className="w-full text-left text-xs">
                      <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                        <tr><th className="px-3 py-2">用例</th><th className="px-3 py-2">类型</th><th className="px-3 py-2">结果</th><th className="px-3 py-2">耗时</th></tr>
                      </thead>
                      <tbody className="divide-y divide-border/70">
                        {verdict.caseResults.map((result, index) => (
                          <tr key={index}>
                            <td className="px-3 py-2">#{index + 1}</td>
                            <td className="px-3 py-2">{result.caseType === 'SAMPLE' ? '示例' : '隐藏'}</td>
                            <td className="px-3 py-2">
                              <Badge tone={algorithmStatusTone(result.status)}>{algorithmStatusLabel(result.status)}</Badge>
                            </td>
                            <td className="px-3 py-2">{result.executionTimeMs != null ? `${result.executionTimeMs}ms` : '-'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
                {submission && !algorithmTerminalStatuses.has(submission.status) && (
                  <p className="flex items-center gap-2 text-xs text-muted-foreground">
                    {submissionTimedOut
                      ? '判题仍在后台进行，可在提交记录中查看最新状态。'
                      : <><Loader2 className="h-3.5 w-3.5 animate-spin" />正在判题，请稍候…</>}
                  </p>
                )}
              </div>
            )}
          </div> : (
            <div className="max-h-80 overflow-y-auto p-4">
              {history.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted-foreground">还没有提交记录，快去提交第一份代码吧。</p>
              ) : (
                <div className="divide-y divide-border/70">
                  {history.map(item => (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => nav(`/algorithm/submissions/${item.id}`)}
                      className="flex w-full items-center justify-between gap-3 py-3 text-left transition hover:bg-muted/40"
                    >
                      <span className="flex min-w-0 items-center gap-2">
                        <Badge tone={algorithmStatusTone(item.status)}>{algorithmStatusLabel(item.status)}</Badge>
                        <span className="min-w-0 truncate text-xs text-muted-foreground">#{item.id}</span>
                      </span>
                      <span className="flex shrink-0 items-center gap-3 text-xs text-muted-foreground">
                        <span>{item.passedCount}/{item.totalCount} 用例</span>
                        {item.executionTimeMs != null && <span>{item.executionTimeMs}ms</span>}
                        <span>{new Date(item.createdAt).toLocaleString('zh-CN')}</span>
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </Card>
      </div>}
    </div>
  )
}
