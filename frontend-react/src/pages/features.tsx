import { useEffect } from 'react'
import { ArrowRight, Bot, CheckCircle2, ChevronRight, ClipboardCheck, FileChartColumn, FileSearch, ShieldCheck, Sparkles } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { FeatureHeading, ProductWindow, Reveal, ScoreRing, StatusBadge } from '@/features/components'
import { CompanyReviewMockup } from '@/features/mockups/CompanyReviewMockup'
import { InterviewMockup } from '@/features/mockups/InterviewMockup'
import { InvitationMockup } from '@/features/mockups/InvitationMockup'
import { JobMatchMockup } from '@/features/mockups/JobMatchMockup'
import { ReportMockup } from '@/features/mockups/ReportMockup'
import { ResumeAnalysisMockup } from '@/features/mockups/ResumeAnalysisMockup'
import { recruitmentWorkflow } from '@/features/mock-data'
import { loginPath, postLoginDestination } from '@/lib/navigation'
import { profile } from '@/lib/session'
import '@/features/features.css'

function platformEntry(requested?: string) {
  const current = profile()
  return current ? postLoginDestination(current.roles, requested) : loginPath(requested)
}

function FeaturesNav() {
  const navigate = useNavigate()
  return <header className="features-nav">
    <Link to="/features" className="features-brand" aria-label="AInterview 产品能力首页"><span className="features-brand-mark"><Bot size={18} /></span><span><strong>AInterview</strong><small>招聘协同与智能面试</small></span></Link>
    <nav aria-label="产品能力导航" className="features-nav-links"><a href="#capabilities">产品能力</a><a href="#interview">智能面试</a><a href="#workflow">招聘流程</a></nav>
    <div className="features-nav-actions"><Button type="button" className="features-nav-button" onClick={() => navigate(platformEntry())}>进入平台<ArrowRight size={15} /></Button></div>
  </header>
}

function HeroOverviewMockup() {
  const steps = [
    ['岗', '岗位发布', '已完成', 'done'],
    ['简', '简历解析', '已完成', 'done'],
    ['面', '智能面试', '进行中', 'active'],
    ['评', '企业复核', '待开始', 'pending'],
  ]
  return <ProductWindow title="招聘流程总览" subtitle="申请、面试与评估" activeTab="概览">
    <div className="overview-mockup">
      <div className="overview-sidebar"><div className="overview-sidebar-brand"><span className="overview-mini-mark"><Sparkles size={14} /></span><span>申请流程</span></div>{steps.map(([code, label, status, state]) => <div className={`overview-step is-${state}`} key={label}><span className="overview-step-icon">{state === 'done' ? <CheckCircle2 size={14} /> : code}</span><span><strong>{label}</strong><small>{status}</small></span></div>)}</div>
      <div className="overview-main"><div className="overview-main-top"><div><span className="mock-kicker">申请详情</span><div className="mock-title">当前进度与下一步</div></div><StatusBadge tone="success">模型服务正常</StatusBadge></div><div className="overview-score-row"><ScoreRing score={86} label="岗位匹配" size={102} /><div><span className="mock-kicker">Java 后端开发工程师</span><strong>张三</strong><p>简历、面试与评估记录已关联</p></div></div><div className="overview-activity"><div><span>当前阶段</span><strong>面试进行中</strong><small>根据本轮回答生成后续问题</small></div><div><span>企业待办</span><strong>3 项待处理</strong><small>查看面试记录与评估报告</small></div></div></div>
    </div>
  </ProductWindow>
}

function WorkflowStrip() {
  return <div className="workflow-strip" aria-label="招聘链路概览">{recruitmentWorkflow.map(([shortLabel, chinese], index) => <div className="workflow-strip-item" key={shortLabel}><span>{shortLabel}</span><strong>{chinese}</strong>{index < recruitmentWorkflow.length - 1 && <ChevronRight size={15} aria-hidden="true" />}</div>)}</div>
}

