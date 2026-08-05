import { BookOpen, Download, Eye, EyeOff, Loader2, Plus, Search, ToggleLeft, ToggleRight, Trash2, UploadCloud, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { AdminConfirmDialog } from '@/components/admin-confirm-dialog'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { recordAuditLog } from '@/lib/audit-log'
import { request } from '@/lib/api'
import { profile } from '@/lib/session'

type Bank = {
  id: string
  categoryId?: string | number | null
  positionId?: string | number | null
  bankCode: string
  name: string
  description?: string
  visibility: number
  status: number
}
type Page<T> = { records: T[]; total: number }
type ImportQuestion = {
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

const visibilityOptions = [
  { value: 0, label: '仅管理端', hint: '候选人不可见', icon: EyeOff },
  { value: 1, label: '内部可见', hint: '仅用于排期与后台创建', icon: Eye },
  { value: 2, label: '练习可见', hint: '候选人可用于模拟练习', icon: Eye },
] as const

const visibilityText = (value: number) => visibilityOptions.find(item => item.value === value)?.label ?? '未知范围'
const visibilityHint = (value: number) => visibilityOptions.find(item => item.value === value)?.hint ?? '请重新设置可见范围'
const emptyQuestion = (index = 0): ImportQuestion => ({
  questionType: 'short_answer',
  difficulty: 2,
  content: '',
  options: '',
  correctAnswer: '',
  answerTemplate: '',
  explanation: '',
  tags: '',
  score: 10,
  source: 'excel_import',
  sortOrder: index,
  status: 1,
})
const typeMap: Record<string, string> = {
  单选: 'single_choice',
  单选题: 'single_choice',
  多选: 'multiple_choice',
  多选题: 'multiple_choice',
  判断: 'true_false',
  判断题: 'true_false',
  简答: 'short_answer',
  简答题: 'short_answer',
  编程: 'coding',
  编程题: 'coding',
}
const headerMap: Record<string, keyof ImportQuestion> = {
  questiontype: 'questionType',
  type: 'questionType',
  题型: 'questionType',
  difficulty: 'difficulty',
  难度: 'difficulty',
  content: 'content',
  题目: 'content',
  题目内容: 'content',
  options: 'options',
  选项: 'options',
  correctanswer: 'correctAnswer',
  标准答案: 'correctAnswer',
  answertemplate: 'answerTemplate',
  参考答案: 'answerTemplate',
  explanation: 'explanation',
  解析: 'explanation',
  tags: 'tags',
  标签: 'tags',
  score: 'score',
  分值: 'score',
}

function splitLine(line: string, separator: string) {
  const result: string[] = []
  let value = ''
  let quoted = false
  for (let index = 0; index < line.length; index += 1) {
    const char = line[index]
    if (char === '"') quoted = !quoted
    else if (char === separator && !quoted) {
      result.push(value.trim())
      value = ''
    } else value += char
  }
  result.push(value.trim())
  return result.map(item => item.replace(/^"|"$/g, '').replaceAll('""', '"'))
}

function parseCsv(text: string) {
  const lines = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter(Boolean)
  if (lines.length < 2) return []
  const separator = lines[0].includes('\t') ? '\t' : ','
  const headers = splitLine(lines[0], separator).map(item => headerMap[item.trim().toLowerCase()] ?? headerMap[item.trim()])
  return lines.slice(1).map((line, index) => {
    const question = emptyQuestion(index)
    splitLine(line, separator).forEach((cell, cellIndex) => {
      const key = headers[cellIndex]
      if (!key) return
      if (key === 'difficulty' || key === 'score' || key === 'sortOrder' || key === 'status') {
        ;(question as Record<string, string | number>)[key] = Number(cell) || Number((question as Record<string, string | number>)[key])
      } else if (key === 'questionType') {
        question.questionType = typeMap[cell.trim()] ?? (cell.trim().toLowerCase().replaceAll('-', '_') || 'short_answer')
      } else {
        ;(question as Record<string, string | number>)[key] = cell
      }
    })
    return question
  }).filter(item => item.content.trim())
}

function downloadTemplate() {
  const csv = [
    'questionType,difficulty,content,options,correctAnswer,answerTemplate,explanation,tags,score',
    'short_answer,2,"请说明 HashMap 在 JDK 8 中的扩容机制。","","","从数组、链表、红黑树和扩容迁移说明。","考察集合底层原理","Java,集合",15',
  ].join('\n')
  const url = URL.createObjectURL(new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = 'question-import-template.csv'
  link.click()
  URL.revokeObjectURL(url)
}

function toBankPayload(item: Bank, patch: Partial<Pick<Bank, 'visibility' | 'status'>>) {
  return {
    categoryId: item.categoryId ?? null,
    positionId: item.positionId ?? null,
    bankCode: item.bankCode,
    name: item.name,
    description: item.description ?? '',
    visibility: patch.visibility ?? item.visibility,
    status: patch.status ?? item.status,
  }
}

export function AdminQuestionBanks() {
  const [items, setItems] = useState<Bank[]>([])
  const [keyword, setKeyword] = useState('')
  const [status, setStatus] = useState('')
  const [visibility, setVisibility] = useState('')
  const [loading, setLoading] = useState(true)
  const [dialog, setDialog] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [updatingId, setUpdatingId] = useState('')
  const [deletingId, setDeletingId] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<Bank>()
  const [importing, setImporting] = useState(false)
  const [error, setError] = useState('')
  const [bankId, setBankId] = useState('')
  const [fileName, setFileName] = useState('')
  const [preview, setPreview] = useState<ImportQuestion[]>([])
  const [form, setForm] = useState({ bankCode: '', name: '', description: '', visibility: 2, status: 1 })

  async function load(nextKeyword = keyword) {
    setLoading(true)
    try {
      const page = await request<Page<Bank>>(`/v1/question-banks?pageNo=1&pageSize=100&keyword=${encodeURIComponent(nextKeyword)}${status ? `&status=${status}` : ''}`)
      setItems(page.records)
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '题库列表加载失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load('') }, [status])

  const visible = useMemo(() => items.filter(item => {
    const keywordMatched = item.name.toLowerCase().includes(keyword.toLowerCase()) || item.bankCode.toLowerCase().includes(keyword.toLowerCase())
    const visibilityMatched = !visibility || String(item.visibility) === visibility
    return keywordMatched && visibilityMatched
  }), [items, keyword, visibility])

  async function create() {
    if (!form.bankCode.trim() || !form.name.trim()) {
      setError('请填写题库编码和题库名称。')
      return
    }
    setSaving(true)
    try {
      const bank = await request<Bank>('/v1/question-banks', { method: 'POST', body: JSON.stringify(form) })
      recordAuditLog({
        module: '题库管理',
        action: '创建题库',
        operator: profile()?.realName ?? '管理员',
        target: bank.name,
        detail: `创建题库 ${bank.bankCode}，可见范围：${visibilityText(bank.visibility)}，状态：${bank.status === 1 ? '启用' : '停用'}`,
      })
      setItems(previous => [bank, ...previous])
      setDialog(false)
      setForm({ bankCode: '', name: '', description: '', visibility: 2, status: 1 })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '题库创建失败，请稍后重试。')
    } finally {
      setSaving(false)
    }
  }

  async function updateBank(item: Bank, patch: Partial<Pick<Bank, 'visibility' | 'status'>>) {
    setUpdatingId(item.id)
    try {
      const next = await request<Bank>(`/v1/question-banks/${item.id}`, {
        method: 'PUT',
        body: JSON.stringify(toBankPayload(item, patch)),
      })
      setItems(previous => previous.map(current => current.id === item.id ? next : current))
      if (patch.status !== undefined) {
        recordAuditLog({
          module: '题库管理',
          action: patch.status === 1 ? '启用题库' : '停用题库',
          operator: profile()?.realName ?? '管理员',
          target: item.name,
          detail: `${patch.status === 1 ? '启用' : '停用'}题库 ${item.bankCode}`,
        })
      }
      if (patch.visibility !== undefined) {
        recordAuditLog({
          module: '题库管理',
          action: '调整可见范围',
          operator: profile()?.realName ?? '管理员',
          target: item.name,
          detail: `将题库 ${item.bankCode} 调整为「${visibilityText(patch.visibility)}」`,
        })
      }
      setError('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '题库设置更新失败，请稍后重试。')
    } finally {
      setUpdatingId('')
    }
  }

  async function removeBank(item: Bank) {
    setDeletingId(item.id)
    try {
      await request(`/v1/question-banks/${item.id}`, { method: 'DELETE' })
      setItems(previous => previous.filter(current => current.id !== item.id))
      if (bankId === item.id) setBankId('')
      recordAuditLog({
        module: '题库管理',
        action: '删除题库',
        operator: profile()?.realName ?? '管理员',
        target: item.name,
        detail: `删除题库 ${item.bankCode}`,
      })
      setError('')
      setDeleteTarget(undefined)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '题库删除失败，请稍后重试。')
    } finally {
      setDeletingId('')
    }
  }

  async function handleFile(file?: File) {
    if (!file) return
    setFileName(file.name)
    setPreview([])
    if (/\.(xlsx|xls)$/i.test(file.name)) {
      setError('当前前端支持 CSV/TSV 直接解析。Excel 文件请先另存为 CSV 后导入。')
      return
    }
    const parsed = parseCsv(await file.text())
    if (!parsed.length) setError('没有解析到有效题目，请确认表头包含 content/题目内容。')
    else {
      setPreview(parsed)
      setError('')
    }
  }

  async function submitImport() {
    if (!bankId) {
      setError('请选择目标题库。')
      return
    }
    if (!preview.length) {
      setError('请先上传并预览题目文件。')
      return
    }
    setImporting(true)
    try {
      for (const [index, question] of preview.entries()) {
        await request(`/v1/question-banks/${bankId}/questions`, { method: 'POST', body: JSON.stringify({ ...question, sortOrder: index }) })
      }
      const bank = items.find(item => item.id === bankId)
      recordAuditLog({
        module: '题库管理',
        action: '批量导入题目',
        operator: profile()?.realName ?? '管理员',
        target: bank?.name ?? bankId,
        detail: `从 ${fileName || '表格文件'} 导入 ${preview.length} 道题`,
      })
      setImportOpen(false)
      setPreview([])
      setFileName('')
      setBankId('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '题目导入失败，请检查文件内容。')
    } finally {
      setImporting(false)
    }
  }

  return <div className="mx-auto max-w-7xl p-4 sm:p-6 lg:p-10">
    <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
      <div>
        <p className="text-sm font-semibold text-[var(--accent)]">题库配置</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">题库管理</h1>
        <p className="mt-3 max-w-2xl text-muted-foreground">管理题库范围、启停状态与批量导入。</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" onClick={downloadTemplate}><Download className="h-4 w-4" />下载模板</Button>
        <Button variant="secondary" onClick={() => setImportOpen(true)}><UploadCloud className="h-4 w-4" />批量导入</Button>
        <Button onClick={() => setDialog(true)}><Plus className="h-4 w-4" />新建题库</Button>
      </div>
    </header>

    {error && <p className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}

    <Card className="mt-7 p-0">
      <div className="flex flex-col gap-3 border-b border-border p-5 md:flex-row">
        <label className="flex h-12 flex-1 items-center gap-2 rounded-full border border-border bg-surface px-4">
          <Search className="h-4 w-4 text-muted-foreground" />
          <input value={keyword} onChange={event => setKeyword(event.target.value)} onKeyDown={event => event.key === 'Enter' && void load()} className="w-full bg-transparent text-sm outline-none" placeholder="搜索题库名称或编码" />
        </label>
        <ResponsiveSelect
          ariaLabel="选择可见范围"
          value={visibility}
          onValueChange={setVisibility}
          className="w-full md:w-44"
          options={[{ value: "", label: "全部可见范围" }, ...visibilityOptions.map(item => ({ value: String(item.value), label: item.label }))]}
        />
        <ResponsiveSelect
          ariaLabel="选择状态"
          value={status}
          onValueChange={setStatus}
          className="w-full md:w-36"
          options={[
            { value: "", label: "全部状态" },
            { value: "1", label: "已启用" },
            { value: "0", label: "已停用" },
          ]}
        />
        <Button type="button" variant="secondary" className="h-9 px-4" onClick={() => void load()}>搜索</Button>
      </div>

      {loading ? <p className="p-12 text-center text-sm text-muted-foreground">正在加载题库…</p> : <div className="grid gap-4 p-5 md:grid-cols-2 xl:grid-cols-3">
        {visible.map((item, index) => {
          const updating = updatingId === item.id
          const deleting = deletingId === item.id
          const VisibilityIcon = visibilityOptions.find(option => option.value === item.visibility)?.icon ?? EyeOff
          return <Card key={item.id} motionDelay={index * .03} className="group flex min-h-[300px] flex-col">
            <div className="flex items-start justify-between">
              <span className="grid h-12 w-12 place-items-center rounded-[18px] bg-[var(--accent-soft)] text-[var(--accent)]"><BookOpen className="h-5 w-5" /></span>
              <button
                type="button"
                disabled={updating}
                onClick={() => void updateBank(item, { status: item.status === 1 ? 0 : 1 })}
                className={[
                  'inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] disabled:cursor-not-allowed',
                  item.status === 1 ? 'bg-[var(--success)] text-[var(--success-foreground)] hover:brightness-95' : 'bg-muted text-muted-foreground hover:bg-[var(--accent-soft)] hover:text-[var(--accent)]',
                  updating ? 'opacity-60' : '',
                ].join(' ')}
                aria-pressed={item.status === 1}
                title={item.status === 1 ? '点击停用题库' : '点击启用题库'}
              >
                {updating ? <Loader2 className="h-4 w-4 animate-spin" /> : item.status === 1 ? <ToggleRight className="h-4 w-4" /> : <ToggleLeft className="h-4 w-4" />}
                {updating ? '处理中…' : item.status === 1 ? '已启用' : '已停用'}
              </button>
            </div>

            <p className="mt-6 text-xs font-semibold tracking-wide text-[var(--accent)]">{item.bankCode}</p>
            <h3 className="mt-1 text-xl font-bold">{item.name}</h3>
            <p className="mt-3 line-clamp-2 min-h-10 text-sm leading-6 text-muted-foreground">{item.description || '暂未添加题库说明'}</p>

            <div className="mt-5 rounded-[22px] border border-[color-mix(in_srgb,var(--accent)_14%,var(--border))] bg-[linear-gradient(145deg,color-mix(in_srgb,var(--accent-soft)_42%,transparent),color-mix(in_srgb,var(--surface)_82%,transparent))] p-3.5 shadow-[inset_0_1px_0_rgba(255,255,255,.62)]">
              <div className="mb-2 flex items-center justify-between">
                <span className="inline-flex items-center gap-2 text-xs font-semibold text-muted-foreground"><VisibilityIcon className="h-3.5 w-3.5" />可见范围</span>
                <span className="text-xs text-muted-foreground">{visibilityHint(item.visibility)}</span>
              </div>
              <div className="mt-3 grid grid-cols-3 gap-2">
                {visibilityOptions.map(option => {
                  const OptionIcon = option.icon
                  const selected = option.value === item.visibility
                  return <button
                    key={option.value}
                    type="button"
                    disabled={updating || selected}
                    onClick={() => void updateBank(item, { visibility: option.value })}
                    className={[
                      'group flex min-h-16 flex-col items-start justify-between rounded-[18px] border px-3 py-2.5 text-left transition duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] disabled:cursor-not-allowed',
                      selected
                        ? 'border-[var(--accent)] bg-surface text-foreground shadow-[0_12px_28px_color-mix(in_srgb,var(--accent)_13%,transparent)]'
                        : 'border-border/80 bg-surface/45 text-muted-foreground hover:-translate-y-0.5 hover:border-[color-mix(in_srgb,var(--accent)_36%,var(--border))] hover:bg-surface/80 hover:text-foreground',
                      updating ? 'opacity-60' : '',
                    ].join(' ')}
                    aria-pressed={selected}
                    title={option.hint}
                  >
                    <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold">
                      <OptionIcon className="h-3.5 w-3.5" />
                      {option.label}
                    </span>
                    <span className="text-[10px] leading-4 text-muted-foreground">{option.hint}</span>
                  </button>
                })}
              </div>
            </div>

            <div className="mt-auto flex items-center justify-between border-t border-border pt-4">
              <span className="text-xs text-muted-foreground">{item.status === 1 ? `${visibilityText(item.visibility)} · 可用` : '停用后不可用于新面试'}</span>
              <div className="flex items-center gap-2">
                <Link to={`/admin/question-banks/${item.id}`} className="text-sm font-semibold text-[var(--accent)] hover:text-foreground">维护题目 →</Link>
                <Button
                  type="button"
                  variant="danger"
                  className="h-9 w-9 rounded-full p-0"
                  disabled={updating || deleting}
                  onClick={() => setDeleteTarget(item)}
                  aria-busy={deleting}
                  aria-label={`删除题库 ${item.name}`}
                  title="删除题库"
                >{deleting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}</Button>
              </div>
            </div>
          </Card>
        })}
        {!visible.length && <p className="col-span-full py-12 text-center text-sm text-muted-foreground">暂未找到题库</p>}
      </div>}
    </Card>

    {dialog && <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="new-bank-title">
      <div className="mx-auto my-4 max-w-lg rounded-[24px] bg-surface p-5 shadow-2xl sm:my-10 sm:rounded-[30px] sm:p-7">
        <div className="flex justify-between">
          <div><p className="text-sm font-semibold text-[var(--accent)]">题库配置</p><h2 id="new-bank-title" className="mt-1 text-2xl font-bold">新建题库</h2></div>
          <Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={() => setDialog(false)} aria-label="关闭新建题库对话框"><X className="h-5 w-5" /></Button>
        </div>
        <div className="mt-6 space-y-5">
          <label className="block text-sm font-semibold">题库编码<input value={form.bankCode} onChange={event => setForm({ ...form, bankCode: event.target.value })} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="block text-sm font-semibold">题库名称<input value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="block text-sm font-semibold">题库说明<textarea value={form.description} onChange={event => setForm({ ...form, description: event.target.value })} className="mt-2 min-h-24 w-full rounded-2xl border border-border bg-background p-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="block text-sm font-semibold">可见范围<ResponsiveSelect
            ariaLabel="选择可见范围"
            value={String(form.visibility)}
            onValueChange={next => setForm({ ...form, visibility: Number(next) })}
            className="mt-2 w-full"
            options={visibilityOptions.map(option => ({ value: String(option.value), label: `${option.label} · ${option.hint}` }))}
          /></label>
          <label className="block text-sm font-semibold">启用状态<ResponsiveSelect
            ariaLabel="选择启用状态"
            value={String(form.status)}
            onValueChange={next => setForm({ ...form, status: Number(next) })}
            className="mt-2 w-full"
            options={[
              { value: "1", label: "创建后立即启用" },
              { value: "0", label: "先保存为停用" },
            ]}
          /></label>
        </div>
        <div className="mt-7 flex justify-end gap-3"><Button variant="secondary" onClick={() => setDialog(false)}>取消</Button><Button disabled={saving} onClick={() => void create()}>{saving ? '正在创建…' : '创建题库'}</Button></div>
      </div>
    </div>}

    {importOpen && <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="import-bank-title">
      <div className="mx-auto my-4 w-full max-w-[min(1180px,calc(100vw-2rem))] overflow-hidden rounded-[24px] bg-surface p-4 shadow-2xl sm:my-8 sm:rounded-[30px] sm:p-7">
        <div className="flex items-start justify-between">
          <div><p className="text-sm font-semibold text-[var(--accent)]">批量导入</p><h2 id="import-bank-title" className="mt-1 text-2xl font-bold">题目批量导入</h2><p className="mt-2 text-sm text-muted-foreground">支持 CSV、TSV；Excel 文件请先另存为 CSV。</p></div>
          <Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={() => setImportOpen(false)} aria-label="关闭题目导入对话框"><X className="h-5 w-5" /></Button>
        </div>
        <div className="mt-6 grid min-w-0 gap-5 lg:grid-cols-[320px_minmax(0,1fr)]">
          <Card className="min-w-0 bg-[var(--accent-soft)]">
            <h3 className="font-bold">导入设置</h3>
            <label className="mt-5 block text-sm font-semibold">目标题库<ResponsiveSelect
              ariaLabel="选择目标题库"
              value={bankId}
              onValueChange={setBankId}
              className="mt-2 w-full"
              options={[{ value: "", label: "选择题库" }, ...items.map(item => ({ value: item.id, label: `${item.name}（${item.bankCode}）` }))]}
            /></label>
            <label className="mt-5 grid min-h-32 cursor-pointer place-items-center rounded-[22px] border border-dashed border-[var(--accent)] bg-surface/60 p-5 text-center text-sm">
              <input type="file" accept=".csv,.tsv,.txt,.xlsx,.xls" className="hidden" onChange={event => void handleFile(event.target.files?.[0])} />
              <UploadCloud className="mb-3 h-7 w-7 text-[var(--accent)]" />
              <span className="font-semibold">{fileName || '点击选择题目文件'}</span>
              <span className="mt-1 text-xs text-muted-foreground">建议使用下载模板填写</span>
            </label>
            <Button variant="secondary" className="mt-5 w-full" onClick={downloadTemplate}>下载模板</Button>
          </Card>
          <Card className="min-w-0 overflow-hidden p-0">
            <div className="flex flex-col gap-3 border-b border-border p-5 sm:flex-row sm:items-center sm:justify-between">
              <div><h3 className="font-bold">导入预览</h3><p className="mt-1 text-sm text-muted-foreground">已解析 {preview.length} 道题。</p></div>
              <Button className="w-full sm:w-auto" disabled={importing || !preview.length} onClick={() => void submitImport()}>{importing ? '导入中…' : '提交导入'}</Button>
            </div>
            <div className="max-h-[520px] w-full overflow-auto">
              <table className="mobile-card-table text-left text-sm">
                <thead className="sticky top-0 border-b border-border bg-surface text-xs text-muted-foreground"><tr><th className="px-4 py-3">题型</th><th className="px-4 py-3">难度</th><th className="px-4 py-3">题目内容</th><th className="px-4 py-3">标签</th><th className="px-4 py-3">分值</th></tr></thead>
                <tbody>
                  {preview.slice(0, 80).map((item, index) => <tr key={index} className="border-b border-border/70"><td data-label="题型" className="px-4 py-3"><Badge tone="info">{item.questionType}</Badge></td><td data-label="难度" className="px-4 py-3">{item.difficulty}</td><td data-label="题目内容" className="max-w-xl break-words px-4 py-3 leading-6">{item.content}</td><td data-label="标签" className="break-words px-4 py-3 text-muted-foreground">{item.tags || '-'}</td><td data-label="分值" className="px-4 py-3">{item.score}</td></tr>)}
                  {!preview.length && <tr><td data-mobile-full colSpan={5} className="px-4 py-16 text-center text-muted-foreground">上传模板文件后会在这里预览。</td></tr>}
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>
    </div>}
    {deleteTarget && <AdminConfirmDialog
      title="删除题库"
      description={`确定删除题库“${deleteTarget.name}”吗？题库下仍有题目时无法删除。`}
      confirmLabel="确认删除"
      danger
      busy={deletingId === deleteTarget.id}
      onClose={() => { if (!deletingId) setDeleteTarget(undefined) }}
      onConfirm={() => void removeBank(deleteTarget)}
    />}
  </div>
}
