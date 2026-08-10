import { useCallback, useEffect, useMemo, useState } from 'react'
import './App.css'
import { approveImportedQuote, fetchAuthors, fetchImportedQuotes, fetchSources, updateImportedQuoteStatus } from './api'
import { ApproveDialog } from './components/ApproveDialog'
import { FilterBar } from './components/FilterBar'
import { ImportedQuotesTable } from './components/ImportedQuotesTable'
import { SelectionBar } from './components/SelectionBar'
import { canApprove, canMarkDuplicate, canReject, canResetToPending } from './statusRules'
import type { ApproveImportedQuoteRequest, Author, ImportedQuote, ProcessingStatus, Source, SourceConfidence } from './types'

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
}

function App() {
  const [importedQuotes, setImportedQuotes] = useState<ImportedQuote[]>([])
  const [authors, setAuthors] = useState<Author[]>([])
  const [sources, setSources] = useState<Source[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [selectedStatuses, setSelectedStatuses] = useState<Set<ProcessingStatus>>(new Set(['pending']))
  const [confidence, setConfidence] = useState<SourceConfidence | 'all'>('all')
  const [provider, setProvider] = useState('all')
  const [search, setSearch] = useState('')

  const [approveTarget, setApproveTarget] = useState<ImportedQuote | null>(null)
  const [approveSubmitting, setApproveSubmitting] = useState(false)
  const [approveError, setApproveError] = useState<string | null>(null)

  const [busyId, setBusyId] = useState<number | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  const [bulkBusy, setBulkBusy] = useState(false)

  const loadAll = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    try {
      const [quotes, authorList, sourceList] = await Promise.all([
        fetchImportedQuotes(),
        fetchAuthors(),
        fetchSources(),
      ])
      setImportedQuotes(quotes)
      setAuthors(authorList)
      setSources(sourceList)
    } catch (err) {
      setLoadError(errorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadAll()
  }, [loadAll])

  const statusCounts = useMemo(() => {
    const counts: Record<ProcessingStatus, number> = { pending: 0, approved: 0, rejected: 0, duplicate: 0 }
    for (const quote of importedQuotes) counts[quote.processingStatus] += 1
    return counts
  }, [importedQuotes])

  const providers = useMemo(
    () => Array.from(new Set(importedQuotes.map((quote) => quote.provider))).sort(),
    [importedQuotes],
  )

  const filteredQuotes = useMemo(() => {
    const term = search.trim().toLowerCase()
    return importedQuotes.filter((quote) => {
      if (!selectedStatuses.has(quote.processingStatus)) return false
      if (confidence !== 'all' && quote.sourceConfidence !== confidence) return false
      if (provider !== 'all' && quote.provider !== provider) return false
      if (term) {
        const haystack = `${quote.rawText} ${quote.rawAuthor ?? ''}`.toLowerCase()
        if (!haystack.includes(term)) return false
      }
      return true
    })
  }, [importedQuotes, selectedStatuses, confidence, provider, search])

  const toggleStatus = (status: ProcessingStatus) => {
    setSelectedStatuses((prev) => {
      const next = new Set(prev)
      if (next.has(status)) next.delete(status)
      else next.add(status)
      return next
    })
  }

  const selectedQuotes = useMemo(
    () => importedQuotes.filter((quote) => selectedIds.has(quote.id)),
    [importedQuotes, selectedIds],
  )

  const allVisibleSelected = filteredQuotes.length > 0 && filteredQuotes.every((quote) => selectedIds.has(quote.id))

  const toggleSelect = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const toggleSelectAllVisible = () => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (allVisibleSelected) {
        for (const quote of filteredQuotes) next.delete(quote.id)
      } else {
        for (const quote of filteredQuotes) next.add(quote.id)
      }
      return next
    })
  }

  const clearSelection = () => setSelectedIds(new Set())

  const bulkApproveTargets = useMemo(
    () => selectedQuotes.filter((quote) => canApprove(quote.processingStatus) && quote.rawAuthor?.trim()),
    [selectedQuotes],
  )
  const bulkApproveSkipped = useMemo(
    () => selectedQuotes.filter((quote) => canApprove(quote.processingStatus) && !quote.rawAuthor?.trim()).length,
    [selectedQuotes],
  )
  const bulkRejectTargets = useMemo(
    () => selectedQuotes.filter((quote) => canReject(quote.processingStatus)),
    [selectedQuotes],
  )
  const bulkDuplicateTargets = useMemo(
    () => selectedQuotes.filter((quote) => canMarkDuplicate(quote.processingStatus)),
    [selectedQuotes],
  )
  const bulkResetTargets = useMemo(
    () => selectedQuotes.filter((quote) => canResetToPending(quote.processingStatus)),
    [selectedQuotes],
  )

  const runBulkStatusAction = async (status: ProcessingStatus, targets: ImportedQuote[]) => {
    if (targets.length === 0) return
    setBulkBusy(true)
    setActionError(null)
    const results = await Promise.allSettled(targets.map((quote) => updateImportedQuoteStatus(quote.id, status)))
    const updates = new Map<number, ImportedQuote>()
    let failures = 0
    for (const result of results) {
      if (result.status === 'fulfilled') updates.set(result.value.id, result.value)
      else failures += 1
    }
    setImportedQuotes((prev) => prev.map((existing) => updates.get(existing.id) ?? existing))
    setSelectedIds((prev) => {
      const next = new Set(prev)
      for (const id of updates.keys()) next.delete(id)
      return next
    })
    if (failures > 0) setActionError(`${failures} of ${targets.length} updates failed`)
    setBulkBusy(false)
  }

  const handleBulkApprove = async () => {
    if (bulkApproveTargets.length === 0) return
    setBulkBusy(true)
    setActionError(null)
    const authorIdByName = new Map(authors.map((author) => [author.name.toLowerCase(), author.id]))
    const approvedIds: number[] = []
    let failures = 0
    for (const quote of bulkApproveTargets) {
      const key = quote.rawAuthor!.trim().toLowerCase()
      const existingAuthorId = authorIdByName.get(key)
      try {
        const result = await approveImportedQuote(quote.id, {
          text: quote.rawText,
          authorId: existingAuthorId,
          newAuthorName: existingAuthorId ? undefined : quote.rawAuthor!.trim(),
        })
        authorIdByName.set(key, result.authorId)
        approvedIds.push(quote.id)
      } catch {
        failures += 1
      }
    }
    setSelectedIds((prev) => {
      const next = new Set(prev)
      for (const id of approvedIds) next.delete(id)
      return next
    })
    if (failures > 0) setActionError(`${failures} of ${bulkApproveTargets.length} approvals failed`)
    await loadAll()
    setBulkBusy(false)
  }

  const runStatusAction = async (quote: ImportedQuote, status: ProcessingStatus) => {
    setBusyId(quote.id)
    setActionError(null)
    try {
      const updated = await updateImportedQuoteStatus(quote.id, status)
      setImportedQuotes((prev) => prev.map((existing) => (existing.id === updated.id ? updated : existing)))
    } catch (err) {
      setActionError(errorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  const handleApproveSubmit = async (request: ApproveImportedQuoteRequest) => {
    if (!approveTarget) return
    setApproveSubmitting(true)
    setApproveError(null)
    try {
      await approveImportedQuote(approveTarget.id, request)
      setApproveTarget(null)
      await loadAll()
    } catch (err) {
      setApproveError(errorMessage(err))
    } finally {
      setApproveSubmitting(false)
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Imported quotes</h1>
        <p>Review staged imports and promote them into the quote library.</p>
      </header>

      {loadError && <div className="banner banner-error">{loadError}</div>}
      {actionError && <div className="banner banner-error">{actionError}</div>}

      <FilterBar
        statusCounts={statusCounts}
        selectedStatuses={selectedStatuses}
        onToggleStatus={toggleStatus}
        confidence={confidence}
        onConfidenceChange={setConfidence}
        providers={providers}
        provider={provider}
        onProviderChange={setProvider}
        search={search}
        onSearchChange={setSearch}
      />

      {!loading && (
        <SelectionBar
          selectedCount={selectedIds.size}
          visibleCount={filteredQuotes.length}
          allVisibleSelected={allVisibleSelected}
          bulkBusy={bulkBusy}
          onToggleSelectAllVisible={toggleSelectAllVisible}
          onClearSelection={clearSelection}
          approveCount={bulkApproveTargets.length}
          approveSkippedCount={bulkApproveSkipped}
          rejectCount={bulkRejectTargets.length}
          duplicateCount={bulkDuplicateTargets.length}
          resetCount={bulkResetTargets.length}
          onBulkApprove={handleBulkApprove}
          onBulkReject={() => runBulkStatusAction('rejected', bulkRejectTargets)}
          onBulkMarkDuplicate={() => runBulkStatusAction('duplicate', bulkDuplicateTargets)}
          onBulkResetToPending={() => runBulkStatusAction('pending', bulkResetTargets)}
        />
      )}

      {loading ? (
        <p className="status-message">Loading…</p>
      ) : (
        <ImportedQuotesTable
          quotes={filteredQuotes}
          busyId={busyId}
          bulkBusy={bulkBusy}
          selectedIds={selectedIds}
          allVisibleSelected={allVisibleSelected}
          onToggleSelect={toggleSelect}
          onToggleSelectAllVisible={toggleSelectAllVisible}
          onApprove={setApproveTarget}
          onReject={(quote) => runStatusAction(quote, 'rejected')}
          onMarkDuplicate={(quote) => runStatusAction(quote, 'duplicate')}
          onResetToPending={(quote) => runStatusAction(quote, 'pending')}
        />
      )}

      {approveTarget && (
        <ApproveDialog
          quote={approveTarget}
          authors={authors}
          sources={sources}
          submitting={approveSubmitting}
          error={approveError}
          onCancel={() => {
            setApproveTarget(null)
            setApproveError(null)
          }}
          onSubmit={handleApproveSubmit}
        />
      )}
    </div>
  )
}

export default App
