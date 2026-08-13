import { ClipboardList, Users, X } from 'lucide-react'
import { useMemo, type Dispatch, type SetStateAction } from 'react'
import { Button } from '@/components/ui/button'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { interviewerStyles } from '@/lib/interviewer-styles'
import type { BulkState, Candidate, CreateMode, FormState, QuestionBank, Template } from './admin-interviews-api'
import type { Question } from './admin-interviews-api'

type Props = {
  saving: boolean
  mode: CreateMode
  setMode: (value: CreateMode) => void
  onClose: () => void
  onSingleSubmit: () => void
  onBulkSubmit: () => void
  form: FormState
  setForm: Dispatch<SetStateAction<FormState>>
  bulk: BulkState
  setBulk: Dispatch<SetStateAction<BulkState>>
  candidates: Candidate[]
  questionsByBank: Record<string, Question[]>
  questionLoadingBank: string
  loadBankQuestions: (bankId: string) => Promise<void>
  banks: QuestionBank[]
  templates: Template[]
  applyTemplate: (value: Template) => void
  applyBulkTemplate: (value: Template) => void
}

export function AdminInterviewCreateDialog({ saving, mode, setMode, onClose, onSingleSubmit, onBulkSubmit, form, setForm, bulk, setBulk, candidates, questionsByBank, questionLoadingBank, loadBankQuestions, banks, templates, applyTemplate, applyBulkTemplate }: Props) {
  const formQuestions = useMemo(() => form.questionBankId ? questionsByBank[form.questionBankId] ?? [] : [], [form.questionBankId, questionsByBank])
  const bulkQuestions = useMemo(() => bulk.questionBankId ? questionsByBank[bulk.questionBankId] ?? [] : [], [bulk.questionBankId, questionsByBank])
  const bulkTemplate = templates.find(item => item.id === bulk.templateId) ?? templates[0]
  const bankOptions = [{ value: '', label: '选择题库' }, ...banks.map(item => ({ value: item.id, label: `${item.name}（${item.bankCode}）` }))]
  const candidateOptions = candidates.map(item => ({ value: item.id, label: `${item.realName}（${item.username}）` }))

  return <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-label="创建面试">
    <div className="mx-auto my-4 max-w-3xl rounded-[24px] border border-border bg-surface p-5 shadow-2xl sm:my-8 sm:rounded-[30px] sm:p-7">
      <div className="flex items-start justify-between"><div><p className="text-sm font-semibold text-[var(--accent)]">面试配置</p><h2 className="mt-1 text-2xl font-bold">创建面试</h2></div><Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={onClose} aria-label="关闭创建面试对话框"><X className="h-5 w-5" /></Button></div>
      <div className="mt-5 flex rounded-full bg-muted p-1"><button type="button" onClick={() => setMode('single')} className={`flex-1 rounded-full px-3 py-2 text-sm transition ${mode === 'single' ? 'bg-surface font-semibold shadow-sm' : 'text-muted-foreground'}`}>单个创建</button><button type="button" onClick={() => setMode('bulk')} className={`flex-1 rounded-full px-3 py-2 text-sm transition ${mode === 'bulk' ? 'bg-surface font-semibold shadow-sm' : 'text-muted-foreground'}`}>批量排期</button></div>
      {mode === 'single' ? <SingleForm form={form} setForm={setForm} candidates={candidateOptions} banks={bankOptions} questions={formQuestions} loadingBank={questionLoadingBank === form.questionBankId} loadBankQuestions={loadBankQuestions} templates={templates} applyTemplate={applyTemplate} /> : <BulkForm bulk={bulk} setBulk={setBulk} candidates={candidateOptions} banks={bankOptions} questions={bulkQuestions} loadingBank={questionLoadingBank === bulk.questionBankId} loadBankQuestions={loadBankQuestions} templates={templates} applyTemplate={applyBulkTemplate} bulkTemplate={bulkTemplate} />}
      <div className="mt-8 flex justify-end gap-3"><Button variant="secondary" onClick={onClose}>取消</Button><Button disabled={saving} onClick={mode === 'single' ? onSingleSubmit : onBulkSubmit}>{mode === 'bulk' && <ClipboardList className="h-4 w-4" />}{saving ? '正在创建…' : mode === 'single' ? '创建面试' : '批量创建面试'}</Button></div>
    </div>
  </div>
}

