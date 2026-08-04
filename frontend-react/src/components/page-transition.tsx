import { AnimatePresence, motion } from 'framer-motion'
import type { ReactNode } from 'react'
import { useLocation } from 'react-router-dom'

export function PageTransition({ children }: { children: ReactNode }) {
  const location = useLocation()
  return <AnimatePresence mode="wait">
    <motion.div
      key={location.pathname}
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -6 }}
      transition={{ duration: .24, ease: 'easeOut' }}
    >
      {children}
    </motion.div>
  </AnimatePresence>
}
