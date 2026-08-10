import type { ArrayAlgorithmStep } from '@/lib/algorithm-visualizer/types'

export function ArrayVisualizer({ step }: { step: ArrayAlgorithmStep }) {
  const maximum = Math.max(1, ...step.data.map(value => Math.abs(value)))
  const active = new Set(step.activeIndices ?? [])
  const sorted = new Set(step.sortedIndices ?? [])

  return <div className="rounded-2xl border border-border bg-background/50 p-4 sm:p-6" role="img" aria-label={`数组可视化：${step.description}`}>
    <div className="flex h-64 items-end justify-center gap-2 border-b border-border/80 px-1 pb-2 sm:gap-3">
      {step.data.map((value, index) => {
        const isActive = active.has(index)
        const isSorted = sorted.has(index)
        const height = Math.max(12, Math.round((Math.abs(value) / maximum) * 100))
        return <div key={`${index}-${value}`} className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-2">
          <span className={`text-xs font-bold tabular-nums transition ${isActive ? 'text-[var(--accent)]' : 'text-muted-foreground'}`}>{value}</span>
          <div className={`relative w-full max-w-12 rounded-t-xl transition-all duration-300 ${isActive ? 'bg-[var(--accent)] shadow-[0_0_0_4px_var(--accent-soft)]' : isSorted ? 'bg-[var(--success-foreground)]' : 'bg-[var(--primary)]/75'}`} style={{ height: `${height}%` }}>
            {isActive && <span className="absolute -top-6 left-1/2 -translate-x-1/2 text-[10px] font-bold text-[var(--accent)]">{step.type === 'swap' ? '交换' : '当前'}</span>}
          </div>
          <span className="text-[11px] tabular-nums text-muted-foreground">{index}</span>
        </div>
      })}
    </div>
    <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-xs text-muted-foreground">
      {step.left !== undefined && <span>left = <strong className="text-foreground">{step.left}</strong></span>}
      {step.mid !== undefined && <span>mid = <strong className="text-foreground">{step.mid}</strong></span>}
      {step.right !== undefined && <span>right = <strong className="text-foreground">{step.right}</strong></span>}
      {step.target !== undefined && <span>target = <strong className="text-foreground">{step.target}</strong></span>}
      {step.foundIndex !== undefined && <span className="font-semibold text-[var(--success-foreground)]">找到索引 {step.foundIndex}</span>}
    </div>
  </div>
}
