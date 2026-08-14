import { Building2, Check, LockKeyhole, ShieldCheck, UserRound } from 'lucide-react'
import type { ComponentType } from 'react'
import type { AdminRole } from '@/lib/admin'
import {
  roleAssignmentDomain,
  roleDisabledReason,
  type RoleAssignmentDomain,
} from '@/lib/role-assignment'

type RoleAssignmentPickerProps = {
  roles: AdminRole[]
  selectedRoleIds: string[]
  companyId?: string | null
  onToggle: (roleId: string) => void
  compact?: boolean
}

type RoleGroup = {
  domain: RoleAssignmentDomain
  title: string
  description: string
  icon: ComponentType<{ className?: string }>
}

const roleGroups: RoleGroup[] = [
  {
    domain: 'candidate',
    title: '候选人身份',
    description: '仅用于求职、练习与提交作答，必须单独分配。',
    icon: UserRound,
  },
  {
    domain: 'platform',
    title: '平台人员',
    description: '管理员、人力资源、面试官及自定义平台角色可按职责组合。',
    icon: ShieldCheck,
  },
  {
    domain: 'company',
    title: '企业成员',
    description: '必须绑定企业，企业管理员、招聘专员与面试官可组合。',
    icon: Building2,
  },
]

export function RoleAssignmentPicker({
  roles,
  selectedRoleIds,
  companyId,
  onToggle,
  compact = false,
}: RoleAssignmentPickerProps) {
  const activeRoles = roles.filter((role) => role.status === 1)

  return <div className="space-y-5">
    {roleGroups.map((group) => {
      const groupRoles = activeRoles.filter((role) => roleAssignmentDomain(role.roleCode) === group.domain)
      if (!groupRoles.length) return null
      const Icon = group.icon
      return <section key={group.domain} aria-labelledby={`role-group-${group.domain}`}>
        <div className="flex items-start gap-3">
          <span className="mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-muted text-muted-foreground">
            <Icon className="h-4 w-4" />
          </span>
          <div>
            <h3 id={`role-group-${group.domain}`} className="text-sm font-bold">{group.title}</h3>
            <p className="mt-1 text-xs leading-5 text-muted-foreground">{group.description}</p>
          </div>
        </div>
        <div className={`mt-3 grid gap-3 ${compact ? 'sm:grid-cols-2' : 'md:grid-cols-2 xl:grid-cols-3'}`}>
          {groupRoles.map((role) => {
            const active = selectedRoleIds.includes(role.id)
            const disabledReason = roleDisabledReason(role, activeRoles, selectedRoleIds, companyId)
            const disabled = Boolean(disabledReason)
            return <button
              type="button"
              key={role.id}
              onClick={() => onToggle(role.id)}
              disabled={disabled}
              className={`min-h-24 rounded-2xl border p-4 text-left transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] ${
                active
                  ? 'border-[var(--accent)] bg-[var(--accent-soft)]'
                  : disabled
                    ? 'cursor-not-allowed border-border bg-muted/40 opacity-70'
                    : 'border-border bg-background hover:bg-muted'
              }`}
              aria-pressed={active}
              aria-describedby={disabled ? `role-reason-${role.id}` : undefined}
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <strong className="block text-sm">{role.roleName}</strong>
                  <span className="mt-1 block text-xs text-muted-foreground">{role.roleCode}</span>
                </div>
                {active
                  ? <Check className="h-4 w-4 shrink-0 text-[var(--accent)]" />
                  : disabled && <LockKeyhole className="h-4 w-4 shrink-0 text-muted-foreground" />}
              </div>
              <p
                id={disabled ? `role-reason-${role.id}` : undefined}
                className={`mt-3 text-xs leading-5 ${disabled ? 'font-medium text-foreground/70' : 'text-muted-foreground'}`}
              >
                {disabledReason || role.description || '暂无角色说明'}
              </p>
            </button>
          })}
        </div>
      </section>
    })}
  </div>
}
