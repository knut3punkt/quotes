import type { ProcessingStatus } from './types'

export function canApprove(status: ProcessingStatus): boolean {
  return status !== 'approved'
}

export function canReject(status: ProcessingStatus): boolean {
  return status !== 'approved' && status !== 'rejected'
}

export function canMarkDuplicate(status: ProcessingStatus): boolean {
  return status !== 'approved' && status !== 'duplicate'
}

export function canResetToPending(status: ProcessingStatus): boolean {
  return status !== 'approved' && status !== 'pending'
}
