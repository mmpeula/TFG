import { apiClient } from '@/lib/api-client'
import type {
  ActivityLogResponse, AdminWorkspaceResponse, AiPlanResponse, AttachmentResponse, AuthResponse, BoardResponse,
  ChatResponse, ChatTurn, ColumnResponse, CommentResponse, DetectedDateResponse, PageResponse,
  InvitationResponse, MyInvitationResponse,
  PlanOptions, ProjectResponse, Role, ProposedTask, TaskPriority, TaskResponse, TaskType, TeamMemberResponse,
  UserResponse, WorkspaceResponse,
} from '@/types/api'

/**
 * Generar un plan puede costar varias llamadas a Groq: si el primero no
 * pasa la validacion, se le devuelve para que lo corrija hasta dos veces.
 */
const PLAN_TIMEOUT = 600_000

export const auth = {
  login: (payload: { email: string; password: string }) =>
    apiClient.post<AuthResponse>('/auth/login', payload).then((r) => r.data),
  register: (payload: { email: string; password: string; fullName: string; role: 'MIEMBRO' | 'DOCENTE' }) =>
    apiClient.post<AuthResponse>('/auth/register', payload).then((r) => r.data),
  me: () => apiClient.get<UserResponse>('/auth/me').then((r) => r.data),
}

export interface WorkspacePayload {
  name: string
  description?: string
  color?: string
  icon?: string
  startDate?: string | null
  deadline?: string | null
  /** Duracion del sprint en dias; se fija al crear el workspace y vale para todo el plan. */
  sprintDays?: number
}

/**
 * Fachada del backend sobre equipo + proyecto + tablero. Crear un workspace es
 * una sola llamada a proposito: es lo que permite empezar en segundos.
 */
export const workspaces = {
  list: () => apiClient.get<WorkspaceResponse[]>('/workspaces').then((r) => r.data),
  get: (workspaceId: number) =>
    apiClient.get<WorkspaceResponse>(`/workspaces/${workspaceId}`).then((r) => r.data),
  create: (payload: WorkspacePayload) =>
    apiClient.post<WorkspaceResponse>('/workspaces', payload).then((r) => r.data),
  update: (workspaceId: number, payload: WorkspacePayload) =>
    apiClient.put<WorkspaceResponse>(`/workspaces/${workspaceId}`, payload).then((r) => r.data),
  remove: (workspaceId: number) =>
    apiClient.delete(`/workspaces/${workspaceId}`).then(() => undefined),

  /** Pregunta en texto libre sobre el workspace. Nunca escribe en el tablero. */
  ask: (workspaceId: number, message: string, history: ChatTurn[]) =>
    apiClient
      .post<ChatResponse>(`/workspaces/${workspaceId}/ai/chat`, { message, history }, {
        // Generar un plan tarda; el resto de llamadas no deberian esperar tanto.
        timeout: 240000,
      })
      .then((r) => r.data),

  /** Convierte una descripcion escrita a mano en un plan. */
  proposeFromText: (workspaceId: number, text: string, options: PlanOptions) =>
    apiClient
      .post<AiPlanResponse>(`/workspaces/${workspaceId}/ai/plan`, { text, options }, {
        timeout: PLAN_TIMEOUT,
      })
      .then((r) => r.data),

  uploadPhoto: (workspaceId: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return apiClient
      .post<WorkspaceResponse>(`/workspaces/${workspaceId}/photo`, form, {
        headers: { 'Content-Type': undefined },
      })
      .then((r) => r.data)
  },
  removePhoto: (workspaceId: number) => apiClient.delete<void>(`/workspaces/${workspaceId}/photo`),
  /** La foto pasa por axios para que lleve el token; se entrega como URL temporal. */
  photoBlob: async (coverUrl: string) => {
    const response = await apiClient.get<Blob>(coverUrl.replace(/^\/api/, ''), { responseType: 'blob' })
    return URL.createObjectURL(response.data)
  },
}

/**
 * El equipo de un workspace. No hay equipos sueltos que crear, renombrar o
 * borrar: el workspace los crea y los borra con el (ver `workspaces`), asi que
 * aqui solo esta lo que de verdad se usa, que es quien esta dentro y con que rol.
 */
