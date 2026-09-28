/** Tipos del backend de PlanifIA (com.tfg.kanban.*.dto). */

export interface ApiError {
  timestamp: string
  status: number
  error: string
  code: string
  message: string
  path: string
  fieldErrors?: Array<{ field: string; message: string }>
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

/**
 * ADMIN es distinto de los otros tres: es un rol global de plataforma, nunca
 * de equipo. Solo aparece en UserResponse.role, jamas en el rol de un
 * TeamMember (ver lib/roles.ts).
 */
/** Rol global (cuenta) y rol dentro de un workspace. RESPONSABLE nunca es global. */
export type Role = 'RESPONSABLE' | 'MIEMBRO' | 'DOCENTE' | 'ADMIN'
export type TaskPriority = 'BAJA' | 'MEDIA' | 'ALTA' | 'CRITICA'
/** De que va la tarea. Es lo unico que colorea la interfaz (ver lib/taskTypes.ts). */
export type TaskType = 'EPICA' | 'HISTORIA' | 'TAREA' | 'BUG' | 'INCIDENCIA'
export type ProjectStatus = 'PLANIFICACION' | 'EN_CURSO' | 'ENTREGADO' | 'ARCHIVADO'
export type ActivityAction =
  | 'CREACION' | 'ACTUALIZACION' | 'BORRADO' | 'MOVIMIENTO'
  | 'ASIGNACION' | 'COMENTARIO' | 'GENERACION_IA'

export interface UserSummary { id: number; fullName: string; email: string }

export interface UserResponse {
  id: number
  email: string
  fullName: string
  role: Role
  enabled: boolean
  createdAt: string
  avatarUrl: string | null
}

export interface AuthResponse {
  token: string
  tokenType: string
  expiresInMinutes: number
  user: UserResponse
}

export interface TeamSummary { id: number; name: string }

/** Un workspace visto desde administracion: diagnostico, sin contenido del tablero. */
export interface AdminWorkspaceResponse {
  id: number
  name: string
  teamId: number
  ownerName?: string
  taskCount: number
  /** False si soporte lo tiene suspendido: el equipo no puede abrirlo, pero no se pierde nada. */
  enabled: boolean
  /** Sin nadie responsable con la cuenta activa: nadie puede administrarlo. */
  blocked: boolean
  createdAt: string
  lastActivityAt?: string
  members: AdminWorkspaceMember[]
}

export interface AdminWorkspaceMember {
  id: number
  fullName: string
  email: string
  role: Role
  enabled: boolean
}

export type InvitationStatus = 'PENDIENTE' | 'ACEPTADA' | 'RECHAZADA' | 'CANCELADA'

/** Una invitacion vista desde el equipo: quien esta pendiente de responder. */
export interface InvitationResponse {
  id: number
  invited: UserSummary
  role: Role
  status: InvitationStatus
  invitedByName?: string
  /** Si el aviso por correo llego a salir; la invitacion vale igual si no. */
  emailSent: boolean
  createdAt: string
}

/** Una invitacion vista por quien la recibe. */
export interface MyInvitationResponse {
  token: string
  teamId: number
  teamName: string
  role: Role
  invitedByName?: string
  createdAt: string
}

export interface TeamMemberResponse {
  id: number
  user: UserSummary
  role: Role
  joinedAt: string
  /** Lo que se sabe de esta persona al repartir trabajo en un plan. Se guarda y vale para todas las ejecuciones. */
  planNotes?: string | null
}

export interface ProjectResponse {
  id: number
  name: string
  description?: string
  briefing?: string
  deadline?: string
  status: ProjectStatus
  team: TeamSummary
  boardCount: number
  myRole: Role | null
  createdAt: string
  updatedAt?: string
}

export interface TaskResponse {
  id: number
  title: string
  description?: string
  position: number
  type: TaskType
  priority: TaskPriority
  dueDate?: string
  estimatedHours?: number
  aiGenerated: boolean
  columnId: number
  columnName: string
  boardId: number
  projectId: number
  assignees: UserSummary[]
  createdBy?: UserSummary
  commentCount: number
  attachmentCount: number
  createdAt: string
  updatedAt?: string
}

export interface ColumnResponse {
  id: number
  name: string
  position: number
  wipLimit?: number | null
  taskCount: number
  tasks?: TaskResponse[]
}

export interface BoardResponse {
  id: number
  name: string
  description?: string
  projectId: number
  projectName: string
  myRole: Role | null
  columns?: ColumnResponse[]
  createdAt: string
}

export interface CommentResponse {
  id: number
  content: string
  taskId: number
  author: UserSummary
  edited: boolean
  createdAt: string
  updatedAt?: string
}

export interface AttachmentResponse {
  id: number
  filename: string
  contentType?: string
  sizeBytes: number
  taskId: number
  uploadedBy: UserSummary
  downloadUrl: string
  uploadedAt: string
}

export interface ActivityLogResponse {
  id: number
  action: ActivityAction
  entityType?: string
  entityId?: number
  details?: string
  user?: UserSummary
  occurredAt: string
}

export interface ProposedTask {
  title: string
  /** Que hay que hacer, en concreto. */
  description?: string
  /** Por que existe: la parte del documento de la que sale. */
  reason?: string
  type?: TaskType
  /** Bloque de trabajo al que pertenece. */
  epic?: string
  /** Numero de sprint, empezando en 1. */
  sprint?: number
  priority?: TaskPriority
  estimatedHours?: number
  /** Ultimo dia laborable de su sprint. Lo fija el servidor, no el modelo. */
  dueDate?: string
  /** Miembros a los que se reparte. Normalmente uno; mas de uno solo si la IA decide que la colaboracion aporta algo. */
  assigneeIds: number[]
  assigneeNames: string[]
  /** Titulos de las tareas de las que depende. Ya validadas por el servidor. */
  dependsOn?: string[]
}

/** Un sprint del plan: las fechas las pone el servidor. */
export interface PlannedSprint {
  number: number
  start: string
  end: string
  /** Objetivo del sprint segun el modelo. */
  goal?: string | null
}

/** Una persona tal y como entra en una generacion de plan. */
export interface PlanTeamSlot {
  userId: number
  hoursPerWeek?: number
  preferences?: string
}

/** Ajustes de una generacion. Nada de esto se guarda. */
export interface PlanOptions {
  startDate?: string | null
  deadline?: string | null
  sprintDays?: number | null
  team?: PlanTeamSlot[]
}

/** Lo que se ha encontrado en el documento, sin generar nada todavia. */
export interface DetectedDateResponse {
  startDate: string | null
}

export interface MemberWorkload {
  memberId: number
  memberName: string
  tasks: number
  hours: number
  /** Horas de las que dispone en todo el plan, segun sus horas por semana. */
  capacityHours: number
}

export interface AiPlanResponse {
  status: string
  projectId: number
  model: string
  /** De donde salio el texto: el guion guardado o el documento subido. */
  source: string
  message: string
  /** Duracion total del plan en semanas. */
  durationWeeks?: number | null
  sprints: PlannedSprint[]
  tasks: ProposedTask[]
  /** Horas y tareas de cada persona, contadas sobre el plan. */
  workload: MemberWorkload[]
  /** Avisos del modelo y reglas que el plan no cumple tras los reintentos. */
  warnings: string[]
  /** Llamadas al modelo que ha costado: 1 si el primer plan ya era valido. */
  attempts: number
  generatedAt: string
}

/**
 * Un workspace es una asignatura, practica o trabajo. Por debajo es un proyecto
 * con su equipo y su tablero, pero la interfaz no expone esa jerarquia.
 */
export interface WorkspaceResponse {
  id: number
  name: string
  description?: string
  /** Nombre del color elegido; el cliente decide como pintarlo (ver lib/workspace.ts). */
  color?: string
  /** Iniciales o emoji para reconocerlo de un vistazo. */
  icon?: string
  teamId: number
  boardId?: number
  myRole: Role | null
  totalTasks: number
  doneTasks: number
  pendingTasks: number
  progress: number
  memberCount: number
  /** Inicio del trabajo: con la entrega define la ventana que se parte en sprints. */
  startDate?: string
  deadline?: string
  /** Duracion del sprint en dias, la misma para todo el plan; se fija al crear el workspace. */
  sprintDays?: number
  lastActivityAt?: string
  /** False si soporte lo tiene suspendido: sigue en la lista, pero no se puede abrir. */
  enabled: boolean
}

export interface ChatTurn {
  role: 'user' | 'assistant'
  content: string
}

export interface ChatResponse {
  answer: string
  model: string
  /** Que datos del workspace ha visto el modelo, para poder ensenarlo. */
  contextSummary: string
  generatedAt: string
}

export type SignalKind = 'parada' | 'saturacion' | 'fecha' | 'bloqueo'

/**
 * Las senales no vienen del backend: se calculan en el cliente a partir del
 * tablero real (ver lib/signals.ts).
 */
export interface Signal {
  id: string
  kind: SignalKind
  label: string
  age: string
  text: string
  actionLabel: string
  target: { screen: 'tablero' | 'pulso'; taskId?: number; columnId?: number; boardId?: number }
}
