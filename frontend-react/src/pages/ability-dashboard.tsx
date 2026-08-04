import { ArrowLeft, BarChart3, ChartNoAxesCombined, TrendingDown, TrendingUp } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { request } from '@/lib/api'

type Trend = { interviewId: string; interviewTitle: string; scheduledAt: string; totalScore: number; professionalScore: number; expressionScore: number; logicScore: number; adaptabilityScore: number }
type Changes = { totalScore: number; professionalScore: number; expressionScore: number; logicScore: number; adaptabilityScore: number }
type Summary = { reportCount: number; latest?: Trend; previous?: Trend; changeFromPrevious: Changes; trends: Trend[] }

const labels: Array<[keyof Changes, string]> = [
  ['professionalScore', '专业能力'],
  ['expressionScore', '表达能力'],
  ['logicScore', '逻辑思维'],
  ['adaptabilityScore', '应变能力'],
]
const radarDimensions: Array<{ key: keyof Pick<Trend, 'professionalScore' | 'expressionScore' | 'logicScore' | 'adaptabilityScore'>; label: string }> = [
  { key: 'professionalScore', label: '专业能力' },
  { key: 'expressionScore', label: '表达能力' },
  { key: 'logicScore', label: '逻辑思维' },
  { key: 'adaptabilityScore', label: '应变能力' },
]
const day = (value: string) => value?.replace('T', ' ').slice(0, 10) || '-'
const shortDay = (value: string) => day(value).slice(5).replace('-', '/')
const change = (value: number) => (value > 0 ? '+' : '') + Number(value || 0).toFixed(1)

function createTrendChart(
  trends: Trend[],
  width: number,
  height: number,
  paddingX: number,
  paddingY: number,
  sequenceOffset = 0,
) {
  const scores = trends.map(item => item.totalScore)
  const lower = Math.max(0, Math.min(...scores, 60) - 8)
  const upper = Math.min(100, Math.max(...scores, 85) + 8)
  const range = Math.max(upper - lower, 1)
  const step = trends.length > 1 ? (width - paddingX * 2) / (trends.length - 1) : 0
  const points = trends.map((item, index) => ({
    ...item,
    sequence: sequenceOffset + index + 1,
    x: trends.length === 1 ? width / 2 : paddingX + index * step,
    y: paddingY + ((upper - item.totalScore) / range) * (height - paddingY * 2),
  }))
  return {
    width, height, paddingX, paddingY, lower, upper, points,
    line: points.map(point => point.x + ',' + point.y).join(' '),
    area: points.length
      ? 'M ' + points[0].x + ' ' + (height - paddingY) + ' L ' + points.map(point => point.x + ' ' + point.y).join(' L ') + ' L ' + points.at(-1)?.x + ' ' + (height - paddingY) + ' Z'
      : '',
  }
}

