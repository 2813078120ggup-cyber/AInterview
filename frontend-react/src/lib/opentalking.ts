const fallbackIceServers: RTCIceServer[] = [{ urls: 'stun:stun.l.google.com:19302' }]

export type OpenTalkingConfig = {
  endpoint: string
  model: string
  avatarId: string
  ttsProvider: string
  ttsVoice: string
  sttProvider?: string
  agentEnabled?: boolean
}

export type OpenTalkingEventHandlers = {
  onStatus?: (status: string) => void
  onError?: (message: string) => void
  onSpeechStarted?: () => void
  onSpeechEnded?: () => void
}

export type OpenTalkingRuntime = {
  sessionId: string
  pc: RTCPeerConnection
  events: EventSource
  video: HTMLVideoElement
  config: OpenTalkingConfig
  speechRevision: number
  speechQueue: Promise<void>
}

type IceConfig = { iceServers?: RTCIceServer[]; iceTransportPolicy?: RTCIceTransportPolicy }
type SessionResponse = { session_id: string; status: string }
type WebRtcAnswer = { sdp: string; type: RTCSdpType }
type TranscribeResponse = { session_id: string; text: string }

function apiUrl(endpoint: string, path: string) {
  return endpoint.replace(/\/+$/, '') + path
}

async function apiRequest<T>(url: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers },
  })
  const body = await response.json().catch(() => ({})) as T & { detail?: string; message?: string }
  if (!response.ok) throw new Error(body.detail || body.message || `OpenTalking request failed (HTTP ${response.status})`)
  return body
}

async function apiFormRequest<T>(url: string, form: FormData): Promise<T> {
  const response = await fetch(url, { method: 'POST', body: form })
  const body = await response.json().catch(() => ({})) as T & { detail?: string; message?: string }
  if (!response.ok) throw new Error(body.detail || body.message || `OpenTalking request failed (HTTP ${response.status})`)
  return body
}

async function iceConfig(endpoint: string): Promise<IceConfig> {
  try {
    const config = await apiRequest<IceConfig>(apiUrl(endpoint, '/sessions/webrtc/ice-config'))
    return {
      iceServers: Array.isArray(config.iceServers) && config.iceServers.length ? config.iceServers : fallbackIceServers,
      iceTransportPolicy: config.iceTransportPolicy === 'relay' || config.iceTransportPolicy === 'all' ? config.iceTransportPolicy : undefined,
    }
  } catch {
    return { iceServers: fallbackIceServers }
  }
}

async function waitForIceGathering(pc: RTCPeerConnection, timeoutMs = 8000) {
  if (pc.iceGatheringState === 'complete') return
  await new Promise<void>(resolve => {
    const done = () => {
      window.clearTimeout(timeout)
      pc.removeEventListener('icegatheringstatechange', onStateChange)
      resolve()
    }
    const onStateChange = () => { if (pc.iceGatheringState === 'complete') done() }
    const timeout = window.setTimeout(done, timeoutMs)
    pc.addEventListener('icegatheringstatechange', onStateChange)
  })
}

function requestPlayback(video: HTMLVideoElement) {
  video.autoplay = true
  video.playsInline = true
  const play = () => {
    void video.play().catch(() => {
      video.muted = true
      void video.play().catch(() => undefined)
    })
  }
  play()
  return play
}

function eventData(event: MessageEvent) {
  try { return JSON.parse(event.data as string) as Record<string, unknown> } catch { return { message: String(event.data ?? '') } }
}

function eventMessage(data: Record<string, unknown>) {
  return String(data.detail ?? data.message ?? data.error ?? '')
}

function stateFromEvent(data: Record<string, unknown>) {
  return String(data.state ?? data.status ?? data.current_state ?? '')
}

function enqueueSpeech<T>(runtime: OpenTalkingRuntime, operation: () => Promise<T>) {
  const queued = runtime.speechQueue.then(operation)
  runtime.speechQueue = queued.then(() => undefined, () => undefined)
  return queued
}

function requestInterrupt(runtime: OpenTalkingRuntime) {
  return apiRequest(apiUrl(runtime.config.endpoint, `/sessions/${runtime.sessionId}/interrupt`), {
    method: 'POST',
    body: '{}',
  })
}

