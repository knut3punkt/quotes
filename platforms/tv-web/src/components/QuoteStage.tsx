import { useCallback, useEffect, useState } from 'react'
import type { Composition } from '../composition/compose'
import type { PublicQuote } from '../types'
import { MotifIcons } from './MotifIcons'
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
 * background (slowly zooming), the motif icons, and the quote, on a frosted backdrop when the
 * background under it is busy.
 */
export function QuoteStage({ quote, composition, stageHeight, onReady }: QuoteStageProps) {
  const { background, icons, iconStyle, iconShadow, quoteBox, align, text, backdropShadow } = composition
  const [failedIcons, setFailedIcons] = useState<ReadonlySet<number>>(new Set())
  const onIconError = useCallback(
    (imageId: number) => setFailedIcons((failed) => new Set(failed).add(imageId)),
    [],
  )

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

      {iconStyle && (
        <MotifIcons
          icons={icons.filter((icon) => !failedIcons.has(icon.imageId))}
          style={iconStyle}
          shadow={iconShadow}
          background={background}
          stageHeight={stageHeight}
          onError={onIconError}
        />
      )}

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
