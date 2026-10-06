import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Loader2 } from 'lucide-react'
import { useCallback, useMemo, useState } from 'react'
import { confidenceBadgeClassName, statusBadgeClassName } from '../statusBadgeClasses'
import { canApprove, canDelete, canMarkDuplicate, canReject, canResetToPending } from '../statusRules'
import type { ImportedQuote, ProcessingStatus, Source } from '../types'
import { DataTable, type DataTableColumn } from './data-table/DataTable'

interface BusyAction {
  id: number
  status: ProcessingStatus
}

interface ImportedQuotesTableProps {
  quotes: ImportedQuote[]
  sources: Source[]
  loading: boolean
  loadingMore: boolean
  hasMore: boolean
  onEndReached: () => void
  busyAction: BusyAction | null
  bulkBusy: boolean
  selectedIds: ReadonlySet<number>
  onToggleSelect: (quote: ImportedQuote) => void
  onToggleSelectLoaded: () => void
  onApprove: (quote: ImportedQuote) => void
  onReject: (quote: ImportedQuote) => void
  onMarkDuplicate: (quote: ImportedQuote) => void
  onResetToPending: (quote: ImportedQuote) => void
  onDelete: (quote: ImportedQuote) => void
}

const NARROW_TRACK = 'minmax(80px,160px)'

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

function QuoteDetails({ quote }: { quote: ImportedQuote }) {
  const pageUrl = payloadString(quote.rawPayload, 'pageUrl')
  const citations = payloadStringArray(quote.rawPayload, 'citations')
  return (
    <div className="bg-card p-2">
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
    </div>
  )
}

export function ImportedQuotesTable({
  quotes,
  sources,
  loading,
  loadingMore,
  hasMore,
  onEndReached,
  busyAction,
  bulkBusy,
  selectedIds,
  onToggleSelect,
  onToggleSelectLoaded,
  onApprove,
  onReject,
  onMarkDuplicate,
  onResetToPending,
  onDelete,
}: ImportedQuotesTableProps) {
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set())

  const sourcesById = useMemo(() => new Map(sources.map((source) => [source.id, source])), [sources])

  const toggleExpanded = useCallback((id: number) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  const columns = useMemo((): DataTableColumn<ImportedQuote>[] => {
    const sourceOf = (quote: ImportedQuote) =>
      quote.sourceId !== null ? sourcesById.get(quote.sourceId) : undefined
    return [
      {
        id: 'quote',
        header: 'Quote',
        track: 'minmax(240px,2fr)',
        minWidth: 240,
        className: 'whitespace-normal',
        cell: (quote) => (
          <button
            type="button"
            className="text-left text-foreground hover:text-primary"
            onClick={() => toggleExpanded(quote.id)}
          >
            {expandedIds.has(quote.id) ? truncate(quote.rawText, 500) : truncate(quote.rawText, 90)}
          </button>
        ),
      },
      { id: 'author', header: 'Author', track: NARROW_TRACK, minWidth: 80, cell: (quote) => quote.rawAuthor ?? '—' },
      {
        id: 'source',
        header: 'Source',
        track: NARROW_TRACK,
        minWidth: 80,
        className: 'whitespace-normal',
        cell: (quote) =>
          sourceOf(quote)?.title ?? (quote.rawSourceTitle ? `${quote.rawSourceTitle} (suggested)` : '—'),
      },
      {
        id: 'version',
        header: 'Version',
        track: NARROW_TRACK,
        minWidth: 80,
        cell: (quote) => sourceOf(quote)?.translation ?? '—',
      },
      {
        id: 'location',
        header: 'Location',
        track: NARROW_TRACK,
        minWidth: 80,
        cell: (quote) => quote.rawSourceLocation ?? '—',
      },
      { id: 'provider', header: 'Provider', track: NARROW_TRACK, minWidth: 80, cell: (quote) => quote.provider },
      {
        id: 'confidence',
        header: 'Confidence',
        track: NARROW_TRACK,
        minWidth: 80,
        cell: (quote) =>
          quote.sourceConfidence ? (
            <Badge variant="outline" className={confidenceBadgeClassName(quote.sourceConfidence)}>
              {quote.sourceConfidence}
            </Badge>
          ) : (
            '—'
          ),
      },
      {
        id: 'status',
        header: 'Status',
        track: NARROW_TRACK,
        minWidth: 80,
        cell: (quote) => (
          <Badge variant="outline" className={statusBadgeClassName(quote.processingStatus)}>
            {quote.processingStatus}
          </Badge>
        ),
      },
      {
        id: 'imported',
        header: 'Imported',
        track: NARROW_TRACK,
        minWidth: 80,
        className: 'text-muted-foreground',
        cell: (quote) => new Date(quote.importedAt).toLocaleString(),
      },
      {
        id: 'actions',
        header: 'Actions',
        track: NARROW_TRACK,
        minWidth: 80,
        cell: (quote) => {
          if (quote.processingStatus === 'approved') {
            return <span className="text-sm text-success">Quote #{quote.quoteId}</span>
          }
          const rowBusyStatus = busyAction?.id === quote.id ? busyAction.status : null
          const busy = rowBusyStatus !== null || bulkBusy
          return (
            <div className="flex flex-wrap gap-1.5">
              {canApprove(quote.processingStatus) && (
                <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => onApprove(quote)}>
                  Approve
                </Button>
              )}
              {canReject(quote.processingStatus) && (
                <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => onReject(quote)}>
                  {rowBusyStatus === 'rejected' && <Loader2 className="animate-spin" />}
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
                  {rowBusyStatus === 'duplicate' && <Loader2 className="animate-spin" />}
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
                  {rowBusyStatus === 'pending' && <Loader2 className="animate-spin" />}
                  Reset
                </Button>
              )}
              {canDelete(quote.processingStatus) && (
                <Button type="button" variant="destructive" size="sm" disabled={busy} onClick={() => onDelete(quote)}>
                  Delete
                </Button>
              )}
            </div>
          )
        },
      },
    ]
  }, [
    sourcesById,
    expandedIds,
    toggleExpanded,
    busyAction,
    bulkBusy,
    onApprove,
    onReject,
    onMarkDuplicate,
    onResetToPending,
    onDelete,
  ])

  return (
    <DataTable
      rows={quotes}
      getRowId={(quote) => quote.id}
      columns={columns}
      selectedIds={selectedIds}
      onToggleSelect={onToggleSelect}
      onToggleSelectLoaded={onToggleSelectLoaded}
      selectionDisabled={bulkBusy}
      renderDetails={(quote) => (expandedIds.has(quote.id) ? <QuoteDetails quote={quote} /> : null)}
      loading={loading}
      loadingMore={loadingMore}
      hasMore={hasMore}
      onEndReached={onEndReached}
      emptyMessage="No imported quotes match the current filters."
    />
  )
}
