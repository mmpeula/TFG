import { daysUntil, decimal } from '@/lib/format'
import { overdueDays, stalledDays, taskWarning, type TaskWarningKind } from '@/lib/signals'
import type { BoardResponse, ColumnResponse, ProjectResponse, TaskResponse } from '@/types/api'

/** Semanas que se ven por defecto en «Evolución temporal»; se pueden elegir otras (ver SEMANAS_POSIBLES). */
export const SEMANAS_POR_DEFECTO = 10
export const SEMANAS_POSIBLES = [4, 8, 10, 12] as const
const MS_SEMANA = 7 * 24 * 60 * 60 * 1000

/**
 * A donde lleva cada boton de un insight: al tablero, y si es sobre una tarea
 * concreta («Abrir la tarea»), con esa tarea abierta.
 */
export interface InsightAction {
  label: string
  primary: boolean
  to: 'tablero'
  taskId?: number
  /** Llegar al tablero con el filtro «Avisos» puesto. */
  onlyWarnings?: boolean
}

/** Una linea del resumen de la derecha: icono, texto y la cifra destacada. */
export interface InsightFact {
  icon: 'calendar' | 'trend'
  text: string
  value: string
  /** La cifra en rojo, porque es la que hace que no se llegue. */
  bad?: boolean
}

/** Una tarea de la lista «Tareas afectadas», con el motivo de su aviso. */
export interface InsightTask {
  taskId: number
  title: string
  kind: TaskWarningKind
  detail: string
}

/**
 * Un insight: titulo y explicacion a la izquierda, con su boton; a la derecha, un
 * resumen en cifras (riesgo de entrega) o las tareas a las que se refiere (avisos).
 * `risk` es el de entrega cuando no se llega; `ok`, el mismo cuando se va a tiempo.
 */
export interface Insight {
  id: string
  variant: 'risk' | 'ok' | 'block'
  kicker: string
  title: string
  body: string
  actions: InsightAction[]
  /** Destacar que pide hacer algo ya (solo cuando no se llega a la entrega). */
  recommended?: boolean
  facts?: InsightFact[]
  tasks?: InsightTask[]
  /** Cuantas tareas con aviso no caben en la lista. */
  moreTasks?: number
}

/** En que punto esta cada columna: la primera es «por hacer», la ultima «hecho», el resto «en curso». */
export type StatusKind = 'todo' | 'doing' | 'done'

interface Pulse {
  weekly: { week: string; done: number; created: number; current?: boolean }[]
  byStatus: { label: string; value: number; max: number; kind: StatusKind }[]
  insights: Insight[]
  /** Tareas terminadas por semana, de media en las ultimas cuatro. */
  velocity: number
  /** Cuanto ha cambiado la velocidad respecto a la de hace una semana; null si no hay con que comparar. */
  velocityDelta: number | null
  cycleDays: number
  cycleSamples: number
  /** Cuanto ha cambiado el tiempo medio respecto al de hace una semana; null si no hay con que comparar. */
  cycleDelta: number | null
  pending: number
}

/**
 * El backend no expone metricas: el pulso se calcula aqui a partir del tablero
 * real. Cada cifra que se muestra sale de estos calculos, y los insights citan
 * exactamente esas cifras — nunca un insight sin dato ni sin accion.
 */
