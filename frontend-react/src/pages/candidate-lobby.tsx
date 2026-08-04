import { motion } from 'framer-motion'
import { Calendar, ClipboardPenLine, Clock3, FileChartColumn, FileUser, NotebookPen, Play, Search, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { type Interview, type PracticeBank, request } from '@/lib/api'
import { canEnterInterview, canViewReport, canWriteReflection, interviewStatusText, interviewStatusTone, isReportPending, type BadgeTone } from '@/lib/interview-status'
import { interviewerStyleFromRemark, interviewerStyleLabel, interviewerStyles, isPracticeInterview, type InterviewerStyleKey } from '@/lib/interviewer-styles'

type FreeInterviewHistory = {
  id: string
  resumeFilename?: string
  targetRole?: string
  status: string
  completedTurns: number
  totalScore?: number
  createdAt: string
  updatedAt?: string
  completedAt?: string
}

type LobbyEntry =
  | { kind: 'interview'; item: Interview; sortAt: string }
  | { kind: 'free'; item: FreeInterviewHistory; sortAt: string }

const freeStatusMeta: Record<string, { label: string; tone: BadgeTone; filterStatus: number }> = {
  ANALYZING: { label: '简历分析中', tone: 'warning', filterStatus: 1 },
  INTERVIEWING: { label: '进行中', tone: 'success', filterStatus: 1 },
  REPORT_GENERATING: { label: '报告生成中', tone: 'warning', filterStatus: 5 },
  REPORT_READY: { label: '报告已生成', tone: 'success', filterStatus: 6 },
  FAILED: { label: '处理失败', tone: 'danger', filterStatus: 7 },
}

function freeMeta(status: string) {
  return freeStatusMeta[status] ?? { label: '未知状态', tone: 'default' as BadgeTone, filterStatus: 7 }
}

function freeAction(status: string) {
  if (status === 'INTERVIEWING') return '继续面试'
  if (status === 'REPORT_READY') return '查看报告'
  if (status === 'ANALYZING' || status === 'REPORT_GENERATING') return '查看进度'
  return '查看记录'
}

export function CandidateLobby() {
  const [items, setItems] = useState<Interview[]>([])
  const [freeItems, setFreeItems] = useState<FreeInterviewHistory[]>([])
  const [banks, setBanks] = useState<PracticeBank[]>([])
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [open, setOpen] = useState(false)
  const [bank, setBank] = useState('')
  const [style, setStyle] = useState<InterviewerStyleKey>('big-tech')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const nav = useNavigate()

  useEffect(() => {
    Promise.all([
      request<Interview[]>('/v1/interviews'),
      request<PracticeBank[]>('/v1/interviews/practice/banks'),
      request<FreeInterviewHistory[]>('/v1/free-interviews'),
    ]).then(([interviews, practiceBanks, freeInterviews]) => {
      setItems(interviews)
      setBanks(practiceBanks)
      setFreeItems(freeInterviews)
    }).catch(reason => setError(reason instanceof Error ? reason.message : '面试大厅加载失败，请稍后重试。'))
  }, [])

  useEffect(() => {
    if (!open) return
    const previousOverflow = document.body.style.overflow
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) setOpen(false)
    }
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', closeOnEscape)
    }
  }, [busy, open])

  const list = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    const entries: LobbyEntry[] = [
      ...items.map(item => ({ kind: 'interview' as const, item, sortAt: item.scheduledAt })),
      ...freeItems.map(item => ({ kind: 'free' as const, item, sortAt: item.updatedAt ?? item.createdAt })),
    ]
    return entries.filter(entry => {
      const statusCode = entry.kind === 'interview' ? entry.item.status : freeMeta(entry.item.status).filterStatus
      const searchable = entry.kind === 'interview'
        ? entry.item.title
        : `${entry.item.targetRole ?? ''} ${entry.item.resumeFilename ?? ''} 自由简历面试`
      return (!status || String(statusCode) === status) && (!keyword || searchable.toLowerCase().includes(keyword))
    }).sort((left, right) => right.sortAt.localeCompare(left.sortAt))
  }, [freeItems, items, query, status])

  async function enter(item: Interview) {
    try {
      if (item.status === 0) await request(`/v1/interviews/${item.id}/start`, { method: 'POST' })
      nav(`/candidate/interviews/${item.id}/room`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '无法进入面试，请稍后重试。')
    }
  }

  async function practice() {
    if (!bank) {
      setError('请选择练习题库。')
      return
    }
    setBusy(true)
    try {
      const result = await request<Interview>('/v1/interviews/practice', {
        method: 'POST',
        body: JSON.stringify({ questionBankId: bank, questionCount: 5, duration: 30, interviewerStyle: style }),
      })
      nav(`/candidate/interviews/${result.id}/room`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '练习创建失败，请稍后重试。')
    } finally {
      setBusy(false)
    }
  }

  return <div className="space-y-6">
    <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
      <div>
        <p className="text-sm font-semibold text-[var(--accent)]">面试与练习</p>
        <h1 className="mt-2 text-3xl font-bold">面试大厅</h1>
        <p className="mt-2 text-muted-foreground">进入已安排的面试，或创建新的模拟练习。</p>
      </div>
      <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap"><Button variant="secondary" className="min-w-0 px-2.5 sm:px-4" onClick={() => nav('/candidate/free-interview')}><FileUser className="h-4 w-4 shrink-0" /><span className="truncate">简历定向面试</span></Button><Button className="min-w-0 px-2.5 sm:px-4" onClick={() => setOpen(true)}><ClipboardPenLine className="h-4 w-4 shrink-0" /><span className="truncate">创建模拟练习</span></Button></div>
    </div>

    {error && <p className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700 dark:bg-rose-950/30 dark:text-rose-200">{error}</p>}

    <Card className="p-4 sm:p-5">
      <div className="flex flex-col gap-3 md:flex-row">
        <label className="flex h-11 flex-1 items-center gap-2 rounded-xl border border-border px-3">
          <Search className="h-4 w-4 text-muted-foreground" />
          <input aria-label="搜索面试" className="w-full bg-transparent outline-none" placeholder="搜索面试主题" value={query} onChange={event => setQuery(event.target.value)} />
        </label>
        <ResponsiveSelect
          ariaLabel="选择状态"
          value={status}
          onValueChange={setStatus}
          className="w-full md:w-44"
          options={[
            { value: "", label: "全部状态" },
            { value: "0", label: "待开始" },
            { value: "1", label: "进行中" },
            { value: "2", label: "已结束" },
            { value: "4", label: "已通过" },
            { value: "5", label: "报告生成中" },
            { value: "6", label: "报告已生成" },
            { value: "7", label: "未通过" },
          ]}
        />
      </div>

      <div className="mt-5 grid gap-4 lg:grid-cols-2">
        {list.map((entry, index) => <motion.article
          key={`${entry.kind}-${entry.item.id}`}
          initial={{ opacity: 0, y: 12 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.12 }}
          transition={{ duration: 0.28, delay: index * 0.04, ease: 'easeOut' }}
          whileHover={{ y: -2 }}
          className="min-w-0 overflow-hidden rounded-2xl border border-border p-4 transition hover:shadow-lg sm:p-5"
        >
          {entry.kind === 'free' ? <>
            <div className="flex min-w-0 flex-wrap items-start justify-between gap-2">
              <span className="min-w-0 flex-1 break-words text-sm leading-5 text-muted-foreground">自由简历面试 · {entry.item.resumeFilename || '已上传简历'}</span>
              <Badge className="max-w-full shrink-0" tone={freeMeta(entry.item.status).tone}>{freeMeta(entry.item.status).label}</Badge>
            </div>
            <h2 className="mt-5 break-words text-lg font-bold">{entry.item.targetRole || '简历定向面试'}</h2>
            <p className="mt-3 flex flex-wrap gap-2 text-sm text-muted-foreground">
              <span className="inline-flex items-center gap-1.5"><Calendar className="h-4 w-4 shrink-0" />{(entry.item.updatedAt ?? entry.item.createdAt).replace('T', ' ').slice(0, 16)}</span>
              <span className="inline-flex items-center gap-1.5"><Clock3 className="h-4 w-4 shrink-0" />已完成 {entry.item.completedTurns}/10 轮</span>
            </p>
            <div className="mt-5 border-t border-border pt-4">
              <span className="block break-all text-xs text-muted-foreground">自由面试 #{entry.item.id}</span>
              <Button className="mt-3 w-full" variant={entry.item.status === 'INTERVIEWING' ? 'primary' : 'secondary'} onClick={() => nav(`/candidate/free-interview?sessionId=${entry.item.id}`)}>
                {entry.item.status === 'REPORT_READY' ? <FileChartColumn className="h-4 w-4" /> : <Play className="h-4 w-4" />}{freeAction(entry.item.status)}
              </Button>
            </div>
          </> : <>
            <div className="flex min-w-0 flex-wrap items-start justify-between gap-2">
              <span className="min-w-0 flex-1 break-words text-sm leading-5 text-muted-foreground">{isPracticeInterview(entry.item.remark) ? '个人模拟练习' : 'AI 模拟面试'} · {interviewerStyleLabel(interviewerStyleFromRemark(entry.item.remark))}</span>
              <Badge className="max-w-full shrink-0" tone={interviewStatusTone(entry.item.status)}>{interviewStatusText[entry.item.status] ?? '未知状态'}</Badge>
            </div>
            <h2 className="mt-5 break-words text-lg font-bold">{entry.item.title}</h2>
            <p className="mt-3 flex flex-wrap gap-2 text-sm text-muted-foreground">
              <span className="inline-flex items-center gap-1.5"><Calendar className="h-4 w-4 shrink-0" />{entry.item.scheduledAt.replace('T', ' ').slice(0, 16)}</span>
              <span className="inline-flex items-center gap-1.5"><Clock3 className="h-4 w-4 shrink-0" />{entry.item.duration} 分钟</span>
            </p>
            <div className="mt-5 border-t border-border pt-4">
              <span className="break-all text-xs text-muted-foreground">#{entry.item.id}</span>
              <div className="mt-3 flex gap-2 [&>button]:min-w-0 [&>button]:flex-1">
                {canWriteReflection(entry.item.status) && <Button
                  variant="ghost"
                  className="px-3"
                  onClick={() => nav(`/candidate/reflections?interviewId=${entry.item.id}`)}
                >
                  <NotebookPen className="h-4 w-4" />写心得
                </Button>}
                {canViewReport(entry.item.status)
                  ? <Button variant="secondary" onClick={() => nav(`/candidate/interviews/${entry.item.id}/report`)}>查看报告</Button>
                  : isReportPending(entry.item.status)
                    ? <Button variant="secondary" disabled>报告生成中</Button>
                    : <Button disabled={!canEnterInterview(entry.item.status)} onClick={() => enter(entry.item)}><Play className="h-4 w-4" />{entry.item.status === 1 ? '继续面试' : '开始面试'}</Button>}
              </div>
            </div>
          </>}
        </motion.article>)}
        {!list.length && <div className="col-span-full py-12 text-center text-sm text-muted-foreground">暂无符合当前条件的面试记录</div>}
      </div>
    </Card>

    {open && <div className="fixed inset-0 z-[80] grid items-end bg-black/45 p-0 backdrop-blur-sm sm:place-items-center sm:p-5" role="dialog" aria-modal="true" aria-labelledby="practice-dialog-title">
      <button type="button" className="absolute inset-0 h-full w-full cursor-default" aria-label="关闭创建练习弹窗" onClick={() => !busy && setOpen(false)} />
      <section className="safe-area-bottom relative z-10 flex max-h-[min(92dvh,760px)] w-full max-w-xl flex-col overflow-hidden rounded-t-[28px] border border-border bg-surface shadow-2xl sm:rounded-[28px]">
        <header className="flex items-start justify-between gap-4 border-b border-border px-5 py-4 sm:px-6 sm:py-5">
          <div className="min-w-0">
            <p className="text-xs font-semibold tracking-[.12em] text-[var(--accent)]">专项模拟</p>
            <h2 id="practice-dialog-title" className="mt-1 text-2xl font-bold">创建模拟练习</h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">选择题库和面试官风格后开始练习。</p>
          </div>
          <button type="button" disabled={busy} onClick={() => setOpen(false)} className="grid h-11 w-11 shrink-0 place-items-center rounded-full hover:bg-muted disabled:opacity-50" aria-label="关闭">
            <X className="h-5 w-5" />
          </button>
        </header>
        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-5 sm:px-6">
          <label className="block text-sm font-semibold">
            练习题库
            <ResponsiveSelect
              ariaLabel="选择练习题库"
              value={bank}
              onValueChange={setBank}
              className="mt-2 w-full"
              options={[{ value: "", label: "请选择练习题库" }, ...banks.map(item => ({ value: item.id, label: `${item.name} · ${item.questionCount} 题` }))]}
            />
          </label>
          <fieldset className="mt-5">
            <legend className="text-sm font-semibold">面试官风格</legend>
            <div className="mt-3 grid grid-cols-2 gap-2.5" role="radiogroup" aria-label="面试官风格">
              {interviewerStyles.map(item => (
                <button
                  key={item.key}
                  type="button"
                  role="radio"
                  aria-checked={style === item.key}
                  onClick={() => setStyle(item.key)}
                  className={`min-h-28 rounded-2xl border p-3 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] ${style === item.key ? 'border-[var(--accent)] bg-[var(--accent-soft)] shadow-sm' : 'border-border bg-surface hover:bg-muted'}`}
                >
                  <span className="text-sm font-semibold">{item.label}</span>
                  <span className="mt-1 block text-xs leading-5 text-muted-foreground">{item.description}</span>
                </button>
              ))}
            </div>
          </fieldset>
        </div>
        <footer className="grid grid-cols-2 gap-3 border-t border-border bg-surface px-5 py-4 sm:flex sm:justify-end sm:px-6">
          <Button variant="secondary" disabled={busy} onClick={() => setOpen(false)}>取消</Button>
          <Button disabled={busy || !bank} onClick={practice}>{busy ? '正在创建…' : '开始练习'}</Button>
        </footer>
      </section>
    </div>}
  </div>
}
