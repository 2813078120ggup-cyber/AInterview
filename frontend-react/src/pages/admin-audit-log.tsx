import { Download, Filter, History, RefreshCw, Search } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { downloadAuditLogs, listAuditLogs, type AuditLog } from '@/lib/audit-log'

const beijingFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
})

function dateText(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value.replace('T', ' ').slice(0, 19) : beijingFormatter.format(date)
}

function moduleTone(module: string): 'info' | 'success' | 'default' | 'warning' {
  if (module.includes('INTERVIEW') || module.includes('面试')) return 'info'
  if (module.includes('REPORT') || module.includes('RECRUITMENT')) return 'success'
  if (module.includes('PROVIDER') || module.includes('AUTHORIZATION')) return 'warning'
  return 'default'
}

function resultTone(result: AuditLog['result']): 'success' | 'danger' | 'warning' {
  return result === 'SUCCESS' ? 'success' : result === 'DENIED' ? 'warning' : 'danger'
}

export function AdminAuditLog() {
  const [keyword, setKeyword] = useState('')
  const [moduleFilter, setModuleFilter] = useState('')
  const [resultFilter, setResultFilter] = useState('')
  const [pageSize, setPageSize] = useState('50')
  const [pageNo, setPageNo] = useState(1)
  const [logs, setLogs] = useState<AuditLog[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const query = useMemo(() => ({
    pageNo, pageSize: Number(pageSize), keyword: keyword.trim(),
    module: moduleFilter, result: resultFilter,
  }), [keyword, moduleFilter, pageNo, pageSize, resultFilter])

  const modules = useMemo(() => Array.from(new Set(logs.map(item => item.module))).filter(Boolean), [logs])
  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const result = await listAuditLogs(query)
      setLogs(result.records)
      setTotal(result.total)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '操作日志加载失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }, [query])

  useEffect(() => { void load() }, [load])

  function resetPage(update: () => void) {
    setPageNo(1)
    update()
  }

  async function exportCsv() {
    try { await downloadAuditLogs({ ...query, pageNo: undefined, pageSize: undefined }) } catch (reason) {
      setError(reason instanceof Error ? reason.message : '日志导出失败，请稍后重试。')
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / Number(pageSize)))
  return <div className="space-y-6">
    <header className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
      <div>
        <p className="text-sm font-semibold text-[var(--accent)]">服务端审计</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">操作日志</h1>
        <p className="mt-3 text-muted-foreground">追加式记录关键业务操作、权限结果与请求上下文。</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="secondary" className="h-9 px-4" onClick={() => void load()} disabled={loading}><RefreshCw className="h-4 w-4" />刷新日志</Button>
        <Button type="button" variant="secondary" className="h-9 px-4" onClick={() => void exportCsv()} disabled={loading || !total}><Download className="h-4 w-4" />导出 CSV</Button>
      </div>
    </header>

    {error && <Card className="border-[var(--danger)]/30 bg-[var(--danger)]/5 text-sm text-[var(--danger)]">{error}</Card>}

    <section className="grid gap-4 md:grid-cols-3">
      <Card><p className="text-sm text-muted-foreground">符合条件的日志</p><strong className="mt-3 block text-3xl">{total}</strong><p className="mt-2 text-xs text-muted-foreground">服务端总数，不受当前页面条数限制</p></Card>
      <Card><p className="text-sm text-muted-foreground">当前页记录</p><strong className="mt-3 block text-3xl">{logs.length}</strong><p className="mt-2 text-xs text-muted-foreground">第 {pageNo} / {totalPages} 页</p></Card>
      <Card><p className="text-sm text-muted-foreground">当前页涉及模块</p><strong className="mt-3 block text-3xl">{modules.length}</strong><p className="mt-2 text-xs text-muted-foreground">筛选条件由服务端执行</p></Card>
    </section>

    <section className="overflow-hidden rounded-[24px] border border-border/90 bg-surface shadow-[0_1px_2px_rgba(20,18,17,.04),0_18px_45px_rgba(20,18,17,.045)]">
      <div className="grid gap-3 border-b border-border p-5 xl:grid-cols-[1fr_220px_170px_150px]">
        <label className="flex h-12 items-center gap-2 rounded-full border border-border bg-surface px-4">
          <Search className="h-4 w-4 text-muted-foreground" />
          <input value={keyword} onChange={event => resetPage(() => setKeyword(event.target.value))} className="w-full bg-transparent text-sm outline-none" placeholder="搜索请求、动作、资源或摘要" />
        </label>
        <div className="flex h-12 items-center gap-2 rounded-full border border-border bg-surface px-4">
          <Filter className="h-4 w-4 text-muted-foreground" />
          <ResponsiveSelect ariaLabel="选择模块" value={moduleFilter || '全部模块'} onValueChange={value => resetPage(() => setModuleFilter(value === '全部模块' ? '' : value))} className="h-12 min-w-0 flex-1 border-0 bg-transparent px-0" options={[{ value: '全部模块', label: '全部模块' }, ...modules.map(item => ({ value: item, label: item }))]} />
        </div>
        <ResponsiveSelect ariaLabel="选择结果" value={resultFilter || '全部结果'} onValueChange={value => resetPage(() => setResultFilter(value === '全部结果' ? '' : value))} options={[{ value: '全部结果', label: '全部结果' }, { value: 'SUCCESS', label: '成功' }, { value: 'FAILURE', label: '失败' }, { value: 'DENIED', label: '拒绝' }]} />
        <ResponsiveSelect ariaLabel="选择每页条数" value={pageSize} onValueChange={value => resetPage(() => setPageSize(value))} options={[{ value: '20', label: '每页 20 条' }, { value: '50', label: '每页 50 条' }, { value: '100', label: '每页 100 条' }]} />
      </div>

      <div className="divide-y divide-border">
        {loading && <p className="p-12 text-center text-sm text-muted-foreground">正在加载服务端日志…</p>}
        {!loading && logs.map(item => <article key={item.id} className="flex gap-4 px-5 py-4 transition hover:bg-muted/35">
          <span className="mt-1 grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><History className="h-4 w-4" /></span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <strong>{item.action}</strong><Badge tone={moduleTone(item.module)}>{item.module}</Badge><Badge tone={resultTone(item.result)}>{item.result}</Badge><span className="text-xs text-muted-foreground">{dateText(item.createdAt)}</span>
            </div>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">{item.summary}</p>
            <p className="mt-1 text-xs text-muted-foreground">角色：{item.actorRole || 'SYSTEM'} · 资源：{item.resourceType}{item.resourceId ? ` #${item.resourceId}` : ''} · 请求：{item.requestId}</p>
          </div>
        </article>)}
        {!loading && !logs.length && <p className="p-12 text-center text-sm text-muted-foreground">{error ? '日志暂时无法加载。' : '暂无符合条件的服务端审计日志。'}</p>}
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-5 py-4 text-sm text-muted-foreground">
        <span>服务端分页 · 历史记录不可由此页面清空</span>
        <div className="flex items-center gap-2"><Button variant="secondary" className="h-8 px-3" disabled={pageNo <= 1 || loading} onClick={() => setPageNo(value => Math.max(1, value - 1))}>上一页</Button><span>{pageNo} / {totalPages}</span><Button variant="secondary" className="h-8 px-3" disabled={pageNo >= totalPages || loading} onClick={() => setPageNo(value => Math.min(totalPages, value + 1))}>下一页</Button></div>
      </div>
    </section>
  </div>
}
