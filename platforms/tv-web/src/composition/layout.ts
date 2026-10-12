import type { NormalizedRect, QuoteLayoutId, SelectedElement } from '../types'
import { regionStats, type LuminanceField } from './field'

/** TV-safe margin on every side, as a fraction of the stage. */
export const SAFE_MARGIN = 0.05
/** The stage is 16:9; icon maths runs in these units so circles stay round. */
const STAGE_WIDTH_UNITS = 16
const STAGE_HEIGHT_UNITS = 9

/** Icon diameter as a fraction of the stage height: large enough for the motif artwork to read from a sofa. */
export const ICON_DIAMETER = 0.2
/** The smallest icons may get when no dock fits at full size. */
const MIN_ICON_DIAMETER = 0.15
/** Space between icons, and the least room kept between the icons and the quote, in fractions of the stage height. */
const ICON_GAP = 0.025
const ICON_CLEARANCE = 0.02
/** How much of the icon's diameter its ring (with edges) covers on each side. */
export const ICON_RING_FRACTION = 0.08
/** The motif's visible content spans this much of the space inside the ring, along its longer side. */
const ICON_CONTENT_FILL = 0.8
/**
 * A dock's score is its detail and luminance spread, plus this much per step down the preference order, so near
 * ties go to the usual corner and the icons feel like a fixed HUD rather than a random placement.
 */
const DOCK_PREFERENCE_STEP = 0.001
const PEAK_DETAIL_WEIGHT = 0.5
const LUMINANCE_SPREAD_WEIGHT = 0.05
/**
 * A reserved quote region this much busier than the rest of the image means the model ignored the layout
 * it was asked for, and the grid decides instead. Below [MIN_DISTRUST_DETAIL] the region is calm enough
 * whatever the comparison says.
 */
const DISTRUST_RATIO = 1.25
const MIN_DISTRUST_DETAIL = 0.02

/** The background's own quote layout, or `grid` when the layout was ignored or there was none. */
export type Arrangement = QuoteLayoutId | 'grid'
export type TextAlign = 'left' | 'center'

type Corner = 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right'
/** A corner, and whether the icons run from it along the horizontal edge (`row`) or the vertical one (`column`). */
export interface Dock {
  corner: Corner
  direction: 'row' | 'column'
}

/** In order of preference: the classic HUD spot first. */
const DOCKS: readonly Dock[] = (['top-left', 'top-right', 'bottom-left', 'bottom-right'] as const).flatMap((corner) =>
  (['row', 'column'] as const).map((direction) => ({ corner, direction })),
)

export interface PlacedIcon {
  element: SelectedElement
  /** The icon's circle on the stage. */
  circle: NormalizedRect
  /** The element's whole canvas, in fractions of [circle], so its visible content sits centred in the ring. */
  canvas: NormalizedRect
}

export interface Layout {
  arrangement: Arrangement
  quoteBox: NormalizedRect
  align: TextAlign
  /** The motifs as icons, most relevant nearest the corner; empty without motifs. */
  icons: PlacedIcon[]
  /** Where the icons are, and the box around them, or null without icons. */
  dock: Dock | null
  dockRect: NormalizedRect | null
}

interface QuotePlan {
  quoteBox: NormalizedRect
  align: TextAlign
}

const SIDE_QUOTE_BOX = { y: 0.14, width: 0.4, height: 0.72 }
const BAND_QUOTE_BOX = { x: 0.12, width: 0.76, height: 0.38 }
const CENTER_QUOTE_BOX = { x: 0.22, y: 0.2, width: 0.56, height: 0.6 }

