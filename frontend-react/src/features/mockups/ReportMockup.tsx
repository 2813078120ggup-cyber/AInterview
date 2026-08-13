import { CheckCircle2, FileChartColumn, Flag, TrendingUp } from 'lucide-react'
import { ProductWindow, ScoreRing, StatusBadge } from '../components'
import { mockReport } from '../mock-data'

export function ReportMockup() {
  return <ProductWindow title="多维面试报告" subtitle="面试报告 · 评估依据" activeTab="报告">
    <div className="report-mockup">
      <div className="report-summary-row">
        <div><span className="mock-kicker">面试报告</span><div className="mock-title">结论与依据同步呈现</div><p>{mockReport.conclusion}</p></div>
        <ScoreRing score={mockReport.overall} label="综合评分" size={116} />
      </div>
      <div className="report-key-metrics"><div><span>岗位匹配度</span><strong>{mockReport.match}%</strong><small><TrendingUp size={13} />当前申请匹配结果</small></div><div><span>记录完整度</span><strong>92%</strong><small><CheckCircle2 size={13} />覆盖 18 条回答记录</small></div><div><span>待核实项</span><strong>2</strong><small><Flag size={13} />建议企业复核</small></div></div>
      <div className="report-details-grid">
        <div className="report-dimensions"><div className="report-section-title"><FileChartColumn size={15} />能力维度</div>{mockReport.dimensions.slice(0, 5).map(([label, value]) => <div className="report-dimension-row" key={label}><span>{label}</span><i><b style={{ width: `${value}%` }} /></i><strong>{value}</strong></div>)}</div>
        <div className="report-notes"><div className="report-note success"><StatusBadge tone="success">优势</StatusBadge><p>{mockReport.strengths}</p></div><div className="report-note warning"><StatusBadge tone="warning">待核实</StatusBadge><p>{mockReport.weaknesses}</p></div></div>
      </div>
    </div>
  </ProductWindow>
}
