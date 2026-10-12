import { SAFE_MARGIN, type Dock } from '../composition/layout'
import type { NormalizedRect } from '../types'

export type Corner = Dock['corner']

/** In order of preference, for ties: away from the classic HUD spot the icons usually take. */
const CORNERS: readonly Corner[] = ['bottom-right', 'bottom-left', 'top-right', 'top-left']

/** The stage is 16:9; distances are measured in stage heights so a gap counts the same either way. */
const STAGE_ASPECT = 16 / 9

/** Where an overlay of [size] sits in [corner], inside the TV-safe margin. */
export function cornerRect(corner: Corner, size: { width: number; height: number }): NormalizedRect {
  return {
    x: corner.endsWith('left') ? SAFE_MARGIN : 1 - SAFE_MARGIN - size.width,
    y: corner.startsWith('top') ? SAFE_MARGIN : 1 - SAFE_MARGIN - size.height,
    ...size,
  }
}

/**
 * The gap between [a] and [b] in stage heights, or, when they overlap, minus how far one reaches into the other
 * (the smaller of the two overlaps), so less overlap still scores better.
 */
export function signedGap(a: NormalizedRect, b: NormalizedRect): number {
  const gapX = Math.max(b.x - (a.x + a.width), a.x - (b.x + b.width)) * STAGE_ASPECT
  const gapY = Math.max(b.y - (a.y + a.height), a.y - (b.y + b.height))
  if (gapX < 0 && gapY < 0) return Math.max(gapX, gapY)
  return Math.hypot(Math.max(gapX, 0), Math.max(gapY, 0))
}

/** Gaps closer than this, in stage heights, count as equal, so the next obstacle or the preference order decides. */
const GAP_TIE = 0.02

/** Whether [gaps] (ascending) beat [best]: a wider nearest gap, or on a tie the next one, and so on. */
function widerGaps(gaps: number[], best: number[]): boolean {
  for (let index = 0; index < gaps.length; index++) {
    if (gaps[index] > best[index] + GAP_TIE) return true
    if (gaps[index] < best[index] - GAP_TIE) return false
  }
  return false
}

/**
 * The corner for an overlay of [size] (fractions of the stage) farthest from whatever it would be nearest among
 * [obstacles] (the quote, the icons); on a tie, farthest from the next nearest. Remaining ties go by [CORNERS] order.
 */
export function chooseOverlayCorner(size: { width: number; height: number }, obstacles: NormalizedRect[]): Corner {
  let best: Corner = CORNERS[0]
  let bestGaps: number[] | null = null
  for (const corner of CORNERS) {
    const rect = cornerRect(corner, size)
    const gaps = obstacles.map((obstacle) => signedGap(rect, obstacle)).sort((a, b) => a - b)
    if (bestGaps === null || widerGaps(gaps, bestGaps)) {
      best = corner
      bestGaps = gaps
    }
  }
  return best
}
