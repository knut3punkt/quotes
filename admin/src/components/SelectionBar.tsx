import { Button } from '@/components/ui/button'

interface SelectionBarProps {
  selectedCount: number
  visibleCount: number
  allVisibleSelected: boolean
  bulkBusy: boolean
  onToggleSelectAllVisible: () => void
  onClearSelection: () => void
  approveCount: number
  approveSkippedCount: number
  rejectCount: number
  duplicateCount: number
  resetCount: number
  onBulkApprove: () => void
  onBulkReject: () => void
  onBulkMarkDuplicate: () => void
  onBulkResetToPending: () => void
}

const bulkActionClassName =
  'border-primary bg-brand-tint text-primary hover:bg-brand-tint hover:text-primary'

export function SelectionBar({
  selectedCount,
  visibleCount,
  allVisibleSelected,
  bulkBusy,
  onToggleSelectAllVisible,
  onClearSelection,
  approveCount,
  approveSkippedCount,
  rejectCount,
  duplicateCount,
  resetCount,
  onBulkApprove,
  onBulkReject,
  onBulkMarkDuplicate,
  onBulkResetToPending,
}: SelectionBarProps) {
  return (
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center gap-2.5">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onToggleSelectAllVisible}
          disabled={bulkBusy || visibleCount === 0}
        >
          {allVisibleSelected ? `Deselect ${visibleCount} visible` : `Select all ${visibleCount} visible`}
        </Button>
        {selectedCount > 0 && (
          <>
            <span className="text-sm text-muted-foreground">{selectedCount} selected</span>
            <Button type="button" variant="outline" size="sm" onClick={onClearSelection} disabled={bulkBusy}>
              Clear selection
            </Button>
          </>
        )}
      </div>

      {selectedCount > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          {approveCount > 0 && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className={bulkActionClassName}
              onClick={onBulkApprove}
              disabled={bulkBusy}
            >
              Approve {approveCount}
            </Button>
          )}
          {rejectCount > 0 && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className={bulkActionClassName}
              onClick={onBulkReject}
              disabled={bulkBusy}
            >
              Reject {rejectCount}
            </Button>
          )}
          {duplicateCount > 0 && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className={bulkActionClassName}
              onClick={onBulkMarkDuplicate}
              disabled={bulkBusy}
            >
              Mark duplicate {duplicateCount}
            </Button>
          )}
          {resetCount > 0 && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className={bulkActionClassName}
              onClick={onBulkResetToPending}
              disabled={bulkBusy}
            >
              Reset {resetCount}
            </Button>
          )}
          {approveSkippedCount > 0 && (
            <span className="text-xs text-muted-foreground">
              {approveSkippedCount} without an author will be skipped by bulk approve — use the row's Approve
              button instead.
            </span>
          )}
        </div>
      )}
    </div>
  )
}
