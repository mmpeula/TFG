import { apiClient } from '@/lib/api-client'
import type {
  ActivityLogResponse, AiPlanResponse, AttachmentResponse, AuthResponse, BoardResponse,
  ChatResponse, ChatTurn, ColumnResponse, CommentResponse, PageResponse,
  ProjectResponse, ProjectStatus, Role, ProposedTask, TaskPriority, TaskResponse, TaskType, TeamMemberResponse,
  TeamResponse, UserResponse, WorkspaceResponse,
} from '@/types/api'

export const auth = {
  login: (payload: { email: string; password: string }) =>
    apiClient.post<AuthResponse>('/auth/login', payload).then((r) => r.data),
  register: (payload: { email: string; password: string; fullName: string }) =>
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
      .post<ChatResponse>(`/workspaces/${workspaceId}/copilot/chat`, { message, history }, {
        // El modelo local tarda; el resto de llamadas no deberian esperar tanto.
        timeout: 240000,
      })
      .then((r) => r.data),

  /** Convierte una descripcion escrita a mano en una propuesta de tareas. */
  proposeFromText: (workspaceId: number, text: string, people?: number, indications?: string) =>
    apiClient
      .post<AiPlanResponse>(`/workspaces/${workspaceId}/copilot/tasks`, { text, indications }, {
        params: { people },
        timeout: 240000,
      })
      .then((r) => r.data),
}

export const teams = {
  list: () =>
    apiClient.get<PageResponse<TeamResponse>>('/teams', { params: { size: 100 } }).then((r) => r.data),
  get: (teamId: number) => apiClient.get<TeamResponse>(`/teams/${teamId}`).then((r) => r.data),
  create: (payload: { name: string; description?: string }) =>
    apiClient.post<TeamResponse>('/teams', payload).then((r) => r.data),
  update: (teamId: number, payload: { name: string; description?: string }) =>
    apiClient.put<TeamResponse>(`/teams/${teamId}`, payload).then((r) => r.data),
  remove: (teamId: number) => apiClient.delete(`/teams/${teamId}`).then(() => undefined),
  members: (teamId: number) =>
    apiClient.get<TeamMemberResponse[]>(`/teams/${teamId}/members`).then((r) => r.data),
  addMember: (teamId: number, payload: { email: string; role: Role }) =>
    apiClient.post<TeamMemberResponse>(`/teams/${teamId}/members`, payload).then((r) => r.data),
  updateMemberRole: (teamId: number, userId: number, role: Role) =>
    apiClient.patch<TeamMemberResponse>(`/teams/${teamId}/members/${userId}`, { role }).then((r) => r.data),
  removeMember: (teamId: number, userId: number) =>
    apiClient.delete(`/teams/${teamId}/members/${userId}`).then(() => undefined),
}

export const projects = {
  list: (filters: { teamId?: number; status?: ProjectStatus } = {}) =>
    apiClient
      .get<PageResponse<ProjectResponse>>('/projects', { params: { ...filters, size: 100 } })
      .then((r) => r.data),
  get: (projectId: number) => apiClient.get<ProjectResponse>(`/projects/${projectId}`).then((r) => r.data),
  create: (payload: {
    teamId: number; name: string; description?: string; briefing?: string; deadline?: string | null
  }) => apiClient.post<ProjectResponse>('/projects', payload).then((r) => r.data),
  update: (projectId: number, payload: {
    name: string; description?: string; briefing?: string; deadline?: string | null; status?: ProjectStatus
  }) => apiClient.put<ProjectResponse>(`/projects/${projectId}`, payload).then((r) => r.data),
  remove: (projectId: number) => apiClient.delete(`/projects/${projectId}`).then(() => undefined),
  activity: (projectId: number) =>
    apiClient
      .get<PageResponse<ActivityLogResponse>>(`/projects/${projectId}/activity`, { params: { size: 30 } })
      .then((r) => r.data),
  /** Pide al modelo local una propuesta a partir del guion guardado. No toca el tablero. */
  aiPlan: (projectId: number, people?: number, indications?: string) =>
    apiClient
      .post<AiPlanResponse>(`/projects/${projectId}/ai-plan`, null, {
        params: { people, indications },
        // Como las otras dos vias de generar: el modelo local puede tardar mas de los 20 s por defecto.
        timeout: 240000,
      })
      .then((r) => r.data),
  /** Igual, pero partiendo del documento de la actividad que sube el usuario. */
  aiPlanFromDocument: (projectId: number, file: File, people?: number, indications?: string) => {
    const form = new FormData()
    form.append('file', file)
    return apiClient
      .post<AiPlanResponse>(`/projects/${projectId}/ai-plan/document`, form, {
        params: { people, indications },
        headers: { 'Content-Type': undefined },
        // Leer el documento y generar el plan puede tardar mas que el resto de llamadas.
        timeout: 240000,
      })
      .then((r) => r.data)
  },
  /** Lleva al tablero solo las tareas aceptadas por el usuario. */
  applyAiPlan: (projectId: number, boardId: number, tasks: ProposedTask[]) =>
    apiClient
      .post<TaskResponse[]>(`/projects/${projectId}/ai-plan/apply`, { boardId, tasks })
      .then((r) => r.data),
}

export const boards = {
  byProject: (projectId: number) =>
    apiClient.get<BoardResponse[]>(`/projects/${projectId}/boards`).then((r) => r.data),
  get: (boardId: number) => apiClient.get<BoardResponse>(`/boards/${boardId}`).then((r) => r.data),
  create: (projectId: number, payload: { name: string; description?: string }) =>
    apiClient.post<BoardResponse>(`/projects/${projectId}/boards`, payload).then((r) => r.data),
  remove: (boardId: number) => apiClient.delete(`/boards/${boardId}`).then(() => undefined),
  createColumn: (boardId: number, payload: { name: string; wipLimit?: number | null }) =>
    apiClient.post<ColumnResponse>(`/boards/${boardId}/columns`, payload).then((r) => r.data),
  updateColumn: (columnId: number, payload: { name: string; wipLimit?: number | null }) =>
    apiClient.put<ColumnResponse>(`/columns/${columnId}`, payload).then((r) => r.data),
  moveColumn: (columnId: number, position: number) =>
    apiClient.patch<ColumnResponse[]>(`/columns/${columnId}/move`, { position }).then((r) => r.data),
  removeColumn: (columnId: number) => apiClient.delete(`/columns/${columnId}`).then(() => undefined),
}

export interface TaskPayload {
  title: string
  description?: string
  type?: TaskType
  priority?: TaskPriority
  dueDate?: string | null
  estimatedHours?: number | null
  assigneeId?: number | null
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
  search: (search: string) =>
    apiClient
      .get<PageResponse<UserResponse>>('/users', { params: { search, size: 10 } })
      .then((r) => r.data),
  /** Nombre siempre; contrasena solo si se quiere cambiar. */
  update: (userId: number, payload: { fullName: string; password?: string }) =>
    apiClient.put<UserResponse>(`/users/${userId}`, payload).then((r) => r.data),
  /** Listado del panel de administracion: sin filtro trae toda la plataforma. */
  list: (search: string) =>
    apiClient
      .get<PageResponse<UserResponse>>('/users', { params: { search, size: 200 } })
      .then((r) => r.data),
  updateGlobalRole: (userId: number, role: Role) =>
    apiClient.patch<UserResponse>(`/users/${userId}/role`, { role }).then((r) => r.data),
  disable: (userId: number) => apiClient.delete<void>(`/users/${userId}`),
  enable: (userId: number) => apiClient.patch<void>(`/users/${userId}/enable`),
}
