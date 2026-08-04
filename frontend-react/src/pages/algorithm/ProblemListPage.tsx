import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BookOpenCheck, RotateCcw, Search, SlidersHorizontal } from 'lucide-react'
import { AlgorithmEmptyState, AlgorithmPageHeader } from '@/components/algorithm/algorithm-page'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { algorithmApi, type AlgorithmProblemListItem, type AlgorithmTag } from '@/lib/algorithm-api'
import { algorithmDifficultyMeta, difficultyLabel, progressStatusMeta } from '@/lib/algorithm-status'

export function ProblemListPage() {
  const nav = useNavigate()
  const [tags, setTags] = useState<AlgorithmTag[]>([])
  const [items, setItems] = useState<AlgorithmProblemListItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [keyword, setKeyword] = useState('')
  const [difficulty, setDifficulty] = useState('')
  const [tagId, setTagId] = useState('')
  const [progressStatus, setProgressStatus] = useState('')
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const result = await algorithmApi.problems({
        keyword: keyword || undefined,
        difficulty: difficulty || undefined,
        tagId: tagId ? Number(tagId) : undefined,
        progressStatus: progressStatus || undefined,
        page,
        pageSize: 20,
      })
      setItems(result.records)
      setTotal(result.total)
    } catch (reason) {
      console.error(reason)
    } finally {
      setLoading(false)
    }
  }, [keyword, difficulty, tagId, progressStatus, page])

  useEffect(() => {
    void algorithmApi.tags().then(setTags).catch(() => undefined)
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const pageCount = Math.max(1, Math.ceil(total / 20))

  return (
    <div className="space-y-6">
      <AlgorithmPageHeader
        title="算法题库"
        description="按难度、标签和完成状态筛选题目，选择适合当前阶段的练习。"
        backTo="/algorithm"
        backLabel="返回练习总览"
      />

      <Card className="overflow-hidden p-0">
        <div className="flex items-center gap-2 border-b border-border px-5 py-4">
          <SlidersHorizontal className="h-4 w-4 text-[var(--accent)]" />
          <div><h2 className="text-sm font-bold">筛选题目</h2><p className="mt-0.5 text-xs text-muted-foreground">共 {total} 道题目</p></div>
        </div>
        <div className="grid gap-3 p-4 sm:p-5 md:grid-cols-2 xl:grid-cols-[minmax(260px,1fr)_150px_180px_180px_auto]">
          <label className="flex h-11 min-w-0 items-center gap-2 rounded-full border border-border bg-background px-4 focus-within:border-[var(--accent)]">
            <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
            <input
              value={keyword}
              onChange={event => { setKeyword(event.target.value); setPage(1) }}
              onKeyDown={event => event.key === 'Enter' && void load()}
              className="w-full min-w-0 bg-transparent text-sm outline-none"
              placeholder="搜索题目名称"
            />
          </label>
          <select
            value={difficulty}
            onChange={event => { setDifficulty(event.target.value); setPage(1) }}
            className="h-11 rounded-full border border-border bg-background px-4 text-sm outline-none focus:border-[var(--accent)]"
          >
            <option value="">全部难度</option>
            {Object.entries(algorithmDifficultyMeta).map(([value, meta]) => (
              <option key={value} value={value}>{meta.label}</option>
            ))}
          </select>
          <select
            value={tagId}
            onChange={event => { setTagId(event.target.value); setPage(1) }}
            className="h-11 rounded-full border border-border bg-background px-4 text-sm outline-none focus:border-[var(--accent)]"
          >
            <option value="">全部标签</option>
            {tags.map(tag => <option key={tag.id} value={tag.id}>{tag.name}</option>)}
          </select>
          <select
            value={progressStatus}
            onChange={event => { setProgressStatus(event.target.value); setPage(1) }}
            className="h-11 rounded-full border border-border bg-background px-4 text-sm outline-none focus:border-[var(--accent)]"
          >
            <option value="">全部状态</option>
            {Object.entries(progressStatusMeta).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
          <Button
            variant="ghost"
            className="px-4"
            onClick={() => { setKeyword(''); setDifficulty(''); setTagId(''); setProgressStatus(''); setPage(1) }}
          >
            <RotateCcw className="h-4 w-4" />重置
          </Button>
        </div>
      </Card>

      <Card className="p-0">
        {loading ? (
          <div className="divide-y divide-border/70">
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className="flex items-center gap-4 px-5 py-4">
                <div className="h-6 w-16 animate-pulse rounded-full bg-muted" />
                <div className="h-4 w-40 animate-pulse rounded bg-muted" />
                <div className="h-6 w-14 animate-pulse rounded-full bg-muted" />
                <div className="h-4 w-24 animate-pulse rounded bg-muted" />
                <div className="ml-auto h-4 w-16 animate-pulse rounded bg-muted" />
              </div>
            ))}
          </div>
        ) : items.length === 0 ? (
          <AlgorithmEmptyState title="没有符合条件的题目" description="尝试减少筛选条件或更换搜索关键词。" icon={BookOpenCheck} />
        ) : (
          <div className="overflow-x-auto">
          <table className="mobile-card-table w-full table-fixed text-left text-sm">
            <colgroup>
              <col className="w-24" />
              <col />
              <col className="w-24" />
              <col className="w-48" />
              <col className="w-24" />
              <col className="w-28" />
            </colgroup>
            <thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground">
              <tr>
                <th className="px-5 py-4">状态</th>
                <th className="px-5 py-4">标题</th>
                <th className="px-5 py-4">难度</th>
                <th className="px-5 py-4">标签</th>
                <th className="px-5 py-4">通过率</th>
                <th className="px-5 py-4">我的提交</th>
              </tr>
            </thead>
            <tbody>
              {items.map(item => (
                <tr
                  key={item.id}
                  className="group cursor-pointer border-b border-border/70 transition last:border-0 hover:bg-muted/30"
                  onClick={() => nav(`/algorithm/problems/${item.id}`)}
                >
                  <td data-label="状态" className="px-5 py-4">
                    {item.progressStatus === 'ACCEPTED' ? (
                      <Badge tone="success">已通过</Badge>
                    ) : item.progressStatus === 'ATTEMPTED' ? (
                      <Badge tone="warning">尝试过</Badge>
                    ) : (
                      <Badge>未开始</Badge>
                    )}
                  </td>
                  <td data-label="标题" className="truncate px-5 py-4 font-semibold text-foreground transition group-hover:text-[var(--accent)]">{item.title}</td>
                  <td data-label="难度" className="px-5 py-4">
                    <Badge tone={algorithmDifficultyMeta[item.difficulty]?.tone ?? 'default'}>
                      {difficultyLabel(item.difficulty)}
                    </Badge>
                  </td>
                  <td data-label="标签" className="px-5 py-4">
                    <div className="flex flex-wrap gap-1">
                      {item.tags.length === 0 ? <span className="text-muted-foreground">-</span> : item.tags.map(tag => (
                        <span key={tag.id} className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">{tag.name}</span>
                      ))}
                    </div>
                  </td>
                  <td data-label="通过率" className="px-5 py-4 tabular-nums text-muted-foreground">{item.acceptanceRate.toFixed(1)}%</td>
                  <td data-label="我的提交" className="px-5 py-4 tabular-nums text-muted-foreground">{item.mySubmitCount} 次</td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
        <div className="flex items-center justify-between border-t border-border px-5 py-4">
          <span className="text-xs text-muted-foreground">共 {total} 题</span>
          <div className="flex gap-2">
            <Button variant="secondary" disabled={page <= 1} onClick={() => setPage(value => value - 1)}>上一页</Button>
            <Button variant="secondary" disabled={page >= pageCount} onClick={() => setPage(value => value + 1)}>下一页</Button>
          </div>
        </div>
      </Card>
    </div>
  )
}
