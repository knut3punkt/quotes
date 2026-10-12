import type { PublicQuote, QuoteQuality, QuoteVisuals } from './types'

const DEFAULT_COUNT = 20

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function request<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`)
  if (!response.ok) {
    const body = await response.text()
    throw new Error(`${response.status} ${response.statusText}${body ? `: ${body}` : ''}`)
  }
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
