import { useCallback, useEffect, useRef, useState } from 'react'

export interface Page<T> {
  items: T[]
  total: number
}

const DEFAULT_PAGE_SIZE = 100
// Matches the server's cap on pageSize.
const MAX_PAGE_SIZE = 2000

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
}

/**
 * Rows of a server-filtered list, fetched a page at a time as the table asks for more. Starts again from the first
 * page whenever `filters` changes (callers memoize it), and ignores responses for filters that are no longer current.
 * `fetchPage` must be stable.
 */
export function usePagedList<T, F>({
  fetchPage,
  filters,
  getId,
  pageSize = DEFAULT_PAGE_SIZE,
}: {
  fetchPage: (filters: F, page: number, pageSize: number) => Promise<Page<T>>
  filters: F
  getId: (row: T) => number
  pageSize?: number
}) {
  const [rows, setRows] = useState<T[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Bumped by every fresh fetch, so a response for an older one can tell it is stale.
  const generation = useRef(0)

  useEffect(() => {
    const current = ++generation.current
    setLoading(true)
    setLoadingMore(false)
    setError(null)
    fetchPage(filters, 1, pageSize)
      .then((page) => {
        if (current !== generation.current) return
        setRows(page.items)
        setTotal(page.total)
      })
      .catch((err) => {
        if (current !== generation.current) return
        setRows([])
        setTotal(0)
        setError(errorMessage(err))
      })
      .finally(() => {
        if (current === generation.current) setLoading(false)
      })
  }, [fetchPage, filters, pageSize])

  const hasMore = rows.length < total

  const loadMore = useCallback(() => {
    if (loading || loadingMore || !hasMore) return
    const current = generation.current
    setLoadingMore(true)
    // After a reload the row count may not be a whole number of pages; re-fetching an overlap is harmless since
    // rows already present are skipped, and it can never leave a gap.
    fetchPage(filters, Math.floor(rows.length / pageSize) + 1, pageSize)
      .then((page) => {
        if (current !== generation.current) return
        setRows((prev) => {
          const present = new Set(prev.map(getId))
          return [...prev, ...page.items.filter((row) => !present.has(getId(row)))]
        })
        setTotal(page.total)
      })
      .catch((err) => {
        if (current === generation.current) setError(errorMessage(err))
      })
      .finally(() => {
        if (current === generation.current) setLoadingMore(false)
      })
  }, [loading, loadingMore, hasMore, fetchPage, filters, rows.length, pageSize, getId])

  /** Re-fetches as many rows as are loaded now, in one request, so the table keeps its scroll position. */
  const reload = useCallback(() => {
    const current = ++generation.current
    setLoadingMore(false)
    const size = Math.min(MAX_PAGE_SIZE, Math.max(pageSize, Math.ceil(rows.length / pageSize) * pageSize))
    fetchPage(filters, 1, size)
      .then((page) => {
        if (current !== generation.current) return
        setRows(page.items)
        setTotal(page.total)
        setError(null)
      })
      .catch((err) => {
        if (current === generation.current) setError(errorMessage(err))
      })
  }, [fetchPage, filters, rows.length, pageSize])

  return { rows, total, loading, loadingMore, error, hasMore, loadMore, reload, patchRows: setRows }
}
