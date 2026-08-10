import type { GraphAlgorithmStep } from '@/lib/algorithm-visualizer/types'

export function GraphVisualizer({ step }: { step: GraphAlgorithmStep }) {
  const visited = new Set(step.visited)
  const nodeById = new Map(step.nodes.map(node => [node.id, node]))

  return <div className="rounded-2xl border border-border bg-background/50 p-3 sm:p-5" role="img" aria-label={`图遍历可视化：${step.description}`}>
    <svg viewBox="0 0 100 100" className="mx-auto block h-auto w-full max-w-xl" aria-hidden="true">
      {step.edges.map(edge => {
        const from = nodeById.get(edge.from)
        const to = nodeById.get(edge.to)
        if (!from || !to) return null
        const highlighted = visited.has(edge.from) && visited.has(edge.to)
        return <line key={`${edge.from}-${edge.to}`} x1={from.x} y1={from.y} x2={to.x} y2={to.y} stroke={highlighted ? 'var(--accent)' : 'var(--border)'} strokeWidth={highlighted ? 1.6 : 1} />
      })}
      {step.nodes.map(node => {
        const isActive = step.activeNode === node.id
        const isVisited = visited.has(node.id)
        const inFrontier = step.frontier.includes(node.id)
        return <g key={node.id}>
          <circle cx={node.x} cy={node.y} r={isActive ? 8 : 6.5} fill={isActive ? 'var(--accent)' : isVisited ? 'var(--success)' : inFrontier ? 'var(--accent-soft)' : 'var(--surface)'} stroke={isActive ? 'var(--accent)' : isVisited ? 'var(--success-foreground)' : 'var(--border)'} strokeWidth={isActive ? 2 : 1.2} />
          <text x={node.x} y={node.y + 1.4} textAnchor="middle" fontSize="5" fontWeight="700" fill={isActive ? 'var(--primary-foreground)' : 'var(--foreground)'}>{node.label}</text>
        </g>
      })}
    </svg>
    <div className="mt-4 grid gap-3 sm:grid-cols-3">
      <div className="rounded-xl bg-muted/55 p-3"><p className="text-[11px] font-semibold text-muted-foreground">已访问</p><p className="mt-1 font-mono text-sm font-bold">{step.visited.join(' → ') || '—'}</p></div>
      <div className="rounded-xl bg-muted/55 p-3"><p className="text-[11px] font-semibold text-muted-foreground">{step.type === 'visit' && step.frontier.length === 0 ? '递归栈' : '待处理队列'}</p><p className="mt-1 font-mono text-sm font-bold">{step.frontier.join(' → ') || '空'}</p></div>
      <div className="rounded-xl bg-muted/55 p-3"><p className="text-[11px] font-semibold text-muted-foreground">访问顺序</p><p className="mt-1 font-mono text-sm font-bold">{step.traversal.join(' → ') || '等待开始'}</p></div>
    </div>
  </div>
}
