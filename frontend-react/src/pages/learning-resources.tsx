import { BookOpen, ChevronLeft, ChevronRight, FileText, LockKeyhole, Sparkles } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'

type Resource = { publicId: string; title: string; description?: string; originalName?: string; fileSize?: number; pageCount?: number; canAnnotate: boolean }
type PageResult = { records: Resource[]; total: number; pageNo: number; pageSize: number }

function formatSize(value?: number) {
  if (!value) return '—'
  if (value < 1024 * 1024) return `${Math.max(1, Math.round(value / 1024))} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

export function LearningResources() {
  const [page, setPage] = useState<PageResult>({ records: [], total: 0, pageNo: 1, pageSize: 12 })
  const [pageNo, setPageNo] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    setLoading(true)
    void request<PageResult>(`/v1/learning-resources/page?pageNo=${pageNo}&pageSize=12`).then(value => { setPage(value); setError('') }).catch(reason => setError(reason instanceof Error ? reason.message : '学习资料加载失败，请稍后重试。')).finally(() => setLoading(false))
  }, [pageNo])

  const totalPages = Math.max(1, Math.ceil(page.total / page.pageSize))

  return <div className="space-y-7">
    <header><p className="text-sm font-semibold text-[var(--accent)]">学习与成长</p><h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">学习资料中心</h1><p className="mt-3 max-w-2xl text-muted-foreground">查看管理员为你开放的 PDF 资料，在阅读过程中高亮重点、记录自己的学习笔记。</p></header>
    {error && <p role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}
    {loading ? <Card><p className="py-8 text-center text-sm text-muted-foreground">正在加载资料…</p></Card> : <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">{page.records.map((item, index) => <Card key={item.publicId} motionDelay={index * .04} className="flex min-h-72 flex-col"><div className="flex items-start justify-between"><span className="grid h-12 w-12 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><BookOpen className="h-5 w-5" /></span><Badge tone="success"><Sparkles className="h-3 w-3" />可查看</Badge></div><h2 className="mt-6 text-xl font-bold">{item.title}</h2><p className="mt-3 line-clamp-3 flex-1 text-sm leading-6 text-muted-foreground">{item.description || '管理员暂未添加资料说明。'}</p><div className="mt-5 flex flex-wrap gap-3 text-xs text-muted-foreground"><span className="inline-flex items-center gap-1"><FileText className="h-3.5 w-3.5" />{item.pageCount ?? '—'} 页</span><span>{formatSize(item.fileSize)}</span>{item.canAnnotate && <span className="inline-flex items-center gap-1"><LockKeyhole className="h-3.5 w-3.5" />可批注</span>}</div><Link to={`/learning-resources/${item.publicId}`} className="mt-6 inline-flex h-11 items-center justify-center rounded-full bg-[var(--primary)] px-5 text-sm font-semibold text-[var(--primary-foreground)] shadow-[0_12px_30px_rgba(20,18,17,.14)] transition hover:-translate-y-0.5">打开资料</Link></Card>)}{!page.records.length && <Card className="col-span-full"><div className="py-12 text-center"><BookOpen className="mx-auto h-8 w-8 text-muted-foreground" /><p className="mt-4 font-semibold">暂时没有开放的资料</p><p className="mt-2 text-sm text-muted-foreground">管理员授权后，资料会显示在这里。</p></div></Card>}</div>}
    {!loading && page.total > 0 && <div className="flex flex-col gap-3 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between"><span>共 {page.total} 份资料 · 第 {page.pageNo}/{totalPages} 页</span><div className="grid grid-cols-2 gap-2 sm:flex"><Button variant="secondary" className="h-11 sm:h-9" disabled={pageNo <= 1} onClick={() => setPageNo(value => value - 1)}><ChevronLeft className="h-4 w-4" />上一页</Button><Button variant="secondary" className="h-11 sm:h-9" disabled={pageNo >= totalPages} onClick={() => setPageNo(value => value + 1)}>下一页<ChevronRight className="h-4 w-4" /></Button></div></div>}
  </div>
}