type FormProps = { form: FormState; setForm: Dispatch<SetStateAction<FormState>>; candidates: Array<{ value: string; label: string }>; banks: Array<{ value: string; label: string }>; questions: Question[]; loadingBank: boolean; loadBankQuestions: (id: string) => Promise<void>; templates: Template[]; applyTemplate: (template: Template) => void }

function SingleForm({ form, setForm, candidates, banks, questions, loadingBank, loadBankQuestions, templates, applyTemplate }: FormProps) {
  return <div className="mt-6 grid gap-5">
    <div><p className="text-sm font-semibold">面试模板</p><div className="mt-2 grid gap-2 sm:grid-cols-2">{templates.map(item => <button key={item.id} type="button" onClick={() => applyTemplate(item)} className="rounded-2xl border border-border p-3 text-left text-sm hover:border-[var(--accent)] hover:bg-[var(--accent-soft)]"><strong>{item.name}</strong><p className="mt-1 text-xs text-muted-foreground">{item.note}</p></button>)}</div></div>
    <label className="text-sm font-semibold">面试主题<input value={form.title} onChange={event => setForm(previous => ({ ...previous, title: event.target.value }))} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
    <label className="text-sm font-semibold">候选人<ResponsiveSelect ariaLabel="选择候选人" value={form.candidateId} onValueChange={candidateId => setForm(previous => ({ ...previous, candidateId }))} className="mt-2 w-full" options={[{ value: '', label: '选择候选人' }, ...candidates]} /></label>
    <div><p className="text-sm font-semibold">面试官风格</p><div className="mt-2 grid gap-2 sm:grid-cols-3">{interviewerStyles.map(item => <button key={item.key} type="button" onClick={() => setForm(previous => ({ ...previous, interviewerStyle: item.key }))} className={`rounded-2xl border p-3 text-left text-sm transition ${form.interviewerStyle === item.key ? 'border-[var(--accent)] bg-[var(--accent-soft)] shadow-sm' : 'border-border hover:border-[var(--accent)] hover:bg-muted'}`}><strong>{item.label}</strong><p className="mt-1 text-xs leading-5 text-muted-foreground">{item.description}</p></button>)}</div></div>
    <SourceFields source={form.source} onSourceChange={source => setForm(previous => ({ ...previous, source, questionBankId: source === 'question' ? '' : previous.questionBankId, questionIds: source === 'bank' ? [] : previous.questionIds }))} bankId={form.questionBankId} onBankChange={bankId => { setForm(previous => ({ ...previous, questionBankId: bankId, questionIds: [] })); void loadBankQuestions(bankId) }} banks={banks} questions={questions} questionIds={form.questionIds} onQuestionIdsChange={questionIds => setForm(previous => ({ ...previous, questionIds }))} questionCount={form.questionCount} onQuestionCountChange={questionCount => setForm(previous => ({ ...previous, questionCount }))} loadingBank={loadingBank} />
    <div className="grid gap-5 sm:grid-cols-2"><label className="text-sm font-semibold">预约时间<input type="datetime-local" value={form.scheduledAt} onChange={event => setForm(previous => ({ ...previous, scheduledAt: event.target.value }))} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal" /></label><label className="text-sm font-semibold">时长（分钟）<input type="number" min="1" max="480" value={form.duration} onChange={event => setForm(previous => ({ ...previous, duration: Number(event.target.value) }))} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal" /></label></div>
  </div>
}

