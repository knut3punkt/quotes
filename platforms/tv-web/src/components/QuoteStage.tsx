import { useEffect, useState } from 'react'
import type { Composition } from '../composition/compose'
import type { PublicQuote } from '../types'
import { percentRect } from './percent-rect'
import { QuoteDisplay } from './QuoteDisplay'

/** How much the scrim reaches past the quote box before its feathered edge starts, per side. */
const SCRIM_INSET_X = 0.02
const SCRIM_INSET_Y = 0.03

interface QuoteStageProps {
  quote: PublicQuote
  composition: Composition
  stageHeight: number
  /** Called once the background can be shown (loaded, failed, or a gradient). */
  onReady: () => void
}

/**
 * One composed quote screen, in three layers (docs/features/quote-composition.md): the mood
 * background (slowly zooming), the collage elements, and the quote, with any scrim between the
 * background and the text.
 */
export function QuoteStage({ quote, composition, stageHeight, onReady }: QuoteStageProps) {
  const { background, elements, quoteBox, align, text, elementFilter } = composition
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

      {text.scrim && (
        <div
          className="absolute rounded-[6vh]"
          style={{
            ...percentRect({
              x: quoteBox.x + SCRIM_INSET_X,
              y: quoteBox.y + SCRIM_INSET_Y,
              width: quoteBox.width - 2 * SCRIM_INSET_X,
              height: quoteBox.height - 2 * SCRIM_INSET_Y,
            }),
            backgroundColor: text.scrim.color,
            boxShadow: `0 0 10vh 7vh ${text.scrim.color}`,
            opacity: text.scrim.opacity,
          }}
        />
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

      <QuoteDisplay quote={quote} box={quoteBox} align={align} text={text} stageHeight={stageHeight} />
    </div>
  )
}
