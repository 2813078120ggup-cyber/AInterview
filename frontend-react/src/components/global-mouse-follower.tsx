import { motion, useMotionValue, useReducedMotion, useSpring } from 'framer-motion'
import { useEffect, useState } from 'react'

export function GlobalMouseFollower() {
  const reduceMotion = useReducedMotion()
  const [visible, setVisible] = useState(false)
  const [pressed, setPressed] = useState(false)
  const cursorX = useMotionValue(-120)
  const cursorY = useMotionValue(-120)
  const haloX = useSpring(cursorX, { stiffness: 170, damping: 25, mass: 0.22 })
  const haloY = useSpring(cursorY, { stiffness: 170, damping: 25, mass: 0.22 })

  useEffect(() => {
    if (reduceMotion) return

    const update = (event: PointerEvent) => {
      if (event.pointerType !== 'mouse') return
      cursorX.set(event.clientX)
      cursorY.set(event.clientY)
      setVisible(true)
    }

    const hide = () => setVisible(false)
    const press = (event: PointerEvent) => {
      if (event.pointerType === 'mouse') setPressed(true)
    }
    const release = () => setPressed(false)

    window.addEventListener('pointermove', update, { passive: true })
    window.addEventListener('pointerdown', press, { passive: true })
    window.addEventListener('pointerup', release, { passive: true })
    window.addEventListener('pointercancel', release)
    window.addEventListener('pointerleave', hide)
    window.addEventListener('blur', hide)
    return () => {
      window.removeEventListener('pointermove', update)
      window.removeEventListener('pointerdown', press)
      window.removeEventListener('pointerup', release)
      window.removeEventListener('pointercancel', release)
      window.removeEventListener('pointerleave', hide)
      window.removeEventListener('blur', hide)
    }
  }, [cursorX, cursorY, reduceMotion])

  if (reduceMotion) return null

  return (
    <>
      <motion.div
        aria-hidden
        className="global-mouse-halo pointer-events-none fixed left-0 top-0 z-[2147483000] hidden rounded-full lg:block"
        style={{ x: haloX, y: haloY, translateX: '-50%', translateY: '-50%', opacity: visible ? 1 : 0 }}
        animate={{ scale: pressed ? 0.72 : 1 }}
        transition={{ type: 'spring', stiffness: 520, damping: 28, mass: 0.25 }}
      />
      <motion.div
        aria-hidden
        className="global-mouse-dot pointer-events-none fixed left-0 top-0 z-[2147483001] hidden rounded-full lg:block"
        style={{ x: cursorX, y: cursorY, translateX: '-50%', translateY: '-50%', opacity: visible ? 1 : 0 }}
        animate={{ scale: pressed ? 1.45 : 1 }}
        transition={{ type: 'spring', stiffness: 620, damping: 22, mass: 0.2 }}
      />
    </>
  )
}
