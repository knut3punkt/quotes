import { parseQuoteLayout, type DominantColor, type NormalizedRect, type QuoteVisuals } from '../types'
import { hexToRgb, relativeLuminance, rgbToCss } from './color'
import { chooseIconStyle, chooseTextStyle, type IconStyle, type TextStyle } from './contrast'
import { fallbackBackground } from './fallback'
import { computeLayout, type Arrangement, type Dock, type TextAlign } from './layout'
import { randomBetween, type Rng } from './random'

/** Pixel widths asked of the server; it snaps them to its cached derivative sizes. */
export const BACKGROUND_IMAGE_WIDTH = 1920
export const ELEMENT_IMAGE_WIDTH = 640

export type ImageUrl = (imageId: number, width: number) => string

export type CompositionBackground =
  | { kind: 'image'; url: string; zoomOrigin: string }
  | { kind: 'gradient'; css: string }

export interface CompositionIcon {
  imageId: number
  url: string
  /** The icon's circle, in fractions of the stage. */
  circle: NormalizedRect
  /** The element's whole canvas, in fractions of [circle]. */
  canvas: NormalizedRect
}

/** Everything the stage needs to draw one quote screen, in fractions of a 16:9 stage. */
export interface Composition {
  background: CompositionBackground
  /** The motifs as a row of icons; [iconStyle] is null when there are none. */
  icons: CompositionIcon[]
  iconStyle: IconStyle | null
  quoteBox: NormalizedRect
  align: TextAlign
  text: TextStyle
  /** CSS `box-shadow` that lifts the quote's backdrop and the icons off the scene, in the background's darkest colour. */
  backdropShadow: string
  iconShadow: string
  debug: {
    mood: string | null
    moodStyle: string | null
    /** The motifs shown as icons, with their styles. */
    motifs: { name: string; style: string | null }[]
    /** The layout the background's prompt asked for, whether or not it was used. */
    quoteLayout: string | null
    arrangement: Arrangement
    dock: Dock | null
  }
}

function darkestColor(colors: DominantColor[]): string {
  const darkest = [...colors].sort(
    (a, b) => relativeLuminance(hexToRgb(a.hex)) - relativeLuminance(hexToRgb(b.hex)),
  )[0]
  return darkest?.hex ?? '#000000'
}

/**
 * Turns the server's chosen images into a screen (docs/features/quote-composition.md): the background
 * (or a fallback gradient), where the quote and the motif icons go, and how the quote and icons are coloured.
 */
export function composeQuote(visuals: QuoteVisuals, imageUrl: ImageUrl, rng: Rng): Composition {
  const { background, elements } = visuals
  const scene = background
    ? {
        field: { columns: background.layout.gridColumns, rows: background.layout.gridRows, cells: background.layout.cells },
        dominantColors: background.dominantColors,
        quoteLayout: parseQuoteLayout(background.quoteLayout),
        layer: {
          kind: 'image' as const,
          url: imageUrl(background.imageId, BACKGROUND_IMAGE_WIDTH),
          zoomOrigin: `${Math.round(randomBetween(rng, 20, 80))}% ${Math.round(randomBetween(rng, 20, 80))}%`,
        },
      }
    : (() => {
        const fallback = fallbackBackground(elements, rng)
        return {
          field: fallback.field,
          dominantColors: fallback.dominantColors,
          quoteLayout: null,
          layer: { kind: 'gradient' as const, css: fallback.css },
        }
      })()

  const layout = computeLayout(scene.field, scene.quoteLayout, elements)
  // An `overlay` background reserved no room, on the promise that the quote brings "its own contrasting
  // background or translucent panel".
  const text = chooseTextStyle(scene.field, scene.dominantColors, layout.quoteBox, layout.arrangement === 'overlay')
  const iconStyle = layout.dockRect ? chooseIconStyle(scene.field, scene.dominantColors, layout.dockRect) : null
  const shadow = hexToRgb(darkestColor(scene.dominantColors))

  return {
    background: scene.layer,
    icons: layout.icons.map((icon) => ({
      imageId: icon.element.imageId,
      url: imageUrl(icon.element.imageId, ELEMENT_IMAGE_WIDTH),
      circle: icon.circle,
      canvas: icon.canvas,
    })),
    iconStyle,
    quoteBox: layout.quoteBox,
    align: layout.align,
    text,
    backdropShadow: `0 1.5vh 5vh ${rgbToCss(shadow, 0.45)}`,
    iconShadow: `0 0.6vh 2vh ${rgbToCss(shadow, 0.5)}`,
    debug: {
      mood: background?.tagName ?? null,
      moodStyle: background?.style ?? null,
      motifs: elements.map((element) => ({ name: element.tagName, style: element.style })),
      quoteLayout: background?.quoteLayout ?? null,
      arrangement: layout.arrangement,
      dock: layout.dock,
    },
  }
}

/** Every image URL a composition shows, for preloading. */
export function compositionImageUrls(composition: Composition): string[] {
  const urls = composition.icons.map((icon) => icon.url)
  return composition.background.kind === 'image' ? [composition.background.url, ...urls] : urls
}
