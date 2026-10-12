import type { PublicQuote, QualityFlag, QuoteQuality, QuoteRating, QuoteVisuals } from './types'

const DEFAULT_COUNT = 20

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, init)
  if (!response.ok) throw new Error(await errorMessage(response))
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function fetchRandomQuotes(count: number = DEFAULT_COUNT): Promise<PublicQuote[]> {
  return request(`/api/quotes/random?count=${count}`)
}

/** A fresh server-side choice of background and motif element for one quote, or the excerpt of it shown. */
export function fetchQuoteVisuals(quoteId: number, excerptId: number | null): Promise<QuoteVisuals> {
  const query = excerptId === null ? '' : `?excerptId=${excerptId}`
  return request(`/api/quotes/${quoteId}/visuals${query}`)
}

/**
 * The quality overlay for a quote, or the excerpt of it shown: its verdict and flags in the run chosen in the admin.
 * Null while the overlay is switched off.
 */
export async function fetchQuoteQuality(quoteId: number, excerptId: number | null): Promise<QuoteQuality | null> {
  const query = excerptId === null ? '' : `?excerptId=${excerptId}`
  return (await request<QuoteQuality | undefined>(`/api/quotes/${quoteId}/quality${query}`)) ?? null
}

/** The server snaps `width` to one of a few sizes and caches the downscaled copy. */
export function tagImageUrl(imageId: number, width: number): string {
  return `${BASE_URL}/api/tag-images/${imageId}?width=${width}`
}

/** The server's `{ "error": ... }` message when it gave one, else the status. */
async function errorMessage(response: Response): Promise<string> {
  const body = await response.text()
  try {
    const parsed: unknown = JSON.parse(body)
    if (parsed && typeof parsed === 'object' && 'error' in parsed && typeof parsed.error === 'string') return parsed.error
  } catch {
    // Not JSON; fall through to the raw body.
  }
  return `${response.status} ${response.statusText}${body ? `: ${body}` : ''}`
}

function sendJson<T>(method: 'PUT' | 'POST', path: string, body: unknown): Promise<T> {
  return request(path, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
}

/** Rating mode for a quote, or the excerpt of it shown, with its rating so far. Null while rating mode is off. */
export async function fetchQuoteRating(quoteId: number, excerptId: number | null): Promise<QuoteRating | null> {
  const query = excerptId === null ? '' : `?excerptId=${excerptId}`
  return (await request<QuoteRating | undefined>(`/api/quotes/${quoteId}/rating${query}`)) ?? null
}

/** Rates the shown text into the admin-chosen human run, replacing an earlier rating of it. */
export function saveQuoteRating(
  quoteId: number,
  excerptId: number | null,
  verdict: string,
  problems: string[],
): Promise<QuoteRating> {
  return sendJson('PUT', `/api/quotes/${quoteId}/rating`, { excerptId, verdict, problems })
}

/** The flag for [label], created when new; a label matching an existing flag returns that one. */
export function createQualityFlag(label: string): Promise<QualityFlag> {
  return sendJson('POST', '/api/quality-flags', { label })
}
