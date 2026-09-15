import { memo } from 'react'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'

interface SelectionBarProps {
  selectedCount: number
  visibleCount: number
  allVisibleSelected: boolean
  bulkBusy: boolean
  approveProgress: { done: number; total: number } | null
  onToggleSelectAllVisible: () => void
  onClearSelection: () => void
  approveCount: number
  approveSkippedCount: number
  rejectCount: number
  duplicateCount: number
  resetCount: number
  deleteCount: number
  onBulkApprove: () => void
  onBulkReject: () => void
  onBulkMarkDuplicate: () => void
  onBulkResetToPending: () => void
  onBulkDelete: () => void
}

const bulkActionClassName =
  'border-primary bg-brand-tint text-primary hover:bg-brand-tint hover:text-primary'

function SelectionBarComponent({
  selectedCount,
  visibleCount,
  allVisibleSelected,
  bulkBusy,
  approveProgress,
  onToggleSelectAllVisible,
  onClearSelection,
  approveCount,
  approveSkippedCount,
  rejectCount,
  duplicateCount,
  resetCount,
  deleteCount,
  onBulkApprove,
  onBulkReject,
  onBulkMarkDuplicate,
  onBulkResetToPending,
  onBulkDelete,
}: SelectionBarProps) {
  return (
    <div className="mb-4 flex flex-col gap-2.5">
      <div className="flex flex-wrap items-center justify-between gap-3">
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
                {approveProgress && <Loader2 className="animate-spin" />}
                {approveProgress
                  ? `Approving ${approveProgress.done} of ${approveProgress.total}…`
                  : `Approve ${approveCount}`}
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
                {bulkBusy && !approveProgress && <Loader2 className="animate-spin" />}
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
                {bulkBusy && !approveProgress && <Loader2 className="animate-spin" />}
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
                {bulkBusy && !approveProgress && <Loader2 className="animate-spin" />}
                Reset {resetCount}
              </Button>
            )}
            {deleteCount > 0 && (
              <Button type="button" variant="destructive" size="sm" onClick={onBulkDelete} disabled={bulkBusy}>
                Delete {deleteCount}
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

      {approveProgress && (
        <Progress value={(approveProgress.done / approveProgress.total) * 100} className="h-1" />
      )}
    </div>
  )
}

export const SelectionBar = memo(SelectionBarComponent)
