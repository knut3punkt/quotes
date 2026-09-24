import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { extractQuoteExcerpts, fetchAuthors, fetchQuotes } from '../api'
import { useDebouncedValue } from '../hooks/use-debounced-value'
import { toast } from '../hooks/use-toast'
import type { Author, QuoteExtractionOutcome, QuoteListItem } from '../types'
import { HighlightedQuoteText } from './HighlightedQuoteText'
import { QuotesSelectionBar } from './QuotesSelectionBar'

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

  const [search, setSearch] = useState('')
  const [verified, setVerified] = useState<'all' | 'true' | 'false'>('all')
  const [language, setLanguage] = useState('')
  const [authorId, setAuthorId] = useState<'all' | string>('all')

  const debouncedSearch = useDebouncedValue(search, FILTER_DEBOUNCE_MILLIS)
  const debouncedLanguage = useDebouncedValue(language, FILTER_DEBOUNCE_MILLIS)

  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  const [extracting, setExtracting] = useState(false)

  useEffect(() => {
    fetchAuthors().then(setAuthors).catch(() => undefined)
  }, [])

  useEffect(() => {
    setLoading(true)
    setLoadError(null)
    fetchQuotes({
      authorId: authorId === 'all' ? undefined : Number(authorId),
      verified: verified === 'all' ? undefined : verified === 'true',
      language: debouncedLanguage.trim() || undefined,
      search: debouncedSearch.trim() || undefined,
      page,
      pageSize: PAGE_SIZE,
    })
      .then((result) => {
        setQuotes(result.items)
        setTotal(result.total)
      })
      .catch((err) => setLoadError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [page, debouncedSearch, verified, debouncedLanguage, authorId])

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

  return (
    <div>
      {loadError && (
        <Alert variant="destructive" className="mb-4 border-destructive/30 bg-destructive/10">
          <AlertDescription>{loadError}</AlertDescription>
        </Alert>
      )}

      <div className="mb-5 flex flex-wrap gap-4 border-b border-border pb-4">
        <div className="flex flex-col gap-1">
          <Label htmlFor="quotes-search" className="text-xs font-normal text-muted-foreground">
            Search
          </Label>
          <Input
            id="quotes-search"
            type="search"
            value={search}
            placeholder="Quote text…"
            onChange={(event) => {
              setSearch(event.target.value)
              setPage(1)
            }}
            className="min-w-[220px]"
          />
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="quotes-verified" className="text-xs font-normal text-muted-foreground">
            Verified
          </Label>
          <Select
            value={verified}
            onValueChange={(value) => {
              setVerified(value as 'all' | 'true' | 'false')
              setPage(1)
            }}
          >
            <SelectTrigger id="quotes-verified" size="sm" className="min-w-[140px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              <SelectItem value="true">Verified</SelectItem>
              <SelectItem value="false">Unverified</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="quotes-language" className="text-xs font-normal text-muted-foreground">
            Language
          </Label>
          <Input
            id="quotes-language"
            value={language}
            placeholder="e.g. en"
            onChange={(event) => {
              setLanguage(event.target.value)
              setPage(1)
            }}
            className="w-24"
          />
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor="quotes-author" className="text-xs font-normal text-muted-foreground">
            Author
          </Label>
          <Select
            value={authorId}
            onValueChange={(value) => {
              setAuthorId(value)
              setPage(1)
            }}
          >
            <SelectTrigger id="quotes-author" size="sm" className="min-w-[160px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              {authors.map((author) => (
                <SelectItem key={author.id} value={String(author.id)}>
                  {author.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {!loading && quotes.length > 0 && (
        <QuotesSelectionBar
          selectedCount={selectedIds.size}
          visibleCount={quotes.length}
          allVisibleSelected={allVisibleSelected}
          eligibleCount={eligibleQuotes.length}
          skippedCount={skippedCount}
          extracting={extracting}
          onToggleSelectAllVisible={toggleSelectAllVisible}
          onClearSelection={clearSelection}
          onExtractExcerpts={handleExtractExcerpts}
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
                    disabled={extracting}
                    aria-label="Select all visible rows"
                  />
                </label>
              </TableHead>
              <TableHead>ID</TableHead>
              <TableHead>Text</TableHead>
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
                      disabled={extracting}
                      aria-label={`Select quote ${quote.id}`}
                    />
                  </label>
                </TableCell>
                <TableCell className="align-top">{quote.id}</TableCell>
                <TableCell className="max-w-[480px] align-top whitespace-normal">
                  <HighlightedQuoteText
                    text={quote.text}
                    excerpts={quote.excerpts.filter((excerpt) => excerpt.meetsThresholds)}
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
