import { Fragment, useState } from 'react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { canApprove, canDelete, canMarkDuplicate, canReject, canResetToPending } from '../statusRules'
import { confidenceBadgeClassName, statusBadgeClassName } from '../statusBadgeClasses'
import type { ImportedQuote, Source } from '../types'

interface ImportedQuotesTableProps {
  quotes: ImportedQuote[]
  sources: Source[]
  busyId: number | null
  bulkBusy: boolean
  selectedIds: Set<number>
  allVisibleSelected: boolean
  onToggleSelect: (id: number) => void
  onToggleSelectAllVisible: () => void
  onApprove: (quote: ImportedQuote) => void
  onReject: (quote: ImportedQuote) => void
  onMarkDuplicate: (quote: ImportedQuote) => void
  onResetToPending: (quote: ImportedQuote) => void
  onDelete: (quote: ImportedQuote) => void
}

function truncate(text: string, maxLength: number): string {
  return text.length > maxLength ? `${text.slice(0, maxLength).trimEnd()}…` : text
}

function payloadString(payload: Record<string, unknown>, key: string): string | undefined {
  const value = payload[key]
  return typeof value === 'string' ? value : undefined
}

function payloadStringArray(payload: Record<string, unknown>, key: string): string[] {
  const value = payload[key]
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

export function ImportedQuotesTable({
  quotes,
  sources,
  busyId,
  bulkBusy,
  selectedIds,
  allVisibleSelected,
  onToggleSelect,
  onToggleSelectAllVisible,
  onApprove,
  onReject,
  onMarkDuplicate,
  onResetToPending,
  onDelete,
}: ImportedQuotesTableProps) {
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set())
  const someVisibleSelected = quotes.some((quote) => selectedIds.has(quote.id))
  const selectAllChecked = allVisibleSelected ? true : someVisibleSelected ? 'indeterminate' : false
  const sourcesById = new Map(sources.map((source) => [source.id, source]))

  const toggleExpanded = (id: number) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  if (quotes.length === 0) {
    return <p className="py-8 text-center text-muted-foreground">No imported quotes match the current filters.</p>
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead className="w-8 p-0 text-center">
            <label className="flex h-full min-h-11 w-full cursor-pointer items-center justify-center p-2.5">
              <Checkbox
                checked={selectAllChecked}
                onCheckedChange={onToggleSelectAllVisible}
                disabled={bulkBusy}
                aria-label="Select all visible rows"
              />
            </label>
          </TableHead>
          <TableHead>Quote</TableHead>
          <TableHead>Author</TableHead>
          <TableHead>Source</TableHead>
          <TableHead>Version</TableHead>
          <TableHead>Location</TableHead>
          <TableHead>Provider</TableHead>
          <TableHead>Confidence</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Imported</TableHead>
          <TableHead>Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {quotes.map((quote) => {
          const expanded = expandedIds.has(quote.id)
          const busy = busyId === quote.id || bulkBusy
          const pageUrl = payloadString(quote.rawPayload, 'pageUrl')
          const citations = payloadStringArray(quote.rawPayload, 'citations')
          const source = quote.sourceId !== null ? sourcesById.get(quote.sourceId) : undefined

          return (
            <Fragment key={quote.id}>
              <TableRow className={selectedIds.has(quote.id) ? 'bg-brand-tint hover:bg-brand-tint' : undefined}>
                <TableCell className="w-8 p-0 text-center">
                  <label className="flex h-full min-h-11 w-full cursor-pointer items-center justify-center p-2.5">
                    <Checkbox
                      checked={selectedIds.has(quote.id)}
                      onCheckedChange={() => onToggleSelect(quote.id)}
                      disabled={bulkBusy}
                      aria-label={`Select quote ${quote.id}`}
                    />
                  </label>
                </TableCell>
                <TableCell className="max-w-[420px] whitespace-normal align-top">
                  <button
                    type="button"
                    className="text-left text-foreground hover:text-primary"
                    onClick={() => toggleExpanded(quote.id)}
                  >
                    {expanded ? truncate(quote.rawText, 500) : truncate(quote.rawText, 90)}
                  </button>
                </TableCell>
                <TableCell className="align-top">{quote.rawAuthor ?? '—'}</TableCell>
                <TableCell className="align-top">{source?.title ?? '—'}</TableCell>
                <TableCell className="align-top">{source?.translation ?? '—'}</TableCell>
                <TableCell className="align-top">{quote.rawSourceLocation ?? '—'}</TableCell>
                <TableCell className="align-top">{quote.provider}</TableCell>
                <TableCell className="align-top">
                  {quote.sourceConfidence ? (
                    <Badge variant="outline" className={confidenceBadgeClassName(quote.sourceConfidence)}>
                      {quote.sourceConfidence}
                    </Badge>
                  ) : (
                    '—'
                  )}
                </TableCell>
                <TableCell className="align-top">
                  <Badge variant="outline" className={statusBadgeClassName(quote.processingStatus)}>
                    {quote.processingStatus}
                  </Badge>
                </TableCell>
                <TableCell className="whitespace-nowrap align-top text-muted-foreground">
                  {new Date(quote.importedAt).toLocaleString()}
                </TableCell>
                <TableCell className="align-top">
                  {quote.processingStatus === 'approved' ? (
                    <span className="text-sm text-success">Quote #{quote.quoteId}</span>
                  ) : (
                    <div className="flex flex-wrap gap-1.5">
                      {canApprove(quote.processingStatus) && (
                        <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => onApprove(quote)}>
                          Approve
                        </Button>
                      )}
                      {canReject(quote.processingStatus) && (
                        <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => onReject(quote)}>
                          Reject
                        </Button>
                      )}
                      {canMarkDuplicate(quote.processingStatus) && (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={busy}
                          onClick={() => onMarkDuplicate(quote)}
                        >
                          Mark duplicate
                        </Button>
                      )}
                      {canResetToPending(quote.processingStatus) && (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={busy}
                          onClick={() => onResetToPending(quote)}
                        >
                          Reset
                        </Button>
                      )}
                      {canDelete(quote.processingStatus) && (
                        <Button type="button" variant="destructive" size="sm" disabled={busy} onClick={() => onDelete(quote)}>
                          Delete
                        </Button>
                      )}
                    </div>
                  )}
                </TableCell>
              </TableRow>
              {expanded && (
                <TableRow>
                  <TableCell colSpan={11} className="whitespace-normal bg-card">
                    <div className="flex flex-col gap-1.5 text-[13px] text-muted-foreground">
                      <p>
                        <strong className="text-foreground">Full text:</strong> {quote.rawText}
                      </p>
                      {pageUrl && (
                        <p>
                          <strong className="text-foreground">Source page:</strong>{' '}
                          <a href={pageUrl} target="_blank" rel="noreferrer" className="text-primary hover:underline">
                            {pageUrl}
                          </a>
                        </p>
                      )}
                      {citations.length > 0 && (
                        <div>
                          <strong className="text-foreground">Citations:</strong>
                          <ul className="mt-1 list-disc pl-[18px]">
                            {citations.map((citation) => (
                              <li key={citation}>{citation}</li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              )}
            </Fragment>
          )
        })}
      </TableBody>
    </Table>
  )
}
