import type { ReactNode } from 'react'
import { ArrowLeft, Inbox, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router-dom'

import { cn } from '@/lib/utils'

type AlgorithmPageHeaderProps = {
  eyebrow?: string
  title: string
  description?: string
  backTo?: string
  backLabel?: string
  actions?: ReactNode
  compact?: boolean
}

export function AlgorithmPageHeader({
  eyebrow = '算法练习中心',
  title,
  description,
  backTo,
  backLabel = '返回',
  actions,
  compact = false,
}: AlgorithmPageHeaderProps) {
  return <header className="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
    <div className="min-w-0">
      {backTo && <Link
        to={backTo}
        className="mb-5 inline-flex h-10 items-center gap-2 rounded-full border border-border bg-surface px-4 text-sm font-semibold shadow-[0_6px_18px_rgba(20,18,17,.04)] transition hover:border-[var(--accent)] hover:bg-[var(--accent-soft)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]"
        aria-label={backLabel}
      >
        <ArrowLeft className="h-4 w-4" />{backLabel}
      </Link>}
      <p className="text-sm font-semibold text-[var(--accent)]">{eyebrow}</p>
      <h1 className={cn('mt-2 font-bold tracking-tight', compact ? 'text-2xl sm:text-3xl' : 'text-3xl sm:text-4xl')}>{title}</h1>
      {description && <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground sm:text-base">{description}</p>}
    </div>
    {actions && <div className="flex w-full shrink-0 flex-wrap items-center gap-2 md:w-auto md:justify-end">{actions}</div>}
  </header>
}

export function AlgorithmSectionHeader({
  title,
  description,
  action,
}: {
  title: string
  description?: string
  action?: ReactNode
}) {
  return <div className="flex flex-wrap items-start justify-between gap-3">
    <div>
      <h2 className="text-base font-bold sm:text-lg">{title}</h2>
      {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
    </div>
    {action}
  </div>
}

export function AlgorithmEmptyState({
  title,
  description,
  icon: Icon = Inbox,
  action,
  className,
}: {
  title: string
  description?: string
  icon?: LucideIcon
  action?: ReactNode
  className?: string
}) {
  return <div className={cn('grid min-h-56 place-items-center px-6 py-10 text-center', className)}>
    <div className="max-w-sm">
      <span className="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]">
        <Icon className="h-5 w-5" />
      </span>
      <h3 className="mt-4 font-bold">{title}</h3>
      {description && <p className="mt-2 text-sm leading-6 text-muted-foreground">{description}</p>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  </div>
}
