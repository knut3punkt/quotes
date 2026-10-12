export type ProcessingStatus = 'pending' | 'approved' | 'rejected' | 'duplicate'

export type SourceConfidence = 'sourced' | 'attributed' | 'unsourced' | 'disputed'

export type LengthFilterOp = 'above' | 'below'

export interface ImportedQuote {
  id: number
  provider: string
  providerQuoteId: string
  rawText: string
  rawAuthor: string | null
  rawSourceLocation: string | null
  rawSourceTitle: string | null
  rawSourceYear: number | null
  sourceId: number | null
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

export type PossibleDuplicateFilter = 'flagged' | 'notFlagged'

/** Server-side filters for imported quotes; `authors` and `sources` hold the keys from the filter-options endpoint. */
export interface ImportedQuoteFilter {
  statuses: ProcessingStatus[]
  provider?: string
  sourceConfidence?: SourceConfidence
  search?: string
  authors: string[]
  sources: string[]
  possibleDuplicate?: PossibleDuplicateFilter
  /** Inclusive ISO timestamp. */
  importedFrom?: string
  /** Exclusive ISO timestamp. */
  importedBefore?: string
  minLength?: number
  maxLength?: number
}

export interface ImportedQuoteSelectionItem {
  id: number
  processingStatus: ProcessingStatus
  /** Has an author or a source, so bulk approve can approve it. */
  approvable: boolean
  quoteId: number | null
}

/** A multi-select filter choice; `value` is the key sent back to the server. */
export interface FilterOption {
  value: string
  label: string
  /** Matching rows, when known. */
  count?: number
}

export interface ImportedQuoteFilterOptions {
  providers: string[]
  statusCounts: Partial<Record<ProcessingStatus, number>>
  authors: FilterOption[]
  sources: FilterOption[]
}

export interface Quote {
  id: number
  text: string
  authorId: number | null
  sourceId: number | null
  sourceDetail: string | null
  language: string
}

export interface QuoteExcerpt {
  id: number
  quoteId: number
  text: string
  startOffset: number
  endOffset: number
  independence: number
  completeness: number
  quotability: number
  contextualFidelity: number | null // null when the fidelity check was skipped (failed the blind review)
  reason: string
  contextSignals: string[]
  judgeNotes: string
  meetsThresholds: boolean
}

export type QuoteExtractionOutcome = 'extracted' | 'skippedTooShort' | 'noExcerptsFound' | 'failed' | 'notFound'

export interface QuoteExtractionResult {
  quoteId: number
  outcome: QuoteExtractionOutcome
  excerpts: QuoteExcerpt[]
}

export interface ExtractQuoteExcerptsResponse {
  results: QuoteExtractionResult[]
}

export interface QuoteInterpretation {
  id: number
  quoteId: number
  excerptId: number | null
  lens: string
  interpretation: string
  textualSupport: number
  speculativeness: number
}

export type QuoteInterpretationOutcome = 'generated' | 'noInterpretationsFound' | 'failed' | 'notFound'

export interface QuoteInterpretationResult {
  quoteId: number
  outcome: QuoteInterpretationOutcome
  interpretations: QuoteInterpretation[]
}

export interface GenerateQuoteInterpretationsResponse {
  results: QuoteInterpretationResult[]
}

export interface QuoteListItem {
  id: number
  text: string
  authorId: number | null
  authorName: string | null
  sourceId: number | null
  sourceTitle: string | null
  sourceDetail: string | null
  language: string
  excerpts: QuoteExcerpt[]
  interpretations: QuoteInterpretation[]
  tags: QuoteTag[]
  /** Assessments of the quote and its excerpts in the quality run the list is filtered by. */
  quality?: QualityAssessment[]
}

export type TagFacet = 'concept' | 'mood' | 'motif'

export type TagBreadth = 'broad' | 'specific'

export interface QuoteTag {
  assignmentId: number
  quoteId: number
  excerptId: number | null // null when the tag is on the whole quote
  tagId: number
  facet: TagFacet
  name: string
  breadth: TagBreadth | null // concepts only
  relevance: number // 1 = peripheral, 2 = significant, 3 = central
  basis: 'text' | 'interpretation'
  origin: 'llm' | 'admin'
}

export type QuoteTaggingOutcome = 'tagged' | 'noTagsFound' | 'failed' | 'notFound'

export interface QuoteTaggingResult {
  quoteId: number
  outcome: QuoteTaggingOutcome
  tags: QuoteTag[]
}

export interface GenerateQuoteTagsResponse {
  results: QuoteTaggingResult[]
}

export interface TagSummary {
  id: number
  facet: TagFacet
  name: string
  breadth: TagBreadth | null
  usageCount: number
  aliases: string[]
}

export interface AddQuoteTagRequest {
  facet: TagFacet
  name: string
  excerptId?: number
  breadth?: TagBreadth
  relevance?: number
}

export interface UpdateTagRequest {
  name?: string
  breadth?: TagBreadth
}

export interface PagedQuotes {
  items: QuoteListItem[]
  total: number
  page: number
  pageSize: number
}

/** Whether a quote has results from an enrichment pipeline; `none` = a run succeeded but found nothing. */
export type EnrichmentFilter = 'has' | 'none' | 'notRun'

/** Server-side filters for approved quotes; multi-value filters match any of their values, empty means no filter. */
export interface QuoteFilter {
  /** Keys from the filter-options endpoint (`id:N` or `none`). */
  authors: string[]
  sources: string[]
  languages: string[]
  search?: string
  providers: string[]
  sourceConfidences: string[]
  minLength?: number
  maxLength?: number
  tag?: string
  excerpts: EnrichmentFilter[]
  interpretations: EnrichmentFilter[]
  tags: EnrichmentFilter[]
  qualityRun?: number
  /** Verdicts plus 'notAssessed' and 'failed'; needs qualityRun. */
  qualityVerdicts: string[]
  /** Model problems or precheck flags; needs qualityRun. */
  qualityFlags: string[]
}

/** The TV preview filter: the TV shows only quotes, and excerpts, matching it (docs/features/quote-quality.md). */
export interface TvFilter {
  /** The quote list's filter parameters, as a URL query string. */
  query: string
  updatedAt: string
  qualityRunLabel: string | null
  /** How many quotes the TV can show under it, and how many of those as an excerpt. */
  poolQuotes: number
  poolAsExcerpt: number
}

/** The TV's quality overlay: while on, the TV shows each shown quote's or excerpt's verdict and flags in the run. */
export interface TvDisplaySettings {
  showQuality: boolean
  /** The runs the TV quality overlay shows, oldest first. */
  qualityRuns: { id: number; label: string }[]
}

export interface QuoteSelectionItem {
  id: number
  wordCount: number
}

export interface QuoteFilterOptions {
  authors: FilterOption[]
  sources: FilterOption[]
  languages: FilterOption[]
  providers: FilterOption[]
  sourceConfidences: FilterOption[]
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
  translation: string | null
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
  translation?: string
}

export interface ApproveImportedQuoteRequest {
  authorId?: number
  newAuthorName?: string
  sourceId?: number
  newSource?: NewSourceRequest
  sourceDetail?: string
  text?: string
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

export interface BulkUnapproveQuotesRequest {
  quoteIds: number[]
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
  quotesSkippedUntranslatable: number
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

export type ImageKind = 'background' | 'element'

export type ImageGenerationItemStatus =
  | 'queued'
  | 'submitting'
  | 'waiting'
  | 'running'
  | 'saving'
  | 'done'
  | 'failed'
  | 'cancelled'

/** One tag in an image generation run, in queue order. Times are epoch milliseconds. */
export interface ImageGenerationItem {
  tagId: number
  tagName: string
  facet: TagFacet
  kind: ImageKind
  /** The style profile id from the image's prompt recipe. */
  style?: string | null
  status: ImageGenerationItemStatus
  prompt?: string | null
  seed?: number | null
  step?: number | null
  maxSteps?: number | null
  error?: string | null
  filePath?: string | null
  startedAt?: number | null
  finishedAt?: number | null
}

export type ImageGenerationJobStatus = 'idle' | 'running' | 'cancelling' | 'finished' | 'cancelled'

export interface ImageGenerationJobState {
  status: ImageGenerationJobStatus
  comfyUiBaseUrl: string
  startedAt?: number | null
  finishedAt?: number | null
  comfyQueueRemaining?: number | null
  connectionWarning?: string | null
  items: ImageGenerationItem[]
}

export type QualityVerdict = 'excellent' | 'good' | 'acceptable' | 'weak' | 'reject'

export const QUALITY_VERDICTS: QualityVerdict[] = ['excellent', 'good', 'acceptable', 'weak', 'reject']

export const QUALITY_PROBLEMS = [
  'FRAGMENT',
  'CONTEXT_DEPENDENT',
  'NARRATION_OR_DIALOGUE',
  'EDITORIAL_OR_META',
  'POSSIBLY_MISATTRIBUTED',
  'NON_ENGLISH',
  'FLAT',
  'CLICHE',
  'TOO_LONG',
  'SENSITIVE_OR_DATED',
] as const

export const PRECHECK_FLAGS = [
  'OVER_WORD_LIMIT',
  'VERY_SHORT',
  'LOWERCASE_START',
  'TRAILING_CONTINUATION',
  'LIST_FRAGMENT',
  'BRACKETED_INSERTION',
  'ELLIPSIS',
  'UNBALANCED_QUOTES',
  'EDITORIAL_PREFIX',
  'CITATION_ONLY',
  'LIKELY_NON_ENGLISH',
] as const

export interface QualityRun {
  id: number
  label: string
  runner: 'llm' | 'external'
  methodVersion: string
  promptVersion: string
  modelId: string | null
  notes: string | null
  createdAt: string
  assessedCount: number
  failedCount: number
  verdictCounts: Partial<Record<QualityVerdict, number>>
}

export interface QualityAssessment {
  id: number
  runId: number
  quoteId: number | null
  excerptId: number | null
  subjectKind: 'quote' | 'excerpt'
  subjectText: string
  status: 'succeeded' | 'failed'
  errorMessage?: string | null
  reading?: string | null
  insight?: string | null
  assessment?: string | null
  unresolvedReferences: string[]
  problems: string[]
  standsAlone?: number | null
  completeness?: number | null
  substance?: number | null
  expression?: number | null
  verdict?: QualityVerdict | null
  precheckFlags: string[]
}

export type QuoteQualityOutcome = 'assessed' | 'failed' | 'notFound'

export interface QuoteQualityResult {
  quoteId: number
  outcome: QuoteQualityOutcome
  assessments: QualityAssessment[]
}

export interface AssessQualityResponse {
  results: QuoteQualityResult[]
}

export interface QualityLevelComparison {
  name: string
  meanA: number
  meanB: number
  exactAgreement: number
}

export interface QualityFlagComparison {
  flag: string
  onlyA: number
  onlyB: number
  both: number
}

export interface QualityDisagreement {
  subjectKind: 'quote' | 'excerpt'
  subjectText: string
  quoteId: number | null
  excerptId: number | null
  a: QualityAssessment
  b: QualityAssessment
}

export interface QualityComparison {
  runA: number
  runB: number
  matched: number
  onlyInA: number
  onlyInB: number
  verdicts: QualityVerdict[]
  verdictMatrix: number[][]
  exactAgreement: number
  withinOneAgreement: number
  weightedKappa: number
  keepDropMatrix: number[][]
  keepDropKappa: number
  levels: QualityLevelComparison[]
  flags: QualityFlagComparison[]
  disagreements: QualityDisagreement[]
}
