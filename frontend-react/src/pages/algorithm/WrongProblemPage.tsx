import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Bookmark, BookOpenCheck, NotebookPen } from 'lucide-react'
import { AlgorithmEmptyState, AlgorithmPageHeader } from '@/components/algorithm/algorithm-page'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { algorithmApi, type AlgorithmWrongProblem } from '@/lib/algorithm-api'
import { algorithmDifficultyMeta, difficultyLabel } from '@/lib/algorithm-status'

export function WrongProblemPage() {
  const [items, setItems] = useState<AlgorithmWrongProblem[]>([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setItems(await algorithmApi.wrongProblems())
    } catch (reason) {
      console.error(reason)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function toggleFavorite(item: AlgorithmWrongProblem) {
    try {
      await algorithmApi.favorite(item.id, !item.favorited)
      setItems(previous => previous.map(value => value.id === item.id ? { ...value, favorited: !value.favorited } : value))
    } catch (reason) {
      console.error(reason)
    }
  }

  return (
    <div className="space-y-6">
      <AlgorithmPageHeader
        title="错题本"
        description="集中复盘提交过但尚未通过的题目；通过后会自动移出，也可收藏重点题目。"
        backTo="/algorithm"
        backLabel="返回练习总览"
      />

      <Card className="p-0">
        {loading ? (
          <p className="p-12 text-center text-sm text-muted-foreground">正在加载错题…</p>
        ) : items.length === 0 ? (
          <AlgorithmEmptyState title="错题本是空的" description="目前没有待复盘题目，继续保持当前状态。" icon={BookOpenCheck} />
        ) : (
          <div className="divide-y divide-border/70">
            {items.map(item => (
              <div key={item.id} className="group flex flex-wrap items-center gap-4 px-5 py-4 transition hover:bg-muted/30">
                <div className="min-w-0 flex-1">
                  <Link to={`/algorithm/problems/${item.id}`} className="font-semibold transition group-hover:text-[var(--accent)]">{item.title}</Link>
                  <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                    <Badge tone={algorithmDifficultyMeta[item.difficulty]?.tone ?? 'default'}>{difficultyLabel(item.difficulty)}</Badge>
                    <span>已提交 {item.mySubmitCount} 次</span>
                    {item.hasNote && <span className="inline-flex items-center gap-1"><NotebookPen className="h-3 w-3" />有笔记</span>}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => void toggleFavorite(item)}
                  className={`inline-flex h-10 items-center gap-1.5 rounded-full border px-4 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] ${item.favorited ? 'border-[var(--accent)] bg-[var(--accent-soft)] text-[var(--accent)]' : 'border-border bg-surface hover:border-[var(--accent)] hover:bg-[var(--accent-soft)]'}`}
                >
                  <Bookmark className={`h-4 w-4 ${item.favorited ? 'fill-current' : ''}`} />
                  {item.favorited ? '已收藏' : '收藏'}
                </button>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  )
}
