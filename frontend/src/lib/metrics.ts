import { daysSince, daysUntil, decimal } from '@/lib/format'
import { deriveSignals } from '@/lib/signals'
import type { BoardResponse, ProjectResponse, TaskResponse } from '@/types/api'

const SEMANAS = 10
const MS_SEMANA = 7 * 24 * 60 * 60 * 1000

interface Kpi {
  id: string
  label: string
  value: string
  delta?: string
  better?: boolean
  unit?: string
  alert?: boolean
}

interface Insight {
  id: string
  variant: 'risk' | 'block' | 'flow'
  kicker: string
  title: string
  body: string
  actions: { label: string; primary: boolean }[]
}

export interface Pulse {
  kpis: Kpi[]
  weekly: { week: string; done: number; created: number; current?: boolean }[]
  byStatus: { label: string; value: number; max: number }[]
  byPriority: { label: string; value: number; color: string }[]
  insights: Insight[]
  progress: number
  doneCount: number
  totalCount: number
  velocity: number
  cycleDays: number
  blocked: number
  pending: number
  dueSoon: number
}

/**
 * El backend no expone metricas: el pulso se calcula aqui a partir del tablero
 * real. Cada cifra que se muestra sale de estos calculos, y los insights citan
 * exactamente esas cifras — nunca un insight sin dato ni sin accion.
 */
export function derivePulse(board?: BoardResponse, project?: ProjectResponse): Pulse {
  const columns = board?.columns ?? []
  const tasks = columns.flatMap((column) => column.tasks ?? [])
  const lastColumn = columns[columns.length - 1]

  const done = tasks.filter((task) => task.columnId === lastColumn?.id)
  const pending = tasks.filter((task) => task.columnId !== lastColumn?.id)
  const total = tasks.length
  const progress = total === 0 ? 0 : Math.round((done.length / total) * 100)

  // Velocidad: tareas terminadas por semana durante las ultimas cuatro.
  const doneLastMonth = done.filter((task) => daysSince(task.updatedAt ?? task.createdAt) <= 28)
  const velocity = doneLastMonth.length / 4

  // Tiempo de ciclo: dias entre crear y terminar, promediados.
  const cycles = done
    .map((task) => cycleOf(task))
    .filter((value): value is number => value !== null)
  const cycleDays = cycles.length ? cycles.reduce((a, b) => a + b, 0) / cycles.length : 0

  const signals = deriveSignals(board)
  const blocked = signals.filter((signal) => signal.kind !== 'saturacion').length
  const dueSoon = pending.filter((task) => {
    const remaining = task.dueDate ? daysUntil(task.dueDate) : null
    return remaining !== null && remaining <= 2
  }).length

  const kpis: Kpi[] = [
    { id: 'progreso', label: 'Progreso', value: `${progress}%` },
    { id: 'velocidad', label: 'Velocidad', value: decimal(velocity), unit: 'tareas / semana' },
    { id: 'ciclo', label: 'Tiempo medio', value: formatCycle(cycleDays, cycles.length) },
    { id: 'bloqueadas', label: 'Bloqueadas', value: String(blocked), alert: blocked > 0 },
    { id: 'pendientes', label: 'Pendientes', value: String(pending.length) },
  ]

  return {
    kpis,
    weekly: weeklySeries(tasks, done),
    byStatus: columns.map((column) => ({
      label: column.name,
      value: column.tasks?.length ?? column.taskCount,
      max: Math.max(1, total),
    })),
    byPriority: priorityBreakdown(tasks),
    insights: buildInsights({
      project, columns, tasks, pending, done, velocity, cycleDays,
      cycleSamples: cycles.length, blocked, signals,
    }),
    progress,
    doneCount: done.length,
    totalCount: total,
    velocity,
    cycleDays,
    blocked,
    pending: pending.length,
    dueSoon,
  }
}

/**
 * Un ciclo de cero dias es un dato valido (tarea abierta y cerrada el mismo dia),
 * no una ausencia de datos: se distinguen los dos casos.
 */
