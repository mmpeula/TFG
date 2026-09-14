import { createContext, useContext } from 'react'
import type { AiPlanResponse, AuthResponse, UserResponse } from '@/types/api'

export interface ToastAction {
  label: string
  onClick: () => void
}

export interface Toast {
  id: number
  text: string
  action?: ToastAction
}

export interface UiValue {
  toasts: Toast[]
  pushToast: (text: string, action?: ToastAction) => void
  dismissToast: (id: number) => void
  highlighted: number | null
  highlight: (taskId: number) => void
}

export interface AuthValue {
  user: UserResponse | null
  isAuthenticated: boolean
  isLoading: boolean
  signIn: (auth: AuthResponse) => void
  signOut: () => void
}

/**
 * Generar una planificacion con el modelo local tarda: si dependiera de que
 * Copiloto siguiera montado, cambiar de pestana mientras piensa perderia el
 * resultado. Este contexto vive por encima de las rutas (junto a Auth/Ui) para
 * que la generacion siga aunque el usuario se vaya a mirar el tablero, y el
 * resultado (o el error) le espere cuando vuelva.
 */
export type CopilotJobStatus = 'pending' | 'done' | 'error'

export interface CopilotJob {
  status: CopilotJobStatus
  /** De donde esta leyendo, para el mensaje de "generando…" aunque se remonte la pantalla. */
  sourceLabel: string
  plan?: AiPlanResponse
  error?: string
}

export interface CopilotGenerateRequest {
  workspaceId: number
  /** Solo para el aviso: "Propuesta lista para «Bases de Datos»". */
  workspaceName: string
  file: File | null
  text: string
  people: number
  indications?: string
}

export interface CopilotJobsValue {
  /** Una entrada por workspace con una generacion en curso o ya resuelta. */
  jobs: Record<number, CopilotJob>
  start: (request: CopilotGenerateRequest) => void
  /** Descarta el resultado (o el error) de este workspace, p. ej. al cambiar el documento/descripcion. */
  clear: (workspaceId: number) => void
}

export const UiContext = createContext<UiValue | null>(null)
export const AuthContext = createContext<AuthValue | null>(null)
export const CopilotJobsContext = createContext<CopilotJobsValue | null>(null)

export function useUi(): UiValue {
  const context = useContext(UiContext)
  if (!context) throw new Error('useUi debe usarse dentro de <UiProvider>')
  return context
}

export function useAuth(): AuthValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth debe usarse dentro de <AuthProvider>')
  return context
}

export function useCopilotJobs(): CopilotJobsValue {
  const context = useContext(CopilotJobsContext)
  if (!context) throw new Error('useCopilotJobs debe usarse dentro de <CopilotJobsProvider>')
  return context
}

/** Estado de la generacion de este workspace, o undefined si no hay ninguna todavia. */
export function useCopilotJob(workspaceId: number): CopilotJob | undefined {
  const { jobs } = useCopilotJobs()
  return jobs[workspaceId]
}
