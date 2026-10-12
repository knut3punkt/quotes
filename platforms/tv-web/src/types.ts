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

/** The region a background-v4 image's prompt kept calm for the quote; `overlay` kept none. */
export type QuoteLayoutId = 'left' | 'right' | 'top' | 'center' | 'bottom' | 'overlay'

const QUOTE_LAYOUT_IDS: readonly string[] = ['left', 'right', 'top', 'center', 'bottom', 'overlay']

/** The layout as a known id, or null for none or one this app doesn't know. */
export function parseQuoteLayout(value: string | null | undefined): QuoteLayoutId | null {
  return value != null && QUOTE_LAYOUT_IDS.includes(value) ? (value as QuoteLayoutId) : null
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
  /** The style profile it was generated in; null for images from recipes without style profiles. */
  style: string | null
  /** The quote layout its prompt reserved room for; null for older images. See `parseQuoteLayout`. */
  quoteLayout: string | null
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
  /** The style profile it was generated in; null for images from recipes without style profiles. */
  style: string | null
}

/**
 * The images the server chose for one showing of a quote: a background (null asks for the fallback) and at
 * most one motif element.
 */
export interface QuoteVisuals {
  background: SelectedBackground | null
  elements: SelectedElement[]
  /** How well the background and motif styles fit (0-3), or null when there was no pair to score. */
  styleFit: number | null
}

export interface PublicQuote {
  id: number
  /** The excerpt shown as `text`, or null when `text` is the full quotation. */
  excerptId: number | null
  text: string
  author: string | null
  sourceTitle: string | null
  sourceDetail: string | null
  visuals: QuoteVisuals
}
