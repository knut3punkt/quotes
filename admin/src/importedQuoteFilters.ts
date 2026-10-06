import { isDefaultFilterState } from './filterState'
import type {
  ImportedQuote,
  ImportedQuoteFilter,
  ImportedQuoteSelectionItem,
  LengthFilterOp,
  PossibleDuplicateFilter,
  ProcessingStatus,
  SourceConfidence,
} from './types'

/** Raw control values; `'all'` stands for "no filter" because Radix Select doesn't allow an empty value. */
export interface ImportedQuoteFilterState {
  statuses: Set<ProcessingStatus>
  confidence: 'all' | SourceConfidence
  provider: 'all' | string
  /** Author keys from the filter-options endpoint; empty means no filter. */
  authors: Set<string>
  /** Source keys from the filter-options endpoint; empty means no filter. */
  sources: Set<string>
  possibleDuplicate: 'all' | PossibleDuplicateFilter
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

export function isDefaultImportedQuoteFilters(filters: ImportedQuoteFilterState): boolean {
  return isDefaultFilterState(filters, DEFAULT_IMPORTED_QUOTE_FILTERS)
}

/** Local midnight at the start of a `yyyy-mm-dd` date, plus `dayOffset` days, as an ISO timestamp. */
function localMidnight(date: string, dayOffset: number): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day + dayOffset).toISOString()
}

/**
 * The server query for the given control values. Pass `search` and `lengthValue` already debounced. The date range
 * covers whole local days: from the start of `importedFrom` up to (excluding) the day after `importedTo`.
 */
export function toImportedQuoteFilter(filters: ImportedQuoteFilterState): ImportedQuoteFilter {
  const lengthThreshold = filters.lengthValue.trim() === '' ? undefined : Number(filters.lengthValue)
  const validLength =
    lengthThreshold !== undefined && Number.isFinite(lengthThreshold) && lengthThreshold >= 0 ? lengthThreshold : undefined
  return {
    statuses: [...filters.statuses],
    provider: filters.provider === 'all' ? undefined : filters.provider,
    sourceConfidence: filters.confidence === 'all' ? undefined : filters.confidence,
    search: filters.search.trim() || undefined,
    authors: [...filters.authors],
    sources: [...filters.sources],
    possibleDuplicate: filters.possibleDuplicate === 'all' ? undefined : filters.possibleDuplicate,
    importedFrom: filters.importedFrom ? localMidnight(filters.importedFrom, 0) : undefined,
    importedBefore: filters.importedTo ? localMidnight(filters.importedTo, 1) : undefined,
    minLength: filters.lengthOp === 'above' ? validLength : undefined,
    maxLength: filters.lengthOp === 'below' ? validLength : undefined,
  }
}

/** The same summary the selection endpoint returns, for a loaded row. */
export function importedQuoteSelectionItem(quote: ImportedQuote): ImportedQuoteSelectionItem {
  return {
    id: quote.id,
    processingStatus: quote.processingStatus,
    approvable: Boolean(quote.rawAuthor?.trim()) || quote.sourceId !== null,
    quoteId: quote.quoteId,
  }
}
