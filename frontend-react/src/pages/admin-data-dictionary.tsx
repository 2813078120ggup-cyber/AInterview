import { AlertTriangle, ChevronRight, Columns3, Database, KeyRound, Link2, Loader2, RefreshCw, Search, ShieldCheck, Table2 } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { getAdminDataDictionary, getAdminDataDictionaryDetail, getAdminDataDictionaryOverview, type AdminDataDictionaryDetail, type AdminDataDictionaryField, type AdminDataDictionaryIndex, type AdminDataDictionaryOverview, type AdminDataDictionaryQuery, type AdminDataDictionaryRelation, type AdminDataDictionaryTable } from '@/lib/admin-data-dictionary'
import { useSearchParams } from 'react-router-dom'

const defaultPageSize = 20

const tableTypeLabels: Record<string, string> = { TABLE: '数据表', VIEW: '视图', SYSTEM: '系统表' }
function dateText(value?: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '暂无时间'
}

function numberText(value?: number | null) {
  return Number(value ?? 0).toLocaleString('zh-CN')
}

function tableTypeText(value?: string | null) {
  return value ? tableTypeLabels[value] ?? value : '未标注'
}

function sensitivityTone(value?: number | null): 'default' | 'warning' {
  return value ? 'warning' : 'default'
}

function emptyValue(value?: string | number | boolean | null) {
  return value === null || value === undefined || value === '' ? '—' : String(value)
}

function summaryValue(summary: AdminDataDictionaryOverview | null, key: keyof Pick<AdminDataDictionaryOverview, 'tableCount' | 'columnCount' | 'sensitiveColumnCount' | 'indexCount' | 'foreignKeyCount'>) {
  return summary?.[key] == null ? '—' : numberText(summary[key])
}

