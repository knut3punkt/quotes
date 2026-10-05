import type {
  AddQuoteTagRequest,
  Author,
  AuthorEnrichmentResponse,
  ApproveImportedQuoteRequest,
  BulkActionResponse,
  BulkDeleteImportedQuotesRequest,
  BulkUnapproveQuotesRequest,
  BulkUpdateImportedQuoteStatusRequest,
  ExtractQuoteExcerptsResponse,
  GenerateQuoteInterpretationsResponse,
  GenerateQuoteTagsResponse,
  ImageGenerationJobState,
  ImportedQuote,
  NewSourceRequest,
  PagedImportedQuotes,
  PagedQuotes,
  ProcessingStatus,
  Quote,
  QuoteFilter,
  QuoteFilterOptions,
  QuoteTag,
  ScriptureImportResult,
  Source,
  SourceType,
  TagFacet,
  TagSummary,
  UpdateTagRequest,
  WikiquoteAuthorSearchResponse,
  WikiquoteImportRequest,
  WikiquoteImportResponse,
} from './types'

// A generous default page size: the admin UI still filters client-side over this whole page, so
// this preserves "see everything" behavior until it adopts real pagination controls.
const DEFAULT_PAGE_SIZE = 2000

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!response.ok) {
    const body = await response.text()
    throw new Error(`${response.status} ${response.statusText}${body ? `: ${body}` : ''}`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export async function fetchImportedQuotes(): Promise<ImportedQuote[]> {
  const page = await request<PagedImportedQuotes>(`/admin/imported-quotes?pageSize=${DEFAULT_PAGE_SIZE}`)
  return page.items
}

export function fetchQuotes(filter: QuoteFilter): Promise<PagedQuotes> {
  const params = new URLSearchParams()
  if (filter.authorId !== undefined) params.set('authorId', String(filter.authorId))
  if (filter.sourceId !== undefined) params.set('sourceId', String(filter.sourceId))
  if (filter.language) params.set('language', filter.language)
  if (filter.search) params.set('search', filter.search)
  if (filter.provider) params.set('provider', filter.provider)
  if (filter.sourceConfidence) params.set('sourceConfidence', filter.sourceConfidence)
  if (filter.minLength !== undefined) params.set('minLength', String(filter.minLength))
  if (filter.maxLength !== undefined) params.set('maxLength', String(filter.maxLength))
  if (filter.tag) params.set('tag', filter.tag)
  if (filter.excerpts) params.set('excerpts', filter.excerpts)
  if (filter.interpretations) params.set('interpretations', filter.interpretations)
  if (filter.tags) params.set('tags', filter.tags)
  params.set('page', String(filter.page))
  params.set('pageSize', String(filter.pageSize))
  return request(`/admin/quotes?${params.toString()}`)
}

export function fetchQuoteFilterOptions(): Promise<QuoteFilterOptions> {
  return request('/admin/quotes/filter-options')
}

export function fetchAuthors(): Promise<Author[]> {
  return request('/admin/authors')
}

export function fetchSources(): Promise<Source[]> {
  return request('/admin/sources')
}

export function fetchSourceTypes(): Promise<SourceType[]> {
  return request('/admin/source-types')
}

export function createSource(body: NewSourceRequest): Promise<Source> {
  return request('/admin/sources', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function updateImportedQuoteStatus(
  id: number,
  status: ProcessingStatus,
  reviewedBy?: string,
  reviewNote?: string,
): Promise<ImportedQuote> {
  return request(`/admin/imported-quotes/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status, reviewedBy, reviewNote }),
  })
}

export function bulkUpdateImportedQuoteStatus(body: BulkUpdateImportedQuoteStatusRequest): Promise<BulkActionResponse> {
  return request('/admin/imported-quotes/bulk/status', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function bulkDeleteImportedQuotes(body: BulkDeleteImportedQuotesRequest): Promise<BulkActionResponse> {
  return request('/admin/imported-quotes/bulk/delete', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function bulkUnapproveQuotes(body: BulkUnapproveQuotesRequest): Promise<BulkActionResponse> {
  return request('/admin/quotes/bulk/unapprove', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function approveImportedQuote(id: number, body: ApproveImportedQuoteRequest): Promise<Quote> {
  return request(`/admin/imported-quotes/${id}/approve`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function importWikiquoteAuthors(body: WikiquoteImportRequest): Promise<WikiquoteImportResponse> {
  return request('/admin/import/wikiquote', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function searchWikiquoteAuthors(query: string): Promise<WikiquoteAuthorSearchResponse> {
  return request(`/admin/import/wikiquote/authors?q=${encodeURIComponent(query)}`)
}

export function importTaoTeChing(): Promise<ScriptureImportResult> {
  return request('/admin/import/tao-te-ching', { method: 'POST' })
}

export function importBhagavadGita(): Promise<ScriptureImportResult> {
  return request('/admin/import/bhagavad-gita', { method: 'POST' })
}

export function importDhammapada(): Promise<ScriptureImportResult> {
  return request('/admin/import/dhammapada', { method: 'POST' })
}

export function importBible(): Promise<ScriptureImportResult> {
  return request('/admin/import/bible', { method: 'POST' })
}

export function importQuran(): Promise<ScriptureImportResult> {
  return request('/admin/import/quran', { method: 'POST' })
}

export function enrichAuthorsFromWikidata(): Promise<AuthorEnrichmentResponse> {
  return request('/admin/authors/enrich', { method: 'POST' })
}

export function extractQuoteExcerpts(quoteIds: number[]): Promise<ExtractQuoteExcerptsResponse> {
  return request('/admin/quotes/extract-excerpts', {
    method: 'POST',
    body: JSON.stringify({ quoteIds }),
  })
}

export function generateQuoteInterpretations(quoteIds: number[]): Promise<GenerateQuoteInterpretationsResponse> {
  return request('/admin/quotes/generate-interpretations', {
    method: 'POST',
    body: JSON.stringify({ quoteIds }),
  })
}

export function generateQuoteTags(quoteIds: number[]): Promise<GenerateQuoteTagsResponse> {
  return request('/admin/quotes/generate-tags', {
    method: 'POST',
    body: JSON.stringify({ quoteIds }),
  })
}

export function addQuoteTag(quoteId: number, body: AddQuoteTagRequest): Promise<QuoteTag> {
  return request(`/admin/quotes/${quoteId}/tags`, { method: 'POST', body: JSON.stringify(body) })
}

export function rejectQuoteTag(assignmentId: number): Promise<void> {
  return request(`/admin/tag-assignments/${assignmentId}/reject`, { method: 'POST' })
}

export function fetchTags(params: { facet?: TagFacet; search?: string; limit?: number } = {}): Promise<TagSummary[]> {
  const query = new URLSearchParams()
  if (params.facet) query.set('facet', params.facet)
  if (params.search) query.set('search', params.search)
  query.set('limit', String(params.limit ?? DEFAULT_PAGE_SIZE))
  return request(`/admin/tags?${query.toString()}`)
}

export function updateTag(tagId: number, body: UpdateTagRequest): Promise<TagSummary> {
  return request(`/admin/tags/${tagId}`, { method: 'PATCH', body: JSON.stringify(body) })
}

export function mergeTag(tagId: number, intoTagId: number): Promise<TagSummary> {
  return request(`/admin/tags/${tagId}/merge`, { method: 'POST', body: JSON.stringify({ intoTagId }) })
}

export function fetchImageGenerationStatus(): Promise<ImageGenerationJobState> {
  return request('/admin/image-generation/status')
}

export function startImageGeneration(): Promise<ImageGenerationJobState> {
  return request('/admin/image-generation/start', { method: 'POST' })
}

export function cancelImageGeneration(): Promise<ImageGenerationJobState> {
  return request('/admin/image-generation/cancel', { method: 'POST' })
}

const IMAGE_GENERATION_RECONNECT_MILLIS = 3000

/**
 * Follows the image generation job over the server's WebSocket, which pushes the whole job state a few
 * times a second. Reconnects after a drop; `onConnectionChange` lets the UI say when updates have stopped.
 * Returns a function that closes the connection for good.
 */
export function subscribeImageGeneration(
  onState: (state: ImageGenerationJobState) => void,
  onConnectionChange: (connected: boolean) => void,
): () => void {
  const url = `${BASE_URL.replace(/^http/, 'ws')}/admin/image-generation/ws`
  let socket: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined
  let closed = false

  const connect = () => {
    socket = new WebSocket(url)
    socket.onopen = () => onConnectionChange(true)
    socket.onmessage = (event) => {
      try {
        onState(JSON.parse(event.data as string) as ImageGenerationJobState)
      } catch {
        // A malformed frame is skipped; the next push carries the full state again.
      }
    }
    socket.onclose = () => {
      onConnectionChange(false)
      if (!closed) reconnectTimer = setTimeout(connect, IMAGE_GENERATION_RECONNECT_MILLIS)
    }
  }

  connect()
  return () => {
    closed = true
    clearTimeout(reconnectTimer)
    const current = socket
    if (!current) return
    current.onmessage = null
    current.onclose = null
    // Closing a socket mid-handshake drops the TCP connection under Ktor's Netty upgrade, which logs a
    // NullPointerException server-side; React StrictMode's dev double-mount does exactly that. Let the
    // handshake finish and close cleanly instead.
    if (current.readyState === WebSocket.CONNECTING) {
      current.onopen = () => current.close()
    } else {
      current.onopen = null
      current.close()
    }
  }
}
