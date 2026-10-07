import { daysSince, daysUntil, formatDate } from '@/lib/format'
import type { ColumnResponse, TaskResponse } from '@/types/api'

/** Dias sin tocar una tarea a partir de los cuales se considera parada. */
const DIAS_PARADA = 4

/*
 * Las tres reglas de aviso, escritas una sola vez. Las usan los avisos de cada
 * tarjeta ({@link taskWarning}), la franja de aviso del tablero ({@link boardWarningText}),
 * los insights y el resumen del equipo, asi que no pueden decir cosas distintas.
 */

/** Dias que faltan para su fecha limite si vence hoy o ya vencio y no esta terminada; null si no. */
export function overdueDays(task: TaskResponse, columns: ColumnResponse[]): number | null {
  if (task.columnId === columns[columns.length - 1]?.id || !task.dueDate) return null
  const remaining = daysUntil(task.dueDate)
  return remaining !== null && remaining <= 0 ? remaining : null
}

/** Columnas de trabajo: ni la primera (por hacer) ni la ultima (hecho). */
function inWorkColumn(task: TaskResponse, columns: ColumnResponse[]): boolean {
  const index = columns.findIndex((column) => column.id === task.columnId)
  return index > 0 && index < columns.length - 1
}

/** Dias que lleva sin cambios si esta en una columna de trabajo y pasa del umbral; null si no. */
export function stalledDays(task: TaskResponse, columns: ColumnResponse[]): number | null {
  if (!inWorkColumn(task, columns)) return null
  const stale = daysSince(task.updatedAt ?? task.createdAt)
  return stale >= DIAS_PARADA ? stale : null
}

/** En una columna de trabajo y sin nadie asignado: no va a avanzar sola. */
export function unassignedInProgress(task: TaskResponse, columns: ColumnResponse[]): boolean {
  return inWorkColumn(task, columns) && task.assignees.length === 0
}

/**
 * La frase del aviso mas grave del tablero, para la franja de debajo de las columnas:
 * «X vencio hace 3 d y sigue en En progreso». Mismo orden de gravedad que el aviso de
 * cada tarjeta: primero lo que va tarde, luego lo parado, luego lo que esta en curso sin
 * nadie. Null si no hay ninguno.
 */
export function boardWarningText(columns: ColumnResponse[]): string | null {
  const tasks = columns.flatMap((column) => column.tasks ?? [])

  for (const task of tasks) {
    const remaining = overdueDays(task, columns)
    if (remaining === null) continue
    return remaining === 0
      ? `«${task.title}» vence hoy y sigue en ${task.columnName}.`
      : `«${task.title}» venció hace ${Math.abs(remaining)} d y sigue en ${task.columnName}.`
  }
  for (const task of tasks) {
    const stale = stalledDays(task, columns)
    if (stale !== null) return `«${task.title}» lleva ${stale} días sin movimiento en ${task.columnName}.`
  }
  const sinNadie = tasks.find((task) => unassignedInProgress(task, columns))
  return sinNadie
    ? `«${sinNadie.title}» está en ${sinNadie.columnName} sin nadie asignado, así que no va a avanzar sola.`
    : null
}

export type TaskWarningKind = 'fecha' | 'parada' | 'sin-responsable'

/**
 * Por que una tarea lleva aviso, dicho de tres formas: la etiqueta corta de la
 * tarjeta, la explicacion completa (al pasar el raton y en el panel) y que hacer
 * para quitarlo. Una tarea solo lleva un aviso, el mas grave: primero la fecha,
 * luego que este parada, luego que no tenga responsable.
 *
 * Es la unica fuente de los avisos de cada tarea: la etiqueta, el panel y el filtro
 * «Avisos» salen de aqui, asi que no pueden contar cosas distintas.
 */
export interface TaskWarning {
  kind: TaskWarningKind
  short: string
  detail: string
  action: string
}

export function taskWarning(task: TaskResponse, columns: ColumnResponse[]): TaskWarning | undefined {
  const remaining = overdueDays(task, columns)
  if (remaining === 0) {
    return {
      kind: 'fecha',
      short: 'vence hoy',
      detail: 'La fecha límite es hoy y la tarea todavía no está terminada.',
      action: 'Termínala hoy o cambia la fecha límite.',
    }
  }
  if (remaining !== null) {
    const dias = Math.abs(remaining)
    return {
      kind: 'fecha',
      short: `venció hace ${dias} d`,
      detail: `La fecha límite era el ${formatDate(task.dueDate)} (hace ${dias} ${dias === 1 ? 'día' : 'días'}) y la tarea sigue sin terminar.`,
      action: 'Termínala o, si ya no es realista, cambia la fecha límite.',
    }
  }

  const columnName = columns.find((item) => item.id === task.columnId)?.name ?? 'su columna'
  const stale = stalledDays(task, columns)
  if (stale !== null) {
    return {
      kind: 'parada',
      short: `${stale} d sin avance`,
      detail: `Lleva ${stale} días en «${columnName}» sin ningún cambio.`,
      // Comentar no la quita: el aviso mira cuando cambio la tarea, no su conversacion.
      action: 'Muévela de columna o actualízala: cualquier cambio en la tarea cuenta como avance.',
    }
  }
  if (unassignedInProgress(task, columns)) {
    return {
      kind: 'sin-responsable',
      short: 'sin responsable',
      detail: `Está en «${columnName}» pero no tiene a nadie asignado.`,
      action: 'Asígnala a alguien del equipo para que la saque adelante.',
    }
  }
  return undefined
}
