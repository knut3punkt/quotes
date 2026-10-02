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
  interpreting: boolean
  tagging: boolean
  unapproving: boolean
  onToggleSelectAllVisible: () => void
  onClearSelection: () => void
  onExtractExcerpts: () => void
  onGenerateInterpretations: () => void
  onGenerateTags: () => void
  onUnapprove: () => void
}

function QuotesSelectionBarComponent({
  selectedCount,
  visibleCount,
  allVisibleSelected,
  eligibleCount,
  skippedCount,
  extracting,
  interpreting,
  tagging,
  unapproving,
  onToggleSelectAllVisible,
  onClearSelection,
  onExtractExcerpts,
  onGenerateInterpretations,
  onGenerateTags,
  onUnapprove,
}: QuotesSelectionBarProps) {
  const busy = extracting || interpreting || tagging || unapproving

  return (
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center gap-2.5">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onToggleSelectAllVisible}
          disabled={busy || visibleCount === 0}
        >
          {allVisibleSelected ? `Deselect ${visibleCount} visible` : `Select all ${visibleCount} visible`}
        </Button>
        {selectedCount > 0 && (
          <>
            <span className="text-sm text-muted-foreground">{selectedCount} selected</span>
            <Button type="button" variant="outline" size="sm" onClick={onClearSelection} disabled={busy}>
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
            disabled={busy || eligibleCount === 0}
          >
            {extracting && <Loader2 className="animate-spin" />}
            Extract excerpts {eligibleCount > 0 ? `(${eligibleCount})` : ''}
          </Button>
          {skippedCount > 0 && (
            <span className="text-xs text-muted-foreground">
              {skippedCount} selected quote{skippedCount === 1 ? ' is' : 's are'} too short and will be skipped.
            </span>
          )}
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="border-primary bg-brand-tint text-primary hover:bg-brand-tint hover:text-primary"
            onClick={onGenerateInterpretations}
            disabled={busy || selectedCount === 0}
          >
            {interpreting && <Loader2 className="animate-spin" />}
            Generate interpretations ({selectedCount})
          </Button>
          <span className="text-xs text-muted-foreground">
            Also generates interpretations for each quote's qualifying excerpts.
          </span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="border-primary bg-brand-tint text-primary hover:bg-brand-tint hover:text-primary"
            onClick={onGenerateTags}
            disabled={busy || selectedCount === 0}
          >
            {tagging && <Loader2 className="animate-spin" />}
            Generate tags ({selectedCount})
          </Button>
          <span className="text-xs text-muted-foreground">
            Quotes with excerpts are tagged through their excerpts only; tags you added or removed by hand are kept.
          </span>
          <Button type="button" variant="destructive" size="sm" onClick={onUnapprove} disabled={busy}>
            {unapproving && <Loader2 className="animate-spin" />}
            Unapprove ({selectedCount})
          </Button>
        </div>
      )}
    </div>
  )
}

export const QuotesSelectionBar = memo(QuotesSelectionBarComponent)
