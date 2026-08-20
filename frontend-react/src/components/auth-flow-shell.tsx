import { Check } from 'lucide-react'
import type { ReactNode } from 'react'
import { AuthBrandBackground } from '@/components/auth-brand-background'
import { AuthTopBar } from '@/components/auth-top-bar'

type AuthFlowShellProps = {
  title: string
  description: string
  steps: readonly string[]
  currentStep: number
  children: ReactNode
}

export function AuthFlowShell({ title, description, steps, currentStep, children }: AuthFlowShellProps) {
  return <main className="auth-flow-shell min-h-dvh text-foreground">
    <AuthBrandBackground />
    <AuthTopBar sectionLabel={title} />

    <section className="auth-flow-content mx-auto w-full max-w-3xl px-4 pb-16 pt-10 sm:px-6 sm:pt-14">
      <div className="text-center">
        <h1 className="text-3xl font-black tracking-[-0.045em] sm:text-[2.25rem]">{title}</h1>
        <p className="mx-auto mt-3 max-w-[42ch] text-sm leading-6 text-muted-foreground sm:text-base">{description}</p>
      </div>

      <ol className="auth-flow-steps relative mx-auto mt-9 grid max-w-2xl gap-2" style={{ gridTemplateColumns: `repeat(${steps.length}, minmax(0, 1fr))` }} aria-label={`${title}进度`}>
        {steps.map((step, index) => {
          const stepNumber = index + 1
          const completed = stepNumber < currentStep
          const active = stepNumber === currentStep
          return <li key={step} className={`auth-flow-step relative z-[1] flex min-w-0 flex-col items-center text-center ${completed ? 'is-complete' : active ? 'is-active' : ''}`} aria-current={active ? 'step' : undefined}>
            <span className="auth-flow-step-marker grid h-9 w-9 place-items-center rounded-full border text-sm font-bold" aria-hidden>
              {completed ? <Check className="h-4 w-4" /> : stepNumber}
            </span>
            <span className="mt-2 max-w-full text-xs font-semibold leading-5 sm:text-sm">{step}</span>
          </li>
        })}
      </ol>

      <div key={currentStep} className="auth-flow-panel auth-flow-panel-enter mx-auto mt-10 w-full max-w-xl rounded-[28px] border border-border bg-surface p-5 sm:p-8">
        {children}
      </div>
    </section>
  </main>
}
