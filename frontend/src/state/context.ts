import { createContext, useContext } from 'react'
import type { AiPlanResponse, AuthResponse, PlanOptions, UserResponse } from '@/types/api'

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
 * Generar una planificacion con DeepSeek tarda: si dependiera de que Generar plan
 * siguiera montada, cambiar de pestana mientras piensa perderia el resultado.
 * Este contexto vive por encima de las rutas (junto a Auth/Ui) para que la
 * generacion siga aunque el usuario se vaya a mirar el tablero, y el resultado
 * (o el error) le espere cuando vuelva.
 */
export type PlanJobStatus = 'pending' | 'done' | 'error'

export interface PlanJob {
  status: PlanJobStatus
  /** De donde esta leyendo, para el mensaje de "generando…" aunque se remonte la pantalla. */
  sourceLabel: string
  /** Si lee un documento subido: solo entonces hay fases de procesamiento que ensenar. */
  withDocument?: boolean
  /**
   * Con que calendario se genero. Las fechas no se guardan en el espacio al generar, sino
   * al confirmar el plan, y tienen que ser estas: las del formulario pueden haber cambiado
   * despues, y la propuesta se hizo con estas.
   */
  options?: PlanOptions
  plan?: AiPlanResponse
  error?: string
}

export interface PlanGenerateRequest {
  workspaceId: number
  /** Solo para el aviso: "Propuesta lista para «Bases de Datos»". */
  workspaceName: string
  file: File | null
  text: string
  /** Calendario y equipo de esta generacion. */
  options: PlanOptions
}

interface PlanJobsValue {
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
  /** La pone `append`, no quien llama: hora a la que el mensaje entro en la conversacion. */
  createdAt: number
}

/**
 * Lo que hay escrito y elegido en la pantalla de Generar plan, por workspace.
 *
 * Vive fuera de la pantalla por lo mismo que la generacion: subir un documento
 * y luego pasarse al tablero a mirar algo no puede costar tener que volver a
 * subirlo todo. El fichero se guarda tal cual —es una
 * referencia en memoria, sigue valiendo—; lo que no sobrevive es recargar la
 * pagina, y ahi no hay nada que hacer sin volver a elegirlo.
 */
export interface PlanDraft {
  file: File | null
  text: string
  /**
   * Fecha de inicio elegida a mano, solo para cuando el workspace no tiene una
   * propia. Si el workspace ya tiene fecha de inicio, esta se ignora: siempre
   * manda la del workspace.
   */
  startDate: string | null
  /** Igual que {@link startDate}, pero la fecha de entrega. */
  deadline: string | null
  /**
   * Quien entra en este reparto y con que datos. null es «todo el equipo con sus
   * valores por defecto»; en cuanto se toca algo, la lista completa.
   */
  team: PlanTeamDraft[] | null
  /** null = todas marcadas, que es lo normal al generar. Un array, lo que el usuario dejo marcado a mano. */
  selected: number[] | null
  /** Titulos de la ultima tanda llevada al tablero, para el resumen de confirmacion. */
  appliedTitles: string[] | null
}

/** Una fila del equipo en el formulario. `included` a false la deja fuera sin perder lo escrito. */
export interface PlanTeamDraft {
  userId: number
  name: string
  preferences: string
  included: boolean
}

interface PlanDraftsValue {
  draftOf: (workspaceId: number) => PlanDraft
  patch: (workspaceId: number, cambios: Partial<PlanDraft>) => void
}

export const EMPTY_PLAN_DRAFT: PlanDraft = {
  file: null,
  text: '',
  startDate: null,
  deadline: null,
  team: null,
  selected: null,
  appliedTitles: null,
}

/** La tarea abierta en el panel, lo justo para que el asistente sepa de cual hablar y como llamarla. */
export interface AssistantActiveTask {
  id: number
  title: string
}

interface AssistantValue {
  open: boolean
  toggle: () => void
  close: () => void
  /** Conversacion de este workspace, vacia si todavia no se ha preguntado nada. */
  messagesOf: (workspaceId: number) => AssistantMessage[]
  /** La hora la pone el provider: quien llama no decide cuando "entro" el mensaje. */
  append: (workspaceId: number, message: Omit<AssistantMessage, 'createdAt'>) => void
  reset: (workspaceId: number) => void
  /**
   * La tarea que se tiene abierta en el panel, si la hay. La pone la pantalla del
   * tablero mientras el panel esta abierto, y el asistente la manda con cada
   * pregunta para no obligar a repetir de que tarea se habla.
   */
  activeTask: AssistantActiveTask | null
  setActiveTask: (task: AssistantActiveTask | null) => void
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