/** Where the quote goes for each of background-v4's quote layouts. */
function planFor(layout: QuoteLayoutId): QuotePlan {
  switch (layout) {
    case 'left':
    case 'right':
      return { quoteBox: { x: layout === 'left' ? 0.08 : 0.52, ...SIDE_QUOTE_BOX }, align: 'left' }
    case 'top':
    case 'bottom':
      return { quoteBox: { y: layout === 'top' ? 0.08 : 0.54, ...BAND_QUOTE_BOX }, align: 'center' }
    case 'center':
    case 'overlay':
      return { quoteBox: CENTER_QUOTE_BOX, align: 'center' }
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

/** The circles of [count] icons of [diameter] (fraction of the stage height) in [dock], the first in the corner. */
function dockCircles(dock: Dock, count: number, diameter: number): NormalizedRect[] {
  const width = (diameter * STAGE_HEIGHT_UNITS) / STAGE_WIDTH_UNITS
  const gapX = (ICON_GAP * STAGE_HEIGHT_UNITS) / STAGE_WIDTH_UNITS
  const fromLeft = dock.corner.endsWith('left')
  const fromTop = dock.corner.startsWith('top')
  return Array.from({ length: count }, (_, index) => {
    const alongX = dock.direction === 'row' ? index * (width + gapX) : 0
    const alongY = dock.direction === 'column' ? index * (diameter + ICON_GAP) : 0
    return {
      x: fromLeft ? SAFE_MARGIN + alongX : 1 - SAFE_MARGIN - width - alongX,
      y: fromTop ? SAFE_MARGIN + alongY : 1 - SAFE_MARGIN - diameter - alongY,
      width,
      height: diameter,
    }
  })
}

function boundingRect(rects: NormalizedRect[]): NormalizedRect {
  const left = Math.min(...rects.map((rect) => rect.x))
  const top = Math.min(...rects.map((rect) => rect.y))
  const right = Math.max(...rects.map((rect) => rect.x + rect.width))
  const bottom = Math.max(...rects.map((rect) => rect.y + rect.height))
  return { x: left, y: top, width: right - left, height: bottom - top }
}

function overlaps(a: NormalizedRect, b: NormalizedRect): boolean {
  return a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height
}

/** [rect] grown by the clearance kept between the icons and the quote. */
function withClearance(rect: NormalizedRect): NormalizedRect {
  const padX = (ICON_CLEARANCE * STAGE_HEIGHT_UNITS) / STAGE_WIDTH_UNITS
  return {
    x: rect.x - padX,
    y: rect.y - ICON_CLEARANCE,
    width: rect.width + 2 * padX,
    height: rect.height + 2 * ICON_CLEARANCE,
  }
}

function fitsSafeArea(rect: NormalizedRect): boolean {
  const epsilon = 1e-9
  return rect.y + rect.height <= 1 - SAFE_MARGIN + epsilon && rect.x + rect.width <= 1 - SAFE_MARGIN + epsilon
}

/** Lower is better: a calm, evenly lit spot where one ring colour reads everywhere; near ties go by preference. */
function dockScore(field: LuminanceField, rect: NormalizedRect, preference: number): number {
  const stats = regionStats(field, rect)
  return (
    stats.meanDetail +
    PEAK_DETAIL_WEIGHT * stats.peakDetail +
    LUMINANCE_SPREAD_WEIGHT * (stats.highLuminance - stats.lowLuminance) +
    DOCK_PREFERENCE_STEP * preference
  )
}

interface DockChoice {
  dock: Dock
  circles: NormalizedRect[]
  rect: NormalizedRect
}

/** The best-scoring dock for [count] icons of [diameter] that stays clear of [quoteBox], or null if none does. */
function bestDock(
  field: LuminanceField,
  quoteBox: NormalizedRect | null,
  count: number,
  diameter: number,
): DockChoice | null {
  let best: DockChoice | null = null
  let bestScore = Infinity
  DOCKS.forEach((dock, preference) => {
    const circles = dockCircles(dock, count, diameter)
    const rect = boundingRect(circles)
    if (!fitsSafeArea(rect)) return
    if (quoteBox && overlaps(withClearance(rect), quoteBox)) return
    const score = dockScore(field, rect, preference)
    if (score < bestScore) {
      best = { dock, circles, rect }
      bestScore = score
    }
  })
  return best
}

/** [quoteBox] cut back from the side facing [dock], so the two keep their clearance. */
function trimQuoteBox(quoteBox: NormalizedRect, choice: DockChoice): NormalizedRect {
  const clear = withClearance(choice.rect)
  const right = quoteBox.x + quoteBox.width
  const bottom = quoteBox.y + quoteBox.height
  const { corner, direction } = choice.dock
  if (direction === 'column') {
    return corner.endsWith('left')
      ? { ...quoteBox, x: clear.x + clear.width, width: right - (clear.x + clear.width) }
      : { ...quoteBox, width: clear.x - quoteBox.x }
  }
  return corner.startsWith('top')
    ? { ...quoteBox, y: clear.y + clear.height, height: bottom - (clear.y + clear.height) }
    : { ...quoteBox, height: clear.y - quoteBox.y }
}

/**
 * Where an element's canvas goes inside its circle, in fractions of the circle: its visible content (its
 * contentBox, not its canvas) centred, its longer side spanning [ICON_CONTENT_FILL] of the space inside the ring.
 */
function canvasInCircle(element: SelectedElement): NormalizedRect {
  const box = element.contentBox
  const contentAspect = (box.width * element.width) / (box.height * element.height)
  const longest = ICON_CONTENT_FILL * (1 - 2 * ICON_RING_FRACTION)
  const contentWidth = contentAspect >= 1 ? longest : longest * contentAspect
  const contentHeight = contentWidth / contentAspect
  const canvasWidth = contentWidth / box.width
  const canvasHeight = contentHeight / box.height
  return {
    x: (1 - contentWidth) / 2 - box.x * canvasWidth,
    y: (1 - contentHeight) / 2 - box.y * canvasHeight,
    width: canvasWidth,
    height: canvasHeight,
  }
}

/**
 * Places the quote and the motif icons (docs/features/quote-composition.md, "Layout"). The quote goes in the
 * region the background's [quoteLayout] reserved for it; without a layout (older images, the fallback
 * gradient), or when the reserved region turns out busier than the rest, it is centred. The icons go in a row
 * or column from one corner: the calmest dock clear of the quote, at full size if possible, else smaller; if no
 * dock is clear even then, the quote box gives way. Deterministic, so a quote is always laid out the same way
 * over the same background.
 */
export function computeLayout(
  field: LuminanceField,
  quoteLayout: QuoteLayoutId | null,
  elements: SelectedElement[],
): Layout {
  const trusted = quoteLayout && (quoteLayout === 'overlay' || reservationHolds(field, planFor(quoteLayout).quoteBox))
  const arrangement: Arrangement = trusted ? quoteLayout : 'grid'
  const plan = planFor(trusted ? quoteLayout : 'center')
  if (elements.length === 0) {
    return { arrangement, quoteBox: plan.quoteBox, align: plan.align, icons: [], dock: null, dockRect: null }
  }

  let quoteBox = plan.quoteBox
  let choice =
    bestDock(field, quoteBox, elements.length, ICON_DIAMETER) ??
    bestDock(field, quoteBox, elements.length, MIN_ICON_DIAMETER)
  if (!choice) {
    choice = bestDock(field, null, elements.length, MIN_ICON_DIAMETER)
    if (!choice) throw new Error(`no dock holds ${elements.length} icons`)
    quoteBox = trimQuoteBox(quoteBox, choice)
  }
  const circles = choice.circles
  return {
    arrangement,
    quoteBox,
    align: plan.align,
    icons: elements.map((element, index) => ({ element, circle: circles[index], canvas: canvasInCircle(element) })),
    dock: choice.dock,
    dockRect: choice.rect,
  }
}