export async function startOpenTalking(
  config: OpenTalkingConfig,
  video: HTMLVideoElement,
  handlers: OpenTalkingEventHandlers = {},
): Promise<OpenTalkingRuntime> {
  const created = await apiRequest<SessionResponse>(apiUrl(config.endpoint, '/sessions'), {
    method: 'POST',
    body: JSON.stringify({
      avatar_id: config.avatarId,
      model: config.model,
      tts_provider: config.ttsProvider,
      tts_voice: config.ttsVoice,
      stt_provider: config.sttProvider || 'dashscope',
      // AInterview owns every question and follow-up. OpenTalking is media-only.
      agent_enabled: config.agentEnabled ?? false,
    }),
  })
  const events = new EventSource(apiUrl(config.endpoint, `/sessions/${created.session_id}/events`))
  const handleState = (event: MessageEvent) => handlers.onStatus?.(stateFromEvent(eventData(event)))
  const handleError = (event: MessageEvent) => {
    const message = eventMessage(eventData(event))
    if (message) handlers.onError?.(message)
  }
  for (const name of ['session.state_changed', 'status', 'speech.started', 'speech.ended']) events.addEventListener(name, handleState as EventListener)
  events.addEventListener('speech.started', () => handlers.onSpeechStarted?.())
  events.addEventListener('speech.ended', () => handlers.onSpeechEnded?.())
  events.addEventListener('error', handleError as EventListener)

  const rtc = await iceConfig(config.endpoint)
  const pc = new RTCPeerConnection(rtc)
  const remoteStream = new MediaStream()
  video.srcObject = remoteStream
  const play = requestPlayback(video)
  pc.ontrack = event => {
    if (!remoteStream.getTracks().some(track => track.id === event.track.id)) remoteStream.addTrack(event.track)
    play()
  }
  pc.addTransceiver('video', { direction: 'recvonly' })
  pc.addTransceiver('audio', { direction: 'recvonly' })
  const offer = await pc.createOffer()
  await pc.setLocalDescription(offer)
  await waitForIceGathering(pc)
  const answer = await apiRequest<WebRtcAnswer>(apiUrl(config.endpoint, `/sessions/${created.session_id}/webrtc/offer`), {
    method: 'POST',
    body: JSON.stringify({ sdp: pc.localDescription?.sdp ?? '', type: pc.localDescription?.type ?? 'offer' }),
  })
  await pc.setRemoteDescription(answer)
  await apiRequest(apiUrl(config.endpoint, `/sessions/${created.session_id}/start`), { method: 'POST', body: '{}' })
  return {
    sessionId: created.session_id,
    pc,
    events,
    video,
    config,
    speechRevision: 0,
    speechQueue: Promise.resolve(),
  }
}

export async function speakOpenTalking(
  runtime: OpenTalkingRuntime,
  text: string,
  options: { readOnly?: boolean; interrupt?: boolean } = {},
) {
  const normalized = text.trim()
  if (!normalized) return false
  const revision = ++runtime.speechRevision
  return enqueueSpeech(runtime, async () => {
    if (revision !== runtime.speechRevision) return false
    if (options.interrupt !== false) await requestInterrupt(runtime)
    if (revision !== runtime.speechRevision) return false
    const readOnly = options.readOnly ?? true
    const speakText = readOnly ? `【只朗读模式】${normalized}` : normalized
    await apiRequest(apiUrl(runtime.config.endpoint, `/sessions/${runtime.sessionId}/speak`), {
      method: 'POST',
      body: JSON.stringify({ text: speakText, tts_provider: runtime.config.ttsProvider, voice: runtime.config.ttsVoice }),
    })
    return revision === runtime.speechRevision
  })
}

export async function transcribeOpenTalking(runtime: OpenTalkingRuntime, audio: Blob) {
  const form = new FormData()
  form.append('file', audio, 'answer.webm')
  form.append('stt_provider', runtime.config.sttProvider || 'dashscope')
  const result = await apiFormRequest<TranscribeResponse>(
    apiUrl(runtime.config.endpoint, `/sessions/${runtime.sessionId}/transcribe`),
    form,
  )
  return result.text.trim()
}

export async function interruptOpenTalking(runtime: OpenTalkingRuntime) {
  const revision = ++runtime.speechRevision
  await enqueueSpeech(runtime, async () => {
    await requestInterrupt(runtime)
    return revision === runtime.speechRevision
  })
}

export function closeOpenTalking(runtime: OpenTalkingRuntime) {
  runtime.speechRevision += 1
  runtime.events.close()
  runtime.pc.getSenders().forEach(sender => sender.track?.stop())
  runtime.pc.close()
  runtime.video.pause()
  runtime.video.srcObject = null
  void fetch(apiUrl(runtime.config.endpoint, `/sessions/${runtime.sessionId}`), { method: 'DELETE', keepalive: true })
}
