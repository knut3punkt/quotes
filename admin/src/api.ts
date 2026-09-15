import type {
  Author,
  AuthorEnrichmentResponse,
  ApproveImportedQuoteRequest,
  BulkActionResponse,
  BulkDeleteImportedQuotesRequest,
  BulkUpdateImportedQuoteStatusRequest,
  ImportedQuote,
  NewSourceRequest,
  PagedImportedQuotes,
  ProcessingStatus,
  Quote,
  ScriptureImportResult,
  Source,
  SourceType,
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

export function approveImportedQuote(id: number, body: ApproveImportedQuoteRequest): Promise<Quote> {
  return request(`/admin/imported-quotes/${id}/approve`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function deleteImportedQuote(id: number): Promise<void> {
  return request(`/admin/imported-quotes/${id}`, { method: 'DELETE' })
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
