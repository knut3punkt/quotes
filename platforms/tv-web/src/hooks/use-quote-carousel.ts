import { useCallback, useEffect, useState } from 'react'
import type { PublicQuote } from '../types'

const AUTO_ADVANCE_MS = 30_000

/** Steps through [quotes], wrapping around; every 30 s on its own while [autoAdvance] is set. */
export function useQuoteCarousel(quotes: PublicQuote[], autoAdvance: boolean = true) {
  const [currentIndex, setCurrentIndex] = useState(0)
  const count = quotes.length

  const next = useCallback(() => {
    if (count === 0) return
    setCurrentIndex((index) => (index + 1) % count)
  }, [count])

  const previous = useCallback(() => {
    if (count === 0) return
    setCurrentIndex((index) => (index - 1 + count) % count)
  }, [count])

  /** Back to the first quote, for a freshly loaded batch. */
  const restart = useCallback(() => setCurrentIndex(0), [])

  useEffect(() => {
    if (count === 0 || !autoAdvance) return
    // Restarts on every currentIndex change (auto-advance or manual nav), so a manual
    // next/previous always gets a fresh 30s window instead of being cut short by a near-due tick.
    const timer = setInterval(next, AUTO_ADVANCE_MS)
    return () => clearInterval(timer)
  }, [currentIndex, count, next, autoAdvance])

  return {
    currentIndex,
    currentQuote: count > 0 ? quotes[currentIndex] : undefined,
    next,
    previous,
    restart,
  }
}
