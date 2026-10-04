import type { DominantColor, NormalizedRect, QuoteVisuals } from '../types'
import { hexToRgb, relativeLuminance, rgbToCss } from './color'
import { chooseTextStyle, type TextStyle } from './contrast'
import { fallbackBackground } from './fallback'
import { computeLayout, type Arrangement, type TextAlign } from './layout'
import { randomBetween, type Rng } from './random'

/** Pixel widths asked of the server; it snaps them to its cached derivative sizes. */
export const BACKGROUND_IMAGE_WIDTH = 1920
export const ELEMENT_IMAGE_WIDTH = 640

export type ImageUrl = (imageId: number, width: number) => string

export type CompositionBackground =
  | { kind: 'image'; url: string; zoomOrigin: string }
  | { kind: 'gradient'; css: string }

export interface CompositionElement {
  imageId: number
  url: string
  /** The element's whole canvas, in fractions of the stage. */
  rect: NormalizedRect
  rotation: number
}

/** Everything the stage needs to draw one quote screen, in fractions of a 16:9 stage. */
export interface Composition {
  background: CompositionBackground
  elements: CompositionElement[]
  quoteBox: NormalizedRect
  align: TextAlign
  text: TextStyle
  /** CSS `filter` that seats the elements in the scene with a shadow in the background's darkest colour. */
  elementFilter: string
  debug: {
    mood: string | null
    motifs: string[]
    arrangement: Arrangement
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
 * (or a fallback gradient), the element layout, and the text styling for the quote box.
 */
export function composeQuote(visuals: QuoteVisuals, imageUrl: ImageUrl, rng: Rng): Composition {
  const { background } = visuals
  const scene = background
    ? {
        field: { columns: background.layout.gridColumns, rows: background.layout.gridRows, cells: background.layout.cells },
        dominantColors: background.dominantColors,
        calmRegion: background.layout.calmRegion,
        layer: {
          kind: 'image' as const,
          url: imageUrl(background.imageId, BACKGROUND_IMAGE_WIDTH),
          zoomOrigin: `${Math.round(randomBetween(rng, 20, 80))}% ${Math.round(randomBetween(rng, 20, 80))}%`,
        },
      }
    : (() => {
        const fallback = fallbackBackground(visuals.elements, rng)
        return {
          field: fallback.field,
          dominantColors: fallback.dominantColors,
          calmRegion: null,
          layer: { kind: 'gradient' as const, css: fallback.css },
        }
      })()

  const layout = computeLayout(scene.field, scene.calmRegion, visuals.elements, rng)
  const text = chooseTextStyle(scene.field, scene.dominantColors, layout.quoteBox)
  const shadow = hexToRgb(darkestColor(scene.dominantColors))

  return {
    background: scene.layer,
    elements: layout.elements.map((placed) => ({
      imageId: placed.element.imageId,
      url: imageUrl(placed.element.imageId, ELEMENT_IMAGE_WIDTH),
      rect: placed.canvasRect,
      rotation: placed.rotation,
    })),
    quoteBox: layout.quoteBox,
    align: layout.align,
    text,
    elementFilter: `drop-shadow(0 0.6vh 1.4vh ${rgbToCss(shadow, 0.5)})`,
    debug: {
      mood: background?.tagName ?? null,
      motifs: layout.elements.map((placed) => placed.element.tagName),
      arrangement: layout.arrangement,
    },
  }
}

/** Every image URL a composition shows, for preloading. */
export function compositionImageUrls(composition: Composition): string[] {
  const urls = composition.elements.map((element) => element.url)
  return composition.background.kind === 'image' ? [composition.background.url, ...urls] : urls
}
