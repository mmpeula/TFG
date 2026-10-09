import { AxiosError, AxiosHeaders, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { apiClient } from '@/lib/api-client'
import type {
  ActivityLogResponse, AdminWorkspaceResponse, AiPlanResponse, BoardResponse, CommentResponse, InvitationResponse,
  MyInvitationResponse, ProjectResponse, Role, TaskResponse, TeamMemberResponse, UserResponse, UserSummary,
  WorkspaceResponse,
} from '@/types/api'

/*
 * Backend falso para los tests de pantallas. Va enchufado como adaptador de axios,
 * asi que por el pasa todo lo de verdad: endpoints.ts, los interceptores del cliente
 * (token, 401) y los hooks de React Query. Guarda los datos en memoria y las
 * escrituras los cambian, de modo que al refrescar la pantalla se ve el resultado.
 *
 * Cada test empieza con los mismos datos (ver resetServer en setup.ts). Para un caso
 * concreto se sobrescribe una ruta con server.on(...) o se tocan los datos en server.db.
 */

export const HOY = '2026-10-07'

const summary = (user: UserResponse): UserSummary => ({
  id: user.id, fullName: user.fullName, email: user.email, avatarUrl: user.avatarUrl,
})

function account(id: number, fullName: string, email: string, role: Role): UserResponse {
  return { id, fullName, email, role, enabled: true, createdAt: '2026-09-01T10:00:00', avatarUrl: null }
}

function seed() {
  const users = {
    lucia: account(1, 'Lucía Fernández', 'lucia@uni.es', 'MIEMBRO'),
    pablo: account(2, 'Pablo Ortega', 'pablo@uni.es', 'MIEMBRO'),
    elena: account(3, 'Elena Navarro', 'elena@uni.es', 'MIEMBRO'),
    carmen: account(4, 'Carmen Molina', 'carmen@uni.es', 'DOCENTE'),
    admin: account(9, 'Admin Soporte', 'admin@uni.es', 'ADMIN'),
  }

  /** Rol de cada cuenta dentro del equipo 100. */
  const teamRoles: Record<number, Role> = { 1: 'RESPONSABLE', 2: 'MIEMBRO', 3: 'MIEMBRO', 4: 'DOCENTE' }

  const members: TeamMemberResponse[] = Object.entries(teamRoles).map(([id, role]) => {
    const user = Object.values(users).find((item) => item.id === Number(id))!
    return { id: Number(id), user: summary(user), role, joinedAt: '2026-09-02T10:00:00', planNotes: null }
  })

  const col = (id: number) => ({ 10: 'Por hacer', 20: 'En progreso', 30: 'En revisión', 40: 'Hecho' } as Record<number, string>)[id]
  const tarea = (id: number, title: string, columnId: number, extra: Partial<TaskResponse> = {}): TaskResponse => ({
    id, title, position: 0, type: 'TAREA', priority: 'MEDIA', aiGenerated: false,
    columnId, columnName: col(columnId), boardId: 100, projectId: 100,
    assignees: [], commentCount: 0, createdAt: '2026-10-01T10:00:00', updatedAt: '2026-10-06T10:00:00',
    ...extra,
  })

  const tasks: TaskResponse[] = [
    tarea(501, 'Leer el enunciado', 10, {
      type: 'HISTORIA', priority: 'ALTA', dueDate: '2026-10-05', assignees: [summary(users.lucia)],
      description: 'Resumir **los requisitos** de la práctica.\n\n- Entrada\n- Salida', estimatedHours: 2,
    }),
    tarea(502, 'Implementar el algoritmo voraz', 20, {
      type: 'BUG', priority: 'CRITICA', dueDate: '2026-10-20', assignees: [summary(users.pablo)],
      updatedAt: '2026-09-28T10:00:00', commentCount: 1, estimatedHours: 6, aiGenerated: true,
    }),
    tarea(503, 'Pruebas de rendimiento', 30, { type: 'EPICA', dueDate: '2026-10-25' }),
    tarea(504, 'Memoria final', 40, {
      type: 'INCIDENCIA', priority: 'BAJA', dueDate: '2026-10-03', assignees: [summary(users.elena)],
      estimatedHours: 3, actualHours: 4, completedAt: '2026-10-03T18:00:00',
    }),
  ].map((task, index) => ({ ...task, position: index }))

  const workspace: WorkspaceResponse = {
    id: 100, name: 'Algorítmica, práctica 3', description: 'Práctica de algoritmos voraces', color: 'azul', icon: 'AL',
    teamId: 100, boardId: 100, myRole: 'RESPONSABLE', totalTasks: 4, doneTasks: 1, pendingTasks: 3, progress: 25,
    memberCount: 4, startDate: '2026-09-28', deadline: '2026-11-22', sprintDays: 14,
    lastActivityAt: '2026-10-06T10:00:00', enabled: true, coverUrl: null,
  }

  const project: ProjectResponse = {
    id: 100, name: workspace.name, description: workspace.description, briefing: 'Guion de la práctica 3',
    deadline: workspace.deadline, status: 'EN_CURSO', team: { id: 100, name: workspace.name }, boardCount: 1,
    myRole: 'RESPONSABLE', createdAt: '2026-09-28T10:00:00',
  }

  const comments: CommentResponse[] = [
    { id: 900, content: 'Empiezo por el caso base', taskId: 502, author: summary(users.pablo), edited: false, createdAt: '2026-10-05T09:00:00' },
  ]

  const activity: ActivityLogResponse[] = [
    { id: 1, action: 'CREACION', entityType: 'TASK', entityId: 501, details: 'Creó «Leer el enunciado»', user: summary(users.lucia), occurredAt: '2026-10-01T10:00:00' },
    { id: 2, action: 'MOVIMIENTO', entityType: 'TASK', entityId: 504, details: 'Movió «Memoria final» a Hecho', user: summary(users.elena), occurredAt: '2026-10-03T18:00:00' },
    { id: 3, action: 'GENERACION_IA', details: 'Generó un plan con IA', user: summary(users.lucia), occurredAt: '2026-10-02T12:00:00' },
  ]

  const invitations: InvitationResponse[] = [
    { id: 70, invited: { id: 5, fullName: 'Jorge Torres', email: 'jorge@uni.es', avatarUrl: null }, role: 'MIEMBRO', status: 'PENDIENTE', invitedByName: 'Lucía Fernández', emailSent: true, createdAt: '2026-10-06T10:00:00' },
  ]

  const myInvitations: MyInvitationResponse[] = [
    { token: 'inv-1', teamId: 200, teamName: 'Bases de Datos', role: 'MIEMBRO', invitedByName: 'Elena Navarro', createdAt: '2026-10-06T10:00:00' },
  ]

  const adminWorkspaces: AdminWorkspaceResponse[] = [
    {
      id: 100, name: workspace.name, teamId: 100, ownerName: 'Lucía Fernández', taskCount: 4, enabled: true, blocked: false,
      createdAt: '2026-09-28T10:00:00', lastActivityAt: '2026-10-06T10:00:00',
      members: members.map((member) => ({ id: member.user.id, fullName: member.user.fullName, email: member.user.email, role: member.role, enabled: true })),
    },
    {
      id: 101, name: 'TFG abandonado', teamId: 101, taskCount: 0, enabled: false, blocked: true,
      createdAt: '2026-09-01T10:00:00', members: [],
    },
  ]

  return { users, teamRoles, members, tasks, workspace, project, comments, activity, invitations, myInvitations, adminWorkspaces, nextId: 1000 }
}

export type Db = ReturnType<typeof seed>

export function aiPlan(overrides: Partial<AiPlanResponse> = {}): AiPlanResponse {
  return {
    status: 'OK', projectId: 100, model: 'deepseek-chat', source: 'documento', message: 'Plan generado',
    durationWeeks: 4,
    sprints: [
      { number: 1, start: '2026-10-12', end: '2026-10-25', goal: 'Tener el algoritmo base' },
      { number: 2, start: '2026-10-26', end: '2026-11-08', goal: 'Pruebas y memoria' },
    ],
    tasks: [
      { title: 'Diseñar la estructura de datos', description: 'Elegir el montículo', reason: 'Apartado 2 del guion', type: 'HISTORIA', epic: 'Algoritmo', sprint: 1, priority: 'ALTA', estimatedHours: 4, dueDate: '2026-10-23', assigneeIds: [1], assigneeNames: ['Lucía Fernández'], labels: ['diseño'] },
      { title: 'Escribir las pruebas', type: 'TAREA', epic: 'Calidad', sprint: 2, priority: 'MEDIA', estimatedHours: 3, dueDate: '2026-11-06', assigneeIds: [2], assigneeNames: ['Pablo Ortega'], dependsOn: ['Diseñar la estructura de datos'] },
    ],
    workload: [
      { memberId: 1, memberName: 'Lucía Fernández', tasks: 1, hours: 4 },
      { memberId: 2, memberName: 'Pablo Ortega', tasks: 1, hours: 3 },
    ],
    warnings: ['El sprint 2 va justo de horas'],
    generatedAt: '2026-10-07T12:00:00',
    ...overrides,
  }
}

// ------------------------------------------------------------------ enrutado

type Method = 'get' | 'post' | 'put' | 'patch' | 'delete'

export interface Ctx {
  params: Record<string, string>
  query: Record<string, unknown>
  body: unknown
  me: UserResponse | undefined
  db: Db
}

type Handler = (ctx: Ctx) => unknown

interface Route { method: Method; regex: RegExp; keys: string[]; handler: Handler }

/** Respuesta de error con el formato de ApiError del backend. */
export class HttpError extends Error {
  status: number
  data: Record<string, unknown>
  constructor(status: number, message: string, extra: Record<string, unknown> = {}) {
    super(message)
    this.status = status
    this.data = { status, message, code: 'ERROR', error: 'Error', path: '', timestamp: '', ...extra }
  }
}

function compile(method: Method, path: string, handler: Handler): Route {
  const keys: string[] = []
  const regex = new RegExp(`^${path.replace(/:(\w+)/g, (_, key) => { keys.push(key); return '([^/]+)' })}$`)
  return { method, regex, keys, handler }
}

let routes: Route[] = []
let overrides: Route[] = []

export const server = {
  db: seed(),
  /** Peticiones recibidas, para comprobar que se llamo con lo que tocaba. */
  calls: [] as { method: string; url: string; body: unknown }[],
  /** Sustituye una ruta solo para el test actual. */
  on(method: Method, path: string, handler: Handler) {
    overrides.unshift(compile(method, path, handler))
  },
  /** Las llamadas a una ruta, por metodo y url exacta. */
  callsTo(method: string, url: string) {
    return this.calls.filter((call) => call.method === method && call.url === url)
  },
}

export function resetServer() {
  server.db = seed()
  server.calls = []
  overrides = []
}

function meFrom(config: InternalAxiosRequestConfig): UserResponse | undefined {
  const header = AxiosHeaders.from(config.headers).get('Authorization')
  const id = typeof header === 'string' ? Number(header.replace('Bearer tok-', '')) : NaN
  return Object.values(server.db.users).find((user) => user.id === id)
}

function parseBody(data: unknown): unknown {
  if (typeof data !== 'string') return data
  try {
    return JSON.parse(data)
  } catch {
    return data
  }
}

apiClient.defaults.adapter = async (config) => {
  const method = (config.method ?? 'get') as Method
  const url = (config.url ?? '').replace(/\?.*$/, '')
  const body = parseBody(config.data)
  server.calls.push({ method, url, body })

  const respond = (status: number, data: unknown): AxiosResponse => ({
    data, status, statusText: String(status), headers: {}, config, request: {},
  })

  const route = [...overrides, ...routes].find((item) => item.method === method && item.regex.test(url))
  if (!route) {
    throw new AxiosError(`Sin ruta falsa para ${method.toUpperCase()} ${url}`, 'ERR_BAD_REQUEST', config, {},
      respond(404, { status: 404, message: `Sin ruta falsa para ${method.toUpperCase()} ${url}` }))
  }

  const match = url.match(route.regex)!
  const params = Object.fromEntries(route.keys.map((key, index) => [key, match[index + 1]]))
  const me = meFrom(config)
  try {
    const data = await route.handler({ params, query: config.params ?? {}, body, me, db: server.db })
    // Por la red siempre llega una copia: si se devolviera el objeto de la base de datos, una
    // escritura posterior cambiaria tambien lo que la pantalla tiene en cache.
    return respond(200, data instanceof Blob || data === undefined ? data : structuredClone(data))
  } catch (error) {
    if (error instanceof HttpError) {
      throw new AxiosError(error.message, 'ERR_BAD_REQUEST', config, {}, respond(error.status, error.data))
    }
    throw error
  }
}

// ------------------------------------------------------------------- rutas

const page = <T,>(content: T[]) => ({
  content, page: 0, size: content.length, totalElements: content.length, totalPages: 1, first: true, last: true,
})

/** El rol que tiene en el equipo 100 quien hace la peticion. */
const roleOf = ({ me, db }: Ctx): Role | null => (me ? db.teamRoles[me.id] ?? null : null)

function workspaceFor(ctx: Ctx): WorkspaceResponse {
  const { db } = ctx
  const done = db.tasks.filter((task) => task.columnId === 40).length
  return {
    ...db.workspace,
    myRole: roleOf(ctx),
    totalTasks: db.tasks.length,
    doneTasks: done,
    pendingTasks: db.tasks.length - done,
    progress: db.tasks.length ? Math.round((done / db.tasks.length) * 100) : 0,
    memberCount: db.members.length,
  }
}

function boardFor(ctx: Ctx): BoardResponse {
  const { db } = ctx
  const columns = [10, 20, 30, 40].map((id, position) => {
    const tasks = db.tasks.filter((task) => task.columnId === id).sort((a, b) => a.position - b.position)
    return { id, name: tasks[0]?.columnName ?? { 10: 'Por hacer', 20: 'En progreso', 30: 'En revisión', 40: 'Hecho' }[id]!, position, taskCount: tasks.length, tasks }
  })
  return {
    id: 100, name: 'Tablero', projectId: 100, projectName: db.workspace.name, myRole: roleOf(ctx), columns,
    createdAt: '2026-09-28T10:00:00',
  }
}

function findTask(db: Db, id: string) {
  const task = db.tasks.find((item) => item.id === Number(id))
  if (!task) throw new HttpError(404, 'La tarea no existe')
  return task
}

function applyTask(ctx: Ctx, task: TaskResponse, payload: Record<string, unknown>): TaskResponse {
  const ids = (payload.assigneeIds as number[] | undefined) ?? task.assignees.map((item) => item.id)
  return {
    ...task,
    ...(payload as Partial<TaskResponse>),
    assignees: ids.map((id) => summary(Object.values(ctx.db.users).find((user) => user.id === id)!)),
    updatedAt: `${HOY}T12:00:00`,
  }
}

routes = [
  // Sesion
  compile('post', '/auth/login', ({ body, db }) => {
    const { email, password } = body as { email: string; password: string }
    const user = Object.values(db.users).find((item) => item.email === email)
    if (!user || password !== 'secreto123') throw new HttpError(401, 'Email o contraseña incorrectos')
    return { token: `tok-${user.id}`, tokenType: 'Bearer', expiresInMinutes: 60, user }
  }),
  compile('post', '/auth/register', ({ body, db }) => {
    const { email, fullName, role } = body as { email: string; fullName: string; role: Role }
    if (Object.values(db.users).some((user) => user.email === email)) {
      throw new HttpError(409, 'Ya hay una cuenta con ese email')
    }
    const user = account(db.nextId++, fullName, email, role)
    ;(db.users as Record<string, UserResponse>)[email] = user
    return { token: `tok-${user.id}`, tokenType: 'Bearer', expiresInMinutes: 60, user }
  }),
  compile('get', '/auth/me', ({ me }) => {
    if (!me) throw new HttpError(401, 'Sesión caducada')
    return me
  }),

  // Workspaces
  compile('get', '/workspaces', (ctx) => (roleOf(ctx) ? [workspaceFor(ctx)] : [])),
  compile('get', '/workspaces/:id', (ctx) => workspaceFor(ctx)),
  compile('post', '/workspaces', (ctx) => {
    const payload = ctx.body as Partial<WorkspaceResponse>
    return { ...workspaceFor(ctx), ...payload, id: 100 }
  }),
  compile('put', '/workspaces/:id', (ctx) => {
    Object.assign(ctx.db.workspace, ctx.body as object)
    return workspaceFor(ctx)
  }),
  compile('delete', '/workspaces/:id', () => undefined),
  compile('post', '/workspaces/:id/photo', (ctx) => {
    ctx.db.workspace.coverUrl = '/api/workspaces/100/photo'
    return workspaceFor(ctx)
  }),
  compile('delete', '/workspaces/:id/photo', (ctx) => { ctx.db.workspace.coverUrl = null }),
  compile('get', '/workspaces/:id/photo', () => new Blob(['foto'], { type: 'image/png' })),
  compile('post', '/workspaces/:id/ai/chat', ({ body }) => ({
    answer: `Empieza por **${(body as { message: string }).message}**.`,
    model: 'deepseek-chat', contextSummary: '4 tareas, 4 personas', generatedAt: `${HOY}T12:00:00`,
  })),
  compile('post', '/workspaces/:id/ai/plan', () => aiPlan({ source: 'texto' })),

  // Equipo
  compile('get', '/teams/:id/members', ({ db }) => db.members),
  compile('patch', '/teams/:id/members/:userId', ({ params, body, db }) => {
    const member = db.members.find((item) => item.user.id === Number(params.userId))!
    member.role = (body as { role: Role }).role
    db.teamRoles[member.user.id] = member.role
    return member
  }),
  compile('patch', '/teams/:id/members/:userId/notes', ({ params, body, db }) => {
    const member = db.members.find((item) => item.user.id === Number(params.userId))!
    member.planNotes = (body as { planNotes: string }).planNotes || null
    return member
  }),
  compile('delete', '/teams/:id/members/:userId', ({ params, db }) => {
    db.members = db.members.filter((item) => item.user.id !== Number(params.userId))
  }),
  compile('get', '/teams/:id/invitations', ({ db }) => db.invitations),
  compile('get', '/teams/:id/invitations/candidate', ({ query }) => {
    const email = String(query.email)
    if (email === 'carmen@uni.es') return { accountRole: 'DOCENTE', defaultRole: 'DOCENTE', allowedRoles: ['DOCENTE', 'MIEMBRO'] }
    if (email === 'nadie@uni.es') throw new HttpError(404, 'No hay ninguna cuenta con ese correo')
    return { accountRole: 'MIEMBRO', defaultRole: 'MIEMBRO', allowedRoles: ['MIEMBRO'] }
  }),
  compile('post', '/teams/:id/invitations', ({ body, db }) => {
    const { email, role } = body as { email: string; role?: Role }
    if (email === 'nadie@uni.es') throw new HttpError(404, 'No hay ninguna cuenta con ese correo')
    const invitation: InvitationResponse = {
      id: db.nextId++, invited: { id: db.nextId++, fullName: email, email, avatarUrl: null }, role: role ?? 'MIEMBRO',
      status: 'PENDIENTE', emailSent: email !== 'sincorreo@uni.es', createdAt: `${HOY}T12:00:00`,
    }
    db.invitations.push(invitation)
    return invitation
  }),
  compile('delete', '/teams/:id/invitations/:invitationId', ({ params, db }) => {
    db.invitations = db.invitations.filter((item) => item.id !== Number(params.invitationId))
  }),

  // Invitaciones recibidas
  compile('get', '/invitations/mine', ({ db }) => db.myInvitations),
  compile('post', '/invitations/:token/accept', ({ params, db, me }) => {
    db.myInvitations = db.myInvitations.filter((item) => item.token !== params.token)
    return { id: 1, user: summary(me!), role: 'MIEMBRO', joinedAt: `${HOY}T12:00:00` }
  }),
  compile('post', '/invitations/:token/reject', ({ params, db }) => {
    db.myInvitations = db.myInvitations.filter((item) => item.token !== params.token)
  }),

  // Administracion
  compile('get', '/admin/workspaces', ({ db }) => db.adminWorkspaces),
  compile('put', '/admin/workspaces/:id/members/:userId/role', () => undefined),
  compile('put', '/admin/workspaces/:id/enabled', ({ params, body, db }) => {
    const workspace = db.adminWorkspaces.find((item) => item.id === Number(params.id))!
    workspace.enabled = (body as { enabled: boolean }).enabled
  }),
  compile('delete', '/admin/workspaces/:id', ({ params, db }) => {
    db.adminWorkspaces = db.adminWorkspaces.filter((item) => item.id !== Number(params.id))
  }),
  compile('get', '/users', ({ db }) => page(Object.values(db.users))),
  compile('patch', '/users/:id/role', ({ params, body, db }) => {
    const user = Object.values(db.users).find((item) => item.id === Number(params.id))!
    user.role = (body as { role: Role }).role
    return user
  }),
  compile('delete', '/users/:id', ({ params, db }) => {
    Object.values(db.users).find((item) => item.id === Number(params.id))!.enabled = false
  }),
  compile('patch', '/users/:id/enable', ({ params, db }) => {
    Object.values(db.users).find((item) => item.id === Number(params.id))!.enabled = true
  }),
  compile('put', '/users/:id', ({ params, body, db }) => {
    const user = Object.values(db.users).find((item) => item.id === Number(params.id))!
    const payload = body as { fullName: string; currentPassword?: string; password?: string }
    if (payload.password && payload.currentPassword !== 'secreto123') {
      throw new HttpError(400, 'La contraseña actual no es correcta')
    }
    user.fullName = payload.fullName
    return user
  }),
  compile('post', '/users/:id/avatar', ({ params, db }) => {
    const user = Object.values(db.users).find((item) => item.id === Number(params.id))!
    user.avatarUrl = `/api/users/${user.id}/avatar`
    return user
  }),
  compile('delete', '/users/:id/avatar', ({ params, db }) => {
    Object.values(db.users).find((item) => item.id === Number(params.id))!.avatarUrl = null
  }),
  compile('get', '/users/:id/avatar', () => new Blob(['foto'], { type: 'image/png' })),

  // Proyecto, tablero e IA
  compile('get', '/projects/:id', (ctx) => ({ ...ctx.db.project, myRole: roleOf(ctx) })),
  compile('get', '/projects/:id/activity', ({ db }) => page(db.activity)),
  compile('get', '/projects/:id/boards', (ctx) => [boardFor(ctx)]),
  compile('get', '/boards/:id', (ctx) => boardFor(ctx)),
  compile('post', '/projects/:id/ai-plan', () => aiPlan({ source: 'guion' })),
  compile('post', '/projects/:id/ai-plan/document', () => aiPlan()),
  compile('get', '/projects/:id/ai-plan/progress', () => ({ stage: 'PLANNING' })),
  compile('post', '/projects/:id/ai-plan/detect-start-date', () => ({ startDate: '2026-10-12' })),
  compile('post', '/projects/:id/ai-plan/apply', (ctx) => {
    const { tasks } = ctx.body as { tasks: { title: string }[] }
    const created = tasks.map((item) => {
      const task = applyTask(ctx, { ...ctx.db.tasks[0], id: ctx.db.nextId++, columnId: 10, columnName: 'Por hacer', aiGenerated: true }, item)
      ctx.db.tasks.push(task)
      return task
    })
    return created
  }),

  // Tareas
  compile('post', '/columns/:id/tasks', (ctx) => {
    const columnId = Number(ctx.params.id)
    const base = ctx.db.tasks[0]
    const task = applyTask(ctx, {
      ...base, id: ctx.db.nextId++, columnId, columnName: boardFor(ctx).columns!.find((c) => c.id === columnId)!.name,
      assignees: [], commentCount: 0, description: undefined, dueDate: undefined, position: 99, completedAt: undefined,
      actualHours: undefined, estimatedHours: undefined,
    }, ctx.body as Record<string, unknown>)
    ctx.db.tasks.push(task)
    return task
  }),
  compile('put', '/tasks/:id', (ctx) => {
    const task = findTask(ctx.db, ctx.params.id)
    const updated = applyTask(ctx, task, ctx.body as Record<string, unknown>)
    Object.assign(task, updated)
    return task
  }),
  compile('patch', '/tasks/:id/move', (ctx) => {
    const task = findTask(ctx.db, ctx.params.id)
    const { columnId, position } = ctx.body as { columnId: number; position: number }
    Object.assign(task, { columnId, position, columnName: boardFor(ctx).columns!.find((c) => c.id === columnId)!.name })
    return task
  }),
  compile('delete', '/tasks/:id', (ctx) => {
    ctx.db.tasks = ctx.db.tasks.filter((task) => task.id !== Number(ctx.params.id))
  }),

  // Comentarios
  compile('get', '/tasks/:id/comments', ({ params, db }) => page(db.comments.filter((item) => item.taskId === Number(params.id)))),
  compile('post', '/tasks/:id/comments', ({ params, body, db, me }) => {
    const comment: CommentResponse = {
      id: db.nextId++, content: (body as { content: string }).content, taskId: Number(params.id),
      author: summary(me!), edited: false, createdAt: `${HOY}T12:00:00`,
    }
    db.comments.push(comment)
    return comment
  }),
  compile('put', '/comments/:id', ({ params, body, db }) => {
    const comment = db.comments.find((item) => item.id === Number(params.id))!
    Object.assign(comment, { content: (body as { content: string }).content, edited: true })
    return comment
  }),
  compile('delete', '/comments/:id', ({ params, db }) => {
    db.comments = db.comments.filter((item) => item.id !== Number(params.id))
  }),
]
