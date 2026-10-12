import type { CompositionBackground, CompositionIcon } from '../composition/compose'
import type { IconStyle } from '../composition/contrast'
import { ICON_RING_FRACTION } from '../composition/layout'
import { FrostedGlass } from './FrostedGlass'
import { percentRect } from './percent-rect'

/** Background blur behind the motif, as a fraction of the stage height; as on the quote's backdrop. */
const ICON_BLUR = 0.018
/** The outer and inner edges of the ring, as fractions of the icon's diameter; the ring fills the rest. */
const OUTER_EDGE_FRACTION = 0.014
const INNER_EDGE_FRACTION = 0.01

interface MotifIconsProps {
  icons: CompositionIcon[]
  style: IconStyle
  shadow: string
  background: CompositionBackground
  stageHeight: number
  onError: (imageId: number) => void
}

/**
 * The quote's motifs as round icons (docs/features/quote-composition.md, "Rendering"): each a disc of frosted
 * glass with the motif in it, framed by a thick ring between two thin edges, alike for every icon.
 */
export function MotifIcons({ icons, style, shadow, background, stageHeight, onError }: MotifIconsProps) {
  return (
    <>
      {icons.map((icon) => {
        const { circle } = icon
        const diameter = circle.height * stageHeight
        const outer = Math.max(1, diameter * OUTER_EDGE_FRACTION)
        const ringOuter = diameter * (ICON_RING_FRACTION - INNER_EDGE_FRACTION)
        const ringTotal = diameter * ICON_RING_FRACTION
        return (
          <div
            key={icon.imageId}
            className="absolute overflow-hidden rounded-full"
            style={{ ...percentRect(circle), boxShadow: shadow }}
          >
            <FrostedGlass
              background={background}
              // The stage, relative to this circle, so the blurred copy lines up with the background.
              copyPlacement={{
                left: `${(-circle.x / circle.width) * 100}%`,
                top: `${(-circle.y / circle.height) * 100}%`,
                width: `${100 / circle.width}%`,
                height: `${100 / circle.height}%`,
              }}
              blurPx={stageHeight * ICON_BLUR}
              tint={style.tint}
              opacity={style.opacity}
            />
            <img
              src={icon.url}
              alt=""
              className="absolute max-w-none"
              style={percentRect(icon.canvas)}
              onError={() => onError(icon.imageId)}
            />
            <div
              className="absolute inset-0 rounded-full"
              style={{
                boxShadow: [
                  `inset 0 0 0 ${outer}px ${style.edge}`,
                  `inset 0 0 0 ${ringOuter}px ${style.ring}`,
                  `inset 0 0 0 ${ringTotal}px ${style.edge}`,
                ].join(', '),
              }}
            />
          </div>
        )
      })}
    </>
  )
}
