import { daysSince, daysUntil, formatDate, relativeTime } from '@/lib/format'
import type { BoardResponse, ColumnResponse, Signal, TaskResponse } from '@/types/api'

/** Dias sin tocar una tarea a partir de los cuales se considera parada. */
const DIAS_PARADA = 4

/*
 * Las tres reglas de aviso, escritas una sola vez. Las usan los avisos de cada
 * tarjeta ({@link taskWarning}), las senales del tablero ({@link deriveSignals})
 * y el resumen del equipo, asi que no pueden decir cosas distintas.
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
 * Las senales son objetos de primera clase de PlanifIA, pero el backend no las
 * expone: se derivan aqui del estado real del tablero. Cada una nombra el dato
 * que la produjo y propone una accion de resolucion.
 *
 * A diferencia del aviso de la tarjeta (uno por tarea, el mas grave), aqui una
 * tarea puede dar varias senales: parada y ademas sin responsable son dos cosas
 * que resolver. Reglas, en orden de gravedad:
 *   1. Fecha de entrega vencida o venciendo hoy, sin terminar ({@link overdueDays}).
 *   2. Sin tocar durante {@link DIAS_PARADA} dias en una columna de trabajo ({@link stalledDays}).
 *   3. En curso sin responsable ({@link unassignedInProgress}).
 */
export function deriveSignals(board?: BoardResponse): Signal[] {
  if (!board?.columns?.length) return []

  const columns = board.columns
  const allTasks = columns.flatMap((column) => column.tasks ?? [])
  const target = (task: TaskResponse) => ({ screen: 'tablero' as const, taskId: task.id, boardId: board.id })
  const signals: Signal[] = []

  for (const task of allTasks) {
    const remaining = overdueDays(task, columns)
    if (remaining === null) continue
    signals.push({
      id: `fecha-${task.id}`,
      kind: 'fecha',
      label: 'Fecha en riesgo',
      age: relativeTime(task.updatedAt ?? task.createdAt),
      text:
        remaining === 0
          ? `«${task.title}» vence hoy y sigue en ${task.columnName}.`
          : `«${task.title}» venció hace ${Math.abs(remaining)} d y sigue en ${task.columnName}.`,
      actionLabel: 'Revisar ahora',
      target: target(task),
    })
  }

  for (const task of allTasks) {
    const stale = stalledDays(task, columns)
    if (stale === null) continue
    signals.push({
      id: `parada-${task.id}`,
      kind: 'parada',
      label: 'Tarea parada',
      age: relativeTime(task.updatedAt ?? task.createdAt),
      text: `«${task.title}» lleva ${stale} días sin movimiento en ${task.columnName}.`,
      actionLabel: 'Ver la tarea',
      target: target(task),
    })
  }

  for (const task of allTasks) {
    if (!unassignedInProgress(task, columns)) continue
    signals.push({
      id: `bloqueo-${task.id}`,
      kind: 'bloqueo',
      label: 'Sin responsable',
      age: relativeTime(task.createdAt),
      text: `«${task.title}» está en ${task.columnName} sin nadie asignado, así que no va a avanzar sola.`,
      actionLabel: 'Asignar a alguien',
      target: target(task),
    })
  }

  return signals
}

export type TaskWarningKind = 'fecha' | 'parada' | 'sin-responsable'

/**
 * Por que una tarea lleva aviso, dicho de tres formas: la etiqueta corta de la
 * tarjeta, la explicacion completa (al pasar el raton y en el panel) y que hacer
 * para quitarlo. Una tarea solo lleva un aviso, el mas grave: primero la fecha,
 * luego que este parada, luego que no tenga responsable.
 *
 * Es la unica fuente de los avisos: la etiqueta, el panel, el filtro «Avisos» y
 * su leyenda salen de aqui, asi que no pueden contar cosas distintas.
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
      action: 'Muévela de columna, actualízala o comenta cómo va.',
    }
  }
  if (unassignedInProgress(task, columns)) {
    return {
      kind: 'sin-responsable',
      short: 'sin responsable',
      detail: `Está en «${columnName}» pero no tiene a nadie asignado.`,
      action: 'Asígnala a alguien para que alguien la saque adelante.',
    }
  }
  return undefined
}

/** Etiqueta corta del aviso, la que se pinta sobre la tarjeta. */
export function taskBadge(task: TaskResponse, columns: ColumnResponse[]): string | undefined {
  return taskWarning(task, columns)?.short
}
