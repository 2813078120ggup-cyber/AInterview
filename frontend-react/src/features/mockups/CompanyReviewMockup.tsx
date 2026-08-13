import { useState } from 'react'
import { ArrowUpRight, CheckCircle2, FileText, MessageSquareText, ShieldCheck, XCircle } from 'lucide-react'
import { ProductWindow, ScoreRing, StatusBadge } from '../components'
import { mockApplications } from '../mock-data'

export function CompanyReviewMockup() {
  const [selectedIndex, setSelectedIndex] = useState(0)
  const selected = mockApplications[selectedIndex]

  return <ProductWindow title="候选人评估" subtitle="企业工作台 · 申请详情" activeTab="评估">
    <div className="company-review-layout">
      <div className="company-table-area">
        <div className="mockup-toolbar"><div><span className="mock-kicker">申请评估</span><div className="mock-title">资料、面试与报告集中复核</div></div><StatusBadge tone="info">3 位候选人</StatusBadge></div>
        <div className="company-review-table" role="table" aria-label="候选人审核列表">
          <div className="company-table-row company-table-head" role="row"><span>候选人</span><span>岗位</span><span>匹配度</span><span>面试评分</span><span>状态</span></div>
          {mockApplications.map((item, index) => <button type="button" key={item.name} className={`company-table-row company-table-button${selectedIndex === index ? ' is-selected' : ''}`} onClick={() => setSelectedIndex(index)} role="row"><span className="company-person"><span className="candidate-avatar">{item.name.slice(0, 1)}</span><strong>{item.name}</strong></span><span>{item.role}</span><strong className="table-score">{item.match}%</strong><strong className="table-score muted-score">{item.interview}</strong><StatusBadge tone={item.tone}>{item.status}</StatusBadge></button>)}
        </div>
        <p className="table-footnote"><ShieldCheck size={14} />系统评估用于辅助复核，录用决定由企业作出</p>
      </div>
      <aside className="review-drawer" aria-label={`${selected.name} 候选人详情`}>
        <div className="review-drawer-heading"><div><span className="mock-kicker">当前候选人</span><div className="mock-title">{selected.name}</div><p>{selected.role}</p></div><span className="mock-icon-button" aria-hidden="true"><ArrowUpRight size={16} /></span></div>
        <div className="review-score-line"><ScoreRing score={selected.match} label="岗位匹配" size={84} /><div><span>面试评分</span><strong>{selected.interview}</strong><StatusBadge tone="warning">企业评估中</StatusBadge></div></div>
        <div className="review-drawer-links"><button type="button"><FileText size={15} />简历</button><button type="button"><MessageSquareText size={15} />面试记录</button><button type="button"><CheckCircle2 size={15} />评测报告</button></div>
        <div className="review-actions"><button type="button" className="review-primary"><CheckCircle2 size={15} />邀请线下面试</button><button type="button" className="review-secondary"><XCircle size={15} />不通过</button></div>
      </aside>
    </div>
  </ProductWindow>
}
