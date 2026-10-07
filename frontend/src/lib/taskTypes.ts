import type { TaskType } from '@/types/api'

/**
 * El tipo de tarea, con su punto de color: se ve en el panel de la tarea, al crearla y
 * en la propuesta de Generar plan. La franja de la tarjeta no es el tipo sino la
 * prioridad (ver TaskCard), y el estado lleva su propia insignia (ver planStatus.ts):
 * son tres preguntas distintas y cada una tiene su sitio.
 *
 * Los tonos de tipo son apagados a proposito: tienen que poder convivir cinco en la
 * misma lista sin que la pantalla parezca un semaforo.
 */
export const TASK_TYPES = {
  EPICA: { label: 'Épica', color: '#6b52a8' },
  HISTORIA: { label: 'Historia de usuario', color: '#3466cf' },
  TAREA: { label: 'Tarea', color: '#467e59' },
  BUG: { label: 'Bug', color: '#a86a1a' },
  INCIDENCIA: { label: 'Incidencia', color: '#9b3a44' },
} as const satisfies Record<TaskType, { label: string; color: string }>

export const TASK_TYPE_IDS = Object.keys(TASK_TYPES) as TaskType[]

/** Las tareas anteriores al campo llegan sin tipo; se leen como «Tarea». */
export function typeOf(type: TaskType | undefined | null) {
  return TASK_TYPES[type ?? 'TAREA'] ?? TASK_TYPES.TAREA
}
