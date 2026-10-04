import { describe, expect, it } from 'vitest'
import { chooseTextStyle, MAX_SCRIM_OPACITY, TARGET_CONTRAST } from './contrast'
import { fieldFrom } from './test-fixtures'

const wholeStage = { x: 0, y: 0, width: 1, height: 1 }
const colors = [{ hex: '#2f4a6b', weight: 0.7 }]

describe('chooseTextStyle', () => {
  it('uses light, unassisted text on a dark calm background', () => {
    const style = chooseTextStyle(fieldFrom(() => ({ luminance: 0.03, detail: 0.005 })), colors, wholeStage)

    expect(style.tone).toBe('light')
    expect(style.scrim).toBeNull()
    expect(style.textShadow).toBeNull()
    expect(style.contrast).toBeGreaterThanOrEqual(TARGET_CONTRAST)
  })

  it('uses dark text on a light calm background', () => {
    const style = chooseTextStyle(fieldFrom(() => ({ luminance: 0.85, detail: 0.005 })), colors, wholeStage)

    expect(style.tone).toBe('dark')
    expect(style.scrim).toBeNull()
    expect(style.contrast).toBeGreaterThanOrEqual(TARGET_CONTRAST)
  })

  it('adds a halo but no scrim over a dark textured background', () => {
    const style = chooseTextStyle(fieldFrom(() => ({ luminance: 0.04, detail: 0.06 })), colors, wholeStage)

    expect(style.textShadow).not.toBeNull()
    expect(style.scrim).toBeNull()
  })

  it('solves a scrim over a background that mixes bright and dark areas', () => {
    const mixed = fieldFrom((_, column) => ({ luminance: column % 2 === 0 ? 0.05 : 0.6, detail: 0.05 }))
    const style = chooseTextStyle(mixed, colors, wholeStage)

    expect(style.scrim?.opacity).toBeGreaterThan(0)
    expect(style.scrim?.opacity).toBeLessThanOrEqual(MAX_SCRIM_OPACITY)
    expect(style.contrast).toBeGreaterThanOrEqual(TARGET_CONTRAST)
    expect(style.textShadow).not.toBeNull()
  })

  it('reaches the target on every uniform grey', () => {
    for (let luminance = 0; luminance <= 1; luminance += 0.05) {
      const style = chooseTextStyle(fieldFrom(() => ({ luminance, detail: 0.01 })), colors, wholeStage)
      expect(style.contrast, `luminance ${luminance}`).toBeGreaterThanOrEqual(TARGET_CONTRAST)
    }
  })

  it('only looks at the region under the text', () => {
    const brightLeft = fieldFrom((_, column) => ({ luminance: column < 8 ? 0.9 : 0.02, detail: 0.005 }))
    const style = chooseTextStyle(brightLeft, colors, { x: 0.55, y: 0.1, width: 0.4, height: 0.8 })

    expect(style.tone).toBe('light')
    expect(style.scrim).toBeNull()
  })
})
