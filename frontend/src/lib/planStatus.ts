import type { TaskState } from './sprints'

/**
 * El unico color del estado en toda la aplicacion.
 *
 * El tipo de tarea (ver taskTypes.ts) ya usa el color para decir de que va la
 * tarea, en la franja izquierda de la tarjeta; este es otro semaforo y vive en
 * su propia insignia, nunca en esa franja: son dos preguntas distintas
 * («que es» y «como va») y cada una tiene su propio color en la misma
 * tarjeta, no un sitio fisico distinto de la pantalla — no hay columnas ni
 * bandas separadas por estado.
 *
 * Rojo lo que falta, ambar lo que esta en marcha, verde lo terminado: el orden
 * de atencion es tambien el orden en que conviene mirarlo.
 */
export const STATE_COLORS: Record<TaskState, { label: string; color: string }> = {
  'sin-empezar': { label: 'Por hacer', color: 'var(--color-danger-600)' },
  'en-curso': { label: 'En curso', color: 'var(--color-warning-600)' },
  terminada: { label: 'Terminadas', color: 'var(--color-accent-2-600)' },
}
