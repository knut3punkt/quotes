export interface ToastMessage {
  /** Changes with every message, so a repeated message fades in again. */
  id: number
  text: string
  tone: 'success' | 'error'
}

interface ToastProps {
  toast: ToastMessage
  stageHeight: number
}

/** A brief confirmation at the top of the stage, e.g. after rating with the remote. */
export function Toast({ toast, stageHeight }: ToastProps) {
  return (
    <div className="pointer-events-none absolute inset-x-0 top-[7%] flex justify-center">
      <div
        key={toast.id}
        className={`fade-in rounded-full px-[1.2em] py-[0.4em] font-semibold text-neutral-50 shadow-lg ${
          toast.tone === 'success' ? 'bg-black/75' : 'bg-red-800/90'
        }`}
        style={{ fontSize: `${stageHeight * 0.026}px` }}
      >
        {toast.text}
      </div>
    </div>
  )
}