export function AbilityDashboard() {
  const navigate = useNavigate()
  const [data, setData] = useState<Summary>()
  const [error, setError] = useState('')

  useEffect(() => {
    void request<Summary>('/v1/reports/my/summary')
      .then(setData)
      .catch(reason => setError(reason instanceof Error ? reason.message : '无法获取能力数据'))
  }, [])

  const changes = data?.changeFromPrevious ?? {
    totalScore: 0, professionalScore: 0, expressionScore: 0, logicScore: 0, adaptabilityScore: 0,
  }
  const trendChart = useMemo(() => {
    const trends = data?.trends ?? []
    return createTrendChart(trends, 920, 260, 52, 30)
  }, [data])
  const mobileTrendChart = useMemo(() => {
    const trends = data?.trends ?? []
    const visibleTrends = trends.slice(-6)
    return createTrendChart(visibleTrends, 320, 230, 34, 36, Math.max(0, trends.length - visibleTrends.length))
  }, [data])
  const radarChart = useMemo(() => {
    const center = 180; const radius = 104
    const polar = (value: number, index: number, extra = 0) => {
      const angle = -Math.PI / 2 + index * Math.PI / 2
      const distance = radius * value + extra
      return { x: center + Math.cos(angle) * distance, y: center + Math.sin(angle) * distance }
    }
    const values = radarDimensions.map(item => data?.latest?.[item.key] ?? 0)
    const polygon = (ratio: number) => radarDimensions.map((_, index) => {
      const point = polar(ratio, index)
      return point.x + ',' + point.y
    }).join(' ')
    const points = values.map((value, index) => {
      const point = polar(value / 100, index)
      return point.x + ',' + point.y
    }).join(' ')
    return { center, radius, polar, values, polygon, points }
  }, [data])

  if (!data?.latest) {
    return <div className="mx-auto max-w-xl py-20 text-center">
      <span className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><ChartNoAxesCombined /></span>
      <h1 className="mt-5 text-2xl font-bold">暂无能力数据</h1>
      <p className="mt-3 text-sm text-muted-foreground">{error || '完成面试并生成报告后，系统将在此展示能力趋势。'}</p>
      <Button className="mt-6" onClick={() => navigate('/candidate/interviews')}>开始模拟面试</Button>
    </div>
  }

  return <div className="mx-auto max-w-6xl space-y-6">
    <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
      <div>
        <button onClick={() => navigate('/candidate/interviews')} className="mb-2 flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" />返回面试大厅</button>
        <p className="text-sm font-semibold text-[var(--accent)]">能力分析</p>
        <h1 className="mt-1 text-3xl font-bold">能力趋势</h1>
        <p className="mt-2 text-muted-foreground">基于 {data.reportCount} 场已评测面试生成。</p>
      </div>
      <Button variant="secondary"><BarChart3 className="h-4 w-4" />已评测 {data.reportCount} 场</Button>
    </header>

    <section className="soft-emphasis-panel grid gap-5 overflow-hidden rounded-[28px] p-7 md:grid-cols-[1fr_auto]">
      <div>
        <p className="text-sm text-white/75">当前综合能力值</p>
        <strong className="mt-2 block text-5xl tracking-tight sm:text-6xl">{data.latest.totalScore}</strong>
        <p className="mt-4 text-sm text-white/80/80">最近一次：{data.latest.interviewTitle} · {day(data.latest.scheduledAt)}</p>
      </div>
      <div className="rounded-2xl border border-white/15 bg-white/10 px-7 py-5 text-center">
        <p className="text-sm text-white/75">较上一次</p>
        <strong className={changes.totalScore >= 0 ? 'mt-2 block text-3xl text-white/80' : 'mt-2 block text-3xl text-rose-200'}>{change(changes.totalScore)}</strong>
        <p className="mt-1 text-xs text-white/75">综合得分变化</p>
      </div>
    </section>

    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {labels.map(([key, label]) => {
        const value = changes[key]
        return <Card key={key}>
          <p className="text-sm text-muted-foreground">{label}</p>
          <div className="mt-4 flex items-center justify-between">
            <strong className={value >= 0 ? 'text-2xl text-[var(--accent)]' : 'text-2xl text-rose-600'}>{change(value)}</strong>
            {value >= 0 ? <TrendingUp className="h-5 w-5 text-[var(--accent)]" /> : <TrendingDown className="h-5 w-5 text-rose-500" />}
          </div>
          <p className="mt-3 text-xs text-muted-foreground">相较上一份报告</p>
        </Card>
      })}
    </div>

    <div className="grid min-w-0 gap-6 xl:grid-cols-[minmax(0,1.58fr)_minmax(380px,.82fr)]">
      <Card className="min-w-0 overflow-hidden p-4 sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div><p className="text-sm font-semibold text-[var(--accent)]">历史趋势</p><h2 className="mt-1 text-xl font-bold">综合能力变化</h2></div>
          <span className="rounded-full bg-muted px-3 py-1 text-xs text-muted-foreground sm:bg-transparent sm:px-0 sm:py-0 sm:text-sm">手机端显示最近 {Math.min(data.trends.length, 6)} 场<span className="hidden sm:inline"> · 按面试时间排序</span></span>
        </div>

        <div className="mt-5 sm:hidden">
          <svg viewBox={'0 0 ' + mobileTrendChart.width + ' ' + mobileTrendChart.height} className="block h-auto w-full" role="img" aria-label="最近六场综合能力折线趋势图">
            <defs><linearGradient id="abilityTrendAreaMobile" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor="var(--accent)" stopOpacity=".16" /><stop offset="100%" stopColor="var(--accent)" stopOpacity="0" /></linearGradient></defs>
            {[0, .5, 1].map(ratio => {
              const y = mobileTrendChart.paddingY + ratio * (mobileTrendChart.height - mobileTrendChart.paddingY * 2)
              const score = Math.round(mobileTrendChart.upper - ratio * (mobileTrendChart.upper - mobileTrendChart.lower))
              return <g key={ratio}>
                <line x1={mobileTrendChart.paddingX} x2={mobileTrendChart.width - mobileTrendChart.paddingX} y1={y} y2={y} stroke="currentColor" strokeOpacity=".1" strokeDasharray="4 6" />
                <text x="2" y={y + 4} className="fill-muted-foreground text-[10px]">{score}</text>
              </g>
            })}
            <path d={mobileTrendChart.area} fill="url(#abilityTrendAreaMobile)" />
            <polyline points={mobileTrendChart.line} fill="none" stroke="var(--accent)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
            {mobileTrendChart.points.map(point => <g key={point.interviewId}>
              <circle cx={point.x} cy={point.y} r="5" fill="var(--surface)" stroke="var(--accent)" strokeWidth="2.5" />
              <text x={point.x} y={point.y - 12} textAnchor="middle" className="fill-foreground text-[11px] font-bold">{point.totalScore}</text>
              <text x={point.x} y={mobileTrendChart.height - 16} textAnchor="middle" className="fill-foreground text-[10px] font-semibold">第{point.sequence}次</text>
              <text x={point.x} y={mobileTrendChart.height - 3} textAnchor="middle" className="fill-muted-foreground text-[9px]">{shortDay(point.scheduledAt)}</text>
              <title>第 {point.sequence} 次：{point.totalScore} 分，{day(point.scheduledAt)}</title>
            </g>)}
          </svg>
          {data.trends.length > 6 && <p className="mt-2 text-center text-xs text-muted-foreground">共 {data.trends.length} 场记录，当前展示最近 6 场</p>}
        </div>

        <div className="mt-8 hidden overflow-x-auto sm:block">
          <div className="min-w-[680px]">
            <svg viewBox={'0 0 ' + trendChart.width + ' ' + trendChart.height} className="h-64 w-full overflow-visible" role="img" aria-label="综合能力折线趋势图">
              <defs><linearGradient id="abilityTrendArea" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stopColor="var(--accent)" stopOpacity=".16" /><stop offset="100%" stopColor="var(--accent)" stopOpacity="0" /></linearGradient></defs>
              {[0, .25, .5, .75, 1].map(ratio => {
                const y = trendChart.paddingY + ratio * (trendChart.height - trendChart.paddingY * 2)
                const score = Math.round(trendChart.upper - ratio * (trendChart.upper - trendChart.lower))
                return <g key={ratio}>
                  <line x1={trendChart.paddingX} x2={trendChart.width - trendChart.paddingX} y1={y} y2={y} stroke="currentColor" strokeOpacity=".1" strokeDasharray="4 6" />
                  <text x="4" y={y + 4} className="fill-muted-foreground text-[11px]">{score}</text>
                </g>
              })}
              <path d={trendChart.area} fill="url(#abilityTrendArea)" />
              <polyline points={trendChart.line} fill="none" stroke="var(--accent)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
              {trendChart.points.map(point => <g key={point.interviewId}>
                <circle cx={point.x} cy={point.y} r="6" fill="var(--surface)" stroke="var(--accent)" strokeWidth="3" />
                <text x={point.x} y={point.y - 17} textAnchor="middle" className="fill-foreground text-[13px] font-bold">{point.totalScore}</text>
                <title>第 {point.sequence} 次：{point.totalScore} 分</title>
              </g>)}
            </svg>
            <div className="grid" style={{ gridTemplateColumns: 'repeat(' + data.trends.length + ', minmax(0, 1fr))', marginLeft: (trendChart.paddingX / trendChart.width) * 100 + '%', marginRight: (trendChart.paddingX / trendChart.width) * 100 + '%' }}>
              {data.trends.map((item, index) => <div key={item.interviewId} className="text-center"><strong className="text-xs">第 {index + 1} 次</strong><p className="mt-1 text-[10px] text-muted-foreground">{day(item.scheduledAt)}</p></div>)}
            </div>
          </div>
        </div>
      </Card>

      <Card className="min-w-0 self-start overflow-hidden p-4 sm:p-5">
        <div>
          <p className="text-sm font-semibold text-[var(--accent)]">能力画像</p>
          <h2 className="mt-1 text-xl font-bold">四维能力画像</h2>
          <p className="mt-1 text-sm text-muted-foreground">最近一次面试的能力分布</p>
        </div>
        <div className="mx-auto mt-5 w-full max-w-[350px] overflow-hidden rounded-[24px] border border-[var(--border)]/80 bg-[linear-gradient(180deg,var(--accent-soft),transparent)] px-1 py-3 sm:px-4 sm:py-5 dark:border-[var(--border)]/10 dark:bg-[var(--surface-soft)]">
          <svg viewBox="0 0 360 330" className="mx-auto block h-auto w-full max-w-[320px]" role="img" aria-label="专业能力、表达能力、逻辑思维和应变能力的雷达图">
            {[.25, .5, .75, 1].map(ratio => <polygon key={ratio} points={radarChart.polygon(ratio)} fill="none" stroke="currentColor" strokeOpacity=".12" strokeWidth="1" />)}
            {radarDimensions.map((item, index) => {
              const outer = radarChart.polar(1, index)
              const label = radarChart.polar(1, index, 28)
              return <g key={item.key}>
                <line x1={radarChart.center} y1={radarChart.center} x2={outer.x} y2={outer.y} stroke="currentColor" strokeOpacity=".14" />
                <text x={index === 1 ? Math.min(label.x, 294) : index === 3 ? Math.max(label.x, 66) : label.x} y={label.y + 4} textAnchor={index === 1 ? 'start' : index === 3 ? 'end' : 'middle'} className="fill-muted-foreground text-[12px] font-medium">{item.label}</text>
              </g>
            })}
            <polygon points={radarChart.points} fill="var(--accent)" fillOpacity=".18" stroke="var(--accent)" strokeWidth="2.5" strokeLinejoin="round" />
            {radarChart.values.map((value, index) => {
              const point = radarChart.polar(value / 100, index)
              return <circle key={radarDimensions[index].key} cx={point.x} cy={point.y} r="5" fill="var(--surface)" stroke="var(--brand)" strokeWidth="2.5"><title>{radarDimensions[index].label}：{value}</title></circle>
            })}
          </svg>
        </div>
        <div className="mt-4 grid grid-cols-1 gap-2 border-t border-border pt-4 min-[360px]:grid-cols-2 min-[360px]:gap-x-4 min-[360px]:gap-y-3">
          {radarDimensions.map((item, index) => <div key={item.key} className="flex min-w-0 items-center justify-between gap-3 text-sm"><span className="truncate text-muted-foreground">{item.label}</span><strong>{radarChart.values[index]}</strong></div>)}
        </div>
      </Card>
    </div>
  </div>
}
