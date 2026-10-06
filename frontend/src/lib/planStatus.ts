import type { TaskState } from './sprints'

/**
 * El color y el texto del estado de una tarea en el plan, donde las tareas van por sprint y
 * no por columna: ahi es la insignia de la tarjeta (ver TaskCard) la que dice como va. La
 * franja de la izquierda es la prioridad, igual que en el tablero; el estado nunca se pinta
 * ahi, son dos preguntas distintas.
 *
 * Rojo lo que falta, ambar lo que esta en marcha, verde lo terminado: el orden de atencion es
 * tambien el orden en que conviene mirarlo.
 */
export const STATE_COLORS: Record<TaskState, { label: string; color: string }> = {
  'sin-empezar': { label: 'Por hacer', color: 'var(--color-danger-600)' },
  'en-curso': { label: 'En curso', color: 'var(--color-warning-600)' },
  terminada: { label: 'Terminada', color: 'var(--color-accent-2-600)' },
}

/**
 * Colores del estado en los paneles (la rueda «Estado de las tareas»): gris lo que no ha
 * empezado, azul lo que esta en marcha, verde lo terminado. Distintos de los de la
 * insignia de cada tarjeta del plan, que ordenan por atencion.
 */
export const PROGRESS_STATE_COLORS = {
  todo: 'var(--color-neutral-400)',
  doing: 'var(--color-accent-500)',
  done: 'var(--color-accent-2-500)',
} as const
