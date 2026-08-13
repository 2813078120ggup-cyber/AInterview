import { ArrowLeft, Check, Clock3, Mail, Phone, Plus, RefreshCw, Tag, Trash2, UserRound, UsersRound, X } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'
import { applicationStatusMeta, formatDateTime, type ApplicationStatus, type TalentPoolDetail, type TalentPoolNote, type TalentPoolTag } from '@/lib/recruitment'

const inputClass = 'mt-2 h-11 w-full rounded-2xl border border-border bg-background px-3 text-sm outline-none focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--brand)]/20'

export function CompanyTalentPoolDetail() {
  const { candidateId } = useParams<{ candidateId: string }>()
  const navigate = useNavigate()
  const [detail, setDetail] = useState<TalentPoolDetail>()
  const [tags, setTags] = useState<TalentPoolTag[]>([])
  const [selectedTagId, setSelectedTagId] = useState('')
  const [noteContent, setNoteContent] = useState('')
  const [noteApplicationId, setNoteApplicationId] = useState('')
  const [editingNote, setEditingNote] = useState<{ id: string; content: string; version: number }>()
  const [newTagName, setNewTagName] = useState('')
  const [newTagColor, setNewTagColor] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const load = useCallback(async () => {
    if (!candidateId) return
    setLoading(true); setError('')
    try {
      const [loadedDetail, loadedTags] = await Promise.all([
        request<TalentPoolDetail>(`/v1/company/recruitment/talent-pool/${candidateId}?notePageNo=1&notePageSize=20`),
        request<TalentPoolTag[]>('/v1/company/recruitment/talent-pool/tags'),
      ])
      setDetail(loadedDetail); setTags(loadedTags)
    } catch (reason) {
      setDetail(undefined); setError(reason instanceof Error ? reason.message : '人才档案加载失败')
    } finally { setLoading(false) }
  }, [candidateId])

  useEffect(() => { void load() }, [load])

  async function mutate(path: string, init?: RequestInit, success = '操作已完成') {
    setBusy(true); setError(''); setMessage('')
    try { await request(path, init); setMessage(success); await load() }
    catch (reason) { setError(reason instanceof Error ? reason.message : '操作失败，请稍后重试') }
    finally { setBusy(false) }
  }

  async function addNote(event: FormEvent) {
    event.preventDefault()
    if (!candidateId || !noteContent.trim()) return
    await mutate(`/v1/company/recruitment/talent-pool/${candidateId}/notes`, { method: 'POST', body: JSON.stringify({ content: noteContent.trim(), applicationId: noteApplicationId || undefined }) }, '共享备注已添加')
    setNoteContent(''); setNoteApplicationId('')
  }

  async function saveNote(event: FormEvent) {
    event.preventDefault()
    if (!candidateId || !editingNote?.content.trim()) return
    await mutate(`/v1/company/recruitment/talent-pool/${candidateId}/notes/${editingNote.id}`, { method: 'PUT', body: JSON.stringify({ content: editingNote.content.trim(), version: editingNote.version }) }, '共享备注已更新')
    setEditingNote(undefined)
  }

  async function createTag(event: FormEvent) {
    event.preventDefault()
    if (!newTagName.trim()) return
    await mutate('/v1/company/recruitment/talent-pool/tags', { method: 'POST', body: JSON.stringify({ name: newTagName.trim(), color: newTagColor.trim() || undefined }) }, '标签已创建')
    setNewTagName(''); setNewTagColor('')
  }

  if (loading && !detail) return <Card className="grid min-h-64 place-items-center"><RefreshCw className="h-7 w-7 animate-spin text-[var(--accent)]" /></Card>
  if (!detail) return <Card className="grid min-h-64 place-items-center text-center"><div><UsersIcon /><h1 className="mt-3 text-xl font-black">人才档案不可用</h1><p className="mt-2 max-w-md text-sm text-muted-foreground">{error || '候选人不在当前企业的人才库，或你没有查看权限。'}</p><div className="mt-5 flex flex-wrap justify-center gap-2"><Button type="button" variant="secondary" onClick={() => navigate('/company/talent-pool')}><ArrowLeft className="h-4 w-4" />返回人才库</Button><Button type="button" onClick={() => void load()}>重试</Button></div></div></Card>

  const candidate = detail.candidate
  const tagIds = new Set(detail.tags.map(tag => tag.id))
  const availableTags = tags.filter(tag => !tagIds.has(tag.id))
  const applicationOptions = [{ value: '', label: '不关联具体申请' }, ...detail.applications.map(application => ({ value: application.applicationId, label: `${application.positionName} · ${formatDateTime(application.submittedAt)}` }))]

  async function addTag() { if (candidateId && selectedTagId) { await mutate(`/v1/company/recruitment/talent-pool/${candidateId}/tags/${selectedTagId}`, { method: 'POST' }, '标签已添加'); setSelectedTagId('') } }
  async function removeTag(tagId: string) { if (candidateId) await mutate(`/v1/company/recruitment/talent-pool/${candidateId}/tags/${tagId}`, { method: 'DELETE' }, '标签已移除') }
  async function removeFromPool() { if (candidateId && window.confirm('移出后历史申请和审计记录仍会保留，确定移出人才库吗？')) { await mutate(`/v1/company/recruitment/talent-pool/${candidateId}`, { method: 'DELETE' }, '已移出人才库'); navigate('/company/talent-pool') } }

  return <div className="min-w-0 space-y-5">
    <header className="rounded-[28px] border border-border/80 bg-surface p-5 shadow-[0_16px_40px_rgba(20,18,17,.05)] sm:p-7"><Button type="button" variant="ghost" className="-ml-3 h-9 px-3" onClick={() => navigate('/company/talent-pool')}><ArrowLeft className="h-4 w-4" />返回人才库</Button><div className="mt-5 flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between"><div className="min-w-0"><div className="flex items-start gap-3"><span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><UserRound className="h-5 w-5" /></span><div className="min-w-0"><p className="text-sm font-bold text-[var(--accent)]">企业协作档案</p><h1 className="mt-1 break-words text-3xl font-black tracking-[-.04em]">{candidate.candidateName || '未命名候选人'}</h1><p className="mt-2 break-words text-sm text-muted-foreground">加入于 {formatDateTime(candidate.addedAt)} · 最近联系 {formatDateTime(candidate.lastContactedAt)}</p></div></div></div><div className="flex flex-wrap gap-2"><Button type="button" variant="secondary" disabled={busy} onClick={() => void mutate(`/v1/company/recruitment/talent-pool/${candidateId}/contact`, { method: 'POST' }, '最近联系时间已更新')}><Clock3 className="h-4 w-4" />标记已联系</Button><Button type="button" variant="danger" disabled={busy} onClick={() => void removeFromPool()}><Trash2 className="h-4 w-4" />移出人才库</Button></div></div><div className="mt-6 grid gap-3 border-t border-border pt-4 text-sm sm:grid-cols-3"><Meta label="当前企业申请" value={`${candidate.applicationCount} 次`} /><Meta label="最近活动" value={formatDateTime(candidate.lastActivityAt || candidate.updatedAt)} /><Meta label="联系方式" value={candidate.email || candidate.phone || '未授权或未填写'} /></div></header>
    {error && <div role="alert" className="flex items-start justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/70 dark:bg-rose-950/30 dark:text-rose-200"><span className="min-w-0 break-words">{error}</span><button type="button" className="shrink-0 font-semibold underline" onClick={() => { setError(''); void load() }}>重试</button></div>}{message && <p role="status" className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 dark:border-emerald-900/70 dark:bg-emerald-950/30 dark:text-emerald-100">{message}</p>}

    <div className="grid min-w-0 gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
      <main className="min-w-0 space-y-5"><Card><SectionTitle icon={<Mail className="h-5 w-5" />} title="联系方式" description="只显示当前账号在当前企业范围内获准查看的信息。" /><div className="mt-5 grid gap-3 sm:grid-cols-2"><Contact icon={<Mail className="h-4 w-4" />} value={candidate.email || '未填写邮箱'} /><Contact icon={<Phone className="h-4 w-4" />} value={candidate.phone || '未填写手机'} /></div></Card>
        <Card><SectionTitle icon={<Clock3 className="h-5 w-5" />} title="当前企业历史申请" description="只包含该候选人在当前企业的申请，跨企业记录不会返回。" /><div className="mt-5 space-y-3">{detail.applications.length ? detail.applications.map(application => { const meta = applicationStatusMeta[application.status as ApplicationStatus] || { label: application.status, tone: 'default' as const }; return <Link key={application.applicationId} to={`/company/applications/${application.applicationId}`} className="block rounded-2xl border border-border bg-background p-4 transition hover:border-[var(--accent)]"><div className="flex flex-wrap items-start justify-between gap-3"><div className="min-w-0"><strong className="block break-words">{application.positionName}</strong><span className="mt-1 block text-xs text-muted-foreground">{application.applicationNo} · 投递于 {formatDateTime(application.submittedAt)}</span></div><Badge tone={meta.tone}>{meta.label}</Badge></div><div className="mt-3 flex flex-wrap gap-3 text-xs text-muted-foreground"><span>匹配度 {application.matchScore == null ? '待评估' : `${application.matchScore}%`}</span><span>面试 {application.interviewStatus || 'NONE'}</span><span>更新于 {formatDateTime(application.updatedAt)}</span></div></Link> }) : <EmptyLine text="当前企业还没有历史申请。" />}</div></Card>
        <Card><SectionTitle icon={<Tag className="h-5 w-5" />} title="企业标签" description="标签只属于当前企业，不会影响候选人的全局资料。" /><div className="mt-5 flex flex-wrap gap-2">{detail.tags.length ? detail.tags.map(tag => <span key={tag.id} className="inline-flex items-center gap-1 rounded-full bg-[var(--accent-soft)] px-3 py-1.5 text-xs font-semibold text-[var(--accent)]">{tag.name}<button type="button" aria-label={`移除标签 ${tag.name}`} className="rounded-full p-0.5 hover:bg-black/10" onClick={() => void removeTag(tag.id)}><X className="h-3.5 w-3.5" /></button></span>) : <span className="text-sm text-muted-foreground">尚未添加企业标签。</span>}</div><div className="mt-5 flex flex-col gap-2 sm:flex-row"><ResponsiveSelect ariaLabel="选择要添加的标签" value={selectedTagId} onValueChange={setSelectedTagId} options={[{ value: '', label: '选择已有标签' }, ...availableTags.map(tag => ({ value: tag.id, label: tag.name }))]} disabled={!availableTags.length} /><Button type="button" variant="secondary" disabled={!selectedTagId || busy} onClick={() => void addTag()}><Plus className="h-4 w-4" />添加</Button></div><form className="mt-5 grid gap-2 sm:grid-cols-[minmax(0,1fr)_120px_auto]" onSubmit={createTag}><label className="min-w-0"><span className="text-xs font-bold text-muted-foreground">新建标签</span><input className={inputClass} value={newTagName} onChange={event => setNewTagName(event.target.value)} placeholder="例如 重点跟进" /></label><label><span className="text-xs font-bold text-muted-foreground">颜色</span><input className={inputClass} value={newTagColor} onChange={event => setNewTagColor(event.target.value)} placeholder="可选" /></label><Button type="submit" className="mt-5" disabled={!newTagName.trim() || busy}><Plus className="h-4 w-4" />创建</Button></form></Card>
      </main>
      <aside className="h-fit min-w-0 space-y-5 xl:sticky xl:top-6"><Card><SectionTitle icon={<Tag className="h-5 w-5" />} title="共享备注" description="企业成员共同维护，保存版本以处理并发编辑。" /><form className="mt-5" onSubmit={addNote}><label className="block text-sm font-bold">添加备注<textarea className="mt-2 min-h-32 w-full rounded-2xl border border-border bg-background p-3 text-sm outline-none focus:border-[var(--accent)]" value={noteContent} onChange={event => setNoteContent(event.target.value)} placeholder="记录沟通重点、候选人偏好或下一步…" /></label><div className="mt-3"><ResponsiveSelect ariaLabel="备注关联申请" value={noteApplicationId} onValueChange={setNoteApplicationId} options={applicationOptions} /></div><Button type="submit" className="mt-3 w-full" disabled={!noteContent.trim() || busy}><Plus className="h-4 w-4" />添加共享备注</Button></form></Card><div className="space-y-3">{detail.notes.records.length ? detail.notes.records.map(note => <NoteCard key={note.id} note={note} editing={editingNote?.id === note.id} onEdit={() => setEditingNote({ id: note.id, content: note.content, version: note.version })} onCancel={() => setEditingNote(undefined)} editingContent={editingNote?.id === note.id ? editingNote.content : ''} onEditingContent={content => setEditingNote(current => current ? ({ ...current, content }) : current)} onSave={saveNote} busy={busy} />) : <Card><EmptyLine text="还没有共享备注。" /></Card>}</div></aside>
    </div>
  </div>
}

function NoteCard({ note, editing, onEdit, onCancel, editingContent, onEditingContent, onSave, busy }: { note: TalentPoolNote; editing: boolean; onEdit: () => void; onCancel: () => void; editingContent: string; onEditingContent: (value: string) => void; onSave: (event: FormEvent) => void; busy: boolean }) { return <Card className="p-4"><div className="flex items-start justify-between gap-3"><div className="min-w-0"><p className="break-words text-sm font-bold">{note.authorName}</p><p className="mt-1 text-xs text-muted-foreground">{formatDateTime(note.createdAt)} · v{note.version}{note.updatedAt !== note.createdAt ? ' · 已更新' : ''}</p></div>{!editing && <Button type="button" variant="ghost" className="h-9 px-3" onClick={onEdit}>编辑</Button>}</div>{editing ? <form className="mt-4" onSubmit={onSave}><textarea className="min-h-28 w-full rounded-2xl border border-border bg-background p-3 text-sm outline-none focus:border-[var(--accent)]" value={editingContent} onChange={event => onEditingContent(event.target.value)} /><div className="mt-3 flex justify-end gap-2"><Button type="button" variant="ghost" onClick={onCancel}>取消</Button><Button type="submit" disabled={!editingContent.trim() || busy}><Check className="h-4 w-4" />保存</Button></div></form> : <p className="mt-4 whitespace-pre-line break-words text-sm leading-6">{note.content}</p>}{note.applicationId && <p className="mt-3 text-xs text-muted-foreground">关联申请：{note.applicationId}</p>}</Card> }
function SectionTitle({ icon, title, description }: { icon: ReactNode; title: string; description: string }) { return <div><div className="flex items-center gap-2 text-[var(--accent)]">{icon}<h2 className="text-lg font-black text-foreground">{title}</h2></div><p className="mt-1 text-sm text-muted-foreground">{description}</p></div> }
function Meta({ label, value }: { label: string; value: string }) { return <div className="min-w-0"><p className="text-xs font-bold text-muted-foreground">{label}</p><p className="mt-1 break-words font-semibold">{value}</p></div> }
function Contact({ icon, value }: { icon: ReactNode; value: string }) { return <div className="flex min-w-0 items-center gap-3 rounded-2xl border border-border bg-background p-4"><span className="shrink-0 text-[var(--accent)]">{icon}</span><span className="min-w-0 break-words text-sm">{value}</span></div> }
function EmptyLine({ text }: { text: string }) { return <p className="rounded-2xl bg-muted p-4 text-sm text-muted-foreground">{text}</p> }
function UsersIcon() { return <UsersRound className="mx-auto h-8 w-8 text-muted-foreground" /> }
