import { BookOpenCheck, CalendarCheck2, PlayCircle } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { CandidatePageShell } from '@/components/candidate-page-shell'
import { request, type Interview, type PracticeBank } from '@/lib/api'

export function CandidateLibrary() {
  const navigate = useNavigate()
  const [banks, setBanks] = useState<PracticeBank[]>([])
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    void request<PracticeBank[]>('/v1/interviews/practice/banks')
      .then(setBanks)
      .catch(reason => setError(reason instanceof Error ? reason.message : '专项练习加载失败，请稍后重试。'))
  }, [])

  async function start(bank: PracticeBank) {
    setBusy(bank.id)
    try {
      const interview = await request<Interview>('/v1/interviews/practice', {
        method: 'POST',
        body: JSON.stringify({ questionBankId: bank.id, questionCount: 5, duration: 30, interviewerStyle: 'big-tech' }),
      })
      navigate(`/candidate/interviews/${interview.id}/room`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '练习创建失败，请稍后重试。')
    } finally {
      setBusy('')
    }
  }

  return <CandidatePageShell><div className="space-y-6">
    <header><p className="text-sm font-semibold text-[var(--accent)]">专项练习</p><h1 className="mt-2 text-3xl font-bold">题库与岗位方向</h1><p className="mt-2 text-muted-foreground">选择题库，围绕目标岗位开展专项模拟练习。</p></header>
    {error && <p role="alert" className="rounded-2xl bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}
    <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
      {banks.map((bank, index) => <Card key={bank.id} className="flex min-h-64 flex-col">
        <div className="flex items-start justify-between"><span className="grid h-11 w-11 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]"><BookOpenCheck className="h-5 w-5" /></span><Badge tone="info">{bank.questionCount} 题</Badge></div>
        <p className="mt-6 text-xs font-semibold text-[var(--accent)]">练习方向 {String(index + 1).padStart(2, '0')}</p>
        <h2 className="mt-2 text-xl font-bold">{bank.name}</h2>
        <p className="mt-3 flex-1 text-sm leading-6 text-muted-foreground">{bank.description || '通过追问和即时反馈，强化本方向的核心能力。'}</p>
        <Button className="mt-6 w-full" disabled={busy === bank.id} onClick={() => void start(bank)}>{busy === bank.id ? '正在创建…' : '开始 30 分钟练习'}<PlayCircle className="h-4 w-4" /></Button>
      </Card>)}
    </div>
    <Card className="soft-emphasis-panel"><div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
      <div><p className="text-sm font-semibold text-white/80">训练建议</p><h2 className="mt-2 text-2xl font-bold">从通用能力开始训练</h2><p className="mt-2 text-sm text-white/75">完成基础模拟后，可根据评测报告选择后续方向。</p></div>
      <Button variant="secondary" className="w-full md:w-auto" onClick={() => navigate('/candidate/interviews')}><CalendarCheck2 className="h-4 w-4" />查看面试记录</Button>
    </div></Card>
  </div></CandidatePageShell>
}
