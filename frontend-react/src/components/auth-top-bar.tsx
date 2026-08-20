import { ArrowLeft, Bot } from 'lucide-react'
import { Link } from 'react-router-dom'

type AuthTopBarProps = {
  sectionLabel: string
}

export function AuthTopBar({ sectionLabel }: AuthTopBarProps) {
  return <header className="auth-flow-header auth-public-nav auth-flow-header-enter">
    <div className="auth-public-nav-inner">
      <div className="flex min-w-0 items-center gap-3 sm:gap-4">
        <Link to="/" className="auth-public-brand" aria-label="AInterview 首页">
          <span className="auth-public-brand-mark"><Bot aria-hidden="true" className="h-[18px] w-[18px]" /></span>
          <span className="auth-public-brand-copy">
            <strong>AInterview</strong>
            <small>招聘协同与智能面试</small>
          </span>
        </Link>
        <span aria-hidden="true" className="h-7 w-px shrink-0 bg-border" />
        <span className="truncate text-sm font-semibold text-muted-foreground sm:text-base">{sectionLabel}</span>
      </div>
      <Link to="/" className="auth-public-back">
        <ArrowLeft aria-hidden="true" className="h-4 w-4" />
        <span className="sm:hidden">首页</span>
        <span className="hidden sm:inline">返回首页</span>
      </Link>
    </div>
  </header>
}
