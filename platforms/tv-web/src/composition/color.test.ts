import { describe, expect, it } from 'vitest'
import { contrastRatio, hexToRgb, oklchToRgb, relativeLuminance, rgbToOklch } from './color'

describe('color', () => {
  it('matches WCAG luminance and contrast for black and white', () => {
    expect(relativeLuminance(hexToRgb('#ffffff'))).toBeCloseTo(1, 6)
    expect(relativeLuminance(hexToRgb('#000000'))).toBe(0)
    expect(contrastRatio(1, 0)).toBeCloseTo(21, 6)
  })

  it('matches the WCAG contrast of a known grey on white', () => {
    // #767676 is the classic lightest grey that passes 4.5:1 on white.
    expect(contrastRatio(relativeLuminance(hexToRgb('#767676')), 1)).toBeCloseTo(4.54, 2)
  })

  it('round-trips through OKLCH', () => {
    const rgb = hexToRgb('#3a7bd5')
    const back = oklchToRgb(rgbToOklch(rgb))
    back.forEach((channel, index) => expect(channel).toBeCloseTo(rgb[index], 4))
  })
})
