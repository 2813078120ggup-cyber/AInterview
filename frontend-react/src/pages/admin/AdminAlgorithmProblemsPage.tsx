import { useCallback, useEffect, useMemo, useState } from 'react'
import { BarChart3, CheckCircle2, CircleGauge, Database, Loader2, Pencil, Plus, Power, PowerOff, Search, Trash2, X } from 'lucide-react'
import { AlgorithmEmptyState, AlgorithmPageHeader } from '@/components/algorithm/algorithm-page'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { algorithmApi, type AlgorithmAdminProblem, type AlgorithmAdminProblemDetail, type AlgorithmAdminSaveRequest, type AlgorithmAdminTestCase, type AlgorithmTag } from '@/lib/algorithm-api'
import { algorithmDifficultyMeta, difficultyLabel } from '@/lib/algorithm-status'

const EMPTY_FORM: AlgorithmAdminSaveRequest = {
  title: '',
  slug: '',
  difficulty: 'EASY',
  descriptionMd: '',
  inputDescription: '',
  outputDescription: '',
  constraintsDescription: '',
  hintContent: '',
  timeLimitMs: 3000,
  memoryLimitMb: 256,
  defaultLanguage: 'JAVA17',
  starterCode: 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        // TODO: 在这里编写你的代码\n    }\n}\n',
  solutionCode: '',
  status: 1,
  sortNo: 0,
  tagIds: [],
  testCases: [
    { inputData: '', expectedOutput: '', caseType: 'SAMPLE', score: 10, enabled: true },
    { inputData: '', expectedOutput: '', caseType: 'HIDDEN', score: 10, enabled: true },
  ],
}

