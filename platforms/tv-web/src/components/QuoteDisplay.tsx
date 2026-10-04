import type { CSSProperties } from 'react'
import type { TextAlign } from '../composition/layout'
import type { TextStyle } from '../composition/contrast'
import { useFitText } from '../hooks/use-fit-text'
import type { NormalizedRect, PublicQuote } from '../types'
import { percentRect } from './percent-rect'

interface QuoteDisplayProps {
  quote: PublicQuote
  box: NormalizedRect
  align: TextAlign
  text: TextStyle
  stageHeight: number
}

/** The quote and its attribution, sized to fill [box] without overflowing it. */
export function QuoteDisplay({ quote, box, align, text, stageHeight }: QuoteDisplayProps) {
  const { boxRef, contentRef, fontSize } = useFitText<HTMLDivElement, HTMLDivElement>(
    stageHeight,
    `${quote.id}:${box.x}:${box.y}:${box.width}:${box.height}`,
  )
  const contentStyle: CSSProperties = {
    color: text.color,
    textShadow: text.textShadow ?? undefined,
    textAlign: align,
    fontSize: fontSize > 0 ? `${fontSize}px` : undefined,
  }

  return (
    <div ref={boxRef} className="absolute flex flex-col justify-center" style={percentRect(box)}>
      <div ref={contentRef} style={contentStyle}>
        <p className="leading-snug font-medium">“{quote.text}”</p>
        {(quote.author || quote.sourceTitle) && (
          <div className="mt-[0.9em] flex flex-col gap-[0.2em] text-[0.55em]" style={{ color: text.attributionColor }}>
            {quote.author && <p>— {quote.author}</p>}
            {quote.sourceTitle && (
              <p>
                {quote.sourceTitle}
                {quote.sourceDetail && `, ${quote.sourceDetail}`}
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
