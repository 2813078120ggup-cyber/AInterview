import { CheckCircle2, FileCode2, History, Loader2, Plus, RotateCcw, Save, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { AdminConfirmDialog } from '@/components/admin-confirm-dialog'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'

type PromptSummary = {
  code: string
  name: string
  category: string
  description: string
  variables: string[]
  activeVersion?: number
  latestVersion?: number
  activatedAt?: string
}

type PromptVersion = {
  id: string
  code: string
  name: string
  category: string
  version: number
  systemTemplate: string
  userTemplate: string
  active: boolean
  changeNote?: string
  createdBy?: string
  createdAt: string
  activatedAt?: string
}

type ActivationLog = {
  id: string
  code: string
  fromVersion?: number
  toVersion: number
  action: 'INITIAL' | 'ACTIVATE' | 'ROLLBACK'
  note?: string
  operatorId?: string
  createdAt: string
}

type PromptDetail = {
  summary: PromptSummary
  versions: PromptVersion[]
  activationHistory: ActivationLog[]
}

type Draft = { systemTemplate: string; userTemplate: string; changeNote: string; activate: boolean }

const categoryNames: Record<string, string> = {
  SIMULATION_INTERVIEW: '模拟面试提示词',
  FREE_INTERVIEW: '自由面试提示词',
  RESUME_ANALYSIS: '简历分析提示词',
  REPORT_SCORING: '报告与评分提示词',
}

function dateText(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚未激活'
}

export function AdminPromptTemplates() {
  const [items, setItems] = useState<PromptSummary[]>([])
  const [detail, setDetail] = useState<PromptDetail>()
  const [draft, setDraft] = useState<Draft>()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [versionAction, setVersionAction] = useState<{ version: PromptVersion; rollback: boolean }>()

  const groups = useMemo(() => Object.entries(categoryNames).map(([code, name]) => ({
    code,
    name,
    prompts: items.filter(item => item.category === code),
  })), [items])

  useEffect(() => { void refresh() }, [])

  async function refresh() {
    setLoading(true)
    try {
      setItems(await request<PromptSummary[]>('/v1/admin/prompt-templates'))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '提示词列表加载失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }

  async function open(code: string) {
    setSaving(true)
    try {
      const value = await request<PromptDetail>(`/v1/admin/prompt-templates/${encodeURIComponent(code)}`)
      setDetail(value)
      setDraft(undefined)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '提示词详情加载失败，请稍后重试。')
    } finally {
      setSaving(false)
    }
  }

  function startVersion() {
    if (!detail) return
    const base = detail.versions.find(item => item.active) ?? detail.versions[0]
    setDraft({
      systemTemplate: base?.systemTemplate ?? '',
      userTemplate: base?.userTemplate ?? '',
      changeNote: '',
      activate: true,
    })
  }

  async function createVersion() {
    if (!detail || !draft || saving) return
    if (!draft.systemTemplate.trim() || !draft.userTemplate.trim() || !draft.changeNote.trim()) {
      setMessage('系统提示词、用户提示词和修改说明都不能为空。')
      return
    }
    setSaving(true)
    try {
      await request(`/v1/admin/prompt-templates/${encodeURIComponent(detail.summary.code)}/versions`, {
        method: 'POST', body: JSON.stringify(draft),
      })
      setMessage(draft.activate ? '新版本已创建并激活。' : '新版本已创建，当前生效版本未改变。')
      setDraft(undefined)
      await Promise.all([refresh(), open(detail.summary.code)])
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '提示词版本创建失败，请稍后重试。')
    } finally {
      setSaving(false)
    }
  }

  async function changeVersion(version: PromptVersion, rollback: boolean) {
    if (!detail || saving) return
    const action = rollback ? '回滚' : '激活'
    setSaving(true)
    try {
      await request(`/v1/admin/prompt-templates/${encodeURIComponent(detail.summary.code)}/versions/${version.version}/${rollback ? 'rollback' : 'activate'}`, {
        method: 'POST', body: JSON.stringify({ note: `管理员${action}到 v${version.version}` }),
      })
      setMessage(`已${action}到 v${version.version}。`)
      await Promise.all([refresh(), open(detail.summary.code)])
    } catch (error) {
      setMessage(error instanceof Error ? error.message : `${action}失败，请稍后重试。`)
    } finally {
      setSaving(false)
      setVersionAction(undefined)
    }
  }

  return <div className="mx-auto max-w-7xl p-4 sm:p-6 lg:p-10">
    <header className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
      <div>
        <p className="text-sm font-semibold text-[var(--accent)]">提示词配置</p>
        <h1 className="mt-2 text-3xl font-bold sm:text-4xl">提示词版本</h1>
        <p className="mt-3 max-w-3xl text-muted-foreground">管理各类 AI 提示词及其版本记录。</p>
      </div>
      <div className="flex items-center gap-2 text-sm text-muted-foreground"><History className="h-4 w-4" />{items.length} 个模板</div>
    </header>

    {message && <div className="mt-6 flex items-center justify-between rounded-2xl border border-border bg-[var(--accent-soft)] px-5 py-4 text-sm text-[var(--accent)]">
      <span className="inline-flex items-center gap-2"><CheckCircle2 className="h-4 w-4" />{message}</span>
      <Button type="button" variant="ghost" className="h-9 w-9 rounded-full px-0" onClick={() => setMessage('')} aria-label="关闭提示"><X className="h-4 w-4" /></Button>
    </div>}

    {loading ? <Card className="mt-8 flex items-center gap-3"><Loader2 className="h-5 w-5 animate-spin" />正在加载提示词版本…</Card> : <div className="mt-8 space-y-9">
      {groups.map(group => <section key={group.code}>
        <div className="mb-4 flex items-center justify-between"><h2 className="text-xl font-bold">{group.name}</h2><span className="text-sm text-muted-foreground">{group.prompts.length} 个模板</span></div>
        <div className="grid gap-4 lg:grid-cols-2">
          {group.prompts.map(prompt => <Card key={prompt.code} className="flex min-h-52 flex-col">
            <div className="flex items-start justify-between gap-4">
              <span className="grid h-11 w-11 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><FileCode2 className="h-5 w-5" /></span>
              <Badge tone={prompt.activeVersion ? 'success' : 'warning'}>{prompt.activeVersion ? `当前 v${prompt.activeVersion}` : '未激活'}</Badge>
            </div>
            <h3 className="mt-5 text-lg font-bold">{prompt.name}</h3>
            <p className="mt-1 font-mono text-xs text-muted-foreground">{prompt.code}</p>
            <p className="mt-3 flex-1 text-sm leading-6 text-muted-foreground">{prompt.description}</p>
            <div className="mt-5 flex items-center justify-between gap-3 border-t border-border pt-4">
              <span className="text-xs text-muted-foreground">最新 v{prompt.latestVersion ?? '-'} · {dateText(prompt.activatedAt)}</span>
              <Button variant="secondary" className="h-9 px-4" disabled={saving} onClick={() => void open(prompt.code)}>管理版本</Button>
            </div>
          </Card>)}
        </div>
      </section>)}
    </div>}

    {detail && <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm">
      <div className="mx-auto my-4 max-w-5xl rounded-[24px] bg-surface shadow-2xl sm:my-6 sm:rounded-3xl">
        <div className="flex items-start justify-between border-b border-border p-4 sm:p-6">
          <div><p className="text-xs font-semibold text-[var(--accent)]">{categoryNames[detail.summary.category]}</p><h2 className="mt-2 text-2xl font-bold">{detail.summary.name}</h2><p className="mt-1 font-mono text-xs text-muted-foreground">{detail.summary.code}</p></div>
          <Button type="button" variant="ghost" className="h-10 w-10 shrink-0 rounded-full px-0" onClick={() => { setDetail(undefined); setDraft(undefined) }} aria-label="关闭"><X className="h-5 w-5" /></Button>
        </div>

        <div className="grid gap-6 p-4 sm:p-6 xl:grid-cols-[1.25fr_.75fr] xl:gap-8">
          <section>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><div><h3 className="font-bold">版本记录</h3><p className="mt-1 text-sm text-muted-foreground">模板正文不可直接覆盖，修改会创建下一个版本。</p></div><Button className="w-full sm:w-auto" onClick={startVersion}><Plus className="h-4 w-4" />新建版本</Button></div>

            {draft && <div className="mt-5 space-y-4 rounded-2xl border border-[var(--accent)] bg-background/70 p-5">
              <label className="block text-sm font-semibold">系统提示词<textarea value={draft.systemTemplate} onChange={event => setDraft({ ...draft, systemTemplate: event.target.value })} className="mt-2 min-h-28 w-full rounded-xl border border-border bg-surface px-4 py-3 font-mono text-sm font-normal leading-6 outline-none focus:border-[var(--accent)]" /></label>
              <label className="block text-sm font-semibold">用户提示词<textarea value={draft.userTemplate} onChange={event => setDraft({ ...draft, userTemplate: event.target.value })} className="mt-2 min-h-64 w-full rounded-xl border border-border bg-surface px-4 py-3 font-mono text-sm font-normal leading-6 outline-none focus:border-[var(--accent)]" /></label>
              <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
                <label className="flex-1 text-sm font-semibold">修改说明<input value={draft.changeNote} onChange={event => setDraft({ ...draft, changeNote: event.target.value })} placeholder="说明本次修改原因" className="mt-2 h-11 w-full rounded-xl border border-border bg-surface px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
                <label className="inline-flex h-11 items-center gap-2 text-sm"><input type="checkbox" checked={draft.activate} onChange={event => setDraft({ ...draft, activate: event.target.checked })} />创建后立即激活</label>
              </div>
              <div className="flex justify-end gap-2"><Button variant="secondary" onClick={() => setDraft(undefined)}>取消</Button><Button disabled={saving} onClick={() => void createVersion()}>{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}保存新版本</Button></div>
            </div>}

            <div className="mt-5 divide-y divide-border overflow-hidden rounded-2xl border border-border">
              {detail.versions.map(version => <div key={version.id} className="bg-background/45 p-5">
                <div className="flex flex-wrap items-start justify-between gap-3"><div className="flex items-center gap-2"><strong>v{version.version}</strong>{version.active && <Badge tone="success">当前生效</Badge>}</div><span className="text-xs text-muted-foreground">{dateText(version.createdAt)}</span></div>
                <p className="mt-2 text-sm text-muted-foreground">{version.changeNote || '无修改说明'}</p>
                <details className="mt-3"><summary className="cursor-pointer text-sm font-semibold text-[var(--accent)]">查看模板正文</summary><div className="mt-3 space-y-3"><pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded-xl bg-muted p-4 text-xs leading-5">{version.systemTemplate}</pre><pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded-xl bg-muted p-4 text-xs leading-5">{version.userTemplate}</pre></div></details>
                {!version.active && <div className="mt-4 flex justify-end"><Button type="button" variant="secondary" className="h-9 px-4" disabled={saving} onClick={() => setVersionAction({ version, rollback: version.version < (detail.summary.activeVersion ?? 0) })}>{version.version < (detail.summary.activeVersion ?? 0) ? <><RotateCcw className="h-4 w-4" />回滚到此版本</> : `激活 v${version.version}`}</Button></div>}
              </div>)}
            </div>
          </section>

          <aside>
            <h3 className="font-bold">可用变量</h3>
            <div className="mt-3 flex flex-wrap gap-2">{detail.summary.variables.map(variable => <code key={variable} className="rounded-lg bg-muted px-2.5 py-1.5 text-xs">${`{${variable}}`}</code>)}</div>
            <h3 className="mt-8 font-bold">激活历史</h3>
            <div className="mt-3 space-y-3">{detail.activationHistory.map(log => <div key={log.id} className="border-l-2 border-[var(--accent)] pl-4"><div className="flex items-center justify-between gap-2"><strong className="text-sm">{log.action === 'ROLLBACK' ? '回滚' : log.action === 'INITIAL' ? '初始化' : '激活'} v{log.toVersion}</strong><span className="text-xs text-muted-foreground">{dateText(log.createdAt)}</span></div><p className="mt-1 text-xs leading-5 text-muted-foreground">{log.note || `${log.fromVersion ?? '-'} → ${log.toVersion}`}</p></div>)}</div>
          </aside>
        </div>
      </div>
    </div>}
    {versionAction && <AdminConfirmDialog
      title={`${versionAction.rollback ? '回滚' : '激活'}到 v${versionAction.version.version}`}
      description="新的 AI 请求会立即使用该版本，请确认当前操作。"
      confirmLabel={versionAction.rollback ? '确认回滚' : '确认激活'}
      busy={saving}
      onClose={() => setVersionAction(undefined)}
      onConfirm={() => void changeVersion(versionAction.version, versionAction.rollback)}
    />}
  </div>
}
