import { useEffect, useState } from 'react'

export type ToastVariant = 'default' | 'success' | 'destructive'

export interface ToastItem {
  id: string
  title: string
  description?: string
  variant: ToastVariant
}

const AUTO_DISMISS_MILLIS = 5000

type Listener = (toasts: ToastItem[]) => void

let toasts: ToastItem[] = []
const listeners = new Set<Listener>()
let idCounter = 0

function emit() {
  for (const listener of listeners) listener(toasts)
}

export function dismissToast(id: string) {
  toasts = toasts.filter((item) => item.id !== id)
  emit()
}

function push(variant: ToastVariant, title: string, description?: string): string {
  const id = String(++idCounter)
  toasts = [...toasts, { id, title, description, variant }]
  emit()
  setTimeout(() => dismissToast(id), AUTO_DISMISS_MILLIS)
  return id
}

export const toast = {
  success: (title: string, description?: string) => push('success', title, description),
  error: (title: string, description?: string) => push('destructive', title, description),
  info: (title: string, description?: string) => push('default', title, description),
}

export function useToasts(): ToastItem[] {
  const [state, setState] = useState(toasts)
  useEffect(() => {
    listeners.add(setState)
    return () => {
      listeners.delete(setState)
    }
  }, [])
  return state
}
