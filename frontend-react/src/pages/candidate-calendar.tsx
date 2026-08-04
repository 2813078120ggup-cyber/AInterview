import { CalendarDays, ChevronLeft, ChevronRight, Clock3, Play } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import { type Interview, request } from '@/lib/api'
import { canEnterInterview, interviewStatusText, interviewStatusTone } from '@/lib/interview-status'
import { isPracticeInterview } from '@/lib/interviewer-styles'

const weekdayLabels = ['一', '二', '三', '四', '五', '六', '日']

function dayKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function parseDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function timeText(value: string) {
  const date = parseDate(value)
  return date ? `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}` : '--:--'
}

function dateText(value: string) {
  const date = parseDate(value)
  return date ? `${date.getMonth() + 1} 月 ${date.getDate()} 日 ${timeText(value)}` : value
}

function dayOfYear(date: Date) {
  const start = new Date(date.getFullYear(), 0, 0)
  return Math.floor((date.getTime() - start.getTime()) / 86_400_000)
}

export function CandidateCalendar() {
  const navigate = useNavigate()
  const now = new Date()
  const [items, setItems] = useState<Interview[]>([])
  const [month, setMonth] = useState(() => new Date(now.getFullYear(), now.getMonth(), 1))
  const [selectedDay, setSelectedDay] = useState(() => dayKey(now))
  const [error, setError] = useState('')

  useEffect(() => {
    void request<Interview[]>('/v1/interviews')
      .then(interviews => setItems(interviews.filter(item => !isPracticeInterview(item.remark))))
      .catch(reason => setError(reason instanceof Error ? reason.message : '面试日历加载失败，请稍后重试。'))
  }, [])

  const interviewsByDay = useMemo(() => {
    const result = new Map<string, Interview[]>()
    items.forEach(item => {
      const date = parseDate(item.scheduledAt)
      if (!date) return
      const key = dayKey(date)
      result.set(key, [...(result.get(key) ?? []), item])
    })
    result.forEach(list => list.sort((left, right) => left.scheduledAt.localeCompare(right.scheduledAt)))
    return result
  }, [items])

  const monthDays = useMemo(() => {
    const firstWeekday = (month.getDay() + 6) % 7
    const first = new Date(month.getFullYear(), month.getMonth(), 1)
    return Array.from({ length: 42 }, (_, index) => new Date(first.getFullYear(), first.getMonth(), index - firstWeekday + 1))
  }, [month])
  const selectedItems = interviewsByDay.get(selectedDay) ?? []
  const today = dayKey(now)
  const currentWeekday = weekdayLabels[(now.getDay() + 6) % 7]
  const yearOptions = Array.from({ length: 8 }, (_, index) => now.getFullYear() - 2 + index)

  function moveMonth(offset: number) {
    setMonth(current => new Date(current.getFullYear(), current.getMonth() + offset, 1))
  }

  function returnToday() {
    const date = new Date()
    setMonth(new Date(date.getFullYear(), date.getMonth(), 1))
    setSelectedDay(dayKey(date))
  }

  async function enter(item: Interview) {
    try {
      if (item.status === 0) await request(`/v1/interviews/${item.id}/start`, { method: 'POST' })
      navigate(`/candidate/interviews/${item.id}/room`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '无法进入面试，请稍后重试。')
    }
  }

  return <div className="mx-auto max-w-6xl">
    <section className="-mx-4 overflow-hidden border-y border-border bg-surface shadow-[0_1px_2px_rgba(20,18,17,.04),0_18px_45px_rgba(20,18,17,.045)] sm:mx-0 sm:rounded-[24px] sm:border">
      <header className="flex items-center gap-4 border-b border-border px-4 py-5 sm:gap-5 sm:px-6 sm:py-6">
        <div className="grid h-20 w-20 shrink-0 place-items-center rounded-2xl bg-muted text-center sm:h-28 sm:w-28">
          <div><p className="text-sm font-bold sm:text-lg">星期{currentWeekday}</p><strong className="mt-1 block text-3xl leading-none sm:text-5xl">{now.getDate()}</strong></div>
        </div>
        <div>
          <p className="text-xl font-bold sm:text-2xl">{month.getFullYear()}年{month.getMonth() + 1}月</p>
          <p className="mt-1 text-sm text-muted-foreground sm:text-base">今天是本年第 {dayOfYear(now)} 天</p>
          <p className="mt-3 flex items-center gap-2 text-sm font-semibold text-[var(--accent)]"><CalendarDays className="h-4 w-4" />已安排的面试</p>
        </div>
      </header>

      <div className="grid grid-cols-[1fr_44px_1fr_44px] items-center gap-2 border-b border-border px-4 py-4 sm:flex sm:flex-wrap sm:gap-3 sm:px-6">
        <ResponsiveSelect
          ariaLabel="选择年份"
          value={String(month.getFullYear())}
          onValueChange={next => setMonth(current => new Date(Number(next), current.getMonth(), 1))}
          className="min-w-0 w-full sm:w-auto"
          options={yearOptions.map(year => ({ value: String(year), label: `${year}年` }))}
        />
        <Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={() => moveMonth(-1)} aria-label="上一个月" title="上一个月"><ChevronLeft className="h-5 w-5" /></Button>
        <ResponsiveSelect
          ariaLabel="选择月份"
          value={String(month.getMonth())}
          onValueChange={next => setMonth(current => new Date(current.getFullYear(), Number(next), 1))}
          className="min-w-0 w-full sm:w-auto"
          options={Array.from({ length: 12 }, (_, index) => ({ value: String(index), label: `${index + 1}月` }))}
        />
        <Button type="button" variant="ghost" className="h-10 w-10 rounded-full px-0" onClick={() => moveMonth(1)} aria-label="下一个月" title="下一个月"><ChevronRight className="h-5 w-5" /></Button>
        <Button type="button" variant="secondary" className="col-span-4 mt-1 w-full sm:col-auto sm:ml-auto sm:mt-0 sm:w-auto" onClick={returnToday}>返回今天</Button>
      </div>

      {error && <p className="mx-6 mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}

      <div className="overflow-hidden">
        <div className="px-0 pb-4 pt-3 sm:px-6 sm:pb-6 sm:pt-4">
          <div className="grid grid-cols-7 text-center text-xs font-semibold sm:text-base">
            {weekdayLabels.map((label, index) => <span key={label} className={`py-2 sm:py-3 ${index > 4 ? 'text-rose-600' : ''}`}>{label}</span>)}
          </div>
          <div className="grid grid-cols-7">
            {monthDays.map(date => {
              const key = dayKey(date)
              const entries = interviewsByDay.get(key) ?? []
              const inMonth = date.getMonth() === month.getMonth()
              const selected = key === selectedDay
              const weekend = date.getDay() === 0 || date.getDay() === 6
              return <button
                key={key}
                type="button"
                onClick={() => setSelectedDay(key)}
                className={`min-h-16 min-w-0 rounded-lg px-1.5 py-2 text-left transition sm:min-h-28 sm:rounded-xl sm:px-3 ${inMonth ? 'hover:bg-muted/55' : 'text-muted-foreground/45'} ${selected ? 'bg-[var(--accent-soft)] ring-2 ring-inset ring-[var(--accent)]' : ''}`}
              >
                <div className="flex items-start justify-between gap-0.5"><span className={`text-base font-bold leading-none sm:text-3xl ${!inMonth ? 'text-muted-foreground/45' : weekend ? 'text-rose-600' : 'text-foreground'}`}>{date.getDate()}</span>{key === today && <span className="text-[10px] font-bold text-[var(--accent)] sm:text-xs">今</span>}</div>
                {entries.length > 0 && <><span className="mx-auto mt-2 block h-1.5 w-1.5 rounded-full bg-[var(--accent)] sm:hidden" aria-label={`${entries.length} 场面试`} /><span className="mt-3 hidden truncate text-xs font-semibold text-[var(--accent)] sm:block">{timeText(entries[0].scheduledAt)} {entries[0].title}</span></>}
                {entries.length > 1 && <span className="mt-1 hidden text-xs text-muted-foreground sm:block">另有 {entries.length - 1} 场面试</span>}
                {!entries.length && <span className="mt-3 hidden text-xs text-muted-foreground/55 sm:block">{inMonth ? '暂无安排' : ''}</span>}
              </button>
            })}
          </div>
        </div>
      </div>
    </section>

    <section className="mt-6 border-t border-border pt-6">
      <div className="flex items-center justify-between gap-4"><div><p className="text-sm font-semibold text-[var(--accent)]">当日安排</p><h2 className="mt-1 text-xl font-bold">{selectedDay.replaceAll('-', '.')}</h2></div><span className="text-sm text-muted-foreground">{selectedItems.length} 场面试</span></div>
      <div className="mt-5 grid gap-3 md:grid-cols-2">
        {selectedItems.map(item => <article key={item.id} className="border-l-2 border-[var(--accent)] bg-surface px-4 py-3">
          <div className="flex items-center justify-between gap-2"><span className="text-sm font-semibold">{timeText(item.scheduledAt)}</span><Badge tone={interviewStatusTone(item.status)}>{interviewStatusText[item.status] ?? '未知状态'}</Badge></div>
          <h3 className="mt-2 font-semibold">{item.title}</h3>
          <p className="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground"><Clock3 className="h-3.5 w-3.5" />{dateText(item.scheduledAt)} · {item.duration} 分钟</p>
          {canEnterInterview(item.status) && <Button className="mt-4 h-9 w-full px-3" onClick={() => void enter(item)}><Play className="h-3.5 w-3.5" />{item.status === 1 ? '继续面试' : '开始面试'}</Button>}
        </article>)}
        {!selectedItems.length && <p className="py-8 text-sm text-muted-foreground">当日暂无面试安排。</p>}
      </div>
    </section>
  </div>
}
