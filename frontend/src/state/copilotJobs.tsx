import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { CopilotJobsContext, useUi, type CopilotGenerateRequest, type CopilotJob } from './context'
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

export function CopilotJobsProvider({ children }: { children: ReactNode }) {
  const [jobs, setJobs] = useState<Record<number, CopilotJob>>({})
  const { pushToast } = useUi()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const start = useCallback(
    (request: CopilotGenerateRequest) => {
      const { workspaceId, workspaceName, file, text, people, indications } = request
      const sourceLabel = sourceLabelOf(file, text)
      setJobs((current) => ({ ...current, [workspaceId]: { status: 'pending', sourceLabel } }))

      const call = file
        ? projectsApi.aiPlanFromDocument(workspaceId, file, people, indications)
        : text.trim()
          ? workspacesApi.proposeFromText(workspaceId, text, people, indications)
          : projectsApi.aiPlan(workspaceId, people, indications)

      const verEnCopiloto = { label: 'Ver', onClick: () => navigate(ROUTES.copilotoById(workspaceId)) }

      call.then(
        (plan) => {
          setJobs((current) => ({ ...current, [workspaceId]: { status: 'done', sourceLabel, plan } }))
          // Subir un documento actualiza el guion guardado del proyecto; lo demas no lo toca.
          if (file) queryClient.invalidateQueries({ queryKey: keys.project(workspaceId) })
          pushToast(`Propuesta de planificacion lista para «${workspaceName}»`, verEnCopiloto)
        },
        (err) => {
          const message = toErrorMessage(err)
          setJobs((current) => ({ ...current, [workspaceId]: { status: 'error', sourceLabel, error: message } }))
          pushToast(`No se ha podido generar la planificacion de «${workspaceName}»`, verEnCopiloto)
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

  return <CopilotJobsContext.Provider value={value}>{children}</CopilotJobsContext.Provider>
}
