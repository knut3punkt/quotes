import { describe, expect, it } from 'vitest'
import {
  chooseIconStyle,
  chooseTextStyle,
  ICON_RING_CONTRAST,
  MAX_BACKDROP_OPACITY,
  MAX_ICON_GLASS_OPACITY,
  MIN_BACKDROP_OPACITY,
  MIN_ICON_GLASS_OPACITY,
  QUIET_MEAN_DETAIL,
  QUIET_PEAK_DETAIL,
  TARGET_CONTRAST,
} from './contrast'
import { regionStats } from './field'
import { fieldFrom } from './test-fixtures'

const wholeStage = { x: 0, y: 0, width: 1, height: 1 }
const colors = [{ hex: '#2f4a6b', weight: 0.7 }]

describe('chooseTextStyle', () => {
  it('uses light, bare text on a dark quiet background', () => {
    const style = chooseTextStyle(fieldFrom(() => ({ luminance: 0.03, detail: 0.005 })), colors, wholeStage)

    expect(style.tone).toBe('light')
    expect(style.backdrop).toBeNull()
    expect(style.textShadow).toBeNull()
    expect(style.contrast).toBeGreaterThanOrEqual(TARGET_CONTRAST)
  })

  it('uses dark, bare text on a light quiet background', () => {
    const style = chooseTextStyle(fieldFrom(() => ({ luminance: 0.85, detail: 0.005 })), colors, wholeStage)

    expect(style.tone).toBe('dark')
    expect(style.backdrop).toBeNull()
    expect(style.contrast).toBeGreaterThanOrEqual(TARGET_CONTRAST)
  })

  it('puts the quote on a backdrop over texture, even when the luminance contrast is fine', () => {
    const style = chooseTextStyle(
      fieldFrom(() => ({ luminance: 0.04, detail: QUIET_MEAN_DETAIL * 2 })),
      colors,
      wholeStage,
    )

    expect(style.backdrop?.reason).toBe('texture')
    expect(style.backdrop?.opacity).toBe(MIN_BACKDROP_OPACITY)
  })

  it('puts the quote on a backdrop when a few sharp details cross a quiet region', () => {
    // Two of sixteen columns carry bright lines; the mean stays quiet.
    const lines = fieldFrom((_, column) => ({ luminance: 0.03, detail: column % 8 === 0 ? 0.06 : 0.002 }))
    expect(regionStats(lines, wholeStage).meanDetail).toBeLessThanOrEqual(QUIET_MEAN_DETAIL)

    const style = chooseTextStyle(lines, colors, wholeStage)
    expect(style.backdrop?.reason).toBe('detail')
    expect(style.peakDetail).toBeGreaterThan(QUIET_PEAK_DETAIL)
  })

  it('solves the backdrop opacity over a quiet background that mixes bright and dark areas', () => {
    const mixed = fieldFrom((_, column) => ({ luminance: column < 8 ? 0.05 : 0.6, detail: 0.005 }))
    const style = chooseTextStyle(mixed, colors, wholeStage)

    expect(style.backdrop?.reason).toBe('contrast')
    expect(style.backdrop?.opacity).toBeGreaterThanOrEqual(MIN_BACKDROP_OPACITY)
    expect(style.backdrop?.opacity).toBeLessThanOrEqual(MAX_BACKDROP_OPACITY)
    expect(style.contrast).toBeGreaterThanOrEqual(TARGET_CONTRAST)
  })

  it('gives a backdrop on request, and only a faint shadow to text on it', () => {
    const quiet = fieldFrom(() => ({ luminance: 0.03, detail: 0.005 }))
    const style = chooseTextStyle(quiet, colors, wholeStage, true)

    expect(style.backdrop?.reason).toBe('overlay')
    expect(style.backdrop?.opacity).toBe(MIN_BACKDROP_OPACITY)
    expect(style.textShadow).not.toContain('0.6em')
  })

  it('reaches the target on every uniform grey', () => {
    for (let luminance = 0; luminance <= 1; luminance += 0.05) {
      const style = chooseTextStyle(fieldFrom(() => ({ luminance, detail: 0.01 })), colors, wholeStage)
      expect(style.contrast, `luminance ${luminance}`).toBeGreaterThanOrEqual(TARGET_CONTRAST)
    }
  })

  it('only looks at the region under the text', () => {
    const brightBusyLeft = fieldFrom((_, column) => ({
      luminance: column < 8 ? 0.9 : 0.02,
      detail: column < 8 ? 0.1 : 0.005,
    }))
    const style = chooseTextStyle(brightBusyLeft, colors, { x: 0.55, y: 0.1, width: 0.4, height: 0.8 })

    expect(style.tone).toBe('light')
    expect(style.backdrop).toBeNull()
  })
})

describe('regionStats', () => {
  it('reports the 90th-percentile detail by area', () => {
    const field = fieldFrom((_, column) => ({ luminance: 0.1, detail: column === 0 ? 0.5 : 0.01 }))
    expect(regionStats(field, wholeStage).peakDetail).toBe(0.01)
    // Columns 0-1 of 4: half the area is the busy column.
    expect(regionStats(field, { x: 0, y: 0, width: 2 / 16, height: 1 }).peakDetail).toBe(0.5)
  })
})

describe('chooseIconStyle', () => {
  it('rings the icons in a tone that stands out from every uniform grey, over glass within its range', () => {
    for (let luminance = 0; luminance <= 1; luminance += 0.05) {
      const style = chooseIconStyle(fieldFrom(() => ({ luminance, detail: 0.01 })), colors, wholeStage)
      expect(style.contrast, `luminance ${luminance}`).toBeGreaterThanOrEqual(ICON_RING_CONTRAST)
      expect(style.opacity).toBeGreaterThanOrEqual(MIN_ICON_GLASS_OPACITY)
      expect(style.opacity).toBeLessThanOrEqual(MAX_ICON_GLASS_OPACITY)
    }
  })

  it('uses a light ring on a dark background and a dark one on a light background', () => {
    expect(chooseIconStyle(fieldFrom(() => ({ luminance: 0.03, detail: 0 })), colors, wholeStage).tone).toBe('light')
    expect(chooseIconStyle(fieldFrom(() => ({ luminance: 0.85, detail: 0 })), colors, wholeStage).tone).toBe('dark')
  })

  it('makes the glass more opaque the busier the scene around the icons', () => {
    const quiet = chooseIconStyle(fieldFrom(() => ({ luminance: 0.1, detail: 0 })), colors, wholeStage)
    const busy = chooseIconStyle(fieldFrom(() => ({ luminance: 0.1, detail: 0.2 })), colors, wholeStage)
    expect(quiet.opacity).toBe(MIN_ICON_GLASS_OPACITY)
    expect(busy.opacity).toBe(MAX_ICON_GLASS_OPACITY)
  })
})
