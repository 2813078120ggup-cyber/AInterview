import * as pdfjsLib from 'pdfjs-dist'
import type { PDFDocumentProxy } from 'pdfjs-dist'
import workerSrc from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import 'pdfjs-dist/web/pdf_viewer.css'
import { ArrowLeft, ChevronLeft, ChevronRight, Download, Highlighter, Loader2, MessageSquarePlus, Minus, Plus, Save, Trash2, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request, requestBlob } from '@/lib/api'

pdfjsLib.GlobalWorkerOptions.workerSrc = workerSrc

type Resource = { publicId: string; title: string; description?: string; status: string; allowDownload: boolean; originalName?: string; pageCount?: number; canAnnotate: boolean }
type Rect = { x: number; y: number; w: number; h: number }
type Point = { x: number; y: number }
type Annotation = { publicId: string; pageIndex: number; annotationType: string; anchorType: string; geometry: Rect | Rect[] | Point; selectedText?: string; noteContent?: string; style?: Record<string, unknown>; visibility: string; version: number }
type Draft = { annotationType: 'HIGHLIGHT' | 'NOTE'; geometry: Rect[] | Point; selectedText?: string; noteContent: string }

function rectangles(geometry: Annotation['geometry']): Rect[] {
  if (Array.isArray(geometry)) return geometry as Rect[]
  if ('w' in geometry) return [geometry]
  return [{ x: geometry.x - .012, y: geometry.y - .012, w: .024, h: .024 }]
}

function clamp(value: number) { return Math.min(1, Math.max(0, value)) }

