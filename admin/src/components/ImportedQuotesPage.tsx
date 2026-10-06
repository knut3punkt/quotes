import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  approveImportedQuote,
  bulkDeleteImportedQuotes,
  bulkUnapproveQuotes,
  bulkUpdateImportedQuoteStatus,
  fetchAuthors,
  fetchImportedQuoteFilterOptions,
  fetchImportedQuotes,
  fetchImportedQuoteSelection,
  fetchSources,
  fetchSourceTypes,
  updateImportedQuoteStatus,
} from '../api'
import { useBulkSelection } from '../hooks/use-bulk-selection'
import { useChunkedRun } from '../hooks/use-chunked-run'
import { useDebouncedValue } from '../hooks/use-debounced-value'
import { usePagedList, type Page } from '../hooks/use-paged-list'
import { toast } from '../hooks/use-toast'
import {
  DEFAULT_IMPORTED_QUOTE_FILTERS,
  importedQuoteSelectionItem,
  toImportedQuoteFilter,
  type ImportedQuoteFilterState,
} from '../importedQuoteFilters'
import { canApprove, canDelete, canMarkDuplicate, canReject, canResetToPending, canUnapprove } from '../statusRules'
import type {
  ApproveImportedQuoteRequest,
  Author,
  ImportedQuote,
  ImportedQuoteFilter,
  ImportedQuoteFilterOptions,
  ImportedQuoteSelectionItem,
  ProcessingStatus,
  Source,
  SourceType,
} from '../types'
import { ApproveDialog } from './ApproveDialog'
import { ConfirmDialog } from './ConfirmDialog'
import { FilterBar } from './FilterBar'
import { ImportedQuoteBulkActions } from './ImportedQuoteBulkActions'
import { ImportedQuotesTable } from './ImportedQuotesTable'
import { SelectionToolbar } from './SelectionToolbar'

const FILTER_DEBOUNCE_MILLIS = 250
const STATUS_CHUNK_SIZE = 1000
const UNAPPROVE_CHUNK_SIZE = 200

const EMPTY_FILTER_OPTIONS: ImportedQuoteFilterOptions = { providers: [], statusCounts: {}, authors: [], sources: [] }

const STATUS_LABELS: Record<ProcessingStatus, string> = {
  pending: 'Reset to pending',
  approved: 'Approved',
  rejected: 'Rejected',
  duplicate: 'Marked duplicate',
}

const STATUS_PROGRESS_LABELS: Record<ProcessingStatus, string> = {
  pending: 'Resetting',
  approved: 'Approving',
  rejected: 'Rejecting',
  duplicate: 'Marking duplicate',
}

// With no status chosen nothing matches; the server reads an empty status list as "no filter", so answer locally.
function fetchImportedQuotePage(filter: ImportedQuoteFilter, page: number, pageSize: number): Promise<Page<ImportedQuote>> {
  return filter.statuses.length === 0
    ? Promise.resolve({ items: [], total: 0 })
    : fetchImportedQuotes(filter, page, pageSize)
}

function fetchSelection(filter: ImportedQuoteFilter): Promise<ImportedQuoteSelectionItem[]> {
  return filter.statuses.length === 0 ? Promise.resolve([]) : fetchImportedQuoteSelection(filter)
}

function importedQuoteId(quote: ImportedQuote): number {
  return quote.id
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
}

function truncateForDialog(text: string): string {
  return text.length > 120 ? `${text.slice(0, 120).trimEnd()}…` : text
}

