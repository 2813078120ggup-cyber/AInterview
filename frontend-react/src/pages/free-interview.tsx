import {
  ArrowLeft,
  Camera,
  CameraOff,
  FileUser,
  Loader2,
  MessageSquareQuote,
  Mic,
  RefreshCw,
  Send,
  Upload,
  Video,
  Volume2,
  VolumeX,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { browserSpeechRecognitionCtor, type BrowserSpeechRecognition, type SpeechRecognitionEventLike } from '@/lib/browser-speech'
import {
  closeOpenTalking,
  interruptOpenTalking,
  speakOpenTalking,
  startOpenTalking,
  transcribeOpenTalking,
  type OpenTalkingRuntime,
} from '@/lib/opentalking'

type Report = {
  totalScore: number
  professionalScore: number
  expressionScore: number
  logicScore: number
  adaptabilityScore: number
  summary: string
  strengths: string
  weaknesses: string
  improvementSuggestions: string
}

type Session = {
  id: string
  resumeFilename: string
  targetRole?: string
  resumeSummary: string
  status: string
  completedTurns: number
  openingPrompt: string
  report?: Report
  activeTaskId?: string
  activeTaskType?: string
  activeTaskStatus?: string
}

type Turn = { turnNo: number; question: string; answer: string; nextQuestion?: string }
type Detail = { session: Session; turns: Turn[] }
type Task = { id: string; status: string; outputPayload?: string; errorMessage?: string }
type TurnResult = { session: Session; nextQuestion?: string; taskId?: string }
type TaskResult = { session: Session; taskId?: string }
type Config = { enabled: boolean; protocol: string; endpoint: string; sceneId: string; avatarId: string; appId: string; vcn: string; message?: string }
type Message = { role: 'assistant' | 'candidate'; text: string }
type StartupStage = 'analyzing' | 'connecting' | 'recovering' | undefined
type ResumeSummary = { candidateProfile?: string; skills?: string[]; experienceHighlights?: string[]; riskPoints?: string[] }

const apiBase = import.meta.env.VITE_API_BASE_URL ?? '/api'
const activeSessionKey = 'free_interview_active_session_id'

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : '操作失败，请稍后重试。'
}

function lines(value: string) {
  return value.split('\n').map(item => item.trim()).filter(Boolean)
}

function parseSummary(value: string): ResumeSummary {
  try { return JSON.parse(value) as ResumeSummary } catch { return { candidateProfile: value } }
}

