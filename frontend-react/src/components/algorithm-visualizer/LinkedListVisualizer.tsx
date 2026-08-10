import { ArrowRight } from 'lucide-react'
import type { LinkedListAlgorithmStep, LinkedListNode } from '@/lib/algorithm-visualizer/types'

function getChain(step: LinkedListAlgorithmStep) {
  const nodeById = new Map(step.nodes.map(node => [node.id, node]))
  const chain: LinkedListNode[] = []
  const seen = new Set<string>()
  let current = step.headId
  while (current && !seen.has(current)) {
    const node = nodeById.get(current)
    if (!node) break
    chain.push(node)
    seen.add(current)
    current = node.nextId
  }
  return chain
}

export function LinkedListVisualizer({ step }: { step: LinkedListAlgorithmStep }) {
  const chain = getChain(step)
  const pointer = (id: string | null) => step.currentId === id ? 'current' : step.previousId === id ? 'previous' : step.nextId === id ? 'next' : ''

  return <div className="rounded-2xl border border-border bg-background/50 p-4 sm:p-6" role="img" aria-label={`链表可视化：${step.description}`}>
    <div className="flex min-h-48 items-center overflow-x-auto pb-2">
      <div className="flex min-w-max items-center gap-2">
        {chain.length === 0 && <span className="text-sm text-muted-foreground">head = null</span>}
        {chain.map((node, index) => {
          const pointerName = pointer(node.id)
          return <div key={node.id} className="flex items-center gap-2">
            <div className="relative pt-7">
              {pointerName && <span className={`absolute left-1/2 top-0 -translate-x-1/2 text-[11px] font-bold ${pointerName === 'current' ? 'text-[var(--accent)]' : 'text-muted-foreground'}`}>{pointerName}</span>}
              <div className={`flex overflow-hidden rounded-xl border-2 transition ${pointerName === 'current' ? 'border-[var(--accent)] shadow-[0_0_0_4px_var(--accent-soft)]' : pointerName === 'previous' ? 'border-[var(--success-foreground)]' : 'border-border'}`}>
                <span className="grid h-12 w-14 place-items-center bg-surface font-bold tabular-nums">{node.value}</span>
                <span className="grid h-12 w-10 place-items-center border-l border-border bg-muted/70 text-xs text-muted-foreground">{node.nextId ? '•' : '∅'}</span>
              </div>
            </div>
            {index < chain.length - 1 && <ArrowRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />}
          </div>
        })}
      </div>
    </div>
    <div className="grid gap-3 border-t border-border pt-4 text-xs sm:grid-cols-3">
      <span className="text-muted-foreground">head <strong className="ml-1 font-mono text-foreground">{step.headId ? chain[0]?.value ?? 'null' : 'null'}</strong></span>
      <span className="text-muted-foreground">previous <strong className="ml-1 font-mono text-foreground">{step.previousId ? step.nodes.find(node => node.id === step.previousId)?.value : 'null'}</strong></span>
      <span className="text-muted-foreground">current <strong className="ml-1 font-mono text-foreground">{step.currentId ? step.nodes.find(node => node.id === step.currentId)?.value : 'null'}</strong></span>
    </div>
  </div>
}
