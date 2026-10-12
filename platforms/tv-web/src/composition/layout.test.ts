import { describe, expect, it } from 'vitest'
import type { NormalizedRect, QuoteLayoutId } from '../types'
import { computeLayout, SAFE_MARGIN } from './layout'
import { mulberry32 } from './random'
import { element, fieldFrom } from './test-fixtures'

const EPSILON = 1e-9
const LAYOUTS: QuoteLayoutId[] = ['left', 'right', 'top', 'center', 'bottom', 'overlay']

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

const centre = (rect: NormalizedRect) => ({ x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 })

const safeArea = { x: SAFE_MARGIN, y: SAFE_MARGIN, width: 1 - 2 * SAFE_MARGIN, height: 1 - 2 * SAFE_MARGIN }
const flatField = fieldFrom(() => ({ luminance: 0.2, detail: 0.01 }))

describe('computeLayout', () => {
  it.each([...LAYOUTS, null])('keeps the quote and the motif in the safe area and apart for layout %s', (quoteLayout) => {
    for (let seed = 0; seed < 50; seed++) {
      const layout = computeLayout(flatField, null, quoteLayout, element(1), mulberry32(seed))

      expect(layout.elements).toHaveLength(1)
      expect(inside(layout.quoteBox, safeArea)).toBe(true)
      const [placed] = layout.elements
      expect(inside(placed.slotRect, safeArea)).toBe(true)
      expect(inside(placed.contentRect, placed.slotRect)).toBe(true)
      expect(overlaps(placed.slotRect, layout.quoteBox)).toBe(false)
    }
  })

  it('puts the quote in the region the background reserved', () => {
    const at = (quoteLayout: QuoteLayoutId) => {
      const layout = computeLayout(flatField, null, quoteLayout, element(1), mulberry32(1))
      expect(layout.arrangement).toBe(quoteLayout)
      return centre(layout.quoteBox)
    }
    expect(at('left').x).toBeLessThan(0.5)
    expect(at('right').x).toBeGreaterThan(0.5)
    expect(at('top').y).toBeLessThan(0.5)
    expect(at('bottom').y).toBeGreaterThan(0.5)
    expect(at('center').x).toBeCloseTo(0.5, 9)
    expect(at('center').y).toBeCloseTo(0.5, 9)
    expect(at('overlay').x).toBeCloseTo(0.5, 9)
  })

  it('puts the motif in the calmest slot outside the quote', () => {
    // Quote on the left; on the right, only the bottom is calm.
    const calmBottomRight = fieldFrom((row, column) => ({
      luminance: 0.2,
      detail: column < 8 || row >= 5 ? 0.001 : 0.08,
    }))
    for (let seed = 0; seed < 20; seed++) {
      const [placed] = computeLayout(calmBottomRight, null, 'left', element(1), mulberry32(seed)).elements
      expect(centre(placed.slotRect).x).toBeGreaterThan(0.5)
      expect(centre(placed.slotRect).y).toBeGreaterThan(0.6)
    }
  })

  it('falls back to the grid when the reserved region is busier than the rest', () => {
    // Asked for the left, but the left is busy and the right is calm.
    const busyLeft = fieldFrom((_, column) => ({ luminance: 0.2, detail: column < 8 ? 0.1 : 0.005 }))
    const layout = computeLayout(busyLeft, null, 'left', element(1), mulberry32(3))

    expect(layout.arrangement).toBe('grid')
    expect(layout.quoteBox.x).toBeGreaterThanOrEqual(0.5)
    const content = layout.elements[0].contentRect
    expect(content.x + content.width).toBeLessThanOrEqual(0.5)
  })

  it('trusts an overlay background however busy it is', () => {
    const busy = fieldFrom(() => ({ luminance: 0.2, detail: 0.2 }))
    expect(computeLayout(busy, null, 'overlay', element(1), mulberry32(1)).arrangement).toBe('overlay')
  })

  it('uses the grid without a layout: the calmer half with a motif, the middle without', () => {
    const busyRight = fieldFrom((_, column) => ({ luminance: 0.2, detail: column >= 8 ? 0.1 : 0.005 }))
    const withMotif = computeLayout(busyRight, null, null, element(1), mulberry32(3))
    expect(withMotif.arrangement).toBe('grid')
    expect(withMotif.quoteBox.x + withMotif.quoteBox.width).toBeLessThanOrEqual(0.5 + EPSILON)
    expect(withMotif.elements[0].contentRect.x).toBeGreaterThanOrEqual(0.5)

    const withoutMotif = computeLayout(busyRight, null, null, null, mulberry32(3))
    expect(withoutMotif.elements).toEqual([])
    expect(centre(withoutMotif.quoteBox).x).toBeCloseTo(0.5, 9)
  })

  it('fits the motif by its visible content, not its canvas', () => {
    const narrow = element(1, { x: 0.4, y: 0, width: 0.2, height: 1 })
    const [placed] = computeLayout(flatField, null, 'left', narrow, mulberry32(2)).elements

    expect(placed.canvasRect.width).toBeCloseTo(placed.contentRect.width / 0.2, 9)
    expect(placed.canvasRect.x).toBeCloseTo(placed.contentRect.x - 0.4 * placed.canvasRect.width, 9)
  })
})
