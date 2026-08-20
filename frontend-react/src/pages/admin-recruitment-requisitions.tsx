import { Banknote, Building2, ChevronLeft, ChevronRight, ClipboardCheck, Loader2, LockKeyhole, RefreshCw, Search, Snowflake, Users } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import type { AdminRecruitmentRequisition, AdminRecruitmentRequisitionDetail } from '@/lib/admin'
import { request } from '@/lib/api'
import { formatDateTime } from '@/lib/recruitment'

type Page<T> = { records: T[]; total: number; pageNo: number; pageSize: number }

const statusOptions = [
  { value: 'PENDING_APPROVAL', label: '待审核' },
  { value: '', label: '全部审批状态' },
  { value: 'DRAFT', label: '企业草稿' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'REJECTED', label: '已驳回' },
]

const statusMeta = {
  DRAFT: { label: '企业草稿', tone: 'default' as const },
  PENDING_APPROVAL: { label: '待超级管理员审核', tone: 'warning' as const },
  APPROVED: { label: '已批准', tone: 'success' as const },
  REJECTED: { label: '已驳回', tone: 'danger' as const },
}

export function AdminRecruitmentRequisitions() {
  const [page, setPage] = useState<Page<AdminRecruitmentRequisition>>({ records: [], total: 0, pageNo: 1, pageSize: 20 })
  const [pageNo, setPageNo] = useState(1)
  const [status, setStatus] = useState('PENDING_APPROVAL')
  const [keyword, setKeyword] = useState('')
  const [draftKeyword, setDraftKeyword] = useState('')
  const [selected, setSelected] = useState<AdminRecruitmentRequisitionDetail>()
  const [note, setNote] = useState('')
  const [approvedHeadcount, setApprovedHeadcount] = useState('1')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    const params = new URLSearchParams({ pageNo: String(pageNo), pageSize: '20' })
    if (status) params.set('approvalStatus', status)
    if (keyword) params.set('keyword', keyword)
    try {
      setPage(await request<Page<AdminRecruitmentRequisition>>(`/v1/admin/recruitment/requisitions?${params.toString()}`))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '招聘需求审批队列加载失败')
    } finally {
      setLoading(false)
    }
  }, [keyword, pageNo, status])

  useEffect(() => { void load() }, [load])

  async function open(id: string) {
    setError('')
    try {
      const detail = await request<AdminRecruitmentRequisitionDetail>(`/v1/admin/recruitment/requisitions/${id}`)
      setSelected(detail)
      setApprovedHeadcount(String(detail.requisition.approvedHeadcount || detail.requisition.requestedHeadcount))
      setNote('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '招聘需求详情加载失败')
    }
  }

  async function decide(action: 'approve' | 'reject' | 'freeze' | 'unfreeze') {
    if (!selected) return
    const requisition = selected.requisition
    if (action !== 'approve' && !note.trim()) {
      setError(action === 'reject' ? '驳回必须填写原因' : '冻结或解除冻结必须填写原因')
      return
    }
    if (action === 'approve') {
      const count = Number(approvedHeadcount)
      if (!Number.isInteger(count) || count < 1 || count > requisition.requestedHeadcount) {
        setError(`批准人数必须为 1 到 ${requisition.requestedHeadcount} 的整数`)
        return
      }
    }
    const labels = { approve: '批准并发布', reject: '驳回', freeze: '冻结', unfreeze: '解除冻结' }
    if (!window.confirm(`确定${labels[action]}招聘需求 ${requisition.requisitionNo} 吗？`)) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const body = action === 'approve' ? { approvedHeadcount: Number(approvedHeadcount), note: note.trim() || undefined } : { note: note.trim() }
      const detail = await request<AdminRecruitmentRequisitionDetail>(`/v1/admin/recruitment/requisitions/${requisition.id}/${action}`, { method: 'POST', body: JSON.stringify(body) })
      setSelected(detail)
      setNote('')
      setMessage(`${labels[action]}已生效${action === 'approve' ? '，岗位已自动发布' : ''}。`)
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '审批动作执行失败')
    } finally {
      setBusy(false)
    }
  }

  const totalPages = useMemo(() => Math.max(1, Math.ceil(page.total / page.pageSize)), [page])

  return <div className="space-y-6">
    <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between"><div><p className="text-sm font-bold text-[var(--accent)]">招聘授权</p><h1 className="mt-2 text-3xl font-black tracking-[-.04em]">招聘需求审批</h1><p className="mt-2 max-w-3xl text-muted-foreground">逐笔核对岗位、编制、成本中心与招聘预算。批准动作会立即发布岗位，冻结动作会立即停止候选人访问。</p></div><Button type="button" variant="secondary" onClick={() => void load()} disabled={loading}><RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />刷新队列</Button></header>
    {message && <p className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/30 dark:text-emerald-200">{message}</p>}
    {error && <p role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-200">{error}</p>}
    <Card className="p-4 sm:p-5"><form className="grid gap-3 md:grid-cols-[minmax(220px,1fr)_220px_auto]" onSubmit={event => { event.preventDefault(); setKeyword(draftKeyword.trim()); setPageNo(1) }}><label className="flex h-12 items-center gap-3 rounded-full border border-border bg-background px-4 focus-within:border-[var(--accent)] focus-within:ring-2 focus-within:ring-[var(--accent)]/20"><Search className="h-4 w-4 text-muted-foreground" /><span className="sr-only">搜索招聘需求</span><input className="min-w-0 flex-1 bg-transparent text-sm outline-none" value={draftKeyword} onChange={event => setDraftKeyword(event.target.value)} placeholder="需求单、企业、岗位、编制或成本中心" /></label><ResponsiveSelect ariaLabel="审批状态" value={status} onValueChange={value => { setStatus(value); setPageNo(1) }} options={statusOptions} /><Button type="submit">筛选</Button></form></Card>
    <div className="grid gap-5 xl:grid-cols-[minmax(0,.9fr)_minmax(420px,1.1fr)]">
      <Card className="overflow-hidden p-0"><div className="flex items-center justify-between border-b border-border px-5 py-4"><div><h2 className="font-black">审批队列</h2><p className="mt-1 text-xs text-muted-foreground">当前筛选共 {page.total} 条</p></div>{loading && <Loader2 className="h-5 w-5 animate-spin text-[var(--accent)]" />}</div><div className="divide-y divide-border">{page.records.map(item => <button key={item.id} type="button" onClick={() => void open(item.id)} className={`w-full p-5 text-left transition hover:bg-muted/35 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[var(--accent)] ${selected?.requisition.id === item.id ? 'bg-[var(--accent-soft)]' : ''}`}><div className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0"><strong className="block break-words">{item.position.name}</strong><span className="mt-1 block text-xs text-muted-foreground">{item.company.name} · {item.requisitionNo}</span></div><StatusBadge item={item} /></div><div className="mt-4 grid grid-cols-2 gap-3 text-xs text-muted-foreground"><span>编制 <b className="text-foreground">{item.headcountCode} · {item.requestedHeadcount} 人</b></span><span>成本中心 <b className="text-foreground">{item.costCenterCode}</b></span><span>预算 <b className="text-foreground">{item.budgetCurrency} {Number(item.budgetAmount).toLocaleString('zh-CN')}</b></span><span>提交 <b className="text-foreground">{formatDateTime(item.submittedAt || undefined)}</b></span></div></button>)}{!loading && !page.records.length && <div className="grid min-h-56 place-items-center p-6 text-center"><div><ClipboardCheck className="mx-auto h-8 w-8 text-muted-foreground" /><p className="mt-3 font-bold">当前没有待处理需求</p><p className="mt-1 text-sm text-muted-foreground">可切换审批状态查看历史记录。</p></div></div>}</div><div className="flex items-center justify-between border-t border-border px-5 py-4 text-sm text-muted-foreground"><span>第 {pageNo} / {totalPages} 页</span><div className="flex gap-2"><Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo <= 1} onClick={() => setPageNo(value => value - 1)}><ChevronLeft className="h-4 w-4" /></Button><Button type="button" variant="secondary" className="h-9 px-3" disabled={pageNo >= totalPages} onClick={() => setPageNo(value => value + 1)}><ChevronRight className="h-4 w-4" /></Button></div></div></Card>
      {selected ? <RequisitionPanel detail={selected} note={note} setNote={setNote} approvedHeadcount={approvedHeadcount} setApprovedHeadcount={setApprovedHeadcount} busy={busy} onAction={decide} /> : <Card className="grid min-h-[520px] place-items-center text-center"><div><ClipboardCheck className="mx-auto h-10 w-10 text-muted-foreground" /><h2 className="mt-4 text-lg font-black">选择一条招聘需求</h2><p className="mt-1 text-sm text-muted-foreground">右侧将展示岗位依据、预算和完整审批记录。</p></div></Card>}
    </div>
  </div>
}

function RequisitionPanel({ detail, note, setNote, approvedHeadcount, setApprovedHeadcount, busy, onAction }: { detail: AdminRecruitmentRequisitionDetail; note: string; setNote: (value: string) => void; approvedHeadcount: string; setApprovedHeadcount: (value: string) => void; busy: boolean; onAction: (action: 'approve' | 'reject' | 'freeze' | 'unfreeze') => void }) {
  const item = detail.requisition
  const pending = item.approvalStatus === 'PENDING_APPROVAL' && !item.frozen
  return <Card className="p-0"><div className="border-b border-border bg-muted/30 p-5 sm:p-6"><div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-[.14em] text-[var(--accent)]">{item.requisitionNo}</p><h2 className="mt-2 text-2xl font-black">{item.position.name}</h2><p className="mt-1 text-sm text-muted-foreground">{item.company.name} · {item.position.code}</p></div><StatusBadge item={item} /></div><div className="mt-5 grid gap-3 sm:grid-cols-2"><Fact icon={<Users />} label="编制" value={`${item.headcountCode} · 申请 ${item.requestedHeadcount} 人`} /><Fact icon={<Building2 />} label="成本中心" value={`${item.costCenterCode}${item.costCenterName ? ` · ${item.costCenterName}` : ''}`} /><Fact icon={<Banknote />} label="招聘预算" value={`${item.budgetCurrency} ${Number(item.budgetAmount).toLocaleString('zh-CN')}`} /><Fact icon={<ClipboardCheck />} label="批准人数" value={item.approvedHeadcount ? `${item.approvedHeadcount} 人` : '待决定'} /></div><div className="mt-4 rounded-2xl border border-border bg-background p-4"><p className="text-xs font-bold text-muted-foreground">招聘理由</p><p className="mt-2 whitespace-pre-line text-sm leading-6">{item.businessJustification}</p></div>{item.reviewNote && <p className="mt-4 text-sm"><strong>审核意见：</strong>{item.reviewNote}</p>}{item.frozen && <p className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-200"><strong>冻结原因：</strong>{item.freezeReason}</p>}</div>
    <div className="p-5 sm:p-6"><h3 className="font-black">决策记录</h3><div className="mt-4 space-y-4">{detail.history.map(event => <div key={event.id} className="border-l border-border pl-4"><div className="flex flex-wrap items-baseline justify-between gap-2"><strong className="text-sm">{eventLabel(event.eventType)}</strong><time className="text-xs text-muted-foreground">{formatDateTime(event.createdAt)}</time></div><p className="mt-1 text-xs text-muted-foreground">{event.operatorName} · {event.note || '已记录'}</p></div>)}</div></div>
    {(pending || item.approvalStatus === 'APPROVED') && <div className="border-t border-border p-5 sm:p-6"><h3 className="font-black">超级管理员决策</h3>{pending && <label className="mt-4 block text-sm font-bold">批准人数<input type="number" min="1" max={item.requestedHeadcount} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 outline-none focus:border-[var(--accent)]" value={approvedHeadcount} onChange={event => setApprovedHeadcount(event.target.value)} /></label>}<label className="mt-4 block text-sm font-bold">{pending ? '审核意见（驳回时必填）' : item.frozen ? '解除冻结原因' : '冻结原因'}<textarea maxLength={1000} className="mt-2 min-h-24 w-full rounded-xl border border-border bg-background p-3 text-sm outline-none focus:border-[var(--accent)]" value={note} onChange={event => setNote(event.target.value)} /></label><div className="mt-4 flex flex-wrap justify-end gap-2">{pending && <><Button type="button" variant="danger" disabled={busy} onClick={() => onAction('reject')}>驳回需求</Button><Button type="button" disabled={busy} onClick={() => onAction('approve')}><ClipboardCheck className="h-4 w-4" />批准并发布</Button></>}{item.approvalStatus === 'APPROVED' && (item.frozen ? <Button type="button" disabled={busy} onClick={() => onAction('unfreeze')}><LockKeyhole className="h-4 w-4" />解除冻结</Button> : <Button type="button" variant="danger" disabled={busy} onClick={() => onAction('freeze')}><Snowflake className="h-4 w-4" />冻结招聘</Button>)}</div></div>}
  </Card>
}

function StatusBadge({ item }: { item: AdminRecruitmentRequisition }) { if (item.frozen) return <Badge tone="danger">招聘冻结</Badge>; const meta = statusMeta[item.approvalStatus]; return <Badge tone={meta.tone}>{meta.label}</Badge> }
function Fact({ icon, label, value }: { icon: ReactNode; label: string; value: string }) { return <div className="rounded-2xl border border-border bg-background p-4"><span className="flex h-8 w-8 items-center justify-center rounded-xl bg-[var(--accent-soft)] text-[var(--accent)] [&>svg]:h-4 [&>svg]:w-4">{icon}</span><p className="mt-3 text-xs text-muted-foreground">{label}</p><strong className="mt-1 block break-words text-sm">{value}</strong></div> }
function eventLabel(type: string) { return ({ CREATED: '企业创建需求', SUBMITTED: '企业提交审批', APPROVED_AND_PUBLISHED: '批准并发布', REJECTED: '驳回需求', FROZEN: '冻结招聘', UNFROZEN: '解除冻结', MIGRATED_APPROVED: '历史岗位迁移', MIGRATED_DRAFT: '历史草稿迁移' } as Record<string, string>)[type] || type }
