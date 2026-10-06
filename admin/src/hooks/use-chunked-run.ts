import { useCallback, useRef, useState } from 'react'

export interface ChunkedRunProgress {
  label: string
  done: number
  total: number
  cancelling: boolean
}

export interface ChunkedRunResult<R> {
  results: R[]
  /** Items in batches whose request failed outright. */
  failedItems: number
  /** Items not attempted because the run was cancelled. */
  cancelledItems: number
}

/**
 * Runs a long bulk action as a sequence of small requests, so a large selection neither times out nor blocks the
 * server for minutes, and reports progress between batches. Cancelling stops after the batch in flight.
 */
export function useChunkedRun() {
  const [progress, setProgress] = useState<ChunkedRunProgress | null>(null)
  const cancelRequested = useRef(false)

  const run = useCallback(async function runChunks<T, R>(
    label: string,
    items: T[],
    chunkSize: number,
    worker: (chunk: T[]) => Promise<R[]>,
  ): Promise<ChunkedRunResult<R>> {
    cancelRequested.current = false
    const results: R[] = []
    let failedItems = 0
    setProgress({ label, done: 0, total: items.length, cancelling: false })
    try {
      for (let start = 0; start < items.length; start += chunkSize) {
        if (cancelRequested.current) {
          return { results, failedItems, cancelledItems: items.length - start }
        }
        const chunk = items.slice(start, start + chunkSize)
        try {
          results.push(...(await worker(chunk)))
        } catch {
          failedItems += chunk.length
        }
        const done = Math.min(start + chunkSize, items.length)
        setProgress((prev) => (prev ? { ...prev, done } : prev))
      }
      return { results, failedItems, cancelledItems: 0 }
    } finally {
      setProgress(null)
    }
  }, [])

  const cancel = useCallback(() => {
    cancelRequested.current = true
    setProgress((prev) => (prev ? { ...prev, cancelling: true } : prev))
  }, [])

  return { progress, running: progress !== null, run, cancel }
}
