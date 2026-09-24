import type { PublicQuote } from './types'

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
