import { ToastClose, ToastDescription, ToastProvider, ToastRoot, ToastTitle, ToastViewport } from '@/components/ui/toast'
import { dismissToast, useToasts } from '../hooks/use-toast'

export function Toaster() {
  const toasts = useToasts()

  return (
    <ToastProvider swipeDirection="right">
      {toasts.map((item) => (
        <ToastRoot
          key={item.id}
          variant={item.variant}
          onOpenChange={(open) => {
            if (!open) dismissToast(item.id)
          }}
        >
          <div className="flex flex-col gap-0.5">
            <ToastTitle>{item.title}</ToastTitle>
            {item.description && <ToastDescription>{item.description}</ToastDescription>}
          </div>
          <ToastClose />
        </ToastRoot>
      ))}
      <ToastViewport />
    </ToastProvider>
  )
}
