import { useEffect, useState } from 'react'
import { BrainCircuit, CheckCircle2, Mic, Play, Sparkles, Video } from 'lucide-react'
import { useReducedMotion } from 'framer-motion'
import { ProductWindow, StatusBadge } from '../components'
import { mockInterview } from '../mock-data'

const phaseLabels = ['问题', '作答', '回答', '思考', '追问', '评估']
const phaseDurations = [2200, 1800, 2200, 1700, 2200, 1800]

export function InterviewMockup() {
  const reduceMotion = useReducedMotion()
  const [step, setStep] = useState(0)

  useEffect(() => {
    if (reduceMotion) {
      setStep(5)
      return
    }
    let cancelled = false
    let timer = 0
    const schedule = (currentStep: number) => {
      timer = window.setTimeout(() => {
        if (cancelled) return
        const nextStep = (currentStep + 1) % phaseLabels.length
        setStep(nextStep)
        schedule(nextStep)
      }, phaseDurations[currentStep])
    }
    setStep(0)
    schedule(0)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [reduceMotion])

  const recording = step === 1 || step === 2
  const thinking = step === 3
  const followUp = step >= 4

  return <ProductWindow title="岗位定向智能面试" subtitle="面试空间 · Java 后端" activeTab="面试">
    <div className="interview-mockup">
      <div className="interview-avatar-stage">
        <div className="interview-avatar-orbit" aria-hidden="true"><span /><span /><span /></div>
        <div className="interview-avatar-core"><BrainCircuit size={34} /></div>
        <div className="interview-avatar-caption"><strong>智能面试官</strong><span>按岗位要求组织提问</span></div>
        <div className="interview-avatar-status"><span className="product-window-live-dot" />{thinking ? '回答处理中' : followUp ? '后续问题已生成' : '等待候选人作答'}</div>
      </div>
      <div className="interview-panel">
        <div className="interview-panel-top"><span className="mock-kicker">{mockInterview.round}</span><div className="interview-mode"><Video size={14} />视频面试</div></div>
        <div className="interview-progress"><span style={{ width: `${Math.max(40, (step + 1) / phaseLabels.length * 100)}%` }} /></div>
        <div className="interview-question-block" aria-live="polite">
          <span className="mock-kicker">{followUp ? '当前追问' : '当前问题'}</span>
          <div className="mock-title">{followUp ? mockInterview.followUp : mockInterview.question}</div>
        </div>
        <div className={`interview-answer-block${thinking ? ' is-thinking' : ''}`}>
          {thinking ? <div className="thinking-line"><Sparkles size={16} />回答分析中<span className="thinking-dots">…</span></div> : <><span className="mock-kicker">候选人回答</span><p>{step >= 2 ? mockInterview.answer : '等待候选人作答…'}</p></>}
        </div>
        <div className="interview-footer-status">
          {recording && <StatusBadge tone="danger"><span className="recording-dot" />正在回答</StatusBadge>}
          {thinking && <StatusBadge tone="info"><Sparkles size={13} />正在分析</StatusBadge>}
          {followUp && <StatusBadge tone="success"><CheckCircle2 size={13} />动态追问</StatusBadge>}
          {!recording && !thinking && !followUp && <StatusBadge tone="default"><Play size={12} />等待回答</StatusBadge>}
          <span className="interview-timer"><Mic size={14} />{recording ? '00:18' : '00:24'}</span>
        </div>
      </div>
    </div>
  </ProductWindow>
}
