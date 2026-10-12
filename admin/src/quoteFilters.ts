import { isDefaultFilterState } from './filterState'
import type { EnrichmentFilter, LengthFilterOp, QuoteFilter } from './types'

/** Raw control values of the approved-quotes filter bar; an empty Set means no filter. */
export interface QuoteFilterState {
  search: string
  /** Keys from the filter-options endpoint (`id:N` or `none`). */
  authors: Set<string>
  sources: Set<string>
  languages: Set<string>
  providers: Set<string>
  confidences: Set<string>
  lengthOp: LengthFilterOp
  lengthValue: string
  tag: string
  excerpts: Set<EnrichmentFilter>
  interpretations: Set<EnrichmentFilter>
  tags: Set<EnrichmentFilter>
  /** A quality run id, or '' for none. */
  qualityRun: string
  qualityVerdicts: Set<string>
  qualityFlags: Set<string>
}

export const DEFAULT_QUOTE_FILTERS: QuoteFilterState = {
  search: '',
  authors: new Set(),
  sources: new Set(),
  languages: new Set(),
  providers: new Set(),
  confidences: new Set(),
  lengthOp: 'above',
  lengthValue: '',
  tag: '',
  excerpts: new Set(),
  interpretations: new Set(),
  tags: new Set(),
  qualityRun: '',
  qualityVerdicts: new Set(),
  qualityFlags: new Set(),
}

export function isDefaultQuoteFilters(filters: QuoteFilterState): boolean {
  return isDefaultFilterState(filters, DEFAULT_QUOTE_FILTERS)
}

/** The server query for the given control values. Pass `search`, `lengthValue` and `tag` already debounced. */
export function toQuoteFilter(filters: QuoteFilterState): QuoteFilter {
  const lengthThreshold = filters.lengthValue.trim() === '' ? undefined : Number(filters.lengthValue)
  const validLength = lengthThreshold !== undefined && Number.isFinite(lengthThreshold) ? lengthThreshold : undefined
  return {
    authors: [...filters.authors],
    sources: [...filters.sources],
    languages: [...filters.languages],
    search: filters.search.trim() || undefined,
    providers: [...filters.providers],
    sourceConfidences: [...filters.confidences],
    minLength: filters.lengthOp === 'above' ? validLength : undefined,
    maxLength: filters.lengthOp === 'below' ? validLength : undefined,
    tag: filters.tag.trim() || undefined,
    excerpts: [...filters.excerpts],
    interpretations: [...filters.interpretations],
    tags: [...filters.tags],
    // Verdict and flag filters only mean something within a run.
    qualityRun: filters.qualityRun ? Number(filters.qualityRun) : undefined,
    qualityVerdicts: filters.qualityRun ? [...filters.qualityVerdicts] : [],
    qualityFlags: filters.qualityRun ? [...filters.qualityFlags] : [],
  }
}