export function LearningResourceViewer() {
  const { publicId = '' } = useParams()
  const location = useLocation()
  const admin = location.pathname.startsWith('/admin/')
  const backPath = admin ? '/admin/learning-resources' : '/learning-resources'
  const [resource, setResource] = useState<Resource>()
  const [pdf, setPdf] = useState<PDFDocumentProxy>()
  const [pageNumber, setPageNumber] = useState(1)
  const [scale, setScale] = useState(1.15)
  const [pageSize, setPageSize] = useState({ width: 0, height: 0 })
  const [annotations, setAnnotations] = useState<Annotation[]>([])
  const [draft, setDraft] = useState<Draft>()
  const [selectedId, setSelectedId] = useState('')
  const [noteEditor, setNoteEditor] = useState('')
  const [mode, setMode] = useState<'select' | 'note'>('select')
  const [loading, setLoading] = useState(true)
  const [saveState, setSaveState] = useState('')
  const [error, setError] = useState('')
  const pageRef = useRef<HTMLDivElement>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const textLayerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.all([request<Resource>(`/v1/learning-resources/${publicId}`), requestBlob(`/v1/learning-resources/${publicId}/content`), request<Annotation[]>(`/v1/learning-resources/${publicId}/annotations`)]).then(async ([metadata, blob, items]) => {
      const loaded = await pdfjsLib.getDocument({ data: await blob.arrayBuffer() }).promise
      if (cancelled) return
      setResource(metadata)
      setAnnotations(items)
      setPdf(loaded)
    }).catch(reason => setError(reason instanceof Error ? reason.message : '资料打开失败，请稍后重试。')).finally(() => setLoading(false))
    return () => { cancelled = true }
  }, [publicId])

  const renderPage = useCallback(async () => {
    if (!pdf || !canvasRef.current || !textLayerRef.current) return
    try {
      const page = await pdf.getPage(pageNumber)
      const viewport = page.getViewport({ scale })
      const canvas = canvasRef.current
      const context = canvas.getContext('2d')
      if (!context) throw new Error('当前浏览器不支持 Canvas')
      const pixelRatio = window.devicePixelRatio || 1
      canvas.width = Math.ceil(viewport.width * pixelRatio)
      canvas.height = Math.ceil(viewport.height * pixelRatio)
      canvas.style.width = `${viewport.width}px`
      canvas.style.height = `${viewport.height}px`
      textLayerRef.current.replaceChildren()
      textLayerRef.current.style.width = `${viewport.width}px`
      textLayerRef.current.style.height = `${viewport.height}px`
      setPageSize({ width: viewport.width, height: viewport.height })
      const renderTask = page.render({ canvas, canvasContext: context, viewport, transform: pixelRatio !== 1 ? [pixelRatio, 0, 0, pixelRatio, 0, 0] : undefined })
      await renderTask.promise
      const textContent = await page.getTextContent()
      const textLayer = new pdfjsLib.TextLayer({ textContentSource: textContent, container: textLayerRef.current, viewport })
      await textLayer.render()
    } catch (reason) {
      if (reason instanceof Error && reason.name !== 'RenderingCancelledException') setError('PDF 页面渲染失败，请刷新后重试。')
    }
  }, [pdf, pageNumber, scale])

  useEffect(() => { void renderPage() }, [renderPage])

  const currentAnnotations = useMemo(() => annotations.filter(item => item.pageIndex === pageNumber - 1), [annotations, pageNumber])

  function normalizedRects() {
    const selection = window.getSelection()
    const textLayer = textLayerRef.current
    const page = pageRef.current
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed || !textLayer || !page || !textLayer.contains(selection.anchorNode) || !textLayer.contains(selection.focusNode)) return undefined
    const range = selection.getRangeAt(0)
    const pageRect = page.getBoundingClientRect()
    const rects = Array.from(range.getClientRects()).map(rect => ({ x: clamp((rect.left - pageRect.left) / pageRect.width), y: clamp((rect.top - pageRect.top) / pageRect.height), w: clamp(rect.width / pageRect.width), h: clamp(rect.height / pageRect.height) })).filter(rect => rect.w > .002 && rect.h > .002)
    if (!rects.length) return undefined
    return { rects, selectedText: selection.toString().trim() }
  }

  function captureSelection() {
    if (mode !== 'select') return
    const selected = normalizedRects()
    if (!selected) return
    setDraft({ annotationType: 'HIGHLIGHT', geometry: selected.rects, selectedText: selected.selectedText, noteContent: '' })
    setSaveState('')
  }

  function captureNote(event: React.MouseEvent<HTMLDivElement>) {
    if (mode !== 'note' || !pageRef.current) return
    if ((event.target as HTMLElement).closest('button')) return
    const rect = pageRef.current.getBoundingClientRect()
    setDraft({ annotationType: 'NOTE', geometry: { x: clamp((event.clientX - rect.left) / rect.width), y: clamp((event.clientY - rect.top) / rect.height) }, noteContent: '' })
    setSaveState('')
  }

  async function saveDraft() {
    if (!draft) return
    if (!resource?.canAnnotate) { setError('当前资料没有批注权限。'); return }
    if (draft.annotationType === 'NOTE' && !draft.noteContent.trim()) { setError('请先填写便签内容。'); return }
    setSaveState('保存中…')
    try {
      const created = await request<Annotation>(`/v1/learning-resources/${publicId}/annotations`, { method: 'POST', body: JSON.stringify({ pageIndex: pageNumber - 1, annotationType: draft.annotationType, anchorType: draft.annotationType === 'HIGHLIGHT' ? 'TEXT' : 'POSITION', geometry: draft.geometry, selectedText: draft.selectedText, noteContent: draft.noteContent, visibility: 'PRIVATE' }) })
      setAnnotations(previous => [...previous, created])
      setDraft(undefined)
      window.getSelection()?.removeAllRanges()
      setSaveState('已保存')
      setError('')
    } catch (reason) {
      setSaveState('')
      setError(reason instanceof Error ? reason.message : '批注保存失败，请稍后重试。')
    }
  }

  async function updateNote(annotation: Annotation) {
    setSaveState('保存中…')
    try {
      const updated = await request<Annotation>(`/v1/learning-resources/annotations/${annotation.publicId}`, { method: 'PUT', body: JSON.stringify({ pageIndex: annotation.pageIndex, annotationType: annotation.annotationType, anchorType: annotation.anchorType, geometry: annotation.geometry, selectedText: annotation.selectedText, noteContent: noteEditor, style: annotation.style, visibility: 'PRIVATE', version: annotation.version }) })
      setAnnotations(previous => previous.map(item => item.publicId === updated.publicId ? updated : item))
      setSelectedId('')
      setSaveState('已保存')
    } catch (reason) {
      setSaveState('')
      setError(reason instanceof Error ? reason.message : '批注更新失败，请稍后重试。')
    }
  }

  async function removeAnnotation(annotation: Annotation) {
    try {
      await request(`/v1/learning-resources/annotations/${annotation.publicId}`, { method: 'DELETE' })
      setAnnotations(previous => previous.filter(item => item.publicId !== annotation.publicId))
      setSelectedId('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '批注删除失败，请稍后重试。')
    }
  }

  async function download() {
    try {
      const blob = await requestBlob(`/v1/learning-resources/${publicId}/download`)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = resource?.originalName || 'learning-resource.pdf'
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'PDF 下载失败，请稍后重试。') }
  }

  if (loading) return <div className="grid min-h-[60vh] place-items-center"><Loader2 className="h-8 w-8 animate-spin text-[var(--accent)]" /></div>
  if (!resource) return <div className="py-16 text-center"><p className="font-semibold">{error || '资料不存在或没有查看权限。'}</p><Link to={backPath} className="mt-4 inline-flex text-sm font-semibold text-[var(--accent)]">返回资料中心</Link></div>

  return <div className="space-y-5">
    <header className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between"><div className="min-w-0"><Link to={backPath} className="inline-flex items-center gap-2 text-sm font-semibold text-[var(--accent)] hover:text-foreground"><ArrowLeft className="h-4 w-4" />返回资料中心</Link><h1 className="mt-4 truncate text-2xl font-bold sm:text-3xl">{resource.title}</h1><p className="mt-2 text-sm text-muted-foreground">第 {pageNumber} / {resource.pageCount ?? '—'} 页 · {resource.canAnnotate ? '可以批注和做笔记' : '仅可阅读'}</p></div><div className="flex flex-wrap items-center gap-2"><Button variant={mode === 'select' ? 'primary' : 'secondary'} onClick={() => setMode('select')} disabled={!resource.canAnnotate}><Highlighter className="h-4 w-4" />高亮</Button><Button variant={mode === 'note' ? 'primary' : 'secondary'} onClick={() => setMode('note')} disabled={!resource.canAnnotate}><MessageSquarePlus className="h-4 w-4" />便签</Button>{resource.allowDownload && <Button variant="secondary" onClick={() => void download()}><Download className="h-4 w-4" />下载</Button>}</div></header>
    {error && <p role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}
    <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
      <Card className="min-w-0 overflow-hidden p-0"><div className="flex flex-wrap items-center justify-between gap-3 border-b border-border p-3"><div className="flex items-center gap-2"><Button variant="ghost" className="h-9 w-9 px-0" disabled={pageNumber <= 1} onClick={() => setPageNumber(value => Math.max(1, value - 1))} aria-label="上一页"><ChevronLeft className="h-4 w-4" /></Button><Button variant="ghost" className="h-9 w-9 px-0" disabled={pageNumber >= (resource.pageCount ?? 1)} onClick={() => setPageNumber(value => Math.min(resource.pageCount ?? value, value + 1))} aria-label="下一页"><ChevronRight className="h-4 w-4" /></Button><span className="text-sm font-semibold">第 {pageNumber} 页</span></div><div className="flex items-center gap-1"><Button variant="ghost" className="h-9 w-9 px-0" onClick={() => setScale(value => Math.max(.7, Number((value - .1).toFixed(2))))} aria-label="缩小"><Minus className="h-4 w-4" /></Button><span className="w-14 text-center text-xs text-muted-foreground">{Math.round(scale * 100)}%</span><Button variant="ghost" className="h-9 w-9 px-0" onClick={() => setScale(value => Math.min(2.4, Number((value + .1).toFixed(2))))} aria-label="放大"><Plus className="h-4 w-4" /></Button></div></div><div className="overflow-auto bg-[#e8e4dc] p-4 sm:p-8"><div ref={pageRef} onMouseUp={captureSelection} onClick={captureNote} className="relative mx-auto bg-white shadow-[0_18px_50px_rgba(40,32,20,.18)]" style={{ width: pageSize.width || 'min(100%, 760px)', minHeight: pageSize.height || 480 }}><canvas ref={canvasRef} className="relative z-0 block" /><div ref={textLayerRef} className="textLayer learning-pdf-text-layer" style={{ '--total-scale-factor': scale } as CSSProperties} />{currentAnnotations.map(annotation => rectangles(annotation.geometry).map((rect, index) => annotation.annotationType === 'NOTE' ? <button key={`${annotation.publicId}-${index}`} type="button" onClick={event => { event.stopPropagation(); setSelectedId(annotation.publicId); setNoteEditor(annotation.noteContent || '') }} className="absolute z-20 grid h-7 w-7 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full border-2 border-white bg-[var(--accent)] text-white shadow-lg" style={{ left: `${(rect.x + rect.w / 2) * 100}%`, top: `${(rect.y + rect.h / 2) * 100}%` }} aria-label="打开便签"><MessageSquarePlus className="h-3.5 w-3.5" /></button> : <button key={`${annotation.publicId}-${index}`} type="button" onClick={() => { setSelectedId(annotation.publicId); setNoteEditor(annotation.noteContent || '') }} className="absolute z-10 rounded-sm bg-yellow-300/45 text-left transition hover:bg-yellow-300/70" style={{ left: `${rect.x * 100}%`, top: `${rect.y * 100}%`, width: `${rect.w * 100}%`, height: `${rect.h * 100}%` }} aria-label="打开高亮批注" />))}{draft && <div className="pointer-events-none absolute inset-0 z-30">{rectangles(draft.geometry).map((rect, index) => draft.annotationType === 'NOTE' ? <span key={index} className="absolute grid h-7 w-7 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full border-2 border-white bg-[var(--accent)] text-white shadow-lg" style={{ left: `${(rect.x + rect.w / 2) * 100}%`, top: `${(rect.y + rect.h / 2) * 100}%` }}><MessageSquarePlus className="h-3.5 w-3.5" /></span> : <span key={index} className="absolute rounded-sm bg-yellow-300/50" style={{ left: `${rect.x * 100}%`, top: `${rect.y * 100}%`, width: `${rect.w * 100}%`, height: `${rect.h * 100}%` }} />)}</div>}</div></div></Card>
      <aside className="space-y-5"><Card><div className="flex items-center justify-between"><div><h2 className="font-bold">我的批注</h2><p className="mt-1 text-xs text-muted-foreground">当前资料的私人笔记</p></div><Badge tone="info">{annotations.length}</Badge></div><div className="mt-5 space-y-3">{currentAnnotations.map(annotation => <button key={annotation.publicId} type="button" onClick={() => { setSelectedId(annotation.publicId); setNoteEditor(annotation.noteContent || '') }} className="w-full rounded-2xl border border-border p-3 text-left transition hover:border-[var(--accent)] hover:bg-[var(--accent-soft)]"><div className="flex items-center justify-between gap-2"><span className="text-xs font-semibold text-[var(--accent)]">{annotation.annotationType === 'NOTE' ? '便签' : '高亮'} · 第 {annotation.pageIndex + 1} 页</span><span className="text-[10px] text-muted-foreground">{annotation.noteContent ? '有笔记' : '未添加笔记'}</span></div>{annotation.selectedText && <p className="mt-2 line-clamp-2 text-xs leading-5 text-muted-foreground">“{annotation.selectedText}”</p>}{annotation.noteContent && <p className="mt-2 line-clamp-2 text-sm leading-5">{annotation.noteContent}</p>}</button>)}{!currentAnnotations.length && <p className="py-6 text-center text-sm text-muted-foreground">选择文字后点击高亮，或切换便签模式在页面上点击。</p>}</div></Card>{draft && <Card className="border-[var(--accent)]"><div className="flex items-start justify-between gap-3"><div><h2 className="font-bold">{draft.annotationType === 'NOTE' ? '新建便签' : '为高亮添加笔记'}</h2><p className="mt-1 text-xs text-muted-foreground">保存后只对你自己可见。</p></div><Button variant="ghost" className="h-8 w-8 px-0" onClick={() => setDraft(undefined)} aria-label="取消批注"><X className="h-4 w-4" /></Button></div>{draft.selectedText && <p className="mt-4 rounded-xl bg-muted p-3 text-xs leading-5 text-muted-foreground">“{draft.selectedText}”</p>}<textarea value={draft.noteContent} onChange={event => setDraft(previous => previous ? { ...previous, noteContent: event.target.value } : previous)} className="mt-4 min-h-24 w-full rounded-2xl border border-border bg-background p-3 text-sm outline-none focus:border-[var(--accent)]" placeholder={draft.annotationType === 'NOTE' ? '写下你的学习笔记…' : '可选：补充这段内容的理解…'} /><Button className="mt-3 w-full" onClick={() => void saveDraft()}><Save className="h-4 w-4" />{saveState || '保存批注'}</Button></Card>}{selectedId && <Card><div className="flex items-start justify-between gap-3"><h2 className="font-bold">编辑笔记</h2><Button variant="ghost" className="h-8 w-8 px-0" onClick={() => setSelectedId('')} aria-label="关闭编辑"><X className="h-4 w-4" /></Button></div><textarea value={noteEditor} onChange={event => setNoteEditor(event.target.value)} className="mt-4 min-h-24 w-full rounded-2xl border border-border bg-background p-3 text-sm outline-none focus:border-[var(--accent)]" placeholder="更新笔记内容…" /><div className="mt-3 flex gap-2"><Button className="flex-1" onClick={() => { const annotation = annotations.find(item => item.publicId === selectedId); if (annotation) void updateNote(annotation) }}><Save className="h-4 w-4" />保存</Button><Button variant="danger" className="h-11 w-11 px-0" onClick={() => { const annotation = annotations.find(item => item.publicId === selectedId); if (annotation) void removeAnnotation(annotation) }} aria-label="删除批注"><Trash2 className="h-4 w-4" /></Button></div></Card>}</aside>
    </div>
  </div>
}
