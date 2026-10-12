import { useLayoutEffect, useState, type CSSProperties, type RefObject } from 'react'
import type { CompositionBackground } from '../composition/compose'
import type { TextAlign } from '../composition/layout'
import type { TextStyle } from '../composition/contrast'
import { useFitText } from '../hooks/use-fit-text'
import type { NormalizedRect, PublicQuote } from '../types'
import { FrostedGlass } from './FrostedGlass'
import { percentRect } from './percent-rect'

/** The backdrop's padding around the text, in em of the fitted quote font, so it scales with it. */
const BACKDROP_PADDING = '0.75em 1.05em'
/** Corner radius and background blur of the backdrop, as fractions of the stage height. */
const BACKDROP_RADIUS = 0.024
const BACKDROP_BLUR = 0.018

interface QuoteDisplayProps {
  quote: PublicQuote
  box: NormalizedRect
  align: TextAlign
  text: TextStyle
  /** What the backdrop shows blurred through its glass. */
  background: CompositionBackground
  backdropShadow: string
  stageHeight: number
}

/** Where the card sits on the stage, and the stage's size, in px; for lining up the blurred copy behind it. */
interface CardPlacement {
  left: number
  top: number
  stageWidth: number
  stageHeight: number
}

/**
 * The quote and its attribution, sized to fill [box] without overflowing it. When [text] has a backdrop, the
 * quote sits on a frosted card that hugs it (docs/features/quote-composition.md, "Rendering").
 */
export function QuoteDisplay({ quote, box, align, text, background, backdropShadow, stageHeight }: QuoteDisplayProps) {
  const { backdrop } = text
  const { boxRef, contentRef, fontSize } = useFitText<HTMLDivElement, HTMLDivElement>(
    stageHeight,
    `${quote.id}:${box.x}:${box.y}:${box.width}:${box.height}:${backdrop ? 'card' : 'bare'}`,
  )
  const placement = useCardPlacement(boxRef, contentRef, backdrop !== null, fontSize, stageHeight)

  const contentStyle: CSSProperties = {
    color: text.color,
    textShadow: text.textShadow ?? undefined,
    textAlign: align,
    fontSize: fontSize > 0 ? `${fontSize}px` : undefined,
  }
  const cardStyle: CSSProperties = backdrop
    ? {
        padding: BACKDROP_PADDING,
        borderRadius: `${stageHeight * BACKDROP_RADIUS}px`,
        boxShadow: backdropShadow,
      }
    : {}

  return (
    <div
      ref={boxRef}
      className="absolute flex flex-col justify-center"
      style={{ ...percentRect(box), alignItems: align === 'center' ? 'center' : 'flex-start' }}
    >
      <div ref={contentRef} className="relative" style={{ ...contentStyle, ...cardStyle }}>
        {backdrop && (
          // The glass clips its own copy of the background, so the stage-sized copy never counts as card
          // overflow when the font is fitted.
          <>
            <FrostedGlass
              background={background}
              copyPlacement={
                placement && {
                  left: -placement.left,
                  top: -placement.top,
                  width: placement.stageWidth,
                  height: placement.stageHeight,
                }
              }
              blurPx={stageHeight * BACKDROP_BLUR}
              tint={backdrop.tint}
              opacity={backdrop.opacity}
            />
            <div
              className="absolute inset-0"
              style={{ borderRadius: 'inherit', boxShadow: `inset 0 0 0 1px ${backdrop.hairline}` }}
            />
          </>
        )}
        <div className="relative">
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
    </div>
  )
}

/**
 * The card's offset from the stage's top left, measured after the font is fitted. Offsets ignore transforms,
 * so they match the untransformed background the blurred copy mirrors.
 */
function useCardPlacement(
  boxRef: RefObject<HTMLElement | null>,
  cardRef: RefObject<HTMLElement | null>,
  hasCard: boolean,
  fontSize: number,
  stageHeight: number,
): CardPlacement | null {
  const [placement, setPlacement] = useState<CardPlacement | null>(null)
  useLayoutEffect(() => {
    const box = boxRef.current
    const card = cardRef.current
    const stage = box?.offsetParent
    if (!hasCard || !box || !card || !(stage instanceof HTMLElement) || fontSize <= 0) return
    setPlacement({
      left: box.offsetLeft + card.offsetLeft,
      top: box.offsetTop + card.offsetTop,
      stageWidth: stage.clientWidth,
      stageHeight: stage.clientHeight,
    })
  }, [boxRef, cardRef, hasCard, fontSize, stageHeight])
  return placement
}
