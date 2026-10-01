import type { ReactNode } from 'react'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import type { QuoteExcerpt, QuoteInterpretation } from '../types'

interface HighlightedQuoteTextProps {
  text: string
  excerpts: QuoteExcerpt[]
  interpretations: QuoteInterpretation[]
}

const EXCERPT_SCORE_FIELDS: { key: 'independence' | 'completeness' | 'quotability' | 'contextualFidelity'; label: string }[] = [
  { key: 'independence', label: 'Independence' },
  { key: 'completeness', label: 'Completeness' },
  { key: 'quotability', label: 'Quotability' },
  { key: 'contextualFidelity', label: 'Contextual fidelity' },
]

/**
 * Keeps the scrollable tooltip body within the space Radix reports as available on the chosen side
 * (minus the content's padding and border), so on short or narrow windows it shrinks and scrolls
 * instead of overflowing past the window edge.
 */
const TOOLTIP_BODY_CLASS =
  'flex max-h-[min(20rem,calc(var(--radix-tooltip-content-available-height)_-_1.25rem))] w-[min(40rem,calc(100vw_-_3rem))] flex-col gap-2 overflow-y-auto'

// Distance in px the tooltip keeps from the window edges.
const TOOLTIP_COLLISION_PADDING = 8

function InterpretationCards({ interpretations }: { interpretations: QuoteInterpretation[] }) {
  if (interpretations.length === 0) {
    return <p className="text-muted-foreground">No interpretations generated yet.</p>
  }

  return (
    <div className="flex flex-col gap-1.5">
      {interpretations.map((interpretation) => (
        <div key={interpretation.id} className="rounded-md border border-border bg-muted/40 px-2.5 py-1.5">
          <div className="mb-1 flex items-center justify-between gap-2">
            <Badge variant="secondary">{interpretation.lens}</Badge>
            <span className="text-xs whitespace-nowrap text-muted-foreground">
              support {interpretation.textualSupport} · speculative {interpretation.speculativeness}
            </span>
          </div>
          <p className="text-muted-foreground">{interpretation.interpretation}</p>
        </div>
      ))}
    </div>
  )
}

function ExcerptTooltipContent({ excerpt, interpretations }: { excerpt: QuoteExcerpt; interpretations: QuoteInterpretation[] }) {
  return (
    <div className={TOOLTIP_BODY_CLASS}>
      <div className="flex flex-wrap gap-1">
        {EXCERPT_SCORE_FIELDS.map(({ key, label }) => (
          <Badge key={key} variant="outline">
            {label}: {excerpt[key] ?? '–'}
          </Badge>
        ))}
      </div>
      {excerpt.reason && <p className="text-muted-foreground">{excerpt.reason}</p>}
      {excerpt.judgeNotes && <p className="whitespace-pre-line text-muted-foreground">{excerpt.judgeNotes}</p>}
      {excerpt.contextSignals.length > 0 && (
        <p className="text-xs text-muted-foreground">Checked references: {excerpt.contextSignals.join(', ')}</p>
      )}
      <InterpretationCards interpretations={interpretations} />
    </div>
  )
}

function WholeQuoteTooltipContent({ interpretations }: { interpretations: QuoteInterpretation[] }) {
  return (
    <div className={TOOLTIP_BODY_CLASS}>
      <span className="text-xs font-medium text-muted-foreground">Whole-quote interpretations</span>
      <InterpretationCards interpretations={interpretations} />
    </div>
  )
}

/**
 * Renders `text` with each excerpt's [startOffset, endOffset) range highlighted inline. Ranges are
 * guaranteed non-overlapping by the backend's deterministic dedup logic, so a single left-to-right
 * pass is enough. Hovering a highlighted excerpt shows its judge scores, the selector's reason, the
 * judge's notes, and its own interpretations (if any); hovering any other part of the quote text
 * shows the whole-quote's interpretations. A span with nothing to show (no excerpt data, no
 * interpretations) is rendered as plain text with no tooltip trigger.
 */
export function HighlightedQuoteText({ text, excerpts, interpretations }: HighlightedQuoteTextProps) {
  const wholeQuoteInterpretations = interpretations.filter((interpretation) => interpretation.excerptId === null)
  const interpretationsByExcerptId = new Map<number, QuoteInterpretation[]>()
  for (const interpretation of interpretations) {
    if (interpretation.excerptId === null) continue
    const group = interpretationsByExcerptId.get(interpretation.excerptId) ?? []
    group.push(interpretation)
    interpretationsByExcerptId.set(interpretation.excerptId, group)
  }

  function renderPlainSegment(segment: string, key: string): ReactNode {
    if (!segment) return null
    if (wholeQuoteInterpretations.length === 0) return segment
    return (
      <Tooltip key={key}>
        <TooltipTrigger asChild>
          <span>{segment}</span>
        </TooltipTrigger>
        <TooltipContent collisionPadding={TOOLTIP_COLLISION_PADDING}>
          <WholeQuoteTooltipContent interpretations={wholeQuoteInterpretations} />
        </TooltipContent>
      </Tooltip>
    )
  }

  if (excerpts.length === 0) {
    return <>{renderPlainSegment(text, 'text') ?? text}</>
  }

  const ranges = [...excerpts].sort((a, b) => a.startOffset - b.startOffset)
  const segments: ReactNode[] = []
  let cursor = 0

  ranges.forEach((excerpt, index) => {
    if (excerpt.startOffset > cursor) {
      segments.push(renderPlainSegment(text.slice(cursor, excerpt.startOffset), `pre-${excerpt.id ?? index}`))
    }
    segments.push(
      <Tooltip key={excerpt.id ?? index}>
        <TooltipTrigger asChild>
          <mark className="rounded-sm bg-brand-tint px-0.5 text-foreground">
            {text.slice(excerpt.startOffset, excerpt.endOffset)}
          </mark>
        </TooltipTrigger>
        <TooltipContent collisionPadding={TOOLTIP_COLLISION_PADDING}>
          <ExcerptTooltipContent excerpt={excerpt} interpretations={interpretationsByExcerptId.get(excerpt.id) ?? []} />
        </TooltipContent>
      </Tooltip>,
    )
    cursor = excerpt.endOffset
  })
  if (cursor < text.length) {
    segments.push(renderPlainSegment(text.slice(cursor), 'tail'))
  }

  return <>{segments}</>
}
