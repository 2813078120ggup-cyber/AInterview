import { ArrowLeft, Calendar, Clock3, ClipboardList, FileChartColumn, Headphones, MessageSquareText, PlayCircle, Video } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request, requestBlob, type Interview } from '@/lib/api'
import { canViewReport, interviewStatusText, interviewStatusTone } from '@/lib/interview-status'

type Question = { interviewQuestionId: string; content: string; options?: string; questionType: string; maxScore: number }
type Answer = { interviewQuestionId: string; answerContent?: string; answerData?: string; score?: number; evaluation?: string; durationSeconds?: number }
type RecordingSegment = { id: string; interviewQuestionId: string; segmentNo: number; startedOffsetMs: number; endedOffsetMs: number; contentType: string; contentPath: string }
type TimelineEvent = { id: string; interviewQuestionId?: string; eventType: string; offsetMs: number; content?: string }
type Recording = { id: string; mode: 'TEXT' | 'AUDIO' | 'VIDEO'; status: string; startedAt: string; endedAt?: string; segments: RecordingSegment[]; events: TimelineEvent[] }
type ReviewOption = { key: string; text: string }

const eventLabels: Record<string, string> = {
  QUESTION_STARTED: '题目开始',
  ANSWER_SUBMITTED: '候选人回答',
  FOLLOW_UP: '面试官追问',
  TRANSITION: '本题收尾',
  QUESTION_COMPLETED: '题目完成',
  RECORDING_STARTED: '开始录制',
  RECORDING_STOPPED: '录制已保存',
  RECORDING_ERROR: '录制异常',
}

const questionTypeLabels: Record<string, string> = {
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
  short_answer: '简答题',
  essay: '问答题',
}

function displayOptionKey(key: string) {
  const normalized = key.trim().toLowerCase()
  if (normalized === 'true') return '正确'
  if (normalized === 'false') return '错误'
  return key.trim().toUpperCase()
}

function parseQuestionOptions(raw?: string): ReviewOption[] {
  if (!raw?.trim()) return []
  let parsed: unknown = raw.trim()

  for (let attempt = 0; attempt < 2 && typeof parsed === 'string'; attempt += 1) {
    try {
      parsed = JSON.parse(parsed)
    } catch {
      break
    }
  }

  if (Array.isArray(parsed)) {
    return parsed.flatMap((item, index) => {
      if (typeof item === 'string' || typeof item === 'number' || typeof item === 'boolean') {
        return [{ key: String.fromCharCode(65 + index), text: String(item) }]
      }
      if (!item || typeof item !== 'object') return []
      const record = item as Record<string, unknown>
      const key = String(record.key ?? record.value ?? record.code ?? String.fromCharCode(65 + index)).trim()
      const text = String(record.text ?? record.label ?? record.content ?? '').trim()
      return text ? [{ key, text }] : []
    })
  }

  if (parsed && typeof parsed === 'object') {
    return Object.entries(parsed as Record<string, unknown>)
      .map(([key, value]) => ({ key, text: String(value ?? '').trim() }))
      .filter(item => item.text)
  }

  if (/^[{[]/.test(raw.trim())) return []

  return raw.split(/\r?\n/)
    .map((line, index) => {
      const text = line.trim()
      const match = text.match(/^([A-Za-z]|true|false)[.、:：)\s-]+(.+)$/i)
      return match
        ? { key: match[1], text: match[2].trim() }
        : { key: String.fromCharCode(65 + index), text }
    })
    .filter(item => item.text)
}

function answerKeys(answer?: Answer) {
  const raw = answer?.answerData?.trim() || answer?.answerContent?.trim() || ''
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw) as unknown
    if (Array.isArray(parsed)) return parsed.map(item => String(item).trim()).filter(Boolean)
    if (typeof parsed === 'string' || typeof parsed === 'number' || typeof parsed === 'boolean') return [String(parsed)]
  } catch {
    // 兼容历史数据中以逗号或空格分隔的选项键。
  }
  return raw.split(/[,，\s]+/).map(item => item.trim()).filter(Boolean)
}

function QuestionOptions({ options }: { options: ReviewOption[] }) {
  if (!options.length) return null
  return <div className="mt-4 grid gap-2 sm:grid-cols-2">
    {options.map((option, index) => <div key={`${option.key}-${index}`} className="flex min-w-0 items-start gap-3 rounded-2xl border border-border/80 bg-background/65 px-4 py-3 text-sm">
      <span className="grid h-7 min-w-7 shrink-0 place-items-center rounded-lg bg-[var(--accent-soft)] px-1.5 font-semibold text-[var(--accent)]">{displayOptionKey(option.key)}</span>
      <span className="min-w-0 break-words leading-7">{option.text}</span>
    </div>)}
  </div>
}

