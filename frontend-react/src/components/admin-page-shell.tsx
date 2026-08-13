import { type ReactNode } from 'react'
import { WorkspaceShell } from '@/components/workspace-shell'

/** Admin-specific compatibility wrapper around the shared workspace shell. */
export function AdminPageShell({ children }: { children: ReactNode }) {
  return <WorkspaceShell audience="admin" showPageHeader={false}>{children}</WorkspaceShell>
}
