import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  approveImportedQuote,
  deleteImportedQuote,
  fetchAuthors,
  fetchImportedQuotes,
  fetchSources,
  fetchSourceTypes,
  updateImportedQuoteStatus,
} from './api'
import { ApproveDialog } from './components/ApproveDialog'
import { ConfirmDialog } from './components/ConfirmDialog'
import { FilterBar } from './components/FilterBar'
import { ImportedQuotesTable } from './components/ImportedQuotesTable'
import { ImportPage } from './components/ImportPage'
import { SelectionBar } from './components/SelectionBar'
import { canApprove, canDelete, canMarkDuplicate, canReject, canResetToPending } from './statusRules'
import type {
  ApproveImportedQuoteRequest,
  Author,
  ImportedQuote,
  LengthFilterOp,
  ProcessingStatus,
  Source,
  SourceConfidence,
  SourceType,
} from './types'

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
}

function truncateForDialog(text: string): string {
  return text.length > 120 ? `${text.slice(0, 120).trimEnd()}…` : text
}

function App() {
  const [page, setPage] = useState<'review' | 'import'>('review')

  const [importedQuotes, setImportedQuotes] = useState<ImportedQuote[]>([])
  const [authors, setAuthors] = useState<Author[]>([])
  const [sources, setSources] = useState<Source[]>([])
  const [sourceTypes, setSourceTypes] = useState<SourceType[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [selectedStatuses, setSelectedStatuses] = useState<Set<ProcessingStatus>>(new Set(['pending']))
  const [confidence, setConfidence] = useState<SourceConfidence | 'all'>('all')
  const [provider, setProvider] = useState('all')
  const [search, setSearch] = useState('')
  const [lengthOp, setLengthOp] = useState<LengthFilterOp>('above')
  const [lengthValue, setLengthValue] = useState('')

  const [approveTarget, setApproveTarget] = useState<ImportedQuote | null>(null)
  const [approveSubmitting, setApproveSubmitting] = useState(false)
  const [approveError, setApproveError] = useState<string | null>(null)

  const [busyId, setBusyId] = useState<number | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  const [bulkBusy, setBulkBusy] = useState(false)

  const [deleteRequest, setDeleteRequest] = useState<ImportedQuote[] | null>(null)
  const [deleteSubmitting, setDeleteSubmitting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const loadAll = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    try {
      const [quotes, authorList, sourceList, sourceTypeList] = await Promise.all([
        fetchImportedQuotes(),
        fetchAuthors(),
        fetchSources(),
        fetchSourceTypes(),
      ])
      setImportedQuotes(quotes)
      setAuthors(authorList)
      setSources(sourceList)
      setSourceTypes(sourceTypeList)
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
    const lengthThreshold = lengthValue.trim() === '' ? null : Number(lengthValue)
    const hasLengthFilter = lengthThreshold !== null && Number.isFinite(lengthThreshold) && lengthThreshold >= 0
    return importedQuotes.filter((quote) => {
      if (!selectedStatuses.has(quote.processingStatus)) return false
      if (confidence !== 'all' && quote.sourceConfidence !== confidence) return false
      if (provider !== 'all' && quote.provider !== provider) return false
      if (hasLengthFilter) {
        const length = quote.rawText.length
        if (lengthOp === 'above' && length <= lengthThreshold) return false
        if (lengthOp === 'below' && length >= lengthThreshold) return false
      }
      if (term) {
        const haystack = `${quote.rawText} ${quote.rawAuthor ?? ''}`.toLowerCase()
        if (!haystack.includes(term)) return false
      }
      return true
    })
  }, [importedQuotes, selectedStatuses, confidence, provider, lengthOp, lengthValue, search])

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
    () =>
      selectedQuotes.filter(
        (quote) => canApprove(quote.processingStatus) && (quote.rawAuthor?.trim() || quote.sourceId !== null),
      ),
    [selectedQuotes],
  )
  const bulkApproveSkipped = useMemo(
    () =>
      selectedQuotes.filter(
        (quote) => canApprove(quote.processingStatus) && !quote.rawAuthor?.trim() && quote.sourceId === null,
      ).length,
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
  const bulkDeleteTargets = useMemo(
    () => selectedQuotes.filter((quote) => canDelete(quote.processingStatus)),
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
    const approvedIds: number[] = []
    let failures = 0
    // Sequential, not Promise.allSettled: two quotes by the same not-yet-existing author
    // approved concurrently could both miss the "does this author exist" check server-side and
    // collide on authors.normalized_name's unique index.
    for (const quote of bulkApproveTargets) {
      try {
        await approveImportedQuote(quote.id, {})
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

  const requestDelete = (quote: ImportedQuote) => {
    setDeleteError(null)
    setDeleteRequest([quote])
  }

  const requestBulkDelete = () => {
    if (bulkDeleteTargets.length === 0) return
    setDeleteError(null)
    setDeleteRequest(bulkDeleteTargets)
  }

  const cancelDelete = () => {
    if (deleteSubmitting) return
    setDeleteRequest(null)
    setDeleteError(null)
  }

  const confirmDelete = async () => {
    if (!deleteRequest) return
    setDeleteSubmitting(true)
    setDeleteError(null)
    const results = await Promise.allSettled(deleteRequest.map((quote) => deleteImportedQuote(quote.id)))
    const deletedIds = new Set<number>()
    let failures = 0
    deleteRequest.forEach((quote, index) => {
      if (results[index].status === 'fulfilled') deletedIds.add(quote.id)
      else failures += 1
    })
    setImportedQuotes((prev) => prev.filter((quote) => !deletedIds.has(quote.id)))
    setSelectedIds((prev) => {
      const next = new Set(prev)
      for (const id of deletedIds) next.delete(id)
      return next
    })
    setDeleteSubmitting(false)
    if (failures > 0) {
      setDeleteError(`${failures} of ${deleteRequest.length} deletions failed`)
      setDeleteRequest((prev) => prev?.filter((quote) => !deletedIds.has(quote.id)) ?? null)
    } else {
      setDeleteRequest(null)
    }
  }

  return (
    <div className="mx-auto max-w-[1280px] px-8 pt-6 pb-16">
      <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="mb-1 text-[28px]">{page === 'review' ? 'Imported quotes' : 'Import quotes'}</h1>
          <p className="text-muted-foreground">
            {page === 'review'
              ? 'Review staged imports and promote them into the quote library.'
              : 'Stage quotes into the review queue from Wikiquote, scripture sources, or refresh author metadata.'}
          </p>
        </div>
        <div className="flex gap-2">
          <Button type="button" variant={page === 'review' ? 'default' : 'outline'} onClick={() => setPage('review')}>
            Review imports
          </Button>
          <Button type="button" variant={page === 'import' ? 'default' : 'outline'} onClick={() => setPage('import')}>
            Import quotes
          </Button>
        </div>
      </header>

      {page === 'review' && (
        <>
          {loadError && (
            <Alert variant="destructive" className="mb-4 border-destructive/30 bg-destructive/10">
              <AlertDescription>{loadError}</AlertDescription>
            </Alert>
          )}
          {actionError && (
            <Alert variant="destructive" className="mb-4 border-destructive/30 bg-destructive/10">
              <AlertDescription>{actionError}</AlertDescription>
            </Alert>
          )}

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
            lengthOp={lengthOp}
            onLengthOpChange={setLengthOp}
            lengthValue={lengthValue}
            onLengthValueChange={setLengthValue}
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
              deleteCount={bulkDeleteTargets.length}
              onBulkApprove={handleBulkApprove}
              onBulkReject={() => runBulkStatusAction('rejected', bulkRejectTargets)}
              onBulkMarkDuplicate={() => runBulkStatusAction('duplicate', bulkDuplicateTargets)}
              onBulkResetToPending={() => runBulkStatusAction('pending', bulkResetTargets)}
              onBulkDelete={requestBulkDelete}
            />
          )}

          {loading ? (
            <p className="py-8 text-center text-muted-foreground">Loading…</p>
          ) : (
            <ImportedQuotesTable
              quotes={filteredQuotes}
              sources={sources}
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
              onDelete={requestDelete}
            />
          )}

          {approveTarget && (
            <ApproveDialog
              quote={approveTarget}
              authors={authors}
              sources={sources}
              sourceTypes={sourceTypes}
              submitting={approveSubmitting}
              error={approveError}
              onCancel={() => {
                setApproveTarget(null)
                setApproveError(null)
              }}
              onSubmit={handleApproveSubmit}
            />
          )}

          {deleteRequest && (
            <ConfirmDialog
              title={deleteRequest.length === 1 ? 'Delete imported quote' : `Delete ${deleteRequest.length} imported quotes`}
              description={
                deleteRequest.length === 1
                  ? `"${truncateForDialog(deleteRequest[0].rawText)}" will be permanently deleted. This cannot be undone.`
                  : `${deleteRequest.length} imported quotes will be permanently deleted. This cannot be undone.`
              }
              confirmLabel="Delete"
              submittingLabel="Deleting…"
              submitting={deleteSubmitting}
              error={deleteError}
              onCancel={cancelDelete}
              onConfirm={confirmDelete}
            />
          )}
        </>
      )}

      {page === 'import' && <ImportPage />}
    </div>
  )
}

export default App
