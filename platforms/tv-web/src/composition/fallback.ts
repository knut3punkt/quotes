import type { DominantColor, SelectedElement } from '../types'
import { hexToRgb, oklchToRgb, relativeLuminance, rgbToCss, rgbToOklch, type Rgb } from './color'
import type { LuminanceField } from './field'
import { randomBetween, type Rng } from './random'

/** Hue of the calm neutral slate used when nothing else suggests a colour. */
const NEUTRAL_HUE = 250
const NEUTRAL_CHROMA = 0.02
/** A dominant colour with less chroma than this is grey for the purpose of choosing a hue. */
export const MIN_HUE_CHROMA = 0.03

export interface FallbackBackground {
  css: string
  field: LuminanceField
  dominantColors: DominantColor[]
}

/** The hue and chroma of the heaviest clearly coloured entry in [colors], or null when all are grey. */
export function dominantHue(colors: DominantColor[]): { h: number; c: number } | null {
  const coloured = [...colors]
    .sort((a, b) => b.weight - a.weight)
    .map((color) => rgbToOklch(hexToRgb(color.hex)))
    .find((oklch) => oklch.c > MIN_HUE_CHROMA)
  return coloured ? { h: coloured.h, c: coloured.c } : null
}

function toHex(rgb: Rgb): string {
  return `#${rgb.map((channel) => Math.round(channel * 255).toString(16).padStart(2, '0')).join('')}`
}

/**
 * A deep gradient for a quote without a mood image (docs/features/quote-composition.md, "Fallback").
 * Its hue comes from the collage elements when there are any, so the screen still feels of a piece.
 * It also yields a luminance field, so layout and contrast run exactly as over a real background.
 */
export function fallbackBackground(elements: SelectedElement[], rng: Rng): FallbackBackground {
  const hue = dominantHue(elements.flatMap((element) => element.dominantColors))
  const h = hue?.h ?? NEUTRAL_HUE
  const c = hue ? Math.min(hue.c * 0.5, 0.05) : NEUTRAL_CHROMA
  const inner = oklchToRgb({ l: randomBetween(rng, 0.24, 0.3), c, h })
  const outer = oklchToRgb({ l: 0.13, c: c * 0.8, h: h + randomBetween(rng, -20, 20) })
  const centerX = randomBetween(rng, 0.35, 0.65)
  const centerY = randomBetween(rng, 0.3, 0.6)
  const angle = Math.round(randomBetween(rng, 0, 360))

  const innerLuminance = relativeLuminance(inner)
  const outerLuminance = relativeLuminance(outer)
  const columns = 16
  const rows = 9
  // Mirrors the radial gradient below: inner colour at the centre, outer from 75% of the way to the
  // farthest corner. Close enough for choosing text colour; both ends are dark.
  const farthest = Math.hypot(Math.max(centerX, 1 - centerX) * 16, Math.max(centerY, 1 - centerY) * 9)
  const cells = Array.from({ length: rows }, (_, row) =>
    Array.from({ length: columns }, (_, column) => {
      const distance = Math.hypot(((column + 0.5) / columns - centerX) * 16, ((row + 0.5) / rows - centerY) * 9)
      const t = Math.min(1, distance / (farthest * 0.75))
      return { luminance: innerLuminance + (outerLuminance - innerLuminance) * t, detail: 0 }
    }),
  )

  return {
    css: [
      `linear-gradient(${angle}deg, ${rgbToCss(inner, 0.25)}, ${rgbToCss(outer, 0)} 60%)`,
      `radial-gradient(ellipse at ${Math.round(centerX * 100)}% ${Math.round(centerY * 100)}%, ${rgbToCss(inner)} 0%, ${rgbToCss(outer)} 75%)`,
    ].join(', '),
    field: { columns, rows, cells },
    dominantColors: [
      { hex: toHex(inner), weight: 0.6 },
      { hex: toHex(outer), weight: 0.4 },
    ],
  }
}
