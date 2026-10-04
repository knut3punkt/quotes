import type { PublicQuote, QuoteVisuals } from './types'

const DEFAULT_COUNT = 20

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function request<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`)
  if (!response.ok) {
    const body = await response.text()
    throw new Error(`${response.status} ${response.statusText}${body ? `: ${body}` : ''}`)
  }
  return response.json() as Promise<T>
}

export function fetchRandomQuotes(count: number = DEFAULT_COUNT): Promise<PublicQuote[]> {
  return request(`/api/quotes/random?count=${count}`)
}

/** A fresh server-side choice of background and collage elements for one quote, or the excerpt of it shown. */
export function fetchQuoteVisuals(quoteId: number, excerptId: number | null): Promise<QuoteVisuals> {
  const query = excerptId === null ? '' : `?excerptId=${excerptId}`
  return request(`/api/quotes/${quoteId}/visuals${query}`)
}

/** The server snaps `width` to one of a few sizes and caches the downscaled copy. */
export function tagImageUrl(imageId: number, width: number): string {
  return `${BASE_URL}/api/tag-images/${imageId}?width=${width}`
}
