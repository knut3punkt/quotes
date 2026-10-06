import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

/**
 * A selection of rows that may not all be loaded, kept as a small summary per row with what bulk actions need to
 * decide on. Cleared whenever `resetKey` changes (callers pass their memoized filters), so the selection is always a
 * subset of the rows matching the current filters.
 */
export function useBulkSelection<S extends { id: number }>(resetKey: unknown) {
  const [selected, setSelected] = useState<ReadonlyMap<number, S>>(new Map())
  const [selectingAll, setSelectingAll] = useState(false)
  const [selectAllError, setSelectAllError] = useState<string | null>(null)
  // Bumped on reset, so a "select all" response for older filters is dropped.
  const generation = useRef(0)

  useEffect(() => {
    generation.current += 1
    setSelected(new Map())
    setSelectingAll(false)
    setSelectAllError(null)
  }, [resetKey])

  const toggle = useCallback((summary: S) => {
    setSelected((prev) => {
      const next = new Map(prev)
      if (next.has(summary.id)) next.delete(summary.id)
      else next.set(summary.id, summary)
      return next
    })
  }, [])

  /** Selects all of `summaries`, or deselects them all when every one is already selected. */
  const toggleMany = useCallback((summaries: S[]) => {
    setSelected((prev) => {
      const next = new Map(prev)
      if (summaries.every((summary) => prev.has(summary.id))) {
        for (const summary of summaries) next.delete(summary.id)
      } else {
        for (const summary of summaries) next.set(summary.id, summary)
      }
      return next
    })
  }, [])

  const selectAll = useCallback(async (fetchAll: () => Promise<S[]>) => {
    const current = generation.current
    setSelectingAll(true)
    setSelectAllError(null)
    try {
      const summaries = await fetchAll()
      if (current !== generation.current) return
      setSelected(new Map(summaries.map((summary) => [summary.id, summary])))
    } catch (err) {
      if (current === generation.current) setSelectAllError(err instanceof Error ? err.message : 'Select all failed')
    } finally {
      if (current === generation.current) setSelectingAll(false)
    }
  }, [])

  const remove = useCallback((ids: Iterable<number>) => {
    setSelected((prev) => {
      const next = new Map(prev)
      for (const id of ids) next.delete(id)
      return next
    })
  }, [])

  /** Refreshes the summary of a row that changed, if it is selected. */
  const updateIfSelected = useCallback((summary: S) => {
    setSelected((prev) => {
      if (!prev.has(summary.id)) return prev
      const next = new Map(prev)
      next.set(summary.id, summary)
      return next
    })
  }, [])

  const clear = useCallback(() => setSelected(new Map()), [])

  const selectedIds = useMemo(() => new Set(selected.keys()), [selected])
  const summaries = useMemo(() => Array.from(selected.values()), [selected])

  return {
    selectedIds,
    summaries,
    count: selected.size,
    selectingAll,
    selectAllError,
    toggle,
    toggleMany,
    selectAll,
    remove,
    updateIfSelected,
    clear,
  }
}
