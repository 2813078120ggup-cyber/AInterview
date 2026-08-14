import { Eye, Loader2, Power, PowerOff, type LucideIcon } from 'lucide-react'
import { useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { AdminConfirmDialog } from '@/components/admin-confirm-dialog'
import { Button } from '@/components/ui/button'
import { buttonClassName } from '@/components/ui/button-styles'
import { cn } from '@/lib/utils'

const actionClassName = 'min-w-[104px] shrink-0 whitespace-nowrap'

export function AdminRowActions({ children }: { children: ReactNode }) {
  return <div className="inline-flex min-w-max items-center justify-end gap-2 whitespace-nowrap">{children}</div>
}

export function AdminRowActionLink({
  to,
  label = '查看详情',
  icon: Icon = Eye,
  className,
  state,
}: {
  to: string
  label?: string
  icon?: LucideIcon
  className?: string
  state?: unknown
}) {
  return <Link to={to} state={state} className={buttonClassName({ variant: 'secondary', size: 'compact', className: cn(actionClassName, className) })}>
    <Icon aria-hidden="true" className="h-4 w-4" />
    {label}
  </Link>
}

export function AdminRowActionButton({
  label,
  icon: Icon,
  busy = false,
  onClick,
  className,
}: {
  label: string
  icon?: LucideIcon
  busy?: boolean
  onClick: () => void
  className?: string
}) {
  return <Button type="button" variant="secondary" size="compact" className={cn(actionClassName, className)} disabled={busy} aria-busy={busy} onClick={onClick}>
    {busy ? <Loader2 aria-hidden="true" className="h-4 w-4 animate-spin" /> : Icon ? <Icon aria-hidden="true" className="h-4 w-4" /> : null}
    {busy ? '处理中…' : label}
  </Button>
}

export function AdminAccountRowActions({
  detailTo,
  subjectLabel,
  active,
  busy = false,
  onToggleStatus,
  disableDescription,
}: {
  detailTo: string
  subjectLabel: string
  active: boolean
  busy?: boolean
  onToggleStatus: () => void
  disableDescription?: string
}) {
  const [confirmOpen, setConfirmOpen] = useState(false)

  function requestStatusChange() {
    if (active) setConfirmOpen(true)
    else onToggleStatus()
  }

  return <>
    <AdminRowActions>
      <AdminRowActionLink to={detailTo} />
      <AdminRowActionButton
        label={active ? '停用' : '启用'}
        icon={active ? PowerOff : Power}
        busy={busy}
        onClick={requestStatusChange}
        className="min-w-[88px]"
      />
    </AdminRowActions>
    {confirmOpen && <AdminConfirmDialog
      title={`停用${subjectLabel}？`}
      description={disableDescription || '停用后该账号将无法登录，历史业务数据仍会保留。'}
      confirmLabel="确认停用"
      danger
      busy={busy}
      onClose={() => setConfirmOpen(false)}
      onConfirm={() => {
        setConfirmOpen(false)
        onToggleStatus()
      }}
    />}
  </>
}
