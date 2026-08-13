import { ArrowLeft, Loader2, Plus, Save, X } from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect, type ResponsiveSelectOption } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'
import type { PositionDetail, RecruitmentJob } from '@/lib/recruitment'

type PositionFormState = {
  positionCode: string
  name: string
  department: string
  salaryMin: string
  salaryMax: string
  city: string
  experienceRequirement: string
  educationRequirement: string
  jobType: string
  description: string
  requirements: string
  skillTags: string[]
  expiresAt: string
}

const emptyForm: PositionFormState = {
  positionCode: '', name: '', department: '', salaryMin: '', salaryMax: '', city: '',
  experienceRequirement: '', educationRequirement: '', jobType: 'FULL_TIME', description: '',
  requirements: '', skillTags: [], expiresAt: '',
}

const jobTypeOptions: ResponsiveSelectOption[] = [
  { value: 'FULL_TIME', label: '全职' },
  { value: 'PART_TIME', label: '兼职' },
  { value: 'INTERNSHIP', label: '实习' },
]

export function CompanyPositionForm() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const editing = Boolean(id)
  const [form, setForm] = useState<PositionFormState>(emptyForm)
  const [skillInput, setSkillInput] = useState('')
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [initialSnapshot, setInitialSnapshot] = useState(() => JSON.stringify(emptyForm))
  const serialized = useMemo(() => JSON.stringify(form), [form])
  const dirty = serialized !== initialSnapshot

  useEffect(() => {
    if (!id) {
      setInitialSnapshot(JSON.stringify(emptyForm))
      setLoading(false)
      return
    }
    let active = true
    setLoading(true)
    request<PositionDetail>(`/v1/company/recruitment/positions/${id}`)
      .then(result => {
        if (!active) return
        const next = fromJob(result.job)
        setForm(next)
        setInitialSnapshot(JSON.stringify(next))
      })
      .catch(reason => { if (active) setError(reason instanceof Error ? reason.message : '岗位加载失败') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [id])

  useEffect(() => {
    if (!dirty) return
    const beforeUnload = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    const interceptNavigation = (event: MouseEvent) => {
      const target = event.target
      if (!(target instanceof Element)) return
      const anchor = target.closest('a[href]')
      if (!anchor || anchor.getAttribute('target') === '_blank') return
      const href = anchor.getAttribute('href')
      if (!href || href.startsWith('#') || href.startsWith('mailto:')) return
      const next = new URL(href, window.location.origin)
      if (next.origin !== window.location.origin || next.pathname === window.location.pathname) return
      if (!window.confirm('当前岗位还有未保存的修改，确定离开吗？')) {
        event.preventDefault()
        event.stopImmediatePropagation()
      }
    }
    window.addEventListener('beforeunload', beforeUnload)
    document.addEventListener('click', interceptNavigation, true)
    return () => {
      window.removeEventListener('beforeunload', beforeUnload)
      document.removeEventListener('click', interceptNavigation, true)
    }
  }, [dirty])

  function update<K extends keyof PositionFormState>(key: K, value: PositionFormState[K]) {
    setForm(current => ({ ...current, [key]: value }))
  }

  function addSkills(value: string) {
    const next = value.split(/[、,，]/).map(item => item.trim()).filter(Boolean)
    if (!next.length) return
    setForm(current => ({ ...current, skillTags: [...current.skillTags, ...next].filter((item, index, all) => all.indexOf(item) === index).slice(0, 20) }))
    setSkillInput('')
  }

  function handleSkillKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter' || event.key === ',' || event.key === '，') {
      event.preventDefault()
      addSkills(skillInput)
    }
  }

  function leave(path: string) {
    if (!dirty || window.confirm('当前岗位还有未保存的修改，确定离开吗？')) navigate(path)
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (form.salaryMin && form.salaryMax && Number(form.salaryMax) < Number(form.salaryMin)) {
      setError('薪资上限不能低于下限')
      return
    }
    if (form.skillTags.length > 20) {
      setError('最多添加 20 个技能标签')
      return
    }
    setSaving(true)
    try {
      const saved = await request<RecruitmentJob>(editing ? `/v1/company/recruitment/positions/${id}` : '/v1/company/recruitment/positions', {
        method: editing ? 'PUT' : 'POST',
        body: JSON.stringify({
          positionCode: form.positionCode.trim(), name: form.name.trim(), department: form.department.trim() || undefined,
          salaryMin: form.salaryMin ? Number(form.salaryMin) : undefined, salaryMax: form.salaryMax ? Number(form.salaryMax) : undefined,
          city: form.city.trim() || undefined, experienceRequirement: form.experienceRequirement.trim() || undefined,
          educationRequirement: form.educationRequirement.trim() || undefined, jobType: form.jobType,
          description: form.description.trim() || undefined, requirements: form.requirements.trim() || undefined,
          skillTags: form.skillTags, expiresAt: form.expiresAt || undefined,
        }),
      })
      setInitialSnapshot(JSON.stringify(form))
      navigate(`/company/positions/${saved.id}`, { state: { message: editing ? '岗位内容已保存。' : '岗位草稿已创建。' } })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '岗位保存失败')
    } finally {
      setSaving(false)
    }
  }

  const notice = (location.state as { message?: string } | null)?.message

  if (loading) return <Card className="grid min-h-64 place-items-center"><Loader2 className="h-7 w-7 animate-spin text-[var(--accent)]" /></Card>
  if (editing && error && !form.name) return <Card className="border-rose-200 bg-rose-50/80 p-5 text-sm text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/30" role="alert">{error}<Button type="button" variant="secondary" className="ml-3 h-9 px-3" onClick={() => window.location.reload()}>重试</Button></Card>

  return <div className="space-y-6">
    <header className="flex items-start gap-3"><Button type="button" variant="ghost" className="h-10 shrink-0 px-3" onClick={() => leave(editing ? `/company/positions/${id}` : '/company/positions')}><ArrowLeft className="h-4 w-4" />返回</Button><div><p className="text-sm font-bold text-[var(--accent)]">企业招聘 · {editing ? '编辑岗位' : '新建岗位'}</p><h1 className="mt-2 text-3xl font-black tracking-[-.04em]">{editing ? '编辑岗位内容' : '创建岗位草稿'}</h1><p className="mt-2 text-muted-foreground">先保存草稿，再在详情页完成发布前检查和状态动作。</p></div></header>
    {notice && <p className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/30 dark:text-emerald-200">{notice}</p>}
    {error && <p role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-200">{error}</p>}
    <form className="space-y-5" onSubmit={submit}>
      <Card><SectionTitle title="基本信息" description="候选人首先看到的岗位识别信息。" /><div className="mt-5 grid gap-4 sm:grid-cols-2"><Field label="岗位编码" required><input required maxLength={64} className={inputClass} value={form.positionCode} onChange={event => update('positionCode', event.target.value)} placeholder="例如 XY-JAVA-2026-003" /></Field><Field label="岗位名称" required><input required maxLength={128} className={inputClass} value={form.name} onChange={event => update('name', event.target.value)} placeholder="例如 Java 开发工程师" /></Field><Field label="所属部门"><input maxLength={128} className={inputClass} value={form.department} onChange={event => update('department', event.target.value)} placeholder="例如 云平台研发部" /></Field><Field label="工作城市"><input maxLength={96} className={inputClass} value={form.city} onChange={event => update('city', event.target.value)} placeholder="例如 北京" /></Field><Field label="岗位类型" required><ResponsiveSelect ariaLabel="岗位类型" value={form.jobType} onValueChange={value => update('jobType', value)} options={jobTypeOptions} /></Field><Field label="招聘截止时间"><input type="datetime-local" className={inputClass} value={form.expiresAt} onChange={event => update('expiresAt', event.target.value)} /></Field></div></Card>
      <Card><SectionTitle title="岗位条件" description="结构化条件会同时用于筛选和候选人预览。" /><div className="mt-5 grid gap-4 sm:grid-cols-2"><Field label="薪资下限（K）"><input min="0" type="number" className={inputClass} value={form.salaryMin} onChange={event => update('salaryMin', event.target.value)} /></Field><Field label="薪资上限（K）"><input min="0" type="number" className={inputClass} value={form.salaryMax} onChange={event => update('salaryMax', event.target.value)} /></Field><Field label="经验要求"><input maxLength={64} className={inputClass} value={form.experienceRequirement} onChange={event => update('experienceRequirement', event.target.value)} placeholder="例如 3-5年" /></Field><Field label="学历要求"><input maxLength={64} className={inputClass} value={form.educationRequirement} onChange={event => update('educationRequirement', event.target.value)} placeholder="例如 本科及以上" /></Field></div><div className="mt-4"><Field label="技能标签"><div className="mt-2 flex gap-2"><input className={inputClass} value={skillInput} onChange={event => setSkillInput(event.target.value)} onKeyDown={handleSkillKeyDown} placeholder="输入技能后按 Enter，例如 Java" /><Button type="button" variant="secondary" className="h-12 shrink-0 px-4" onClick={() => addSkills(skillInput)}><Plus className="h-4 w-4" />添加</Button></div></Field><div className="mt-3 flex min-h-8 flex-wrap gap-2">{form.skillTags.map(tag => <Badge key={tag} tone="info"><span>{tag}</span><button type="button" className="ml-1 rounded-full hover:bg-white/20" onClick={() => update('skillTags', form.skillTags.filter(item => item !== tag))} aria-label={`移除技能 ${tag}`}><X className="h-3 w-3" /></button></Badge>)}{!form.skillTags.length && <span className="text-xs text-muted-foreground">还没有技能标签。发布前至少添加一个。</span>}</div></div></Card>
      <Card><SectionTitle title="岗位说明" description="发布前必须补充岗位介绍、任职要求和至少一个技能标签。" /><div className="mt-5 space-y-4"><Field label="岗位介绍"><textarea maxLength={10000} className={textareaClass} value={form.description} onChange={event => update('description', event.target.value)} placeholder="介绍岗位职责、团队和工作内容" /></Field><Field label="任职要求"><textarea maxLength={10000} className={textareaClass} value={form.requirements} onChange={event => update('requirements', event.target.value)} placeholder="描述必备能力、经验和加分项" /></Field></div></Card>
      <div className="sticky bottom-3 z-10 flex flex-col-reverse gap-2 rounded-2xl border border-border bg-surface/95 p-3 shadow-xl backdrop-blur sm:flex-row sm:justify-end"><Button type="button" variant="secondary" onClick={() => leave(editing ? `/company/positions/${id}` : '/company/positions')}>取消</Button><Button type="submit" disabled={saving}>{saving ? <><Loader2 className="h-4 w-4 animate-spin" />保存中</> : <><Save className="h-4 w-4" />保存草稿</>}</Button></div>
    </form>
  </div>
}

function fromJob(job: RecruitmentJob): PositionFormState {
  return { positionCode: job.positionCode, name: job.name, department: job.department || '', salaryMin: job.salaryMin?.toString() || '', salaryMax: job.salaryMax?.toString() || '', city: job.city || '', experienceRequirement: job.experienceRequirement || '', educationRequirement: job.educationRequirement || '', jobType: job.jobType, description: job.description || '', requirements: job.requirements || '', skillTags: job.skillTags || [], expiresAt: job.expiresAt?.slice(0, 16) || '' }
}

const inputClass = 'mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 text-sm outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20'
const textareaClass = 'mt-2 min-h-36 w-full rounded-2xl border border-border bg-background p-4 text-sm leading-7 outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20'

function Field({ label, required, children }: { label: string; required?: boolean; children: ReactNode }) { return <label className="block text-sm font-bold">{label}{required && <span className="ml-1 text-rose-500">*</span>}{children}</label> }
function SectionTitle({ title, description }: { title: string; description: string }) { return <div><h2 className="text-lg font-black">{title}</h2><p className="mt-1 text-sm text-muted-foreground">{description}</p></div> }
