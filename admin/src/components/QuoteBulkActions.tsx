import { memo } from 'react'
import { Button } from '@/components/ui/button'

interface QuoteBulkActionsProps {
  selectedCount: number
  eligibleCount: number
  skippedCount: number
  busy: boolean
  onExtractExcerpts: () => void
  onGenerateInterpretations: () => void
  onGenerateTags: () => void
  onAssessQuality: () => void
  onUnapprove: () => void
}

const bulkActionClassName = 'border-primary bg-brand-tint text-primary hover:bg-brand-tint hover:text-primary'

/** Bulk actions for the approved-quotes page, shown inside the shared selection toolbar. */
function QuoteBulkActionsComponent({
  selectedCount,
  eligibleCount,
  skippedCount,
  busy,
  onExtractExcerpts,
  onGenerateInterpretations,
  onGenerateTags,
  onAssessQuality,
  onUnapprove,
}: QuoteBulkActionsProps) {
  return (
    <>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className={bulkActionClassName}
        onClick={onExtractExcerpts}
        disabled={busy || eligibleCount === 0}
      >
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
        className={bulkActionClassName}
        onClick={onGenerateInterpretations}
        disabled={busy}
      >
        Generate interpretations ({selectedCount})
      </Button>
      <span className="text-xs text-muted-foreground">
        Also generates interpretations for each quote's qualifying excerpts.
      </span>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className={bulkActionClassName}
        onClick={onGenerateTags}
        disabled={busy}
      >
        Generate tags ({selectedCount})
      </Button>
      <span className="text-xs text-muted-foreground">
        Quotes with excerpts are tagged through their excerpts only; tags you added or removed by hand are kept.
      </span>
      <Button
        type="button"
        variant="outline"
        size="sm"
        className={bulkActionClassName}
        onClick={onAssessQuality}
        disabled={busy}
      >
        Assess quality ({selectedCount})
      </Button>
      <span className="text-xs text-muted-foreground">
        Assesses each quote and its accepted excerpts as the TV shows them. Advisory only.
      </span>
      <Button type="button" variant="destructive" size="sm" onClick={onUnapprove} disabled={busy}>
        Unapprove ({selectedCount})
      </Button>
    </>
  )
}

export const QuoteBulkActions = memo(QuoteBulkActionsComponent)
