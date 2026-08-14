import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { buttonClassName, inferredButtonSize, type ButtonSize, type ButtonVariant } from '@/components/ui/button-styles'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: ButtonSize
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button({ className, variant = 'primary', size, ...props }, ref) {
  const resolvedSize = size ?? inferredButtonSize(className)
  return <button
    ref={ref}
    data-ui="button"
    data-size={resolvedSize}
    className={buttonClassName({ variant, size: resolvedSize, className })}
    {...props}
  />
})
