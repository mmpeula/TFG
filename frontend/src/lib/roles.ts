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

/** DOCENTE: sigue un workspace sin intervenir en él. */
export function isTeacher(role: Role | null | undefined): boolean {
  return role === 'DOCENTE'
}

/**
 * MIEMBRO se muestra como «Alumno»: es el nombre que entiende quien usa la
 * aplicacion de verdad. El identificador interno se queda como esta —en la
 * base de datos, en el token y en la API— porque cambiarlo no le aporta nada a
 * nadie y solo anadiria riesgo (constraints, migraciones, tests); aqui, en el
 * unico sitio por el que pasa el texto que se ve, basta con esto.
 */
export const ROLE_LABELS: Record<Role, string> = {
  RESPONSABLE: 'Responsable',
  MIEMBRO: 'Alumno',
  DOCENTE: 'Docente',
  ADMIN: 'Administrador',
}

export const PRIORITY_LABELS = {
  BAJA: 'Baja', MEDIA: 'Media', ALTA: 'Alta', CRITICA: 'Critica',
} as const

/*
 * El color de cada prioridad es el mismo lenguaje que el resto del diff: rojo
 * es lo critico (la mitad "mala"), ambar lo que pide atencion, gris lo que
 * puede esperar. Ningun color se usa sin que signifique esto.
 */
export const PRIORITY_COLOR = {
  CRITICA: 'var(--color-danger-500)',
  ALTA: 'var(--color-warning-500)',
  MEDIA: 'var(--color-neutral-400)',
  BAJA: 'var(--color-neutral-300)',
} as const
