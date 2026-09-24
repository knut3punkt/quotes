import { memo } from 'react'
import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface QuotesSelectionBarProps {
  selectedCount: number
  visibleCount: number
  allVisibleSelected: boolean
  eligibleCount: number
  skippedCount: number
  extracting: boolean
  onToggleSelectAllVisible: () => void
  onClearSelection: () => void
  onExtractExcerpts: () => void
}

function QuotesSelectionBarComponent({
  selectedCount,
  visibleCount,
  allVisibleSelected,
  eligibleCount,
  skippedCount,
  extracting,
  onToggleSelectAllVisible,
  onClearSelection,
  onExtractExcerpts,
}: QuotesSelectionBarProps) {
  return (
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center gap-2.5">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onToggleSelectAllVisible}
          disabled={extracting || visibleCount === 0}
        >
          {allVisibleSelected ? `Deselect ${visibleCount} visible` : `Select all ${visibleCount} visible`}
        </Button>
        {selectedCount > 0 && (
          <>
            <span className="text-sm text-muted-foreground">{selectedCount} selected</span>
            <Button type="button" variant="outline" size="sm" onClick={onClearSelection} disabled={extracting}>
              Clear selection
            </Button>
          </>
        )}
      </div>

      {selectedCount > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="border-primary bg-brand-tint text-primary hover:bg-brand-tint hover:text-primary"
            onClick={onExtractExcerpts}
            disabled={extracting || eligibleCount === 0}
          >
            {extracting && <Loader2 className="animate-spin" />}
            Extract excerpts {eligibleCount > 0 ? `(${eligibleCount})` : ''}
          </Button>
          {skippedCount > 0 && (
            <span className="text-xs text-muted-foreground">
              {skippedCount} selected quote{skippedCount === 1 ? ' is' : 's are'} too short and will be skipped.
            </span>
          )}
        </div>
      )}
    </div>
  )
}

export const QuotesSelectionBar = memo(QuotesSelectionBarComponent)
