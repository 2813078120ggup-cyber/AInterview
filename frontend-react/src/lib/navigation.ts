export type WorkspaceAudience = 'candidate' | 'company' | 'admin'

const companyRoles = new Set(['COMPANY_ADMIN', 'COMPANY_RECRUITER', 'COMPANY_INTERVIEWER'])

export const workspaceHomes: Record<WorkspaceAudience, string> = {
  candidate: '/workspace',
  company: '/company',
  admin: '/admin/workspace',
}

export function workspaceAudienceFor(roles: string[]): WorkspaceAudience {
  if (roles.includes('ADMIN')) return 'admin'
  if (roles.some(role => companyRoles.has(role))) return 'company'
  return 'candidate'
}

export function workspaceHomeFor(roles: string[]) {
  return workspaceHomes[workspaceAudienceFor(roles)]
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

function destinationBelongsTo(audience: WorkspaceAudience, destination: string) {
  const pathname = new URL(destination, 'https://ainterview.local').pathname
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
    || pathname.startsWith('/candidate/')
    || pathname.startsWith('/algorithm')
    || pathname.startsWith('/learning-resources')
}

export function postLoginDestination(roles: string[], requested?: string | null) {
  const audience = workspaceAudienceFor(roles)
  const destination = internalPath(requested)
  return destination && destinationBelongsTo(audience, destination) ? destination : workspaceHomes[audience]
}

export function loginPath(requested?: string | null) {
  const destination = internalPath(requested)
  return destination ? `/login?next=${encodeURIComponent(destination)}` : '/login'
}