export function derivePulse(board?: BoardResponse, project?: ProjectResponse, weeks: number = SEMANAS_POR_DEFECTO): Pulse {
  const columns = board?.columns ?? []
  const tasks = columns.flatMap((column) => column.tasks ?? [])
  const lastColumn = columns[columns.length - 1]

  const done = tasks.filter((task) => task.columnId === lastColumn?.id)
  const pending = tasks.filter((task) => task.columnId !== lastColumn?.id)
  const total = tasks.length

  // Velocidad: tareas terminadas por semana durante las ultimas cuatro. Para la variacion,
  // la misma cuenta hecha hace una semana (las cuatro semanas anteriores a esa), con la misma
  // ventana: si se midieran distinto, la flecha subiria o bajaria sin que cambiara nada.
  const finishedAt = (task: TaskResponse) => new Date(task.updatedAt ?? task.createdAt).getTime()
  const now = Date.now()
  const velocityAt = (weeksAgo: number) => {
    const end = now - weeksAgo * MS_SEMANA
    return done.filter((task) => finishedAt(task) <= end && finishedAt(task) > end - 4 * MS_SEMANA).length / 4
  }
  const velocity = velocityAt(0)
  const hayHistoria = done.some((task) => finishedAt(task) <= now - MS_SEMANA)
  const velocityDelta = hayHistoria ? velocity - velocityAt(1) : null

  // Tiempo de ciclo: dias entre crear y terminar, promediados. La variacion compara con
  // el promedio de las que ya estaban terminadas hace una semana.
  const average = (values: number[]) => values.reduce((a, b) => a + b, 0) / values.length
  const cycles = done
    .map((task) => cycleOf(task))
    .filter((value): value is number => value !== null)
  const cycleDays = cycles.length ? average(cycles) : 0
  const previousCycles = done
    .filter((task) => finishedAt(task) <= now - MS_SEMANA)
    .map((task) => cycleOf(task))
    .filter((value): value is number => value !== null)
  const cycleDelta = cycles.length > 0 && previousCycles.length > 0 && previousCycles.length < cycles.length
    ? cycleDays - average(previousCycles)
    : null

  return {
    weekly: weeklySeries(tasks, done, weeks),
    byStatus: columns.map((column, index) => ({
      label: column.name,
      value: column.tasks?.length ?? column.taskCount,
      max: Math.max(1, total),
      kind: index === 0 ? 'todo' : index === columns.length - 1 ? 'done' : 'doing',
    })),
    insights: buildInsights({
      project, columns, tasks, pending, velocity,
    }),
    velocity,
    velocityDelta,
    cycleDays,
    cycleSamples: cycles.length,
    cycleDelta,
    pending: pending.length,
  }
}

/**
 * Un ciclo de cero dias es un dato valido (tarea abierta y cerrada el mismo dia),
 * no una ausencia de datos: se distinguen los dos casos.
 */
export function formatCycle(days: number, samples: number): string {
  if (samples === 0) return '—'
  if (days < 1) return '<1 d'
  return `${decimal(days)} d`
}

function cycleOf(task: TaskResponse): number | null {
  if (!task.updatedAt) return null
  const created = new Date(task.createdAt).getTime()
  const finished = new Date(task.updatedAt).getTime()
  if (Number.isNaN(created) || Number.isNaN(finished)) return null
  return Math.max(0, (finished - created) / (24 * 60 * 60 * 1000))
}

function weeklySeries(tasks: TaskResponse[], done: TaskResponse[], weeks: number) {
  const now = Date.now()
  return Array.from({ length: weeks }, (_, index) => {
    const offset = weeks - 1 - index
    const start = now - (offset + 1) * MS_SEMANA
    const end = now - offset * MS_SEMANA
    const inRange = (value?: string) => {
      if (!value) return false
      const time = new Date(value).getTime()
      return time >= start && time < end
    }
    return {
      week: `S${weeks - offset}`,
      created: tasks.filter((task) => inRange(task.createdAt)).length,
      done: done.filter((task) => inRange(task.updatedAt ?? task.createdAt)).length,
      current: offset === 0,
    }
  })
}

interface InsightInput {
  project?: ProjectResponse
  columns: ColumnResponse[]
  tasks: TaskResponse[]
  pending: TaskResponse[]
  velocity: number
}

