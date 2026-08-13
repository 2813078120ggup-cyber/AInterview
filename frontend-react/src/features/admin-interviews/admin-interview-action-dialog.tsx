import { CheckCircle2, Trash2, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { Candidate, InterviewRow } from './admin-interviews-api'

const dateText = (value?: string) => value?.replace('T', ' ').slice(0, 16) || '-'

export type InterviewActionTarget = { type: 'pass' | 'delete'; interview: InterviewRow }

type Props = {
  target: InterviewActionTarget
  candidate?: Candidate
  busy: boolean
  onClose: () => void
  onConfirm: () => void
}

export function AdminInterviewActionDialog({ target, candidate, busy, onClose, onConfirm }: Props) {
  const isDelete = target.type === 'delete'
  return <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 backdrop-blur-sm sm:items-center sm:p-4" role="dialog" aria-modal="true" aria-labelledby="interview-action-title">
    <div className="safe-area-bottom w-full max-w-lg rounded-t-[28px] border border-border bg-surface p-5 shadow-2xl sm:rounded-[32px] sm:p-7">
      <div className="flex items-start justify-between gap-4"><div>
        <p className="text-sm font-semibold text-[var(--accent)]">{isDelete ? '删除面试' : '面试结果'}</p>
        <h2 id="interview-action-title" className="mt-2 text-2xl font-black">{isDelete ? '确认删除这场面试？' : '确认标记为已通过？'}</h2>
        <p className="mt-3 text-sm leading-6 text-muted-foreground">{isDelete ? '删除后会同步移除该面试的题目快照、回答和关联评测数据，此操作不可恢复。' : '系统会把面试状态更新为已通过；如果面试还没有结束，会同时写入当前结束时间。'}</p>
      </div><Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={onClose} aria-label="关闭确认对话框"><X className="h-5 w-5" /></Button></div>
      <div className="mt-6 rounded-3xl border border-border bg-muted/30 p-4"><p className="text-xs font-semibold tracking-[.08em] text-muted-foreground">面试信息</p><p className="mt-2 font-bold">{target.interview.title}</p><p className="mt-1 text-sm text-muted-foreground">候选人：{candidate?.realName ?? target.interview.candidateId} · 预约时间：{dateText(target.interview.scheduledAt)}</p></div>
      <div className="mt-7 flex justify-end gap-3"><Button variant="secondary" onClick={onClose} disabled={busy}>取消</Button><Button variant={isDelete ? 'danger' : 'primary'} onClick={onConfirm} disabled={busy}>{isDelete ? <Trash2 className="h-4 w-4" /> : <CheckCircle2 className="h-4 w-4" />}{busy ? '正在处理…' : isDelete ? '确认删除' : '标记通过'}</Button></div>
    </div>
  </div>
}
