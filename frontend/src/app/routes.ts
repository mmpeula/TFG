/**
 * Rutas centralizadas para no repetir literales por el codigo.
 *
 * La estructura es deliberadamente plana: inicio -> workspaces -> seccion del
 * workspace. Las rutas antiguas (/proyecto/..., /equipos) siguen respondiendo y
 * redirigen a su equivalente, para que ningun enlace guardado se rompa.
 */
export const ROUTES = {
  home: '/',
  login: '/login',
  register: '/registro',

  workspaces: '/workspaces',
  workspace: '/workspaces/:workspaceId',
  workspaceById: (id: string | number) => `/workspaces/${id}`,
  plan: '/workspaces/:workspaceId/plan',
  planById: (id: string | number) => `/workspaces/${id}/plan`,
  tablero: '/workspaces/:workspaceId/tablero',
  tableroById: (id: string | number) => `/workspaces/${id}/tablero`,
  copiloto: '/workspaces/:workspaceId/copiloto',
  copilotoById: (id: string | number) => `/workspaces/${id}/copiloto`,
  miembros: '/workspaces/:workspaceId/miembros',
  miembrosById: (id: string | number) => `/workspaces/${id}/miembros`,
  ajustes: '/workspaces/:workspaceId/ajustes',
  ajustesById: (id: string | number) => `/workspaces/${id}/ajustes`,
  pulso: '/workspaces/:workspaceId/pulso',
  pulsoById: (id: string | number) => `/workspaces/${id}/pulso`,

  configuracion: '/configuracion',
  administracion: '/administracion',
  docente: '/docente',
  /** Donde aterriza el enlace del correo de invitacion. */
  invitacion: '/invitacion/:token',
  invitacionByToken: (token: string) => `/invitacion/${token}`,

  legacyEquipos: '/equipos',
  legacyEquipo: '/equipos/:teamId',
  legacyProyectos: '/proyectos',
  legacyProyecto: '/proyecto/:workspaceId',
  legacyTablero: '/proyecto/:workspaceId/tablero',
  legacyCopiloto: '/proyecto/:workspaceId/copiloto',
  legacyPulso: '/proyecto/:workspaceId/pulso',
} as const

/** El destino inicial depende exclusivamente del rol global de la cuenta. */
export function homeForRole(role: 'ADMIN' | 'DOCENTE' | 'MIEMBRO' | 'RESPONSABLE'): string {
  if (role === 'ADMIN') return ROUTES.administracion
  if (role === 'DOCENTE') return ROUTES.docente
  return ROUTES.workspaces
}
