import { ArrowRight, Binary, GitBranch, ListTree, Play, Search, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { AlgorithmPageHeader } from '@/components/algorithm/algorithm-page'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { visualizerAlgorithms } from '@/lib/algorithm-visualizer/algorithms'

const categoryIcons = {
  '数组与排序': Binary,
  '查找算法': Search,
  '数据结构': ListTree,
  '图算法': GitBranch,
} as const

export function AlgorithmVisualizerPage() {
  const featured = visualizerAlgorithms.filter(algorithm => ['bubble-sort', 'quick-sort', 'binary-search', 'reverse-linked-list', 'bfs', 'dfs'].includes(algorithm.slug))
  const categories = [...new Set(visualizerAlgorithms.map(algorithm => algorithm.category))]

  return <div className="space-y-7">
    <AlgorithmPageHeader
      eyebrow="算法学习实验室"
      title="算法可视化"
      description="让代码、执行行、数据结构变化和操作说明同步发生，按自己的节奏理解每一步。"
      backTo="/algorithm"
      backLabel="返回算法练习"
      actions={<Link to="/algorithm/problems" className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-full border border-border bg-surface px-5 text-sm font-semibold shadow-[0_8px_26px_rgba(20,18,17,.06)] transition hover:-translate-y-0.5 hover:border-[var(--accent)] hover:bg-[var(--accent-soft)] sm:w-auto">进入题库 <ArrowRight className="h-4 w-4" /></Link>}
    />

    <Card className="relative overflow-hidden border-[var(--accent)]/20 bg-[linear-gradient(135deg,var(--primary),#3d3028)] p-6 text-[var(--primary-foreground)] sm:p-8">
      <div className="pointer-events-none absolute -right-20 -top-24 h-64 w-64 rounded-full border border-white/10" />
      <div className="pointer-events-none absolute -bottom-32 right-16 h-64 w-64 rounded-full border border-white/10" />
      <div className="relative grid gap-7 lg:grid-cols-[1fr_auto] lg:items-end">
        <div className="max-w-2xl">
          <div className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1.5 text-xs font-semibold text-white/80"><Sparkles className="h-3.5 w-3.5" />本地执行 · 无需等待接口</div>
          <h2 className="mt-5 text-2xl font-bold tracking-tight sm:text-3xl">把“看懂代码”变成可暂停、可回退的过程。</h2>
          <p className="mt-3 max-w-xl text-sm leading-6 text-white/70">选择一个经典算法，输入自己的数据，逐步观察当前代码行、指针、队列和节点状态。</p>
        </div>
        <Link to="/algorithm/visualizer/bubble-sort" className="inline-flex h-11 items-center justify-center gap-2 rounded-full bg-[var(--accent-soft)] px-5 text-sm font-semibold text-[var(--accent)] transition hover:-translate-y-0.5 hover:bg-white">开始第一个实验 <Play className="h-4 w-4 fill-current" /></Link>
      </div>
    </Card>

    <section aria-labelledby="featured-visualizers">
      <div className="flex items-end justify-between gap-4">
        <div><h2 id="featured-visualizers" className="text-xl font-bold">高频面试算法</h2><p className="mt-1 text-sm text-muted-foreground">先从最常见的六个场景开始，熟悉统一播放器。</p></div>
        <span className="hidden text-xs font-semibold text-muted-foreground sm:inline">{featured.length} 个实验</span>
      </div>
      <div className="mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {featured.map((algorithm, index) => <VisualizerCard key={algorithm.slug} algorithm={algorithm} motionDelay={index * 0.04} />)}
      </div>
    </section>

    <div className="space-y-6">
      {categories.map(category => {
        const Icon = categoryIcons[category as keyof typeof categoryIcons] ?? Binary
        const items = visualizerAlgorithms.filter(algorithm => algorithm.category === category)
        return <section key={category} aria-labelledby={`category-${category}`}>
          <div className="flex items-center gap-3"><span className="grid h-10 w-10 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><Icon className="h-5 w-5" /></span><div><h2 id={`category-${category}`} className="text-lg font-bold">{category}</h2><p className="text-sm text-muted-foreground">{items.length} 个可视化实验</p></div></div>
          <div className="mt-3 flex flex-wrap gap-3">
            {items.map(algorithm => <Link key={algorithm.slug} to={`/algorithm/visualizer/${algorithm.slug}`} className="group flex min-h-12 items-center gap-3 rounded-2xl border border-border bg-surface px-4 py-3 text-sm transition hover:-translate-y-0.5 hover:border-[var(--accent)] hover:bg-[var(--accent-soft)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]">
              <span className="font-semibold group-hover:text-[var(--accent)]">{algorithm.title}</span><span className="text-xs text-muted-foreground">{algorithm.englishTitle}</span><ArrowRight className="h-4 w-4 text-muted-foreground transition group-hover:translate-x-0.5 group-hover:text-[var(--accent)]" />
            </Link>)}
          </div>
        </section>
      })}
    </div>
  </div>
}

function VisualizerCard({ algorithm, motionDelay }: { algorithm: typeof visualizerAlgorithms[number]; motionDelay: number }) {
  return <Card motionDelay={motionDelay} className="group flex min-h-56 flex-col justify-between p-5 transition hover:border-[var(--accent)]/45">
    <div>
      <div className="flex items-start justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">{algorithm.category}</p><h3 className="mt-2 text-lg font-bold">{algorithm.title}</h3><p className="mt-1 text-xs text-muted-foreground">{algorithm.englishTitle}</p></div><Badge tone={algorithm.difficulty === '基础' ? 'info' : 'success'}>{algorithm.difficulty}</Badge></div>
      <p className="mt-4 text-sm leading-6 text-muted-foreground">{algorithm.description}</p>
    </div>
    <div className="mt-5 flex items-center justify-between gap-3"><span className="text-xs text-muted-foreground">时间 {algorithm.timeComplexity}</span><Link to={`/algorithm/visualizer/${algorithm.slug}`} className="inline-flex items-center gap-1 text-sm font-semibold text-[var(--accent)] transition group-hover:gap-2">进入实验 <ArrowRight className="h-4 w-4" /></Link></div>
  </Card>
}
