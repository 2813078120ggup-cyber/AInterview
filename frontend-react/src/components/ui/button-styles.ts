import { cn } from '@/lib/utils'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
export type ButtonSize = 'default' | 'compact' | 'large' | 'icon' | 'icon-compact'

const variants: Record<ButtonVariant, string> = {
  primary: 'border border-transparent bg-[var(--primary)] text-[var(--primary-foreground)] shadow-[0_14px_34px_rgba(20,18,17,.18)] hover:-translate-y-0.5 hover:shadow-[0_18px_42px_rgba(20,18,17,.22)] dark:shadow-[0_14px_34px_rgba(0,0,0,.3)]',
  secondary: 'border border-border bg-surface text-foreground shadow-[0_8px_26px_rgba(20,18,17,.06)] hover:-translate-y-0.5 hover:border-[var(--accent)] hover:bg-[var(--accent-soft)]',
  ghost: 'border border-transparent text-foreground hover:bg-muted',
  danger: 'border border-transparent bg-rose-600 text-white hover:bg-rose-700',
}

const sizes: Record<ButtonSize, string> = {
  default: 'h-11 px-5 text-sm font-semibold sm:h-11 sm:px-5 sm:text-sm',
  compact: 'h-10 px-4 text-sm font-semibold sm:h-10 sm:px-4 sm:text-sm',
  large: 'h-12 px-6 text-sm font-semibold sm:h-12 sm:px-6 sm:text-sm',
  icon: 'h-11 w-11 px-0 text-sm font-semibold sm:h-11 sm:w-11 sm:px-0 sm:text-sm',
  'icon-compact': 'h-10 w-10 px-0 text-sm font-semibold sm:h-10 sm:w-10 sm:px-0 sm:text-sm',
}

export function inferredButtonSize(className?: string): ButtonSize {
  if (!className) return 'default'
  const icon = /\bpx-0\b/.test(className) && /\bw-(?:8|9|10|11)\b/.test(className)
  if (icon) return /\bh-(?:8|9|10)\b/.test(className) ? 'icon-compact' : 'icon'
  if (/\bh-12\b/.test(className)) return 'large'
  if (/\bh-(?:8|9|10)\b/.test(className)) return 'compact'
  return 'default'
}

export function buttonClassName({ variant = 'primary', size = 'default', className }: { variant?: ButtonVariant; size?: ButtonSize; className?: string } = {}) {
  return cn(
    'inline-flex items-center justify-center gap-2 rounded-full font-sans leading-none transition duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-55',
    variants[variant],
    className,
    sizes[size],
  )
}
