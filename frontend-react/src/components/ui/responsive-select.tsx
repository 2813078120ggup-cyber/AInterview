import * as Dialog from '@radix-ui/react-dialog'
import { Check, ChevronDown, Search, X } from 'lucide-react'
import { useMemo, useState } from 'react'

import { cn } from '@/lib/utils'

export type ResponsiveSelectOption = {
  value: string
  label: string
}

type ResponsiveSelectProps = {
  ariaLabel: string
  value?: string
  values?: string[]
  options: ResponsiveSelectOption[]
  onValueChange?: (value: string) => void
  onValuesChange?: (values: string[]) => void
  className?: string
  searchable?: boolean
  multiple?: boolean
  placeholder?: string
  disabled?: boolean
}

export function ResponsiveSelect({
  ariaLabel,
  value,
  values,
  options,
  onValueChange,
  onValuesChange,
  className,
  searchable = false,
  multiple = false,
  placeholder = '请选择',
  disabled = false,
}: ResponsiveSelectProps) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const selectedValues = multiple ? (values ?? []) : value !== undefined ? [value] : []
  const selectedOptions = options.filter(option => selectedValues.includes(option.value))
  const summary = multiple
    ? selectedOptions.length > 1
      ? `已选 ${selectedOptions.length} 项`
      : (selectedOptions[0]?.label ?? placeholder)
    : (options.find(option => option.value === value)?.label ?? placeholder)
  const visibleOptions = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    return keyword ? options.filter(option => option.label.toLowerCase().includes(keyword)) : options
  }, [options, query])

  function choose(nextValue: string) {
    onValueChange?.(nextValue)
    setOpen(false)
    setQuery('')
  }

  function toggle(nextValue: string) {
    if (!multiple) {
      choose(nextValue)
      return
    }
    const next = selectedValues.includes(nextValue)
      ? selectedValues.filter(item => item !== nextValue)
      : [...selectedValues, nextValue]
    onValuesChange?.(next)
  }

  return <>
    {multiple ? (
      <select
        aria-label={ariaLabel}
        multiple
        disabled={disabled}
        value={selectedValues}
        onChange={event => onValuesChange?.(Array.from(event.target.selectedOptions, option => option.value))}
        className={cn('hidden min-h-28 w-full rounded-xl border border-border bg-background p-3 text-sm disabled:cursor-not-allowed disabled:opacity-60 sm:block', className)}
      >
        {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
    ) : (
      <select
        aria-label={ariaLabel}
        disabled={disabled}
        className={cn('hidden h-12 rounded-full border border-border bg-surface px-4 text-sm disabled:cursor-not-allowed disabled:opacity-60 sm:block', className)}
        value={value}
        onChange={event => choose(event.target.value)}
      >
        {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
      </select>
    )}

    <Dialog.Root open={open && !disabled} onOpenChange={nextOpen => {
      setOpen(nextOpen)
      if (!nextOpen) setQuery('')
    }}>
      <Dialog.Trigger asChild>
        <button
          type="button"
          aria-label={ariaLabel}
          disabled={disabled}
          className={cn('flex h-12 w-full min-w-0 items-center justify-between gap-3 rounded-full border border-border bg-surface px-4 text-left text-base transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60 sm:hidden', className)}
        >
          <span className="min-w-0 flex-1 truncate">{summary}</span>
          <ChevronDown className="h-4 w-4 shrink-0 text-muted-foreground" />
        </button>
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-[90] bg-black/45 backdrop-blur-sm sm:hidden" />
        <Dialog.Content className="safe-area-bottom fixed inset-x-0 bottom-0 z-[91] flex max-h-[min(82dvh,680px)] flex-col overflow-hidden rounded-t-[28px] border border-border bg-surface shadow-2xl sm:hidden">
          <header className="flex items-start justify-between gap-4 border-b border-border px-5 py-4">
            <div className="min-w-0">
              <p className="text-xs font-semibold tracking-[.12em] text-[var(--accent)]">{multiple ? '多选' : '筛选条件'}</p>
              <Dialog.Title className="mt-1 truncate text-xl font-bold">{ariaLabel}</Dialog.Title>
              <Dialog.Description className="sr-only">{multiple ? `从列表中选择一个或多个${ariaLabel}。` : `选择后将立即应用${ariaLabel}。`}</Dialog.Description>
            </div>
            <Dialog.Close asChild>
              <button type="button" aria-label="关闭" className="grid h-11 w-11 shrink-0 place-items-center rounded-full transition hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]">
                <X className="h-5 w-5" />
              </button>
            </Dialog.Close>
          </header>

          {searchable && <label className="mx-4 mt-4 flex h-12 items-center gap-2 rounded-2xl border border-border bg-background px-3">
            <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
            <span className="sr-only">搜索{ariaLabel}</span>
            <input
              autoFocus
              value={query}
              onChange={event => setQuery(event.target.value)}
              className="min-w-0 flex-1 bg-transparent text-base outline-none"
              placeholder={`搜索${ariaLabel}`}
            />
          </label>}

          <div role="listbox" aria-label={ariaLabel} className="min-h-0 flex-1 overscroll-contain overflow-y-auto p-4">
            <div className="space-y-2">
              {visibleOptions.map(option => {
                const active = selectedValues.includes(option.value)
                return <button
                  key={option.value}
                  type="button"
                  role="option"
                  aria-selected={active}
                  className={cn(
                    'flex min-h-12 w-full items-center justify-between gap-3 rounded-2xl border px-4 py-3 text-left text-base transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]',
                    active ? 'border-[var(--accent)] bg-[var(--accent-soft)] font-semibold text-foreground' : 'border-border bg-surface hover:bg-muted',
                  )}
                  onClick={() => toggle(option.value)}
                >
                  <span className="min-w-0 flex-1 break-words">{option.label}</span>
                  {multiple ? (
                    <span className={cn('grid h-6 w-6 shrink-0 place-items-center rounded-lg border', active ? 'border-[var(--accent)] bg-[var(--accent)] text-white' : 'border-border bg-background')}>
                      {active && <Check className="h-4 w-4" />}
                    </span>
                  ) : active && <Check className="h-5 w-5 shrink-0 text-[var(--accent)]" />}
                </button>
              })}
              {!visibleOptions.length && <p className="py-10 text-center text-sm text-muted-foreground">没有匹配的选项</p>}
            </div>
          </div>

          {multiple && (
            <footer className="safe-area-bottom border-t border-border p-4">
              <Dialog.Close asChild>
                <button type="button" className="h-12 w-full rounded-full bg-[var(--accent)] font-semibold text-white transition hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)] focus-visible:ring-offset-2">
                  完成{selectedOptions.length ? `（已选 ${selectedOptions.length} 项）` : ''}
                </button>
              </Dialog.Close>
            </footer>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  </>
}
