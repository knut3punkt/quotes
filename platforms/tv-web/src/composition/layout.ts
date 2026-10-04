import type { NormalizedRect, SelectedElement } from '../types'
import { regionStats, type LuminanceField } from './field'
import { randomBetween, shuffle, type Rng } from './random'

/** TV-safe margin on every side, as a fraction of the stage. */
export const SAFE_MARGIN = 0.05
/** The stage is 16:9; slot and element maths runs in these units so aspect ratios stay true. */
const STAGE_WIDTH_UNITS = 16
const STAGE_HEIGHT_UNITS = 9

const CORNER_SLOT_WIDTH = 0.24
const CORNER_SLOT_HEIGHT = 0.34
const MAX_ROTATION_DEGREES = 5
const MAX_JITTER = 0.02

export type Arrangement = 'center' | 'corners' | 'three-corners' | 'side'
export type TextAlign = 'left' | 'center'

type Corner = 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right'
const CORNERS: Corner[] = ['top-left', 'top-right', 'bottom-left', 'bottom-right']

interface Slot {
  rect: NormalizedRect
  /** Where the element's visible content sits in the slot's slack, 0 = left/top, 1 = right/bottom. */
  anchorX: number
  anchorY: number
}

export interface PlacedElement {
  element: SelectedElement
  /** The element's whole canvas on the stage; its visible content is [contentRect]. */
  canvasRect: NormalizedRect
  contentRect: NormalizedRect
  /** The slot the content was fitted into. */
  slotRect: NormalizedRect
  rotation: number
}

export interface Layout {
  arrangement: Arrangement
  quoteBox: NormalizedRect
  align: TextAlign
  elements: PlacedElement[]
}

function cornerSlot(corner: Corner): Slot {
  const right = corner.endsWith('right')
  const bottom = corner.startsWith('bottom')
  return {
    rect: {
      x: right ? 1 - SAFE_MARGIN - CORNER_SLOT_WIDTH : SAFE_MARGIN,
      y: bottom ? 1 - SAFE_MARGIN - CORNER_SLOT_HEIGHT : SAFE_MARGIN,
      width: CORNER_SLOT_WIDTH,
      height: CORNER_SLOT_HEIGHT,
    },
    anchorX: right ? 1 : 0,
    anchorY: bottom ? 1 : 0,
  }
}

function quadrant(corner: Corner): NormalizedRect {
  return {
    x: corner.endsWith('right') ? 0.5 : 0,
    y: corner.startsWith('bottom') ? 0.5 : 0,
    width: 0.5,
    height: 0.5,
  }
}

/**
 * The quote box for three corner elements: the free rectangle that reaches into the empty corner,
 * bounded by the opposite column's slots and the slot below or above the empty corner, then inset.
 */
function threeCornerQuoteBox(empty: Corner): NormalizedRect {
  const right = empty.endsWith('right')
  const bottom = empty.startsWith('bottom')
  const left = right ? SAFE_MARGIN + CORNER_SLOT_WIDTH : SAFE_MARGIN
  const width = 1 - 2 * SAFE_MARGIN - CORNER_SLOT_WIDTH
  const top = bottom ? SAFE_MARGIN + CORNER_SLOT_HEIGHT : SAFE_MARGIN
  const height = 1 - 2 * SAFE_MARGIN - CORNER_SLOT_HEIGHT
  const insetX = 0.03
  const insetY = 0.04
  return { x: left + insetX, y: top + insetY, width: width - 2 * insetX, height: height - 2 * insetY }
}

/** The left or right half, whichever is calmer; near-ties go to the calm region's side, then to chance. */
function calmerSide(field: LuminanceField, calmRegion: NormalizedRect | null, rng: Rng): 'left' | 'right' {
  const leftDetail = regionStats(field, { x: 0, y: 0, width: 0.5, height: 1 }).meanDetail
  const rightDetail = regionStats(field, { x: 0.5, y: 0, width: 0.5, height: 1 }).meanDetail
  const larger = Math.max(leftDetail, rightDetail)
  if (larger > 0 && Math.abs(leftDetail - rightDetail) / larger > 0.1) {
    return leftDetail < rightDetail ? 'left' : 'right'
  }
  if (calmRegion && calmRegion.width < 0.9) {
    return calmRegion.x + calmRegion.width / 2 < 0.5 ? 'left' : 'right'
  }
  return rng() < 0.5 ? 'left' : 'right'
}

function sideSlots(count: 1 | 2, elementSide: 'left' | 'right'): Slot[] {
  const x0 = elementSide === 'right' ? 0.55 : SAFE_MARGIN
  const width = 1 - SAFE_MARGIN - 0.55
  const outward = elementSide === 'right' ? 1 : 0
  if (count === 1) {
    return [{ rect: { x: x0 + 0.01, y: 0.12, width: width - 0.02, height: 0.76 }, anchorX: 0.5, anchorY: 0.5 }]
  }
  // Two slots staggered diagonally: one high toward the quote, one low toward the edge.
  const slotWidth = width * 0.64
  const inner = elementSide === 'right' ? x0 : x0 + width - slotWidth
  const outer = elementSide === 'right' ? x0 + width - slotWidth : x0
  return [
    { rect: { x: inner, y: SAFE_MARGIN + 0.02, width: slotWidth, height: 0.43 }, anchorX: 0.5, anchorY: 0.4 },
    { rect: { x: outer, y: 0.5, width: slotWidth, height: 0.43 }, anchorX: outward, anchorY: 0.6 },
  ]
}

