import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { Lightbulb } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  bulkUnapproveQuotes,
  extractQuoteExcerpts,
  fetchAuthors,
  fetchQuoteFilterOptions,
  fetchQuotes,
  fetchSources,
  fetchTags,
  generateQuoteInterpretations,
  generateQuoteTags,
} from '../api'
import { useDebouncedValue } from '../hooks/use-debounced-value'
import { toast } from '../hooks/use-toast'
import type {
  Author,
  QuoteExtractionOutcome,
  QuoteFilterOptions,
  QuoteInterpretationOutcome,
  QuoteListItem,
  QuoteTag,
  QuoteTaggingOutcome,
  Source,
  TagFacet,
} from '../types'
import { ConfirmDialog } from './ConfirmDialog'
import { HighlightedQuoteText } from './HighlightedQuoteText'
import { DEFAULT_QUOTE_FILTERS, QuotesFilterBar, type QuoteFilterState } from './QuotesFilterBar'
import { QuotesSelectionBar } from './QuotesSelectionBar'
import { QuoteTagsCell } from './QuoteTagsCell'

const PAGE_SIZE = 50
const FILTER_DEBOUNCE_MILLIS = 250

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

const EMPTY_VOCABULARY: Record<TagFacet, string[]> = { concept: [], mood: [], motif: [] }

const EMPTY_FILTER_OPTIONS: QuoteFilterOptions = { providers: [], languages: [] }

const INTERPRETATION_OUTCOME_LABELS: Record<QuoteInterpretationOutcome, string> = {
  generated: 'generated',
  noInterpretationsFound: 'no interpretation found',
  failed: 'failed',
  notFound: 'not found',
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
}

function wordCount(text: string): number {
  return text.trim().split(/\s+/).filter(Boolean).length
}

