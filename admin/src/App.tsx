import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import {
  approveImportedQuote,
  bulkDeleteImportedQuotes,
  bulkUpdateImportedQuoteStatus,
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
import { Toaster } from './components/Toaster'
import { useDebouncedValue } from './hooks/use-debounced-value'
import { toast } from './hooks/use-toast'
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

const FILTER_DEBOUNCE_MILLIS = 250

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
}

function truncateForDialog(text: string): string {
  return text.length > 120 ? `${text.slice(0, 120).trimEnd()}…` : text
}

const STATUS_LABELS: Record<ProcessingStatus, string> = {
  pending: 'Reset to pending',
  approved: 'Approved',
  rejected: 'Rejected',
  duplicate: 'Marked duplicate',
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
  const debouncedSearch = useDebouncedValue(search, FILTER_DEBOUNCE_MILLIS)
  const debouncedLengthValue = useDebouncedValue(lengthValue, FILTER_DEBOUNCE_MILLIS)

  const [approveTarget, setApproveTarget] = useState<ImportedQuote | null>(null)
  const [approveSubmitting, setApproveSubmitting] = useState(false)
  const [approveError, setApproveError] = useState<string | null>(null)

  const [busyAction, setBusyAction] = useState<{ id: number; status: ProcessingStatus } | null>(null)

  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  const [bulkBusy, setBulkBusy] = useState(false)
  const [bulkApproveProgress, setBulkApproveProgress] = useState<{ done: number; total: number } | null>(null)

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
    const term = debouncedSearch.trim().toLowerCase()
    const lengthThreshold = debouncedLengthValue.trim() === '' ? null : Number(debouncedLengthValue)
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
  }, [importedQuotes, selectedStatuses, confidence, provider, lengthOp, debouncedLengthValue, debouncedSearch])

  const toggleStatus = useCallback((status: ProcessingStatus) => {
    setSelectedStatuses((prev) => {
      const next = new Set(prev)
      if (next.has(status)) next.delete(status)
      else next.add(status)
      return next
    })
  }, [])

  const selectedQuotes = useMemo(
    () => importedQuotes.filter((quote) => selectedIds.has(quote.id)),
    [importedQuotes, selectedIds],
  )

  const allVisibleSelected = filteredQuotes.length > 0 && filteredQuotes.every((quote) => selectedIds.has(quote.id))

  const toggleSelect = useCallback((id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  const toggleSelectAllVisible = useCallback(() => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (allVisibleSelected) {
        for (const quote of filteredQuotes) next.delete(quote.id)
      } else {
        for (const quote of filteredQuotes) next.add(quote.id)
      }
      return next
    })
  }, [allVisibleSelected, filteredQuotes])

  const clearSelection = useCallback(() => setSelectedIds(new Set()), [])

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

  const runBulkStatusAction = useCallback(async (status: ProcessingStatus, targets: ImportedQuote[]) => {
    if (targets.length === 0) return
    setBulkBusy(true)
    const ids = targets.map((quote) => quote.id)
    const label = STATUS_LABELS[status]
    try {
      const result = await bulkUpdateImportedQuoteStatus({ ids, status })
      const succeeded = new Set(result.succeededIds)
      const now = new Date().toISOString()
      setImportedQuotes((prev) =>
        prev.map((existing) =>
          succeeded.has(existing.id) ? { ...existing, processingStatus: status, reviewedAt: now } : existing,
        ),
      )
      setSelectedIds((prev) => {
        const next = new Set(prev)
        for (const id of result.succeededIds) next.delete(id)
        return next
      })
      if (result.failedIds.length > 0) {
        toast.error(
          `${result.failedIds.length} of ${ids.length} failed`,
          `${label}: ${result.succeededIds.length} succeeded`,
        )
      } else {
        toast.success(`${label}: ${result.succeededIds.length} quote${result.succeededIds.length === 1 ? '' : 's'}`)
      }
    } catch (err) {
      toast.error(`${label} failed`, errorMessage(err))
    } finally {
      setBulkBusy(false)
    }
  }, [])

  const handleBulkReject = useCallback(
    () => runBulkStatusAction('rejected', bulkRejectTargets),
    [runBulkStatusAction, bulkRejectTargets],
  )
  const handleBulkMarkDuplicate = useCallback(
    () => runBulkStatusAction('duplicate', bulkDuplicateTargets),
    [runBulkStatusAction, bulkDuplicateTargets],
  )
  const handleBulkResetToPending = useCallback(
    () => runBulkStatusAction('pending', bulkResetTargets),
    [runBulkStatusAction, bulkResetTargets],
  )

  const handleBulkApprove = useCallback(async () => {
    const targets = bulkApproveTargets
    if (targets.length === 0) return
    setBulkBusy(true)
    setBulkApproveProgress({ done: 0, total: targets.length })
    const patchedQuoteIds = new Map<number, number>()
    let mayHaveCreatedAuthor = false
    let failures = 0
    for (const [index, quote] of targets.entries()) {
      try {
        const response = await approveImportedQuote(quote.id, {})
        patchedQuoteIds.set(quote.id, response.id)
        if (quote.rawAuthor?.trim()) mayHaveCreatedAuthor = true
      } catch {
        failures += 1
      }
      setBulkApproveProgress({ done: index + 1, total: targets.length })
    }
    const now = new Date().toISOString()
    setImportedQuotes((prev) =>
      prev.map((existing) => {
        const quoteId = patchedQuoteIds.get(existing.id)
        return quoteId !== undefined
          ? { ...existing, processingStatus: 'approved', quoteId, reviewedAt: now }
          : existing
      }),
    )
    setSelectedIds((prev) => {
      const next = new Set(prev)
      for (const id of patchedQuoteIds.keys()) next.delete(id)
      return next
    })
    if (failures > 0) {
      toast.error(`${failures} of ${targets.length} approvals failed`, `${patchedQuoteIds.size} succeeded`)
    } else if (patchedQuoteIds.size > 0) {
      toast.success(`Approved ${patchedQuoteIds.size} quote${patchedQuoteIds.size === 1 ? '' : 's'}`)
    }
    if (mayHaveCreatedAuthor) fetchAuthors().then(setAuthors).catch(() => undefined)
    setBulkApproveProgress(null)
    setBulkBusy(false)
  }, [bulkApproveTargets])

  const runStatusAction = useCallback(async (quote: ImportedQuote, status: ProcessingStatus) => {
    setBusyAction({ id: quote.id, status })
    const label = STATUS_LABELS[status]
    try {
      const updated = await updateImportedQuoteStatus(quote.id, status)
      setImportedQuotes((prev) => prev.map((existing) => (existing.id === updated.id ? updated : existing)))
      toast.success(label)
    } catch (err) {
      toast.error(`${label} failed`, errorMessage(err))
    } finally {
      setBusyAction(null)
    }
  }, [])

  const handleReject = useCallback((quote: ImportedQuote) => runStatusAction(quote, 'rejected'), [runStatusAction])
  const handleMarkDuplicate = useCallback(
    (quote: ImportedQuote) => runStatusAction(quote, 'duplicate'),
    [runStatusAction],
  )
  const handleResetToPending = useCallback(
    (quote: ImportedQuote) => runStatusAction(quote, 'pending'),
    [runStatusAction],
  )

  const handleApproveSubmit = useCallback(
    async (request: ApproveImportedQuoteRequest) => {
      if (!approveTarget) return
      const target = approveTarget
      setApproveSubmitting(true)
      setApproveError(null)
      try {
        const quote = await approveImportedQuote(target.id, request)
        const now = new Date().toISOString()
        setImportedQuotes((prev) =>
          prev.map((existing) =>
            existing.id === target.id
              ? { ...existing, processingStatus: 'approved', quoteId: quote.id, reviewedAt: now }
              : existing,
          ),
        )
        setApproveTarget(null)
        toast.success('Approved', truncateForDialog(target.rawText))
        if (request.newAuthorName) fetchAuthors().then(setAuthors).catch(() => undefined)
        if (request.newSource) fetchSources().then(setSources).catch(() => undefined)
      } catch (err) {
        setApproveError(errorMessage(err))
      } finally {
        setApproveSubmitting(false)
      }
    },
    [approveTarget],
  )

  const requestDelete = useCallback((quote: ImportedQuote) => {
    setDeleteError(null)
    setDeleteRequest([quote])
  }, [])

  const requestBulkDelete = useCallback(() => {
    if (bulkDeleteTargets.length === 0) return
    setDeleteError(null)
    setDeleteRequest(bulkDeleteTargets)
  }, [bulkDeleteTargets])

  const cancelDelete = useCallback(() => {
    if (deleteSubmitting) return
    setDeleteRequest(null)
    setDeleteError(null)
  }, [deleteSubmitting])

  const confirmDelete = useCallback(async () => {
    if (!deleteRequest) return
    setDeleteSubmitting(true)
    setDeleteError(null)
    const ids = deleteRequest.map((quote) => quote.id)
    try {
      const result = await bulkDeleteImportedQuotes({ ids })
      const deleted = new Set(result.succeededIds)
      setImportedQuotes((prev) => prev.filter((quote) => !deleted.has(quote.id)))
      setSelectedIds((prev) => {
        const next = new Set(prev)
        for (const id of result.succeededIds) next.delete(id)
        return next
      })
      if (result.failedIds.length > 0) {
        setDeleteError(`${result.failedIds.length} of ${ids.length} deletions failed`)
        setDeleteRequest((prev) => prev?.filter((quote) => !deleted.has(quote.id)) ?? null)
      } else {
        toast.success(ids.length === 1 ? 'Deleted' : `Deleted ${ids.length} quotes`)
        setDeleteRequest(null)
      }
    } catch (err) {
      setDeleteError(errorMessage(err))
    } finally {
      setDeleteSubmitting(false)
    }
  }, [deleteRequest])

  return (
    <div className="mx-auto max-w-[1280px] px-8 pt-6 pb-16">
      <Toaster />
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
          <Button
            type="button"
            variant={page === 'review' ? 'default' : 'outline'}
            onClick={() => {
              setPage('review')
              loadAll()
            }}
          >
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
              approveProgress={bulkApproveProgress}
              onToggleSelectAllVisible={toggleSelectAllVisible}
              onClearSelection={clearSelection}
              approveCount={bulkApproveTargets.length}
              approveSkippedCount={bulkApproveSkipped}
              rejectCount={bulkRejectTargets.length}
              duplicateCount={bulkDuplicateTargets.length}
              resetCount={bulkResetTargets.length}
              deleteCount={bulkDeleteTargets.length}
              onBulkApprove={handleBulkApprove}
              onBulkReject={handleBulkReject}
              onBulkMarkDuplicate={handleBulkMarkDuplicate}
              onBulkResetToPending={handleBulkResetToPending}
              onBulkDelete={requestBulkDelete}
            />
          )}

          {loading ? (
            <p className="py-8 text-center text-muted-foreground">Loading…</p>
          ) : (
            <ImportedQuotesTable
              quotes={filteredQuotes}
              sources={sources}
              busyAction={busyAction}
              bulkBusy={bulkBusy}
              selectedIds={selectedIds}
              allVisibleSelected={allVisibleSelected}
              onToggleSelect={toggleSelect}
              onToggleSelectAllVisible={toggleSelectAllVisible}
              onApprove={setApproveTarget}
              onReject={handleReject}
              onMarkDuplicate={handleMarkDuplicate}
              onResetToPending={handleResetToPending}
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
