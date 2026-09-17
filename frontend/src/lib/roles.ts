import type { Role } from '@/types/api'

/**
 * Permisos derivados del rol que el usuario tiene dentro del equipo dueno del
 * recurso (campo `myRole` de las respuestas del backend). Es el reflejo en la UI
 * de AccessService: aqui solo se oculta lo que ademas el servidor rechaza.
 */

/** RESPONSABLE y MIEMBRO: tareas, comentarios y adjuntos. */
export function canWrite(role: Role | null | undefined): boolean {
  return role === 'RESPONSABLE' || role === 'MIEMBRO'
}

/** Solo RESPONSABLE: equipo, proyecto, tableros y columnas. */
export function canManage(role: Role | null | undefined): boolean {
  return role === 'RESPONSABLE'
}

/** Solo ADMIN: gestiona las cuentas de la plataforma (rol global, sin equipo). */
export function isAdmin(role: Role | null | undefined): boolean {
  return role === 'ADMIN'
}

export const ROLE_LABELS: Record<Role, string> = {
  RESPONSABLE: 'Responsable',
  MIEMBRO: 'Miembro',
  OBSERVADOR: 'Observador',
  ADMIN: 'Administrador',
}

export const ROLE_DESCRIPTIONS: Record<Role, string> = {
  RESPONSABLE: 'Control total del equipo y sus proyectos',
  MIEMBRO: 'Crea y mueve tareas, comenta y adjunta ficheros',
  OBSERVADOR: 'Solo lectura',
  ADMIN: 'Gestiona las cuentas de toda la plataforma',
}

export const PRIORITY_LABELS = {
  BAJA: 'Baja', MEDIA: 'Media', ALTA: 'Alta', CRITICA: 'Critica',
} as const

/*
 * Aqui el color si dice algo, y por eso es el unico sitio del tablero donde se
 * usa rojo: solo destaca lo que urge, lo demas se queda en gris.
 */
export const PRIORITY_COLOR = {
  CRITICA: '#b42318',
  ALTA: '#c26a12',
  MEDIA: 'var(--color-neutral-400)',
  BAJA: 'var(--color-neutral-300)',
} as const