export function AdminDataDictionary() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [keywordDraft, setKeywordDraft] = useState(() => searchParams.get('keyword') ?? '')
  const [keyword, setKeyword] = useState(() => searchParams.get('keyword') ?? '')
  const [tableType, setTableType] = useState(() => searchParams.get('tableType') ?? '')
  const [sensitiveOnly, setSensitiveOnly] = useState(() => searchParams.get('sensitiveOnly') ?? '')
  const [hasPrimaryKey, setHasPrimaryKey] = useState(() => searchParams.get('hasPrimaryKey') ?? '')
  const [hasForeignKey, setHasForeignKey] = useState(() => searchParams.get('hasForeignKey') ?? '')
  const [sortBy] = useState(() => searchParams.get('sortBy') ?? 'tableName')
  const [sortOrder] = useState(() => searchParams.get('sortOrder') ?? 'asc')
  const [pageNo, setPageNo] = useState(() => Math.max(1, Number(searchParams.get('pageNo') ?? 1) || 1))
  const [pageSize] = useState(() => Math.min(100, Math.max(1, Number(searchParams.get('pageSize') ?? defaultPageSize) || defaultPageSize)))
  const [records, setRecords] = useState<AdminDataDictionaryTable[]>([])
  const [total, setTotal] = useState(0)
  const [summary, setSummary] = useState<AdminDataDictionaryOverview | null>(null)
  const [generatedAt, setGeneratedAt] = useState<string | null>(null)
  const [stale, setStale] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedName, setSelectedName] = useState('')
  const [detail, setDetail] = useState<AdminDataDictionaryDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')

  const updateSearch = useCallback((updates: Record<string, string | null>) => {
    setSearchParams(previous => {
      const next = new URLSearchParams(previous)
      Object.entries(updates).forEach(([key, value]) => {
        if (value) next.set(key, value)
        else next.delete(key)
      })
      return next
    }, { replace: true })
  }, [setSearchParams])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const nextKeyword = keywordDraft.trim()
      setKeyword(nextKeyword)
      setPageNo(1)
      updateSearch({ keyword: nextKeyword || null, pageNo: null })
    }, 350)
    return () => window.clearTimeout(timer)
  }, [keywordDraft, updateSearch])

  const query = useMemo<AdminDataDictionaryQuery>(() => ({
    pageNo,
    pageSize,
    keyword,
    tableType: tableType || undefined,
    sensitiveOnly: sensitiveOnly ? sensitiveOnly === 'true' : undefined,
    hasPrimaryKey: hasPrimaryKey ? hasPrimaryKey === 'true' : undefined,
    hasForeignKey: hasForeignKey ? hasForeignKey === 'true' : undefined,
    sortBy,
    sortOrder,
  }), [hasForeignKey, hasPrimaryKey, keyword, pageNo, pageSize, sensitiveOnly, sortBy, sortOrder, tableType])

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [page, totals] = await Promise.all([getAdminDataDictionary(query), getAdminDataDictionaryOverview()])
      setRecords(page.records ?? [])
      setTotal(page.total ?? 0)
      setSummary(totals)
      setGeneratedAt(totals.generatedAt ?? null)
      setStale(Date.now() - new Date(totals.generatedAt).getTime() > 5 * 60 * 1000)
      setSelectedName(current => current && (page.records ?? []).some(item => item.tableName === current) ? current : (page.records?.[0]?.tableName ?? ''))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '数据字典加载失败，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }, [query])

  useEffect(() => { void load() }, [load])

  useEffect(() => {
    if (!selectedName) {
      setDetail(null)
      return
    }
    let active = true
    setDetailLoading(true)
    setDetailError('')
    void getAdminDataDictionaryDetail(selectedName).then(value => {
      if (active) setDetail(value)
    }).catch(reason => {
      if (active) setDetailError(reason instanceof Error ? reason.message : '表详情加载失败，请稍后重试。')
    }).finally(() => {
      if (active) setDetailLoading(false)
    })
    return () => { active = false }
  }, [selectedName])

  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const selectedTable = records.find(item => item.tableName === selectedName)

  function applyFilter(key: 'tableType' | 'sensitiveOnly' | 'hasPrimaryKey' | 'hasForeignKey', value: string) {
    setPageNo(1)
    if (key === 'tableType') setTableType(value)
    if (key === 'sensitiveOnly') setSensitiveOnly(value)
    if (key === 'hasPrimaryKey') setHasPrimaryKey(value)
    if (key === 'hasForeignKey') setHasForeignKey(value)
    updateSearch({ [key]: value || null, pageNo: null })
  }

  function resetFilters() {
    setKeywordDraft('')
    setKeyword('')
    setTableType('')
    setSensitiveOnly('')
    setHasPrimaryKey('')
    setHasForeignKey('')
    setPageNo(1)
    setSearchParams({}, { replace: true })
  }

  return <div className="space-y-6" aria-busy={loading || detailLoading}>
    <header className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
      <div>
        <p className="flex items-center gap-2 text-sm font-semibold text-[var(--accent)]"><Database className="h-4 w-4" aria-hidden="true" />运维 · 数据资产</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">数据字典</h1>
        <p className="mt-3 max-w-3xl text-muted-foreground">基于当前数据库元数据展示表、字段、索引、约束与关联关系，帮助运维和审计快速确认结构边界。</p>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <div className="text-right text-xs text-muted-foreground" aria-live="polite"><p>最近同步：{dateText(generatedAt)}</p><p className="mt-1">Flyway：<span>{summary?.latestFlywayVersion || '未读取'}</span></p></div>
        {stale && <Badge tone="warning"><AlertTriangle className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />数据可能已过期</Badge>}
        <Button type="button" variant="secondary" className="h-10" onClick={() => void load()} disabled={loading}><RefreshCw className={loading ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} aria-hidden="true" />刷新字典</Button>
      </div>
    </header>

    {error && <Card className="border-[var(--danger)]/35 bg-[var(--danger-soft)] p-4" role="alert"><div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-[var(--danger)]">{error}</p><Button type="button" variant="secondary" className="h-9" onClick={() => void load()}>重新加载</Button></div></Card>}

    <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="数据字典摘要">
      <SummaryCard icon={Table2} label="数据表与视图" value={summaryValue(summary, 'tableCount')} />
      <SummaryCard icon={Columns3} label="字段总数" value={summaryValue(summary, 'columnCount')} />
      <SummaryCard icon={ShieldCheck} label="敏感字段" value={summaryValue(summary, 'sensitiveColumnCount')} />
      <SummaryCard icon={Link2} label="外键关系" value={summaryValue(summary, 'foreignKeyCount')} />
    </section>

    <Card className="overflow-visible p-0">
      <div className="border-b border-border p-5 sm:p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div><p className="text-xs font-bold uppercase tracking-[.14em] text-[var(--accent)]">Metadata browser</p><h2 className="mt-2 text-xl font-bold">结构检索</h2><p className="mt-1 text-sm text-muted-foreground">搜索表名、中文说明或字段用途，并按约束特征快速筛选。</p></div>
          <Button type="button" variant="ghost" className="h-9 self-start px-3 text-sm lg:self-auto" onClick={resetFilters}>重置筛选</Button>
        </div>
        <div className="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-[minmax(220px,1fr)_170px_170px_170px_170px]">
          <label className="flex h-11 min-w-0 items-center gap-2 rounded-xl border border-border bg-background px-3 focus-within:border-[var(--accent)] focus-within:ring-2 focus-within:ring-[var(--accent-soft)]"><Search className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" /><span className="sr-only">搜索数据表</span><input value={keywordDraft} onChange={event => setKeywordDraft(event.target.value)} placeholder="搜索表名、说明或字段" className="min-w-0 flex-1 bg-transparent text-sm outline-none" /></label>
          <ResponsiveSelect ariaLabel="表类型" value={tableType} onValueChange={value => applyFilter('tableType', value)} options={[{ value: '', label: '全部表类型' }, { value: 'TABLE', label: '数据表' }, { value: 'VIEW', label: '视图' }, { value: 'SYSTEM', label: '系统表' }]} />
          <ResponsiveSelect ariaLabel="敏感性" value={sensitiveOnly} onValueChange={value => applyFilter('sensitiveOnly', value)} options={[{ value: '', label: '全部敏感性' }, { value: 'true', label: '含敏感字段' }, { value: 'false', label: '无敏感字段' }]} />
          <ResponsiveSelect ariaLabel="主键筛选" value={hasPrimaryKey} onValueChange={value => applyFilter('hasPrimaryKey', value)} options={[{ value: '', label: '主键：全部' }, { value: 'true', label: '包含主键' }, { value: 'false', label: '不含主键' }]} />
          <ResponsiveSelect ariaLabel="外键筛选" value={hasForeignKey} onValueChange={value => applyFilter('hasForeignKey', value)} options={[{ value: '', label: '外键：全部' }, { value: 'true', label: '包含外键' }, { value: 'false', label: '不含外键' }]} />
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="mobile-card-table w-full md:min-w-[850px] text-left text-sm">
          <thead className="bg-muted/45 text-xs text-muted-foreground"><tr><th className="px-5 py-4">表 / 说明</th><th className="px-5 py-4">类型</th><th className="px-5 py-4">敏感性</th><th className="px-5 py-4">字段</th><th className="px-5 py-4">索引 / 约束</th><th className="px-5 py-4 text-right">查看</th></tr></thead>
          <tbody className="divide-y divide-border">
            {records.map(item => <tr key={item.tableName} className="align-top transition hover:bg-muted/25">
              <td data-label="表 / 说明" className="px-5 py-4"><button type="button" aria-label={`查看表 ${item.tableName} 的详细结构`} aria-current={item.tableName === selectedName ? 'true' : undefined} onClick={() => setSelectedName(item.tableName)} className="min-w-0 text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] focus-visible:ring-offset-2"><strong className="block break-all font-mono text-sm">{item.tableName}</strong><span className="mt-1 block max-w-[30rem] break-words text-xs leading-5 text-muted-foreground">{item.tableComment || '暂无表说明'}</span></button></td>
              <td data-label="类型" className="px-5 py-4"><Badge>{tableTypeText(item.tableType)}</Badge></td>
              <td data-label="敏感性" className="px-5 py-4"><Badge tone={sensitivityTone(item.sensitiveColumnCount)}>{item.sensitiveColumnCount ? `${item.sensitiveColumnCount} 个敏感字段` : '无敏感字段'}</Badge></td>
              <td data-label="字段" className="px-5 py-4"><strong className="tabular-nums">{numberText(item.columnCount)}</strong><p className="mt-1 text-xs text-muted-foreground">{item.hasPrimaryKey ? `主键：${item.primaryKeyColumns.join(' · ')}` : '无主键'}</p></td>
              <td data-label="索引 / 约束" className="px-5 py-4"><p className="tabular-nums">{numberText(item.indexCount)} 个索引</p><p className="mt-1 text-xs text-muted-foreground">{item.foreignKeyCount ? `${item.foreignKeyCount} 个外键关系` : '无外键关系'}</p></td>
              <td data-label="查看" className="px-5 py-4 text-right"><Button type="button" variant="ghost" className="h-10 px-3" aria-label={`打开 ${item.tableName} 详情`} onClick={() => setSelectedName(item.tableName)}>详情<ChevronRight className="h-4 w-4" aria-hidden="true" /></Button></td>
            </tr>)}
            {!loading && !records.length && <tr><td data-mobile-full colSpan={6} className="p-12 text-center text-sm text-muted-foreground">暂无数据表符合当前筛选条件。可以清空筛选后重新查看。</td></tr>}
            {loading && <tr><td data-mobile-full colSpan={6} className="p-12 text-center text-sm text-muted-foreground"><span className="inline-flex items-center gap-2" role="status"><Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />正在读取数据库元数据…</span></td></tr>}
          </tbody>
        </table>
      </div>
      <div className="flex flex-col gap-3 border-t border-border px-4 py-4 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between sm:px-5"><span>共 {numberText(total)} 张表 · 第 {pageNo}/{totalPages} 页</span><div className="grid grid-cols-2 gap-2 sm:flex"><Button type="button" variant="secondary" className="h-10" disabled={pageNo <= 1 || loading} onClick={() => { const next = pageNo - 1; setPageNo(next); updateSearch({ pageNo: String(next) }) }}>上一页</Button><Button type="button" variant="secondary" className="h-10" disabled={pageNo >= totalPages || loading} onClick={() => { const next = pageNo + 1; setPageNo(next); updateSearch({ pageNo: String(next) }) }}>下一页</Button></div></div>
    </Card>

    <DictionaryDetail detail={detail} selectedTable={selectedTable} stale={stale} loading={detailLoading} error={detailError} onRetry={() => { if (selectedName) { setSelectedName(''); window.setTimeout(() => setSelectedName(selectedName), 0) } }} />
  </div>
}

