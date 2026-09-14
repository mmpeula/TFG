import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { UiContext, type Toast, type ToastAction, type UiValue } from './context'

/** Estado puramente de interfaz. Lo que viene del servidor lo lleva TanStack Query. */
export function UiProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const [highlighted, setHighlighted] = useState<number | null>(null)
  const nextId = useRef(1)

  const dismissToast = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const pushToast = useCallback(
    (text: string, action?: ToastAction) => {
      const id = nextId.current++
      setToasts((current) => [...current, { id, text, action }])
      window.setTimeout(() => dismissToast(id), 4000)
    },
    [dismissToast],
  )

  const highlight = useCallback((taskId: number) => {
    setHighlighted(taskId)
    window.setTimeout(() => setHighlighted(null), 1000)
  }, [])

  const value = useMemo<UiValue>(
    () => ({ toasts, pushToast, dismissToast, highlighted, highlight }),
    [toasts, pushToast, dismissToast, highlighted, highlight],
  )

  return <UiContext.Provider value={value}>{children}</UiContext.Provider>
}