/** Cada insight nombra el dato que lo produjo y trae al menos una accion. */
function buildInsights(input: InsightInput): Insight[] {
  const { project, columns, tasks, pending, velocity } = input
  const insights: Insight[] = []

  // Riesgo de entrega: pendientes frente a velocidad y fecha del proyecto.
  if (pending.length > 0) {
    const weeksNeeded = velocity > 0 ? pending.length / velocity : Infinity
    const remaining = project?.deadline ? daysUntil(project.deadline) : null
    const weeksLeft = remaining === null ? null : remaining / 7
    const vencida = remaining !== null && remaining < 0
    const diasVencida = vencida ? Math.abs(remaining) : 0

    const body =
      vencida
        ? `La entrega era hace ${diasVencida} ${diasVencida === 1 ? 'día' : 'días'} y todavía quedan ${pending.length} tareas sin cerrar.`
        : velocity <= 0
          ? `Quedan ${pending.length} tareas y en el último mes no se ha cerrado ninguna, así que todavía no hay ritmo con el que estimar la fecha de entrega.`
          : remaining === null
            ? `Quedan ${pending.length} tareas a un ritmo de ${decimal(velocity)} por semana: unas ${decimal(weeksNeeded)} semanas de trabajo. El proyecto no tiene fecha de entrega fijada.`
            : weeksLeft !== null && weeksNeeded > weeksLeft
              ? `Quedan ${pending.length} tareas a ${decimal(velocity)} por semana, es decir ${decimal(weeksNeeded)} semanas, pero hasta la entrega solo hay ${remaining} ${remaining === 1 ? 'día' : 'días'}. Al ritmo actual se llega tarde.`
              : `Quedan ${pending.length} tareas a ${decimal(velocity)} por semana y hasta la entrega hay ${remaining} ${remaining === 1 ? 'día' : 'días'}: el ritmo actual llega a tiempo.`

    // Con la entrega ya pasada y tareas sin cerrar se llega tarde, haya ritmo medido o no.
    const late = vencida || (velocity > 0 && weeksLeft !== null && weeksNeeded > weeksLeft)
    const facts: InsightFact[] = [
      remaining === null
        ? { icon: 'calendar', text: 'Entrega', value: 'sin fecha fijada' }
        : vencida
          ? { icon: 'calendar', text: 'Entrega', value: `vencida hace ${diasVencida} ${diasVencida === 1 ? 'día' : 'días'}`, bad: true }
          : { icon: 'calendar', text: 'Entrega en', value: `${remaining} ${remaining === 1 ? 'día' : 'días'}`, bad: late },
      velocity > 0
        ? { icon: 'trend', text: 'Ritmo estimado:', value: `${decimal(weeksNeeded)} ${weeksNeeded === 1 ? 'semana' : 'semanas'}` }
        : { icon: 'trend', text: 'Ritmo estimado:', value: 'sin datos todavía' },
    ]
    insights.push({
      id: 'riesgo',
      variant: late ? 'risk' : 'ok',
      kicker: late ? 'Riesgo de entrega' : 'Ritmo de entrega',
      title: vencida
        ? 'La fecha de entrega ya ha pasado'
        : late ? 'El ritmo actual no llega a la fecha de entrega' : `Quedan ${pending.length} tareas por cerrar`,
      body,
      recommended: late,
      facts,
      actions: [{ label: 'Ver el tablero', primary: true, to: 'tablero' }],
    })
  }

  // Avisos: las mismas tareas que salen en el filtro «Avisos» del tablero, cada una con su
  // motivo (hasta tres, para que el numero del titulo no se quede sin explicar).
  const conAviso = tasks
    .map((task) => ({ task, warning: taskWarning(task, columns) }))
    .filter((item): item is { task: TaskResponse; warning: NonNullable<ReturnType<typeof taskWarning>> } =>
      item.warning !== undefined)
  if (conAviso.length > 0) {
    const nombradas = conAviso.slice(0, 3).map(({ task, warning }) => `«${task.title}» (${warning.short})`)
    const lista = nombradas.length === 1
      ? nombradas[0]
      : `${nombradas.slice(0, -1).join(', ')} y ${nombradas[nombradas.length - 1]}`
    const resto = conAviso.length - nombradas.length
    const MAX_LISTA = 4
    insights.push({
      id: 'avisos',
      variant: 'block',
      kicker: 'Avisos',
      title: conAviso.length === 1 ? 'Hay una tarea con aviso' : `Hay ${conAviso.length} tareas con aviso`,
      body: `${conAviso.length === 1 ? 'Es' : 'Son'} ${lista}${resto > 0 ? ` y ${resto} más` : ''}. `
        + 'Resolverlas es lo que más ayuda a llegar a la entrega.',
      tasks: conAviso.slice(0, MAX_LISTA).map(({ task, warning }) => ({
        taskId: task.id,
        title: task.title,
        kind: warning.kind,
        detail: warningDetail(task, warning.kind, columns),
      })),
      moreTasks: Math.max(0, conAviso.length - MAX_LISTA),
      actions: conAviso.length === 1
        ? [{ label: 'Abrir la tarea', primary: true, to: 'tablero', taskId: conAviso[0].task.id }]
        : [{ label: 'Verlas en el tablero', primary: true, to: 'tablero', onlyWarnings: true }],
    })
  }

  return insights
}

/** El motivo del aviso, dicho entero para la lista: «Venció hace 1 día», «5 días sin avance», «Sin responsable». */
function warningDetail(task: TaskResponse, kind: TaskWarningKind, columns: ColumnResponse[]): string {
  if (kind === 'fecha') {
    const remaining = overdueDays(task, columns) ?? 0
    const dias = Math.abs(remaining)
    return remaining === 0 ? 'Vence hoy' : `Venció hace ${dias} ${dias === 1 ? 'día' : 'días'}`
  }
  if (kind === 'parada') {
    const dias = stalledDays(task, columns) ?? 0
    return `${dias} días sin avance`
  }
  return 'Sin responsable'
}
