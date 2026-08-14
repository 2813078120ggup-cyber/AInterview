import { useEffect, useMemo, useState } from 'react'
import { ArrowLeft, RotateCcw, Settings2 } from 'lucide-react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { AlgorithmPageHeader } from '@/components/algorithm/algorithm-page'
import { AlgorithmCode } from '@/components/algorithm-visualizer/AlgorithmCode'
import { AlgorithmPlayer } from '@/components/algorithm-visualizer/AlgorithmPlayer'
import { useAlgorithmPlayer } from '@/components/algorithm-visualizer/useAlgorithmPlayer'
import { ArrayVisualizer } from '@/components/algorithm-visualizer/ArrayVisualizer'
import { GraphVisualizer } from '@/components/algorithm-visualizer/GraphVisualizer'
import { LinkedListVisualizer } from '@/components/algorithm-visualizer/LinkedListVisualizer'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { formatNumberInput, getVisualizerAlgorithm, parseNumberInput, visualizerAlgorithms } from '@/lib/algorithm-visualizer/algorithms'

export function AlgorithmVisualizerDetailPage() {
  const { algorithmSlug } = useParams()
  const algorithm = getVisualizerAlgorithm(algorithmSlug)
  const selectedAlgorithm = algorithm ?? visualizerAlgorithms[0]
  const [values, setValues] = useState<number[]>(() => selectedAlgorithm.defaultInput)
  const [draftInput, setDraftInput] = useState(() => formatNumberInput(selectedAlgorithm.defaultInput))
  const [target, setTarget] = useState(selectedAlgorithm.defaultTarget?.toString() ?? '')
  const [draftTarget, setDraftTarget] = useState(selectedAlgorithm.defaultTarget?.toString() ?? '')
  const [runVersion, setRunVersion] = useState(0)

  useEffect(() => {
    if (!algorithm) return
    setValues(algorithm.defaultInput)
    setDraftInput(formatNumberInput(algorithm.defaultInput))
    setTarget(algorithm.defaultTarget?.toString() ?? '')
    setDraftTarget(algorithm.defaultTarget?.toString() ?? '')
  }, [algorithm, algorithmSlug])

  const steps = useMemo(() => selectedAlgorithm.buildSteps(values, target ? Number(target) : undefined), [selectedAlgorithm, target, values])
  const player = useAlgorithmPlayer(steps.length, `${selectedAlgorithm.slug}:${runVersion}`)
  const currentStep = steps[player.currentStep]

  if (!algorithm) return <Navigate to="/algorithm/visualizer" replace />
  if (!currentStep) return null

  function applyInput() {
    setValues(parseNumberInput(draftInput, selectedAlgorithm.defaultInput))
    if (selectedAlgorithm.defaultTarget !== undefined) {
      const nextTarget = Number(draftTarget)
      setTarget(Number.isFinite(nextTarget) ? String(nextTarget) : String(selectedAlgorithm.defaultTarget))
    }
    setRunVersion(version => version + 1)
  }

  function resetInput() {
    setValues(selectedAlgorithm.defaultInput)
    setDraftInput(formatNumberInput(selectedAlgorithm.defaultInput))
    const defaultTarget = selectedAlgorithm.defaultTarget?.toString() ?? ''
    setTarget(defaultTarget)
    setDraftTarget(defaultTarget)
    setRunVersion(version => version + 1)
  }

  return <div className="space-y-6">
    <AlgorithmPageHeader
      eyebrow={`${selectedAlgorithm.category} · Algorithm Visualizer`}
      title={selectedAlgorithm.title}
      description={selectedAlgorithm.description}
      backTo="/algorithm/visualizer"
      backLabel="返回可视化"
      actions={<Link to="/algorithm" className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-full border border-border bg-surface px-5 text-sm font-semibold shadow-[0_8px_26px_rgba(20,18,17,.06)] transition hover:-translate-y-0.5 hover:border-[var(--accent)] hover:bg-[var(--accent-soft)] sm:w-auto"><ArrowLeft className="h-4 w-4" />算法练习</Link>}
      compact
    />

    <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-muted-foreground">
      <span>难度 <strong className="ml-1 text-foreground">{selectedAlgorithm.difficulty}</strong></span>
      <span>时间复杂度 <strong className="ml-1 font-mono text-foreground">{selectedAlgorithm.timeComplexity}</strong></span>
      <span>空间复杂度 <strong className="ml-1 font-mono text-foreground">{selectedAlgorithm.spaceComplexity}</strong></span>
      <span>本地步骤 <strong className="ml-1 text-foreground">{steps.length}</strong></span>
    </div>

    <Card className="overflow-hidden p-0">
      <div className="grid xl:grid-cols-[minmax(19rem,.82fr)_minmax(0,1.18fr)]">
        <section className="border-b border-border p-4 sm:p-6 xl:border-b-0 xl:border-r" aria-labelledby="algorithm-code-title">
          <div className="flex items-center justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">Code</p><h2 id="algorithm-code-title" className="mt-1 text-lg font-bold">执行代码</h2></div><span className="rounded-full bg-[var(--accent-soft)] px-3 py-1 text-xs font-semibold text-[var(--accent)]">第 {currentStep.line} 行</span></div>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">当前行会随步骤移动，先看代码，再看右侧数据结构如何回应。</p>
          <div className="mt-4"><AlgorithmCode lines={selectedAlgorithm.code} activeLine={currentStep.line} /></div>
          <div className="mt-4 grid grid-cols-2 gap-3">
            <div className="rounded-2xl bg-muted/55 p-3"><p className="text-xs text-muted-foreground">时间复杂度</p><p className="mt-1 font-mono font-bold">{selectedAlgorithm.timeComplexity}</p></div>
            <div className="rounded-2xl bg-muted/55 p-3"><p className="text-xs text-muted-foreground">空间复杂度</p><p className="mt-1 font-mono font-bold">{selectedAlgorithm.spaceComplexity}</p></div>
          </div>
        </section>

        <section className="min-w-0 p-4 sm:p-6" aria-labelledby="algorithm-visual-title">
          <div className="flex items-center justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[0.12em] text-[var(--accent)]">Live state</p><h2 id="algorithm-visual-title" className="mt-1 text-lg font-bold">数据结构变化</h2></div><span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground"><span className="h-2 w-2 rounded-full bg-[var(--accent)]" />当前操作</span></div>
          <div className="mt-4">
            {currentStep.kind === 'array' && <ArrayVisualizer step={currentStep} />}
            {currentStep.kind === 'linked-list' && <LinkedListVisualizer step={currentStep} />}
            {currentStep.kind === 'graph' && <GraphVisualizer step={currentStep} />}
          </div>
          <div className="mt-4 rounded-2xl border border-[var(--accent)]/20 bg-[var(--accent-soft)] p-4"><p className="text-xs font-semibold text-[var(--accent)]">当前操作</p><p className="mt-1 text-sm font-semibold leading-6">{currentStep.description}</p></div>
          <div className="mt-4"><AlgorithmPlayer currentStep={player.currentStep} stepCount={steps.length} playing={player.playing} speed={player.speed} onFirst={player.first} onPrevious={player.previous} onNext={player.next} onLast={player.last} onTogglePlaying={player.togglePlaying} onSpeedChange={player.setSpeed} /></div>
        </section>
      </div>
    </Card>

    <Card className="p-4 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-4"><div><div className="flex items-center gap-2"><Settings2 className="h-4 w-4 text-[var(--accent)]" /><h2 className="font-bold">实验输入</h2></div><p className="mt-1 text-sm text-muted-foreground">修改数据后点击应用，步骤会在浏览器本地重新生成。</p></div><Button type="button" variant="ghost" onClick={resetInput}><RotateCcw className="h-4 w-4" />恢复示例</Button></div>
      {selectedAlgorithm.kind !== 'graph' && <div className="mt-4 grid gap-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
        <label className="block text-sm font-semibold">{selectedAlgorithm.kind === 'linked-list' ? '链表节点值' : '数组'}
          <input value={draftInput} onChange={event => setDraftInput(event.target.value)} className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 font-mono text-sm outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20" aria-label={selectedAlgorithm.kind === 'linked-list' ? '链表节点值' : '数组输入'} />
          <span className="mt-1 block text-xs font-normal text-muted-foreground">用逗号或空格分隔，最多 12 个数字。</span>
        </label>
        {selectedAlgorithm.defaultTarget !== undefined && <label className="block text-sm font-semibold sm:w-36">目标值<input value={draftTarget} onChange={event => setDraftTarget(event.target.value)} type="number" className="mt-2 h-11 w-full rounded-xl border border-border bg-background px-3 font-mono text-sm outline-none transition focus:border-[var(--accent)] focus:ring-2 focus:ring-[var(--accent)]/20" /></label>}
        <Button type="button" className="sm:min-w-28" onClick={applyInput}>应用输入</Button>
      </div>}
      {selectedAlgorithm.kind === 'graph' && <p className="mt-4 rounded-xl bg-muted/55 p-3 text-sm text-muted-foreground">当前使用示例图 A → B、C；B → D、E；C → F。你可以通过播放器观察队列或递归栈的变化。</p>}
    </Card>
  </div>
}
