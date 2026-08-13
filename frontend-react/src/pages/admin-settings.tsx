import { Activity, AudioLines, Bot, BrainCircuit, CheckCircle2, Database, Edit3, Eye, EyeOff, KeyRound, Loader2, Mic2, Plus, Server, Settings2, Speech, Trash2, Volume2, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { AdminConfirmDialog } from '@/components/admin-confirm-dialog'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { request } from '@/lib/api'

type ProviderKind = 'llm' | 'virtual-human' | 'speech' | 'asr' | 'tts'
type Provider = {
  id: string
  name: string
  code: string
  kind: ProviderKind
  baseUrl: string
  chatModel: string
  voiceModel: string
  avatarModel: string
  apiKey: string
  apiSecret: string
  appId: string
  enabled: boolean
  textDefault: boolean
  voiceDefault: boolean
  remark: string
}

type ProviderTestResult = {
  success: boolean
  statusCode: number | null
  latencyMs: number
  message: string
}

const emptyProvider: Provider = {
  id: '',
  name: '',
  code: '',
  kind: 'llm',
  baseUrl: '',
  chatModel: '',
  voiceModel: '',
  avatarModel: '',
  apiKey: '',
  apiSecret: '',
  appId: '',
  enabled: true,
  textDefault: false,
  voiceDefault: false,
  remark: '',
}

const kindMap: Record<ProviderKind, { label: string; icon: typeof Server; tone: 'success' | 'info' | 'warning' | 'default' }> = {
  llm: { label: '大模型', icon: BrainCircuit, tone: 'success' },
  'virtual-human': { label: '虚拟人', icon: Bot, tone: 'info' },
  speech: { label: '浏览器语音', icon: Volume2, tone: 'default' },
  asr: { label: '语音识别', icon: AudioLines, tone: 'warning' },
  tts: { label: '语音合成', icon: Speech, tone: 'info' },
}

function canBeTextDefault(item: Provider) {
  return item.kind === 'llm'
}

function canBeVoiceDefault(item: Provider) {
  return item.kind === 'virtual-human' || item.kind === 'speech' || item.kind === 'asr' || item.kind === 'tts'
}

function canTestProvider(item: Provider) {
  if (!item.enabled) return false
  if (item.kind === 'speech') return true
  return Boolean(item.baseUrl.trim()) && item.baseUrl !== '待配置'
}

function defaultDeleteReason(item: Provider) {
  if (item.textDefault) return '当前服务为文字默认项，请先切换默认服务。'
  if (item.voiceDefault) return '当前服务为语音默认项，请先切换默认服务。'
  return ''
}

function secretLabel(value: string) {
  return value || '未配置'
}

function providerLabels(kind: ProviderKind, code = '') {
  if (code === 'open-talking-virtual-human') {
    return {
      chatModel: 'OpenTalking 模型',
      voiceModel: 'TTS 音色',
      avatarModel: 'OpenTalking Avatar ID',
      appId: 'TTS Provider',
      apiKey: '不使用',
      apiSecret: '不使用',
      baseUrlHint: '例如：http://127.0.0.1:8000',
    }
  }
  if (kind === 'virtual-human') {
    return {
      chatModel: '接口服务 ID',
      voiceModel: '发音人 / 音色',
      avatarModel: '虚拟人形象 ID',
      appId: 'APP ID',
      apiKey: 'API Key',
      apiSecret: 'API Secret',
      baseUrlHint: '例如：http://127.0.0.1:8000',
    }
  }
  if (kind === 'asr') return { chatModel: '聊天模型', voiceModel: '语音识别模型', avatarModel: '虚拟人形象', appId: 'APP ID', apiKey: 'API Key', apiSecret: 'API Secret', baseUrlHint: '' }
  if (kind === 'tts') return { chatModel: '聊天模型', voiceModel: '语音合成模型', avatarModel: '虚拟人形象', appId: 'APP ID', apiKey: 'API Key', apiSecret: 'API Secret', baseUrlHint: '' }
  return { chatModel: '聊天模型', voiceModel: '语音模型', avatarModel: '虚拟人形象', appId: 'APP ID', apiKey: 'API Key', apiSecret: 'API Secret', baseUrlHint: '' }
}

function Field({ label, value, secret = false }: { label: string; value: string; secret?: boolean }) {
  const [visible, setVisible] = useState(false)
  const displayValue = secret && !visible ? secretLabel(value) : value || '未配置'

  return <div className="grid min-w-0 gap-1.5 rounded-2xl border border-border bg-background/60 px-4 py-3 text-sm sm:grid-cols-[minmax(7.5rem,.42fr)_minmax(0,1fr)] sm:items-center sm:gap-4">
    <span className="min-w-0 break-words text-muted-foreground">{label}</span>
    <span className="flex min-w-0 items-start justify-between gap-2 sm:justify-end">
      <span className="min-w-0 break-all font-semibold leading-5 sm:text-right">{displayValue}</span>
      {secret && value && <Button
        type="button"
        variant="ghost"
        onClick={() => setVisible(!visible)}
        className="h-8 w-8 shrink-0 rounded-full px-0 text-muted-foreground hover:text-foreground"
        aria-label={visible ? '隐藏密钥' : '查看密钥'}
        aria-pressed={visible}
      >
        {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
      </Button>}
    </span>
  </div>
}

export function AdminSettings() {
  const [items, setItems] = useState<Provider[]>([])
  const [editing, setEditing] = useState<Provider | null>(null)
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [testingId, setTestingId] = useState('')
  const [updatingId, setUpdatingId] = useState('')
  const [deletingId, setDeletingId] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<Provider>()

  const enabledCount = items.filter(item => item.enabled).length
  const textDefault = items.find(item => item.textDefault)
  const voiceDefault = items.find(item => item.voiceDefault)
  const groups = useMemo(() => [
    ['模型服务', items.filter(item => item.kind === 'llm')],
    ['虚拟人与语音', items.filter(item => item.kind !== 'llm')],
  ] as const, [items])

  useEffect(() => {
    void refresh()
  }, [])

  async function refresh() {
    setLoading(true)
    try {
      const data = await request<Provider[]>('/v1/admin/ai-providers')
      setItems(data.map(item => ({ ...item, id: String(item.id) })))
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '服务配置加载失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }


  async function submit() {
    if (!editing || saving) return
    if (!editing.name.trim() || !editing.code.trim()) {
      setMessage('请填写服务名称和服务编码。')
      return
    }
    setSaving(true)
    try {
      const existed = items.some(item => item.id === editing.id)
      const path = existed ? `/v1/admin/ai-providers/${editing.id}` : '/v1/admin/ai-providers'
      const saved = await request<Provider>(path, { method: existed ? 'PUT' : 'POST', body: JSON.stringify(editing) })
      setItems(previous => existed ? previous.map(item => item.id === editing.id ? { ...saved, id: String(saved.id) } : item) : [{ ...saved, id: String(saved.id) }, ...previous])
      setEditing(null)
      setMessage(existed ? '服务配置已保存。' : '服务配置已新增。')
      void refresh()
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '服务配置保存失败，请稍后重试。')
    } finally {
      setSaving(false)
    }
  }

  async function updateProvider(item: Provider, patch: Partial<Provider>) {
    setUpdatingId(item.id)
    try {
      const next = { ...item, ...patch }
      const saved = await request<Provider>(`/v1/admin/ai-providers/${item.id}`, { method: 'PUT', body: JSON.stringify(next) })
      setItems(previous => previous.map(current => current.id === item.id ? { ...saved, id: String(saved.id) } : current))
      setMessage(`${item.name} 已更新。`)
      void refresh()
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '服务配置更新失败，请稍后重试。')
    } finally {
      setUpdatingId('')
    }
  }

  async function remove(item: Provider) {
    setDeletingId(item.id)
    try {
      await request(`/v1/admin/ai-providers/${item.id}`, { method: 'DELETE' })
      setItems(previous => previous.filter(current => current.id !== item.id))
      setMessage(`${item.name} 已删除。`)
      setDeleteTarget(undefined)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '服务配置删除失败，请稍后重试。')
    } finally {
      setDeletingId('')
    }
  }

  async function testProvider(item: Provider) {
    setTestingId(item.id)
    try {
      const result = await request<ProviderTestResult>(`/v1/admin/ai-providers/${item.id}/test`, { method: 'POST' })
      const statusText = result.statusCode ? `HTTP ${result.statusCode} · ` : ''
      setMessage(`${item.name}：${result.success ? '测试通过' : '测试未通过'}，${statusText}${result.latencyMs}ms。${result.message}`)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '连通性测试失败，请检查服务地址与密钥。')
    } finally {
      setTestingId('')
    }
  }

  return <div className="space-y-6">
    <header className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
      <div className="flex items-start gap-4">
        <span className="grid h-14 w-14 place-items-center rounded-[22px] bg-[linear-gradient(135deg,var(--brand),var(--brand-pink))] text-white shadow-[0_18px_42px_rgba(109,93,252,.25)]">
          <Settings2 className="h-7 w-7" />
        </span>
        <div>
          <p className="text-sm font-semibold text-[var(--accent)]">服务配置</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">系统设置</h1>
          <p className="mt-3 max-w-2xl text-muted-foreground">管理大模型、虚拟人与语音服务。密钥由后端加密保存。</p>
        </div>
      </div>
      <Button onClick={() => setEditing({ ...emptyProvider })}><Plus className="h-4 w-4" />新增服务</Button>
    </header>

    {message && <div className="mt-6 flex items-center justify-between rounded-[22px] border border-border bg-[var(--accent-soft)] px-5 py-4 text-sm text-[var(--accent)]">
      <span className="inline-flex items-center gap-2"><CheckCircle2 className="h-4 w-4" />{message}</span>
      <Button type="button" variant="ghost" className="h-9 w-9 rounded-full px-0" onClick={() => setMessage('')} aria-label="关闭提示"><X className="h-4 w-4" /></Button>
    </div>}

    <div className="mt-7 grid gap-4 md:grid-cols-3">
      <Card><p className="text-sm text-muted-foreground">已启用服务</p><strong className="mt-3 block text-3xl">{loading ? '…' : enabledCount}</strong><p className="mt-2 text-xs text-muted-foreground">停用后不再用于新面试</p></Card>
      <Card><p className="text-sm text-muted-foreground">文字默认模型</p><strong className="mt-3 block break-words text-2xl">{textDefault?.name ?? '未设置'}</strong><p className="mt-2 break-all text-xs text-muted-foreground">{textDefault?.chatModel ?? '用于 AI 提问、追问、评分'}</p></Card>
      <Card><p className="text-sm text-muted-foreground">默认语音服务</p><strong className="mt-3 block break-words text-2xl">{voiceDefault?.name ?? '未设置'}</strong><p className="mt-2 break-all text-xs text-muted-foreground">{voiceDefault?.voiceModel ?? '用于朗读、语音识别或虚拟人播报'}</p></Card>
    </div>

    {loading ? <Card className="mt-8 flex items-center gap-3"><Loader2 className="h-5 w-5 animate-spin" />正在加载系统配置…</Card> : <div className="mt-8 space-y-9">
      {groups.map(([title, providers]) => <section key={title}>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-xl font-bold">{title}</h2>
          <span className="text-sm text-muted-foreground">{providers.length} 个配置</span>
        </div>
        <div className="grid gap-5 xl:grid-cols-2">
          {providers.map((item, index) => {
            const meta = kindMap[item.kind]
            const Icon = meta.icon
            const testable = canTestProvider(item)
            const deleteReason = defaultDeleteReason(item)
            const updating = updatingId === item.id
            const deleting = deletingId === item.id
            const labels = providerLabels(item.kind, item.code)
            return <Card key={item.id} motionDelay={index * .04} className="min-w-0 overflow-hidden p-0">
              <div className="flex flex-col gap-4 p-4 sm:flex-row sm:items-start sm:justify-between sm:p-5">
                <div className="flex min-w-0 items-center gap-3">
                  <span className="grid h-11 w-11 shrink-0 place-items-center rounded-[18px] bg-[var(--accent-soft)] text-[var(--accent)]"><Icon className="h-5 w-5" /></span>
                  <div className="min-w-0">
                    <h3 className="break-words text-lg font-bold">{item.name}</h3>
                    <p className="break-all text-xs text-muted-foreground">{item.code}</p>
                  </div>
                </div>
                <div className="flex min-w-0 flex-wrap gap-2 sm:shrink-0 sm:justify-end">
                  <Badge tone={item.enabled ? 'success' : 'default'}>{item.enabled ? '已启用' : '已停用'}</Badge>
                  <Badge tone={meta.tone}>{meta.label}</Badge>
                  {item.textDefault && <Badge tone="info">文字默认</Badge>}
                  {item.voiceDefault && <Badge tone="warning">语音默认</Badge>}
                </div>
              </div>

              <div className="min-w-0 space-y-2 border-y border-border bg-background/45 p-4 sm:p-5">
                <Field label="Base URL" value={item.baseUrl} />
                <Field label={labels.chatModel} value={item.chatModel} />
                <Field label={labels.voiceModel} value={item.voiceModel} />
                <Field label={labels.avatarModel} value={item.avatarModel} />
                <Field label={labels.appId} value={item.appId} secret />
                <Field label={labels.apiKey} value={item.apiKey} secret />
                <Field label={labels.apiSecret} value={item.apiSecret} secret />
              </div>

              <div className="min-w-0 p-4 sm:p-5">
                <p className="min-h-10 break-words text-sm leading-6 text-muted-foreground">{item.remark || '暂无说明。'}</p>
                <div className="mt-5 grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:items-center">
                  <Button type="button" variant="secondary" className="h-10 min-w-0 px-3 sm:px-4" disabled={updating || deleting} onClick={() => setEditing(item)}><Edit3 className="h-4 w-4" />编辑</Button>
                  <Button
                    variant="secondary"
                    className="h-10 min-w-0 px-3 sm:px-4"
                    disabled={!testable || testingId === item.id || updating || deleting}
                    title={!item.enabled ? '请先启用服务。' : !testable ? '请先配置服务地址。' : '测试当前服务连通性'}
                    onClick={() => void testProvider(item)}
                  >{testingId === item.id ? <Loader2 className="h-4 w-4 animate-spin" /> : <Activity className="h-4 w-4" />}测试</Button>
                  <Button
                    variant={item.enabled ? 'secondary' : 'primary'}
                    className="h-10 min-w-0 px-3 sm:px-4"
                    disabled={updating || deleting}
                    aria-busy={updating}
                    onClick={() => void updateProvider(item, { enabled: !item.enabled })}
                  >{updating ? <Loader2 className="h-4 w-4 animate-spin" /> : null}{item.enabled ? '停用' : '启用'}</Button>
                  {canBeTextDefault(item) && <Button
                    variant="secondary"
                    className="h-10 min-w-0 px-3 sm:px-4"
                    disabled={!item.enabled || item.textDefault || updating || deleting}
                    onClick={() => void updateProvider(item, { textDefault: true })}
                  ><Database className="h-4 w-4" />{item.textDefault ? '文字默认' : '设为文字'}</Button>}
                  {canBeVoiceDefault(item) && <Button
                    variant="secondary"
                    className="h-10 min-w-0 px-3 sm:px-4"
                    disabled={!item.enabled || item.voiceDefault || updating || deleting}
                    onClick={() => void updateProvider(item, { voiceDefault: true })}
                  ><Mic2 className="h-4 w-4" />{item.voiceDefault ? '语音默认' : '设为语音'}</Button>}
                  <Button
                    variant="secondary"
                    className="h-10 min-w-0 px-3 text-rose-700 hover:border-rose-200 hover:bg-rose-50 sm:px-4 dark:text-rose-200 dark:hover:bg-rose-950/30"
                    disabled={Boolean(deleteReason) || updating || deleting}
                    title={deleteReason || '删除当前服务配置'}
                    onClick={() => setDeleteTarget(item)}
                  >{deleting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}删除</Button>
                </div>
              </div>
            </Card>
          })}
        </div>
      </section>)}
    </div>}

    {editing && <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-0 backdrop-blur-sm sm:p-4" role="dialog" aria-modal="true" aria-labelledby="provider-dialog-title">
      <div className="mx-auto mt-[8vh] min-h-[92vh] w-full max-w-3xl rounded-t-[28px] bg-surface p-5 shadow-2xl sm:my-8 sm:min-h-0 sm:rounded-[30px] sm:p-7">
        <div className="flex items-start justify-between">
          <div>
            <p className="text-sm font-semibold text-[var(--accent)]">服务配置</p>
            <h2 id="provider-dialog-title" className="mt-1 text-2xl font-bold">{editing.id ? '编辑服务' : '新增服务'}</h2>
            <p className="mt-2 text-sm text-muted-foreground">密钥保留脱敏值时，系统将沿用原密钥。</p>
          </div>
          <Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={() => setEditing(null)} aria-label="关闭服务配置对话框"><X className="h-5 w-5" /></Button>
        </div>

        <div className="mt-6 grid gap-5 md:grid-cols-2">
          <label className="block text-sm font-semibold">服务名称<input value={editing.name} onChange={event => setEditing({ ...editing, name: event.target.value })} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="block text-sm font-semibold">服务编码<input value={editing.code} onChange={event => setEditing({ ...editing, code: event.target.value })} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="block text-sm font-semibold">类型<ResponsiveSelect
            ariaLabel="选择服务类型"
            value={editing.kind}
            onValueChange={next => setEditing({ ...editing, kind: next as ProviderKind })}
            className="mt-2 w-full"
            options={Object.entries(kindMap).map(([value, meta]) => ({ value, label: meta.label }))}
          /></label>
          <label className="block text-sm font-semibold">Base URL<input value={editing.baseUrl} placeholder={providerLabels(editing.kind, editing.code).baseUrlHint} onChange={event => setEditing({ ...editing, baseUrl: event.target.value })} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="block text-sm font-semibold">{providerLabels(editing.kind, editing.code).chatModel}<input value={editing.chatModel} onChange={event => setEditing({ ...editing, chatModel: event.target.value })} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="block text-sm font-semibold">{providerLabels(editing.kind, editing.code).voiceModel}<input value={editing.voiceModel} onChange={event => setEditing({ ...editing, voiceModel: event.target.value })} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="block text-sm font-semibold">{providerLabels(editing.kind, editing.code).avatarModel}<input value={editing.avatarModel} onChange={event => setEditing({ ...editing, avatarModel: event.target.value })} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="block text-sm font-semibold">{providerLabels(editing.kind, editing.code).appId}<input value={editing.appId} onChange={event => setEditing({ ...editing, appId: event.target.value })} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="block text-sm font-semibold">{providerLabels(editing.kind, editing.code).apiKey}<input value={editing.apiKey} onChange={event => setEditing({ ...editing, apiKey: event.target.value })} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
          <label className="block text-sm font-semibold">{providerLabels(editing.kind, editing.code).apiSecret}<input value={editing.apiSecret} onChange={event => setEditing({ ...editing, apiSecret: event.target.value })} className="mt-2 h-12 w-full rounded-2xl border border-border bg-background px-4 font-normal outline-none focus:border-[var(--accent)]" /></label>
        </div>
        <label className="mt-5 block text-sm font-semibold">配置说明<textarea value={editing.remark} onChange={event => setEditing({ ...editing, remark: event.target.value })} className="mt-2 min-h-24 w-full rounded-2xl border border-border bg-background px-4 py-3 font-normal outline-none focus:border-[var(--accent)]" /></label>
        <div className="mt-5 grid gap-3 text-sm sm:grid-cols-3">
          <label className="inline-flex items-center gap-2"><input type="checkbox" checked={editing.enabled} onChange={event => setEditing({ ...editing, enabled: event.target.checked })} />启用服务</label>
          <label className="inline-flex items-center gap-2"><input type="checkbox" checked={editing.textDefault} disabled={!canBeTextDefault(editing)} onChange={event => setEditing({ ...editing, textDefault: event.target.checked })} />文字默认</label>
          <label className="inline-flex items-center gap-2"><input type="checkbox" checked={editing.voiceDefault} disabled={!canBeVoiceDefault(editing)} onChange={event => setEditing({ ...editing, voiceDefault: event.target.checked })} />语音默认</label>
        </div>
        <div className="mt-7 grid grid-cols-2 gap-3 sm:flex sm:justify-end">
          <Button variant="secondary" className="w-full sm:w-auto" disabled={saving} onClick={() => setEditing(null)}>取消</Button>
          <Button className="w-full sm:w-auto" disabled={saving} onClick={() => void submit()}>{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}保存配置</Button>
        </div>
      </div>
    </div>}
    {deleteTarget && <AdminConfirmDialog
      title="删除服务配置"
      description={`确定删除 ${deleteTarget.name} 配置吗？删除后需要重新录入密钥。`}
      confirmLabel="确认删除"
      danger
      busy={deletingId === deleteTarget.id}
      onClose={() => { if (!deletingId) setDeleteTarget(undefined) }}
      onConfirm={() => void remove(deleteTarget)}
    />}
  </div>
}
