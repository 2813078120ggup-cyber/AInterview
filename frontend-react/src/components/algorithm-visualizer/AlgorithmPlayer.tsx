import { Pause, Play, RotateCcw, SkipBack, SkipForward } from 'lucide-react'
import { Button } from '@/components/ui/button'

const speedOptions = [0.5, 1, 1.5, 2]

export function AlgorithmPlayer({
  currentStep,
  stepCount,
  playing,
  speed,
  onFirst,
  onPrevious,
  onNext,
  onLast,
  onTogglePlaying,
  onSpeedChange,
}: {
  currentStep: number
  stepCount: number
  playing: boolean
  speed: number
  onFirst: () => void
  onPrevious: () => void
  onNext: () => void
  onLast: () => void
  onTogglePlaying: () => void
  onSpeedChange: (speed: number) => void
}) {
  const isFirst = currentStep <= 0
  const isLast = currentStep >= stepCount - 1

  return <div className="rounded-2xl border border-border bg-background/65 p-3 sm:p-4" aria-label="算法步骤播放器">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center gap-1">
        <Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={onFirst} disabled={isFirst} aria-label="跳到第一步" title="第一步"><SkipBack className="h-4 w-4" /></Button>
        <Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={onPrevious} disabled={isFirst} aria-label="上一步" title="上一步"><span className="text-base">‹</span></Button>
        <Button type="button" className="h-10 min-w-24 rounded-full px-4" onClick={onTogglePlaying} aria-label={playing ? '暂停播放' : '播放算法'}>
          {playing ? <Pause className="h-4 w-4" /> : <Play className="h-4 w-4" />} {playing ? '暂停' : '播放'}
        </Button>
        <Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={onNext} disabled={isLast} aria-label="下一步" title="下一步"><span className="text-base">›</span></Button>
        <Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={onLast} disabled={isLast} aria-label="跳到最后一步" title="最后一步"><SkipForward className="h-4 w-4" /></Button>
      </div>
      <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <span className="mr-1 hidden font-semibold sm:inline">速度</span>
        {speedOptions.map(option => <button
          key={option}
          type="button"
          onClick={() => onSpeedChange(option)}
          aria-pressed={speed === option}
          className={`min-h-9 rounded-full px-2.5 font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] ${speed === option ? 'bg-[var(--accent-soft)] text-[var(--accent)]' : 'hover:bg-muted hover:text-foreground'}`}
        >{option}x</button>)}
      </div>
    </div>
    <div className="mt-3 flex items-center gap-3 text-xs text-muted-foreground">
      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted" aria-hidden="true"><div className="h-full rounded-full bg-[var(--accent)] transition-all" style={{ width: `${stepCount <= 1 ? 100 : (currentStep / (stepCount - 1)) * 100}%` }} /></div>
      <span className="shrink-0 tabular-nums">{currentStep + 1} / {stepCount}</span>
      <button type="button" onClick={onFirst} className="inline-flex h-9 w-9 items-center justify-center rounded-full text-muted-foreground transition hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]" aria-label="重新开始" title="重新开始"><RotateCcw className="h-3.5 w-3.5" /></button>
    </div>
  </div>
}
