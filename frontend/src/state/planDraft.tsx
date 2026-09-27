import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { PlanDraftsContext, EMPTY_PLAN_DRAFT, type PlanDraft } from './context'

/**
 * Lo que hay a medio rellenar en la pantalla de Generar plan, por workspace.
 *
 * Vive aqui arriba, junto a las generaciones en curso, porque el trabajo previo
 * a darle a generar (subir el enunciado, elegir entre cuantos repartir) es
 * demasiado como para perderlo por pasarse un momento al tablero. Se guarda por
 * workspace: cada practica tiene su propio borrador.
 */
export function PlanDraftsProvider({ children }: { children: ReactNode }) {
  const [drafts, setDrafts] = useState<Record<number, PlanDraft>>({})

  const draftOf = useCallback(
    (workspaceId: number) => drafts[workspaceId] ?? EMPTY_PLAN_DRAFT,
    [drafts],
  )

  const patch = useCallback((workspaceId: number, cambios: Partial<PlanDraft>) => {
    setDrafts((current) => ({
      ...current,
      [workspaceId]: { ...(current[workspaceId] ?? EMPTY_PLAN_DRAFT), ...cambios },
    }))
  }, [])

  const value = useMemo(() => ({ draftOf, patch }), [draftOf, patch])

  return <PlanDraftsContext.Provider value={value}>{children}</PlanDraftsContext.Provider>
}
