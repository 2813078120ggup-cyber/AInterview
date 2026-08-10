import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, CalendarCheck2, CheckCircle2, Flame, ListChecks, PlaySquare, Send, Target, TrendingUp } from 'lucide-react'
import { AlgorithmEmptyState, AlgorithmPageHeader, AlgorithmSectionHeader } from '@/components/algorithm/algorithm-page'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { algorithmApi, type AlgorithmDashboard } from '@/lib/algorithm-api'
import { algorithmDifficultyMeta, algorithmStatusLabel, algorithmStatusTone, difficultyLabel } from '@/lib/algorithm-status'

export function AlgorithmHomePage() {
  const [data, setData] = useState<AlgorithmDashboard>()
  const [error, setError] = useState('')

  useEffect(() => {
    void algorithmApi.dashboard()
      .then(setData)
      .catch(reason => setError(reason instanceof Error ? reason.message : '加载失败，请稍后重试'))
  }, [])

  const stats = data
    ? [
        { label: '今日完成', value: data.todayAcceptedCount, hint: '今日通过题目', icon: CalendarCheck2, tone: 'bg-[#f3eadf] text-[#7d4929]' },
        { label: '累计通过', value: data.acceptedProblemCount, hint: '已掌握题目', icon: CheckCircle2, tone: 'bg-[#eef2e6] text-[#59613b]' },
        { label: '连续练习', value: `${data.continuousPracticeDays} 天`, hint: '保持练习节奏', icon: Flame, tone: 'bg-[#fff3d8] text-[#8a5d16]' },
        { label: '总提交', value: data.submissionCount, hint: '全部提交记录', icon: Send, tone: 'bg-[#eaf2f7] text-[#48677d]' },
        { label: '通过率', value: `${data.acceptanceRate.toFixed(1)}%`, hint: '累计提交通过率', icon: Target, tone: 'bg-[#f4eaf2] text-[#80536f]' },
      ]
    : []

  return (
    <div className="space-y-6">
      <AlgorithmPageHeader
        title="算法练习"
        description="查看练习进度、推荐题目与最近提交，持续巩固编码能力。"
        actions={<div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row"><Link to="/algorithm/visualizer" className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-full border border-border bg-surface px-5 text-sm font-semibold shadow-[0_8px_26px_rgba(20,18,17,.06)] transition hover:-translate-y-0.5 hover:border-[var(--accent)] hover:bg-[var(--accent-soft)] sm:w-auto">
          <PlaySquare className="h-4 w-4" />算法可视化
        </Link><Link to="/algorithm/problems" className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-full bg-[var(--primary)] px-5 text-sm font-semibold text-[var(--primary-foreground)] shadow-[0_14px_34px_rgba(20,18,17,.18)] transition hover:-translate-y-0.5 sm:w-auto">
          进入题库 <ArrowRight className="h-4 w-4" />
        </Link></div>}
      />

      {error && <p className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{error}</p>}

      {!data && !error && <p className="p-12 text-center text-sm text-muted-foreground">正在加载统计…</p>}

      {data && <>
        <section aria-label="练习数据概览" className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-5">
          {stats.map(stat => (
            <Card key={stat.label} className="min-h-36 p-4 sm:p-5">
              <div className="flex items-start justify-between gap-3">
                <span className={`grid h-10 w-10 place-items-center rounded-2xl ${stat.tone}`}><stat.icon className="h-4.5 w-4.5" /></span>
                <span className="text-xs font-semibold text-muted-foreground">{stat.label}</span>
              </div>
              <p className="mt-5 text-2xl font-bold tracking-tight">{stat.value}</p>
              <p className="mt-1 text-xs text-muted-foreground">{stat.hint}</p>
            </Card>
          ))}
        </section>

        <Card className="p-5">
          <AlgorithmSectionHeader title="难度完成进度" description="按题目难度查看当前掌握情况" action={<TrendingUp className="h-5 w-5 text-[var(--accent)]" />} />
          <div className="mt-6 grid gap-5 lg:grid-cols-3">
            {Object.entries(data.difficultyProgress).map(([difficulty, progress]) => {
              const percent = progress.total === 0 ? 0 : Math.round(progress.accepted * 100 / progress.total)
              return (
                <div key={difficulty} className="rounded-2xl border border-border bg-background/55 p-4">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-semibold">{difficultyLabel(difficulty)}</span>
                    <span className="font-semibold tabular-nums">{percent}%</span>
                  </div>
                  <div className="mt-2 h-2.5 overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-[var(--accent)] transition-all"
                      style={{ width: `${percent}%` }}
                    />
                  </div>
                  <p className="mt-2 text-xs text-muted-foreground">已完成 {progress.accepted} / {progress.total} 题</p>
                </div>
              )
            })}
          </div>
        </Card>

        <div className="grid gap-5 lg:grid-cols-2">
          <Card className="p-5">
            <AlgorithmSectionHeader title="推荐练习" description="从尚未掌握的题目开始" action={<ListChecks className="h-5 w-5 text-[var(--accent)]" />} />
            <div className="mt-4 divide-y divide-border/70">
              {data.recommended.length === 0 && <AlgorithmEmptyState className="min-h-44" title="推荐题目已完成" description="可以回顾错题或挑战更高难度。" icon={CheckCircle2} />}
              {data.recommended.map(item => (
                <Link key={item.id} to={`/algorithm/problems/${item.id}`} className="group flex items-center justify-between gap-3 rounded-xl px-2 py-3 transition hover:bg-muted/50">
                  <span className="min-w-0 truncate font-semibold group-hover:text-[var(--accent)]">{item.title}</span>
                  <span className="flex min-w-0 shrink-0 items-center gap-2">
                    <Badge tone={algorithmDifficultyMeta[item.difficulty]?.tone ?? 'default'}>{difficultyLabel(item.difficulty)}</Badge>
                    <ArrowRight className="h-4 w-4 text-muted-foreground transition group-hover:translate-x-0.5 group-hover:text-[var(--accent)]" />
                  </span>
                </Link>
              ))}
            </div>
          </Card>

          <Card className="p-5">
            <AlgorithmSectionHeader title="热门题目" description="近期提交活跃的练习" action={<Flame className="h-5 w-5 text-[var(--accent)]" />} />
            <div className="mt-4 divide-y divide-border/70">
              {data.hot.length === 0 && <AlgorithmEmptyState className="min-h-44" title="暂无热门题目" description="完成一次提交后，热度数据会展示在这里。" />}
              {data.hot.map(item => (
                <Link key={item.id} to={`/algorithm/problems/${item.id}`} className="group flex items-center justify-between gap-3 rounded-xl px-2 py-3 transition hover:bg-muted/50">
                  <span className="min-w-0 truncate font-semibold group-hover:text-[var(--accent)]">{item.title}</span>
                  <span className="shrink-0 text-xs tabular-nums text-muted-foreground">{item.submissionCount} 次 · {item.acceptanceRate.toFixed(1)}%</span>
                </Link>
              ))}
            </div>
          </Card>
        </div>

        <Card className="p-5">
          <AlgorithmSectionHeader title="最近练习" description="快速回看最近的判题结果" action={<Link to="/algorithm/submissions" className="inline-flex items-center gap-1 text-sm font-semibold text-[var(--accent)] hover:text-foreground">查看全部 <ArrowRight className="h-4 w-4" /></Link>} />
          <div className="mt-4 divide-y divide-border/70">
            {data.recentPractice.length === 0 && <AlgorithmEmptyState className="min-h-44" title="还没有提交记录" description="进入题库完成第一道算法题。" icon={Send} />}
            {data.recentPractice.map(item => (
              <Link key={item.id} to={`/algorithm/submissions/${item.id}`} className="group flex flex-wrap items-center justify-between gap-3 rounded-xl px-2 py-3 transition hover:bg-muted/50">
                <span className="min-w-0 truncate font-semibold group-hover:text-[var(--accent)]">{item.problemTitle}</span>
                <span className="flex flex-wrap items-center justify-end gap-2 text-xs text-muted-foreground">
                  <Badge tone={algorithmStatusTone(item.status)}>{algorithmStatusLabel(item.status)}</Badge>
                  {item.submitType === 'SUBMIT' && `${item.passedCount}/${item.totalCount} 用例`}
                  <span>{item.executionTimeMs != null ? `${item.executionTimeMs}ms` : ''}</span>
                  <span>{new Date(item.createdAt).toLocaleString('zh-CN')}</span>
                </span>
              </Link>
            ))}
          </div>
        </Card>
      </>}
    </div>
  )
}