function SummaryCard({ icon: Icon, label, value }: { icon: typeof Table2; label: string; value: string }) {
  return <Card className="p-5"><div className="flex items-center justify-between gap-3"><p className="flex items-center gap-2 text-sm text-muted-foreground"><Icon className="h-4 w-4 text-[var(--accent)]" aria-hidden="true" />{label}</p><ShieldCheck className="h-4 w-4 text-muted-foreground/60" aria-hidden="true" /></div><strong className="mt-4 block text-3xl tabular-nums tracking-tight">{value}</strong><p className="mt-2 text-xs text-muted-foreground">只读元数据快照</p></Card>
}

function DictionaryDetail({ detail, selectedTable, stale, loading, error, onRetry }: { detail: AdminDataDictionaryDetail | null; selectedTable?: AdminDataDictionaryTable; stale: boolean; loading: boolean; error: string; onRetry: () => void }) {
  if (!selectedTable && !loading) return <Card className="border-dashed p-8 text-center"><Database className="mx-auto h-7 w-7 text-muted-foreground" aria-hidden="true" /><h2 className="mt-3 text-lg font-bold">选择一张表查看详细结构</h2><p className="mt-2 text-sm text-muted-foreground">字段、索引、约束和关系会在这里按数据字典规范展开。</p></Card>
  const constraintViews: ConstraintView[] = detail ? [
    ...detail.primaryKeys.reduce<ConstraintView[]>((items, key) => {
      const current = items.find(item => item.name === key.keyName)
      if (current) current.columns.push(key.columnName)
      else items.push({ name: key.keyName, type: 'PRIMARY KEY', columns: [key.columnName] })
      return items
    }, []),
    ...detail.uniqueConstraints.map(item => ({ name: item.constraintName, type: 'UNIQUE', columns: item.columns })),
  ] : []
  return <Card className="overflow-hidden p-0" aria-live="polite">
    <div className="flex flex-col gap-4 border-b border-border bg-muted/25 p-5 sm:flex-row sm:items-start sm:justify-between sm:p-6"><div className="min-w-0"><p className="text-xs font-bold uppercase tracking-[.14em] text-[var(--accent)]">Table detail</p><h2 className="mt-2 break-all text-2xl font-bold">{detail?.tableName ?? selectedTable?.tableName ?? '正在读取'}</h2><p className="mt-2 break-words text-sm text-muted-foreground">{detail?.tableComment || selectedTable?.tableComment || '数据库结构详情'}</p></div>{stale && <Badge tone="warning"><AlertTriangle className="mr-1.5 h-3.5 w-3.5" aria-hidden="true" />详情可能已过期</Badge>}</div>
    {loading && <div className="flex items-center gap-2 p-8 text-sm text-muted-foreground" role="status"><Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />正在读取字段和约束…</div>}
    {!loading && error && <div className="flex flex-wrap items-center justify-between gap-3 p-6" role="alert"><p className="text-sm text-[var(--danger)]">{error}</p><Button type="button" variant="secondary" className="h-9" onClick={onRetry}>重新加载</Button></div>}
    {!loading && !error && detail && <div className="space-y-6 p-5 sm:p-6"><div className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4"><DetailFact label="数据库" value={detail.catalog || '—'} /><DetailFact label="字段数量" value={numberText(detail.columns.length)} /><DetailFact label="索引数量" value={numberText(detail.indexes.length)} /><DetailFact label="外键关系" value={numberText(detail.foreignKeys.length)} /></div><DetailFields fields={detail.columns} primaryKeys={detail.primaryKeys.map(item => item.columnName)} foreignKeys={detail.foreignKeys.map(item => item.columnName)} /><div className="grid gap-6 xl:grid-cols-2"><DetailIndexes indexes={detail.indexes} /><DetailConstraints constraints={constraintViews} /></div><DetailRelations relations={detail.foreignKeys} /></div>}
  </Card>
}

function DetailFact({ label, value }: { label: string; value: string }) {
  return <div className="rounded-2xl bg-muted/45 p-4"><p className="text-xs text-muted-foreground">{label}</p><p className="mt-2 break-words font-semibold">{value}</p></div>
}

function DetailFields({ fields, primaryKeys, foreignKeys }: { fields: AdminDataDictionaryField[]; primaryKeys: string[]; foreignKeys: string[] }) {
  return <section><SectionHeading icon={Columns3} title="字段定义" count={fields.length} /><div className="mt-4 overflow-x-auto rounded-2xl border border-border"><table className="mobile-card-table w-full md:min-w-[780px] text-left text-sm"><thead className="bg-muted/45 text-xs text-muted-foreground"><tr><th className="px-4 py-3">字段</th><th className="px-4 py-3">类型</th><th className="px-4 py-3">可空</th><th className="px-4 py-3">键</th><th className="px-4 py-3">敏感性</th><th className="px-4 py-3">默认值 / 说明</th></tr></thead><tbody className="divide-y divide-border">{fields.map(field => { const isPrimary = primaryKeys.includes(field.columnName); const isForeign = foreignKeys.includes(field.columnName); return <tr key={field.columnName} className="align-top"><td data-label="字段" className="px-4 py-3"><strong className="break-all font-mono">{field.columnName}</strong></td><td data-label="类型" className="px-4 py-3 font-mono text-xs">{field.nativeType || field.dataType}</td><td data-label="可空" className="px-4 py-3">{field.nullable ? '是' : '否'}</td><td data-label="键" className="px-4 py-3"><Badge tone={isPrimary ? 'warning' : isForeign ? 'info' : 'default'}>{isPrimary && isForeign ? 'PK / FK' : isPrimary ? 'PK' : isForeign ? 'FK' : '—'}</Badge></td><td data-label="敏感性" className="px-4 py-3">{field.sensitive ? <Badge tone="warning">敏感字段</Badge> : '—'}</td><td data-label="默认值 / 说明" className="max-w-[24rem] px-4 py-3"><p className="break-words">{field.defaultValueMasked ? (field.maskingDefault || field.maskedDefaultValue || '敏感默认值已隐藏') : emptyValue(field.defaultValue)}</p><p className="mt-1 break-words text-xs leading-5 text-muted-foreground">{field.comment || '暂无字段说明'}</p></td></tr>})}{!fields.length && <tr><td data-mobile-full colSpan={6} className="p-8 text-center text-sm text-muted-foreground">暂无字段元数据。</td></tr>}</tbody></table></div></section>
}

function DetailIndexes({ indexes }: { indexes: AdminDataDictionaryIndex[] }) {
  return <section><SectionHeading icon={KeyRound} title="索引" count={indexes.length} /><div className="mt-4 divide-y divide-border rounded-2xl border border-border">{indexes.map(index => <div key={index.indexName} className="p-4"><div className="flex flex-wrap items-center gap-2"><strong className="break-all font-mono text-sm">{index.indexName}</strong>{index.primary && <Badge tone="warning">主键</Badge>}{index.unique && <Badge tone="info">唯一</Badge>}</div><p className="mt-2 break-words text-sm">{index.columns.join(' · ') || '未列出索引字段'}</p><p className="mt-1 text-xs text-muted-foreground">{index.primary ? '主键索引' : index.unique ? '唯一索引' : '普通索引'}</p></div>)}{!indexes.length && <p className="p-8 text-center text-sm text-muted-foreground">暂无索引信息。</p>}</div></section>
}

type ConstraintView = { name: string; type: string; columns: string[] }

function DetailConstraints({ constraints }: { constraints: ConstraintView[] }) {
  return <section><SectionHeading icon={ShieldCheck} title="约束" count={constraints.length} /><div className="mt-4 divide-y divide-border rounded-2xl border border-border">{constraints.map(constraint => <div key={constraint.name} className="p-4"><div className="flex flex-wrap items-center gap-2"><strong className="break-all font-mono text-sm">{constraint.name}</strong><Badge>{constraint.type}</Badge></div><p className="mt-2 break-words text-sm">{constraint.columns.join(' · ') || '未列出约束字段'}</p></div>)}{!constraints.length && <p className="p-8 text-center text-sm text-muted-foreground">暂无约束信息。</p>}</div></section>
}

function DetailRelations({ relations }: { relations: AdminDataDictionaryRelation[] }) {
  return <section><SectionHeading icon={Link2} title="关联关系" count={relations.length} /><div className="mt-4 divide-y divide-border rounded-2xl border border-border">{relations.map((relation, index) => <div key={`${relation.constraintName || relation.referencedTable}-${index}`} className="flex flex-col gap-2 p-4 sm:flex-row sm:items-start sm:justify-between"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><Badge tone="info">外键</Badge><strong className="break-all font-mono text-sm">{relation.constraintName || '未命名约束'}</strong></div><p className="mt-2 break-words text-sm">{relation.columnName} → {relation.referencedTable}.{relation.referencedColumn}</p></div><p className="shrink-0 text-xs text-muted-foreground">更新：{relation.updateRule} · 删除：{relation.deleteRule}</p></div>)}{!relations.length && <p className="p-8 text-center text-sm text-muted-foreground">暂无跨表关联关系。</p>}</div></section>
}

function SectionHeading({ icon: Icon, title, count }: { icon: typeof Columns3; title: string; count: number }) {
  return <div className="flex items-center justify-between gap-3"><h3 className="flex items-center gap-2 text-lg font-bold"><Icon className="h-5 w-5 text-[var(--accent)]" aria-hidden="true" />{title}</h3><span className="text-xs text-muted-foreground">{numberText(count)} 项</span></div>
}
