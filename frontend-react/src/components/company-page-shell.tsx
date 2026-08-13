import { type ReactNode } from 'react'
import { WorkspaceShell } from '@/components/workspace-shell'

/** Company-specific compatibility wrapper around the shared workspace shell. */
export function CompanyPageShell({ children }: { children: ReactNode }) {
  return <WorkspaceShell audience="company">{children}</WorkspaceShell>
}
