import type {
  Author,
  ApproveImportedQuoteRequest,
  ImportedQuote,
  ProcessingStatus,
  Quote,
  Source,
} from './types'

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
  return response.json() as Promise<T>
}

export function fetchImportedQuotes(): Promise<ImportedQuote[]> {
  return request('/admin/imported-quotes')
}

export function fetchAuthors(): Promise<Author[]> {
  return request('/admin/authors')
}

export function fetchSources(): Promise<Source[]> {
  return request('/admin/sources')
}

export function updateImportedQuoteStatus(id: number, status: ProcessingStatus): Promise<ImportedQuote> {
  return request(`/admin/imported-quotes/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

export function approveImportedQuote(id: number, body: ApproveImportedQuoteRequest): Promise<Quote> {
  return request(`/admin/imported-quotes/${id}/approve`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}
