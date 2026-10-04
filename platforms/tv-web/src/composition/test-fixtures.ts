import type { LayoutCell, SelectedBackground, SelectedElement } from '../types'
import type { LuminanceField } from './field'

export function fieldFrom(cellAt: (row: number, column: number) => LayoutCell): LuminanceField {
  return {
    columns: 16,
    rows: 9,
    cells: Array.from({ length: 9 }, (_, row) => Array.from({ length: 16 }, (_, column) => cellAt(row, column))),
  }
}

export function background(field: LuminanceField, hex = '#335577'): SelectedBackground {
  return {
    imageId: 1,
    tagId: 1,
    tagName: 'serene',
    width: 2752,
    height: 1536,
    meanLuminance: 0.2,
    dominantColors: [{ hex, weight: 0.6 }],
    layout: { gridColumns: field.columns, gridRows: field.rows, cells: field.cells, calmRegion: null, textTone: 'light' },
  }
}

export function element(imageId: number, contentBox = { x: 0.2, y: 0.1, width: 0.6, height: 0.8 }): SelectedElement {
  return {
    imageId,
    tagId: imageId,
    tagName: `motif ${imageId}`,
    width: 1024,
    height: 1024,
    dominantColors: [{ hex: '#aa6633', weight: 0.5 }],
    contentBox,
  }
}
