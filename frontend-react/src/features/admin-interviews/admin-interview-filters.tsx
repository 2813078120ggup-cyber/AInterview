import { Search } from 'lucide-react'
import { ResponsiveSelect } from '@/components/ui/responsive-select'
import type { Candidate } from './admin-interviews-api'

export type InterviewFilters = {
  search: string
  candidate: string
  status: string
  time: string
}

type Props = {
  filters: InterviewFilters
  candidates: Candidate[]
  onChange: (next: Partial<InterviewFilters>) => void
}

export function AdminInterviewFilters({ filters, candidates, onChange }: Props) {
  return (
    <div className="flex flex-col gap-3 border-b border-border p-5 md:flex-row">
      <label className="flex h-12 flex-1 items-center gap-2 rounded-full border border-border bg-surface px-4">
        <Search className="h-4 w-4 text-muted-foreground" />
        <input
          value={filters.search}
          onChange={event => onChange({ search: event.target.value })}
          className="w-full bg-transparent text-sm outline-none"
          placeholder="搜索主题、候选人姓名或账号"
        />
      </label>
      <ResponsiveSelect
        ariaLabel="选择候选人"
        value={filters.candidate}
        onValueChange={candidate => onChange({ candidate })}
        searchable
        className="w-full md:w-auto md:max-w-[260px]"
        options={[
          { value: '', label: '全部候选人' },
          ...candidates.map(item => ({ value: String(item.id), label: `${item.realName}（${item.username}）` })),
        ]}
      />
      <ResponsiveSelect
        ariaLabel="选择面试时间"
        value={filters.time}
        onValueChange={time => onChange({ time })}
        className="w-full md:w-auto"
        options={[
          { value: 'all', label: '全部时间' },
          { value: 'today', label: '今天' },
          { value: 'next7', label: '未来 7 天' },
          { value: 'past', label: '已过期' },
        ]}
      />
      <ResponsiveSelect
        ariaLabel="选择面试状态"
        value={filters.status}
        onValueChange={status => onChange({ status })}
        className="w-full md:w-auto"
        options={[
          { value: '', label: '全部状态' },
          { value: '0', label: '待开始' },
          { value: '1', label: '进行中' },
          { value: '2', label: '已结束' },
          { value: '3', label: '已取消' },
          { value: '4', label: '已通过' },
          { value: '5', label: '报告生成中' },
          { value: '6', label: '报告已生成' },
          { value: '7', label: '未通过' },
        ]}
      />
    </div>
  )
}
