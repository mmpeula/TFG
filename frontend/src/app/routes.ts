/**
 * Rutas centralizadas para no repetir literales por el codigo.
 *
 * La estructura es deliberadamente plana: inicio -> workspaces -> seccion del
 * workspace.
 */
export const ROUTES = {
  home: '/',
  login: '/login',
  register: '/registro',

  workspaces: '/workspaces',
  workspace: '/workspaces/:workspaceId',
  plan: '/workspaces/:workspaceId/plan',
  planById: (id: string | number) => `/workspaces/${id}/plan`,
  tablero: '/workspaces/:workspaceId/tablero',
  tableroById: (id: string | number) => `/workspaces/${id}/tablero`,
  generarPlan: '/workspaces/:workspaceId/generar-plan',
  generarPlanById: (id: string | number) => `/workspaces/${id}/generar-plan`,
  miembros: '/workspaces/:workspaceId/miembros',
  miembrosById: (id: string | number) => `/workspaces/${id}/miembros`,
  ajustes: '/workspaces/:workspaceId/ajustes',
  ajustesById: (id: string | number) => `/workspaces/${id}/ajustes`,
  pulso: '/workspaces/:workspaceId/pulso',
  pulsoById: (id: string | number) => `/workspaces/${id}/pulso`,

  configuracion: '/configuracion',
  administracion: '/administracion',
  docente: '/docente',
  /** Donde aterriza el enlace del correo de invitacion (lo compone el backend, ver InvitationService). */
  invitacion: '/invitacion/:token',
} as const

/**
 * El destino inicial depende del rol global de la cuenta. Un docente aterriza en
 * lo mismo que un alumno: sus espacios (los que crea, donde es responsable) y, si
 * le han anadido a alguno como docente, la tarjeta del panel docente.
 */
export function homeForRole(role: 'ADMIN' | 'DOCENTE' | 'MIEMBRO' | 'RESPONSABLE'): string {
  return role === 'ADMIN' ? ROUTES.administracion : ROUTES.workspaces
}
