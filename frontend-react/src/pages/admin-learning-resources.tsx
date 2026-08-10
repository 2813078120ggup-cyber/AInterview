import { BookOpen, FileUp, KeyRound, Loader2, Pencil, Search, ShieldCheck, Trash2, X } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AdminConfirmDialog } from '@/components/admin-confirm-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request, uploadMultipart } from '@/lib/api'

type Resource = {
  id: number
  publicId: string
  title: string
  description?: string
  status: 'DRAFT' | 'PUBLISHED' | 'OFFLINE'
  allowDownload: boolean
  originalName?: string
  fileSize?: number
  pageCount?: number
  updatedAt?: string
}

type PageResult = { records: Resource[]; total: number }
type Candidate = { id: number; username: string; realName?: string }
type Permission = { subjectType: string; subjectId: string; canView: boolean; canAnnotate: boolean }

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'OFFLINE', label: '已下线' },
]

function formatSize(value?: number) {
  if (!value) return '—'
  if (value < 1024 * 1024) return `${Math.max(1, Math.round(value / 1024))} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function statusLabel(status: Resource['status']) {
  return status === 'PUBLISHED' ? '已发布' : status === 'OFFLINE' ? '已下线' : '草稿'
}

export function AdminLearningResources() {
  const [items, setItems] = useState<Resource[]>([])
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [uploadOpen, setUploadOpen] = useState(false)
  const [file, setFile] = useState<File>()
  const [form, setForm] = useState({ title: '', description: '', status: 'DRAFT', allowDownload: false })
  const [deleteTarget, setDeleteTarget] = useState<Resource>()
  const [permissionTarget, setPermissionTarget] = useState<Resource>()
  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [selectedUsers, setSelectedUsers] = useState<string[]>([])
  const [permissionLoading, setPermissionLoading] = useState(false)
  const [permissionSaving, setPermissionSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await request<PageResult>(`/v1/admin/learning-resources?pageNo=1&pageSize=100${keyword.trim() ? `&keyword=${encodeURIComponent(keyword.trim())}` : ''}${status ? `&status=${status}` : ''}`)
      setItems(result.records)
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '资料列表加载失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }, [keyword, status])

  useEffect(() => { void load() }, [load])

  async function submitUpload() {
    if (!file) { setError('请选择 PDF 文件。'); return }
    if (!form.title.trim()) { setError('请填写资料标题。'); return }
    setBusy('upload')
    try {
      const body = new FormData()
      body.append('metadata', new Blob([JSON.stringify({ ...form, title: form.title.trim() })], { type: 'application/json' }))
      body.append('file', file)
      const created = await uploadMultipart<Resource>('/v1/admin/learning-resources', body)
      setItems(previous => [created, ...previous])
      setUploadOpen(false)
      setFile(undefined)
      setForm({ title: '', description: '', status: 'DRAFT', allowDownload: false })
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'PDF 上传失败，请稍后重试。')
    } finally {
      setBusy('')
    }
  }

  async function updateStatus(item: Resource, nextStatus: Resource['status']) {
    setBusy(item.publicId)
    try {
      const updated = await request<Resource>(`/v1/admin/learning-resources/${item.publicId}`, { method: 'PUT', body: JSON.stringify({ title: item.title, description: item.description ?? '', status: nextStatus, allowDownload: item.allowDownload }) })
      setItems(previous => previous.map(current => current.publicId === item.publicId ? updated : current))
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '资料状态更新失败，请稍后重试。')
    } finally {
      setBusy('')
    }
  }

  async function remove(item: Resource) {
    setBusy(item.publicId)
    try {
      await request(`/v1/admin/learning-resources/${item.publicId}`, { method: 'DELETE' })
      setItems(previous => previous.filter(current => current.publicId !== item.publicId))
      setDeleteTarget(undefined)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '资料删除失败，请稍后重试。')
    } finally {
      setBusy('')
    }
  }

  useEffect(() => {
    if (!permissionTarget) return
    setPermissionLoading(true)
    Promise.all([
      request<Candidate[]>('/v1/users/candidates'),
      request<Permission[]>(`/v1/admin/learning-resources/${permissionTarget.publicId}/permissions`),
    ]).then(([users, permissions]) => {
      setCandidates(users)
      setSelectedUsers(permissions.filter(item => item.subjectType === 'USER' && item.canView).map(item => item.subjectId))
    }).catch(reason => setError(reason instanceof Error ? reason.message : '资料权限加载失败，请稍后重试。')).finally(() => setPermissionLoading(false))
  }, [permissionTarget])

  async function savePermissions() {
    if (!permissionTarget) return
    setPermissionSaving(true)
    try {
      await request(`/v1/admin/learning-resources/${permissionTarget.publicId}/permissions`, { method: 'PUT', body: JSON.stringify(selectedUsers.map(subjectId => ({ subjectType: 'USER', subjectId, canView: true, canAnnotate: true }))) })
      setPermissionTarget(undefined)
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '资料权限保存失败，请稍后重试。')
    } finally {
      setPermissionSaving(false)
    }
  }

  return <div className="mx-auto max-w-7xl p-4 sm:p-6 lg:p-10">
    <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
      <div><p className="text-sm font-semibold text-[var(--accent)]">内容与权限</p><h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">学习资料中心</h1><p className="mt-3 max-w-2xl text-muted-foreground">上传私有 PDF，按候选人分配查看和批注权限。</p></div>
      <Button onClick={() => setUploadOpen(true)}><FileUp className="h-4 w-4" />上传 PDF</Button>
    </header>

    {error && <p role="alert" className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}

    <Card className="mt-7 p-0">
      <div className="flex flex-col gap-3 border-b border-border p-5 md:flex-row">
        <label className="flex h-12 flex-1 items-center gap-2 rounded-full border border-border bg-surface px-4"><Search className="h-4 w-4 text-muted-foreground" /><span className="sr-only">搜索资料</span><input value={keyword} onChange={event => setKeyword(event.target.value)} onKeyDown={event => event.key === 'Enter' && void load()} className="w-full bg-transparent text-sm outline-none" placeholder="搜索资料标题或说明" /></label>
        <ResponsiveSelect ariaLabel="选择资料状态" value={status} onValueChange={setStatus} options={statusOptions} className="w-full md:w-40" />
        <Button variant="secondary" className="h-12" onClick={() => void load()}>搜索</Button>
      </div>
      {loading ? <p className="p-12 text-center text-sm text-muted-foreground">正在加载资料…</p> : <div className="divide-y divide-border">
        {items.map(item => <article key={item.publicId} className="flex flex-col gap-5 p-5 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex min-w-0 items-start gap-4"><span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><BookOpen className="h-5 w-5" /></span><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><h2 className="truncate text-lg font-bold">{item.title}</h2><Badge tone={item.status === 'PUBLISHED' ? 'success' : item.status === 'OFFLINE' ? 'default' : 'warning'}>{statusLabel(item.status)}</Badge></div><p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{item.description || '暂无资料说明'}</p><p className="mt-3 text-xs text-muted-foreground">{item.originalName || 'PDF'} · {item.pageCount ?? '—'} 页 · {formatSize(item.fileSize)}</p></div></div>
          <div className="flex flex-wrap items-center gap-2 lg:justify-end"><Link to={`/admin/learning-resources/${item.publicId}`} className="inline-flex h-11 items-center gap-2 rounded-full border border-border bg-surface px-4 text-sm font-semibold transition hover:-translate-y-0.5 hover:border-[var(--accent)]"><BookOpen className="h-4 w-4" />查看</Link><Button variant="secondary" className="h-11" onClick={() => setPermissionTarget(item)}><ShieldCheck className="h-4 w-4" />权限</Button><Button variant="secondary" className="h-11" disabled={busy === item.publicId} onClick={() => void updateStatus(item, item.status === 'PUBLISHED' ? 'OFFLINE' : 'PUBLISHED')}><Pencil className="h-4 w-4" />{item.status === 'PUBLISHED' ? '下线' : '发布'}</Button><Button variant="danger" className="h-11 w-11 px-0" disabled={busy === item.publicId} onClick={() => setDeleteTarget(item)} aria-label={`删除 ${item.title}`}><Trash2 className="h-4 w-4" /></Button></div>
        </article>)}
        {!items.length && <p className="p-12 text-center text-sm text-muted-foreground">暂未上传学习资料</p>}
      </div>}
    </Card>

    {uploadOpen && <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="upload-learning-title"><div className="mx-auto my-8 max-w-lg rounded-[28px] bg-surface p-6 shadow-2xl"><div className="flex items-start justify-between"><div><p className="text-sm font-semibold text-[var(--accent)]">资料中心</p><h2 id="upload-learning-title" className="mt-1 text-2xl font-bold">上传 PDF 资料</h2></div><Button variant="ghost" className="h-10 w-10 px-0" onClick={() => setUploadOpen(false)} aria-label="关闭上传窗口"><X className="h-5 w-5" /></Button></div><div className="mt-6 space-y-5"><label className="block text-sm font-semibold">资料标题<input value={form.title} onChange={event => setForm({ ...form, title: event.target.value })} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" placeholder="例如：Java 面试基础资料" /></label><label className="block text-sm font-semibold">资料说明<textarea value={form.description} onChange={event => setForm({ ...form, description: event.target.value })} className="mt-2 min-h-24 w-full rounded-2xl border border-border bg-background p-4 font-normal outline-none focus:border-[var(--accent)]" placeholder="说明资料适用的岗位或学习阶段" /></label><label className="block text-sm font-semibold">初始状态<ResponsiveSelect ariaLabel="选择初始状态" value={form.status} onValueChange={next => setForm({ ...form, status: next })} className="mt-2 w-full" options={statusOptions.filter(item => item.value).map(item => ({ value: item.value, label: item.label }))} /></label><label className="flex cursor-pointer items-center gap-3 rounded-2xl border border-border bg-background p-4 text-sm"><input type="checkbox" checked={form.allowDownload} onChange={event => setForm({ ...form, allowDownload: event.target.checked })} className="h-4 w-4 accent-[var(--accent)]" />允许有权限的用户下载原始 PDF</label><label className="flex cursor-pointer items-center justify-center rounded-2xl border border-dashed border-[var(--accent)] bg-[var(--accent-soft)] p-6 text-center text-sm"><input type="file" accept="application/pdf,.pdf" className="hidden" onChange={event => setFile(event.target.files?.[0])} /><span><FileUp className="mx-auto mb-2 h-6 w-6 text-[var(--accent)]" />{file ? file.name : '点击选择 PDF 文件'}<small className="mt-1 block text-xs text-muted-foreground">单个文件不超过系统上传限制</small></span></label></div><div className="mt-7 flex justify-end gap-3"><Button variant="secondary" onClick={() => setUploadOpen(false)}>取消</Button><Button disabled={busy === 'upload'} onClick={() => void submitUpload()}>{busy === 'upload' ? <><Loader2 className="h-4 w-4 animate-spin" />上传中…</> : '开始上传'}</Button></div></div></div>}

    {permissionTarget && <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="permission-title"><div className="mx-auto my-8 max-w-2xl rounded-[28px] bg-surface p-6 shadow-2xl"><div className="flex items-start justify-between"><div><p className="text-sm font-semibold text-[var(--accent)]">访问控制</p><h2 id="permission-title" className="mt-1 text-2xl font-bold">设置查看权限</h2><p className="mt-2 text-sm text-muted-foreground">“{permissionTarget.title}”仅对选中的候选人开放查看和批注。</p></div><Button variant="ghost" className="h-10 w-10 px-0" onClick={() => setPermissionTarget(undefined)} aria-label="关闭权限窗口"><X className="h-5 w-5" /></Button></div>{permissionLoading ? <p className="p-12 text-center text-sm text-muted-foreground">正在加载候选人…</p> : <div className="mt-6 max-h-[50vh] space-y-2 overflow-y-auto">{candidates.map(candidate => { const id = String(candidate.id); const checked = selectedUsers.includes(id); return <label key={id} className="flex cursor-pointer items-center gap-3 rounded-2xl border border-border p-4 transition hover:border-[var(--accent)] hover:bg-[var(--accent-soft)]"><input type="checkbox" checked={checked} onChange={() => setSelectedUsers(previous => checked ? previous.filter(item => item !== id) : [...previous, id])} className="h-4 w-4 accent-[var(--accent)]" /><span className="min-w-0 flex-1"><strong className="block">{candidate.realName || candidate.username}</strong><small className="text-muted-foreground">{candidate.username}</small></span><KeyRound className="h-4 w-4 text-muted-foreground" /></label>})}{!candidates.length && <p className="py-10 text-center text-sm text-muted-foreground">暂无可授权候选人</p>}</div>}<div className="mt-7 flex justify-end gap-3"><Button variant="secondary" onClick={() => setPermissionTarget(undefined)}>取消</Button><Button disabled={permissionLoading || permissionSaving} onClick={() => void savePermissions()}>{permissionSaving ? '保存中…' : `保存权限（${selectedUsers.length} 人）`}</Button></div></div></div>}
    {deleteTarget && <AdminConfirmDialog
      title="删除学习资料"
      description={`确定删除“${deleteTarget.title}”吗？删除后候选人将无法继续查看，关联媒体访问也会被撤销。`}
      confirmLabel="确认删除"
      danger
      busy={busy === deleteTarget.publicId}
      onClose={() => { if (busy !== deleteTarget.publicId) setDeleteTarget(undefined) }}
      onConfirm={() => void remove(deleteTarget)}
    />}
  </div>
}
