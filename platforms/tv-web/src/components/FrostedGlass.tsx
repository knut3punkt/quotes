import type { CSSProperties } from 'react'
import type { CompositionBackground } from '../composition/compose'

interface FrostedGlassProps {
  /** What the glass shows blurred. */
  background: CompositionBackground
  /** Where the stage-sized copy of the background goes, relative to the glass, so it lines up with the stage; null hides it until known. */
  copyPlacement: Pick<CSSProperties, 'left' | 'top' | 'width' | 'height'> | null
  blurPx: number
  tint: string
  opacity: number
}

/**
 * A pane of frosted, tinted glass filling its (positioned, rounded) parent (docs/features/quote-composition.md,
 * "Rendering"). Without `backdrop-filter`, which older webOS browsers lack and which is costly to redo every
 * frame over the zooming background: it clips a blurred copy of the background, zooming in step with it, and
 * lays the tint over it. Over the fallback gradient it is tint only.
 */
export function FrostedGlass({ background, copyPlacement, blurPx, tint, opacity }: FrostedGlassProps) {
  return (
    <div className="absolute inset-0 overflow-hidden" style={{ borderRadius: 'inherit' }}>
      {background.kind === 'image' && (
        <img
          src={background.url}
          alt=""
          className="ken-burns absolute max-w-none object-cover"
          style={{
            // Mounted at once, so its zoom starts with the background's; shown once lined up.
            visibility: copyPlacement ? 'visible' : 'hidden',
            ...(copyPlacement ?? { left: 0, top: 0, width: 0, height: 0 }),
            transformOrigin: background.zoomOrigin,
            filter: `blur(${blurPx}px) saturate(1.1)`,
          }}
        />
      )}
      <div className="absolute inset-0" style={{ backgroundColor: tint, opacity }} />
    </div>
  )
}