function plural(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? '' : 's'}`
}

/** What a delete or unapprove confirmation is about; `text` is set when it is a single quote. */
interface ConfirmRequest {
  ids: number[]
  text?: string
}

export function ImportedQuotesPage() {
  const [authors, setAuthors] = useState<Author[]>([])
  const [sources, setSources] = useState<Source[]>([])
  const [sourceTypes, setSourceTypes] = useState<SourceType[]>([])
  const [filterOptions, setFilterOptions] = useState<ImportedQuoteFilterOptions>(EMPTY_FILTER_OPTIONS)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [filters, setFilters] = useState<ImportedQuoteFilterState>(DEFAULT_IMPORTED_QUOTE_FILTERS)
  const debouncedSearch = useDebouncedValue(filters.search, FILTER_DEBOUNCE_MILLIS)
  const debouncedLengthValue = useDebouncedValue(filters.lengthValue, FILTER_DEBOUNCE_MILLIS)
  const updateFilters = useCallback(
    (patch: Partial<ImportedQuoteFilterState>) => setFilters((prev) => ({ ...prev, ...patch })),
    [],
  )
  const resetFilters = useCallback(() => setFilters(DEFAULT_IMPORTED_QUOTE_FILTERS), [])

  const { statuses, confidence, provider, authors: authorKeys, sources: sourceKeys } = filters
  const { possibleDuplicate, importedFrom, importedTo, lengthOp } = filters
  const queryFilter = useMemo(
    () =>
      toImportedQuoteFilter({
        statuses,
        confidence,
        provider,
        authors: authorKeys,
        sources: sourceKeys,
        possibleDuplicate,
        importedFrom,
        importedTo,
        lengthOp,
        search: debouncedSearch,
        lengthValue: debouncedLengthValue,
      }),
    [
      statuses,
      confidence,
      provider,
      authorKeys,
      sourceKeys,
      possibleDuplicate,
      importedFrom,
      importedTo,
      lengthOp,
      debouncedSearch,
      debouncedLengthValue,
    ],
  )

  const list = usePagedList({ fetchPage: fetchImportedQuotePage, filters: queryFilter, getId: importedQuoteId })
  const selection = useBulkSelection<ImportedQuoteSelectionItem>(queryFilter)
  const chunked = useChunkedRun()
  const { toggle: toggleSelection, toggleMany: toggleSelectionMany, selectAll, remove: removeFromSelection, updateIfSelected } =
    selection
  const { run: runChunked } = chunked
  const { patchRows, reload } = list

  const [approveTarget, setApproveTarget] = useState<ImportedQuote | null>(null)
  const [approveSubmitting, setApproveSubmitting] = useState(false)
  const [approveError, setApproveError] = useState<string | null>(null)

  const [busyAction, setBusyAction] = useState<{ id: number; status: ProcessingStatus } | null>(null)

  const [deleteRequest, setDeleteRequest] = useState<ConfirmRequest | null>(null)
  const [deleteSubmitting, setDeleteSubmitting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const [unapproveRequest, setUnapproveRequest] = useState<ConfirmRequest | null>(null)
  const [unapproveSubmitting, setUnapproveSubmitting] = useState(false)
  const [unapproveError, setUnapproveError] = useState<string | null>(null)

  const bulkBusy = chunked.running || deleteSubmitting || unapproveSubmitting

  const loadFilterOptions = useCallback(() => {
    fetchImportedQuoteFilterOptions()
      .then(setFilterOptions)
      .catch((err) => setLoadError(errorMessage(err)))
  }, [])

  useEffect(() => {
    Promise.all([fetchAuthors(), fetchSources(), fetchSourceTypes()])
      .then(([authorList, sourceList, sourceTypeList]) => {
        setAuthors(authorList)
        setSources(sourceList)
        setSourceTypes(sourceTypeList)
      })
      .catch((err) => setLoadError(errorMessage(err)))
    loadFilterOptions()
  }, [loadFilterOptions])

  /** After rows changed status or disappeared: refresh the visible rows and the counts in the filters. */
  const refreshAfterBulkChange = useCallback(() => {
    reload()
    loadFilterOptions()
  }, [reload, loadFilterOptions])

  const statusCounts = useMemo(
    () => ({
      pending: filterOptions.statusCounts.pending ?? 0,
      approved: filterOptions.statusCounts.approved ?? 0,
      rejected: filterOptions.statusCounts.rejected ?? 0,
      duplicate: filterOptions.statusCounts.duplicate ?? 0,
    }),
    [filterOptions],
  )

  const selected = selection.summaries
  const approveTargets = useMemo(
    () => selected.filter((item) => canApprove(item.processingStatus) && item.approvable),
    [selected],
  )
  const approveSkippedCount = useMemo(
    () => selected.filter((item) => canApprove(item.processingStatus) && !item.approvable).length,
    [selected],
  )
  const rejectTargets = useMemo(() => selected.filter((item) => canReject(item.processingStatus)), [selected])
  const duplicateTargets = useMemo(() => selected.filter((item) => canMarkDuplicate(item.processingStatus)), [selected])
  const resetTargets = useMemo(() => selected.filter((item) => canResetToPending(item.processingStatus)), [selected])
  const deleteTargets = useMemo(() => selected.filter((item) => canDelete(item.processingStatus)), [selected])
  const unapproveTargets = useMemo(
    () => selected.filter((item) => canUnapprove(item.processingStatus) && item.quoteId !== null),
    [selected],
  )

  const { rows } = list
  const toggleSelect = useCallback((quote: ImportedQuote) => toggleSelection(importedQuoteSelectionItem(quote)), [toggleSelection])
  const toggleSelectLoaded = useCallback(
    () => toggleSelectionMany(rows.map(importedQuoteSelectionItem)),
    [toggleSelectionMany, rows],
  )
  const selectAllMatching = useCallback(
    () => selectAll(() => fetchSelection(queryFilter)),
    [selectAll, queryFilter],
  )

  const runBulkStatusAction = useCallback(
    async (status: ProcessingStatus, targets: ImportedQuoteSelectionItem[]) => {
      if (targets.length === 0) return
      const ids = targets.map((item) => item.id)
      const label = STATUS_LABELS[status]
      const { results, cancelledItems } = await runChunked(
        STATUS_PROGRESS_LABELS[status],
        ids,
        STATUS_CHUNK_SIZE,
        async (chunk) => (await bulkUpdateImportedQuoteStatus({ ids: chunk, status })).succeededIds,
      )
      removeFromSelection(results)
      const failed = ids.length - results.length - cancelledItems
      if (failed > 0) {
        toast.error(`${failed} of ${ids.length} failed`, `${label}: ${results.length} succeeded`)
      } else if (cancelledItems > 0) {
        toast.success(`${label}: ${plural(results.length, 'quote')}`, `Cancelled before ${cancelledItems} more`)
      } else {
        toast.success(`${label}: ${plural(results.length, 'quote')}`)
      }
      refreshAfterBulkChange()
    },
    [runChunked, removeFromSelection, refreshAfterBulkChange],
  )

  const handleBulkReject = useCallback(
    () => runBulkStatusAction('rejected', rejectTargets),
    [runBulkStatusAction, rejectTargets],
  )
  const handleBulkMarkDuplicate = useCallback(
    () => runBulkStatusAction('duplicate', duplicateTargets),
    [runBulkStatusAction, duplicateTargets],
  )
  const handleBulkResetToPending = useCallback(
    () => runBulkStatusAction('pending', resetTargets),
    [runBulkStatusAction, resetTargets],
  )

  const handleBulkApprove = useCallback(async () => {
    const ids = approveTargets.map((item) => item.id)
    if (ids.length === 0) return
    const { results, failedItems, cancelledItems } = await runChunked('Approving', ids, 1, async ([id]) => {
      await approveImportedQuote(id, {})
      return [id]
    })
    removeFromSelection(results)
    if (failedItems > 0) {
      toast.error(`${failedItems} of ${ids.length} approvals failed`, `${results.length} succeeded`)
    } else if (results.length > 0) {
      toast.success(
        `Approved ${plural(results.length, 'quote')}`,
        cancelledItems > 0 ? `Cancelled before ${cancelledItems} more` : undefined,
      )
    }
    // Approving may have created authors from the raw author names.
    if (results.length > 0) fetchAuthors().then(setAuthors).catch(() => undefined)
    refreshAfterBulkChange()
  }, [approveTargets, runChunked, removeFromSelection, refreshAfterBulkChange])

  const runStatusAction = useCallback(
    async (quote: ImportedQuote, status: ProcessingStatus) => {
      setBusyAction({ id: quote.id, status })
      const label = STATUS_LABELS[status]
      try {
        const updated = await updateImportedQuoteStatus(quote.id, status)
        // Single-row actions update the row in place, so it stays in view until the next filter change.
        patchRows((prev) => prev.map((existing) => (existing.id === updated.id ? updated : existing)))
        updateIfSelected(importedQuoteSelectionItem(updated))
        loadFilterOptions()
        toast.success(label)
      } catch (err) {
        toast.error(`${label} failed`, errorMessage(err))
      } finally {
        setBusyAction(null)
      }
    },
    [patchRows, updateIfSelected, loadFilterOptions],
  )

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
        const approved: ImportedQuote = {
          ...target,
          processingStatus: 'approved',
          quoteId: quote.id,
          reviewedAt: new Date().toISOString(),
        }
        patchRows((prev) => prev.map((existing) => (existing.id === target.id ? approved : existing)))
        updateIfSelected(importedQuoteSelectionItem(approved))
        loadFilterOptions()
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
    [approveTarget, patchRows, updateIfSelected, loadFilterOptions],
  )

  const requestDelete = useCallback((quote: ImportedQuote) => {
    setDeleteError(null)
    setDeleteRequest({ ids: [quote.id], text: quote.rawText })
  }, [])

  const requestBulkDelete = useCallback(() => {
    if (deleteTargets.length === 0) return
    setDeleteError(null)
    setDeleteRequest({ ids: deleteTargets.map((item) => item.id) })
  }, [deleteTargets])

  const cancelDelete = useCallback(() => {
    if (deleteSubmitting) return
    setDeleteRequest(null)
    setDeleteError(null)
  }, [deleteSubmitting])

  const confirmDelete = useCallback(async () => {
    if (!deleteRequest) return
    setDeleteSubmitting(true)
    setDeleteError(null)
    const { ids } = deleteRequest
    const { results, cancelledItems } = await runChunked(
      'Deleting',
      ids,
      STATUS_CHUNK_SIZE,
      async (chunk) => (await bulkDeleteImportedQuotes({ ids: chunk })).succeededIds,
    )
    removeFromSelection(results)
    const failed = ids.length - results.length - cancelledItems
    if (failed > 0) {
      const deleted = new Set(results)
      setDeleteError(`${failed} of ${ids.length} deletions failed`)
      setDeleteRequest((prev) => (prev ? { ...prev, ids: prev.ids.filter((id) => !deleted.has(id)) } : null))
    } else {
      toast.success(ids.length === 1 ? 'Deleted' : `Deleted ${plural(results.length, 'quote')}`)
      setDeleteRequest(null)
    }
    setDeleteSubmitting(false)
    refreshAfterBulkChange()
  }, [deleteRequest, runChunked, removeFromSelection, refreshAfterBulkChange])

  const requestBulkUnapprove = useCallback(() => {
    if (unapproveTargets.length === 0) return
    setUnapproveError(null)
    setUnapproveRequest({ ids: unapproveTargets.map((item) => item.id) })
  }, [unapproveTargets])

  const cancelUnapprove = useCallback(() => {
    if (unapproveSubmitting) return
    setUnapproveRequest(null)
    setUnapproveError(null)
  }, [unapproveSubmitting])

  const confirmUnapprove = useCallback(async () => {
    if (!unapproveRequest) return
    setUnapproveSubmitting(true)
    setUnapproveError(null)
    const quoteIdByImportId = new Map(
      unapproveTargets
        .filter((item) => unapproveRequest.ids.includes(item.id) && item.quoteId !== null)
        .map((item) => [item.id, item.quoteId as number]),
    )
    const importIdByQuoteId = new Map(Array.from(quoteIdByImportId, ([importId, quoteId]) => [quoteId, importId]))
    const quoteIds = Array.from(quoteIdByImportId.values())
    const { results, cancelledItems } = await runChunked(
      'Unapproving',
      quoteIds,
      UNAPPROVE_CHUNK_SIZE,
      async (chunk) => (await bulkUnapproveQuotes({ quoteIds: chunk })).succeededIds,
    )
    const unapprovedImportIds = results.flatMap((quoteId) => {
      const importId = importIdByQuoteId.get(quoteId)
      return importId === undefined ? [] : [importId]
    })
    removeFromSelection(unapprovedImportIds)
    const failed = quoteIds.length - results.length - cancelledItems
    if (failed > 0) {
      const done = new Set(unapprovedImportIds)
      setUnapproveError(`${failed} of ${quoteIds.length} unapprovals failed`)
      setUnapproveRequest((prev) => (prev ? { ...prev, ids: prev.ids.filter((id) => !done.has(id)) } : null))
    } else {
      toast.success(`Unapproved ${plural(results.length, 'quote')}`)
      setUnapproveRequest(null)
    }
    setUnapproveSubmitting(false)
    refreshAfterBulkChange()
  }, [unapproveRequest, unapproveTargets, runChunked, removeFromSelection, refreshAfterBulkChange])

  const error = loadError ?? list.error

  return (
    <>
      {error && (
        <Alert variant="destructive" className="mb-4 border-destructive/30 bg-destructive/10">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <FilterBar
        filters={filters}
        onChange={updateFilters}
        onReset={resetFilters}
        statusCounts={statusCounts}
        providers={filterOptions.providers}
        authorOptions={filterOptions.authors}
        sourceOptions={filterOptions.sources}
      />

      {!list.loading && (
        <SelectionToolbar
          selectedCount={selection.count}
          matchingCount={list.total}
          selectingAll={selection.selectingAll}
          selectAllError={selection.selectAllError}
          busy={bulkBusy}
          progress={chunked.progress}
          onSelectAllMatching={selectAllMatching}
          onClearSelection={selection.clear}
          onCancelRun={chunked.cancel}
        >
          <ImportedQuoteBulkActions
            bulkBusy={bulkBusy}
            approveCount={approveTargets.length}
            approveSkippedCount={approveSkippedCount}
            rejectCount={rejectTargets.length}
            duplicateCount={duplicateTargets.length}
            resetCount={resetTargets.length}
            deleteCount={deleteTargets.length}
            unapproveCount={unapproveTargets.length}
            onBulkApprove={handleBulkApprove}
            onBulkReject={handleBulkReject}
            onBulkMarkDuplicate={handleBulkMarkDuplicate}
            onBulkResetToPending={handleBulkResetToPending}
            onBulkDelete={requestBulkDelete}
            onBulkUnapprove={requestBulkUnapprove}
          />
        </SelectionToolbar>
      )}

      <ImportedQuotesTable
        quotes={list.rows}
        sources={sources}
        loading={list.loading}
        loadingMore={list.loadingMore}
        hasMore={list.hasMore}
        onEndReached={list.loadMore}
        busyAction={busyAction}
        bulkBusy={bulkBusy}
        selectedIds={selection.selectedIds}
        onToggleSelect={toggleSelect}
        onToggleSelectLoaded={toggleSelectLoaded}
        onApprove={setApproveTarget}
        onReject={handleReject}
        onMarkDuplicate={handleMarkDuplicate}
        onResetToPending={handleResetToPending}
        onDelete={requestDelete}
      />

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
          title={
            deleteRequest.ids.length === 1 ? 'Delete imported quote' : `Delete ${deleteRequest.ids.length} imported quotes`
          }
          description={
            deleteRequest.text !== undefined
              ? `"${truncateForDialog(deleteRequest.text)}" will be permanently deleted. This cannot be undone.`
              : `${plural(deleteRequest.ids.length, 'imported quote')} will be permanently deleted. This cannot be undone.`
          }
          confirmLabel="Delete"
          submittingLabel="Deleting…"
          submitting={deleteSubmitting}
          error={deleteError}
          onCancel={cancelDelete}
          onConfirm={confirmDelete}
        />
      )}

      {unapproveRequest && (
        <ConfirmDialog
          title={
            unapproveRequest.ids.length === 1 ? 'Unapprove quote' : `Unapprove ${unapproveRequest.ids.length} quotes`
          }
          description={`${
            unapproveRequest.ids.length === 1 ? 'This quote' : plural(unapproveRequest.ids.length, 'quote')
          } will be deleted from the library, along with excerpts, interpretations and tags. The imports return to pending.`}
          confirmLabel="Unapprove"
          submittingLabel="Unapproving…"
          submitting={unapproveSubmitting}
          error={unapproveError}
          onCancel={cancelUnapprove}
          onConfirm={confirmUnapprove}
        />
      )}
    </>
  )
}
