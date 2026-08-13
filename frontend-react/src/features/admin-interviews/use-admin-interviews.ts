import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { adminInterviewsApi, type Candidate, type InterviewRow, type Question, type QuestionBank, type ReportItem } from './admin-interviews-api'

type LoadOptions = { silent?: boolean }

export function useAdminInterviews() {
  const [items, setItems] = useState<InterviewRow[]>([])
  const [reports, setReports] = useState<ReportItem[]>([])
  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [questionsByBank, setQuestionsByBank] = useState<Record<string, Question[]>>({})
  const [questionLoadingBank, setQuestionLoadingBank] = useState('')
  const [banks, setBanks] = useState<QuestionBank[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const loadingRef = useRef(false)
  const questionsRef = useRef<Record<string, Question[]>>({})
  const loadingBankRef = useRef('')

  const load = useCallback(async ({ silent = false }: LoadOptions = {}) => {
    if (loadingRef.current) return
    loadingRef.current = true
    setLoading(!silent)
    setRefreshing(silent)

    const results = await Promise.allSettled([
      adminInterviewsApi.listInterviews(),
      adminInterviewsApi.listCandidates(),
      adminInterviewsApi.listBanks(),
      adminInterviewsApi.listReports(),
    ])
    const failures: string[] = []
    const [interviews, candidateList, bankPage, reportPage] = results
    if (interviews.status === 'fulfilled') setItems(interviews.value)
    else failures.push('面试列表')
    if (candidateList.status === 'fulfilled') setCandidates(candidateList.value)
    else failures.push('候选人')
    if (bankPage.status === 'fulfilled') setBanks(bankPage.value.records ?? [])
    else failures.push('题库')
    if (reportPage.status === 'fulfilled') setReports(reportPage.value.records ?? [])
    else failures.push('报告')

    setError(failures.length ? `${failures.join('、')}数据加载失败，已显示可用内容；可点击重试。` : '')
    setLoading(false)
    setRefreshing(false)
    loadingRef.current = false
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') void load({ silent: true })
    }
    window.addEventListener('focus', refreshWhenVisible)
    document.addEventListener('visibilitychange', refreshWhenVisible)
    return () => {
      window.removeEventListener('focus', refreshWhenVisible)
      document.removeEventListener('visibilitychange', refreshWhenVisible)
    }
  }, [load])

  const loadBankQuestions = useCallback(async (bankId: string) => {
    if (!bankId || questionsRef.current[bankId] || loadingBankRef.current === bankId) return
    loadingBankRef.current = bankId
    setQuestionLoadingBank(bankId)
    try {
      const page = await adminInterviewsApi.listBankQuestions(bankId)
      questionsRef.current[bankId] = page.records ?? []
      setQuestionsByBank(previous => ({ ...previous, [bankId]: page.records ?? [] }))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '题库题目加载失败，请稍后重试。')
    } finally {
      loadingBankRef.current = ''
      setQuestionLoadingBank('')
    }
  }, [])

  const candidateById = useMemo(() => new Map(candidates.map(item => [String(item.id), item])), [candidates])
  const reportByInterviewId = useMemo(() => new Map(reports.map(item => [String(item.interviewId), item])), [reports])

  return {
    items,
    setItems,
    reports,
    setReports,
    candidates,
    banks,
    questionsByBank,
    questionLoadingBank,
    loading,
    refreshing,
    error,
    setError,
    load,
    loadBankQuestions,
    candidateById,
    reportByInterviewId,
  }
}