export const teams = {
  members: (teamId: number) =>
    apiClient.get<TeamMemberResponse[]>(`/teams/${teamId}/members`).then((r) => r.data),
  updateMemberRole: (teamId: number, userId: number, role: Role) =>
    apiClient.patch<TeamMemberResponse>(`/teams/${teamId}/members/${userId}`, { role }).then((r) => r.data),

  /**
   * Lo que hay que tener en cuenta de esa persona al repartir. Se guarda
   * y vale para todas las ejecuciones siguientes.
   */
  updateMemberNotes: (teamId: number, userId: number, planNotes: string) =>
    apiClient
      .patch<TeamMemberResponse>(`/teams/${teamId}/members/${userId}/notes`, { planNotes })
      .then((r) => r.data),
  removeMember: (teamId: number, userId: number) =>
    apiClient.delete(`/teams/${teamId}/members/${userId}`).then(() => undefined),

  /** Invitar no mete a nadie: deja la invitacion pendiente y manda el correo. */
  invite: (teamId: number, payload: { email: string }) =>
    apiClient.post<InvitationResponse>(`/teams/${teamId}/invitations`, payload).then((r) => r.data),
  invitations: (teamId: number) =>
    apiClient.get<InvitationResponse[]>(`/teams/${teamId}/invitations`).then((r) => r.data),
  cancelInvitation: (teamId: number, invitationId: number) =>
    apiClient.delete(`/teams/${teamId}/invitations/${invitationId}`).then(() => undefined),
}

/**
 * Soporte sobre los workspaces de toda la plataforma. Solo para rol ADMIN.
 * No hay forma de leer el tablero desde aqui: el backend tampoco la ofrece.
 */
export const adminWorkspaces = {
  list: () => apiClient.get<AdminWorkspaceResponse[]>('/admin/workspaces').then((r) => r.data),
  cambiarRol: (workspaceId: number, userId: number, role: Role) =>
    apiClient
      .put(`/admin/workspaces/${workspaceId}/members/${userId}/role`, { role })
      .then(() => undefined),
  /** Suspender o reactivar. Suspender no borra nada: se puede deshacer. */
  setEnabled: (workspaceId: number, enabled: boolean) =>
    apiClient.put(`/admin/workspaces/${workspaceId}/enabled`, { enabled }).then(() => undefined),
  remove: (workspaceId: number) =>
    apiClient.delete(`/admin/workspaces/${workspaceId}`).then(() => undefined),
}

/** Las invitaciones desde el lado de quien las recibe. */
export const invitations = {
  mine: () => apiClient.get<MyInvitationResponse[]>('/invitations/mine').then((r) => r.data),
  accept: (token: string) =>
    apiClient.post<TeamMemberResponse>(`/invitations/${token}/accept`).then((r) => r.data),
  reject: (token: string) =>
    apiClient.post(`/invitations/${token}/reject`).then(() => undefined),
}

/**
 * El proyecto que hay debajo del workspace. Crearlo, renombrarlo y borrarlo se
 * hace desde `workspaces`, que lleva de una vez el equipo, el proyecto y el
 * tablero; aqui queda lo que no cabe en esa fachada: leerlo, su historial y el
 * modulo de IA, que trabaja sobre el guion de la practica.
 */
export const projects = {
  get: (projectId: number) => apiClient.get<ProjectResponse>(`/projects/${projectId}`).then((r) => r.data),
  activity: (projectId: number) =>
    apiClient
      .get<PageResponse<ActivityLogResponse>>(`/projects/${projectId}/activity`, { params: { size: 30 } })
      .then((r) => r.data),
  /** Pide a Groq un plan a partir del guion guardado. No toca el tablero. */
  aiPlan: (projectId: number, options: PlanOptions) =>
    apiClient
      .post<AiPlanResponse>(`/projects/${projectId}/ai-plan`, options, { timeout: PLAN_TIMEOUT })
      .then((r) => r.data),
  /** Igual, pero partiendo del documento de la actividad que sube el usuario. */
  aiPlanFromDocument: (projectId: number, file: File, options: PlanOptions) => {
    const form = new FormData()
    form.append('file', file)
    // Los ajustes van como una parte JSON aparte: el equipo es una lista y no cabe
    // en parametros de la URL.
    form.append('options', new Blob([JSON.stringify(options)], { type: 'application/json' }))
    return apiClient
      .post<AiPlanResponse>(`/projects/${projectId}/ai-plan/document`, form, {
        headers: { 'Content-Type': undefined },
        timeout: PLAN_TIMEOUT,
      })
      .then((r) => r.data)
  },
  /** Lleva al tablero solo las tareas aceptadas por el usuario. */
  applyAiPlan: (projectId: number, boardId: number, tasks: ProposedTask[]) =>
    apiClient
      .post<TaskResponse[]>(`/projects/${projectId}/ai-plan/apply`, { boardId, tasks })
      .then((r) => r.data),
  /** Busca una fecha de inicio en el documento sin generar nada: no llama a Groq. */
  detectStartDate: (projectId: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return apiClient
      .post<DetectedDateResponse>(`/projects/${projectId}/ai-plan/detect-start-date`, form, {
        headers: { 'Content-Type': undefined },
      })
      .then((r) => r.data)
  },
}