/**
 * Fits an element's visible content (its contentBox, not its canvas) inside [slot], keeping its aspect
 * ratio, at a slightly random scale, and with a small jitter that never leaves the slot.
 */
function placeInSlot(element: SelectedElement, slot: Slot, rng: Rng): PlacedElement {
  const box = element.contentBox
  const contentAspect = (box.width * element.width) / (box.height * element.height)
  const slotWidth = slot.rect.width * STAGE_WIDTH_UNITS
  const slotHeight = slot.rect.height * STAGE_HEIGHT_UNITS
  const scale = randomBetween(rng, 0.85, 1)
  const contentWidth = Math.min(slotWidth, slotHeight * contentAspect) * scale
  const contentHeight = contentWidth / contentAspect

  const slackX = slotWidth - contentWidth
  const slackY = slotHeight - contentHeight
  const jitterX = randomBetween(rng, -MAX_JITTER, MAX_JITTER) * STAGE_WIDTH_UNITS
  const jitterY = randomBetween(rng, -MAX_JITTER, MAX_JITTER) * STAGE_HEIGHT_UNITS
  const offsetX = Math.min(slackX, Math.max(0, slot.anchorX * slackX + jitterX))
  const offsetY = Math.min(slackY, Math.max(0, slot.anchorY * slackY + jitterY))

  const contentRect = {
    x: slot.rect.x + offsetX / STAGE_WIDTH_UNITS,
    y: slot.rect.y + offsetY / STAGE_HEIGHT_UNITS,
    width: contentWidth / STAGE_WIDTH_UNITS,
    height: contentHeight / STAGE_HEIGHT_UNITS,
  }
  const canvasWidth = contentRect.width / box.width
  const canvasHeight = contentRect.height / box.height
  return {
    element,
    contentRect,
    canvasRect: {
      x: contentRect.x - box.x * canvasWidth,
      y: contentRect.y - box.y * canvasHeight,
      width: canvasWidth,
      height: canvasHeight,
    },
    slotRect: slot.rect,
    rotation: randomBetween(rng, -MAX_ROTATION_DEGREES, MAX_ROTATION_DEGREES),
  }
}

/** The calmest corner quadrant, by mean detail. */
function calmestCorner(field: LuminanceField): Corner {
  return CORNERS.reduce((best, corner) =>
    regionStats(field, quadrant(corner)).meanDetail < regionStats(field, quadrant(best)).meanDetail ? corner : best,
  )
}

/**
 * Places the quote and its collage elements (docs/features/quote-composition.md, "Layout"):
 * - 0 elements: quote in the middle;
 * - 4: one element per corner, quote in the middle;
 * - 3: three corners, quote shifted toward the calmest corner, which stays empty;
 * - 1 or 2: quote on the calmer half, elements on the other.
 */
export function computeLayout(
  field: LuminanceField,
  calmRegion: NormalizedRect | null,
  elements: SelectedElement[],
  rng: Rng,
): Layout {
  const shuffled = shuffle(rng, elements)
  switch (shuffled.length) {
    case 0:
      return { arrangement: 'center', quoteBox: { x: 0.19, y: 0.1, width: 0.62, height: 0.8 }, align: 'center', elements: [] }
    case 1:
    case 2: {
      const quoteSide = calmerSide(field, calmRegion, rng)
      const slots = sideSlots(shuffled.length as 1 | 2, quoteSide === 'left' ? 'right' : 'left')
      return {
        arrangement: 'side',
        quoteBox: { x: quoteSide === 'left' ? SAFE_MARGIN + 0.03 : 0.5, y: 0.12, width: 0.45 - 0.03, height: 0.76 },
        align: 'left',
        elements: shuffled.map((element, index) => placeInSlot(element, slots[index], rng)),
      }
    }
    case 3: {
      const empty = calmestCorner(field)
      const occupied = CORNERS.filter((corner) => corner !== empty)
      return {
        arrangement: 'three-corners',
        quoteBox: threeCornerQuoteBox(empty),
        align: 'center',
        elements: shuffled.map((element, index) => placeInSlot(element, cornerSlot(occupied[index]), rng)),
      }
    }
    default: {
      const gap = 0.01
      return {
        arrangement: 'corners',
        quoteBox: {
          x: SAFE_MARGIN + CORNER_SLOT_WIDTH + gap,
          y: 0.08,
          width: 1 - 2 * (SAFE_MARGIN + CORNER_SLOT_WIDTH + gap),
          height: 0.84,
        },
        align: 'center',
        elements: shuffled.slice(0, 4).map((element, index) => placeInSlot(element, cornerSlot(CORNERS[index]), rng)),
      }
    }
  }
}
