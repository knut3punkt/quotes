import { Alert, AlertDescription } from '@/components/ui/alert'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { Lightbulb } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  bulkUnapproveQuotes,
  extractQuoteExcerpts,
  fetchQuoteFilterOptions,
  fetchQuotes,
  fetchQuoteSelection,
  fetchTags,
  generateQuoteInterpretations,
  generateQuoteTags,
} from '../api'
import { useBulkSelection } from '../hooks/use-bulk-selection'
import { useChunkedRun } from '../hooks/use-chunked-run'
import { useDebouncedValue } from '../hooks/use-debounced-value'
import { usePagedList } from '../hooks/use-paged-list'
import { toast } from '../hooks/use-toast'
import { DEFAULT_QUOTE_FILTERS, toQuoteFilter, type QuoteFilterState } from '../quoteFilters'
import type {
  QuoteExtractionOutcome,
  QuoteFilterOptions,
  QuoteInterpretationOutcome,
  QuoteListItem,
  QuoteSelectionItem,
  QuoteTag,
  QuoteTaggingOutcome,
  TagFacet,
} from '../types'
import { ConfirmDialog } from './ConfirmDialog'
import { DataTable, type DataTableColumn } from './data-table/DataTable'
import { HighlightedQuoteText } from './HighlightedQuoteText'
import { QuoteBulkActions } from './QuoteBulkActions'
import { QuotesFilterBar } from './QuotesFilterBar'
import { QuoteTagsCell } from './QuoteTagsCell'
import { SelectionToolbar } from './SelectionToolbar'

const FILTER_DEBOUNCE_MILLIS = 250

// Each LLM run takes seconds per quote, so enrichment requests stay small; unapprove is a plain delete.
const ENRICHMENT_CHUNK_SIZE = 10
const UNAPPROVE_CHUNK_SIZE = 200

// The server's actual EXTRACTION_MIN_SOURCE_WORDS-driven check is authoritative; this only estimates
// which selected quotes will be skipped so the button can show a helpful count before submitting.
const ESTIMATED_MIN_SOURCE_WORDS = 55

const OUTCOME_LABELS: Record<QuoteExtractionOutcome, string> = {
  extracted: 'extracted',
  noExcerptsFound: 'no excerpt found',
  skippedTooShort: 'skipped (too short)',
  failed: 'failed',
  notFound: 'not found',
}

const TAGGING_OUTCOME_LABELS: Record<QuoteTaggingOutcome, string> = {
  tagged: 'tagged',
  noTagsFound: 'no tags found',
  failed: 'failed',
  notFound: 'not found',
}

const INTERPRETATION_OUTCOME_LABELS: Record<QuoteInterpretationOutcome, string> = {
  generated: 'generated',
  noInterpretationsFound: 'no interpretation found',
  failed: 'failed',
  notFound: 'not found',
}

const EMPTY_VOCABULARY: Record<TagFacet, string[]> = { concept: [], mood: [], motif: [] }

const EMPTY_FILTER_OPTIONS: QuoteFilterOptions = {
  authors: [],
  sources: [],
  languages: [],
  providers: [],
  sourceConfidences: [],
}

function wordCount(text: string): number {
  return text.trim().split(/\s+/).filter(Boolean).length
}

function quoteId(quote: QuoteListItem): number {
  return quote.id
}

function quoteSelectionItem(quote: QuoteListItem): QuoteSelectionItem {
  return { id: quote.id, wordCount: wordCount(quote.text) }
}

/** A run's results, as "3 tagged, 1 failed", plus quotes whose request failed outright or were cancelled. */
function summarizeOutcomes<O extends string>(
  outcomes: O[],
  labels: Record<O, string>,
  failedItems: number,
  cancelledItems: number,
): { summary: string; anyFailed: boolean } {
  const counts = new Map<O, number>()
  for (const outcome of outcomes) counts.set(outcome, (counts.get(outcome) ?? 0) + 1)
  const parts = Array.from(counts, ([outcome, count]) => `${count} ${labels[outcome]}`)
  if (failedItems > 0) parts.push(`${failedItems} not processed (request failed)`)
  if (cancelledItems > 0) parts.push(`${cancelledItems} cancelled`)
  return { summary: parts.join(', '), anyFailed: failedItems > 0 || (counts.get('failed' as O) ?? 0) > 0 }
}

