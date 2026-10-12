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
import { regionStats, type LuminanceField, type RegionStats } from './field'

/**
 * Minimum contrast between the text and the *worst* part of the background under it. Stricter than
 * WCAG's 3:1 for large text, because a TV is read from across the room, over an image.
 */
export const TARGET_CONTRAST = 4.5
/**
 * Above this mean detail under the quote, the background is textured enough to fight the letters, whatever
 * the luminance contrast says, and the quote gets a backdrop.
 */
export const QUIET_MEAN_DETAIL = 0.015
/**
 * Above this 90th-percentile detail, a few sharp or bright details (neon lines, filaments, hatching) cross
 * the quote. Cell means average them away, so they are caught by detail instead.
 */
export const QUIET_PEAK_DETAIL = 0.022
/** A backdrop fainter than this looks like an accident rather than a pane of glass. */
export const MIN_BACKDROP_OPACITY = 0.4
/** Past this the glass stops showing the background and becomes a box. */
export const MAX_BACKDROP_OPACITY = 0.8
/** Mean detail above this means texture under bare text, which a soft halo helps against. */
const HALO_DETAIL = 0.02

const LIGHT_TEXT = { start: 0.96, extreme: 0.99, chroma: 0.025 }
const DARK_TEXT = { start: 0.2, extreme: 0.12, chroma: 0.03 }
const LIGHTNESS_STEP = 0.01

export type TextTone = 'light' | 'dark'

/** Why the quote got a backdrop: texture, a few sharp details, too little contrast, or an `overlay` background. */
export type BackdropReason = 'texture' | 'detail' | 'contrast' | 'overlay'

/** A frosted, hue-tinted pane of glass behind the quote. */
export interface Backdrop {
  /** Opaque tint over the blurred background; [opacity] is applied to it separately. */
  tint: string
  opacity: number
  /** The 1 px inner edge that makes the pane read as glass. */
  hairline: string
  reason: BackdropReason
}

export interface TextStyle {
  tone: TextTone
  color: string
  attributionColor: string
  textShadow: string | null
  backdrop: Backdrop | null
  /** The contrast reached against the worst part of the region, after any backdrop. */
  contrast: number
  /** Detail under the quote, for `?debug`. */
  meanDetail: number
  peakDetail: number
}

/**
 * Chooses text colour and any help it needs over [region] of a background
 * (docs/features/quote-composition.md, "Text contrast"), gentlest means first:
 *
 * 1. light or dark text, whichever contrasts better with the worst part of the region;
 * 2. a colour tinted with the background's own hue rather than pure white or black;
 * 3. lightness pushed toward the extreme if the target isn't met;
 * 4. on a quiet region, bare text, with a soft halo when contrast is marginal;
 * 5. on a textured region, when the target still isn't met, or when [forceBackdrop] is set, a frosted backdrop
 *    in the background's hue, at the lowest opacity that meets the target within the backdrop's range.
 */
