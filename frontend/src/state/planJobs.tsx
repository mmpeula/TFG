import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { PlanJobsContext, useUi, type PlanGenerateRequest, type PlanJob } from './context'
import { projects as projectsApi, workspaces as workspacesApi } from '@/api/endpoints'
import { keys } from '@/hooks/useData'
import { ROUTES } from '@/app/routes'
import { toErrorMessage } from '@/lib/api-client'

/** Documento si lo hay, si no la descripcion, si tampoco el guion ya guardado. */
function sourceLabelOf(file: File | null, text: string): string {
  if (file) return `«${file.name}»`
  if (text.trim()) return 'la descripcion escrita'
  return 'el guion guardado'
}

export function PlanJobsProvider({ children }: { children: ReactNode }) {
  const [jobs, setJobs] = useState<Record<number, PlanJob>>({})
  const { pushToast } = useUi()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const start = useCallback(
    (request: PlanGenerateRequest) => {
      const { workspaceId, workspaceName, file, text, options } = request
      const sourceLabel = sourceLabelOf(file, text)
      setJobs((current) => ({ ...current, [workspaceId]: { status: 'pending', sourceLabel } }))

      const call = file
        ? projectsApi.aiPlanFromDocument(workspaceId, file, options)
        : text.trim()
          ? workspacesApi.proposeFromText(workspaceId, text, options)
          : projectsApi.aiPlan(workspaceId, options)

      const verElPlan = { label: 'Ver', onClick: () => navigate(ROUTES.generarPlanById(workspaceId)) }

      call.then(
        (plan) => {
          setJobs((current) => ({ ...current, [workspaceId]: { status: 'done', sourceLabel, plan } }))
          // Subir un documento actualiza el guion guardado del proyecto; lo demas no lo toca.
          if (file) queryClient.invalidateQueries({ queryKey: keys.project(workspaceId) })
          pushToast(`Propuesta de planificacion lista para «${workspaceName}»`, verElPlan)
        },
        (err) => {
          const message = toErrorMessage(err)
          setJobs((current) => ({ ...current, [workspaceId]: { status: 'error', sourceLabel, error: message } }))
          pushToast(`No se ha podido generar la planificacion de «${workspaceName}»`, verElPlan)
        },
      )
    },
    [pushToast, queryClient, navigate],
  )

  const clear = useCallback((workspaceId: number) => {
    setJobs((current) => {
      if (!(workspaceId in current)) return current
      const next = { ...current }
      delete next[workspaceId]
      return next
    })
  }, [])

  const value = useMemo(() => ({ jobs, start, clear }), [jobs, start, clear])

  return <PlanJobsContext.Provider value={value}>{children}</PlanJobsContext.Provider>
}
