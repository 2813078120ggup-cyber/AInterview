type AlgorithmCodeProps = {
  lines: string[]
  activeLine: number
}

export function AlgorithmCode({ lines, activeLine }: AlgorithmCodeProps) {
  return <div className="overflow-x-auto rounded-2xl border border-border bg-[#171512] p-3 text-[#eee8de] shadow-inner" aria-label="算法伪代码">
    <div className="min-w-[32rem] font-mono text-[12px] leading-6">
      {lines.map((line, index) => {
        const lineNumber = index + 1
        const active = activeLine === lineNumber
        return <div key={`${lineNumber}-${line}`} className={`flex min-h-6 items-start rounded-lg px-2 transition ${active ? 'bg-[var(--accent)]/25 text-white' : 'text-[#a9a196]'}`}>
          <span className={`w-8 shrink-0 select-none pr-3 text-right ${active ? 'font-bold text-[var(--accent-soft)]' : 'text-[#6e675e]'}`}>{lineNumber}</span>
          <code className="whitespace-pre">{line || ' '}</code>
        </div>
      })}
    </div>
  </div>
}
