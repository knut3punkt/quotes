import { describe, expect, it } from 'vitest'
import { chooseOverlayCorner, signedGap } from './overlay-placement'

const SIZE = { width: 0.3, height: 0.15 }

describe('signedGap', () => {
  it('measures the gap between apart rectangles in stage heights', () => {
    expect(signedGap({ x: 0, y: 0, width: 0.1, height: 0.1 }, { x: 0, y: 0.3, width: 0.1, height: 0.1 })).toBeCloseTo(0.2)
    expect(signedGap({ x: 0, y: 0, width: 0.1, height: 0.1 }, { x: 0.19, y: 0, width: 0.1, height: 0.1 })).toBeCloseTo(
      0.16,
    )
  })

  it('is negative by the shallower overlap when rectangles overlap', () => {
    expect(signedGap({ x: 0, y: 0, width: 0.5, height: 0.5 }, { x: 0.4, y: 0.45, width: 0.5, height: 0.5 })).toBeCloseTo(
      -0.05,
    )
  })
})

describe('chooseOverlayCorner', () => {
  it('keeps clear of a bottom band quote, even when that is the corner opposite the icons', () => {
    const quote = { x: 0.12, y: 0.54, width: 0.76, height: 0.38 }
    const icons = { x: 0.05, y: 0.05, width: 0.11, height: 0.2 }
    expect(chooseOverlayCorner(SIZE, [quote, icons])).toBe('top-right')
  })

  it('takes the side away from a side quote', () => {
    const quote = { x: 0.52, y: 0.14, width: 0.4, height: 0.72 }
    expect(chooseOverlayCorner(SIZE, [quote])).toBe('bottom-left')
  })

  it('keeps away from the icons when the quote leaves every corner free', () => {
    const quote = { x: 0.3, y: 0.35, width: 0.4, height: 0.3 }
    const icons = { x: 0.84, y: 0.75, width: 0.11, height: 0.2 }
    expect(chooseOverlayCorner(SIZE, [quote, icons])).toBe('top-left')
  })

  it('prefers bottom right when nothing is near', () => {
    expect(chooseOverlayCorner(SIZE, [{ x: 0.45, y: 0.45, width: 0.1, height: 0.1 }])).toBe('bottom-right')
  })
})
