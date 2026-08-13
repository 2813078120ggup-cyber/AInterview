import { AlertTriangle, CheckCircle2, FileSearch, Lightbulb, Search } from 'lucide-react'
import { ProductWindow, StatusBadge } from '../components'
import { mockCandidate, mockResumeAnalysis } from '../mock-data'

export function ResumeAnalysisMockup() {
  return <ProductWindow title="简历结构化分析" subtitle={`${mockCandidate.name} · 解析结果`} activeTab="简历分析">
    <div className="resume-analysis-layout">
      <aside className="resume-analysis-sidebar">
        <div className="resume-file-card"><FileSearch size={18} /><div><strong>{mockCandidate.name}_简历.pdf</strong><span>已完成解析</span></div></div>
        {['概览', '核心技能', '项目经历', '岗位优势', '风险点'].map((item, index) => <span key={item} className={`resume-nav-item${index === 0 ? ' is-active' : ''}`}><span>{String(index + 1).padStart(2, '0')}</span>{item}</span>)}
      </aside>
      <div className="resume-analysis-content">
        <div className="mockup-toolbar"><div><span className="mock-kicker">概览</span><div className="mock-title">履历信息结构化归纳</div></div><StatusBadge tone="info">解析完成</StatusBadge></div>
        <div className="analysis-signal-row"><div className="analysis-signal"><span className="signal-icon success"><CheckCircle2 size={16} /></span><strong>6</strong><span>核心技能</span></div><div className="analysis-signal"><span className="signal-icon"><Search size={16} /></span><strong>5</strong><span>推荐追问</span></div><div className="analysis-signal"><span className="signal-icon warning"><AlertTriangle size={16} /></span><strong>2</strong><span>风险点</span></div></div>
        <div className="analysis-two-column">
          <div className="analysis-list-block"><div className="analysis-list-heading"><CheckCircle2 size={15} />优势</div>{mockResumeAnalysis.strengths.map(item => <p key={item}>{item}</p>)}</div>
          <div className="analysis-list-block"><div className="analysis-list-heading is-warning"><AlertTriangle size={15} />风险</div>{mockResumeAnalysis.risks.map(item => <p key={item}>{item}</p>)}</div>
        </div>
        <div className="recommended-topics"><div className="analysis-list-heading"><Lightbulb size={15} />推荐追问主题</div><div>{mockResumeAnalysis.topics.map(topic => <span key={topic}>{topic}</span>)}</div></div>
      </div>
    </div>
  </ProductWindow>
}
