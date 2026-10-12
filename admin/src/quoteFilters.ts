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

const ENRICHMENT_VALUES: readonly string[] = ['has', 'none', 'notRun']

function enrichmentValues(params: URLSearchParams, name: string): Set<EnrichmentFilter> {
  return new Set(params.getAll(name).filter((value) => ENRICHMENT_VALUES.includes(value)) as EnrichmentFilter[])
}

/**
 * Control values for a stored filter query (as made by `quoteFilterQuery` in api.ts), the inverse of
 * `toQuoteFilter` plus that query.
 */
export function quoteFilterStateFromQuery(query: string): QuoteFilterState {
  const params = new URLSearchParams(query)
  const minLength = params.get('minLength')
  const maxLength = params.get('maxLength')
  return {
    search: params.get('search') ?? '',
    authors: new Set(params.getAll('author')),
    sources: new Set(params.getAll('source')),
    languages: new Set(params.getAll('language')),
    providers: new Set(params.getAll('provider')),
    confidences: new Set(params.getAll('sourceConfidence')),
    lengthOp: maxLength !== null && minLength === null ? 'below' : 'above',
    lengthValue: minLength ?? maxLength ?? '',
    tag: params.get('tag') ?? '',
    excerpts: enrichmentValues(params, 'excerpts'),
    interpretations: enrichmentValues(params, 'interpretations'),
    tags: enrichmentValues(params, 'tags'),
    qualityRun: params.get('qualityRun') ?? '',
    qualityVerdicts: new Set(params.getAll('qualityVerdict')),
    qualityFlags: new Set(params.getAll('qualityFlag')),
  }
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