export function QuotesPage() {
  const [filterOptions, setFilterOptions] = useState<QuoteFilterOptions>(EMPTY_FILTER_OPTIONS)
  const [filters, setFilters] = useState<QuoteFilterState>(DEFAULT_QUOTE_FILTERS)

  const debouncedSearch = useDebouncedValue(filters.search, FILTER_DEBOUNCE_MILLIS)
  const debouncedLengthValue = useDebouncedValue(filters.lengthValue, FILTER_DEBOUNCE_MILLIS)
  const debouncedTag = useDebouncedValue(filters.tag, FILTER_DEBOUNCE_MILLIS)

  const [vocabulary, setVocabulary] = useState<Record<TagFacet, string[]>>(EMPTY_VOCABULARY)
  const [unapproveRequest, setUnapproveRequest] = useState<number[] | null>(null)
  const [unapproving, setUnapproving] = useState(false)
  const [unapproveError, setUnapproveError] = useState<string | null>(null)

  const { authors, sources, languages, providers, confidences, lengthOp, excerpts, interpretations, tags } = filters
  const queryFilter = useMemo(
    () =>
      toQuoteFilter({
        authors,
        sources,
        languages,
        providers,
        confidences,
        lengthOp,
        excerpts,
        interpretations,
        tags,
        search: debouncedSearch,
        lengthValue: debouncedLengthValue,
        tag: debouncedTag,
      }),
    [
      authors,
      sources,
      languages,
      providers,
      confidences,
      lengthOp,
      excerpts,
      interpretations,
      tags,
      debouncedSearch,
      debouncedLengthValue,
      debouncedTag,
    ],
  )

  const list = usePagedList({ fetchPage: fetchQuotes, filters: queryFilter, getId: quoteId })
  const selection = useBulkSelection<QuoteSelectionItem>(queryFilter)
  const chunked = useChunkedRun()
  const { toggle: toggleSelection, toggleMany: toggleSelectionMany, selectAll, remove: removeFromSelection } = selection
  const { run: runChunked } = chunked
  const { rows, patchRows, reload } = list

  const busy = chunked.running || unapproving

  useEffect(() => {
    fetchQuoteFilterOptions().then(setFilterOptions).catch(() => undefined)
  }, [])

  const tagNames = useMemo(
    () => Array.from(new Set([...vocabulary.concept, ...vocabulary.mood, ...vocabulary.motif])).sort(),
    [vocabulary],
  )

  const handleFiltersChange = useCallback((patch: Partial<QuoteFilterState>) => {
    setFilters((prev) => ({ ...prev, ...patch }))
  }, [])

  const resetFilters = useCallback(() => setFilters(DEFAULT_QUOTE_FILTERS), [])

  // Autocomplete suggestions for the add-tag input; refreshed after each generation run.
  const loadVocabulary = useCallback(() => {
    fetchTags()
      .then((tagList) => {
        const byFacet: Record<TagFacet, string[]> = { concept: [], mood: [], motif: [] }
        for (const tag of tagList) byFacet[tag.facet].push(tag.name)
        setVocabulary(byFacet)
      })
      .catch(() => undefined)
  }, [])

  useEffect(() => {
    loadVocabulary()
  }, [loadVocabulary])

  const selectedIds = useMemo(() => selection.summaries.map((item) => item.id), [selection.summaries])
  const eligibleIds = useMemo(
    () => selection.summaries.filter((item) => item.wordCount >= ESTIMATED_MIN_SOURCE_WORDS).map((item) => item.id),
    [selection.summaries],
  )
  const skippedCount = selectedIds.length - eligibleIds.length

  const toggleSelect = useCallback((quote: QuoteListItem) => toggleSelection(quoteSelectionItem(quote)), [toggleSelection])
  const toggleSelectLoaded = useCallback(
    () => toggleSelectionMany(rows.map(quoteSelectionItem)),
    [toggleSelectionMany, rows],
  )
  const selectAllMatching = useCallback(
    () => selectAll(() => fetchQuoteSelection(queryFilter)),
    [selectAll, queryFilter],
  )

  /**
   * Runs an enrichment pipeline over `ids` in small batches, merges each result into its row if it is loaded, and
   * reports the combined outcome counts in one toast.
   */
  const runEnrichment = useCallback(
    async <R extends { quoteId: number; outcome: O }, O extends string>({
      progressLabel,
      ids,
      request,
      merge,
      outcomeLabels,
      title,
    }: {
      progressLabel: string
      ids: number[]
      request: (chunk: number[]) => Promise<R[]>
      merge: (quote: QuoteListItem, result: R) => QuoteListItem
      outcomeLabels: Record<O, string>
      title: { success: string; partialFailure: string }
    }) => {
      if (ids.length === 0) return
      const { results, failedItems, cancelledItems } = await runChunked(progressLabel, ids, ENRICHMENT_CHUNK_SIZE, request)
      const resultsById = new Map(results.map((result) => [result.quoteId, result]))
      patchRows((prev) =>
        prev.map((quote) => {
          const result = resultsById.get(quote.id)
          return result ? merge(quote, result) : quote
        }),
      )
      removeFromSelection(resultsById.keys())
      const { summary, anyFailed } = summarizeOutcomes(
        results.map((result) => result.outcome),
        outcomeLabels,
        failedItems,
        cancelledItems,
      )
      if (anyFailed) toast.error(title.partialFailure, summary)
      else toast.success(title.success, summary)
    },
    [runChunked, patchRows, removeFromSelection],
  )

  const handleExtractExcerpts = useCallback(
    () =>
      runEnrichment({
        progressLabel: 'Extracting excerpts',
        ids: eligibleIds,
        request: async (chunk) => (await extractQuoteExcerpts(chunk)).results,
        // A skipped/not-found outcome never touched the database, so leave existing highlights as-is.
        merge: (quote, result) =>
          result.outcome === 'skippedTooShort' || result.outcome === 'notFound'
            ? quote
            : { ...quote, excerpts: result.excerpts },
        outcomeLabels: OUTCOME_LABELS,
        title: { success: 'Extraction complete', partialFailure: 'Some extractions failed' },
      }),
    [runEnrichment, eligibleIds],
  )

  const handleGenerateInterpretations = useCallback(
    () =>
      runEnrichment({
        progressLabel: 'Generating interpretations',
        ids: selectedIds,
        request: async (chunk) => (await generateQuoteInterpretations(chunk)).results,
        // A not-found outcome never touched the database, so leave existing interpretations as-is.
        merge: (quote, result) =>
          result.outcome === 'notFound' ? quote : { ...quote, interpretations: result.interpretations },
        outcomeLabels: INTERPRETATION_OUTCOME_LABELS,
        title: { success: 'Interpretation generation complete', partialFailure: 'Some interpretation generations failed' },
      }),
    [runEnrichment, selectedIds],
  )

  const handleGenerateTags = useCallback(async () => {
    await runEnrichment({
      progressLabel: 'Tagging',
      ids: selectedIds,
      request: async (chunk) => (await generateQuoteTags(chunk)).results,
      // The server returns every active tag on the quote, including admin-added ones.
      merge: (quote, result) => (result.outcome === 'notFound' ? quote : { ...quote, tags: result.tags }),
      outcomeLabels: TAGGING_OUTCOME_LABELS,
      title: { success: 'Tagging complete', partialFailure: 'Some tagging runs failed' },
    })
    loadVocabulary()
  }, [runEnrichment, selectedIds, loadVocabulary])

  const handleTagsChange = useCallback(
    (id: number, quoteTags: QuoteTag[]) => {
      patchRows((prev) => prev.map((quote) => (quote.id === id ? { ...quote, tags: quoteTags } : quote)))
    },
    [patchRows],
  )

  const requestUnapprove = useCallback(() => {
    if (selectedIds.length === 0) return
    setUnapproveError(null)
    setUnapproveRequest(selectedIds)
  }, [selectedIds])

  const cancelUnapprove = useCallback(() => {
    if (unapproving) return
    setUnapproveRequest(null)
    setUnapproveError(null)
  }, [unapproving])

  const confirmUnapprove = useCallback(async () => {
    if (!unapproveRequest) return
    setUnapproving(true)
    setUnapproveError(null)
    const { results, cancelledItems } = await runChunked(
      'Unapproving',
      unapproveRequest,
      UNAPPROVE_CHUNK_SIZE,
      async (chunk) => (await bulkUnapproveQuotes({ quoteIds: chunk })).succeededIds,
    )
    removeFromSelection(results)
    const failed = unapproveRequest.length - results.length - cancelledItems
    if (failed > 0) {
      const unapproved = new Set(results)
      setUnapproveError(`${failed} of ${unapproveRequest.length} unapprovals failed`)
      setUnapproveRequest((prev) => prev?.filter((id) => !unapproved.has(id)) ?? null)
    } else {
      toast.success(`Unapproved ${results.length} quote${results.length === 1 ? '' : 's'}`)
      setUnapproveRequest(null)
    }
    setUnapproving(false)
    reload()
  }, [unapproveRequest, runChunked, removeFromSelection, reload])

  const columns = useMemo(
    (): DataTableColumn<QuoteListItem>[] => [
      { id: 'id', header: 'ID', track: '64px', minWidth: 64, cell: (quote) => quote.id },
      {
        id: 'text',
        header: 'Text',
        track: 'minmax(320px,3fr)',
        minWidth: 320,
        className: 'whitespace-normal',
        cell: (quote) => (
          <>
            {quote.interpretations.length > 0 && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Lightbulb
                    className="mr-1 inline-block h-3.5 w-3.5 -translate-y-px align-middle text-primary"
                    aria-label={`${quote.interpretations.length} interpretation${quote.interpretations.length === 1 ? '' : 's'}`}
                  />
                </TooltipTrigger>
                <TooltipContent>
                  Has {quote.interpretations.length} interpretation{quote.interpretations.length === 1 ? '' : 's'}
                </TooltipContent>
              </Tooltip>
            )}
            <HighlightedQuoteText
              text={quote.text}
              excerpts={quote.excerpts.filter((excerpt) => excerpt.meetsThresholds)}
              interpretations={quote.interpretations}
            />
          </>
        ),
      },
      {
        id: 'tags',
        header: 'Tags',
        track: 'minmax(200px,2fr)',
        minWidth: 200,
        className: 'whitespace-normal',
        cell: (quote) => (
          <QuoteTagsCell
            quoteId={quote.id}
            tags={quote.tags}
            excerpts={quote.excerpts.filter((excerpt) => excerpt.meetsThresholds)}
            vocabulary={vocabulary}
            disabled={busy}
            onTagsChange={handleTagsChange}
          />
        ),
      },
      {
        id: 'author',
        header: 'Author',
        track: 'minmax(100px,1fr)',
        minWidth: 100,
        cell: (quote) => quote.authorName ?? '—',
      },
      {
        id: 'source',
        header: 'Source',
        track: 'minmax(100px,1fr)',
        minWidth: 100,
        className: 'whitespace-normal',
        cell: (quote) => (
          <>
            {quote.sourceTitle ?? '—'}
            {quote.sourceDetail && <div className="text-xs text-muted-foreground">{quote.sourceDetail}</div>}
          </>
        ),
      },
      { id: 'language', header: 'Language', track: '88px', minWidth: 88, cell: (quote) => quote.language },
    ],
    [vocabulary, busy, handleTagsChange],
  )

  return (
    <div>
      {list.error && (
        <Alert variant="destructive" className="mb-4 border-destructive/30 bg-destructive/10">
          <AlertDescription>{list.error}</AlertDescription>
        </Alert>
      )}

      <QuotesFilterBar
        filters={filters}
        onChange={handleFiltersChange}
        onReset={resetFilters}
        options={filterOptions}
        tagNames={tagNames}
      />

      {!list.loading && (
        <SelectionToolbar
          selectedCount={selection.count}
          matchingCount={list.total}
          selectingAll={selection.selectingAll}
          selectAllError={selection.selectAllError}
          busy={busy}
          progress={chunked.progress}
          onSelectAllMatching={selectAllMatching}
          onClearSelection={selection.clear}
          onCancelRun={chunked.cancel}
        >
          <QuoteBulkActions
            selectedCount={selection.count}
            eligibleCount={eligibleIds.length}
            skippedCount={skippedCount}
            busy={busy}
            onExtractExcerpts={handleExtractExcerpts}
            onGenerateInterpretations={handleGenerateInterpretations}
            onGenerateTags={handleGenerateTags}
            onUnapprove={requestUnapprove}
          />
        </SelectionToolbar>
      )}

      {unapproveRequest && (
        <ConfirmDialog
          title={unapproveRequest.length === 1 ? 'Unapprove quote' : `Unapprove ${unapproveRequest.length} quotes`}
          description={`${unapproveRequest.length === 1 ? 'This quote' : `${unapproveRequest.length} quotes`} will be deleted from the library, along with excerpts, interpretations and tags. Linked imports return to pending.`}
          confirmLabel="Unapprove"
          submittingLabel="Unapproving…"
          submitting={unapproving}
          error={unapproveError}
          onCancel={cancelUnapprove}
          onConfirm={confirmUnapprove}
        />
      )}

      <DataTable
        rows={rows}
        getRowId={quoteId}
        columns={columns}
        selectedIds={selection.selectedIds}
        onToggleSelect={toggleSelect}
        onToggleSelectLoaded={toggleSelectLoaded}
        selectionDisabled={busy}
        loading={list.loading}
        loadingMore={list.loadingMore}
        hasMore={list.hasMore}
        onEndReached={list.loadMore}
        emptyMessage="No quotes match these filters."
      />

      <p className="mt-3 text-sm text-muted-foreground">
        {list.total} quotes{list.hasMore ? ` · ${rows.length} loaded, scroll for more` : ''}
      </p>
    </div>
  )
}
