import { memo } from 'react'
import { Button } from '@/components/ui/button'

interface ImportedQuoteBulkActionsProps {
  bulkBusy: boolean
  approveCount: number
  approveSkippedCount: number
  rejectCount: number
  duplicateCount: number
  resetCount: number
  deleteCount: number
  unapproveCount: number
  onBulkApprove: () => void
  onBulkReject: () => void
  onBulkMarkDuplicate: () => void
  onBulkResetToPending: () => void
  onBulkDelete: () => void
  onBulkUnapprove: () => void
}

const bulkActionClassName = 'border-primary bg-brand-tint text-primary hover:bg-brand-tint hover:text-primary'

/** Bulk actions for the imported-quotes page, shown inside the shared selection toolbar. */
function ImportedQuoteBulkActionsComponent({
  bulkBusy,
  approveCount,
  approveSkippedCount,
  rejectCount,
  duplicateCount,
  resetCount,
  deleteCount,
  unapproveCount,
  onBulkApprove,
  onBulkReject,
  onBulkMarkDuplicate,
  onBulkResetToPending,
  onBulkDelete,
  onBulkUnapprove,
}: ImportedQuoteBulkActionsProps) {
  return (
    <>
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
      {deleteCount > 0 && (
        <Button type="button" variant="destructive" size="sm" onClick={onBulkDelete} disabled={bulkBusy}>
          Delete {deleteCount}
        </Button>
      )}
      {unapproveCount > 0 && (
        <Button type="button" variant="destructive" size="sm" onClick={onBulkUnapprove} disabled={bulkBusy}>
          Unapprove {unapproveCount}
        </Button>
      )}
      {approveSkippedCount > 0 && (
        <span className="text-xs text-muted-foreground">
          {approveSkippedCount} without an author or source will be skipped by bulk approve — use the row's Approve
          button instead.
        </span>
      )}
    </>
  )
}

export const ImportedQuoteBulkActions = memo(ImportedQuoteBulkActionsComponent)
