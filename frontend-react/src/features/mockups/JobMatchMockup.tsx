import { useEffect, useState } from 'react'
import { BrainCircuit, Check, FileText, MapPin, ShieldAlert, Sparkles } from 'lucide-react'
import { useReducedMotion } from 'framer-motion'
import { ProductWindow, ScoreRing, StatusBadge } from '../components'
import { mockCandidate, mockJob, mockMatch } from '../mock-data'

function SkillTag({ children, muted = false }: { children: string; muted?: boolean }) {
  return <span className={`mock-skill-tag${muted ? ' is-muted' : ''}`}>{children}</span>
}

export function JobMatchMockup() {
  const reduceMotion = useReducedMotion()
  const [score, setScore] = useState(42)

  useEffect(() => {
    if (reduceMotion) {
      setScore(mockMatch.score)
      return
    }
    setScore(42)
    const timer = window.setInterval(() => {
      setScore(current => {
        if (current >= mockMatch.score) {
          window.clearInterval(timer)
          return mockMatch.score
        }
        return Math.min(mockMatch.score, current + 2)
      })
    }, 42)
    return () => window.clearInterval(timer)
  }, [reduceMotion])

  return <ProductWindow title="岗位匹配评估" subtitle="招聘协同工作台" activeTab="岗位匹配">
    <div className="job-match-layout">
      <div className="job-match-column">
        <div className="mock-panel-heading"><span className="mock-icon"><FileText size={16} /></span><div><span className="mock-kicker">岗位描述</span><strong>{mockJob.title}</strong></div></div>
        <div className="job-meta"><span>{mockJob.salary}</span><span><MapPin size={13} />{mockJob.location}</span><span>{mockJob.experience}</span></div>
        <div className="mock-rule" />
        <span className="mock-caption">任职技能</span>
        <div className="mock-skill-list">{mockJob.skills.map(skill => <SkillTag key={skill}>{skill}</SkillTag>)}</div>
      </div>
      <div className="job-match-center">
        <div className="analyzing-label"><BrainCircuit size={15} />匹配任务处理中</div>
        <ScoreRing score={score} label="岗位匹配度" size={118} />
        <span className="match-note">基于岗位要求与简历证据</span>
      </div>
      <div className="job-match-column job-candidate-column">
        <div className="mock-panel-heading"><span className="candidate-avatar">张</span><div><span className="mock-kicker">候选人简历</span><strong>{mockCandidate.name}</strong></div><StatusBadge tone="success">已解析</StatusBadge></div>
        <div className="candidate-education"><span>{mockCandidate.school}</span><span>{mockCandidate.major}</span></div>
        <div className="mock-rule" />
        <span className="mock-caption">已找到的证据</span>
        <div className="mock-skill-list">{mockCandidate.skills.map(skill => <SkillTag key={skill}>{skill}</SkillTag>)}</div>
      </div>
    </div>
    <div className="job-match-insights">
      <div><span className="insight-label"><Check size={13} />匹配技能</span><p>{mockMatch.matchedSkills.map(skill => <span key={skill}>{skill}</span>)}</p></div>
      <div><span className="insight-label is-muted"><ShieldAlert size={13} />待核实差距</span><p>{mockMatch.missingSkills.map(skill => <span key={skill}>{skill}</span>)}</p></div>
      <div className="insight-risk"><span className="insight-label"><Sparkles size={13} />待核实风险</span><p>{mockMatch.risk}</p></div>
    </div>
  </ProductWindow>
}
