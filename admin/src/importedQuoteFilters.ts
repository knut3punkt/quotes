import type { MultiSelectOption } from './components/MultiSelectFilter'
import type { ImportedQuote, LengthFilterOp, ProcessingStatus, Source, SourceConfidence } from './types'

export type PossibleDuplicateFilter = 'all' | 'flagged' | 'notFlagged'

/** Raw control values; `'all'` stands for "no filter" because Radix Select doesn't allow an empty value. */
export interface ImportedQuoteFilterState {
  statuses: Set<ProcessingStatus>
  confidence: 'all' | SourceConfidence
  provider: 'all' | string
  /** Keys from {@link authorFilterKey}; empty means no filter. */
  authors: Set<string>
  /** Keys from {@link sourceFilterKey}; empty means no filter. */
  sources: Set<string>
  possibleDuplicate: PossibleDuplicateFilter
  /** Inclusive local dates as `yyyy-mm-dd`, empty for an open end. */
  importedFrom: string
  importedTo: string
  search: string
  lengthOp: LengthFilterOp
  lengthValue: string
}

export const DEFAULT_IMPORTED_QUOTE_FILTERS: ImportedQuoteFilterState = {
  statuses: new Set(['pending']),
  confidence: 'all',
  provider: 'all',
  authors: new Set(),
  sources: new Set(),
  possibleDuplicate: 'all',
  importedFrom: '',
  importedTo: '',
  search: '',
  lengthOp: 'above',
  lengthValue: '',
}

function sameSet<T>(a: Set<T>, b: Set<T>): boolean {
  return a.size === b.size && [...a].every((value) => b.has(value))
}

export function isDefaultImportedQuoteFilters(filters: ImportedQuoteFilterState): boolean {
  return (Object.keys(DEFAULT_IMPORTED_QUOTE_FILTERS) as (keyof ImportedQuoteFilterState)[]).every((key) => {
    const value = filters[key]
    const defaultValue = DEFAULT_IMPORTED_QUOTE_FILTERS[key]
    return value instanceof Set && defaultValue instanceof Set ? sameSet(value, defaultValue) : value === defaultValue
  })
}

/** Filter key shared by both author and source for quotes that have neither value. */
export const NONE_FILTER_KEY = 'none'

/** Author key: the trimmed raw author, prefixed so it can never collide with {@link NONE_FILTER_KEY}. */
export function authorFilterKey(quote: ImportedQuote): string {
  const author = quote.rawAuthor?.trim()
  return author ? `name:${author}` : NONE_FILTER_KEY
}

/**
 * Source key, matching what the table's Source column shows: the linked source if there is one, otherwise the
 * unlinked suggested title from the import, otherwise none.
 */
export function sourceFilterKey(quote: ImportedQuote): string {
  if (quote.sourceId !== null) return `id:${quote.sourceId}`
  const rawTitle = quote.rawSourceTitle?.trim()
  return rawTitle ? `raw:${rawTitle}` : NONE_FILTER_KEY
}

/** The import timestamp as a local `yyyy-mm-dd`, comparable with the values of `<input type="date">`. */
function localImportDate(quote: ImportedQuote): string {
  const date = new Date(quote.importedAt)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** Expects `search` and `lengthValue` to already be debounced by the caller. */
export function matchesImportedQuoteFilters(quote: ImportedQuote, filters: ImportedQuoteFilterState): boolean {
  if (!filters.statuses.has(quote.processingStatus)) return false
  if (filters.confidence !== 'all' && quote.sourceConfidence !== filters.confidence) return false
  if (filters.provider !== 'all' && quote.provider !== filters.provider) return false
  if (filters.authors.size > 0 && !filters.authors.has(authorFilterKey(quote))) return false
  if (filters.sources.size > 0 && !filters.sources.has(sourceFilterKey(quote))) return false
  if (filters.possibleDuplicate === 'flagged' && quote.possibleDuplicateOfId === null) return false
  if (filters.possibleDuplicate === 'notFlagged' && quote.possibleDuplicateOfId !== null) return false
  if (filters.importedFrom || filters.importedTo) {
    const importDate = localImportDate(quote)
    if (filters.importedFrom && importDate < filters.importedFrom) return false
    if (filters.importedTo && importDate > filters.importedTo) return false
  }
  const lengthThreshold = filters.lengthValue.trim() === '' ? null : Number(filters.lengthValue)
  if (lengthThreshold !== null && Number.isFinite(lengthThreshold) && lengthThreshold >= 0) {
    const length = quote.rawText.length
    if (filters.lengthOp === 'above' && length <= lengthThreshold) return false
    if (filters.lengthOp === 'below' && length >= lengthThreshold) return false
  }
  const term = filters.search.trim().toLowerCase()
  if (term) {
    const haystack = `${quote.rawText} ${quote.rawAuthor ?? ''}`.toLowerCase()
    if (!haystack.includes(term)) return false
  }
  return true
}

function buildOptions(
  quotes: ImportedQuote[],
  keyOf: (quote: ImportedQuote) => string,
  labelOf: (key: string) => string,
  noneLabel: string,
): MultiSelectOption[] {
  const counts = new Map<string, number>()
  for (const quote of quotes) {
    const key = keyOf(quote)
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  const noneCount = counts.get(NONE_FILTER_KEY) ?? 0
  counts.delete(NONE_FILTER_KEY)
  const options = Array.from(counts, ([value, count]) => ({ value, label: labelOf(value), count })).sort((a, b) =>
    a.label.localeCompare(b.label),
  )
  return [{ value: NONE_FILTER_KEY, label: noneLabel, count: noneCount }, ...options]
}

export function buildAuthorOptions(quotes: ImportedQuote[]): MultiSelectOption[] {
  return buildOptions(quotes, authorFilterKey, (key) => key.slice('name:'.length), '(No author)')
}

export function buildSourceOptions(quotes: ImportedQuote[], sources: Source[]): MultiSelectOption[] {
  const titlesById = new Map(sources.map((source) => [`id:${source.id}`, source.title]))
  return buildOptions(
    quotes,
    sourceFilterKey,
    (key) => titlesById.get(key) ?? (key.startsWith('raw:') ? `${key.slice('raw:'.length)} (suggested)` : key),
    '(No source)',
  )
}
