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
  seguimiento: '/workspaces/:workspaceId/seguimiento',
  seguimientoById: (id: string | number) => `/workspaces/${id}/seguimiento`,

  configuracion: '/configuracion',
  administracion: '/administracion',
  docente: '/docente',
  /** Donde aterriza el enlace del correo de invitacion (lo compone el backend, ver InvitationService). */
  invitacion: '/invitacion/:token',
} as const

/**
 * El destino inicial depende del rol global de la cuenta: el administrador entra
 * en su panel, el docente en el panel docente (lo que sigue, que es lo suyo) y el
 * alumno en sus espacios de trabajo. El docente tiene sus propios espacios a una
 * pestana, en «Mis espacios de trabajo» (ver HomeTabs).
 */
export function homeForRole(role: 'ADMIN' | 'DOCENTE' | 'MIEMBRO' | 'RESPONSABLE'): string {
  if (role === 'ADMIN') return ROUTES.administracion
  if (role === 'DOCENTE') return ROUTES.docente
  return ROUTES.workspaces
}
