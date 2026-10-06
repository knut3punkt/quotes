import type { ReactNode } from 'react'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import type { ChunkedRunProgress } from '../hooks/use-chunked-run'

interface SelectionToolbarProps {
  selectedCount: number
  matchingCount: number
  selectingAll: boolean
  selectAllError: string | null
  /** True while any bulk action runs, chunked or not. */
  busy: boolean
  progress: ChunkedRunProgress | null
  onSelectAllMatching: () => void
  onClearSelection: () => void
  onCancelRun: () => void
  /** Page-specific bulk actions, shown while something is selected. */
  children: ReactNode
}

export function SelectionToolbar({
  selectedCount,
  matchingCount,
  selectingAll,
  selectAllError,
  busy,
  progress,
  onSelectAllMatching,
  onClearSelection,
  onCancelRun,
  children,
}: SelectionToolbarProps) {
  return (
    <div className="mb-4 flex flex-col gap-2.5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2.5">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onSelectAllMatching}
            disabled={busy || selectingAll || matchingCount === 0 || selectedCount === matchingCount}
          >
            {selectingAll && <Loader2 className="animate-spin" />}
            Select all {matchingCount} matching
          </Button>
          {selectedCount > 0 && (
            <>
              <span className="text-sm text-muted-foreground">
                {selectedCount} of {matchingCount} selected
              </span>
              <Button type="button" variant="outline" size="sm" onClick={onClearSelection} disabled={busy}>
                Clear selection
              </Button>
            </>
          )}
          {selectAllError && <span className="text-sm text-destructive">{selectAllError}</span>}
        </div>

        {selectedCount > 0 && <div className="flex flex-wrap items-center gap-2">{children}</div>}
      </div>

      {progress && (
        <div className="flex items-center gap-3">
          <span className="shrink-0 text-sm text-muted-foreground tabular-nums">
            {progress.label} {progress.done} of {progress.total}…
          </span>
          <Progress value={(progress.done / Math.max(1, progress.total)) * 100} className="h-1 flex-1" />
          <Button type="button" variant="outline" size="sm" onClick={onCancelRun} disabled={progress.cancelling}>
            {progress.cancelling ? 'Cancelling…' : 'Cancel'}
          </Button>
        </div>
      )}
    </div>
  )
}
