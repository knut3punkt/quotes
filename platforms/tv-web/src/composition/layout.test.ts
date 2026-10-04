import { describe, expect, it } from 'vitest'
import type { NormalizedRect } from '../types'
import { computeLayout, SAFE_MARGIN } from './layout'
import { mulberry32 } from './random'
import { element, fieldFrom } from './test-fixtures'

const EPSILON = 1e-9

function overlaps(a: NormalizedRect, b: NormalizedRect): boolean {
  return (
    a.x < b.x + b.width - EPSILON &&
    b.x < a.x + a.width - EPSILON &&
    a.y < b.y + b.height - EPSILON &&
    b.y < a.y + a.height - EPSILON
  )
}

function inside(inner: NormalizedRect, outer: NormalizedRect): boolean {
  return (
    inner.x >= outer.x - EPSILON &&
    inner.y >= outer.y - EPSILON &&
    inner.x + inner.width <= outer.x + outer.width + EPSILON &&
    inner.y + inner.height <= outer.y + outer.height + EPSILON
  )
}

const safeArea = { x: SAFE_MARGIN, y: SAFE_MARGIN, width: 1 - 2 * SAFE_MARGIN, height: 1 - 2 * SAFE_MARGIN }
const flatField = fieldFrom(() => ({ luminance: 0.2, detail: 0.01 }))

describe('computeLayout', () => {
  it.each([0, 1, 2, 3, 4])('keeps %i elements in safe slots clear of the quote and of each other', (count) => {
    for (let seed = 0; seed < 50; seed++) {
      const elements = Array.from({ length: count }, (_, index) => element(index + 1))
      const layout = computeLayout(flatField, null, elements, mulberry32(seed))

      expect(layout.elements).toHaveLength(count)
      expect(inside(layout.quoteBox, safeArea)).toBe(true)
      for (const placed of layout.elements) {
        expect(inside(placed.slotRect, safeArea)).toBe(true)
        expect(inside(placed.contentRect, placed.slotRect)).toBe(true)
        expect(overlaps(placed.slotRect, layout.quoteBox)).toBe(false)
      }
      layout.elements.forEach((a, i) =>
        layout.elements.slice(i + 1).forEach((b) => expect(overlaps(a.contentRect, b.contentRect)).toBe(false)),
      )
    }
  })

  it('picks the arrangement by element count', () => {
    const arrangement = (count: number) =>
      computeLayout(flatField, null, Array.from({ length: count }, (_, i) => element(i + 1)), mulberry32(1)).arrangement
    expect([0, 1, 2, 3, 4].map(arrangement)).toEqual(['center', 'side', 'side', 'three-corners', 'corners'])
  })

  it('puts the quote on the calm side and the element on the busy side', () => {
    const busyRight = fieldFrom((_, column) => ({ luminance: 0.2, detail: column >= 8 ? 0.1 : 0.005 }))
    const layout = computeLayout(busyRight, null, [element(1)], mulberry32(3))

    expect(layout.quoteBox.x + layout.quoteBox.width).toBeLessThanOrEqual(0.5 + EPSILON)
    expect(layout.elements[0].contentRect.x).toBeGreaterThanOrEqual(0.5)
  })

  it('leaves the calmest corner empty for three elements and leans the quote toward it', () => {
    const calmBottomLeft = fieldFrom((row, column) => ({
      luminance: 0.2,
      detail: row >= 5 && column < 8 ? 0.001 : 0.08,
    }))
    const layout = computeLayout(calmBottomLeft, null, [element(1), element(2), element(3)], mulberry32(5))

    const box = layout.quoteBox
    expect(box.x + box.width / 2).toBeLessThan(0.5)
    expect(box.y + box.height / 2).toBeGreaterThan(0.5)
    for (const placed of layout.elements) {
      expect(placed.slotRect.x < 0.5 && placed.slotRect.y > 0.5).toBe(false)
    }
  })

  it('fits an element by its visible content, not its canvas', () => {
    const narrow = element(1, { x: 0.4, y: 0, width: 0.2, height: 1 })
    const [placed] = computeLayout(flatField, null, [narrow], mulberry32(2)).elements

    expect(placed.canvasRect.width).toBeCloseTo(placed.contentRect.width / 0.2, 9)
    expect(placed.canvasRect.x).toBeCloseTo(placed.contentRect.x - 0.4 * placed.canvasRect.width, 9)
  })
})
