import { Checkbox } from '@/components/ui/checkbox'
import { cn } from '@/lib/utils'
import { useVirtualizer } from '@tanstack/react-virtual'
import { Loader2 } from 'lucide-react'
import { useEffect, useRef, type ReactNode } from 'react'

export interface DataTableColumn<T> {
  id: string
  header: ReactNode
  /** A CSS grid track, e.g. `minmax(80px,160px)`. */
  track: string
  /** Minimum width in pixels, used to size the horizontal scroll area. */
  minWidth: number
  cell: (row: T) => ReactNode
  className?: string
}

interface DataTableProps<T> {
  rows: T[]
  getRowId: (row: T) => number
  columns: DataTableColumn<T>[]
  selectedIds: ReadonlySet<number>
  onToggleSelect: (row: T) => void
  /** Selects every loaded row, or deselects them all when they already are. */
  onToggleSelectLoaded: () => void
  /** Disables the selection checkboxes, e.g. while a bulk action runs. */
  selectionDisabled: boolean
  /** Full-width content under a row's cells, such as expanded details. */
  renderDetails?: (row: T) => ReactNode
  loading: boolean
  loadingMore: boolean
  hasMore: boolean
  onEndReached: () => void
  emptyMessage: string
}

const SELECT_COLUMN_WIDTH = 40
const ROW_HEIGHT_ESTIMATE = 68
const VIEWPORT_HEIGHT = 640
const OVERSCAN = 8

// The checkbox header row has min-h-11 = 44px. The virtualizer uses it as its scroll margin, since the
// list starts right below the sticky header.
const HEADER_HEIGHT = 44

const headerCellClassName = 'p-2 text-left align-middle font-medium whitespace-nowrap text-foreground'
const cellClassName = 'p-2 align-top'

/**
 * A virtualized table on a CSS grid (header and rows share one grid template), loading more rows as the user scrolls
 * near the end. The header and rows sit in one width-constraining element inside the single scroll container, so
 * horizontal scrolling always moves them together.
 */
export function DataTable<T>({
  rows,
  getRowId,
  columns,
  selectedIds,
  onToggleSelect,
  onToggleSelectLoaded,
  selectionDisabled,
  renderDetails,
  loading,
  loadingMore,
  hasMore,
  onEndReached,
  emptyMessage,
}: DataTableProps<T>) {
  const parentRef = useRef<HTMLDivElement>(null)

  const gridTemplate = [`${SELECT_COLUMN_WIDTH}px`, ...columns.map((column) => column.track)].join(' ')
  const minWidth = SELECT_COLUMN_WIDTH + columns.reduce((sum, column) => sum + column.minWidth, 0)

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT_ESTIMATE,
    overscan: OVERSCAN,
    getItemKey: (index) => getRowId(rows[index]),
    scrollMargin: HEADER_HEIGHT,
  })

  const virtualItems = virtualizer.getVirtualItems()
  const lastRenderedIndex = virtualItems.length > 0 ? virtualItems[virtualItems.length - 1].index : -1

  useEffect(() => {
    if (hasMore && !loading && !loadingMore && lastRenderedIndex >= rows.length - 1 - OVERSCAN) onEndReached()
  }, [hasMore, loading, loadingMore, lastRenderedIndex, rows.length, onEndReached])

  if (loading) {
    return <p className="py-8 text-center text-muted-foreground">Loading…</p>
  }
  if (rows.length === 0) {
    return <p className="py-8 text-center text-muted-foreground">{emptyMessage}</p>
  }

  const loadedSelectedCount = rows.reduce((count, row) => count + (selectedIds.has(getRowId(row)) ? 1 : 0), 0)
  const headerChecked =
    loadedSelectedCount === rows.length ? true : loadedSelectedCount > 0 ? ('indeterminate' as const) : false

  return (
    <div ref={parentRef} className="overflow-auto rounded-md border border-border" style={{ height: VIEWPORT_HEIGHT }}>
      <div className="w-full" style={{ minWidth }}>
        <div
          className="sticky top-0 z-10 grid border-b border-border bg-card"
          style={{ gridTemplateColumns: gridTemplate, height: HEADER_HEIGHT }}
        >
          <div className={cn(headerCellClassName, 'p-0 text-center')}>
            <label className="flex h-full min-h-11 w-full cursor-pointer items-center justify-center p-2.5">
              <Checkbox
                checked={headerChecked}
                onCheckedChange={onToggleSelectLoaded}
                disabled={selectionDisabled}
                aria-label="Select all loaded rows"
              />
            </label>
          </div>
          {columns.map((column) => (
            <div key={column.id} className={headerCellClassName}>
              {column.header}
            </div>
          ))}
        </div>

        <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
          {virtualItems.map((virtualRow) => {
            const row = rows[virtualRow.index]
            const id = getRowId(row)
            const selected = selectedIds.has(id)
            return (
              <div
                key={id}
                data-index={virtualRow.index}
                ref={virtualizer.measureElement}
                className="border-b border-border"
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '100%',
                  // virtualRow.start is relative to the scroll element; the list starts below the header.
                  transform: `translateY(${virtualRow.start - HEADER_HEIGHT}px)`,
                }}
              >
                <div
                  className={cn('grid hover:bg-muted/50', selected && 'bg-brand-tint hover:bg-brand-tint')}
                  style={{ gridTemplateColumns: gridTemplate }}
                >
                  <div className="text-center">
                    <label className="flex h-full min-h-11 w-full cursor-pointer items-start justify-center p-2.5 pt-3">
                      <Checkbox
                        checked={selected}
                        onCheckedChange={() => onToggleSelect(row)}
                        disabled={selectionDisabled}
                        aria-label={`Select row ${id}`}
                      />
                    </label>
                  </div>
                  {columns.map((column) => (
                    <div key={column.id} className={cn(cellClassName, column.className)}>
                      {column.cell(row)}
                    </div>
                  ))}
                </div>
                {renderDetails?.(row)}
              </div>
            )
          })}
        </div>

        {loadingMore && (
          <div className="flex items-center justify-center gap-2 py-3 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" /> Loading more…
          </div>
        )}
      </div>
    </div>
  )
}