export function chooseTextStyle(
  field: LuminanceField,
  dominantColors: DominantColor[],
  region: NormalizedRect,
  /** For a background that reserved no room for the quote. */
  forceBackdrop = false,
): TextStyle {
  const stats = regionStats(field, region)
  const palette = tintedPalette(dominantColors)
  const { tone, lightness, rgb: text, worstBackground } = toneFor(stats, palette, TARGET_CONTRAST)
  const textLuminance = relativeLuminance(text)
  // What the halo and the backdrop's tint are made of.
  const shade = palette.shade(tone)

  const reason = backdropReason(
    forceBackdrop,
    stats.meanDetail,
    stats.peakDetail,
    contrastRatio(textLuminance, worstBackground) >= TARGET_CONTRAST,
  )
  let effectiveBackground: Rgb = greyWithLuminance(worstBackground)
  let backdrop: Backdrop | null = null
  if (reason) {
    const solved = solveBackdropOpacity(effectiveBackground, shade, textLuminance)
    const opacity = Math.min(MAX_BACKDROP_OPACITY, Math.max(MIN_BACKDROP_OPACITY, solved))
    backdrop = { tint: rgbToCss(shade), opacity, hairline: rgbToCss(text, 0.1), reason }
    effectiveBackground = blendRgb(effectiveBackground, shade, opacity)
  }
  const effectiveLuminance = relativeLuminance(effectiveBackground)
  const contrast = contrastRatio(textLuminance, effectiveLuminance)

  // On glass, only a faint tight shadow; bare text gets the soft halo when texture or contrast call for it.
  let textShadow: string | null = null
  if (backdrop) {
    textShadow = `0 0.03em 0.08em ${rgbToCss(shade, 0.35)}`
  } else if (stats.meanDetail > HALO_DETAIL || contrast < TARGET_CONTRAST * 1.3) {
    textShadow = `0 0 0.6em ${rgbToCss(shade, tone === 'light' ? 0.5 : 0.6)}, 0 0.04em 0.12em ${rgbToCss(shade, 0.4)}`
  }

  // The attribution is a step softer, but only when it still meets the target.
  const softer = palette.toneRgb(tone, tone === 'light' ? lightness - 0.1 : lightness + 0.12)
  const attribution = contrastRatio(relativeLuminance(softer), effectiveLuminance) >= TARGET_CONTRAST ? softer : text

  return {
    tone,
    color: rgbToCss(text),
    attributionColor: rgbToCss(attribution),
    textShadow,
    backdrop,
    contrast,
    meanDetail: stats.meanDetail,
    peakDetail: stats.peakDetail,
  }
}

/** Why the quote needs a backdrop, or null when the region is quiet and the text alone is readable. */
function backdropReason(
  forced: boolean,
  meanDetail: number,
  peakDetail: number,
  textAloneMeetsTarget: boolean,
): BackdropReason | null {
  if (forced) return 'overlay'
  if (meanDetail > QUIET_MEAN_DETAIL) return 'texture'
  if (peakDetail > QUIET_PEAK_DETAIL) return 'detail'
  if (!textAloneMeetsTarget) return 'contrast'
  return null
}

/** Colours tinted with the background's own hue: text-like tones, and the opposite-tone shade that backs them. */
interface TintedPalette {
  /** A light or dark colour at [lightness], tinted less toward the extreme. */
  toneRgb: (tone: TextTone, lightness: number) => Rgb
  /** The opposite of [tone] in the same hue: a deep (or pale) shade, never black or white. */
  shade: (tone: TextTone) => Rgb
}

function tintedPalette(dominantColors: DominantColor[]): TintedPalette {
  const hue = dominantHue(dominantColors)
  const h = hue?.h ?? 0
  const sourceChroma = hue?.c ?? 0
  return {
    toneRgb: (tone, lightness) => {
      const spec = tone === 'light' ? LIGHT_TEXT : DARK_TEXT
      // Tint fades out toward the extreme, where any chroma would push the colour out of gamut.
      const fade = Math.min(1, Math.abs(lightness - spec.extreme) / Math.abs(spec.start - spec.extreme) + 0.3)
      return oklchToRgb({ l: lightness, c: Math.min(sourceChroma, spec.chroma) * fade, h })
    },
    shade: (tone) =>
      oklchToRgb(
        tone === 'light' ? { l: 0.14, c: Math.min(sourceChroma, 0.04), h } : { l: 0.95, c: Math.min(sourceChroma, 0.03), h },
      ),
  }
}

interface ToneChoice {
  tone: TextTone
  lightness: number
  rgb: Rgb
  /** Luminance of the part of the region the colour contrasts worst with. */
  worstBackground: number
}

/**
 * Light or dark, whichever contrasts better with the worst part of the region, with its lightness pushed toward
 * the extreme until it meets [target] (or reaches the extreme).
 */
