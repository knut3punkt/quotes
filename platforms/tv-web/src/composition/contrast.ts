import type { DominantColor, NormalizedRect } from '../types'
import {
  blendRgb,
  contrastRatio,
  greyWithLuminance,
  oklchToRgb,
  relativeLuminance,
  rgbToCss,
  type Rgb,
} from './color'
import { dominantHue } from './fallback'
import { regionStats, type LuminanceField } from './field'

/**
 * Minimum contrast between the text and the *worst* part of the background under it. Stricter than
 * WCAG's 3:1 for large text, because a TV is read from across the room, over an image.
 */
export const TARGET_CONTRAST = 4.5
/** A scrim is a last resort; past this it stops looking like light and starts looking like a box. */
export const MAX_SCRIM_OPACITY = 0.7
/** Mean detail above this means texture under the text, which a soft halo helps against. */
const HALO_DETAIL = 0.02

const LIGHT_TEXT = { start: 0.96, extreme: 0.99, chroma: 0.025 }
const DARK_TEXT = { start: 0.2, extreme: 0.12, chroma: 0.03 }
const LIGHTNESS_STEP = 0.01

export type TextTone = 'light' | 'dark'

export interface Scrim {
  /** Opaque colour of the scrim; [opacity] is applied separately so it can be feathered. */
  color: string
  opacity: number
}

export interface TextStyle {
  tone: TextTone
  color: string
  attributionColor: string
  textShadow: string | null
  scrim: Scrim | null
  /** The contrast reached against the worst part of the region, after any scrim. */
  contrast: number
}

/**
 * Chooses text colour and any help it needs over [region] of a background
 * (docs/features/quote-composition.md, "Text contrast"), gentlest means first:
 *
 * 1. light or dark text, whichever contrasts better with the worst part of the region;
 * 2. a colour tinted with the background's own hue rather than pure white or black;
 * 3. lightness pushed toward the extreme if the target isn't met;
 * 4. a soft halo when the region is textured or contrast is marginal;
 * 5. a feathered scrim in the background's hue, at the lowest opacity that meets the target.
 */
export function chooseTextStyle(
  field: LuminanceField,
  dominantColors: DominantColor[],
  region: NormalizedRect,
): TextStyle {
  const stats = regionStats(field, region)
  const hue = dominantHue(dominantColors)
  const h = hue?.h ?? 0
  const sourceChroma = hue?.c ?? 0

  const textRgb = (tone: TextTone, lightness: number): Rgb => {
    const spec = tone === 'light' ? LIGHT_TEXT : DARK_TEXT
    // Tint fades out toward the extreme, where any chroma would push the colour out of gamut.
    const fade = Math.min(1, Math.abs(lightness - spec.extreme) / Math.abs(spec.start - spec.extreme) + 0.3)
    return oklchToRgb({ l: lightness, c: Math.min(sourceChroma, spec.chroma) * fade, h })
  }
  const contrastOver = (rgb: Rgb, backgroundLuminance: number) =>
    contrastRatio(relativeLuminance(rgb), backgroundLuminance)

  const lightWorst = contrastOver(textRgb('light', LIGHT_TEXT.start), stats.highLuminance)
  const darkWorst = contrastOver(textRgb('dark', DARK_TEXT.start), stats.lowLuminance)
  const tone: TextTone = lightWorst >= darkWorst ? 'light' : 'dark'
  const spec = tone === 'light' ? LIGHT_TEXT : DARK_TEXT
  const worstBackground = tone === 'light' ? stats.highLuminance : stats.lowLuminance

  let lightness = spec.start
  const direction = Math.sign(spec.extreme - spec.start)
  while (
    contrastOver(textRgb(tone, lightness), worstBackground) < TARGET_CONTRAST &&
    Math.abs(spec.extreme - lightness) > 1e-9
  ) {
    lightness = direction > 0 ? Math.min(spec.extreme, lightness + LIGHTNESS_STEP) : Math.max(spec.extreme, lightness - LIGHTNESS_STEP)
  }
  const text = textRgb(tone, lightness)
  const textLuminance = relativeLuminance(text)

  // The opposite tone in the same hue: what the halo and scrim are made of.
  const shade = oklchToRgb(
    tone === 'light' ? { l: 0.14, c: Math.min(sourceChroma, 0.04), h } : { l: 0.95, c: Math.min(sourceChroma, 0.03), h },
  )

  let effectiveBackground: Rgb = greyWithLuminance(worstBackground)
  let scrim: Scrim | null = null
  if (contrastOver(text, worstBackground) < TARGET_CONTRAST) {
    const opacity = solveScrimOpacity(effectiveBackground, shade, textLuminance)
    // A little extra, because the scrim's feathered edge is thinner than its middle.
    const applied = Math.min(MAX_SCRIM_OPACITY, opacity + 0.05)
    scrim = { color: rgbToCss(shade), opacity: applied }
    effectiveBackground = blendRgb(effectiveBackground, shade, applied)
  }
  const effectiveLuminance = relativeLuminance(effectiveBackground)
  const contrast = contrastRatio(textLuminance, effectiveLuminance)

  const needsHalo = stats.meanDetail > HALO_DETAIL || scrim !== null || contrast < TARGET_CONTRAST * 1.3
  const textShadow = needsHalo
    ? `0 0 0.6em ${rgbToCss(shade, tone === 'light' ? 0.5 : 0.6)}, 0 0.04em 0.12em ${rgbToCss(shade, 0.4)}`
    : null

  // The attribution is a step softer, but only when it still meets the target.
  const softer = textRgb(tone, tone === 'light' ? lightness - 0.1 : lightness + 0.12)
  const attribution = contrastRatio(relativeLuminance(softer), effectiveLuminance) >= TARGET_CONTRAST ? softer : text

  return {
    tone,
    color: rgbToCss(text),
    attributionColor: rgbToCss(attribution),
    textShadow,
    scrim,
    contrast,
  }
}

/** The lowest scrim opacity, up to the cap, at which text of [textLuminance] meets the target. */
function solveScrimOpacity(background: Rgb, scrim: Rgb, textLuminance: number): number {
  const meets = (opacity: number) =>
    contrastRatio(textLuminance, relativeLuminance(blendRgb(background, scrim, opacity))) >= TARGET_CONTRAST
  if (!meets(MAX_SCRIM_OPACITY)) return MAX_SCRIM_OPACITY
  let low = 0
  let high = MAX_SCRIM_OPACITY
  for (let i = 0; i < 20; i++) {
    const middle = (low + high) / 2
    if (meets(middle)) high = middle
    else low = middle
  }
  return high
}
