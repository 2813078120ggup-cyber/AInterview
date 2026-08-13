import { useState } from 'react'
import { ArrowRight, CalendarDays, CheckCircle2, MapPin, Send, UserRound } from 'lucide-react'
import { ProductWindow, StatusBadge } from '../components'
import { mockInvitation } from '../mock-data'

export function InvitationMockup() {
  const [sent, setSent] = useState(false)

  return <ProductWindow title="线下面试邀请" subtitle="面试安排 · 候选人通知" activeTab="邀请">
    <div className="invitation-layout">
      <div className="invitation-form-state">
        <div className="mockup-toolbar"><div><span className="mock-kicker">面试安排</span><div className="mock-title">确认下一阶段面试信息</div></div><StatusBadge tone={sent ? 'success' : 'warning'}>{sent ? '已发送' : '待发送'}</StatusBadge></div>
        <div className="invitation-field-list"><div><CalendarDays size={16} /><span>时间</span><strong>{mockInvitation.date}</strong></div><div><MapPin size={16} /><span>地点</span><strong>{mockInvitation.location}</strong></div><div><UserRound size={16} /><span>联系人</span><strong>{mockInvitation.contact}</strong></div><div><Send size={16} /><span>备注</span><strong>{mockInvitation.note}</strong></div></div>
        <button type="button" className="invitation-send-button" onClick={() => setSent(true)} disabled={sent}>{sent ? <><CheckCircle2 size={16} />邀请已发送</> : <>发送线下面试邀请<ArrowRight size={16} /></>}</button>
      </div>
      <div className={`candidate-notification${sent ? ' is-sent' : ''}`} aria-live="polite">
        <div className="notification-top"><span className="notification-app-mark">A</span><span>候选人通知</span><span className="notification-time">刚刚</span></div>
        <div className="notification-body"><span className="notification-check"><CheckCircle2 size={22} /></span><span className="mock-kicker">面试邀请</span><div className="mock-title">{sent ? '线下面试安排已发送' : '待发送的面试安排'}</div><p>{sent ? '招聘企业已发送线下面试安排，请核对时间、地点和联系人。' : '发送后，候选人可在站内通知中查看并确认安排。'}</p><button type="button" className="notification-confirm" disabled={!sent}>{sent ? '确认参加' : '等待企业发送'}</button></div>
      </div>
    </div>
  </ProductWindow>
}
