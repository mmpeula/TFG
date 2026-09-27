import { daysSince, daysUntil, relativeTime } from '@/lib/format'
import type { BoardResponse, ColumnResponse, Signal, TaskResponse } from '@/types/api'

/** Dias sin tocar una tarea a partir de los cuales se considera parada. */
const DIAS_PARADA = 4

/**
 * Las senales son objetos de primera clase de PlanifIA, pero el backend no las
 * expone: se derivan aqui del estado real del tablero. Cada una nombra el dato
 * que la produjo y propone una accion de resolucion.
 *
 * Reglas, en orden de gravedad:
 *   1. Columna con mas tareas que su limite de trabajo en curso.
 *   2. Tarea con la fecha de entrega vencida o venciendo hoy, sin terminar.
 *   3. Tarea sin tocar durante {@link DIAS_PARADA} dias en una columna de trabajo.
 *   4. Tarea en curso sin responsable: no puede avanzar sola.
 */
export function deriveSignals(board?: BoardResponse): Signal[] {
  if (!board?.columns?.length) return []

  const columns = board.columns
  const lastColumn = columns[columns.length - 1]
  const isDone = (task: TaskResponse) => task.columnId === lastColumn.id
  const workColumns = columns.slice(1, -1)

  const signals: Signal[] = []

  // 1. Columnas saturadas
  for (const column of columns) {
    const count = column.tasks?.length ?? column.taskCount
    if (column.wipLimit != null && count > column.wipLimit) {
      signals.push({
        id: `saturacion-${column.id}`,
        kind: 'saturacion',
        label: 'Columna saturada',
        age: 'ahora',
        text: `${column.name} acumula ${count} tareas con un limite de ${column.wipLimit}. Mientras siga por encima, cada tarea nueva tarda mas en salir.`,
        actionLabel: 'Abrir la columna',
        target: { screen: 'tablero', columnId: column.id, boardId: board.id },
      })
    }
  }

  const allTasks = columns.flatMap((column) => column.tasks ?? [])

  // 2. Fechas vencidas o de hoy
  for (const task of allTasks) {
    if (isDone(task) || !task.dueDate) continue
    const remaining = daysUntil(task.dueDate)
    if (remaining === null || remaining > 0) continue
    signals.push({
      id: `fecha-${task.id}`,
      kind: 'fecha',
      label: 'Fecha en riesgo',
      age: relativeTime(task.updatedAt ?? task.createdAt),
      text:
        remaining === 0
          ? `«${task.title}» vence hoy y sigue en ${task.columnName}.`
          : `«${task.title}» vencio hace ${Math.abs(remaining)} d y sigue en ${task.columnName}.`,
      actionLabel: 'Revisar ahora',
      target: { screen: 'tablero', taskId: task.id, boardId: board.id },
    })
  }

  // 3. Tareas paradas en columnas de trabajo
  for (const column of workColumns) {
    for (const task of column.tasks ?? []) {
      const stale = daysSince(task.updatedAt ?? task.createdAt)
      if (stale < DIAS_PARADA) continue
      signals.push({
        id: `parada-${task.id}`,
        kind: 'parada',
        label: 'Tarea parada',
        age: relativeTime(task.updatedAt ?? task.createdAt),
        text: `«${task.title}» lleva ${stale} dias sin movimiento en ${column.name}.`,
        actionLabel: 'Ver la tarea',
        target: { screen: 'tablero', taskId: task.id, boardId: board.id },
      })
    }
  }

  // 4. Tareas en curso sin responsable
  for (const column of workColumns) {
    for (const task of column.tasks ?? []) {
      if (task.assignees.length > 0) continue
      signals.push({
        id: `bloqueo-${task.id}`,
        kind: 'bloqueo',
        label: 'Sin responsable',
        age: relativeTime(task.createdAt),
        text: `«${task.title}» esta en ${column.name} sin nadie asignado, asi que no va a avanzar sola.`,
        actionLabel: 'Asignar a alguien',
        target: { screen: 'tablero', taskId: task.id, boardId: board.id },
      })
    }
  }

  return signals
}

/** Micro-badge que se pinta sobre la tarjeta, si la tarea tiene algun problema. */
export function taskBadge(task: TaskResponse, columns: ColumnResponse[]): string | undefined {
  const lastColumn = columns[columns.length - 1]
  if (task.columnId === lastColumn?.id) return undefined

  const remaining = task.dueDate ? daysUntil(task.dueDate) : null
  if (remaining !== null && remaining === 0) return 'vence hoy'
  if (remaining !== null && remaining < 0) return `vencio hace ${Math.abs(remaining)} d`

  const isWorkColumn = columns.findIndex((column) => column.id === task.columnId) > 0
  if (isWorkColumn) {
    const stale = daysSince(task.updatedAt ?? task.createdAt)
    if (stale >= DIAS_PARADA) return `${stale} d sin avance`
    if (task.assignees.length === 0) return 'sin responsable'
  }
  return undefined
}
