import { memo, useMemo, useRef, useState } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { Loader2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { cn } from '@/lib/utils'
import { canApprove, canDelete, canMarkDuplicate, canReject, canResetToPending } from '../statusRules'
import { confidenceBadgeClassName, statusBadgeClassName } from '../statusBadgeClasses'
import type { ImportedQuote, ProcessingStatus, Source } from '../types'

interface BusyAction {
  id: number
  status: ProcessingStatus
}

interface ImportedQuotesTableProps {
  quotes: ImportedQuote[]
  sources: Source[]
  busyAction: BusyAction | null
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

// Row virtualization needs a fixed set of columns to align the header with each
// absolutely-positioned virtual row, so this grid replaces the semantic <table> markup
// components/ui/table.tsx provides elsewhere — a real <table> can't lay out rows that aren't
// direct DOM siblings of every other row.
const GRID_TEMPLATE =
  '40px minmax(240px,2fr) 130px minmax(140px,1fr) 110px 130px 130px 100px 90px 150px minmax(210px,1.5fr)'

const ROW_HEIGHT_ESTIMATE = 68
const VIEWPORT_HEIGHT = 640

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

const headerCellClassName = 'p-2 text-left align-middle font-medium whitespace-nowrap text-foreground'
const cellClassName = 'p-2 align-middle'

interface RowProps {
  quote: ImportedQuote
  source: Source | undefined
  selected: boolean
  expanded: boolean
  busy: boolean
  rowBusyStatus: ProcessingStatus | null
  onToggleExpanded: (id: number) => void
  onToggleSelect: (id: number) => void
  onApprove: (quote: ImportedQuote) => void
  onReject: (quote: ImportedQuote) => void
  onMarkDuplicate: (quote: ImportedQuote) => void
  onResetToPending: (quote: ImportedQuote) => void
  onDelete: (quote: ImportedQuote) => void
  bulkBusy: boolean
}

const Row = memo(function Row({
  quote,
  source,
  selected,
  expanded,
  busy,
  rowBusyStatus,
  onToggleExpanded,
  onToggleSelect,
  onApprove,
  onReject,
  onMarkDuplicate,
  onResetToPending,
  onDelete,
  bulkBusy,
}: RowProps) {
  const pageUrl = payloadString(quote.rawPayload, 'pageUrl')
  const citations = payloadStringArray(quote.rawPayload, 'citations')

  return (
    <div className="border-b border-border">
      <div
        className={cn('grid hover:bg-muted/50', selected && 'bg-brand-tint hover:bg-brand-tint')}
        style={{ gridTemplateColumns: GRID_TEMPLATE }}
      >
        <div className={cn(cellClassName, 'text-center')}>
          <label className="flex h-full min-h-11 w-full cursor-pointer items-center justify-center p-2.5">
            <Checkbox
              checked={selected}
              onCheckedChange={() => onToggleSelect(quote.id)}
              disabled={bulkBusy}
              aria-label={`Select quote ${quote.id}`}
            />
          </label>
        </div>
        <div className={cn(cellClassName, 'max-w-0 whitespace-normal')}>
          <button
            type="button"
            className="text-left text-foreground hover:text-primary"
            onClick={() => onToggleExpanded(quote.id)}
          >
            {expanded ? truncate(quote.rawText, 500) : truncate(quote.rawText, 90)}
          </button>
        </div>
        <div className={cellClassName}>{quote.rawAuthor ?? '—'}</div>
        <div className={cn(cellClassName, 'whitespace-normal')}>
          {source?.title ?? (quote.rawSourceTitle ? `${quote.rawSourceTitle} (suggested)` : '—')}
        </div>
        <div className={cellClassName}>{source?.translation ?? '—'}</div>
        <div className={cellClassName}>{quote.rawSourceLocation ?? '—'}</div>
        <div className={cellClassName}>{quote.provider}</div>
        <div className={cellClassName}>
          {quote.sourceConfidence ? (
            <Badge variant="outline" className={confidenceBadgeClassName(quote.sourceConfidence)}>
              {quote.sourceConfidence}
            </Badge>
          ) : (
            '—'
          )}
        </div>
        <div className={cellClassName}>
          <Badge variant="outline" className={statusBadgeClassName(quote.processingStatus)}>
            {quote.processingStatus}
          </Badge>
        </div>
        <div className={cn(cellClassName, 'whitespace-nowrap text-muted-foreground')}>
          {new Date(quote.importedAt).toLocaleString()}
        </div>
        <div className={cellClassName}>
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
          )}
        </div>
      </div>
      {expanded && (
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
      )}
    </div>
  )
})

