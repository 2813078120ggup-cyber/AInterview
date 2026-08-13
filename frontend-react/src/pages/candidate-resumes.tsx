import { CheckCircle2, FileText, Loader2, RefreshCw, ShieldCheck, Star, Trash2, UploadCloud } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { AdminConfirmDialog } from '@/components/admin-confirm-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request, requestBlob, upload } from '@/lib/api'
import { formatDateTime, type Resume, type ResumeParseTask } from '@/lib/recruitment'

const parseMeta: Record<Resume['parseStatus'], { label: string; tone: 'default' | 'success' | 'warning' | 'danger' | 'info' }> = {
  MANUAL: { label: '演示资料', tone: 'default' },
  PENDING: { label: '等待解析', tone: 'warning' },
  PROCESSING: { label: '正在解析', tone: 'info' },
  SUCCESS: { label: '解析完成', tone: 'success' },
  FAILED: { label: '解析失败', tone: 'danger' },
}

const MAX_FILE_BYTES = 10 * 1024 * 1024

export function CandidateResumes() {
  const [resumes, setResumes] = useState<Resume[]>([])
  const [title, setTitle] = useState('')
  const [defaultResume, setDefaultResume] = useState(true)
  const [busy, setBusy] = useState<string>()
  const [selectedFile, setSelectedFile] = useState<File>()
  const [pendingDelete, setPendingDelete] = useState<Resume>()
  const [loading, setLoading] = useState(true)
  const [hasLoaded, setHasLoaded] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await request<Resume[]>('/v1/recruitment/resumes')
      setResumes(result)
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '简历加载失败')
    } finally {
      setHasLoaded(true)
      setLoading(false)
    }
  }, [])

  useEffect(() => { void load() }, [load])

  useEffect(() => {
    if (!resumes.some(item => item.parseStatus === 'PENDING' || item.parseStatus === 'PROCESSING')) return
    const timer = window.setInterval(() => { void load() }, 3000)
    return () => window.clearInterval(timer)
  }, [load, resumes])

  async function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedFile) {
      setError('请先选择 PDF、DOCX、TXT 或 Markdown 简历')
      return
    }
    if (selectedFile.size > MAX_FILE_BYTES) {
      setError('简历文件不能超过 10MB，请压缩文件后重试。')
      return
    }
    setBusy('upload')
    setError('')
    setMessage('')
    try {
      const form = new FormData()
      form.append('file', selectedFile)
      if (title.trim()) form.append('title', title.trim())
      form.append('defaultResume', String(defaultResume))
      await upload<Resume>('/v1/recruitment/resumes', form)
      setSelectedFile(undefined)
      setTitle('')
      if (inputRef.current) inputRef.current.value = ''
      setMessage('简历上传成功，系统正在生成结构化画像。')
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '简历上传失败')
    } finally {
      setBusy(undefined)
    }
  }

  async function retry(id: string) {
    setBusy(`retry-${id}`)
    setError('')
    try {
      await request<ResumeParseTask>(`/v1/recruitment/resumes/${id}/parse/retry`, { method: 'POST' })
      setMessage('已重新提交解析任务。')
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '重新解析失败')
    } finally {
      setBusy(undefined)
    }
  }

  async function setDefault(id: string) {
    setBusy(`default-${id}`)
    setError('')
    try {
      await request<Resume>(`/v1/recruitment/resumes/${id}/default`, { method: 'PUT' })
      setMessage('默认简历已更新。')
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '默认简历更新失败')
    } finally {
      setBusy(undefined)
    }
  }

  async function remove() {
    if (!pendingDelete) return
    const id = pendingDelete.id
    setBusy(`delete-${id}`)
    setError('')
    try {
      await request<void>(`/v1/recruitment/resumes/${id}`, { method: 'DELETE' })
      setMessage('简历已删除。')
      setPendingDelete(undefined)
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '简历删除失败')
    } finally {
      setBusy(undefined)
    }
  }

  async function download(resume: Resume) {
    setBusy(`download-${resume.id}`)
    setError('')
    try {
      const blob = await requestBlob(`/v1/recruitment/resumes/${resume.id}/content`)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = resume.fileName || `${resume.title}.pdf`
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '简历文件读取失败')
    } finally {
      setBusy(undefined)
    }
  }

  return <div className="space-y-6">
    <header className="overflow-hidden rounded-[28px] border border-border bg-[linear-gradient(135deg,var(--surface),var(--accent-soft))] p-5 sm:p-8">
      <div className="max-w-3xl">
        <p className="text-sm font-bold text-[var(--accent)]">个人资料</p>
        <h1 className="mt-2 text-3xl font-black tracking-[-.04em] sm:text-4xl">简历管理</h1>
        <p className="mt-3 max-w-2xl leading-7 text-muted-foreground">上传并维护可用于岗位申请的简历。系统会提取技能、项目和经历信息，文件仅在授权关系内可见。</p>
      </div>
    </header>

    <div aria-live="polite" aria-atomic="true">
      {message && <p role="status" className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-200"><CheckCircle2 className="mr-2 inline h-4 w-4" aria-hidden="true" />{message}</p>}
      {error && <p role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-200">{error}</p>}
    </div>

    <Card className="p-5 sm:p-6">
      <div className="flex items-start gap-3">
        <div className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><UploadCloud className="h-5 w-5" /></div>
        <div><h2 className="text-lg font-black">上传新简历</h2><p className="mt-1 text-sm text-muted-foreground">支持 PDF、DOCX、TXT、Markdown，单文件不超过 10MB。</p></div>
      </div>
      <form aria-label="上传新简历" className="mt-5 grid gap-4 lg:grid-cols-[minmax(0,1fr)_220px_auto]" onSubmit={event => void handleUpload(event)}>
        <div className="space-y-2">
          <label htmlFor="resume-file" className="text-sm font-semibold">简历文件 <span className="text-rose-500">*</span></label>
          <input ref={inputRef} id="resume-file" name="resume-file" type="file" required accept=".pdf,.docx,.txt,.md,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain,text/markdown" aria-describedby="resume-file-help" onChange={event => setSelectedFile(event.target.files?.[0])} className="block min-h-11 w-full rounded-2xl border border-border bg-background px-3 py-2 text-sm file:mr-3 file:rounded-xl file:border-0 file:bg-muted file:px-3 file:py-2 file:text-sm file:font-semibold" />
          <p id="resume-file-help" className="text-xs text-muted-foreground">{selectedFile ? `${selectedFile.name} · ${(selectedFile.size / 1024 / 1024).toFixed(2)}MB` : '上传后会自动执行文件签名和安全校验。'}</p>
        </div>
        <div className="space-y-2"><label htmlFor="resume-title" className="text-sm font-semibold">简历名称</label><input id="resume-title" name="title" autoComplete="off" value={title} onChange={event => setTitle(event.target.value)} placeholder="例如：Java 后端开发简历" className="h-11 w-full rounded-2xl border border-border bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]" /></div>
        <div className="flex items-end gap-3 lg:flex-col lg:items-stretch lg:justify-end"><label className="flex min-h-11 items-center gap-2 text-sm"><input name="defaultResume" type="checkbox" checked={defaultResume} onChange={event => setDefaultResume(event.target.checked)} className="h-4 w-4 accent-[var(--brand)]" />设为默认简历</label><Button type="submit" disabled={busy === 'upload'} className="min-h-11">{busy === 'upload' ? <><Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />上传中</> : <><UploadCloud className="h-4 w-4" aria-hidden="true" />开始上传</>}</Button></div>
      </form>
    </Card>

    <section className="min-w-0 space-y-4" aria-labelledby="resume-list-title">
      <div className="flex items-end justify-between gap-3"><div><p className="text-sm font-bold text-[var(--accent)]">我的简历</p><h2 id="resume-list-title" className="mt-1 text-2xl font-black tracking-[-.03em]">简历与解析状态</h2></div><span className="text-sm text-muted-foreground">{resumes.length} 份资料</span></div>
      {loading && !hasLoaded ? <ResumeListSkeleton /> : error && !resumes.length ? <Card className="grid min-h-48 place-items-center text-center"><div><RefreshCw className="mx-auto h-8 w-8 text-muted-foreground" aria-hidden="true" /><h3 className="mt-3 font-bold">简历暂时加载失败</h3><p className="mt-1 text-sm text-muted-foreground">请重试后再查看你的简历和解析状态。</p><Button type="button" variant="secondary" className="mt-4" onClick={() => void load()}><RefreshCw className="h-4 w-4" aria-hidden="true" />重新加载</Button></div></Card> : !resumes.length ? <Card className="grid min-h-48 place-items-center text-center"><div><FileText className="mx-auto h-8 w-8 text-muted-foreground" aria-hidden="true" /><h3 className="mt-3 font-bold">还没有上传简历</h3><p className="mt-1 text-sm text-muted-foreground">上传后才能更准确地参与岗位匹配。</p></div></Card> : <div className="grid min-w-0 gap-4 lg:grid-cols-2">{resumes.map(resume => {
        const status = parseMeta[resume.parseStatus]
        const isParsing = resume.parseStatus === 'PENDING' || resume.parseStatus === 'PROCESSING'
        return <Card key={resume.id} className="flex min-h-[260px] min-w-0 flex-col p-5 sm:p-6">
          <div className="flex min-w-0 flex-wrap items-start justify-between gap-3"><div className="flex min-w-0 items-center gap-3"><div className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-muted"><FileText className="h-5 w-5 text-[var(--accent)]" /></div><div className="min-w-0"><h3 className="truncate font-black">{resume.title}</h3><p className="mt-1 truncate text-xs text-muted-foreground">{resume.fileName || '未绑定文件'}</p></div></div><div className="flex max-w-full shrink-0 flex-wrap items-center justify-end gap-2">{resume.defaultResume && <Badge tone="success"><Star className="mr-1 h-3 w-3" />默认</Badge>}<Badge tone={status.tone}>{isParsing && <Loader2 className="mr-1 h-3 w-3 animate-spin" />}{status.label}</Badge></div></div>
          <p className="mt-5 line-clamp-3 min-h-[4.5rem] text-sm leading-6 text-muted-foreground">{resume.summary || (isParsing ? '简历已安全保存，正在提取技能和项目证据。' : resume.parseError || '等待结构化解析。')}</p>
          <div className="mt-4 flex min-h-7 flex-wrap gap-1.5">{resume.skills.slice(0, 8).map(skill => <span key={skill} className="rounded-full border border-border px-2.5 py-1 text-xs">{skill}</span>)}</div>
          <div className="mt-auto flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4"><p className="flex items-center gap-1.5 text-xs text-muted-foreground"><ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" />最近更新 {formatDateTime(resume.updatedAt)}</p><div className="flex flex-wrap gap-2"><Button type="button" variant="ghost" className="h-10 px-3 text-xs" disabled={!resume.mediaId || busy === `download-${resume.id}`} onClick={() => void download(resume)}>{busy === `download-${resume.id}` ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <FileText className="h-4 w-4" aria-hidden="true" />}查看文件</Button>{!resume.defaultResume && <Button type="button" variant="secondary" className="h-10 px-3 text-xs" disabled={Boolean(busy)} onClick={() => void setDefault(resume.id)}>设为默认</Button>}{resume.parseStatus === 'FAILED' && <Button type="button" variant="secondary" className="h-10 px-3 text-xs" disabled={Boolean(busy)} onClick={() => void retry(resume.id)}>{busy === `retry-${resume.id}` ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <RefreshCw className="h-4 w-4" aria-hidden="true" />}重试解析</Button>}<Button type="button" variant="ghost" className="h-10 w-10 px-0" disabled={Boolean(busy)} aria-label={`删除${resume.title}`} onClick={() => setPendingDelete(resume)}><Trash2 className="h-4 w-4 text-rose-600" aria-hidden="true" /></Button></div></div>
        </Card>
      })}</div>}
    </section>
    {pendingDelete && <AdminConfirmDialog title={`删除${pendingDelete.title}？`} description="删除后将无法从简历中心查看这份文件；已关联岗位申请的简历会由后端拒绝删除。" confirmLabel="确认删除" danger busy={busy === `delete-${pendingDelete.id}`} onClose={() => { if (!busy) setPendingDelete(undefined) }} onConfirm={() => void remove()} />}
  </div>
}

function ResumeListSkeleton() {
  return <div role="status" aria-label="正在加载简历" aria-busy="true" className="grid min-w-0 gap-4 lg:grid-cols-2">{Array.from({ length: 2 }, (_, index) => <div key={index} aria-hidden="true" className="min-h-[260px] animate-pulse rounded-[24px] border border-border bg-surface p-5 sm:p-6"><div className="flex gap-3"><div className="h-11 w-11 rounded-2xl bg-muted" /><div className="flex-1"><div className="h-5 w-2/5 rounded bg-muted" /><div className="mt-3 h-3 w-1/3 rounded bg-muted" /></div><div className="h-6 w-16 rounded-full bg-muted" /></div><div className="mt-6 h-4 w-full rounded bg-muted" /><div className="mt-3 h-4 w-4/5 rounded bg-muted" /><div className="mt-8 h-4 w-1/2 rounded bg-muted" /><div className="mt-10 h-10 w-full rounded bg-muted" /></div>)}</div>
}
