import type { ReactNode } from 'react'
import type { QuoteExcerpt } from '../types'

interface HighlightedQuoteTextProps {
  text: string
  excerpts: QuoteExcerpt[]
}

function scoreTooltip(excerpt: QuoteExcerpt): string {
  return [
    `independence: ${excerpt.independence}`,
    `completeness: ${excerpt.completeness}`,
    `quotability: ${excerpt.quotability}`,
    `contextualFidelity: ${excerpt.contextualFidelity}`,
  ].join(' · ')
}

/**
 * Renders `text` with each excerpt's [startOffset, endOffset) range highlighted inline. Ranges are
 * guaranteed non-overlapping by the backend's deterministic dedup logic, so a single left-to-right
 * pass is enough.
 */
export function HighlightedQuoteText({ text, excerpts }: HighlightedQuoteTextProps) {
  if (excerpts.length === 0) {
    return <>{text}</>
  }

  const ranges = [...excerpts].sort((a, b) => a.startOffset - b.startOffset)
  const segments: ReactNode[] = []
  let cursor = 0

  ranges.forEach((excerpt, index) => {
    if (excerpt.startOffset > cursor) {
      segments.push(text.slice(cursor, excerpt.startOffset))
    }
    segments.push(
      <mark key={excerpt.id ?? index} className="rounded-sm bg-brand-tint px-0.5 text-foreground" title={scoreTooltip(excerpt)}>
        {text.slice(excerpt.startOffset, excerpt.endOffset)}
      </mark>,
    )
    cursor = excerpt.endOffset
  })
  if (cursor < text.length) {
    segments.push(text.slice(cursor))
  }

  return <>{segments}</>
}