type BulkProps = { bulk: BulkState; setBulk: Dispatch<SetStateAction<BulkState>>; candidates: Array<{ value: string; label: string }>; banks: Array<{ value: string; label: string }>; questions: Question[]; loadingBank: boolean; loadBankQuestions: (id: string) => Promise<void>; templates: Template[]; applyTemplate: (template: Template) => void; bulkTemplate?: Template }

function BulkForm({ bulk, setBulk, candidates, banks, questions, loadingBank, loadBankQuestions, templates, applyTemplate, bulkTemplate }: BulkProps) {
  return <div className="mt-6 grid gap-5"><div className="rounded-[26px] border border-border bg-[var(--accent-soft)] p-5"><div className="flex items-start gap-3"><span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-surface text-[var(--accent)]"><Users className="h-5 w-5" /></span><div><h3 className="font-bold">批量排期</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">共用面试官风格、题目来源、题库抽题、题目多选和时长配置。</p></div></div></div>
    <label className="text-sm font-semibold">面试模板<ResponsiveSelect ariaLabel="选择面试模板" value={bulk.templateId} onValueChange={templateId => { const template = templates.find(item => item.id === templateId); if (template) applyTemplate(template) }} className="mt-2 w-full" options={templates.map(item => ({ value: item.id, label: `${item.name} · ${item.duration} 分钟` }))} /></label>
    <label className="text-sm font-semibold">批量面试主题<input value={bulk.title} onChange={event => setBulk(previous => ({ ...previous, title: event.target.value }))} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
    <label className="text-sm font-semibold">候选人（可多选）<ResponsiveSelect ariaLabel="选择候选人" multiple values={bulk.candidateIds} onValuesChange={candidateIds => setBulk(previous => ({ ...previous, candidateIds }))} placeholder="选择候选人" className="mt-2 w-full" options={candidates} /><span className="mt-2 block text-xs text-muted-foreground">会按照选择顺序和间隔时间依次排期。</span></label>
    <div><p className="text-sm font-semibold">面试官风格</p><div className="mt-2 grid gap-2 sm:grid-cols-3">{interviewerStyles.map(item => <button key={item.key} type="button" onClick={() => setBulk(previous => ({ ...previous, interviewerStyle: item.key }))} className={`rounded-2xl border p-3 text-left text-sm transition ${bulk.interviewerStyle === item.key ? 'border-[var(--accent)] bg-[var(--accent-soft)] shadow-sm' : 'border-border hover:border-[var(--accent)] hover:bg-muted'}`}><strong>{item.label}</strong><p className="mt-1 text-xs leading-5 text-muted-foreground">{item.description}</p></button>)}</div></div>
    <SourceFields source={bulk.source} onSourceChange={source => setBulk(previous => ({ ...previous, source, questionBankId: source === 'question' ? '' : previous.questionBankId, questionIds: source === 'bank' ? [] : previous.questionIds }))} bankId={bulk.questionBankId} onBankChange={bankId => { setBulk(previous => ({ ...previous, questionBankId: bankId, questionIds: [] })); void loadBankQuestions(bankId) }} banks={banks} questions={questions} questionIds={bulk.questionIds} onQuestionIdsChange={questionIds => setBulk(previous => ({ ...previous, questionIds }))} questionCount={bulk.questionCount} onQuestionCountChange={questionCount => setBulk(previous => ({ ...previous, questionCount }))} loadingBank={loadingBank} />
    <div className="grid gap-5 sm:grid-cols-2"><label className="text-sm font-semibold">第一场开始时间<input type="datetime-local" value={bulk.scheduledAt} onChange={event => setBulk(previous => ({ ...previous, scheduledAt: event.target.value }))} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal" /></label><label className="text-sm font-semibold">场次间隔（分钟）<input type="number" min="10" max="240" value={bulk.interval} onChange={event => setBulk(previous => ({ ...previous, interval: Number(event.target.value) }))} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal" /></label></div>
    <div className="grid gap-5 sm:grid-cols-2"><label className="text-sm font-semibold">面试类型<ResponsiveSelect ariaLabel="选择面试类型" value={bulk.type} onValueChange={type => setBulk(previous => ({ ...previous, type }))} className="mt-2 w-full" options={[{ value: 'tech', label: '技术面试' }, { value: 'hr', label: 'HR 综合面' }, { value: 'algorithm', label: '算法面试' }]} /></label><label className="text-sm font-semibold">时长（分钟）<input type="number" min="1" max="480" value={bulk.duration} onChange={event => setBulk(previous => ({ ...previous, duration: Number(event.target.value) }))} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal" /></label></div>
    <div className="rounded-[24px] border border-border bg-muted/30 p-4 text-sm text-muted-foreground">当前模板：<strong className="text-foreground">{bulkTemplate?.name}</strong> · {bulk.source === 'bank' ? `每场随机抽取 ${bulk.questionCount} 题` : `每场使用 ${bulk.questionIds.length} 道自选题`} · 每场 {bulk.duration} 分钟</div>
  </div>
}

