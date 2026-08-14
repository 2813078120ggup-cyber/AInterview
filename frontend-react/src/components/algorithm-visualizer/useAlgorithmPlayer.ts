import { useEffect, useState } from 'react'

export function useAlgorithmPlayer(stepCount: number, runKey: string | number) {
  const [currentStep, setCurrentStep] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [speed, setSpeed] = useState(1)

  useEffect(() => {
    setCurrentStep(0)
    setPlaying(false)
  }, [runKey, stepCount])

  useEffect(() => {
    if (!playing || stepCount <= 1) return
    if (currentStep >= stepCount - 1) {
      setPlaying(false)
      return
    }
    const timer = window.setTimeout(() => setCurrentStep(step => Math.min(step + 1, stepCount - 1)), 900 / speed)
    return () => window.clearTimeout(timer)
  }, [currentStep, playing, speed, stepCount])

  function first() {
    setPlaying(false)
    setCurrentStep(0)
  }

  function previous() {
    setPlaying(false)
    setCurrentStep(step => Math.max(0, step - 1))
  }

  function next() {
    setPlaying(false)
    setCurrentStep(step => Math.min(stepCount - 1, step + 1))
  }

  function last() {
    setPlaying(false)
    setCurrentStep(Math.max(0, stepCount - 1))
  }

  function togglePlaying() {
    if (currentStep >= stepCount - 1) setCurrentStep(0)
    setPlaying(value => !value)
  }

  return { currentStep, playing, speed, setSpeed, setCurrentStep, setPlaying, first, previous, next, last, togglePlaying }
}
