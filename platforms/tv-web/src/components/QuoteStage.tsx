import { useEffect, useState } from 'react'
import type { Composition } from '../composition/compose'
import type { PublicQuote } from '../types'
import { percentRect } from './percent-rect'
import { QuoteDisplay } from './QuoteDisplay'

interface QuoteStageProps {
  quote: PublicQuote
  composition: Composition
  stageHeight: number
  /** Called once the background can be shown (loaded, failed, or a gradient). */
  onReady: () => void
}

/**
 * One composed quote screen, in three layers (docs/features/quote-composition.md): the mood
 * background (slowly zooming), the motif element, and the quote, on a frosted backdrop when the
 * background under it is busy.
 */
export function QuoteStage({ quote, composition, stageHeight, onReady }: QuoteStageProps) {
  const { background, elements, quoteBox, align, text, elementFilter, backdropShadow } = composition
  const [failedElements, setFailedElements] = useState<ReadonlySet<number>>(new Set())

  useEffect(() => {
    if (background.kind === 'gradient') onReady()
  }, [background, onReady])

  return (
    <div className="absolute inset-0 overflow-hidden bg-black">
      {background.kind === 'image' ? (
        <img
          src={background.url}
          alt=""
          className="ken-burns absolute inset-0 h-full w-full object-cover"
          style={{ transformOrigin: background.zoomOrigin }}
          onLoad={onReady}
          onError={onReady}
        />
      ) : (
        <div className="absolute inset-0" style={{ background: background.css }} />
      )}

      {elements
        .filter((element) => !failedElements.has(element.imageId))
        .map((element) => (
          <img
            key={element.imageId}
            src={element.url}
            alt=""
            className="absolute"
            style={{ ...percentRect(element.rect), transform: `rotate(${element.rotation}deg)`, filter: elementFilter }}
            onError={() => setFailedElements((failed) => new Set(failed).add(element.imageId))}
          />
        ))}

      <QuoteDisplay
        quote={quote}
        box={quoteBox}
        align={align}
        text={text}
        background={background}
        backdropShadow={backdropShadow}
        stageHeight={stageHeight}
      />
    </div>
  )
}
