import { type ReactNode } from 'react'
import { WorkspaceShell } from '@/components/workspace-shell'

/** Candidate-specific compatibility wrapper around the shared workspace shell. */
export function CandidatePageShell({ children }: { children: ReactNode }) {
  return <WorkspaceShell audience="candidate">{children}</WorkspaceShell>
}