function formatCycle(days: number, samples: number): string {
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

function weeklySeries(tasks: TaskResponse[], done: TaskResponse[]) {
  const now = Date.now()
  return Array.from({ length: SEMANAS }, (_, index) => {
    const offset = SEMANAS - 1 - index
    const start = now - (offset + 1) * MS_SEMANA
    const end = now - offset * MS_SEMANA
    const inRange = (value?: string) => {
      if (!value) return false
      const time = new Date(value).getTime()
      return time >= start && time < end
    }
    return {
      week: `S${SEMANAS - offset}`,
      created: tasks.filter((task) => inRange(task.createdAt)).length,
      done: done.filter((task) => inRange(task.updatedAt ?? task.createdAt)).length,
      current: offset === 0,
    }
  })
}

function priorityBreakdown(tasks: TaskResponse[]) {
  const config = [
    { key: 'CRITICA', label: 'Critica', color: 'var(--color-accent-700)' },
    { key: 'ALTA', label: 'Alta', color: 'var(--color-accent-600)' },
    { key: 'MEDIA', label: 'Media', color: 'var(--color-accent-400)' },
    { key: 'BAJA', label: 'Baja', color: 'var(--color-accent-2-400)' },
  ]
  return config
    .map((item) => ({
      label: item.label,
      value: tasks.filter((task) => task.priority === item.key).length,
      color: item.color,
    }))
    .filter((item) => item.value > 0)
}

interface InsightInput {
  project?: ProjectResponse
  columns: { id: number; name: string; wipLimit?: number | null; tasks?: TaskResponse[]; taskCount: number }[]
  tasks: TaskResponse[]
  pending: TaskResponse[]
  done: TaskResponse[]
  velocity: number
  cycleDays: number
  /** Numero de tareas con las que se ha podido medir el ciclo. */
  cycleSamples: number
  blocked: number
  signals: ReturnType<typeof deriveSignals>
}

/** Cada insight nombra el dato que lo produjo y trae al menos una accion. */
function buildInsights(input: InsightInput): Insight[] {
  const { project, columns, pending, velocity, cycleDays, cycleSamples, blocked, signals } = input
  const insights: Insight[] = []

  // Riesgo de entrega: pendientes frente a velocidad y fecha del proyecto.
  if (pending.length > 0) {
    const weeksNeeded = velocity > 0 ? pending.length / velocity : Infinity
    const remaining = project?.deadline ? daysUntil(project.deadline) : null
    const weeksLeft = remaining === null ? null : remaining / 7

    const body =
      velocity <= 0
        ? `Quedan ${pending.length} tareas y esta semana no se ha cerrado ninguna, asi que todavia no hay ritmo con el que estimar la fecha de entrega.`
        : remaining === null
          ? `Quedan ${pending.length} tareas a un ritmo de ${decimal(velocity)} por semana: unas ${decimal(weeksNeeded)} semanas de trabajo. El proyecto no tiene fecha de entrega fijada.`
          : weeksLeft !== null && weeksNeeded > weeksLeft
            ? `Quedan ${pending.length} tareas a ${decimal(velocity)} por semana, es decir ${decimal(weeksNeeded)} semanas, pero hasta la entrega solo hay ${remaining} dias. Al ritmo actual se llega tarde.`
            : `Quedan ${pending.length} tareas a ${decimal(velocity)} por semana y hasta la entrega hay ${remaining} dias: el ritmo actual llega a tiempo.`

    insights.push({
      id: 'riesgo',
      variant: 'risk',
      kicker: 'Riesgo de entrega',
      title:
        velocity > 0 && weeksLeft !== null && weeksNeeded > weeksLeft
          ? `El ritmo actual no llega a la fecha de entrega`
          : `Quedan ${pending.length} tareas por cerrar`,
      body,
      actions: [
        { label: 'Ver el tablero', primary: true },
        { label: 'Revisar el proyecto', primary: false },
      ],
    })
  }

  // Bloqueos: la senal mas grave que haya.
  const worst = signals.find((signal) => signal.kind === 'parada') ?? signals.find((signal) => signal.kind === 'fecha')
  if (worst) {
    insights.push({
      id: 'bloqueos',
      variant: 'block',
      kicker: 'Bloqueos',
      title: blocked === 1 ? 'Hay una tarea atascada' : `Hay ${blocked} tareas atascadas`,
      body: `${worst.text} Desatascarla es lo que mas mueve la fecha de entrega.`,
      actions: [{ label: 'Abrir la tarea', primary: true }],
    })
  }

  // Flujo: la columna mas cargada.
  const busiest = [...columns].sort(
    (a, b) => (b.tasks?.length ?? b.taskCount) - (a.tasks?.length ?? a.taskCount),
  )[0]
  if (busiest && (busiest.tasks?.length ?? busiest.taskCount) > 0) {
    const count = busiest.tasks?.length ?? busiest.taskCount
    insights.push({
      id: 'flujo',
      variant: 'flow',
      kicker: 'Flujo',
      title: `${busiest.name} concentra ${count} de las tareas`,
      body:
        cycleSamples === 0
          ? `Todavia no se ha cerrado ninguna tarea, asi que aun no hay tiempo de ciclo que medir. En cuanto se cierre la primera, aqui aparecera la media.`
          : cycleDays < 1
            ? `Las ${cycleSamples === 1 ? 'tarea cerrada se resolvio' : `${cycleSamples} tareas cerradas se resolvieron`} en menos de un dia. Vaciar ${busiest.name} es la via mas rapida para mantener ese ritmo.`
            : `El tiempo medio desde que se crea una tarea hasta que se cierra es de ${decimal(cycleDays)} dias, medido sobre ${cycleSamples} ${cycleSamples === 1 ? 'tarea cerrada' : 'tareas cerradas'}. Vaciar ${busiest.name} es la via mas rapida para bajarlo.`,
      actions: [{ label: 'Ir a la columna', primary: true }],
    })
  }

  return insights
}
