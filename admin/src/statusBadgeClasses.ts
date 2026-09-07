import type { ProcessingStatus, SourceConfidence } from './types'

export function statusBadgeClassName(status: ProcessingStatus): string {
  switch (status) {
    case 'pending':
      return 'border-transparent bg-brand-tint text-primary'
    case 'approved':
      return 'border-transparent bg-success/10 text-success'
    case 'rejected':
    case 'duplicate':
      return 'border-transparent bg-destructive/10 text-destructive'
  }
}

export function confidenceBadgeClassName(confidence: SourceConfidence): string {
  switch (confidence) {
    case 'sourced':
      return 'border-transparent bg-success/10 text-success'
    case 'attributed':
      return 'border-transparent bg-brand-tint text-primary'
    case 'unsourced':
      return 'border-transparent bg-destructive/10 text-destructive'
    case 'disputed':
      return 'border-transparent bg-warning/10 text-warning'
  }
}
