import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'
import { AlgorithmPageHeader } from '@/components/algorithm/algorithm-page'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { LazyCodeEditor } from '@/components/algorithm/LazyCodeEditor'
import { algorithmApi, type AlgorithmSubmissionDetail } from '@/lib/algorithm-api'
import { algorithmStatusLabel, algorithmStatusTone } from '@/lib/algorithm-status'
import { useTheme } from '@/lib/theme'

export function SubmissionDetailPage() {
  const { submissionId } = useParams()
  const [item, setItem] = useState<AlgorithmSubmissionDetail>()
  const [error, setError] = useState('')
  const { dark } = useTheme()

  useEffect(() => {
    void algorithmApi.submission(Number(submissionId))
      .then(setItem)
      .catch(reason => setError(reason instanceof Error ? reason.message : '加载失败'))
  }, [submissionId])

  if (error) return <p className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{error}</p>
  if (!item) return <p className="p-12 text-center text-sm text-muted-foreground">正在加载提交记录…</p>

  return (
    <div className="space-y-6">
      <AlgorithmPageHeader
        title={`#${item.id} · ${item.problemTitle}`}
        description={`${item.language} · 通过 ${item.passedCount}/${item.totalCount} · ${item.executionTimeMs != null ? `${item.executionTimeMs}ms` : '-'} · ${new Date(item.createdAt).toLocaleString('zh-CN')}`}
        backTo="/algorithm/submissions"
        backLabel="返回提交记录"
        compact
        actions={<Link to={`/algorithm/problems/${item.problemId}`} className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-full border border-border bg-surface px-5 text-sm font-semibold transition hover:border-[var(--accent)] hover:bg-[var(--accent-soft)] sm:w-auto">再次作答 <ArrowRight className="h-4 w-4" /></Link>}
      />
      <Badge tone={algorithmStatusTone(item.status)}>{algorithmStatusLabel(item.status)}</Badge>

      {(item.compileMessage || item.runtimeMessage) && (
        <Card className="p-4">
          {item.compileMessage && <>
            <p className="text-xs font-semibold text-muted-foreground">编译信息</p>
            <pre className="mt-1 max-h-64 overflow-auto whitespace-pre-wrap rounded-xl bg-rose-50 p-3 text-xs leading-5 text-rose-700">{item.compileMessage}</pre>
          </>}
          {item.runtimeMessage && <>
            <p className="mt-3 text-xs font-semibold text-muted-foreground">运行信息</p>
            <pre className="mt-1 max-h-64 overflow-auto whitespace-pre-wrap rounded-xl bg-rose-50 p-3 text-xs leading-5 text-rose-700">{item.runtimeMessage}</pre>
          </>}
        </Card>
      )}

      {item.caseResults.length > 0 && (
        <Card className="p-0">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground">
              <tr><th className="px-5 py-4">用例</th><th className="px-5 py-4">类型</th><th className="px-5 py-4">结果</th><th className="px-5 py-4">耗时</th></tr>
            </thead>
            <tbody className="divide-y divide-border/70">
              {item.caseResults.map((result, index) => (
                <tr key={index}>
                  <td className="px-5 py-4">#{index + 1}</td>
                  <td className="px-5 py-4">{result.caseType === 'SAMPLE' ? '示例' : '隐藏'}</td>
                  <td className="px-5 py-4">
                    <Badge tone={algorithmStatusTone(result.status)}>{algorithmStatusLabel(result.status)}</Badge>
                  </td>
                  <td className="px-5 py-4">{result.executionTimeMs != null ? `${result.executionTimeMs}ms` : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <Card className="overflow-hidden">
        <div className="border-b border-border p-4"><h2 className="font-bold">提交代码</h2></div>
        <LazyCodeEditor value={item.sourceCode} onChange={() => undefined} height={420} readOnly dark={dark} />
      </Card>
    </div>
  )
}
