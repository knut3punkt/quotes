export type ProcessingStatus = 'pending' | 'approved' | 'rejected' | 'duplicate'

export type SourceConfidence = 'sourced' | 'attributed' | 'unsourced' | 'disputed'

export type LengthFilterOp = 'above' | 'below'

export interface ImportedQuote {
  id: number
  provider: string
  providerQuoteId: string
  rawText: string
  rawAuthor: string | null
  rawPayload: Record<string, unknown>
  importedAt: string
  processingStatus: ProcessingStatus
  quoteId: number | null
  sourceConfidence: SourceConfidence | null
  reviewedBy: string | null
  reviewedAt: string | null
  reviewNote: string | null
  duplicateOfId: number | null
  language: string
  possibleDuplicateOfId: number | null
}

export interface PagedImportedQuotes {
  items: ImportedQuote[]
  total: number
  page: number
  pageSize: number
}

export interface Quote {
  id: number
  text: string
  authorId: number
  sourceId: number | null
  sourceDetail: string | null
  verified: boolean
  language: string
}

export interface Author {
  id: number
  name: string
  birthYear: number | null
  deathYear: number | null
  wikidataQid: string | null
}

export interface Source {
  id: number
  title: string
  typeCode: string
  year: number | null
  url: string | null
  citationUnit: string | null
  license: string | null
  attributionText: string | null
}

export interface SourceType {
  code: string
  description: string
}

export interface NewSourceRequest {
  title: string
  typeCode: string
  year?: number
  url?: string
  citationUnit?: string
  license?: string
  attributionText?: string
}

export interface ApproveImportedQuoteRequest {
  authorId?: number
  newAuthorName?: string
  sourceId?: number
  newSource?: NewSourceRequest
  sourceDetail?: string
  text?: string
  verified?: boolean
  reviewedBy?: string
}

export interface BulkUpdateImportedQuoteStatusRequest {
  ids: number[]
  status: ProcessingStatus
  reviewedBy?: string
  reviewNote?: string
}

export interface BulkDeleteImportedQuotesRequest {
  ids: number[]
}

export interface BulkActionResponse {
  succeededIds: number[]
  failedIds: number[]
}

export interface WikiquoteImportRequest {
  authorNames: string[]
  sourceConfidence?: SourceConfidence[]
}

export interface WikiquoteAuthorImportResult {
  requestedName: string
  resolvedTitle: string | null
  found: boolean
  quotesInserted: number
  quotesSkippedAsDuplicate: number
  quotesBySection: Record<string, number>
}

export interface WikiquoteImportResponse {
  results: WikiquoteAuthorImportResult[]
}

export interface WikiquoteAuthorSearchResponse {
  results: string[]
}

export interface ScriptureImportResult {
  sourceId: number
  quotesInserted: number
  quotesSkippedAsDuplicate: number
  quotesFailedToFetch: number
}

export interface AuthorEnrichmentResponse {
  checked: number
  enriched: number
  skipped: number
}