export function FreeInterview() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const requestedSessionId = searchParams.get('sessionId')
  const avatarVideo = useRef<HTMLVideoElement>(null)
  const cameraVideo = useRef<HTMLVideoElement>(null)
  const runtime = useRef<OpenTalkingRuntime | null>(null)
  const cameraStream = useRef<MediaStream | null>(null)
  const recorder = useRef<MediaRecorder | null>(null)
  const recorderStream = useRef<MediaStream | null>(null)
  const audioChunks = useRef<BlobPart[]>([])
  const browserRecognition = useRef<BrowserSpeechRecognition | null>(null)
  const messagesEnd = useRef<HTMLDivElement>(null)
  const recoverySessionId = useRef<string | null>(null)
  const pendingSubmission = useRef<{ id: string; question: string; answer: string } | null>(null)

  const [file, setFile] = useState<File>()
  const [targetRole, setTargetRole] = useState('Java 后端开发工程师')
  const [session, setSession] = useState<Session>()
  const [messages, setMessages] = useState<Message[]>([])
  const [answer, setAnswer] = useState('')
  const [creating, setCreating] = useState(false)
  const [thinking, setThinking] = useState(false)
  const [reporting, setReporting] = useState(false)
  const [startupStage, setStartupStage] = useState<StartupStage>()
  const [virtualReady, setVirtualReady] = useState(false)
  const [virtualMessage, setVirtualMessage] = useState('待连接')
  const [ttsEnabled, setTtsEnabled] = useState(true)
  const [cameraOn, setCameraOn] = useState(false)
  const [listening, setListening] = useState(false)
  const [transcribing, setTranscribing] = useState(false)
  const [error, setError] = useState('')

  const summary = useMemo(() => parseSummary(session?.resumeSummary ?? ''), [session?.resumeSummary])
  const currentQuestion = messages.filter(item => item.role === 'assistant').at(-1)?.text ?? ''
  const currentTurn = Math.min((session?.completedTurns ?? 0) + 1, 10)

  useEffect(() => {
    messagesEnd.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [messages, thinking])

  useEffect(() => () => {
    if (runtime.current) closeOpenTalking(runtime.current)
    stopRecording(false)
    cameraStream.current?.getTracks().forEach(track => track.stop())
    browserRecognition.current?.abort?.()
  }, [])

  useEffect(() => {
    const activeId = requestedSessionId || localStorage.getItem(activeSessionKey)
    if (!activeId || recoverySessionId.current === activeId) return
    recoverySessionId.current = activeId
    void recover(activeId)
  }, [requestedSessionId])

  async function api<T>(path: string, init: RequestInit = {}) {
    const token = localStorage.getItem('access_token')
    const response = await fetch(`${apiBase}${path}`, {
      ...init,
      headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init.headers },
    })
    const body = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(body.message ?? '请求失败，请稍后重试。')
    return body.data as T
  }

  function detailMessages(detail: Detail) {
    const restored: Message[] = []
    if (detail.session.openingPrompt) restored.push({ role: 'assistant', text: detail.session.openingPrompt })
    detail.turns.forEach(turn => {
      restored.push({ role: 'candidate', text: turn.answer })
      if (turn.nextQuestion) restored.push({ role: 'assistant', text: turn.nextQuestion })
    })
    return restored
  }

  async function waitTask(taskId: string, timeoutMs = 180000) {
    const deadline = Date.now() + timeoutMs
    while (Date.now() < deadline) {
      const task = await api<Task>(`/v1/ai-tasks/${taskId}`)
      if (task.status === 'SUCCESS') return task
      if (task.status === 'FAILED') throw new Error(task.errorMessage || 'AI 任务执行失败，请稍后重试。')
      await new Promise(resolve => window.setTimeout(resolve, 1000))
    }
    throw new Error('任务仍在后台处理中。稍后重新打开本页面会自动恢复进度。')
  }

  async function activateDetail(detail: Detail, speak = false) {
    const sessionId = String(detail.session.id)
    recoverySessionId.current = sessionId
    localStorage.setItem(activeSessionKey, sessionId)
    if (requestedSessionId !== sessionId) {
      navigate(`/candidate/free-interview?sessionId=${sessionId}`, { replace: true })
    }
    const restored = detailMessages(detail)
    flushSync(() => {
      setSession(detail.session)
      setMessages(restored)
    })
    if (detail.session.status !== 'INTERVIEWING' || detail.session.report || runtime.current) return
    setStartupStage('connecting')
    try {
      await connectAvatar()
      if (speak) readQuestion(restored.filter(item => item.role === 'assistant').at(-1)?.text ?? '')
    } catch (reason) {
      setVirtualReady(false)
      setVirtualMessage('虚拟人未连接，可继续文字面试')
      setError(`虚拟人连接失败：${errorMessage(reason)} 可继续使用文字完成面试。`)
    } finally {
      setStartupStage(undefined)
    }
  }

  async function recover(id: string) {
    setCreating(true)
    setStartupStage('recovering')
    setError('')
    let detailLoaded = false
    try {
      let detail = await api<Detail>(`/v1/free-interviews/${id}`)
      detailLoaded = true
      await activateDetail(detail)
      if (detail.session.activeTaskId && ['PENDING', 'RUNNING'].includes(detail.session.activeTaskStatus ?? '')) {
        setReporting(detail.session.activeTaskType === 'FREE_REPORT')
        setThinking(detail.session.activeTaskType === 'FREE_FOLLOW_UP')
        await waitTask(detail.session.activeTaskId)
        detail = await api<Detail>(`/v1/free-interviews/${id}`)
        await activateDetail(detail)
      }
      if (detail.session.status === 'FAILED') {
        setError(detail.session.completedTurns >= 10 ? '报告生成失败，答题记录已保存，可以重新生成报告。' : '简历分析失败，请重新上传简历。')
      }
    } catch (reason) {
      if (!detailLoaded) {
        localStorage.removeItem(activeSessionKey)
        recoverySessionId.current = null
        if (requestedSessionId) navigate('/candidate/free-interview', { replace: true })
        setSession(undefined)
        setMessages([])
      }
      setError(errorMessage(reason))
    } finally {
      setCreating(false)
      setThinking(false)
      setReporting(false)
      setStartupStage(undefined)
    }
  }

  async function waitForAvatarVideo(timeoutMs = 3000) {
    if (avatarVideo.current) return
    await new Promise<void>((resolve, reject) => {
      const deadline = Date.now() + timeoutMs
      const poll = () => {
        if (avatarVideo.current) return resolve()
        if (Date.now() >= deadline) return reject(new Error('虚拟人视频区域尚未准备完成'))
        window.requestAnimationFrame(poll)
      }
      window.requestAnimationFrame(poll)
    })
  }

  async function connectAvatar() {
    await waitForAvatarVideo()
    if (!avatarVideo.current) throw new Error('虚拟人视频区域尚未准备完成')
    const config = await api<Config>('/v1/virtual-human/sdk-config')
    if (!config.enabled || config.protocol !== 'opentalking') {
      throw new Error(config.message || '虚拟人服务尚未配置，请联系管理员。')
    }
    runtime.current = await startOpenTalking({
      endpoint: config.endpoint,
      model: config.sceneId,
      avatarId: config.avatarId,
      ttsProvider: config.appId || 'dashscope',
      ttsVoice: config.vcn,
      sttProvider: 'dashscope',
      agentEnabled: false,
    }, avatarVideo.current, {
      onSpeechStarted: () => setVirtualMessage('正在朗读'),
      onSpeechEnded: () => setVirtualMessage('等待回答'),
      onError: message => setVirtualMessage(message || '播报异常'),
    })
    setVirtualReady(true)
    setVirtualMessage('等待回答')
    return runtime.current
  }

  function readQuestion(text: string) {
    const activeRuntime = runtime.current
    if (!ttsEnabled || !activeRuntime || !text.trim()) return
    setVirtualMessage('正在准备朗读')
    void speakOpenTalking(activeRuntime, text, { readOnly: true }).catch(reason => {
      setVirtualMessage('朗读失败，可继续文字面试')
      setError(`虚拟人朗读失败：${errorMessage(reason)}`)
    })
  }

  async function create() {
    if (!file) { setError('请先上传 PDF、DOCX、TXT 或 Markdown 格式的简历。'); return }
    setCreating(true)
    setStartupStage('analyzing')
    setError('')
    try {
      const form = new FormData()
      form.append('resume', file)
      form.append('targetRole', targetRole.trim())
      const created = await api<Session>('/v1/free-interviews', { method: 'POST', body: form })
      const createdId = String(created.id)
      recoverySessionId.current = createdId
      localStorage.setItem(activeSessionKey, createdId)
      navigate(`/candidate/free-interview?sessionId=${createdId}`, { replace: true })
      if (!created.activeTaskId) throw new Error('简历分析任务创建失败，请稍后重试。')
      await waitTask(created.activeTaskId)
      const detail = await api<Detail>(`/v1/free-interviews/${created.id}`)
      await activateDetail(detail, true)
    } catch (reason) {
      setSession(undefined)
      setMessages([])
      setError(errorMessage(reason))
    } finally {
      setStartupStage(undefined)
      setCreating(false)
    }
  }

  async function submit() {
    const currentAnswer = answer.trim()
    if (!session || !currentQuestion || !currentAnswer || thinking || reporting) return
    if (runtime.current) {
      try {
        await interruptOpenTalking(runtime.current)
      } catch {
        // The next exact-text speak request retries the interrupt before playback.
      }
    }
    const previousMessages = messages
    const submission = pendingSubmission.current?.question === currentQuestion && pendingSubmission.current.answer === currentAnswer
      ? pendingSubmission.current
      : { id: crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`, question: currentQuestion, answer: currentAnswer }
    pendingSubmission.current = submission
    let saved = false
    setMessages(items => [...items, { role: 'candidate', text: currentAnswer }])
    setAnswer('')
    setThinking(true)
    setError('')
    try {
      const result = await api<TurnResult>(`/v1/free-interviews/${session.id}/turns`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ submissionId: submission.id, question: currentQuestion, answer: currentAnswer }),
      })
      saved = true
      pendingSubmission.current = null
      setSession(result.session)
      if (result.session.completedTurns === 10) {
        setReporting(true)
      }
      if (result.taskId) {
        await waitTask(result.taskId)
      }
      const detail = await api<Detail>(`/v1/free-interviews/${session.id}`)
      setSession(detail.session)
      setMessages(detailMessages(detail))
      const nextQuestion = detail.turns.at(-1)?.nextQuestion
      if (nextQuestion) readQuestion(nextQuestion)
    } catch (reason) {
      if (!saved) {
        setMessages(previousMessages)
        setAnswer(currentAnswer)
        setError(`${errorMessage(reason)}。再次提交会使用同一请求编号，不会重复保存。`)
      } else {
        try {
          const detail = await api<Detail>(`/v1/free-interviews/${session.id}`)
          setSession(detail.session)
          setMessages(detailMessages(detail))
        } catch {
          // Keep the optimistic answer visible when recovery is temporarily unavailable.
        }
        setError(`回答已经保存，${errorMessage(reason)}`)
      }
    } finally {
      setThinking(false)
      setReporting(false)
    }
  }

  async function retryReport() {
    if (!session || reporting) return
    setReporting(true)
    setError('')
    try {
      const result = await api<TaskResult>(`/v1/free-interviews/${session.id}/report`, { method: 'POST' })
      if (result.taskId) await waitTask(result.taskId)
      const detail = await api<Detail>(`/v1/free-interviews/${session.id}`)
      setSession(detail.session)
      setMessages(detailMessages(detail))
    } catch (reason) {
      setError(errorMessage(reason))
    } finally {
      setReporting(false)
    }
  }

  function stopRecording(upload: boolean) {
    const activeRecorder = recorder.current
    if (activeRecorder && activeRecorder.state !== 'inactive') {
      if (!upload) activeRecorder.onstop = null
      activeRecorder.stop()
    }
    if (!upload) {
      recorderStream.current?.getTracks().forEach(track => track.stop())
      recorderStream.current = null
      recorder.current = null
      audioChunks.current = []
      setListening(false)
    }
  }

  function startBrowserRecognition() {
    const Recognition = browserSpeechRecognitionCtor()
    if (!Recognition) throw new Error('当前浏览器不支持语音识别，请使用最新版 Chrome 或 Edge。')
    const recognition = new Recognition()
    browserRecognition.current = recognition
    recognition.lang = 'zh-CN'
    recognition.interimResults = false
    recognition.continuous = false
    recognition.onresult = (event: SpeechRecognitionEventLike) => {
      const text = Array.from(event.results).map(result => result[0]?.transcript ?? '').join('').trim()
      if (text) setAnswer(previous => (previous + (previous ? '\n' : '') + text).trim())
    }
    recognition.onerror = () => setError('浏览器语音识别失败，请检查麦克风权限')
    recognition.onend = () => { setListening(false); browserRecognition.current = null }
    recognition.start()
    setListening(true)
  }

  async function toggleVoiceAnswer() {
    if (listening) {
      if (recorder.current) {
        setTranscribing(true)
        stopRecording(true)
      } else {
        browserRecognition.current?.stop?.()
        setListening(false)
      }
      return
    }
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      setError('语音回答只能在 HTTPS 或 localhost 的最新版浏览器中使用')
      return
    }
    if (!runtime.current || typeof MediaRecorder === 'undefined') {
      try { startBrowserRecognition(); setError('') } catch (reason) { setError(errorMessage(reason)) }
      return
    }
    try {
      audioChunks.current = []
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false })
      recorderStream.current = stream
      const options = MediaRecorder.isTypeSupported('audio/webm') ? { mimeType: 'audio/webm' } : undefined
      const activeRecorder = new MediaRecorder(stream, options)
      recorder.current = activeRecorder
      activeRecorder.ondataavailable = event => { if (event.data.size > 0) audioChunks.current.push(event.data) }
      activeRecorder.onstop = () => {
        const chunks = audioChunks.current
        audioChunks.current = []
        recorderStream.current?.getTracks().forEach(track => track.stop())
        recorderStream.current = null
        recorder.current = null
        setListening(false)
        void (async () => {
          try {
            if (!chunks.length || !runtime.current) return
            const text = await transcribeOpenTalking(runtime.current, new Blob(chunks, { type: 'audio/webm' }))
            if (!text) throw new Error('未识别到有效语音，请靠近麦克风后重试')
            setAnswer(previous => (previous + (previous ? '\n' : '') + text).trim())
          } catch (reason) {
            setError(errorMessage(reason))
          } finally {
            setTranscribing(false)
          }
        })()
      }
      activeRecorder.start()
      setListening(true)
      setError('')
    } catch (reason) {
      stopRecording(false)
      setError(`录音启动失败：${errorMessage(reason)}`)
    }
  }

  async function toggleCamera() {
    if (cameraOn) {
      cameraStream.current?.getTracks().forEach(track => track.stop())
      cameraStream.current = null
      if (cameraVideo.current) cameraVideo.current.srcObject = null
      setCameraOn(false)
      return
    }
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      setError('摄像头只能在 HTTPS 或 localhost 的最新版浏览器中使用')
      return
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false })
      cameraStream.current = stream
      if (cameraVideo.current) cameraVideo.current.srcObject = stream
      setCameraOn(true)
      setError('')
    } catch {
      setError('未获得摄像头权限，请在浏览器地址栏中允许访问')
    }
  }

  function returnHome() {
    if (runtime.current) closeOpenTalking(runtime.current)
    runtime.current = null
    cameraStream.current?.getTracks().forEach(track => track.stop())
    navigate('/workspace')
  }

  function restart() {
    if (runtime.current) closeOpenTalking(runtime.current)
    runtime.current = null
    cameraStream.current?.getTracks().forEach(track => track.stop())
    cameraStream.current = null
    setCameraOn(false)
    setVirtualReady(false)
    setVirtualMessage('待连接')
    localStorage.removeItem(activeSessionKey)
    recoverySessionId.current = null
    navigate('/candidate/free-interview', { replace: true })
    pendingSubmission.current = null
    setSession(undefined)
    setMessages([])
    setAnswer('')
    setError('')
  }

  if (session?.report) {
    return <FreeInterviewReport report={session.report} targetRole={session.targetRole} onRestart={restart} onHome={returnHome} />
  }

  const startupLabel = startupStage === 'analyzing'
    ? '正在分析简历…'
    : startupStage === 'recovering' ? '正在恢复面试…' : '正在创建面试…'

  if (!session) return <main className="mx-auto flex min-h-screen w-full max-w-2xl flex-col justify-center px-5 py-10 sm:px-8">
    <header className="mb-7">
      <Button variant="ghost" className="mb-6 -ml-3" onClick={returnHome}><ArrowLeft className="h-4 w-4" />返回工作台</Button>
      <p className="text-sm font-semibold text-[var(--accent)]">简历定向面试</p>
      <h1 className="mt-2 text-3xl font-bold">基于简历生成面试</h1>
      <p className="mt-2 max-w-xl text-muted-foreground">上传简历，生成 10 轮岗位相关面试。</p>
    </header>
    {error && <p role="alert" className="mb-4 rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}
    <Card>
      <div className="flex items-start gap-4"><span className="grid h-11 w-11 place-items-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)]"><FileUser /></span><div><h2 className="font-bold">上传简历</h2><p className="mt-1 text-sm text-muted-foreground">支持 PDF、DOCX、TXT、Markdown。</p></div></div>
      <label className="mt-6 flex min-h-36 cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed border-border bg-muted/40 p-4 text-sm transition hover:bg-muted"><Upload className="mb-2 h-5 w-5" />{file ? file.name : '选择简历文件'}<input className="hidden" type="file" accept=".pdf,.docx,.txt,.md,text/plain,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" onChange={event => setFile(event.target.files?.[0])} /></label>
      <label className="mt-5 block text-sm font-semibold">目标岗位<ResponsiveSelect
        ariaLabel="选择目标岗位"
        value={targetRole}
        onValueChange={setTargetRole}
        className="mt-2 w-full"
        options={[
          { value: "", label: "由系统根据简历推荐" },
          { value: "Java 后端开发工程师", label: "Java 后端开发工程师" },
          { value: "前端开发工程师", label: "前端开发工程师" },
          { value: "全栈开发工程师", label: "全栈开发工程师" },
          { value: "Python 开发工程师", label: "Python 开发工程师" },
          { value: "Go 开发工程师", label: "Go 开发工程师" },
          { value: "测试开发工程师", label: "测试开发工程师" },
          { value: "数据分析师", label: "数据分析师" },
          { value: "算法工程师", label: "算法工程师" },
          { value: "产品经理", label: "产品经理" },
          { value: "运营专员", label: "运营专员" },
        ]}
      /></label>
      <Button className="mt-6" disabled={creating} onClick={() => void create()}>{creating ? <><Loader2 className="h-4 w-4 animate-spin" />{startupLabel}</> : <><MessageSquareQuote className="h-4 w-4" />开始简历定向面试</>}</Button>
    </Card>
  </main>

  const interviewLocked = session.status !== 'INTERVIEWING' || session.completedTurns >= 10

  return <main className="mx-auto max-w-7xl space-y-5 px-4 py-5 sm:space-y-6 sm:px-5 sm:py-8">
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div><Button variant="ghost" className="mb-4 -ml-3" onClick={returnHome}><ArrowLeft className="h-4 w-4" />返回工作台</Button><p className="text-sm font-semibold text-[var(--accent)]">简历定向面试</p><h1 className="mt-2 text-3xl font-bold">岗位相关问答</h1></div>
      <div className="flex items-center gap-2"><Badge tone="info">第 {currentTurn}/10 轮</Badge><Badge tone={virtualReady ? 'success' : 'warning'}>{virtualReady ? '虚拟人已连接' : '文字模式可用'}</Badge></div>
    </header>
    {error && <p role="alert" className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">{error}</p>}
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
      <Card className="flex min-h-[520px] flex-col p-4 sm:min-h-[680px] sm:p-6">
        <div className="flex items-center justify-between border-b border-border pb-4"><div><p className="font-bold">AI 面试官</p><p className="mt-1 text-xs text-muted-foreground">{thinking ? '回答已保存，正在生成追问…' : reporting ? '答题已完成，正在生成评测报告…' : interviewLocked ? '本次答题已结束' : '等待回答'}</p></div><span className={'h-2.5 w-2.5 rounded-full ' + (thinking || reporting ? 'animate-pulse bg-amber-500' : 'bg-emerald-500')} /></div>
        <div className="my-5 flex flex-1 flex-col gap-3 overflow-y-auto pr-1">
          {messages.map((item, index) => <article key={`${item.role}-${index}`} className={'max-w-[88%] rounded-2xl px-4 py-3 text-sm leading-6 ' + (item.role === 'candidate' ? 'ml-auto bg-[var(--primary)] text-white' : 'bg-muted')}><strong className="mb-1 block text-xs">{item.role === 'candidate' ? '我' : '面试官'}</strong>{item.text}</article>)}
          {(thinking || reporting) && <p className="w-fit rounded-2xl bg-muted px-4 py-3 text-sm text-muted-foreground"><Loader2 className="mr-2 inline h-4 w-4 animate-spin" />{reporting ? '正在生成报告…' : '正在生成下一题…'}</p>}
          <div ref={messagesEnd} />
        </div>
        <div className="relative">
          <textarea value={answer} disabled={thinking || reporting || interviewLocked} onChange={event => setAnswer(event.target.value)} className="min-h-32 w-full resize-none rounded-2xl border border-border bg-background p-4 pr-14 text-sm outline-none focus:border-[var(--accent)]" placeholder={interviewLocked ? '本次答题已结束' : '输入回答，或点击麦克风进行语音转写。'} />
          <button type="button" onClick={() => void toggleVoiceAnswer()} disabled={thinking || reporting || interviewLocked || transcribing} className={'absolute bottom-3 right-3 grid h-10 w-10 place-items-center rounded-xl text-white ' + (listening ? 'bg-rose-500' : 'bg-[var(--primary)]')} title={listening ? '停止录音' : '语音回答'}>{transcribing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Mic className="h-4 w-4" />}</button>
        </div>
        <div className="mt-4 flex items-center justify-between gap-3"><p className="text-xs text-muted-foreground">{transcribing ? '正在转写…' : listening ? '正在录音，再次点击可停止。' : interviewLocked ? '所有回答均已保存' : '语音内容将转写至回答框。'}</p>{interviewLocked && !session.report ? <Button disabled={reporting} onClick={() => void retryReport()}>{reporting ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}重新生成报告</Button> : <Button disabled={thinking || reporting || interviewLocked || !answer.trim()} onClick={() => void submit()}>提交回答<Send className="h-4 w-4" /></Button>}</div>
      </Card>
      <div className="space-y-5">
        <Card className="overflow-hidden p-0">
          <div className="relative aspect-[4/5] min-h-[360px] overflow-hidden bg-muted sm:min-h-[430px]"><video ref={avatarVideo} autoPlay playsInline className="absolute inset-0 h-full w-full object-cover" />{!virtualReady && <div className="absolute inset-0 grid place-items-center"><div className="text-center text-muted-foreground"><Video className="mx-auto h-7 w-7" /><p className="mt-3 text-sm">文字面试已就绪</p></div></div>}<div className="absolute bottom-4 left-4 right-4 rounded-xl border border-white/50 bg-white/80 px-4 py-3 shadow-sm backdrop-blur-xl"><p className="font-bold text-[#251c18]">面试官</p><p className="mt-1 text-xs text-[#62534b]">{virtualMessage}</p></div></div>
          <div className="flex items-center justify-between p-4"><div><p className="text-sm font-semibold">虚拟人朗读</p><p className="mt-1 text-xs text-muted-foreground">不影响文字内容显示</p></div><button className="rounded-xl p-2 hover:bg-muted" onClick={() => setTtsEnabled(value => !value)} title={ttsEnabled ? '关闭朗读' : '开启朗读'} aria-label={ttsEnabled ? '关闭虚拟人朗读' : '开启虚拟人朗读'}>{ttsEnabled ? <Volume2 className="h-4 w-4 text-[var(--accent)]" /> : <VolumeX className="h-4 w-4" />}</button></div>
          <Button variant="secondary" className="mx-4 mb-4 h-9 px-3" disabled={!currentQuestion || !virtualReady} onClick={() => readQuestion(currentQuestion)}><Volume2 className="h-3.5 w-3.5" />重读当前问题</Button>
        </Card>
        <Card>
          <div className="flex items-center justify-between"><div><p className="font-semibold">摄像头</p><p className="mt-1 text-xs text-muted-foreground">仅本地预览</p></div><Button variant="secondary" className="h-9 px-3" onClick={() => void toggleCamera()}>{cameraOn ? <CameraOff className="h-4 w-4" /> : <Camera className="h-4 w-4" />}{cameraOn ? '关闭' : '开启'}</Button></div>
          <div className="relative mt-4 grid aspect-video place-items-center overflow-hidden rounded-xl bg-muted"><video ref={cameraVideo} autoPlay muted playsInline className={'h-full w-full object-cover -scale-x-100 ' + (cameraOn ? 'block' : 'hidden')} />{!cameraOn && <div className="text-center text-muted-foreground"><Camera className="mx-auto h-6 w-6" /><p className="mt-2 text-xs">未开启</p></div>}</div>
        </Card>
        <Card>
          <p className="font-semibold">简历要点</p>
          {summary.candidateProfile && <p className="mt-3 text-sm leading-6 text-muted-foreground">{summary.candidateProfile}</p>}
          {!!summary.skills?.length && <div className="mt-4 flex flex-wrap gap-2">{summary.skills.map(skill => <span key={skill} className="rounded-full bg-muted px-3 py-1 text-xs">{skill}</span>)}</div>}
        </Card>
      </div>
    </div>
  </main>
}

function FreeInterviewReport({ report, targetRole, onRestart, onHome }: { report: Report; targetRole?: string; onRestart: () => void; onHome: () => void }) {
  const scores = [['专业能力', report.professionalScore], ['表达沟通', report.expressionScore], ['逻辑分析', report.logicScore], ['应变能力', report.adaptabilityScore]]
  return <main className="mx-auto max-w-6xl space-y-5 px-4 py-5 sm:space-y-6 sm:px-5 sm:py-8">
    <header className="flex flex-wrap items-start justify-between gap-4"><div><Button variant="ghost" className="mb-5 -ml-3" onClick={onHome}><ArrowLeft className="h-4 w-4" />返回工作台</Button><p className="text-sm font-semibold text-[var(--accent)]">简历定向评测</p><h1 className="mt-2 text-3xl font-bold">面试评测报告</h1><p className="mt-2 text-muted-foreground">目标岗位：{targetRole || '未指定'}</p></div><Button variant="secondary" onClick={onRestart}>重新练习</Button></header>
    <section className="soft-emphasis-panel rounded-2xl p-5 sm:p-7"><p className="text-sm font-semibold">综合得分</p><strong className="mt-2 block text-5xl sm:text-6xl">{report.totalScore}</strong><p className="mt-5 max-w-3xl leading-7 text-white/85">{report.summary}</p></section>
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{scores.map(([label, score]) => <Card key={String(label)}><p className="text-sm text-muted-foreground">{label}</p><strong className="mt-4 block text-3xl">{score}</strong></Card>)}</div>
    <div className="grid gap-5 lg:grid-cols-3">{[['优势', report.strengths], ['待提升', report.weaknesses], ['提升建议', report.improvementSuggestions]].map(([title, content]) => <Card key={String(title)}><h2 className="font-bold">{title}</h2><ul className="mt-4 space-y-3 text-sm leading-6 text-muted-foreground">{lines(String(content)).map(item => <li key={item}>{item}</li>)}</ul></Card>)}</div>
  </main>
}
