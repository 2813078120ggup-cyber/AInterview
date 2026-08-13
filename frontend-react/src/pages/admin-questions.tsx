import { ArrowLeft, Pencil, Plus, Search, Trash2, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { AdminConfirmDialog } from '@/components/admin-confirm-dialog'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'

type Bank = { id: string; bankCode: string; name: string; description?: string }
type Question = {
  id: string
  questionType: string
  difficulty: number
  content: string
  options?: string
  correctAnswer?: string
  answerTemplate?: string
  explanation?: string
  tags?: string
  score: number
  source?: string
  sortOrder?: number
  status?: number
}
type Page<T> = { records: T[] }
type QuestionForm = {
  questionType: string
  difficulty: number
  content: string
  options: string
  correctAnswer: string
  answerTemplate: string
  explanation: string
  tags: string
  score: number
  source: string
  sortOrder: number
  status: number
}

const labels: Record<string, string> = {
  short_answer: '简答题',
  single_choice: '单选题',
  multiple_choice: '多选题',
  true_false: '判断题',
  coding: '编程题',
}

const emptyForm: QuestionForm = {
  questionType: 'short_answer',
  difficulty: 2,
  content: '',
  options: '',
  correctAnswer: '',
  answerTemplate: '',
  explanation: '',
  tags: '',
  score: 10,
  source: 'manual',
  sortOrder: 0,
  status: 1,
}

const statusText: Record<number, string> = { 0: '草稿', 1: '已发布', 2: '已停用' }

function toForm(question: Question): QuestionForm {
  return {
    questionType: question.questionType,
    difficulty: question.difficulty,
    content: question.content,
    options: question.options ?? '',
    correctAnswer: question.correctAnswer ?? '',
    answerTemplate: question.answerTemplate ?? '',
    explanation: question.explanation ?? '',
    tags: question.tags ?? '',
    score: Number(question.score ?? 10),
    source: question.source ?? 'manual',
    sortOrder: Number(question.sortOrder ?? 0),
    status: Number(question.status ?? 1),
  }
}

function questionSummary(question: Question) {
  return question.answerTemplate || question.explanation || '尚未填写参考答案说明'
}

export function AdminQuestions() {
  const { id = '' } = useParams()
  const nav = useNavigate()
  const [bank, setBank] = useState<Bank>()
  const [items, setItems] = useState<Question[]>([])
  const [keyword, setKeyword] = useState('')
  const [type, setType] = useState('')
  const [difficulty, setDifficulty] = useState('')
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Question | null>(null)
  const [saving, setSaving] = useState(false)
  const [deletingId, setDeletingId] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<Question>()
  const [error, setError] = useState('')
  const [form, setForm] = useState<QuestionForm>(emptyForm)

  async function load() {
    setLoading(true)
    try {
      const [detail, page] = await Promise.all([
        request<Bank>(`/v1/question-banks/${id}`),
        request<Page<Question>>(`/v1/question-banks/${id}/questions?pageNo=1&pageSize=100&keyword=${encodeURIComponent(keyword)}${type ? `&questionType=${type}` : ''}${difficulty ? `&difficulty=${difficulty}` : ''}`),
      ])
      setBank(detail)
      setItems(page.records)
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '题目列表加载失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [id])

  function openCreate() {
    setEditing(null)
    setForm(emptyForm)
    setOpen(true)
  }

  function openEdit(question: Question) {
    setEditing(question)
    setForm(toForm(question))
    setOpen(true)
  }

  async function save() {
    if (!form.content.trim()) {
      setError('请填写题目内容。')
      return
    }
    setSaving(true)
    try {
      if (editing) {
        const item = await request<Question>(`/v1/question-banks/${id}/questions/${editing.id}`, { method: 'PUT', body: JSON.stringify(form) })
        setItems(previous => previous.map(question => question.id === item.id ? item : question))
      } else {
        const item = await request<Question>(`/v1/question-banks/${id}/questions`, { method: 'POST', body: JSON.stringify(form) })
        setItems(previous => [item, ...previous])
      }
      setOpen(false)
      setEditing(null)
      setForm(emptyForm)
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : editing ? '题目修改失败，请稍后重试。' : '题目创建失败，请稍后重试。')
    } finally {
      setSaving(false)
    }
  }

  async function remove(question: Question) {
    setDeletingId(question.id)
    try {
      await request(`/v1/question-banks/${id}/questions/${question.id}`, { method: 'DELETE' })
      setItems(previous => previous.filter(item => item.id !== question.id))
      setError('')
      setDeleteTarget(undefined)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '题目删除失败，请稍后重试。')
    } finally {
      setDeletingId('')
    }
  }

  return <div className="space-y-6">
    <header className="flex flex-col gap-4 rounded-[24px] border border-border bg-surface p-5 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <Button type="button" variant="ghost" className="mb-2 -ml-3 h-9 px-3 text-sm text-muted-foreground hover:text-foreground" onClick={() => nav('/admin/question-banks')}><ArrowLeft className="h-4 w-4" />返回题库</Button>
        <p className="text-sm font-semibold text-[var(--accent)]">题目配置</p>
        <h1 className="mt-1 text-2xl font-bold">{bank?.name || '题目维护'}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{bank?.bankCode} · {bank?.description || '管理面试题目与评分配置'}</p>
      </div>
      <Button onClick={openCreate}><Plus className="h-4 w-4" />新增题目</Button>
    </header>

    {error && <p className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}

    <Card className="mt-6 p-0">
      <div className="flex flex-col gap-3 border-b border-border p-5 md:flex-row">
        <label className="flex h-11 flex-1 items-center gap-2 rounded-xl border border-border px-3">
          <Search className="h-4 w-4 text-muted-foreground" />
          <input value={keyword} onChange={event => setKeyword(event.target.value)} onKeyDown={event => event.key === 'Enter' && void load()} className="w-full bg-transparent text-sm outline-none" placeholder="搜索题目内容或标签" />
        </label>
        <ResponsiveSelect
          ariaLabel="选择题型"
          value={type}
          onValueChange={setType}
          className="w-full md:w-40"
          options={[{ value: "", label: "全部题型" }, ...Object.entries(labels).map(([value, label]) => ({ value, label }))]}
        />
        <ResponsiveSelect
          ariaLabel="选择难度"
          value={difficulty}
          onValueChange={setDifficulty}
          className="w-full md:w-40"
          options={[{ value: "", label: "全部难度" }, ...[1, 2, 3].map(value => ({ value: String(value), label: `难度 ${value}` }))]}
        />
        <Button variant="secondary" onClick={() => void load()}>筛选</Button>
      </div>

      {loading ? <p className="p-12 text-center text-sm text-muted-foreground">正在加载题目…</p> : <div className="divide-y divide-border">
        {items.map((question, index) => <article key={question.id} className="group flex gap-4 p-5 transition hover:bg-muted/20">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[var(--accent-soft)] text-sm font-bold text-[var(--accent)]">{String(index + 1).padStart(2, '0')}</span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="info">{labels[question.questionType] || question.questionType}</Badge>
              <Badge tone={question.difficulty === 3 ? 'warning' : 'default'}>难度 {question.difficulty}</Badge>
              <Badge tone={question.status === 1 ? 'success' : question.status === 2 ? 'default' : 'warning'}>{statusText[question.status ?? 1]}</Badge>
              <span className="text-xs text-muted-foreground">{question.score} 分</span>
            </div>
            <h2 className="mt-3 font-semibold leading-6">{question.content}</h2>
            <p className="mt-2 text-sm text-muted-foreground">{questionSummary(question)}</p>
            {question.tags && <p className="mt-2 text-xs text-muted-foreground">标签：{question.tags}</p>}
          </div>
          <div className="flex shrink-0 flex-col gap-2 sm:flex-row">
            <Button variant="secondary" className="h-9 px-3" onClick={() => openEdit(question)}><Pencil className="h-3.5 w-3.5" />修改</Button>
            <Button type="button" variant="secondary" className="h-9 border-rose-200 bg-rose-50/70 px-3 text-rose-700 hover:border-rose-300 hover:bg-rose-100 hover:text-rose-800 dark:border-rose-900/60 dark:bg-rose-950/20 dark:text-rose-200 dark:hover:bg-rose-950/40" disabled={deletingId === question.id || Boolean(deleteTarget)} onClick={() => setDeleteTarget(question)}><Trash2 className="h-3.5 w-3.5" />删除</Button>
          </div>
        </article>)}
        {!items.length && <p className="p-12 text-center text-sm text-muted-foreground">暂无符合条件的题目</p>}
      </div>}
    </Card>

    {open && <div className="fixed inset-0 z-50 overflow-y-auto bg-[var(--primary)]/35 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="question-dialog-title">
      <div className="mx-auto my-4 max-w-3xl rounded-[24px] bg-surface p-5 shadow-2xl sm:my-8 sm:rounded-[28px] sm:p-6">
        <div className="flex justify-between">
          <div>
            <p className="text-sm font-semibold text-[var(--accent)]">{editing ? '编辑题目' : '题目配置'}</p>
            <h2 id="question-dialog-title" className="mt-1 text-2xl font-bold">{editing ? '修改题目' : '新增题目'}</h2>
          </div>
          <Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={() => setOpen(false)} aria-label="关闭题目对话框"><X className="h-5 w-5" /></Button>
        </div>

        <div className="mt-6 grid gap-5 sm:grid-cols-2">
          <label className="text-sm font-semibold">题型<ResponsiveSelect
            ariaLabel="选择题型"
            value={form.questionType}
            onValueChange={next => setForm({ ...form, questionType: next })}
            className="mt-2 w-full"
            options={Object.entries(labels).map(([value, label]) => ({ value, label }))}
          /></label>
          <label className="text-sm font-semibold">难度<ResponsiveSelect
            ariaLabel="选择难度"
            value={String(form.difficulty)}
            onValueChange={next => setForm({ ...form, difficulty: Number(next) })}
            className="mt-2 w-full"
            options={[1, 2, 3].map(value => ({ value: String(value), label: `难度 ${value}` }))}
          /></label>
          <label className="sm:col-span-2 text-sm font-semibold">题目内容<textarea value={form.content} onChange={event => setForm({ ...form, content: event.target.value })} className="mt-2 min-h-28 w-full rounded-xl border border-border bg-background p-3 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="sm:col-span-2 text-sm font-semibold">选项 JSON（选择题 / 判断题填写）<textarea value={form.options} onChange={event => setForm({ ...form, options: event.target.value })} className="mt-2 min-h-20 w-full rounded-xl border border-border bg-background p-3 font-normal outline-none focus:border-[var(--accent)]" placeholder='例如：[{"key":"A","text":"选项内容"}]' /></label>
          <label className="sm:col-span-2 text-sm font-semibold">标准答案 JSON（选择题 / 判断题填写）<textarea value={form.correctAnswer} onChange={event => setForm({ ...form, correctAnswer: event.target.value })} className="mt-2 min-h-16 w-full rounded-xl border border-border bg-background p-3 font-normal outline-none focus:border-[var(--accent)]" placeholder='例如：["A"]' /></label>
          <label className="sm:col-span-2 text-sm font-semibold">参考答案 / 回答模板<textarea value={form.answerTemplate} onChange={event => setForm({ ...form, answerTemplate: event.target.value })} className="mt-2 min-h-20 w-full rounded-xl border border-border bg-background p-3 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="sm:col-span-2 text-sm font-semibold">解析<textarea value={form.explanation} onChange={event => setForm({ ...form, explanation: event.target.value })} className="mt-2 min-h-20 w-full rounded-xl border border-border bg-background p-3 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="text-sm font-semibold">分值<input type="number" min="0" step="0.5" value={form.score} onChange={event => setForm({ ...form, score: Number(event.target.value) })} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="text-sm font-semibold">状态<ResponsiveSelect
            ariaLabel="选择状态"
            value={String(form.status)}
            onValueChange={next => setForm({ ...form, status: Number(next) })}
            className="mt-2 w-full"
            options={[
              { value: "1", label: "已发布" },
              { value: "0", label: "草稿" },
              { value: "2", label: "已停用" },
            ]}
          /></label>
          <label className="text-sm font-semibold">排序<input type="number" value={form.sortOrder} onChange={event => setForm({ ...form, sortOrder: Number(event.target.value) })} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="text-sm font-semibold">标签<input value={form.tags} onChange={event => setForm({ ...form, tags: event.target.value })} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 font-normal outline-none focus:border-[var(--accent)]" placeholder='例如：["Spring","IOC"]' /></label>
        </div>
        <div className="mt-7 flex justify-end gap-3">
          <Button variant="secondary" onClick={() => setOpen(false)}>取消</Button>
          <Button disabled={saving} onClick={() => void save()}>{saving ? '正在保存…' : editing ? '保存修改' : '保存题目'}</Button>
        </div>
      </div>
    </div>}
    {deleteTarget && <AdminConfirmDialog
      title="删除题目"
      description={`确认删除题目「${deleteTarget.content.slice(0, 40)}」吗？删除后不可恢复。`}
      confirmLabel="确认删除"
      danger
      busy={deletingId === deleteTarget.id}
      onClose={() => { if (!deletingId) setDeleteTarget(undefined) }}
      onConfirm={() => void remove(deleteTarget)}
    />}
  </div>
}