function toneFor(stats: RegionStats, palette: TintedPalette, target: number): ToneChoice {
  const lightWorst = contrastRatio(relativeLuminance(palette.toneRgb('light', LIGHT_TEXT.start)), stats.highLuminance)
  const darkWorst = contrastRatio(relativeLuminance(palette.toneRgb('dark', DARK_TEXT.start)), stats.lowLuminance)
  const tone: TextTone = lightWorst >= darkWorst ? 'light' : 'dark'
  const spec = tone === 'light' ? LIGHT_TEXT : DARK_TEXT
  const worstBackground = tone === 'light' ? stats.highLuminance : stats.lowLuminance

  let lightness = spec.start
  const direction = Math.sign(spec.extreme - spec.start)
  while (
    contrastRatio(relativeLuminance(palette.toneRgb(tone, lightness)), worstBackground) < target &&
    Math.abs(spec.extreme - lightness) > 1e-9
  ) {
    lightness = direction > 0 ? Math.min(spec.extreme, lightness + LIGHTNESS_STEP) : Math.max(spec.extreme, lightness - LIGHTNESS_STEP)
  }
  return { tone, lightness, rgb: palette.toneRgb(tone, lightness), worstBackground }
}

/**
 * Minimum contrast of the icons' ring against the background around it: WCAG's 3:1 for graphical objects. The
 * ring is a shape, not text.
 */
export const ICON_RING_CONTRAST = 3
/** The icons' glass: always enough to set the motif off, never an opaque disc. */
export const MIN_ICON_GLASS_OPACITY = 0.4
export const MAX_ICON_GLASS_OPACITY = 0.7
/** At this peak detail around the icons, the glass is at its most opaque, to quiet the scene behind the motifs. */
const ICON_BUSY_DETAIL = 0.08

/** How the motif icons are drawn; one style for all of them, so they read as a set. */
export interface IconStyle {
  tone: TextTone
  /** The thick ring, in the text's tinted tone. */
  ring: string
  /** The thin edges either side of the ring, in the opposite tone, so it reads on any background. */
  edge: string
  /** The glass disc's tint and its opacity over the blurred background. */
  tint: string
  opacity: number
  /** Contrast of the ring against the worst part of the background around the icons. */
  contrast: number
}

/**
 * Chooses the icons' colours over [region], the box around them (docs/features/quote-composition.md, "Icon
 * style"): a ring tinted like the quote text, light or dark by the background around the icons, edged in the
 * opposite tone; and frosted glass inside the ring, in the opposite tone, more opaque the busier the scene there.
 */
export function chooseIconStyle(field: LuminanceField, dominantColors: DominantColor[], region: NormalizedRect): IconStyle {
  const stats = regionStats(field, region)
  const palette = tintedPalette(dominantColors)
  const { tone, rgb: ring, worstBackground } = toneFor(stats, palette, ICON_RING_CONTRAST)
  const shade = palette.shade(tone)
  const busyness = Math.min(1, stats.peakDetail / ICON_BUSY_DETAIL)
  return {
    tone,
    ring: rgbToCss(ring),
    edge: rgbToCss(shade, 0.85),
    tint: rgbToCss(shade),
    opacity: MIN_ICON_GLASS_OPACITY + (MAX_ICON_GLASS_OPACITY - MIN_ICON_GLASS_OPACITY) * busyness,
    contrast: contrastRatio(relativeLuminance(ring), worstBackground),
  }
}

/**
 * The lowest backdrop opacity, up to the maximum, at which text of [textLuminance] meets the target over
 * [background] blended with [tint] (the way CSS composites).
 */
function solveBackdropOpacity(background: Rgb, tint: Rgb, textLuminance: number): number {
  const meets = (opacity: number) =>
    contrastRatio(textLuminance, relativeLuminance(blendRgb(background, tint, opacity))) >= TARGET_CONTRAST
  if (!meets(MAX_BACKDROP_OPACITY)) return MAX_BACKDROP_OPACITY
  let low = 0
  let high = MAX_BACKDROP_OPACITY
  for (let i = 0; i < 20; i++) {
    const middle = (low + high) / 2
    if (meets(middle)) high = middle
    else low = middle
  }
  return high
}
