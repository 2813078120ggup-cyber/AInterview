import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { cn } from '@/lib/utils'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
}

const variants = {
  primary: 'bg-[var(--primary)] text-[var(--primary-foreground)] shadow-[0_14px_34px_rgba(20,18,17,.18)] hover:-translate-y-0.5 hover:shadow-[0_18px_42px_rgba(20,18,17,.22)] dark:shadow-[0_14px_34px_rgba(0,0,0,.3)]',
  secondary: 'border border-border bg-surface text-foreground shadow-[0_8px_26px_rgba(20,18,17,.06)] hover:-translate-y-0.5 hover:border-[var(--accent)] hover:bg-[var(--accent-soft)]',
  ghost: 'text-foreground hover:bg-muted',
  danger: 'bg-rose-600 text-white hover:bg-rose-700',
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button({ className, variant = 'primary', ...props }, ref) {
  return <button
    ref={ref}
    className={cn('inline-flex h-11 items-center justify-center gap-2 rounded-full px-5 text-sm font-semibold transition duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-55', variants[variant], className)}
    {...props}
  />
})
