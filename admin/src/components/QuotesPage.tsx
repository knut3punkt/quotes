import { useEffect, useState } from 'react'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { fetchAuthors, fetchQuotes } from '../api'
import { useDebouncedValue } from '../hooks/use-debounced-value'
import type { Author, QuoteListItem } from '../types'

const PAGE_SIZE = 50
const FILTER_DEBOUNCE_MILLIS = 250

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'Something went wrong'
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

      {loading ? (
        <p className="py-8 text-center text-muted-foreground">Loading…</p>
      ) : quotes.length === 0 ? (
        <p className="py-8 text-center text-muted-foreground">No quotes match these filters.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
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
                <TableCell className="align-top">{quote.id}</TableCell>
                <TableCell className="max-w-[480px] align-top whitespace-normal">{quote.text}</TableCell>
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
