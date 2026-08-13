import { Layers3, Plus, RefreshCw } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { AdminInterviewActionDialog, type InterviewActionTarget } from '@/features/admin-interviews/admin-interview-action-dialog'
import { AdminInterviewCreateDialog } from '@/features/admin-interviews/admin-interview-create-dialog'
import { AdminInterviewFilters, type InterviewFilters } from '@/features/admin-interviews/admin-interview-filters'
import { AdminInterviewList } from '@/features/admin-interviews/admin-interview-list'
import { AdminInterviewNotificationDialog } from '@/features/admin-interviews/admin-interview-notification-dialog'
import { AdminInterviewReportDrawer } from '@/features/admin-interviews/admin-interview-report-drawer'
import { adminInterviewsApi, type BulkState, type CreateMode, type FormState, type InterviewRow, type ReportItem, type Template } from '@/features/admin-interviews/admin-interviews-api'
import { useAdminInterviews } from '@/features/admin-interviews/use-admin-interviews'

const localInput = () => {
  const date = new Date(Date.now() + 10 * 60_000)
  date.setSeconds(0, 0)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
const toBackendTime = (value: string) => value.length === 16 ? `${value}:00` : value
const templates: Template[] = [
  { id: 'java-backend', name: 'Java 后端岗', title: 'Java 后端工程师模拟面试', type: 'tech', duration: 60, questionCount: 8, note: '集合、并发、Spring、数据库与项目表达' },
  { id: 'frontend', name: '前端工程师岗', title: '前端工程师综合能力面试', type: 'tech', duration: 50, questionCount: 8, note: 'Vue/React、浏览器、工程化、性能优化' },
  { id: 'algorithm', name: '算法与编程岗', title: '算法基础与编码思维面试', type: 'algorithm', duration: 45, questionCount: 6, note: '复杂度、数据结构、编码思路与边界条件' },
  { id: 'campus', name: '校园招聘通用', title: '综合素质与项目经历面试', type: 'hr', duration: 40, questionCount: 5, note: '自我介绍、项目复盘、沟通表达与稳定性' },
]
const defaultForm = (): FormState => ({ title: '', candidateId: '', scheduledAt: localInput(), duration: 60, type: 'tech', source: 'question', questionIds: [], questionBankId: '', questionCount: 5, interviewerStyle: 'big-tech' })
const defaultBulk = (): BulkState => ({ templateId: templates[0].id, title: templates[0].title, candidateIds: [], scheduledAt: localInput(), interval: 60, duration: templates[0].duration, type: templates[0].type, source: 'bank', questionIds: [], questionBankId: '', questionCount: templates[0].questionCount, interviewerStyle: 'big-tech' })

export function AdminInterviews() {
  const data = useAdminInterviews()
  const [searchParams, setSearchParams] = useSearchParams()
  const [filters, setFilters] = useState<InterviewFilters>({ search: '', candidate: '', status: '', time: 'all' })
  const [dialog, setDialog] = useState(false)
  const [createMode, setCreateMode] = useState<CreateMode>('single')
  const [form, setForm] = useState<FormState>(defaultForm)
  const [bulk, setBulk] = useState<BulkState>(defaultBulk)
  const [noticeTarget, setNoticeTarget] = useState<InterviewRow>()
  const [actionTarget, setActionTarget] = useState<InterviewActionTarget>()
  const [selectedReport, setSelectedReport] = useState<ReportItem>()
  const [reportDetail, setReportDetail] = useState<import('@/features/admin-interviews/admin-interviews-api').ReportDetail>()
  const [reportLoading, setReportLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [actionBusy, setActionBusy] = useState(false)
  const targetReportInterviewId = searchParams.get('reportInterviewId')

  const list = useMemo(() => data.items.filter(item => {
    const person = data.candidateById.get(String(item.candidateId))
    const keyword = filters.search.trim().toLowerCase()
    const date = new Date(item.scheduledAt)
    const now = new Date()
    const matchesTime = filters.time === 'all' || (filters.time === 'today' && date.toDateString() === now.toDateString()) || (filters.time === 'past' && date < now) || (filters.time === 'next7' && date >= now && date <= new Date(now.getTime() + 7 * 86400000))
    return (!keyword || [item.title, person?.realName, person?.username].some(value => value?.toLowerCase().includes(keyword))) && (!filters.candidate || String(item.candidateId) === filters.candidate) && (!filters.status || String(item.status) === filters.status) && matchesTime
  }).sort((a, b) => b.scheduledAt.localeCompare(a.scheduledAt)), [data.items, data.candidateById, filters])

  const openReport = useCallback(async (report: ReportItem) => {
    setSelectedReport(report)
    setReportDetail(undefined)
    setReportLoading(true)
    try {
      setReportDetail(await adminInterviewsApi.report(report.interviewId))
    } catch (reason) {
      data.setError(reason instanceof Error ? reason.message : '评测报告加载失败，请稍后重试。')
    } finally {
      setReportLoading(false)
    }
  }, [data])

  useEffect(() => {
    if (data.loading || !targetReportInterviewId || selectedReport || reportLoading) return
    const report = data.reportByInterviewId.get(targetReportInterviewId)
    if (report) void openReport(report)
  }, [data.loading, data.reportByInterviewId, targetReportInterviewId, selectedReport, reportLoading, openReport])

  const closeReport = useCallback(() => {
    setSelectedReport(undefined)
    setReportDetail(undefined)
    if (targetReportInterviewId) {
      const next = new URLSearchParams(searchParams)
      next.delete('reportInterviewId')
      setSearchParams(next, { replace: true })
    }
  }, [searchParams, setSearchParams, targetReportInterviewId])

  const applyTemplate = useCallback((template: Template) => setForm(previous => ({ ...previous, title: template.title, duration: template.duration, type: template.type, questionCount: template.questionCount, source: 'bank' })), [])
  const applyBulkTemplate = useCallback((template: Template) => setBulk(previous => ({ ...previous, templateId: template.id, title: template.title, duration: template.duration, type: template.type, questionCount: template.questionCount, source: 'bank', questionIds: [] })), [])

  const create = useCallback(async () => {
    if (!form.title.trim() || !form.candidateId || !form.scheduledAt) return data.setError('请填写面试主题、候选人和预约时间。')
    if (form.source === 'question' && !form.questionBankId) return data.setError('请先选择题库，再选择面试题目。')
    if (form.source === 'question' && !form.questionIds.length) return data.setError('请至少选择一道题目。')
    if (form.source === 'bank' && !form.questionBankId) return data.setError('请选择题库。')
    setSaving(true)
    try {
      await adminInterviewsApi.createInterview({ title: form.title, candidateId: form.candidateId, scheduledAt: toBackendTime(form.scheduledAt), duration: form.duration, type: form.type, interviewerStyle: form.interviewerStyle, questionIds: form.source === 'question' ? form.questionIds : [], questionBankId: form.source === 'bank' ? form.questionBankId : undefined, questionCount: form.source === 'bank' ? form.questionCount : undefined })
      setDialog(false)
      setForm(defaultForm())
      await data.load()
    } catch (reason) {
      data.setError(reason instanceof Error ? reason.message : '面试创建失败，请稍后重试。')
    } finally { setSaving(false) }
  }, [data, form])

  const createBulk = useCallback(async () => {
    if (!bulk.title.trim()) return data.setError('请填写批量面试主题。')
    if (!bulk.candidateIds.length) return data.setError('请至少选择一名候选人。')
    if (bulk.source === 'question' && !bulk.questionBankId) return data.setError('请先选择题库，再选择面试题目。')
    if (bulk.source === 'question' && !bulk.questionIds.length) return data.setError('请至少选择一道题目。')
    if (bulk.source === 'bank' && !bulk.questionBankId) return data.setError('请选择批量面试题库。')
    setSaving(true)
    try {
      const start = new Date(bulk.scheduledAt)
      for (const [index, candidateId] of bulk.candidateIds.entries()) {
        const scheduled = new Date(start.getTime() + index * bulk.interval * 60_000)
        await adminInterviewsApi.createInterview({ title: bulk.title, candidateId, scheduledAt: `${scheduled.getFullYear()}-${String(scheduled.getMonth() + 1).padStart(2, '0')}-${String(scheduled.getDate()).padStart(2, '0')}T${String(scheduled.getHours()).padStart(2, '0')}:${String(scheduled.getMinutes()).padStart(2, '0')}:00`, duration: bulk.duration, type: bulk.type, interviewerStyle: bulk.interviewerStyle, questionIds: bulk.source === 'question' ? bulk.questionIds : [], questionBankId: bulk.source === 'bank' ? bulk.questionBankId : undefined, questionCount: bulk.source === 'bank' ? bulk.questionCount : undefined })
      }
      setDialog(false); setCreateMode('single'); setBulk(defaultBulk()); await data.load()
    } catch (reason) {
      data.setError(reason instanceof Error ? reason.message : '批量面试创建失败，请稍后重试。')
    } finally { setSaving(false) }
  }, [bulk, data])

  const confirmInterviewAction = useCallback(async () => {
    if (!actionTarget) return
    setActionBusy(true)
    try {
      if (actionTarget.type === 'pass') {
        await adminInterviewsApi.passInterview(actionTarget.interview.id)
        await data.load()
      } else {
        await adminInterviewsApi.deleteInterview(actionTarget.interview.id)
        data.setItems(previous => previous.filter(item => String(item.id) !== String(actionTarget.interview.id)))
        data.setReports(previous => previous.filter(item => String(item.interviewId) !== String(actionTarget.interview.id)))
      }
      setActionTarget(undefined)
    } catch (reason) {
      data.setError(reason instanceof Error ? reason.message : '操作失败，请稍后重试。')
    } finally { setActionBusy(false) }
  }, [actionTarget, data])

  const openCreate = useCallback((template?: Template) => {
    setCreateMode('single'); setForm(defaultForm()); setBulk(defaultBulk())
    if (template) setForm(previous => ({ ...previous, title: template.title, duration: template.duration, type: template.type, questionCount: template.questionCount, source: 'bank' }))
    setDialog(true)
  }, [])

  return <div className="space-y-6">
    <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between"><div><p className="text-sm font-semibold text-[var(--accent)]">面试安排</p><h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">面试管理</h1><p className="mt-3 max-w-2xl text-muted-foreground">创建和管理面试安排，查看回顾与评测报告。</p></div><Button onClick={() => openCreate()}><Plus className="h-4 w-4" />创建面试</Button></header>
    {data.error && <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"><span className="min-w-0 flex-1">{data.error}</span><Button type="button" variant="secondary" className="h-9 shrink-0 px-3 text-xs" disabled={data.loading || data.refreshing} onClick={() => void data.load()}><RefreshCw className={data.refreshing ? 'h-3.5 w-3.5 animate-spin' : 'h-3.5 w-3.5'} />{data.refreshing ? '刷新中' : '重试'}</Button></div>}
    <section className="mt-7 grid gap-4 md:grid-cols-4">{templates.map((item, index) => <Card key={item.id} motionDelay={index * 0.04} className="cursor-pointer bg-[linear-gradient(180deg,var(--surface),color-mix(in_srgb,var(--surface)_84%,var(--accent-soft)))]" onClick={() => openCreate(item)}><Layers3 className="h-5 w-5 text-[var(--accent)]" /><h3 className="mt-4 font-bold">{item.name}</h3><p className="mt-2 text-sm leading-6 text-muted-foreground">{item.note}</p><p className="mt-4 text-xs text-muted-foreground">{item.duration} 分钟 · {item.questionCount} 题</p></Card>)}</section>
    <Card className="mt-7 p-0" initial={false}><AdminInterviewFilters filters={filters} candidates={data.candidates} onChange={next => setFilters(previous => ({ ...previous, ...next }))} /><AdminInterviewList items={list} reports={data.reportByInterviewId} candidates={data.candidateById} loading={data.loading} onNotice={setNoticeTarget} onAction={setActionTarget} onReport={report => void openReport(report)} /></Card>
    {dialog && <AdminInterviewCreateDialog saving={saving} mode={createMode} setMode={setCreateMode} onClose={() => setDialog(false)} onSingleSubmit={() => void create()} onBulkSubmit={() => void createBulk()} form={form} setForm={setForm} bulk={bulk} setBulk={setBulk} candidates={data.candidates} questionsByBank={data.questionsByBank} questionLoadingBank={data.questionLoadingBank} loadBankQuestions={data.loadBankQuestions} banks={data.banks} templates={templates} applyTemplate={applyTemplate} applyBulkTemplate={applyBulkTemplate} />}
    {noticeTarget && <AdminInterviewNotificationDialog interview={noticeTarget} candidate={data.candidateById.get(String(noticeTarget.candidateId))} onClose={() => setNoticeTarget(undefined)} />}
    {actionTarget && <AdminInterviewActionDialog target={actionTarget} candidate={data.candidateById.get(String(actionTarget.interview.candidateId))} busy={actionBusy} onClose={() => setActionTarget(undefined)} onConfirm={() => void confirmInterviewAction()} />}
    {selectedReport && <AdminInterviewReportDrawer report={selectedReport} detail={reportDetail} loading={reportLoading} onClose={closeReport} onRegenerated={() => openReport(selectedReport)} />}
  </div>
}