function CandidateAnswer({ answer, options, choiceQuestion }: { answer?: Answer; options: ReviewOption[]; choiceQuestion: boolean }) {
  if (!answer?.answerContent && !answer?.answerData) {
    return <div className="mt-3 rounded-2xl border border-dashed border-border bg-muted/30 px-4 py-4">
      <p className="text-sm font-semibold">未作答</p>
      <p className="mt-1 text-xs leading-5 text-muted-foreground">候选人未提交本题答案。</p>
    </div>
  }

  if (!choiceQuestion) {
    return <p className="mt-3 whitespace-pre-wrap break-words rounded-2xl bg-muted/45 px-4 py-3 text-sm leading-6">{answer.answerContent || '已提交结构化作答'}</p>
  }

  const selected = answerKeys(answer)
  const optionMap = new Map(options.map(option => [option.key.toLowerCase(), option]))
  if (!selected.length) {
    return <p className="mt-3 rounded-2xl bg-muted/45 px-4 py-3 text-sm leading-6">{answer.answerContent || '已提交作答，暂无可展示的选项。'}</p>
  }
  return <div className="mt-3 flex flex-wrap gap-2">
    {selected.map((key, index) => {
      const option = optionMap.get(key.toLowerCase())
      const label = option ? `${displayOptionKey(option.key)}. ${option.text}` : displayOptionKey(key)
      return <span key={`${key}-${index}`} className="inline-flex min-h-9 max-w-full items-center rounded-full border border-[color-mix(in_srgb,var(--accent)_28%,var(--border))] bg-[var(--accent-soft)] px-3 py-1.5 text-sm font-semibold text-[var(--accent)]">
        <span className="break-words">{label}</span>
      </span>
    })}
  </div>
}

