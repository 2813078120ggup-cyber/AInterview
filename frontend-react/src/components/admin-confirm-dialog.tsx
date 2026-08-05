import { X } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'

type AdminConfirmDialogProps = {
  title: string
  description: string
  confirmLabel: string
  busy?: boolean
  danger?: boolean
  children?: ReactNode
  onClose: () => void
  onConfirm: () => void
}

export function AdminConfirmDialog({
  title,
  description,
  confirmLabel,
  busy = false,
  danger = false,
  children,
  onClose,
  onConfirm,
}: AdminConfirmDialogProps) {
  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-black/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-labelledby="admin-confirm-dialog-title">
      <div className="mx-auto my-8 max-w-md rounded-[24px] bg-surface p-5 shadow-2xl sm:rounded-[30px] sm:p-7">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-sm font-semibold text-[var(--accent)]">操作确认</p>
            <h2 id="admin-confirm-dialog-title" className="mt-1 text-2xl font-bold">{title}</h2>
          </div>
          <Button type="button" variant="ghost" className="h-10 w-10 shrink-0 rounded-full px-0" aria-label="关闭确认对话框" onClick={onClose} disabled={busy}>
            <X className="h-5 w-5" />
          </Button>
        </div>
        <p className="mt-5 text-sm leading-6 text-muted-foreground">{description}</p>
        {children}
        <div className="mt-7 flex justify-end gap-3">
          <Button type="button" variant="secondary" onClick={onClose} disabled={busy}>取消</Button>
          <Button type="button" variant={danger ? 'danger' : 'primary'} onClick={onConfirm} disabled={busy} aria-busy={busy}>
            {busy ? '处理中…' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
