import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

type BadgeProps = HTMLAttributes<HTMLSpanElement> & {
  tone?: 'default' | 'success' | 'warning' | 'danger' | 'info'
}

const tones = {
  default: 'bg-muted text-muted-foreground',
  success: 'bg-[var(--success)] text-[var(--success-foreground)]',
  warning: 'bg-[var(--warning)] text-[var(--warning-foreground)]',
  danger: 'bg-[var(--danger)] text-[var(--danger-foreground)]',
  info: 'bg-[var(--info)] text-[var(--info-foreground)]',
}

export function Badge({ className, tone = 'default', ...props }: BadgeProps) {
  return <span className={cn('inline-flex items-center whitespace-nowrap rounded-full px-3 py-1 text-xs font-semibold', tones[tone], className)} {...props} />
}
