/** Colour maths: sRGB, linear light, OKLab/OKLCH, and WCAG luminance and contrast. Channels are 0-1. */

export type Rgb = readonly [number, number, number]

export interface Oklch {
  l: number
  c: number
  /** Hue in degrees. */
  h: number
}

export function hexToRgb(hex: string): Rgb {
  const value = parseInt(hex.replace('#', ''), 16)
  return [((value >> 16) & 0xff) / 255, ((value >> 8) & 0xff) / 255, (value & 0xff) / 255]
}

export function srgbToLinear(channel: number): number {
  return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
}

export function linearToSrgb(channel: number): number {
  const clamped = Math.min(1, Math.max(0, channel))
  return clamped <= 0.0031308 ? clamped * 12.92 : 1.055 * clamped ** (1 / 2.4) - 0.055
}

/** WCAG 2 relative luminance. */
export function relativeLuminance([r, g, b]: Rgb): number {
  return 0.2126 * srgbToLinear(r) + 0.7152 * srgbToLinear(g) + 0.0722 * srgbToLinear(b)
}

/** WCAG 2 contrast ratio between two relative luminances, 1 to 21. */
export function contrastRatio(first: number, second: number): number {
  const lighter = Math.max(first, second)
  const darker = Math.min(first, second)
  return (lighter + 0.05) / (darker + 0.05)
}

/** The sRGB grey with the given relative luminance. */
export function greyWithLuminance(luminance: number): Rgb {
  const channel = linearToSrgb(luminance)
  return [channel, channel, channel]
}

export function rgbToOklch([r, g, b]: Rgb): Oklch {
  const lr = srgbToLinear(r)
  const lg = srgbToLinear(g)
  const lb = srgbToLinear(b)
  const l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
  const m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
  const s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
  const lightness = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s
  const a = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
  const bAxis = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
  const hue = (Math.atan2(bAxis, a) * 180) / Math.PI
  return { l: lightness, c: Math.hypot(a, bAxis), h: hue < 0 ? hue + 360 : hue }
}

/** OKLCH to sRGB, clipped to the sRGB gamut (the colours used here are low-chroma, so clipping is mild). */
export function oklchToRgb({ l: lightness, c, h }: Oklch): Rgb {
  const radians = (h * Math.PI) / 180
  const a = c * Math.cos(radians)
  const b = c * Math.sin(radians)
  const l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3
  const m = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3
  const s = (lightness - 0.0894841775 * a - 1.291485548 * b) ** 3
  return [
    linearToSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
    linearToSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
    linearToSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s),
  ]
}

/** Blends [top] over [bottom] in sRGB space, the way CSS composites a translucent layer. */
export function blendRgb(bottom: Rgb, top: Rgb, alpha: number): Rgb {
  return [
    bottom[0] * (1 - alpha) + top[0] * alpha,
    bottom[1] * (1 - alpha) + top[1] * alpha,
    bottom[2] * (1 - alpha) + top[2] * alpha,
  ]
}

/** `rgba(...)` rather than newer CSS colour syntax, which older webOS browsers lack. */
export function rgbToCss(rgb: Rgb, alpha = 1): string {
  const [r, g, b] = rgb.map((channel) => Math.round(Math.min(1, Math.max(0, channel)) * 255))
  return alpha >= 1 ? `rgb(${r}, ${g}, ${b})` : `rgba(${r}, ${g}, ${b}, ${Math.round(alpha * 1000) / 1000})`
}
