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
 * The images the server chose for one showing of a quote: a background (null asks for the fallback) and one
 * element per motif tag, most relevant first, shown as icons.
 */
export interface QuoteVisuals {
  background: SelectedBackground | null
  elements: SelectedElement[]
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

/** One quality run's assessment of the shown quote or excerpt. */
export interface RunQuality {
  runLabel: string
  status: 'succeeded' | 'failed' | 'notAssessed'
  verdict: string | null
  problems: string[]
}

/** A shown quote's or excerpt's assessments in the admin-chosen quality runs (docs/features/quote-quality.md). */
export interface QuoteQuality {
  /** Oldest run first. */
  runs: RunQuality[]
  /** Computed from the text, so given once for all runs. */
  precheckFlags: string[]
}

/** A problem flag the remote can pick: one of the model's problems, or one a human added. */
export interface QualityFlag {
  code: string
  label: string
  custom: boolean
}

/** Rating mode for a shown quote or excerpt (docs/features/quote-quality.md, "Human ratings on TV"). */
export interface QuoteRating {
  runLabel: string
  /** Null while the shown text is not rated yet. */
  verdict: string | null
  problems: string[]
  /** The model's problems in their fixed order, then those humans added. */
  flags: QualityFlag[]
}
