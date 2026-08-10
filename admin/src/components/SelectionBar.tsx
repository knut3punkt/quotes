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
    <div className="selection-bar">
      <div className="selection-summary">
        <button type="button" onClick={onToggleSelectAllVisible} disabled={bulkBusy || visibleCount === 0}>
          {allVisibleSelected ? `Deselect ${visibleCount} visible` : `Select all ${visibleCount} visible`}
        </button>
        {selectedCount > 0 && (
          <>
            <span className="selection-count">{selectedCount} selected</span>
            <button type="button" onClick={onClearSelection} disabled={bulkBusy}>
              Clear selection
            </button>
          </>
        )}
      </div>

      {selectedCount > 0 && (
        <div className="bulk-actions">
          {approveCount > 0 && (
            <button type="button" onClick={onBulkApprove} disabled={bulkBusy}>
              Approve {approveCount}
            </button>
          )}
          {rejectCount > 0 && (
            <button type="button" onClick={onBulkReject} disabled={bulkBusy}>
              Reject {rejectCount}
            </button>
          )}
          {duplicateCount > 0 && (
            <button type="button" onClick={onBulkMarkDuplicate} disabled={bulkBusy}>
              Mark duplicate {duplicateCount}
            </button>
          )}
          {resetCount > 0 && (
            <button type="button" onClick={onBulkResetToPending} disabled={bulkBusy}>
              Reset {resetCount}
            </button>
          )}
          {approveSkippedCount > 0 && (
            <span className="selection-note">
              {approveSkippedCount} without an author will be skipped by bulk approve — use the row's Approve
              button instead.
            </span>
          )}
        </div>
      )}
    </div>
  )
}
