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

  legacyEquipos: '/equipos',
  legacyEquipo: '/equipos/:teamId',
  legacyProyectos: '/proyectos',
  legacyProyecto: '/proyecto/:workspaceId',
  legacyTablero: '/proyecto/:workspaceId/tablero',
  legacyCopiloto: '/proyecto/:workspaceId/copiloto',
  legacyPulso: '/proyecto/:workspaceId/pulso',
} as const
