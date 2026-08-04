import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileClock } from 'lucide-react'
import { AlgorithmEmptyState, AlgorithmPageHeader } from '@/components/algorithm/algorithm-page'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { algorithmApi, type AlgorithmSubmissionItem } from '@/lib/algorithm-api'
import { algorithmStatusLabel, algorithmStatusTone } from '@/lib/algorithm-status'

const STATUS_OPTIONS = ['', 'ACCEPTED', 'WRONG_ANSWER', 'COMPILE_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED', 'SYSTEM_ERROR']

export function SubmissionListPage() {
  const nav = useNavigate()
  const [items, setItems] = useState<AlgorithmSubmissionItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('')
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await algorithmApi.submissions({ status: status || undefined, page, pageSize: 20 })
      setItems(result.records)
      setTotal(result.total)
    } catch (reason) {
      console.error(reason)
    } finally {
      setLoading(false)
    }
  }, [status, page])

  useEffect(() => {
    void load()
  }, [load])

  const pageCount = Math.max(1, Math.ceil(total / 20))

  return (
    <div className="space-y-6">
      <AlgorithmPageHeader
        title="提交记录"
        description="查看每次提交的判题状态、运行效率与完整代码。"
        backTo="/algorithm"
        backLabel="返回练习总览"
        actions={<select
          aria-label="按判题状态筛选"
          value={status}
          onChange={event => { setStatus(event.target.value); setPage(1) }}
          className="h-11 w-full rounded-full border border-border bg-surface px-4 text-sm outline-none focus:border-[var(--accent)] sm:w-auto"
        >
          <option value="">全部状态</option>
          {STATUS_OPTIONS.filter(Boolean).map(value => (
            <option key={value} value={value}>{algorithmStatusLabel(value)}</option>
          ))}
        </select>}
      />

      <Card className="p-0">
        {loading ? (
          <p className="p-12 text-center text-sm text-muted-foreground">正在加载提交记录…</p>
        ) : items.length === 0 ? (
          <AlgorithmEmptyState title="还没有提交记录" description="完成第一次题目提交后，判题结果会显示在这里。" icon={FileClock} />
        ) : (
          <div className="overflow-x-auto">
          <table className="mobile-card-table w-full text-left text-sm">
            <thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground">
              <tr>
                <th className="px-5 py-4">编号</th>
                <th className="px-5 py-4">题目</th>
                <th className="px-5 py-4">语言</th>
                <th className="px-5 py-4">判题状态</th>
                <th className="px-5 py-4">通过用例</th>
                <th className="px-5 py-4">运行时间</th>
                <th className="px-5 py-4">提交时间</th>
              </tr>
            </thead>
            <tbody>
              {items.map(item => (
                <tr
                  key={item.id}
                  className="cursor-pointer border-b border-border/70 last:border-0 hover:bg-muted/30"
                  onClick={() => nav(`/algorithm/submissions/${item.id}`)}
                >
                  <td data-label="编号" className="px-5 py-4 font-mono text-xs text-muted-foreground">#{item.id}</td>
                  <td data-label="题目" className="px-5 py-4 font-semibold">{item.problemTitle}</td>
                  <td data-label="语言" className="px-5 py-4">{item.language}</td>
                  <td data-label="判题状态" className="px-5 py-4">
                    <Badge tone={algorithmStatusTone(item.status)}>{algorithmStatusLabel(item.status)}</Badge>
                  </td>
                  <td data-label="通过用例" className="px-5 py-4 tabular-nums">{item.passedCount}/{item.totalCount}</td>
                  <td data-label="运行时间" className="px-5 py-4 tabular-nums">{item.executionTimeMs != null ? `${item.executionTimeMs}ms` : '-'}</td>
                  <td data-label="提交时间" className="px-5 py-4 text-muted-foreground">{new Date(item.createdAt).toLocaleString('zh-CN')}</td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
        <div className="flex items-center justify-between border-t border-border px-5 py-4">
          <span className="text-xs text-muted-foreground">共 {total} 条</span>
          <div className="flex gap-2">
            <Button variant="secondary" disabled={page <= 1} onClick={() => setPage(value => value - 1)}>上一页</Button>
            <Button variant="secondary" disabled={page >= pageCount} onClick={() => setPage(value => value + 1)}>下一页</Button>
          </div>
        </div>
      </Card>
    </div>
  )
}
