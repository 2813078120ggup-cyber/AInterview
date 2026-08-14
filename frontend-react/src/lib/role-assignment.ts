import type { AdminRole } from '@/lib/admin'

export type RoleAssignmentDomain = 'candidate' | 'platform' | 'company'

export const companyRoleCodes = new Set([
  'COMPANY_ADMIN',
  'COMPANY_RECRUITER',
  'COMPANY_INTERVIEWER',
])

export function roleAssignmentDomain(roleCode: string): RoleAssignmentDomain {
  if (roleCode === 'CANDIDATE') return 'candidate'
  if (companyRoleCodes.has(roleCode)) return 'company'
  return 'platform'
}

export function evaluateRoleSelection(
  roles: AdminRole[],
  selectedRoleIds: string[],
  companyId?: string | null,
) {
  const selectedRoles = roles.filter((role) => selectedRoleIds.includes(role.id))
  const domains = new Set(selectedRoles.map((role) => roleAssignmentDomain(role.roleCode)))

  if (!selectedRoles.length) {
    return { valid: false, domain: 'empty' as const, label: '待选择身份', message: '至少选择一个角色。' }
  }
  if (domains.has('candidate') && selectedRoles.length > 1) {
    return {
      valid: false,
      domain: 'conflict' as const,
      label: '身份冲突',
      message: '候选人角色必须单独分配，不能同时拥有平台或企业角色。',
    }
  }
  if (!companyId && domains.has('company')) {
    return {
      valid: false,
      domain: 'conflict' as const,
      label: '缺少企业归属',
      message: '企业角色必须先绑定企业。',
    }
  }
  if (companyId && [...domains].some((domain) => domain !== 'company')) {
    return {
      valid: false,
      domain: 'conflict' as const,
      label: '身份冲突',
      message: '企业成员只能分配企业角色。',
    }
  }

  const domain = domains.has('company') ? 'company' : domains.has('candidate') ? 'candidate' : 'platform'
  const labels = { candidate: '候选人身份', platform: '平台人员', company: '企业成员' } as const
  return { valid: true, domain, label: labels[domain], message: '' }
}

export function roleDisabledReason(
  role: AdminRole,
  roles: AdminRole[],
  selectedRoleIds: string[],
  companyId?: string | null,
) {
  if (selectedRoleIds.includes(role.id)) return ''
  const roleDomain = roleAssignmentDomain(role.roleCode)
  const selectedDomains = new Set(
    roles
      .filter((item) => selectedRoleIds.includes(item.id))
      .map((item) => roleAssignmentDomain(item.roleCode)),
  )

  if (companyId) {
    if (roleDomain !== 'company') return '企业成员只能使用企业角色'
    if ([...selectedDomains].some((domain) => domain !== 'company')) return '请先移除与企业归属冲突的角色'
  } else {
    if (roleDomain === 'company') return '需先绑定企业'
    if (selectedDomains.has('company')) return '请先移除企业角色'
  }
  if (roleDomain === 'candidate' && selectedDomains.has('platform')) return '候选人身份必须单独使用'
  if (roleDomain === 'platform' && selectedDomains.has('candidate')) return '已选择候选人身份'
  return ''
}

export function sameRoleSelection(left: string[], right: string[]) {
  if (left.length !== right.length) return false
  const rightSet = new Set(right)
  return left.every((roleId) => rightSet.has(roleId))
}
