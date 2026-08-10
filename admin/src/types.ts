export type ProcessingStatus = 'pending' | 'approved' | 'rejected' | 'duplicate'

export type SourceConfidence = 'sourced' | 'attributed' | 'unsourced'

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
}

export interface Quote {
  id: number
  text: string
  authorId: number
  sourceId: number | null
  sourceDetail: string | null
  verified: boolean
}

export interface Author {
  id: number
  name: string
}

export interface Source {
  id: number
  title: string
}

export interface ApproveImportedQuoteRequest {
  authorId?: number
  newAuthorName?: string
  sourceId?: number
  sourceDetail?: string
  text?: string
  verified?: boolean
}
