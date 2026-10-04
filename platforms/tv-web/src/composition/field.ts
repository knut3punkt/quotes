import type { LayoutCell, NormalizedRect } from '../types'

/** A background's luminance and detail grid, as analysed on the server (docs/features/tag-images.md). */
export interface LuminanceField {
  columns: number
  rows: number
  /** Row-major: `cells[row][column]`. */
  cells: LayoutCell[][]
}

export interface RegionStats {
  meanLuminance: number
  /** 10th percentile of luminance by area: the darkest part that matters. */
  lowLuminance: number
  /** 90th percentile of luminance by area: the brightest part that matters. */
  highLuminance: number
  meanDetail: number
}

interface WeightedCell {
  cell: LayoutCell
  weight: number
}

/** The cells under [region], each weighted by how much of it the region covers. */
function cellsUnder(field: LuminanceField, region: NormalizedRect): WeightedCell[] {
  const result: WeightedCell[] = []
  for (let row = 0; row < field.rows; row++) {
    const top = row / field.rows
    const bottom = (row + 1) / field.rows
    const overlapY = Math.min(bottom, region.y + region.height) - Math.max(top, region.y)
    if (overlapY <= 0) continue
    for (let column = 0; column < field.columns; column++) {
      const left = column / field.columns
      const right = (column + 1) / field.columns
      const overlapX = Math.min(right, region.x + region.width) - Math.max(left, region.x)
      if (overlapX <= 0) continue
      result.push({ cell: field.cells[row][column], weight: overlapX * overlapY })
    }
  }
  return result
}

function weightedPercentile(values: { value: number; weight: number }[], fraction: number): number {
  const sorted = [...values].sort((a, b) => a.value - b.value)
  const total = sorted.reduce((sum, item) => sum + item.weight, 0)
  let running = 0
  for (const item of sorted) {
    running += item.weight
    if (running >= fraction * total) return item.value
  }
  return sorted[sorted.length - 1].value
}

export function regionStats(field: LuminanceField, region: NormalizedRect): RegionStats {
  const cells = cellsUnder(field, region)
  if (cells.length === 0) return { meanLuminance: 0, lowLuminance: 0, highLuminance: 0, meanDetail: 0 }
  const total = cells.reduce((sum, { weight }) => sum + weight, 0)
  const luminances = cells.map(({ cell, weight }) => ({ value: cell.luminance, weight }))
  return {
    meanLuminance: cells.reduce((sum, { cell, weight }) => sum + cell.luminance * weight, 0) / total,
    lowLuminance: weightedPercentile(luminances, 0.1),
    highLuminance: weightedPercentile(luminances, 0.9),
    meanDetail: cells.reduce((sum, { cell, weight }) => sum + cell.detail * weight, 0) / total,
  }
}

/** A field with the same luminance everywhere and no detail, used where there's no analysed image. */
export function uniformField(luminance: number, columns = 16, rows = 9): LuminanceField {
  return {
    columns,
    rows,
    cells: Array.from({ length: rows }, () => Array.from({ length: columns }, () => ({ luminance, detail: 0 }))),
  }
}