function ImportedQuotesTableComponent({
  quotes,
  sources,
  busyAction,
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
  const sourcesById = useMemo(() => new Map(sources.map((source) => [source.id, source])), [sources])

  const toggleExpanded = (id: number) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const parentRef = useRef<HTMLDivElement>(null)
  const virtualizer = useVirtualizer({
    count: quotes.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT_ESTIMATE,
    overscan: 8,
    getItemKey: (index) => quotes[index].id,
  })

  if (quotes.length === 0) {
    return <p className="py-8 text-center text-muted-foreground">No imported quotes match the current filters.</p>
  }

  return (
    <div className="rounded-md border border-border">
      <div
        className="grid border-b border-border bg-card"
        style={{ gridTemplateColumns: GRID_TEMPLATE }}
      >
        <div className={cn(headerCellClassName, 'p-0 text-center')}>
          <label className="flex h-full min-h-11 w-full cursor-pointer items-center justify-center p-2.5">
            <Checkbox
              checked={selectAllChecked}
              onCheckedChange={onToggleSelectAllVisible}
              disabled={bulkBusy}
              aria-label="Select all visible rows"
            />
          </label>
        </div>
        <div className={headerCellClassName}>Quote</div>
        <div className={headerCellClassName}>Author</div>
        <div className={headerCellClassName}>Source</div>
        <div className={headerCellClassName}>Version</div>
        <div className={headerCellClassName}>Location</div>
        <div className={headerCellClassName}>Provider</div>
        <div className={headerCellClassName}>Confidence</div>
        <div className={headerCellClassName}>Status</div>
        <div className={headerCellClassName}>Imported</div>
        <div className={headerCellClassName}>Actions</div>
      </div>

      <div ref={parentRef} className="overflow-y-auto overflow-x-auto" style={{ height: VIEWPORT_HEIGHT }}>
        <div style={{ height: virtualizer.getTotalSize(), position: 'relative', width: '100%' }}>
          {virtualizer.getVirtualItems().map((virtualRow) => {
            const quote = quotes[virtualRow.index]
            const rowBusyStatus = busyAction?.id === quote.id ? busyAction.status : null
            const busy = rowBusyStatus !== null || bulkBusy
            const source = quote.sourceId !== null ? sourcesById.get(quote.sourceId) : undefined

            return (
              <div
                key={quote.id}
                data-index={virtualRow.index}
                ref={virtualizer.measureElement}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '100%',
                  transform: `translateY(${virtualRow.start}px)`,
                }}
              >
                <Row
                  quote={quote}
                  source={source}
                  selected={selectedIds.has(quote.id)}
                  expanded={expandedIds.has(quote.id)}
                  busy={busy}
                  rowBusyStatus={rowBusyStatus}
                  onToggleExpanded={toggleExpanded}
                  onToggleSelect={onToggleSelect}
                  onApprove={onApprove}
                  onReject={onReject}
                  onMarkDuplicate={onMarkDuplicate}
                  onResetToPending={onResetToPending}
                  onDelete={onDelete}
                  bulkBusy={bulkBusy}
                />
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

export const ImportedQuotesTable = memo(ImportedQuotesTableComponent)
