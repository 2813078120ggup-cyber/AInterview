import { Download, Filter, History, RefreshCw, Search, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { auditLogEvent, clearAuditLogs, listAuditLogs, type AuditLog } from '@/lib/audit-log'

const beijingFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

function beijingParts(value: string | Date) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return null
  return Object.fromEntries(beijingFormatter.formatToParts(date).map(part => [part.type, part.value]))
}

function beijingDateKey(value: string | Date) {
  const parts = beijingParts(value)
  return parts ? `${parts.year}-${parts.month}-${parts.day}` : ''
}

function dateText(value: string) {
  const parts = beijingParts(value)
  return parts ? `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}` : value.replace('T', ' ').slice(0, 19)
}

function moduleTone(module: string): 'info' | 'success' | 'default' | 'warning' {
  if (module.includes('面试')) return 'info'
  if (module.includes('题库') || module.includes('题目')) return 'success'
  if (module.includes('配置') || module.includes('系统')) return 'warning'
  return 'default'
}

function loadLogs() {
  return listAuditLogs().sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

export function AdminAuditLog() {
  const [keyword, setKeyword] = useState('')
  const [moduleFilter, setModuleFilter] = useState('全部模块')
  const [limit, setLimit] = useState('100')
  const [logs, setLogs] = useState<AuditLog[]>(() => loadLogs())
  const [confirmingClear, setConfirmingClear] = useState(false)

  const modules = useMemo(() => ['全部模块', ...Array.from(new Set(logs.map(item => item.module))).filter(Boolean)], [logs])
  const filtered = useMemo(() => {
    const key = keyword.trim().toLowerCase()
    const count = Number(limit)
    return logs
      .filter(item => moduleFilter === '全部模块' || item.module === moduleFilter)
      .filter(item => !key || [item.action, item.module, item.operator, item.target, item.detail].some(value => value.toLowerCase().includes(key)))
      .slice(0, Number.isFinite(count) ? count : 100)
  }, [keyword, limit, logs, moduleFilter])

  const todayCount = logs.filter(item => beijingDateKey(item.createdAt) === beijingDateKey(new Date())).length
  const moduleCount = modules.length - 1

  function refresh() {
    setLogs(loadLogs())
  }

  useEffect(() => {
    const syncLogs = () => refresh()
    window.addEventListener(auditLogEvent, syncLogs)
    window.addEventListener('storage', syncLogs)
    return () => {
      window.removeEventListener(auditLogEvent, syncLogs)
      window.removeEventListener('storage', syncLogs)
    }
  }, [])

  function exportCsv() {
    const header = ['时间', '模块', '动作', '操作人', '对象', '详情']
    const rows = filtered.map(item => [dateText(item.createdAt), item.module, item.action, item.operator, item.target, item.detail])
    const csv = [header, ...rows].map(row => row.map(value => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n')
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `operation-logs-${beijingDateKey(new Date())}.csv`
    anchor.click()
    URL.revokeObjectURL(url)
  }

  function clearAll() {
    clearAuditLogs()
    setLogs([])
    setKeyword('')
    setModuleFilter('全部模块')
    setConfirmingClear(false)
  }

  return <div className="mx-auto max-w-7xl p-4 sm:p-6 lg:p-10">
    <header className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
      <div>
        <p className="text-sm font-semibold text-[var(--accent)]">操作记录</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">操作日志</h1>
        <p className="mt-3 text-muted-foreground">记录面试、候选人、题库与系统配置的关键操作。</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" onClick={refresh}><RefreshCw className="h-4 w-4" />刷新日志</Button>
        <Button variant="secondary" onClick={exportCsv} disabled={!filtered.length}><Download className="h-4 w-4" />导出 CSV</Button>
        <Button variant="danger" onClick={() => setConfirmingClear(true)} disabled={!logs.length}><Trash2 className="h-4 w-4" />清空日志</Button>
      </div>
    </header>

    <section className="mt-7 grid gap-4 md:grid-cols-3">
      <Card>
        <p className="text-sm text-muted-foreground">日志总数</p>
        <strong className="mt-3 block text-3xl">{logs.length}</strong>
        <p className="mt-2 text-xs text-muted-foreground">本地保留最近 200 条关键操作</p>
      </Card>
      <Card>
        <p className="text-sm text-muted-foreground">今日操作</p>
        <strong className="mt-3 block text-3xl">{todayCount}</strong>
        <p className="mt-2 text-xs text-muted-foreground">按当前浏览器本地时间统计</p>
      </Card>
      <Card>
        <p className="text-sm text-muted-foreground">涉及模块</p>
        <strong className="mt-3 block text-3xl">{moduleCount}</strong>
        <p className="mt-2 text-xs text-muted-foreground">可通过模块筛选快速定位</p>
      </Card>
    </section>

    <section className="mt-7 overflow-hidden rounded-[24px] border border-border/90 bg-surface shadow-[0_1px_2px_rgba(20,18,17,.04),0_18px_45px_rgba(20,18,17,.045)]">
      <div className="grid gap-3 border-b border-border p-5 xl:grid-cols-[1fr_220px_170px]">
        <label className="flex h-12 items-center gap-2 rounded-full border border-border bg-surface px-4">
          <Search className="h-4 w-4 text-muted-foreground" />
          <input
            value={keyword}
            onChange={event => setKeyword(event.target.value)}
            className="w-full bg-transparent text-sm outline-none"
            placeholder="搜索模块、动作、对象、详情或操作人"
          />
        </label>
        <div className="flex h-12 items-center gap-2 rounded-full border border-border bg-surface px-4">
          <Filter className="h-4 w-4 text-muted-foreground" />
          <ResponsiveSelect
            ariaLabel="选择模块"
            value={moduleFilter}
            onValueChange={setModuleFilter}
            className="h-12 min-w-0 flex-1 border-0 bg-transparent px-0"
            options={modules.map(item => ({ value: item, label: item }))}
          />
        </div>
        <ResponsiveSelect
          ariaLabel="选择条数"
          value={limit}
          onValueChange={setLimit}
          className="w-full xl:w-auto"
          options={[
            { value: "50", label: "最近 50 条" },
            { value: "100", label: "最近 100 条" },
            { value: "200", label: "最近 200 条" },
          ]}
        />
      </div>

      <div className="divide-y divide-border">
        {filtered.map(item => <article key={item.id} className="flex gap-4 px-5 py-4 transition hover:bg-muted/35">
          <span className="mt-1 grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]">
            <History className="h-4 w-4" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <strong>{item.action}</strong>
              <Badge tone={moduleTone(item.module)}>{item.module}</Badge>
              <span className="text-xs text-muted-foreground">{dateText(item.createdAt)}</span>
            </div>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">{item.detail}</p>
            <p className="mt-1 text-xs text-muted-foreground">操作人：{item.operator} · 对象：{item.target}</p>
          </div>
        </article>)}
        {!filtered.length && <p className="p-12 text-center text-sm text-muted-foreground">{logs.length ? '没有匹配当前筛选条件的日志。' : '暂无操作日志。'}</p>}
      </div>
    </section>

    {confirmingClear && <div className="fixed inset-0 z-50 grid place-items-center bg-black/35 p-4 backdrop-blur-sm">
      <Card className="w-full max-w-md">
        <div className="flex items-start gap-3">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[var(--danger)] text-[var(--danger-foreground)]">
            <Trash2 className="h-5 w-5" />
          </span>
          <div>
            <h2 className="text-xl font-bold">确认清空操作日志？</h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">清空后会删除当前浏览器本地保存的所有操作日志，无法恢复。业务数据不会受到影响。</p>
          </div>
        </div>
        <div className="mt-7 flex justify-end gap-3">
          <Button variant="secondary" onClick={() => setConfirmingClear(false)}>取消</Button>
          <Button variant="danger" onClick={clearAll}><Trash2 className="h-4 w-4" />确认清空</Button>
        </div>
      </Card>
    </div>}
  </div>
}
