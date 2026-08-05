import { ArrowLeft, ImagePlus, Save, Send, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { TicketLoading } from '@/components/tickets/ticket-ui'
import { ticketTypeLabels } from '@/components/tickets/ticket-labels'
import { createTicket, getTicket, submitTicket, updateTicket, uploadTicketAttachment, type TicketDetail, type TicketType } from '@/lib/ticket-api'

const types = (Object.keys(ticketTypeLabels) as TicketType[]).map(value => ({ value, label: ticketTypeLabels[value] }))

export function CandidateTicketCreate() {
  const { id } = useParams()
  const edit = Boolean(id)
  const navigate = useNavigate()
  const inputRef = useRef<HTMLInputElement>(null)
  const [detail, setDetail] = useState<TicketDetail | null>(null)
  const [ticketType, setTicketType] = useState<TicketType>('BUG_REPORT')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [busy, setBusy] = useState(Boolean(id))
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!id) return
    getTicket(id).then(result => { setDetail(result); setTicketType(result.ticket.ticketType); setTitle(result.ticket.title); setDescription(result.description) }).catch(reason => setMessage(reason instanceof Error ? reason.message : '草稿加载失败')).finally(() => setBusy(false))
  }, [id])

  async function ensureDraft() {
    if (detail) return detail
    const created = await createTicket({ ticketType, title, description })
    setDetail(created)
    return created
  }

  async function saveDraft() {
    setBusy(true); setMessage('')
    try {
      const result = detail ? await updateTicket(detail.ticket.id, { ticketType, title, description }) : await ensureDraft()
      setDetail(result)
      if (files.length) {
        for (const file of files) await uploadTicketAttachment(result.ticket.id, file)
        setFiles([])
      }
      setMessage('草稿已保存')
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : '草稿保存失败')
    } finally { setBusy(false) }
  }

  async function submit() {
    setBusy(true); setMessage('')
    try {
      const draft = detail ? await updateTicket(detail.ticket.id, { ticketType, title, description }) : await ensureDraft()
      if (files.length) for (const file of files) await uploadTicketAttachment(draft.ticket.id, file)
      const result = await submitTicket(draft.ticket.id)
      navigate(`/candidate/tickets/${result.ticket.id}`)
    } catch (reason) {
      setMessage(reason instanceof Error ? reason.message : '提交失败')
      setBusy(false)
    }
  }

  if (edit && busy && !detail) return <TicketLoading />
  return <div className="mx-auto max-w-4xl space-y-6">
    <Link to={detail ? `/candidate/tickets/${detail.ticket.id}` : '/candidate/tickets'} className="inline-flex items-center gap-2 text-sm font-semibold text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" />返回工单</Link>
    <div><p className="text-sm font-bold uppercase tracking-[.16em] text-[var(--accent)]">New feedback</p><h1 className="mt-2 text-3xl font-black">{edit ? '编辑反馈草稿' : '提交问题反馈'}</h1><p className="mt-2 text-sm leading-6 text-muted-foreground">请尽量描述复现步骤、实际结果和期望结果，截图可以帮助管理员更快定位问题。</p></div>
    <Card className="space-y-5 p-5 sm:p-7">
      <label className="block space-y-2"><span className="text-sm font-bold">问题类型</span><ResponsiveSelect ariaLabel="问题类型" value={ticketType} options={types} onValueChange={value => setTicketType(value as TicketType)} /></label>
      <label className="block space-y-2"><span className="text-sm font-bold">标题</span><input value={title} onChange={event => setTitle(event.target.value)} maxLength={120} placeholder="例如：第二轮追问后虚拟人没有声音" className="h-12 w-full rounded-2xl border border-border bg-background px-4 text-sm outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20" /></label>
      <label className="block space-y-2"><span className="text-sm font-bold">问题描述</span><textarea value={description} onChange={event => setDescription(event.target.value)} maxLength={10000} rows={9} placeholder="请描述发生时间、操作步骤、实际表现和期望表现" className="w-full resize-y rounded-2xl border border-border bg-background p-4 text-sm leading-6 outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20" /><span className="block text-right text-xs text-muted-foreground">{description.length}/10000</span></label>
      <div className="space-y-3"><div className="flex items-center justify-between gap-3"><div><p className="text-sm font-bold">截图附件</p><p className="mt-1 text-xs text-muted-foreground">支持 PNG、JPEG、GIF；单张不超过 10MB，最多 5 张。</p></div><Button type="button" variant="secondary" onClick={() => inputRef.current?.click()}><ImagePlus className="h-4 w-4" />添加截图</Button><input ref={inputRef} type="file" accept="image/png,image/jpeg,image/gif" multiple hidden onChange={event => setFiles(previous => [...previous, ...Array.from(event.target.files || [])].slice(0, 5))} /></div>{files.length > 0 && <div className="grid gap-2 sm:grid-cols-2">{files.map((file, index) => <div key={`${file.name}-${index}`} className="flex items-center justify-between gap-3 rounded-2xl bg-muted px-3 py-2 text-sm"><span className="min-w-0 truncate">{file.name}</span><button type="button" aria-label={`移除${file.name}`} onClick={() => setFiles(previous => previous.filter((_, itemIndex) => itemIndex !== index))} className="rounded-full p-1 text-muted-foreground hover:bg-background hover:text-foreground"><X className="h-4 w-4" /></button></div>)}</div>}</div>
      {message && <div className="rounded-2xl border border-[var(--accent)]/30 bg-[var(--accent-soft)] px-4 py-3 text-sm text-foreground">{message}</div>}
      <div className="flex flex-col-reverse gap-3 border-t border-border pt-5 sm:flex-row sm:justify-end"><Button type="button" variant="secondary" onClick={saveDraft} disabled={busy}><Save className="h-4 w-4" />保存草稿</Button><Button type="button" onClick={submit} disabled={busy || !title.trim() || !description.trim()}><Send className="h-4 w-4" />{busy ? '提交中…' : '提交工单'}</Button></div>
    </Card>
  </div>
}