function InterviewTimeline() {
  return <div className="interview-storyline" aria-label="连续追问示例"><div className="interview-storyline-line" aria-hidden="true" /><div><span>问题</span><strong>项目经历</strong><small>关联简历内容</small></div><div><span>作答</span><strong>Redis 缓存</strong><small>说明实施方案</small></div><div><span>追问</span><strong>缓存一致性</strong><small>核实技术边界</small></div><div><span>复核</span><strong>高并发处理</strong><small>形成评估依据</small></div></div>
}

export function FeaturesPage() {
  const navigate = useNavigate()

  useEffect(() => {
    const previousTitle = document.title
    document.title = '产品能力 | AInterview'
    return () => { document.title = previousTitle }
  }, [])

  return <div className="features-page">
    <FeaturesNav />
    <main>
      <section className="features-hero" aria-labelledby="features-hero-title">
        <div className="features-container features-hero-grid">
          <Reveal className="features-hero-copy">
            <p className="features-eyebrow">招聘协同 · 面试评估</p>
            <h1 id="features-hero-title">见人，见岗，<em>见依据。</em></h1>
            <p className="features-hero-description">将岗位要求、候选人简历、面试记录与评估报告纳入同一流程，支持从申请提交到企业复核的全过程管理。</p>
            <div className="features-hero-actions"><Button type="button" onClick={() => navigate(platformEntry())}>进入平台<ArrowRight size={16} /></Button><a href="#capabilities" className="features-outline-link">查看核心能力<ChevronRight size={16} /></a></div>
            <div className="features-hero-note"><ShieldCheck size={15} /><span>系统记录过程并辅助评估，录用结论由企业作出</span></div>
          </Reveal>
          <Reveal className="features-hero-demo" delay={100}><HeroOverviewMockup /></Reveal>
        </div>
      </section>

      <section className="features-overview-section" aria-labelledby="overview-title">
        <div className="features-container"><Reveal><div className="features-overview-heading"><div><span className="features-overview-kicker">从岗位到录用</span><h2 id="overview-title">一岗一档，<br />一面一据。</h2></div><p>岗位、简历、面试与评估按申请关联，减少跨页面整理，支持团队依据统一记录推进流程。</p></div></Reveal><Reveal delay={80}><WorkflowStrip /></Reveal></div>
      </section>

      <section id="capabilities" className="features-story-section features-job-section" aria-labelledby="job-match-title">
        <div className="features-container features-story-grid"><Reveal className="features-story-copy"><FeatureHeading id="job-match-title" eyebrow="岗位匹配评估" title="要求逐项对照，差距清晰可查。" description="系统对岗位要求与简历结构化信息进行比对，展示匹配技能、待核实项和评估依据；结果用于筛选参考，不替代招聘决策。" /><div className="feature-proof-list"><span><CheckCircle2 size={16} />匹配项结构化归纳</span><span><FileSearch size={16} />差距、风险与依据分开展示</span></div></Reveal><Reveal className="features-story-demo" delay={100}><JobMatchMockup /></Reveal></div>
      </section>

      <section className="features-story-section features-resume-section" aria-labelledby="resume-title">
        <div className="features-container features-story-grid is-reversed"><Reveal className="features-story-demo"><ResumeAnalysisMockup /></Reveal><Reveal className="features-story-copy" delay={100}><FeatureHeading id="resume-title" title="从履历中提炼面试线索。" description="技能、项目、优势和待核实项按结构展示，帮助招聘人员围绕候选人的实际经历准备问题。" /><div className="feature-note-block"><span className="feature-note-icon"><FileChartColumn size={17} /></span><div><strong>结构化结果用于面试准备</strong><p>推荐追问方向可供题目准备和面试复核使用。</p></div></div></Reveal></div>
      </section>

      <section id="interview" className="features-interview-section" aria-labelledby="interview-title">
        <div className="features-container features-interview-grid"><div className="features-interview-sticky"><Reveal><FeatureHeading id="interview-title" eyebrow="岗位定向智能面试" title="循简历而问，依回答再问。" description="系统根据岗位、简历与本轮回答生成后续问题，并保留题目、回答和追问记录，供招聘团队复核。" /><div className="interview-sticky-quote"><Sparkles size={16} /><span>题库确定范围，追问补充依据。</span></div></Reveal></div><Reveal className="features-interview-demo" delay={120}><InterviewMockup /><InterviewTimeline /></Reveal></div>
      </section>

      <section className="features-story-section features-report-section" aria-labelledby="report-title">
        <div className="features-container features-story-grid"><Reveal className="features-story-copy"><FeatureHeading id="report-title" title="评分有依据，结论可复核。" description="报告按能力维度汇总题目、回答与评分依据，区分优势、待核实项和改进建议；候选人仅查看企业已发布的内容。" /><div className="report-stat-callout"><strong>85</strong><span>综合评分</span><small>依据已记录的回答与评分维度汇总</small></div></Reveal><Reveal className="features-story-demo" delay={100}><ReportMockup /></Reveal></div>
      </section>

      <section className="features-review-section" aria-labelledby="review-title"><div className="features-container"><Reveal><FeatureHeading id="review-title" title="系统提供依据，企业作出决定。" description="申请详情集中呈现简历、匹配评估、面试记录和报告状态，帮助招聘团队在权限范围内完成复核与推进。" /></Reveal><Reveal delay={100}><CompanyReviewMockup /></Reveal></div></section>

      <section className="features-invitation-section" aria-labelledby="invitation-title"><div className="features-container features-story-grid is-reversed"><Reveal className="features-story-demo"><InvitationMockup /></Reveal><Reveal className="features-story-copy" delay={100}><FeatureHeading id="invitation-title" title="安排明确，通知有据。" description="企业在申请流程中记录时间、地点、联系人和备注，候选人通过站内通知查看并确认后续安排。" /><div className="feature-proof-list"><span><ClipboardCheck size={16} />记录时间、地点与联系人</span><span><CheckCircle2 size={16} />候选人接收并确认</span></div></Reveal></div></section>

      <section id="workflow" className="features-workflow-section" aria-labelledby="workflow-title"><div className="features-container"><Reveal className="features-workflow-heading"><FeatureHeading id="workflow-title" align="center" title="从投递到结果，节点清楚，责任明确。" description="申请状态、面试安排、评估报告与通知记录按流程关联，便于候选人与招聘团队查看当前进度。" /></Reveal><Reveal delay={80}><div className="features-workflow-rail">{recruitmentWorkflow.map(([shortLabel, chinese], index) => <div className="features-workflow-node" key={shortLabel}><span className="features-workflow-node-index">{String(index + 1).padStart(2, '0')}</span><div><strong>{shortLabel}</strong><span>{chinese}</span></div>{index < recruitmentWorkflow.length - 1 && <span className="features-workflow-connector" aria-hidden="true" />}</div>)}</div></Reveal></div></section>

      <section className="features-final-cta" aria-labelledby="final-cta-title"><div className="features-container"><Reveal className="features-final-cta-inner"><div><p className="features-eyebrow">统一招聘流程，保留完整依据</p><h2 id="final-cta-title">从岗位出发，把每一步落到记录里。</h2><p>候选人可以浏览岗位、管理申请并参加面试；企业可以管理岗位、复核申请和安排后续流程。</p></div><div className="features-final-cta-actions"><Button type="button" onClick={() => navigate(platformEntry())}>进入平台<ArrowRight size={16} /></Button><Button type="button" variant="secondary" onClick={() => navigate(platformEntry('/jobs'))}>查看招聘岗位<ChevronRight size={16} /></Button></div></Reveal></div></section>
    </main>
    <footer className="features-footer"><div className="features-container features-footer-inner"><Link to="/features" className="features-brand"><span className="features-brand-mark"><Bot size={18} /></span><span><strong>AInterview</strong><small>招聘协同与智能面试</small></span></Link><span>让每一面，都算数。</span><div className="features-footer-links"><Link to="/login">登录</Link><a href="#top">回到顶部</a></div></div></footer>
  </div>
}
