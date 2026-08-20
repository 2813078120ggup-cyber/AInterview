export type WorkspaceAudience = 'candidate' | 'company' | 'admin'

const companyRoles = new Set(['COMPANY_ADMIN', 'COMPANY_RECRUITER', 'COMPANY_INTERVIEWER'])
const candidateRoles = new Set(['CANDIDATE'])
const adminRoles = new Set(['ADMIN'])

export const workspaceHomes: Record<WorkspaceAudience, string> = {
  candidate: '/workspace',
  company: '/company',
  admin: '/admin/workspace',
}

export function workspaceAudienceFor(roles: readonly unknown[] = []): WorkspaceAudience | null {
  if (!Array.isArray(roles) || !roles.length || roles.some(role => typeof role !== 'string')) return null
  const normalizedRoles = roles.map(role => role.trim().toUpperCase())
  if (normalizedRoles.some(role => !role)) return null
  const normalized = [...new Set(normalizedRoles)]

  const hasAdmin = normalized.some(role => adminRoles.has(role))
  const hasCompany = normalized.some(role => companyRoles.has(role))
  const hasCandidate = normalized.some(role => candidateRoles.has(role))
  // Platform administrators may carry auxiliary platform duties (for
  // example HR, INTERVIEWER, or a future internal role). They must never be
  // combined with a candidate or company-domain role.
  if (hasAdmin) return hasCompany || hasCandidate ? null : 'admin'
  // Company and candidate identities are deliberately closed sets. This
  // keeps legacy HR/INTERVIEWER/unknown roles from silently entering either
  // business workspace and prevents cross-domain role combinations.
  if (hasCompany) return normalized.every(role => companyRoles.has(role)) ? 'company' : null
  if (hasCandidate) return normalized.every(role => candidateRoles.has(role)) ? 'candidate' : null
  return null
}

export function workspaceHomeFor(roles: string[]) {
  const audience = workspaceAudienceFor(roles)
  return audience ? workspaceHomes[audience] : '/login'
}

function internalPath(value: string | null | undefined) {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return null
  try {
    const parsed = new URL(value, 'https://ainterview.local')
    return parsed.origin === 'https://ainterview.local' ? `${parsed.pathname}${parsed.search}${parsed.hash}` : null
  } catch {
    return null
  }
}

export function destinationBelongsTo(audience: WorkspaceAudience, destination: string) {
  const pathname = new URL(destination, 'https://ainterview.local').pathname
  const isPath = (prefix: string) => pathname === prefix || pathname.startsWith(`${prefix}/`)
  if (audience === 'admin') return pathname === '/admin' || pathname.startsWith('/admin/')
  if (audience === 'company') return pathname === '/company' || pathname.startsWith('/company/')
  return pathname === '/workspace'
    || pathname === '/jobs'
    || pathname === '/applications'
    || pathname === '/resumes'
    || pathname === '/reports'
    || pathname === '/library'
    || pathname === '/interviews'
    || pathname === '/users'
    || isPath('/candidate')
    || isPath('/algorithm')
    || isPath('/learning-resources')
}

export function postLoginDestination(roles: string[], requested?: string | null) {
  const audience = workspaceAudienceFor(roles)
  if (!audience) return '/login'
  const destination = internalPath(requested)
  return destination && destinationBelongsTo(audience, destination) ? destination : workspaceHomes[audience]
}

export function loginPath(requested?: string | null) {
  const destination = internalPath(requested)
  return destination ? `/login?next=${encodeURIComponent(destination)}` : '/login'
}