function offsetText(offsetMs: number) {
  const seconds = Math.floor(offsetMs / 1000)
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

export function AdminInterviewReview() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [interview, setInterview] = useState<Interview>()
  const [questions, setQuestions] = useState<Question[]>([])
  const [answers, setAnswers] = useState<Answer[]>([])
  const [recording, setRecording] = useState<Recording | null>()
  const [selectedSegment, setSelectedSegment] = useState<RecordingSegment>()
  const [mediaUrl, setMediaUrl] = useState('')
  const [loading, setLoading] = useState(true)
  const [mediaLoading, setMediaLoading] = useState(false)
  const [error, setError] = useState('')
  const videoPlayer = useRef<HTMLVideoElement>(null)
  const audioPlayer = useRef<HTMLAudioElement>(null)
  const pendingSeek = useRef<{ segmentId: string; seconds: number } | undefined>(undefined)

  function mediaElement(): HTMLMediaElement | null {
    return videoPlayer.current ?? audioPlayer.current
  }

  useEffect(() => {
    let disposed = false
    Promise.all([
      request<Interview>(`/v1/interviews/${id}`),
      request<Question[]>(`/v1/interviews/${id}/questions`),
      request<Answer[]>(`/v1/interviews/${id}/answers`),
      request<Recording | null>(`/v1/interviews/${id}/recording`),
    ]).then(([item, questionList, answerList, recordingView]) => {
      if (disposed) return
      setInterview(item)
      setQuestions(questionList)
      setAnswers(answerList)
      setRecording(recordingView)
      setSelectedSegment(recordingView?.segments[0])
    }).catch(reason => {
      if (!disposed) setError(reason instanceof Error ? reason.message : '面试回顾加载失败，请稍后重试。')
    }).finally(() => { if (!disposed) setLoading(false) })
    return () => { disposed = true }
  }, [id])

  useEffect(() => {
    let disposed = false
    let objectUrl = ''
    setMediaUrl('')
    if (!selectedSegment) return
    setMediaLoading(true)
    void requestBlob(selectedSegment.contentPath).then(blob => {
      if (disposed) return
      objectUrl = URL.createObjectURL(blob)
      setMediaUrl(objectUrl)
    }).catch(reason => {
      if (!disposed) setError(reason instanceof Error ? reason.message : '录制分段加载失败，请稍后重试。')
    }).finally(() => { if (!disposed) setMediaLoading(false) })
    return () => {
      disposed = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [selectedSegment])

  const answersByQuestion = useMemo(() => new Map(answers.map(item => [String(item.interviewQuestionId), item])), [answers])
  const segmentsByQuestion = useMemo(() => {
    const result = new Map<string, RecordingSegment[]>()
    recording?.segments.forEach(segment => {
      const key = String(segment.interviewQuestionId)
      result.set(key, [...(result.get(key) ?? []), segment])
    })
    return result
  }, [recording])
  const selectedQuestionId = selectedSegment ? String(selectedSegment.interviewQuestionId) : ''
  const selectedEvents = useMemo(() => recording?.events.filter(event => String(event.interviewQuestionId ?? '') === selectedQuestionId) ?? [], [recording, selectedQuestionId])
  const selectedQuestionSegments = segmentsByQuestion.get(selectedQuestionId) ?? []

  function playSegment(segment: RecordingSegment, localSeconds = 0) {
    pendingSeek.current = { segmentId: segment.id, seconds: Math.max(0, localSeconds) }
    const element = mediaElement()
    if (selectedSegment?.id === segment.id && element && element.readyState >= 1) {
      element.currentTime = Math.max(0, localSeconds)
      void element.play().catch(() => undefined)
      pendingSeek.current = undefined
      return
    }
    setSelectedSegment(segment)
  }

  function onMediaReady() {
    const target = pendingSeek.current
    const element = mediaElement()
    if (!element || (target && target.segmentId !== selectedSegment?.id)) return
    element.currentTime = target?.seconds ?? 0
    pendingSeek.current = undefined
    void element.play().catch(() => undefined)
  }

  function playTimelineEvent(event: TimelineEvent) {
    const segment = selectedQuestionSegments.find(item => event.offsetMs >= item.startedOffsetMs && event.offsetMs <= item.endedOffsetMs)
      ?? selectedQuestionSegments[0]
    if (!segment) return
    playSegment(segment, (event.offsetMs - segment.startedOffsetMs) / 1000)
  }

  if (loading) return <div><Card>正在加载面试回顾…</Card></div>
  if (!interview) return <div><Card><p className="text-rose-700">{error || '面试不存在或无权访问。'}</p><Button type="button" variant="secondary" className="mt-5 h-9 px-4" onClick={() => navigate('/admin/interviews')}>返回面试管理</Button></Card></div>

  return <div className="space-y-6">
    <header className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div><Button type="button" variant="ghost" className="-ml-3 h-9 px-3 text-sm text-muted-foreground hover:text-foreground" onClick={() => navigate('/admin/interviews')}><ArrowLeft className="h-4 w-4" />返回面试管理</Button><p className="mt-4 text-sm font-semibold text-[var(--accent)]">面试回顾</p><h1 className="mt-1 text-3xl font-bold tracking-tight">{interview.title}</h1><p className="mt-2 text-muted-foreground">查看题目、作答与音视频时间轴。</p></div>
      <div className="flex gap-2"><Badge tone={interviewStatusTone(interview.status)}>{interviewStatusText[interview.status] ?? '未知状态'}</Badge>{canViewReport(interview.status) && <Button onClick={() => navigate(`/admin/interviews?reportInterviewId=${interview.id}`)}><FileChartColumn className="h-4 w-4" />查看评测报告</Button>}</div>
    </header>

    <Card className="grid gap-4 sm:grid-cols-4">
      <div className="flex items-center gap-3"><span className="grid h-10 w-10 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><Calendar className="h-4 w-4" /></span><div><p className="text-xs text-muted-foreground">预约时间</p><strong className="text-sm">{interview.scheduledAt.replace('T', ' ').slice(0, 16)}</strong></div></div>
      <div className="flex items-center gap-3"><span className="grid h-10 w-10 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><ClipboardList className="h-4 w-4" /></span><div><p className="text-xs text-muted-foreground">面试题目</p><strong className="text-sm">{questions.length} 道</strong></div></div>
      <div className="flex items-center gap-3"><span className="grid h-10 w-10 place-items-center rounded-xl bg-sky-50 text-sky-700"><MessageSquareText className="h-4 w-4" /></span><div><p className="text-xs text-muted-foreground">已提交作答</p><strong className="text-sm">{answers.filter(item => item.answerContent || item.answerData).length} 份</strong></div></div>
      <div className="flex items-center gap-3"><span className="grid h-10 w-10 place-items-center rounded-xl bg-emerald-50 text-emerald-700">{recording?.mode === 'VIDEO' ? <Video className="h-4 w-4" /> : <Headphones className="h-4 w-4" />}</span><div><p className="text-xs text-muted-foreground">面试方式</p><strong className="text-sm">{recording?.mode === 'VIDEO' ? '视频面试' : recording?.mode === 'AUDIO' ? '语音面试' : recording?.mode === 'TEXT' ? '文字面试' : '未记录'}</strong></div></div>
    </Card>

    {error && <p role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}

    {recording && recording.mode !== 'TEXT' && <Card className="p-0">
      <div className="border-b border-border p-5"><div className="flex items-center gap-3"><span className="grid h-10 w-10 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]">{recording.mode === 'VIDEO' ? <Video className="h-5 w-5" /> : <Headphones className="h-5 w-5" />}</span><div><h2 className="font-bold">题目回放与时间轴</h2><p className="mt-1 text-sm text-muted-foreground">点击题目加载录制；点击时间轴事件可定位到对应回答或追问时刻。</p></div></div></div>
      <div className="grid gap-0 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="min-h-72 bg-black p-4">{mediaLoading ? <div className="grid min-h-64 place-items-center text-sm text-white/70">正在加载受保护媒体…</div> : mediaUrl ? recording.mode === 'VIDEO' ? <video ref={videoPlayer} src={mediaUrl} controls playsInline onLoadedMetadata={onMediaReady} className="mx-auto max-h-[520px] w-full rounded-xl bg-black" /> : <div className="grid min-h-64 place-items-center"><audio ref={audioPlayer} src={mediaUrl} controls onLoadedMetadata={onMediaReady} className="w-full max-w-xl" /></div> : <div className="grid min-h-64 place-items-center text-sm text-white/70">暂无可播放分段</div>}</div>
        <div className="max-h-[552px] overflow-y-auto border-t border-border p-4 lg:border-l lg:border-t-0"><h3 className="flex items-center gap-2 text-sm font-bold"><Clock3 className="h-4 w-4" />当前题目时间轴</h3>{selectedQuestionSegments.length > 1 && <div className="mt-3 flex flex-wrap gap-2">{selectedQuestionSegments.map((segment, index) => <button key={segment.id} type="button" aria-pressed={selectedSegment?.id === segment.id} onClick={() => playSegment(segment)} className={'min-h-11 rounded-full px-3 py-1 text-xs font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] ' + (selectedSegment?.id === segment.id ? 'bg-[var(--accent)] text-white' : 'bg-muted text-muted-foreground hover:text-foreground')}>分段 {index + 1}</button>)}</div>}<div className="mt-4 space-y-3">{selectedEvents.map(event => <button type="button" key={event.id} onClick={() => playTimelineEvent(event)} disabled={!selectedQuestionSegments.length} className="w-full rounded-2xl bg-muted/70 p-3 text-left transition hover:bg-[var(--accent-soft)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] disabled:cursor-default disabled:opacity-50"><div className="flex items-center justify-between gap-2"><strong className="text-xs">{eventLabels[event.eventType] ?? event.eventType}</strong><span className="font-mono text-xs text-muted-foreground">{offsetText(event.offsetMs)}</span></div>{event.content && <p className="mt-2 text-xs leading-5 text-muted-foreground">{event.content}</p>}</button>)}{!selectedEvents.length && <p className="py-8 text-center text-xs text-muted-foreground">请选择包含录制的题目。</p>}</div></div>
      </div>
    </Card>}

    <section className="space-y-4">{questions.map((question, index) => {
      const questionId = String(question.interviewQuestionId)
      const answer = answersByQuestion.get(questionId)
      const segments = segmentsByQuestion.get(questionId) ?? []
      const selected = selectedQuestionId === questionId
      const parsedOptions = parseQuestionOptions(question.options)
      const choiceQuestion = ['single_choice', 'multiple_choice', 'true_false'].includes(question.questionType)
      return <Card key={question.interviewQuestionId} className={'p-0 transition ' + (selected ? 'ring-2 ring-[var(--accent)]' : '')}>
        <div className="flex flex-col items-start gap-4 border-b border-border p-4 sm:flex-row sm:p-5"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-[var(--accent-soft)] text-sm font-bold text-[var(--accent)]">{String(index + 1).padStart(2, '0')}</span><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><Badge tone="info">{questionTypeLabels[question.questionType] ?? question.questionType.replaceAll('_', ' ')}</Badge><span className="text-xs text-muted-foreground">{question.maxScore} 分</span>{segments.length > 0 && <Badge tone="success">{segments.length} 个录制分段</Badge>}</div><h2 className="mt-3 font-semibold leading-6">{question.content}</h2><QuestionOptions options={parsedOptions} /></div>{segments.length > 0 && <Button variant="secondary" className="w-full shrink-0 sm:w-auto" onClick={() => playSegment(segments[0])}><PlayCircle className="h-4 w-4" />播放本题</Button>}</div>
        <div className="p-4 sm:p-5"><p className="text-xs font-semibold tracking-wide text-muted-foreground">候选人作答</p><CandidateAnswer answer={answer} options={parsedOptions} choiceQuestion={choiceQuestion} />{answer?.evaluation && <div className="mt-4 rounded-xl bg-muted/70 p-4"><p className="text-xs font-semibold text-[var(--accent)]">AI 评价</p><p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-muted-foreground">{answer.evaluation}</p></div>}</div>
      </Card>
    })}{!questions.length && <Card><p className="text-center text-sm text-muted-foreground">该面试尚未关联题目。</p></Card>}</section>
  </div>
}
