import type { NormalizedRect, QuoteLayoutId, SelectedElement } from '../types'
import { regionStats, type LuminanceField } from './field'
import { pick, randomBetween, type Rng } from './random'

/** TV-safe margin on every side, as a fraction of the stage. */
export const SAFE_MARGIN = 0.05
/** The stage is 16:9; slot and element maths runs in these units so aspect ratios stay true. */
const STAGE_WIDTH_UNITS = 16
const STAGE_HEIGHT_UNITS = 9

const MAX_ROTATION_DEGREES = 5
const MAX_JITTER = 0.02
/**
 * A reserved quote region this much busier than the rest of the image means the model ignored the layout
 * it was asked for, and the grid decides instead. Below [MIN_DISTRUST_DETAIL] the region is calm enough
 * whatever the comparison says.
 */
const DISTRUST_RATIO = 1.25
const MIN_DISTRUST_DETAIL = 0.02
/** Motif slots whose detail is within this fraction of the calmest one count as equally calm. */
const CALM_TIE_FRACTION = 0.1

/** The background's own quote layout, or `grid` when the layout came from the detail grid instead. */
export type Arrangement = QuoteLayoutId | 'grid'
export type TextAlign = 'left' | 'center'

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
  /** The motif, when there is one. */
  elements: PlacedElement[]
}

interface QuotePlan {
  quoteBox: NormalizedRect
  align: TextAlign
  /** Where the motif may go, all clear of the quote box; the calmest is used. */
  motifSlots: Slot[]
}

const SIDE_QUOTE_BOX = { y: 0.14, width: 0.4, height: 0.72 }
const BAND_QUOTE_BOX = { x: 0.12, width: 0.76, height: 0.38 }
const CENTER_QUOTE_BOX = { x: 0.22, y: 0.2, width: 0.56, height: 0.6 }

/** Three slots stacked on one side, for a quote on the other side. */
function sideMotifSlots(side: 'left' | 'right'): Slot[] {
  const width = 0.36
  const x = side === 'right' ? 0.55 : 1 - 0.55 - width
  return [0.08, 0.25, 0.42].map((y, index) => ({
    rect: { x, y, width, height: 0.5 },
    anchorX: 0.5,
    anchorY: index / 2,
  }))
}

/** Three slots side by side in one band, for a quote in the other band. */
function bandMotifSlots(band: 'top' | 'bottom'): Slot[] {
  const height = 0.44
  const y = band === 'bottom' ? 0.5 : 1 - 0.5 - height
  return [0.08, 0.35, 0.62].map((x, index) => ({
    rect: { x, y, width: 0.3, height },
    anchorX: index / 2,
    anchorY: band === 'bottom' ? 0.4 : 0.6,
  }))
}

/** Corner and side slots around a centred quote, anchored toward the edge. */
function surroundingMotifSlots(): Slot[] {
  const width = 0.16
  const left = SAFE_MARGIN
  const right = 1 - SAFE_MARGIN - width
  return [
    { rect: { x: left, y: SAFE_MARGIN, width, height: 0.3 }, anchorX: 0, anchorY: 0 },
    { rect: { x: right, y: SAFE_MARGIN, width, height: 0.3 }, anchorX: 1, anchorY: 0 },
    { rect: { x: left, y: 1 - SAFE_MARGIN - 0.3, width, height: 0.3 }, anchorX: 0, anchorY: 1 },
    { rect: { x: right, y: 1 - SAFE_MARGIN - 0.3, width, height: 0.3 }, anchorX: 1, anchorY: 1 },
    { rect: { x: left, y: 0.25, width, height: 0.5 }, anchorX: 0, anchorY: 0.5 },
    { rect: { x: right, y: 0.25, width, height: 0.5 }, anchorX: 1, anchorY: 0.5 },
  ]
}

/** Where the quote and motif go for each of background-v4's quote layouts. */
function planFor(layout: QuoteLayoutId): QuotePlan {
  switch (layout) {
    case 'left':
    case 'right':
      return {
        quoteBox: { x: layout === 'left' ? 0.08 : 0.52, ...SIDE_QUOTE_BOX },
        align: 'left',
        motifSlots: sideMotifSlots(layout === 'left' ? 'right' : 'left'),
      }
    case 'top':
    case 'bottom':
      return {
        quoteBox: { y: layout === 'top' ? 0.08 : 0.54, ...BAND_QUOTE_BOX },
        align: 'center',
        motifSlots: bandMotifSlots(layout === 'top' ? 'bottom' : 'top'),
      }
    case 'center':
    case 'overlay':
      return { quoteBox: CENTER_QUOTE_BOX, align: 'center', motifSlots: surroundingMotifSlots() }
  }
}

/** Whether the region the background reserved for the quote really is calmer than the rest of the image. */
function reservationHolds(field: LuminanceField, quoteBox: NormalizedRect): boolean {
  const boxDetail = regionStats(field, quoteBox).meanDetail
  if (boxDetail <= MIN_DISTRUST_DETAIL) return true
  const area = quoteBox.width * quoteBox.height
  const wholeDetail = regionStats(field, { x: 0, y: 0, width: 1, height: 1 }).meanDetail
  const restDetail = (wholeDetail - boxDetail * area) / (1 - area)
  return boxDetail <= restDetail * DISTRUST_RATIO
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

/** The calmest slot by mean detail; slots within [CALM_TIE_FRACTION] of it are picked between at random. */
function calmestSlot(field: LuminanceField, slots: Slot[], rng: Rng): Slot {
  const details = slots.map((slot) => regionStats(field, slot.rect).meanDetail)
  const calmest = Math.min(...details)
  const tied = slots.filter((_, index) => details[index] <= calmest * (1 + CALM_TIE_FRACTION) + 1e-12)
  return pick(rng, tied)
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

/**
 * Places the quote and the one motif (docs/features/quote-composition.md, "Layout"). The quote goes in the
 * region the background's [quoteLayout] reserved for it, and the motif in the calmest of that layout's
 * slots outside it. Without a layout (older images, the fallback gradient), or when the reserved region
 * turns out busier than the rest, the detail grid decides: the quote on the calmer half, the motif on the
 * other. Without a motif, a quote with no reserved region is centred.
 */
export function computeLayout(
  field: LuminanceField,
  calmRegion: NormalizedRect | null,
  quoteLayout: QuoteLayoutId | null,
  element: SelectedElement | null,
  rng: Rng,
): Layout {
  let arrangement: Arrangement
  let plan: QuotePlan
  if (quoteLayout && (quoteLayout === 'overlay' || reservationHolds(field, planFor(quoteLayout).quoteBox))) {
    arrangement = quoteLayout
    plan = planFor(quoteLayout)
  } else if (element) {
    arrangement = 'grid'
    plan = planFor(calmerSide(field, calmRegion, rng))
  } else {
    arrangement = 'grid'
    plan = planFor('center')
  }
  return {
    arrangement,
    quoteBox: plan.quoteBox,
    align: plan.align,
    elements: element ? [placeInSlot(element, calmestSlot(field, plan.motifSlots, rng), rng)] : [],
  }
}
