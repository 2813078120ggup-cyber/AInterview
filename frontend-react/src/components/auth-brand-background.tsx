import { useEffect, useRef } from 'react'
import './auth-brand-background.css'

/**
 * Decorative layer for the public authentication screens.
 *
 * It deliberately owns no product state: the artwork sits behind the auth
 * form and communicates the relationship between a candidate and an
 * interviewer without participating in the authentication flow.
 */
export function AuthBrandBackground() {
  const rootRef = useRef<HTMLDivElement>(null)
  const svgRef = useRef<SVGSVGElement>(null)

  useEffect(() => {
    const root = rootRef.current
    if (!root) return

    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
    const desktopPointer = window.matchMedia('(min-width: 1024px) and (hover: hover) and (pointer: fine)')
    const svg = svgRef.current

    let targetX = 0
    let targetY = 0
    let currentX = 0
    let currentY = 0
    let frame = 0

    const writeVariables = () => {
      root.style.setProperty('--auth-pointer-far-x', `${(currentX * 2).toFixed(2)}px`)
      root.style.setProperty('--auth-pointer-far-y', `${(currentY * 2).toFixed(2)}px`)
      root.style.setProperty('--auth-pointer-mid-x', `${(currentX * 3.5).toFixed(2)}px`)
      root.style.setProperty('--auth-pointer-mid-y', `${(currentY * 3.5).toFixed(2)}px`)
      root.style.setProperty('--auth-pointer-near-x', `${(currentX * 5.5).toFixed(2)}px`)
      root.style.setProperty('--auth-pointer-near-y', `${(currentY * 5.5).toFixed(2)}px`)
    }

    const settle = () => {
      currentX += (targetX - currentX) * 0.09
      currentY += (targetY - currentY) * 0.09
      writeVariables()

      if (Math.abs(targetX - currentX) > 0.01 || Math.abs(targetY - currentY) > 0.01) {
        frame = window.requestAnimationFrame(settle)
      } else {
        currentX = targetX
        currentY = targetY
        writeVariables()
        frame = 0
      }
    }

    const requestSettle = () => {
      if (!frame) frame = window.requestAnimationFrame(settle)
    }

    const syncSvgAnimation = () => {
      if (reducedMotion.matches) svg?.pauseAnimations()
      else svg?.unpauseAnimations()
    }

    const handlePointerMove = (event: PointerEvent) => {
      if (reducedMotion.matches) return
      const bounds = root.getBoundingClientRect()
      if (!bounds.width || !bounds.height) return
      targetX = Math.max(-1, Math.min(1, ((event.clientX - bounds.left) / bounds.width - 0.5) * 2))
      targetY = Math.max(-1, Math.min(1, ((event.clientY - bounds.top) / bounds.height - 0.5) * 2))
      requestSettle()
    }

    const resetPointer = () => {
      targetX = 0
      targetY = 0
      requestSettle()
    }

    const handlePointerOut = (event: PointerEvent) => {
      if (!event.relatedTarget) resetPointer()
    }

    const handleMotionChange = () => {
      syncSvgAnimation()
      if (reducedMotion.matches) resetPointer()
    }

    syncSvgAnimation()
    reducedMotion.addEventListener('change', handleMotionChange)

    if (reducedMotion.matches || !desktopPointer.matches) {
      return () => {
        reducedMotion.removeEventListener('change', handleMotionChange)
        if (frame) window.cancelAnimationFrame(frame)
      }
    }

    /* The root intentionally has pointer-events:none, so listen at window
     * level and translate the viewport coordinates into the same tiny shifts. */
    window.addEventListener('pointermove', handlePointerMove, { passive: true })
    window.addEventListener('pointerout', handlePointerOut, { passive: true })
    window.addEventListener('blur', resetPointer, { passive: true })

    return () => {
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerout', handlePointerOut)
      window.removeEventListener('blur', resetPointer)
      reducedMotion.removeEventListener('change', handleMotionChange)
      if (frame) window.cancelAnimationFrame(frame)
      root.style.removeProperty('--auth-pointer-far-x')
      root.style.removeProperty('--auth-pointer-far-y')
      root.style.removeProperty('--auth-pointer-mid-x')
      root.style.removeProperty('--auth-pointer-mid-y')
      root.style.removeProperty('--auth-pointer-near-x')
      root.style.removeProperty('--auth-pointer-near-y')
    }
  }, [])

  return <div ref={rootRef} className="auth-brand-background" data-testid="auth-brand-background" aria-hidden="true">
    <div className="auth-brand-background__image-frame">
      <img
        className="auth-brand-background__image"
        src="/auth/ainterview-geometric-background.png"
        alt=""
        width="1672"
        height="941"
        loading="eager"
        decoding="async"
        fetchPriority="high"
        draggable="false"
      />
    </div>
    <div className="auth-brand-background__wash" />

    <div className="auth-brand-background__layer auth-brand-background__layer--far">
      <div className="auth-brand-background__plane auth-brand-background__plane--far" />
    </div>
    <div className="auth-brand-background__layer auth-brand-background__layer--mid">
      <div className="auth-brand-background__plane auth-brand-background__plane--mid" />
    </div>
    <div className="auth-brand-background__layer auth-brand-background__layer--near">
      <div className="auth-brand-background__plane auth-brand-background__plane--near" />
    </div>

    <svg ref={svgRef} className="auth-brand-background__svg" viewBox="0 0 1672 941" preserveAspectRatio="xMidYMid slice">
      <g className="auth-brand-background__connection-lines">
        <path className="auth-brand-background__connection auth-brand-background__connection--forward" pathLength="1" d="M 203 731 C 286 722 350 672 410 609 C 480 530 536 470 608 420" />
        <path className="auth-brand-background__connection auth-brand-background__connection--return" pathLength="1" d="M 610 420 C 664 488 654 562 527 685 C 440 770 347 799 204 843" />
      </g>

      <g className="auth-brand-background__flow-dots">
        <circle className="auth-brand-background__flow-dot auth-brand-background__flow-dot--forward" r="4">
          <animateMotion dur="10s" repeatCount="indefinite" begin="1.1s" path="M 203 731 C 286 722 350 672 410 609 C 480 530 536 470 608 420" />
        </circle>
        <circle className="auth-brand-background__flow-dot auth-brand-background__flow-dot--return" r="3.5">
          <animateMotion dur="11s" repeatCount="indefinite" begin="4s" path="M 610 420 C 664 488 654 562 527 685 C 440 770 347 799 204 843" />
        </circle>
      </g>

      <g className="auth-brand-background__assessment-rings">
        <circle className="auth-brand-background__ring auth-brand-background__ring--outer" cx="608" cy="420" r="68" />
        <circle className="auth-brand-background__ring auth-brand-background__ring--middle" cx="608" cy="420" r="50" />
        <circle className="auth-brand-background__ring auth-brand-background__ring--inner" cx="608" cy="420" r="29" />
        <circle className="auth-brand-background__ring-dot" cx="608" cy="420" r="5" />
      </g>

      <g className="auth-brand-background__assessment-nodes">
        <circle className="auth-brand-background__assessment-node auth-brand-background__assessment-node--one" cx="203" cy="731" r="11" />
        <circle className="auth-brand-background__assessment-node auth-brand-background__assessment-node--two" cx="410" cy="609" r="10" />
        <circle className="auth-brand-background__assessment-node auth-brand-background__assessment-node--three" cx="527" cy="685" r="9" />
        <circle className="auth-brand-background__assessment-node auth-brand-background__assessment-node--four" cx="608" cy="420" r="10" />
        <circle className="auth-brand-background__assessment-node auth-brand-background__assessment-node--five" cx="506" cy="510" r="9" />
      </g>

      <g className="auth-brand-background__growth-steps">
        <path className="auth-brand-background__growth-step auth-brand-background__growth-step--one" d="M 92 889 H 304 V 842 H 92 Z" />
        <path className="auth-brand-background__growth-step auth-brand-background__growth-step--two" d="M 304 842 H 454 V 794 H 304 Z" />
        <path className="auth-brand-background__growth-step auth-brand-background__growth-step--three" d="M 454 794 H 594 V 744 H 454 Z" />
        <path className="auth-brand-background__growth-step auth-brand-background__growth-step--four" d="M 594 744 H 730 V 692 H 594 Z" />
      </g>

      <g className="auth-brand-background__quiet-mark">
        <path d="M 210 818 L 496 256 L 786 818" />
        <path d="M 354 535 H 642" />
      </g>
    </svg>
  </div>
}
