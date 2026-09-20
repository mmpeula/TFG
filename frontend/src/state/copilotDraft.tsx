import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { CopilotDraftsContext, EMPTY_COPILOT_DRAFT, type CopilotDraft } from './context'

/**
 * Lo que hay a medio rellenar en la pantalla de Copiloto, por workspace.
 *
 * Vive aqui arriba, junto a las generaciones en curso, porque el trabajo previo
 * a darle a generar (subir el enunciado, escribir las indicaciones, elegir entre
 * cuantos repartir) es demasiado como para perderlo por pasarse un momento al
 * tablero. Se guarda por workspace: cada practica tiene su propio borrador.
 */
export function CopilotDraftsProvider({ children }: { children: ReactNode }) {
  const [drafts, setDrafts] = useState<Record<number, CopilotDraft>>({})

  const draftOf = useCallback(
    (workspaceId: number) => drafts[workspaceId] ?? EMPTY_COPILOT_DRAFT,
    [drafts],
  )

  const patch = useCallback((workspaceId: number, cambios: Partial<CopilotDraft>) => {
    setDrafts((current) => ({
      ...current,
      [workspaceId]: { ...(current[workspaceId] ?? EMPTY_COPILOT_DRAFT), ...cambios },
    }))
  }, [])

  const value = useMemo(() => ({ draftOf, patch }), [draftOf, patch])

  return <CopilotDraftsContext.Provider value={value}>{children}</CopilotDraftsContext.Provider>
}
