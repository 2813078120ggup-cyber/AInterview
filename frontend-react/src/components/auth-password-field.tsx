import { Eye, EyeOff } from 'lucide-react'
import { authFieldClass } from '@/lib/auth-form'

type AuthPasswordFieldProps = {
  id: string
  label: string
  placeholder: string
  value: string
  visible: boolean
  disabled?: boolean
  autoComplete?: 'current-password' | 'new-password'
  onChange: (value: string) => void
  onToggle: () => void
}

export function AuthPasswordField({ id, label, placeholder, value, visible, disabled = false, autoComplete = 'new-password', onChange, onToggle }: AuthPasswordFieldProps) {
  return <div>
    <label className="sr-only" htmlFor={id}>{label}</label>
    <div className="relative">
      <input id={id} type={visible ? 'text' : 'password'} value={value} onChange={event => onChange(event.target.value)} className={`${authFieldClass} pr-12`} placeholder={placeholder} autoComplete={autoComplete} minLength={8} maxLength={64} aria-label={label} disabled={disabled} required />
      <button type="button" onClick={onToggle} disabled={disabled} className="absolute right-2 top-1/2 grid h-8 w-8 -translate-y-1/2 place-items-center rounded-full text-muted-foreground transition hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--brand)]" aria-label={visible ? `隐藏${label}` : `显示${label}`}>
        {visible ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
      </button>
    </div>
  </div>
}