export const boards = {
  byProject: (projectId: number) =>
    apiClient.get<BoardResponse[]>(`/projects/${projectId}/boards`).then((r) => r.data),
  get: (boardId: number) => apiClient.get<BoardResponse>(`/boards/${boardId}`).then((r) => r.data),
  createColumn: (boardId: number, payload: { name: string; wipLimit?: number | null }) =>
    apiClient.post<ColumnResponse>(`/boards/${boardId}/columns`, payload).then((r) => r.data),
  updateColumn: (columnId: number, payload: { name: string; wipLimit?: number | null }) =>
    apiClient.put<ColumnResponse>(`/columns/${columnId}`, payload).then((r) => r.data),
  removeColumn: (columnId: number) => apiClient.delete(`/columns/${columnId}`).then(() => undefined),
}

export interface TaskPayload {
  title: string
  description?: string
  type?: TaskType
  priority?: TaskPriority
  dueDate?: string | null
  estimatedHours?: number | null
  assigneeIds?: number[]
}

export const tasks = {
  search: (filters: { projectId?: number; boardId?: number; assigneeId?: number }) =>
    apiClient
      .get<PageResponse<TaskResponse>>('/tasks', { params: { ...filters, size: 200 } })
      .then((r) => r.data),
  get: (taskId: number) => apiClient.get<TaskResponse>(`/tasks/${taskId}`).then((r) => r.data),
  create: (columnId: number, payload: TaskPayload) =>
    apiClient.post<TaskResponse>(`/columns/${columnId}/tasks`, payload).then((r) => r.data),
  update: (taskId: number, payload: TaskPayload) =>
    apiClient.put<TaskResponse>(`/tasks/${taskId}`, payload).then((r) => r.data),
  move: (taskId: number, payload: { columnId: number; position: number }) =>
    apiClient.patch<TaskResponse>(`/tasks/${taskId}/move`, payload).then((r) => r.data),
  remove: (taskId: number) => apiClient.delete(`/tasks/${taskId}`).then(() => undefined),
}

export const comments = {
  byTask: (taskId: number) =>
    apiClient.get<PageResponse<CommentResponse>>(`/tasks/${taskId}/comments`).then((r) => r.data),
  create: (taskId: number, content: string) =>
    apiClient.post<CommentResponse>(`/tasks/${taskId}/comments`, { content }).then((r) => r.data),
  update: (commentId: number, content: string) =>
    apiClient.put<CommentResponse>(`/comments/${commentId}`, { content }).then((r) => r.data),
  remove: (commentId: number) => apiClient.delete(`/comments/${commentId}`).then(() => undefined),
}

export const attachments = {
  byTask: (taskId: number) =>
    apiClient.get<AttachmentResponse[]>(`/tasks/${taskId}/attachments`).then((r) => r.data),
  upload: (taskId: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return apiClient
      .post<AttachmentResponse>(`/tasks/${taskId}/attachments`, form, {
        headers: { 'Content-Type': undefined },
      })
      .then((r) => r.data)
  },
  remove: (attachmentId: number) => apiClient.delete(`/attachments/${attachmentId}`).then(() => undefined),
  /** La descarga pasa por axios para que lleve el token; se entrega con una URL temporal. */
  download: async (attachment: AttachmentResponse) => {
    const response = await apiClient.get<Blob>(`/attachments/${attachment.id}/download`, {
      responseType: 'blob',
    })
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = attachment.filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  },
}

export const users = {
  /**
   * Nombre siempre; contraseña solo si se quiere cambiar, y entonces hace falta
   * la actual. El backend no se la pide al administrador que restablece la de
   * otra persona, porque no la tiene.
   */
  update: (userId: number, payload: { fullName: string; currentPassword?: string; password?: string }) =>
    apiClient.put<UserResponse>(`/users/${userId}`, payload).then((r) => r.data),
  /** Listado del panel de administracion: sin filtro trae toda la plataforma. Solo ADMIN. */
  list: (search: string) =>
    apiClient
      .get<PageResponse<UserResponse>>('/users', { params: { search, size: 200 } })
      .then((r) => r.data),
  updateGlobalRole: (userId: number, role: Role) =>
    apiClient.patch<UserResponse>(`/users/${userId}/role`, { role }).then((r) => r.data),
  disable: (userId: number) => apiClient.delete<void>(`/users/${userId}`),
  enable: (userId: number) => apiClient.patch<void>(`/users/${userId}/enable`),
  uploadAvatar: (userId: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return apiClient
      .post<UserResponse>(`/users/${userId}/avatar`, form, { headers: { 'Content-Type': undefined } })
      .then((r) => r.data)
  },
  removeAvatar: (userId: number) => apiClient.delete<void>(`/users/${userId}/avatar`),
  /** La foto pasa por axios para que lleve el token; se entrega como URL temporal. */
  avatarBlob: async (avatarUrl: string) => {
    const response = await apiClient.get<Blob>(avatarUrl.replace(/^\/api/, ''), { responseType: 'blob' })
    return URL.createObjectURL(response.data)
  },
}