type SourceProps = { source: 'question' | 'bank'; onSourceChange: (source: 'question' | 'bank') => void; bankId: string; onBankChange: (id: string) => void; banks: Array<{ value: string; label: string }>; questions: Question[]; questionIds: string[]; onQuestionIdsChange: (ids: string[]) => void; questionCount: number; onQuestionCountChange: (value: number) => void; loadingBank: boolean }

function SourceFields({ source, onSourceChange, bankId, onBankChange, banks, questions, questionIds, onQuestionIdsChange, questionCount, onQuestionCountChange, loadingBank }: SourceProps) {
  return <div><p className="text-sm font-semibold">题目来源</p><div className="mt-2 flex rounded-full bg-muted p-1"><button type="button" onClick={() => onSourceChange('question')} className={`flex-1 rounded-full px-3 py-2 text-sm transition ${source === 'question' ? 'bg-surface font-semibold shadow-sm' : 'text-muted-foreground'}`}>自定义选择题目</button><button type="button" onClick={() => onSourceChange('bank')} className={`flex-1 rounded-full px-3 py-2 text-sm transition ${source === 'bank' ? 'bg-surface font-semibold shadow-sm' : 'text-muted-foreground'}`}>选择题库抽题</button></div>{source === 'question' ? <div className="mt-5 grid gap-5"><label className="text-sm font-semibold">选择题库<ResponsiveSelect ariaLabel="选择题库" value={bankId} onValueChange={onBankChange} className="mt-2 w-full" options={[{ value: '', label: '请选择管理后台题库' }, ...banks.filter(item => item.value)]} /></label><label className="text-sm font-semibold">从当前题库挑选题目<ResponsiveSelect ariaLabel="从当前题库挑选题目" multiple values={questionIds} onValuesChange={onQuestionIdsChange} disabled={!bankId || loadingBank} placeholder="请选择题库后再挑选题目" className="mt-2 w-full" options={questions.map(item => ({ value: item.id, label: `#${item.id} · ${item.content}` }))} /><span className="mt-2 block text-xs text-muted-foreground">{!bankId ? '请选择题库后再挑选题目。' : loadingBank ? '正在加载题库题目…' : questions.length ? '可多选题目。' : '当前题库暂无可选题目。'}</span></label></div> : <div className="mt-5 grid gap-5 sm:grid-cols-[1fr_150px]"><label className="text-sm font-semibold">面试题库<ResponsiveSelect ariaLabel="选择题库" value={bankId} onValueChange={onBankChange} className="mt-2 w-full" options={banks} /></label><label className="text-sm font-semibold">抽题数量<ResponsiveSelect ariaLabel="选择抽题数量" value={String(questionCount)} onValueChange={value => onQuestionCountChange(Number(value))} className="mt-2 w-full" options={[3, 5, 8, 10, 15, 20].map(value => ({ value: String(value), label: String(value) }))} /></label></div>}</div>
}
