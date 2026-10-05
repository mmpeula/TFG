import type { TaskType } from '@/types/api'

/**
 * El tipo de tarea es el color de la propia tarjeta: la franja del borde y el
 * punto junto a su nombre. Es lo unico que se colorea ahi.
 *
 * El estado tiene su propio semaforo (ver planStatus.ts), pero vive en la
 * cabecera de cada banda del plan, nunca en la tarjeta: son dos preguntas
 * distintas y cada una tiene su sitio. Si el estado tambien coloreara la
 * tarjeta, una terminada y una sin empezar del mismo tipo competirian por el
 * mismo canal y no se entenderia ninguna de las dos.
 *
 * Los tonos de tipo son apagados a proposito: tienen que poder convivir cinco
 * en la misma banda sin que la pantalla parezca un semaforo.
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
