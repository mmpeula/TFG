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
 * Generar plan siguiera montada, cambiar de pestana mientras piensa perderia el
 * resultado. Este contexto vive por encima de las rutas (junto a Auth/Ui) para
 * que la generacion siga aunque el usuario se vaya a mirar el tablero, y el
 * resultado (o el error) le espere cuando vuelva.
 */
export type PlanJobStatus = 'pending' | 'done' | 'error'

export interface PlanJob {
  status: PlanJobStatus
  /** De donde esta leyendo, para el mensaje de "generando…" aunque se remonte la pantalla. */
  sourceLabel: string
  plan?: AiPlanResponse
  error?: string
}

export interface PlanGenerateRequest {
  workspaceId: number
  /** Solo para el aviso: "Propuesta lista para «Bases de Datos»". */
  workspaceName: string
  file: File | null
  text: string
  people: number
  indications?: string
}

export interface PlanJobsValue {
  /** Una entrada por workspace con una generacion en curso o ya resuelta. */
  jobs: Record<number, PlanJob>
  start: (request: PlanGenerateRequest) => void
  /** Descarta el resultado (o el error) de este workspace, p. ej. al cambiar el documento/descripcion. */
  clear: (workspaceId: number) => void
}

/**
 * El chat de ayuda flota por encima de todo el workspace, asi que su conversacion
 * no puede vivir en la pantalla que hay debajo: cambiar de Plan a Tablero la
 * borraria a media pregunta. Se guarda aqui, por workspace, junto con si el panel
 * esta abierto, para que cambiar de seccion no interrumpa nada.
 */
export interface AssistantMessage {
  role: 'user' | 'assistant'
  content: string
  /** Lo que el modelo tenia delante al responder; se muestra en pequeno. */
  context?: string
  error?: boolean
}

/**
 * Lo que hay escrito y elegido en la pantalla de Generar plan, por workspace.
 *
 * Vive fuera de la pantalla por lo mismo que la generacion: subir un documento,
 * escribir las indicaciones y luego pasarse al tablero a mirar algo no puede
 * costar tener que volver a subirlo todo. El fichero se guarda tal cual —es una
 * referencia en memoria, sigue valiendo—; lo que no sobrevive es recargar la
 * pagina, y ahi no hay nada que hacer sin volver a elegirlo.
 */
export interface PlanDraft {
  file: File | null
  text: string
  indications: string
  /** Entre cuantas personas repartir; null es «las que haya en el equipo». */
  people: number | null
  /** null = todas marcadas, que es lo normal al generar. Un array, lo que el usuario dejo marcado a mano. */
  selected: number[] | null
  /** Titulos de la ultima tanda llevada al tablero, para el resumen de confirmacion. */
  appliedTitles: string[] | null
  scheduleApplied: boolean
}

export interface PlanDraftsValue {
  draftOf: (workspaceId: number) => PlanDraft
  patch: (workspaceId: number, cambios: Partial<PlanDraft>) => void
}

export const EMPTY_PLAN_DRAFT: PlanDraft = {
  file: null,
  text: '',
  indications: '',
  people: null,
  selected: null,
  appliedTitles: null,
  scheduleApplied: false,
}

export interface AssistantValue {
  open: boolean
  toggle: () => void
  close: () => void
  /** Conversacion de este workspace, vacia si todavia no se ha preguntado nada. */
  messagesOf: (workspaceId: number) => AssistantMessage[]
  append: (workspaceId: number, message: AssistantMessage) => void
  reset: (workspaceId: number) => void
}

export const UiContext = createContext<UiValue | null>(null)
export const AuthContext = createContext<AuthValue | null>(null)
export const PlanJobsContext = createContext<PlanJobsValue | null>(null)
export const AssistantContext = createContext<AssistantValue | null>(null)
export const PlanDraftsContext = createContext<PlanDraftsValue | null>(null)

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

export function usePlanJobs(): PlanJobsValue {
  const context = useContext(PlanJobsContext)
  if (!context) throw new Error('usePlanJobs debe usarse dentro de <PlanJobsProvider>')
  return context
}

/** Estado de la generacion de este workspace, o undefined si no hay ninguna todavia. */
export function usePlanJob(workspaceId: number): PlanJob | undefined {
  const { jobs } = usePlanJobs()
  return jobs[workspaceId]
}

export function usePlanDrafts(): PlanDraftsValue {
  const context = useContext(PlanDraftsContext)
  if (!context) throw new Error('usePlanDrafts debe usarse dentro de <PlanDraftsProvider>')
  return context
}

export function useAssistant(): AssistantValue {
  const context = useContext(AssistantContext)
  if (!context) throw new Error('useAssistant debe usarse dentro de <AssistantProvider>')
  return context
}
