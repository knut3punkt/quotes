import { describe, expect, it } from 'vitest'
import type { NormalizedRect, QuoteLayoutId } from '../types'
import { computeLayout, ICON_DIAMETER, SAFE_MARGIN } from './layout'
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
const elements = (count: number) => Array.from({ length: count }, (_, index) => element(index + 1))

const safeArea = { x: SAFE_MARGIN, y: SAFE_MARGIN, width: 1 - 2 * SAFE_MARGIN, height: 1 - 2 * SAFE_MARGIN }
const flatField = fieldFrom(() => ({ luminance: 0.2, detail: 0.01 }))

describe('computeLayout', () => {
  it.each([...LAYOUTS, null])('keeps the quote and every icon in the safe area and apart for layout %s', (quoteLayout) => {
    for (let count = 1; count <= 4; count++) {
      const layout = computeLayout(flatField, quoteLayout, elements(count))

      expect(layout.icons).toHaveLength(count)
      expect(inside(layout.quoteBox, safeArea)).toBe(true)
      layout.icons.forEach((icon, index) => {
        expect(inside(icon.circle, safeArea)).toBe(true)
        expect(overlaps(icon.circle, layout.quoteBox)).toBe(false)
        layout.icons.slice(index + 1).forEach((other) => expect(overlaps(icon.circle, other.circle)).toBe(false))
      })
    }
  })

  it('makes the icons round on a 16:9 stage, at full size when there is room', () => {
    for (const quoteLayout of LAYOUTS) {
      for (const icon of computeLayout(flatField, quoteLayout, elements(3)).icons) {
        expect(icon.circle.width * 16).toBeCloseTo(icon.circle.height * 9, 9)
        expect(icon.circle.height).toBeCloseTo(ICON_DIAMETER, 9)
      }
    }
  })

  it('lines the icons up from a corner, the first in it', () => {
    const { icons, dock } = computeLayout(flatField, 'center', elements(3))
    expect(dock).not.toBeNull()
    const along = dock!.direction === 'row' ? 'x' : 'y'
    const across = dock!.direction === 'row' ? 'y' : 'x'
    const towardStart = dock!.direction === 'row' ? dock!.corner.endsWith('left') : dock!.corner.startsWith('top')
    for (let index = 1; index < icons.length; index++) {
      expect(icons[index].circle[across]).toBeCloseTo(icons[0].circle[across], 9)
      expect(icons[index].circle[along] > icons[index - 1].circle[along]).toBe(towardStart)
    }
  })

  it('prefers the top left when every corner is equally calm', () => {
    const { dock } = computeLayout(flatField, 'right', elements(3))
    expect(dock?.corner).toBe('top-left')
  })

  it('takes the calmest dock clear of the quote', () => {
    // Quote on the left; on the right, only the bottom is calm.
    const calmBottomRight = fieldFrom((row, column) => ({
      luminance: 0.2,
      detail: column < 8 || row >= 5 ? 0.001 : 0.08,
    }))
    const { dock, icons } = computeLayout(calmBottomRight, 'left', elements(3))
    expect(dock?.corner).toBe('bottom-right')
    icons.forEach((icon) => expect(centre(icon.circle).y).toBeGreaterThan(0.5))
  })

  it('is deterministic', () => {
    const busy = fieldFrom((row, column) => ({ luminance: (row * column) % 3 === 0 ? 0.6 : 0.1, detail: 0.03 }))
    expect(computeLayout(busy, 'top', elements(4))).toEqual(computeLayout(busy, 'top', elements(4)))
  })

  it('puts the quote in the region the background reserved', () => {
    const at = (quoteLayout: QuoteLayoutId) => {
      const layout = computeLayout(flatField, quoteLayout, elements(2))
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

  it('centres the quote on the grid when the reserved region is busier than the rest', () => {
    // Asked for the left, but the left is busy and the right is calm.
    const busyLeft = fieldFrom((_, column) => ({ luminance: 0.2, detail: column < 8 ? 0.1 : 0.005 }))
    const layout = computeLayout(busyLeft, 'left', elements(1))

    expect(layout.arrangement).toBe('grid')
    expect(centre(layout.quoteBox).x).toBeCloseTo(0.5, 9)
  })

  it('trusts an overlay background however busy it is', () => {
    const busy = fieldFrom(() => ({ luminance: 0.2, detail: 0.2 }))
    expect(computeLayout(busy, 'overlay', elements(1)).arrangement).toBe('overlay')
  })

  it('centres the quote without a layout, and places no icons without motifs', () => {
    const layout = computeLayout(flatField, null, [])
    expect(layout.arrangement).toBe('grid')
    expect(layout.icons).toEqual([])
    expect(layout.dock).toBeNull()
    expect(centre(layout.quoteBox).x).toBeCloseTo(0.5, 9)
  })

  it('centres the motif by its visible content, not its canvas', () => {
    const narrow = element(1, { x: 0.4, y: 0, width: 0.2, height: 1 })
    const [icon] = computeLayout(flatField, 'left', [narrow]).icons
    const { canvas } = icon

    const content = {
      x: canvas.x + 0.4 * canvas.width,
      y: canvas.y,
      width: 0.2 * canvas.width,
      height: canvas.height,
    }
    expect(centre(content).x).toBeCloseTo(0.5, 9)
    expect(centre(content).y).toBeCloseTo(0.5, 9)
    // Tall content: its height is the longer side and stays inside the ring.
    expect(content.height).toBeLessThan(1)
    expect(content.height).toBeGreaterThan(content.width)
  })
})