export function AdminAlgorithmProblemsPage() {
  const [items, setItems] = useState<AlgorithmAdminProblem[]>([])
  const [tags, setTags] = useState<AlgorithmTag[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<number>()
  const [form, setForm] = useState<AlgorithmAdminSaveRequest>(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [keyword, setKeyword] = useState('')
  const [difficulty, setDifficulty] = useState('')
  const [status, setStatus] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setItems(await algorithmApi.adminProblems())
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
    void algorithmApi.tags().then(setTags).catch(() => undefined)
  }, [load])

  async function openCreate() {
    setEditingId(undefined)
    setForm(EMPTY_FORM)
    setError('')
    setOpen(true)
  }

  async function openEdit(item: AlgorithmAdminProblem) {
    setError('')
    try {
      const detail: AlgorithmAdminProblemDetail = await algorithmApi.adminProblem(item.id)
      setEditingId(item.id)
      setForm({
        title: detail.title,
        slug: detail.slug ?? '',
        difficulty: detail.difficulty,
        descriptionMd: detail.descriptionMd,
        inputDescription: detail.inputDescription ?? '',
        outputDescription: detail.outputDescription ?? '',
        constraintsDescription: detail.constraintsDescription ?? '',
        hintContent: detail.hintContent ?? '',
        timeLimitMs: detail.timeLimitMs,
        memoryLimitMb: detail.memoryLimitMb,
        defaultLanguage: detail.defaultLanguage,
        starterCode: detail.starterCode,
        solutionCode: detail.solutionCode ?? '',
        status: detail.status,
        sortNo: detail.sortNo,
        tagIds: detail.tags.map(tag => tag.id),
        testCases: detail.testCases.map(testCase => ({
          id: testCase.id,
          inputData: testCase.inputData ?? '',
          expectedOutput: testCase.expectedOutput,
          caseType: testCase.caseType,
          score: testCase.score,
          sortNo: testCase.sortNo,
          enabled: testCase.enabled,
        })),
      })
      setOpen(true)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '加载题目失败')
    }
  }

  async function save() {
    setSaving(true)
    setError('')
    try {
      if (editingId) {
        await algorithmApi.adminUpdate(editingId, form)
      } else {
        await algorithmApi.adminCreate(form)
      }
      setOpen(false)
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  async function toggleStatus(item: AlgorithmAdminProblem) {
    try {
      await algorithmApi.adminStatus(item.id, item.status === 1 ? 0 : 1)
      await load()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '操作失败')
    }
  }

  function updateTestCase(index: number, patch: Partial<AlgorithmAdminTestCase>) {
    setForm(previous => ({
      ...previous,
      testCases: previous.testCases.map((testCase, testCaseIndex) =>
        testCaseIndex === index ? { ...testCase, ...patch } : testCase),
    }))
  }

  function toggleTag(tagId: number) {
    setForm(previous => ({
      ...previous,
      tagIds: previous.tagIds.includes(tagId)
        ? previous.tagIds.filter(value => value !== tagId)
        : [...previous.tagIds, tagId],
    }))
  }

  const visibleItems = useMemo(() => items.filter(item => {
    const matchesKeyword = !keyword.trim() || item.title.toLowerCase().includes(keyword.trim().toLowerCase()) || String(item.id).includes(keyword.trim())
    const matchesDifficulty = !difficulty || item.difficulty === difficulty
    const matchesStatus = !status || String(item.status) === status
    return matchesKeyword && matchesDifficulty && matchesStatus
  }), [difficulty, items, keyword, status])

  const totalSubmissions = items.reduce((sum, item) => sum + item.submissionCount, 0)
  const totalAccepted = items.reduce((sum, item) => sum + item.acceptedCount, 0)
  const metrics = [
    { label: '题目总数', value: items.length, icon: Database, tone: 'bg-[#f3eadf] text-[#7d4929]' },
    { label: '已启用', value: items.filter(item => item.status === 1).length, icon: CheckCircle2, tone: 'bg-[#eef2e6] text-[#59613b]' },
    { label: '累计提交', value: totalSubmissions, icon: BarChart3, tone: 'bg-[#eaf2f7] text-[#48677d]' },
    { label: '整体通过率', value: totalSubmissions ? `${(totalAccepted * 100 / totalSubmissions).toFixed(1)}%` : '0%', icon: CircleGauge, tone: 'bg-[#fff3d8] text-[#8a5d16]' },
  ]

  return (
    <div className="mx-auto max-w-[1680px] p-4 sm:p-5 lg:p-8">
      <div className="space-y-6">
      <AlgorithmPageHeader
        eyebrow="算法题库"
        title="算法题目管理"
        description="集中维护题目内容、难度标签、启用状态与判题用例。"
        actions={<Button className="w-full sm:w-auto" onClick={() => void openCreate()}><Plus className="h-4 w-4" />新建题目</Button>}
      />

      {error && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}

      <section aria-label="算法题库概览" className="grid grid-cols-2 gap-3 xl:grid-cols-4">
        {metrics.map(metric => <Card key={metric.label} className="flex min-h-28 items-center gap-4 p-4 sm:p-5">
          <span className={`grid h-11 w-11 shrink-0 place-items-center rounded-2xl ${metric.tone}`}>
            <metric.icon className="h-5 w-5" />
          </span>
          <div className="min-w-0">
            <p className="text-2xl font-bold tracking-tight">{metric.value}</p>
            <p className="mt-1 truncate text-xs text-muted-foreground sm:text-sm">{metric.label}</p>
          </div>
        </Card>)}
      </section>

      <Card className="overflow-hidden p-0">
        <div className="flex flex-col gap-4 border-b border-border p-4 sm:p-5 xl:flex-row xl:items-center xl:justify-between">
          <div>
            <h2 className="font-bold">题目列表</h2>
            <p className="mt-1 text-sm text-muted-foreground">当前显示 {visibleItems.length} / {items.length} 道题目</p>
          </div>
          <div className="grid gap-3 sm:grid-cols-[minmax(220px,1fr)_150px_150px] xl:w-[680px]">
            <label className="flex h-11 min-w-0 items-center gap-2 rounded-full border border-border bg-background px-4 focus-within:border-[var(--accent)]">
              <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
              <span className="sr-only">搜索题目</span>
              <input value={keyword} onChange={event => setKeyword(event.target.value)} className="min-w-0 flex-1 bg-transparent text-sm outline-none" placeholder="搜索标题或 ID" />
            </label>
            <select aria-label="按难度筛选" value={difficulty} onChange={event => setDifficulty(event.target.value)} className="h-11 rounded-full border border-border bg-background px-4 text-sm outline-none focus:border-[var(--accent)]">
              <option value="">全部难度</option>
              <option value="EASY">简单</option>
              <option value="MEDIUM">中等</option>
              <option value="HARD">困难</option>
            </select>
            <select aria-label="按状态筛选" value={status} onChange={event => setStatus(event.target.value)} className="h-11 rounded-full border border-border bg-background px-4 text-sm outline-none focus:border-[var(--accent)]">
              <option value="">全部状态</option>
              <option value="1">已启用</option>
              <option value="0">已停用</option>
            </select>
          </div>
        </div>
        {loading ? (
          <div className="space-y-3 p-5" aria-label="正在加载题库">
            {Array.from({ length: 6 }).map((_, index) => <div key={index} className="h-12 animate-pulse rounded-xl bg-muted" />)}
          </div>
        ) : visibleItems.length === 0 ? (
          <AlgorithmEmptyState title="没有找到匹配的题目" description="调整关键词或筛选条件后再试。" icon={Search} />
        ) : (
          <div className="overflow-x-auto">
          <table className="mobile-card-table w-full min-w-[760px] text-left text-sm">
            <thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground">
              <tr>
                <th className="px-5 py-4">ID</th>
                <th className="px-5 py-4">标题</th>
                <th className="px-5 py-4">难度</th>
                <th className="px-5 py-4">状态</th>
                <th className="px-5 py-4">提交/通过</th>
                <th className="px-5 py-4 text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {visibleItems.map(item => (
                <tr key={item.id} className="border-b border-border/70 transition last:border-0 hover:bg-muted/30">
                  <td data-label="ID" className="px-5 py-4 font-mono text-xs text-muted-foreground">#{item.id}</td>
                  <td data-label="标题" className="px-5 py-4 font-semibold">{item.title}</td>
                  <td data-label="难度" className="px-5 py-4">
                    <Badge tone={algorithmDifficultyMeta[item.difficulty]?.tone ?? 'default'}>{difficultyLabel(item.difficulty)}</Badge>
                  </td>
                  <td data-label="状态" className="px-5 py-4">
                    <Badge tone={item.status === 1 ? 'success' : 'default'} className="gap-1.5">
                      <span className="h-1.5 w-1.5 rounded-full bg-current" />{item.status === 1 ? '已启用' : '已停用'}
                    </Badge>
                  </td>
                  <td data-label="提交/通过" className="px-5 py-4 tabular-nums"><span className="font-semibold">{item.submissionCount}</span><span className="mx-1.5 text-muted-foreground">/</span>{item.acceptedCount}</td>
                  <td data-label="操作" className="px-5 py-4 text-right">
                    <div className="inline-flex items-center gap-1">
                      <Button variant="ghost" className="h-9 w-9 rounded-full px-0" onClick={() => void openEdit(item)} aria-label={`编辑${item.title}`} title="编辑题目"><Pencil className="h-4 w-4" /></Button>
                      <Button variant="ghost" className="h-9 w-9 rounded-full px-0" onClick={() => void toggleStatus(item)} aria-label={`${item.status === 1 ? '停用' : '启用'}${item.title}`} title={item.status === 1 ? '停用题目' : '启用题目'}>
                        {item.status === 1 ? <PowerOff className="h-4 w-4" /> : <Power className="h-4 w-4" />}
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </Card>

      {open && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-black/45 p-3 backdrop-blur-sm sm:p-6" role="dialog" aria-modal="true" aria-label="编辑算法题目">
          <div className="mx-auto w-full max-w-4xl rounded-[24px] bg-surface p-5 shadow-2xl sm:p-7">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-semibold text-[var(--accent)]">算法题库</p>
                <h2 className="mt-1 text-2xl font-bold">{editingId ? '编辑题目' : '新建题目'}</h2>
              </div>
              <button onClick={() => setOpen(false)} className="rounded-full p-2 hover:bg-muted" aria-label="关闭">
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-6 grid gap-5 sm:grid-cols-2">
              <label className="block text-sm font-semibold">标题
                <input value={form.title} onChange={event => setForm({ ...form, title: event.target.value })} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 font-normal outline-none focus:border-[var(--accent)]" />
              </label>
              <label className="block text-sm font-semibold">Slug（可选）
                <input value={form.slug} onChange={event => setForm({ ...form, slug: event.target.value })} placeholder="two-sum" className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 font-normal outline-none focus:border-[var(--accent)]" />
              </label>
              <label className="block text-sm font-semibold">难度
                <select value={form.difficulty} onChange={event => setForm({ ...form, difficulty: event.target.value })} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 font-normal outline-none">
                  <option value="EASY">简单</option>
                  <option value="MEDIUM">中等</option>
                  <option value="HARD">困难</option>
                </select>
              </label>
              <label className="block text-sm font-semibold">排序
                <input type="number" value={form.sortNo} onChange={event => setForm({ ...form, sortNo: Number(event.target.value) })} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 font-normal outline-none" />
              </label>
              <label className="block text-sm font-semibold">时间限制（ms）
                <input type="number" min={100} max={60000} value={form.timeLimitMs} onChange={event => setForm({ ...form, timeLimitMs: Number(event.target.value) })} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 font-normal outline-none" />
              </label>
              <label className="block text-sm font-semibold">内存限制（MB）
                <input type="number" min={16} max={1024} value={form.memoryLimitMb} onChange={event => setForm({ ...form, memoryLimitMb: Number(event.target.value) })} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 font-normal outline-none" />
              </label>
              <label className="block text-sm font-semibold sm:col-span-2">状态
                <select value={form.status} onChange={event => setForm({ ...form, status: Number(event.target.value) })} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 font-normal outline-none">
                  <option value={1}>启用</option>
                  <option value={0}>停用</option>
                </select>
              </label>
              <label className="block text-sm font-semibold sm:col-span-2">题目描述（Markdown）
                <textarea rows={8} value={form.descriptionMd} onChange={event => setForm({ ...form, descriptionMd: event.target.value })} className="mt-2 w-full rounded-xl border border-border bg-background p-3 font-normal outline-none focus:border-[var(--accent)]" />
              </label>
              <label className="block text-sm font-semibold">输入说明
                <textarea rows={3} value={form.inputDescription} onChange={event => setForm({ ...form, inputDescription: event.target.value })} className="mt-2 w-full rounded-xl border border-border bg-background p-3 font-normal outline-none" />
              </label>
              <label className="block text-sm font-semibold">输出说明
                <textarea rows={3} value={form.outputDescription} onChange={event => setForm({ ...form, outputDescription: event.target.value })} className="mt-2 w-full rounded-xl border border-border bg-background p-3 font-normal outline-none" />
              </label>
              <label className="block text-sm font-semibold">数据范围
                <textarea rows={2} value={form.constraintsDescription} onChange={event => setForm({ ...form, constraintsDescription: event.target.value })} className="mt-2 w-full rounded-xl border border-border bg-background p-3 font-normal outline-none" />
              </label>
              <label className="block text-sm font-semibold">提示
                <textarea rows={2} value={form.hintContent} onChange={event => setForm({ ...form, hintContent: event.target.value })} className="mt-2 w-full rounded-xl border border-border bg-background p-3 font-normal outline-none" />
              </label>
              <label className="block text-sm font-semibold sm:col-span-2">代码模板
                <textarea rows={10} value={form.starterCode} onChange={event => setForm({ ...form, starterCode: event.target.value })} className="mt-2 w-full rounded-xl border border-border bg-background p-3 font-mono text-xs outline-none focus:border-[var(--accent)]" />
              </label>
              <label className="block text-sm font-semibold sm:col-span-2">标准答案（仅管理端可见，用户端不会返回）
                <textarea rows={10} value={form.solutionCode} onChange={event => setForm({ ...form, solutionCode: event.target.value })} placeholder="留空表示暂无标准答案" className="mt-2 w-full rounded-xl border border-border bg-background p-3 font-mono text-xs outline-none focus:border-[var(--accent)]" />
              </label>
              <div className="sm:col-span-2">
                <p className="text-sm font-semibold">标签</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {tags.map(tag => (
                    <button
                      key={tag.id}
                      type="button"
                      onClick={() => toggleTag(tag.id)}
                      className={`rounded-full border px-3 py-1.5 text-xs transition ${form.tagIds.includes(tag.id) ? 'border-[var(--accent)] bg-[var(--accent-soft)] font-semibold text-[var(--accent)]' : 'border-border bg-surface hover:bg-muted'}`}
                    >
                      {tag.name}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            <div className="mt-6">
              <div className="flex items-center justify-between">
                <h3 className="font-bold">测试用例（SAMPLE 用户可见，HIDDEN 判题隐藏）</h3>
                <Button variant="secondary" className="h-9 px-3 text-xs" onClick={() => setForm(previous => ({ ...previous, testCases: [...previous.testCases, { inputData: '', expectedOutput: '', caseType: 'HIDDEN', score: 10, enabled: true }] }))}>
                  <Plus className="h-3.5 w-3.5" />添加用例
                </Button>
              </div>
              <div className="mt-3 space-y-3">
                {form.testCases.map((testCase, index) => (
                  <div key={index} className="grid gap-2 rounded-xl border border-border bg-muted/20 p-3 sm:grid-cols-[110px_1fr_1fr_90px_70px_36px]">
                    <select value={testCase.caseType} onChange={event => updateTestCase(index, { caseType: event.target.value as 'SAMPLE' | 'HIDDEN' })} className="h-10 rounded-lg border border-border bg-background px-2 text-xs outline-none">
                      <option value="SAMPLE">示例</option>
                      <option value="HIDDEN">隐藏</option>
                    </select>
                    <textarea rows={2} value={testCase.inputData ?? ''} onChange={event => updateTestCase(index, { inputData: event.target.value })} placeholder="输入数据" className="rounded-lg border border-border bg-background p-2 font-mono text-xs outline-none" />
                    <textarea rows={2} value={testCase.expectedOutput} onChange={event => updateTestCase(index, { expectedOutput: event.target.value })} placeholder="期望输出" className="rounded-lg border border-border bg-background p-2 font-mono text-xs outline-none" />
                    <input type="number" min={0} value={testCase.score} onChange={event => updateTestCase(index, { score: Number(event.target.value) })} className="h-10 rounded-lg border border-border bg-background px-2 text-xs outline-none" />
                    <label className="flex h-10 items-center gap-1 text-xs">
                      <input type="checkbox" checked={testCase.enabled} onChange={event => updateTestCase(index, { enabled: event.target.checked })} className="accent-[var(--accent)]" />
                      启用
                    </label>
                    <button type="button" onClick={() => setForm(previous => ({ ...previous, testCases: previous.testCases.filter((_, testCaseIndex) => testCaseIndex !== index) }))} className="grid h-10 w-9 place-items-center rounded-lg text-muted-foreground hover:bg-muted" aria-label="删除用例">
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                ))}
              </div>
            </div>

            <div className="mt-7 flex justify-end gap-3">
              <Button variant="secondary" onClick={() => setOpen(false)}>取消</Button>
              <Button disabled={saving} onClick={() => void save()}>
                {saving && <Loader2 className="h-4 w-4 animate-spin" />}
                {editingId ? '保存修改' : '创建题目'}
              </Button>
            </div>
          </div>
        </div>
      )}
      </div>
    </div>
  )
}
