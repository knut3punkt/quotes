/** A rectangle in 0-1 fractions of its container, origin top left. */
export interface NormalizedRect {
  x: number
  y: number
  width: number
  height: number
}

export interface DominantColor {
  hex: string
  weight: number
}

/** One cell of a background's layout grid: WCAG relative luminance (0-1) and edge detail. */
export interface LayoutCell {
  luminance: number
  detail: number
}

export interface BackgroundLayout {
  gridColumns: number
  gridRows: number
  /** Row-major: `cells[row][column]`. */
  cells: LayoutCell[][]
  calmRegion: NormalizedRect | null
  textTone: 'light' | 'dark'
}

export interface SelectedBackground {
  imageId: number
  tagId: number
  tagName: string
  width: number
  height: number
  meanLuminance: number
  dominantColors: DominantColor[]
  layout: BackgroundLayout
}

export interface SelectedElement {
  imageId: number
  tagId: number
  tagName: string
  width: number
  height: number
  dominantColors: DominantColor[]
  /** The box around the element's visible pixels, in fractions of its canvas. */
  contentBox: NormalizedRect
}

/** The images the server chose for one showing of a quote; a null background asks for the fallback. */
export interface QuoteVisuals {
  background: SelectedBackground | null
  elements: SelectedElement[]
}

export interface PublicQuote {
  id: number
  text: string
  author: string | null
  sourceTitle: string | null
  sourceDetail: string | null
  visuals: QuoteVisuals
}
