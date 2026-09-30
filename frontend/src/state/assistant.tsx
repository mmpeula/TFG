import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { AssistantContext, type AssistantActiveTask, type AssistantMessage } from './context'

/**
 * Conversaciones del chat de ayuda, una por workspace, y si el panel esta abierto.
 *
 * Vive por encima de las rutas (como PlanJobsProvider) porque el chat flota
 * sobre toda la aplicacion: si el estado colgara de la pantalla de debajo, ir del
 * plan al tablero mientras el modelo piensa perderia la respuesta.
 */
export function AssistantProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const [conversations, setConversations] = useState<Record<number, AssistantMessage[]>>({})
  const [activeTask, setActiveTask] = useState<AssistantActiveTask | null>(null)

  const toggle = useCallback(() => setOpen((value) => !value), [])
  const close = useCallback(() => setOpen(false), [])

  const messagesOf = useCallback(
    (workspaceId: number) => conversations[workspaceId] ?? [],
    [conversations],
  )

  const append = useCallback((workspaceId: number, message: AssistantMessage) => {
    setConversations((current) => ({
      ...current,
      [workspaceId]: [...(current[workspaceId] ?? []), message],
    }))
  }, [])

  const reset = useCallback((workspaceId: number) => {
    setConversations((current) => ({ ...current, [workspaceId]: [] }))
  }, [])

  const value = useMemo(
    () => ({ open, toggle, close, messagesOf, append, reset, activeTask, setActiveTask }),
    [open, toggle, close, messagesOf, append, reset, activeTask],
  )

  return <AssistantContext.Provider value={value}>{children}</AssistantContext.Provider>
}