export function QuotesPage() {
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [quotes, setQuotes] = useState<QuoteListItem[]>([])
  const [authors, setAuthors] = useState<Author[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [sources, setSources] = useState<Source[]>([])
  const [filterOptions, setFilterOptions] = useState<QuoteFilterOptions>(EMPTY_FILTER_OPTIONS)
  const [filters, setFilters] = useState<QuoteFilterState>(DEFAULT_QUOTE_FILTERS)

  const debouncedSearch = useDebouncedValue(filters.search, FILTER_DEBOUNCE_MILLIS)
  const debouncedLengthValue = useDebouncedValue(filters.lengthValue, FILTER_DEBOUNCE_MILLIS)
  const debouncedTag = useDebouncedValue(filters.tag, FILTER_DEBOUNCE_MILLIS)

  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  const [extracting, setExtracting] = useState(false)
  const [interpreting, setInterpreting] = useState(false)
  const [tagging, setTagging] = useState(false)
  const [vocabulary, setVocabulary] = useState<Record<TagFacet, string[]>>(EMPTY_VOCABULARY)
  const [unapproveRequest, setUnapproveRequest] = useState<number[] | null>(null)
  const [unapproving, setUnapproving] = useState(false)
  const [unapproveError, setUnapproveError] = useState<string | null>(null)
  const busy = extracting || interpreting || tagging || unapproving

  useEffect(() => {
    fetchAuthors().then(setAuthors).catch(() => undefined)
    fetchSources().then(setSources).catch(() => undefined)
    fetchQuoteFilterOptions().then(setFilterOptions).catch(() => undefined)
  }, [])

  const tagNames = useMemo(
    () => Array.from(new Set([...vocabulary.concept, ...vocabulary.mood, ...vocabulary.motif])).sort(),
    [vocabulary],
  )

  const handleFiltersChange = useCallback((patch: Partial<QuoteFilterState>) => {
    setFilters((prev) => ({ ...prev, ...patch }))
    setPage(1)
  }, [])

  const resetFilters = useCallback(() => {
    setFilters(DEFAULT_QUOTE_FILTERS)
    setPage(1)
  }, [])

  // Autocomplete suggestions for the add-tag input; refreshed after each generation run.
  const loadVocabulary = useCallback(() => {
    fetchTags()
      .then((tags) => {
        const byFacet: Record<TagFacet, string[]> = { concept: [], mood: [], motif: [] }
        for (const tag of tags) byFacet[tag.facet].push(tag.name)
        setVocabulary(byFacet)
      })
      .catch(() => undefined)
  }, [])

  useEffect(() => {
    loadVocabulary()
  }, [loadVocabulary])

  useEffect(() => {
    setLoading(true)
    setLoadError(null)
    const lengthThreshold = debouncedLengthValue.trim() === '' ? undefined : Number(debouncedLengthValue)
    const validLength = lengthThreshold !== undefined && Number.isFinite(lengthThreshold) ? lengthThreshold : undefined
    fetchQuotes({
      authorId: filters.authorId === 'all' ? undefined : Number(filters.authorId),
      sourceId: filters.sourceId === 'all' ? undefined : Number(filters.sourceId),
      verified: filters.verified === 'all' ? undefined : filters.verified === 'true',
      language: filters.language === 'all' ? undefined : filters.language,
      search: debouncedSearch.trim() || undefined,
      provider: filters.provider === 'all' ? undefined : filters.provider,
      sourceConfidence: filters.confidence === 'all' ? undefined : filters.confidence,
      minLength: filters.lengthOp === 'above' ? validLength : undefined,
      maxLength: filters.lengthOp === 'below' ? validLength : undefined,
      tag: debouncedTag.trim() || undefined,
      excerpts: filters.excerpts === 'all' ? undefined : filters.excerpts,
      interpretations: filters.interpretations === 'all' ? undefined : filters.interpretations,
      tags: filters.tags === 'all' ? undefined : filters.tags,
      page,
      pageSize: PAGE_SIZE,
    })
      .then((result) => {
        setQuotes(result.items)
        setTotal(result.total)
      })
      .catch((err) => setLoadError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [
    page,
    debouncedSearch,
    debouncedLengthValue,
    debouncedTag,
    filters.authorId,
    filters.sourceId,
    filters.verified,
    filters.language,
    filters.provider,
    filters.confidence,
    filters.lengthOp,
    filters.excerpts,
    filters.interpretations,
    filters.tags,
  ])

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  const allVisibleSelected = quotes.length > 0 && quotes.every((quote) => selectedIds.has(quote.id))

  const selectedQuotes = useMemo(
    () => quotes.filter((quote) => selectedIds.has(quote.id)),
    [quotes, selectedIds],
  )
  const eligibleQuotes = useMemo(
    () => selectedQuotes.filter((quote) => wordCount(quote.text) >= ESTIMATED_MIN_SOURCE_WORDS),
    [selectedQuotes],
  )
  const skippedCount = selectedQuotes.length - eligibleQuotes.length

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
        for (const quote of quotes) next.delete(quote.id)
      } else {
        for (const quote of quotes) next.add(quote.id)
      }
      return next
    })
  }, [allVisibleSelected, quotes])

  const clearSelection = useCallback(() => setSelectedIds(new Set()), [])

  const handleGenerateInterpretations = useCallback(async () => {
    const ids = selectedQuotes.map((quote) => quote.id)
    if (ids.length === 0) return
    setInterpreting(true)
    try {
      const response = await generateQuoteInterpretations(ids)
      const resultsById = new Map(response.results.map((result) => [result.quoteId, result]))

      setQuotes((prev) =>
        prev.map((quote) => {
          const result = resultsById.get(quote.id)
          if (!result) return quote
          // A not-found outcome never touched the database, so leave existing interpretations as-is.
          if (result.outcome === 'notFound') return quote
          return { ...quote, interpretations: result.interpretations }
        }),
      )

      const counts = response.results.reduce<Record<string, number>>((acc, result) => {
        acc[result.outcome] = (acc[result.outcome] ?? 0) + 1
        return acc
      }, {})
      const summary = Object.entries(counts)
        .map(([outcome, count]) => `${count} ${INTERPRETATION_OUTCOME_LABELS[outcome as QuoteInterpretationOutcome]}`)
        .join(', ')
      const failedCount = counts.failed ?? 0
      if (failedCount > 0) {
        toast.error('Some interpretation generations failed', summary)
      } else {
        toast.success('Interpretation generation complete', summary)
      }

      setSelectedIds((prev) => {
        const next = new Set(prev)
        for (const id of ids) next.delete(id)
        return next
      })
    } catch (err) {
      toast.error('Interpretation generation failed', errorMessage(err))
    } finally {
      setInterpreting(false)
    }
  }, [selectedQuotes])

  const handleGenerateTags = useCallback(async () => {
    const ids = selectedQuotes.map((quote) => quote.id)
    if (ids.length === 0) return
    setTagging(true)
    try {
      const response = await generateQuoteTags(ids)
      const resultsById = new Map(response.results.map((result) => [result.quoteId, result]))

      setQuotes((prev) =>
        prev.map((quote) => {
          const result = resultsById.get(quote.id)
          if (!result || result.outcome === 'notFound') return quote
          // The server returns every active tag on the quote, including admin-added ones.
          return { ...quote, tags: result.tags }
        }),
      )

      const counts = response.results.reduce<Record<string, number>>((acc, result) => {
        acc[result.outcome] = (acc[result.outcome] ?? 0) + 1
        return acc
      }, {})
      const summary = Object.entries(counts)
        .map(([outcome, count]) => `${count} ${TAGGING_OUTCOME_LABELS[outcome as QuoteTaggingOutcome]}`)
        .join(', ')
      if ((counts.failed ?? 0) > 0) {
        toast.error('Some tagging runs failed', summary)
      } else {
        toast.success('Tagging complete', summary)
      }

      setSelectedIds((prev) => {
        const next = new Set(prev)
        for (const id of ids) next.delete(id)
        return next
      })
      loadVocabulary()
    } catch (err) {
      toast.error('Tagging failed', errorMessage(err))
    } finally {
      setTagging(false)
    }
  }, [selectedQuotes, loadVocabulary])

  const handleTagsChange = useCallback((quoteId: number, tags: QuoteTag[]) => {
    setQuotes((prev) => prev.map((quote) => (quote.id === quoteId ? { ...quote, tags } : quote)))
  }, [])

  const handleExtractExcerpts = useCallback(async () => {
    const ids = selectedQuotes.map((quote) => quote.id)
    if (ids.length === 0) return
    setExtracting(true)
    try {
      const response = await extractQuoteExcerpts(ids)
      const resultsById = new Map(response.results.map((result) => [result.quoteId, result]))

      setQuotes((prev) =>
        prev.map((quote) => {
          const result = resultsById.get(quote.id)
          if (!result) return quote
          // A skipped/not-found outcome never touched the database, so leave existing highlights as-is.
          if (result.outcome === 'skippedTooShort' || result.outcome === 'notFound') return quote
          return { ...quote, excerpts: result.excerpts }
        }),
      )

      const counts = response.results.reduce<Record<string, number>>((acc, result) => {
        acc[result.outcome] = (acc[result.outcome] ?? 0) + 1
        return acc
      }, {})
      const summary = Object.entries(counts)
        .map(([outcome, count]) => `${count} ${OUTCOME_LABELS[outcome as QuoteExtractionOutcome]}`)
        .join(', ')
      const failedCount = counts.failed ?? 0
      if (failedCount > 0) {
        toast.error('Some extractions failed', summary)
      } else {
        toast.success('Extraction complete', summary)
      }

      setSelectedIds((prev) => {
        const next = new Set(prev)
        for (const id of ids) next.delete(id)
        return next
      })
    } catch (err) {
      toast.error('Extraction failed', errorMessage(err))
    } finally {
      setExtracting(false)
    }
  }, [selectedQuotes])

  const requestUnapprove = useCallback(() => {
    const ids = selectedQuotes.map((quote) => quote.id)
    if (ids.length === 0) return
    setUnapproveError(null)
    setUnapproveRequest(ids)
  }, [selectedQuotes])

  const cancelUnapprove = useCallback(() => {
    if (unapproving) return
    setUnapproveRequest(null)
    setUnapproveError(null)
  }, [unapproving])

  const confirmUnapprove = useCallback(async () => {
    if (!unapproveRequest) return
    setUnapproving(true)
    setUnapproveError(null)
    try {
      const result = await bulkUnapproveQuotes({ quoteIds: unapproveRequest })
      const unapproved = new Set(result.succeededIds)
      setQuotes((prev) => prev.filter((quote) => !unapproved.has(quote.id)))
      setTotal((prev) => Math.max(0, prev - unapproved.size))
      setSelectedIds((prev) => {
        const next = new Set(prev)
        for (const id of unapproved) next.delete(id)
        return next
      })
      if (result.failedIds.length > 0) {
        setUnapproveError(`${result.failedIds.length} of ${unapproveRequest.length} unapprovals failed`)
        setUnapproveRequest((prev) => prev?.filter((id) => !unapproved.has(id)) ?? null)
      } else {
        toast.success(`Unapproved ${unapproved.size} quote${unapproved.size === 1 ? '' : 's'}`)
        setUnapproveRequest(null)
      }
    } catch (err) {
      setUnapproveError(errorMessage(err))
    } finally {
      setUnapproving(false)
    }
  }, [unapproveRequest])

  return (
    <div>
      {loadError && (
        <Alert variant="destructive" className="mb-4 border-destructive/30 bg-destructive/10">
          <AlertDescription>{loadError}</AlertDescription>
        </Alert>
      )}

      <QuotesFilterBar
        filters={filters}
        onChange={handleFiltersChange}
        onReset={resetFilters}
        authors={authors}
        sources={sources}
        options={filterOptions}
        tagNames={tagNames}
      />

      {!loading && quotes.length > 0 && (
        <QuotesSelectionBar
          selectedCount={selectedIds.size}
          visibleCount={quotes.length}
          allVisibleSelected={allVisibleSelected}
          eligibleCount={eligibleQuotes.length}
          skippedCount={skippedCount}
          extracting={extracting}
          interpreting={interpreting}
          tagging={tagging}
          unapproving={unapproving}
          onToggleSelectAllVisible={toggleSelectAllVisible}
          onClearSelection={clearSelection}
          onExtractExcerpts={handleExtractExcerpts}
          onGenerateInterpretations={handleGenerateInterpretations}
          onGenerateTags={handleGenerateTags}
          onUnapprove={requestUnapprove}
        />
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

      {loading ? (
        <p className="py-8 text-center text-muted-foreground">Loading…</p>
      ) : quotes.length === 0 ? (
        <p className="py-8 text-center text-muted-foreground">No quotes match these filters.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-10">
                <label className="flex h-full w-full cursor-pointer items-center justify-center">
                  <Checkbox
                    checked={allVisibleSelected}
                    onCheckedChange={toggleSelectAllVisible}
                    disabled={busy}
                    aria-label="Select all visible rows"
                  />
                </label>
              </TableHead>
              <TableHead>ID</TableHead>
              <TableHead>Text</TableHead>
              <TableHead>Tags</TableHead>
              <TableHead>Author</TableHead>
              <TableHead>Source</TableHead>
              <TableHead>Verified</TableHead>
              <TableHead>Language</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {quotes.map((quote) => (
              <TableRow key={quote.id}>
                <TableCell className="text-center align-top">
                  <label className="flex h-full w-full cursor-pointer items-center justify-center">
                    <Checkbox
                      checked={selectedIds.has(quote.id)}
                      onCheckedChange={() => toggleSelect(quote.id)}
                      disabled={busy}
                      aria-label={`Select quote ${quote.id}`}
                    />
                  </label>
                </TableCell>
                <TableCell className="align-top">{quote.id}</TableCell>
                <TableCell className="max-w-[480px] align-top whitespace-normal">
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
                </TableCell>
                <TableCell className="max-w-[320px] align-top whitespace-normal">
                  <QuoteTagsCell
                    quoteId={quote.id}
                    tags={quote.tags}
                    excerpts={quote.excerpts.filter((excerpt) => excerpt.meetsThresholds)}
                    vocabulary={vocabulary}
                    disabled={busy}
                    onTagsChange={handleTagsChange}
                  />
                </TableCell>
                <TableCell className="align-top">{quote.authorName ?? '—'}</TableCell>
                <TableCell className="align-top">
                  {quote.sourceTitle ?? '—'}
                  {quote.sourceDetail && <div className="text-xs text-muted-foreground">{quote.sourceDetail}</div>}
                </TableCell>
                <TableCell className="align-top">
                  <Badge variant={quote.verified ? 'default' : 'secondary'}>
                    {quote.verified ? 'Verified' : 'Unverified'}
                  </Badge>
                </TableCell>
                <TableCell className="align-top">{quote.language}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <div className="mt-4 flex items-center justify-between gap-4">
        <span className="text-sm text-muted-foreground">{total} quotes</span>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page === 1}
            onClick={() => setPage((prev) => Math.max(1, prev - 1))}
          >
            Prev
          </Button>
          <span className="text-sm text-muted-foreground">
            Page {page} of {totalPages}
          </span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={page * PAGE_SIZE >= total}
            onClick={() => setPage((prev) => prev + 1)}
          >
            Next
          </Button>
        </div>
      </div>
    </div>
  )
}
