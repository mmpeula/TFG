import { toDate } from '@/lib/format'
import type { ColumnResponse, TaskResponse } from '@/types/api'

/**
 * Duraciones de sprint que se ofrecen al crear (o editar) un workspace. Se fija
 * una vez y vale para todos sus sprints: no es una preferencia de la vista.
 */
export const SPRINT_DURATIONS: { value: string; label: string }[] = [
  { value: '7', label: '1 semana' },
  { value: '14', label: '2 semanas' },
  { value: '21', label: '3 semanas' },
]

/** Los workspaces creados antes de este campo no tienen duracion guardada. */
export const DEFAULT_SPRINT_DAYS = 14

/** En que punto del trabajo esta una tarea, mirando en que columna vive. */
export type TaskState = 'sin-empezar' | 'en-curso' | 'terminada'

export interface SprintTask {
  task: TaskResponse
  state: TaskState
}

export interface Sprint {
  /** 1, 2, 3... en el orden en que ocurren. */
  number: number
  start: Date
  end: Date
  /** El sprint en el que cae hoy. */
  current: boolean
  /** Ya ha terminado por fecha. */
  past: boolean
  tasks: SprintTask[]
  sinEmpezar: SprintTask[]
  enCurso: SprintTask[]
  terminadas: SprintTask[]
  /** Porcentaje de tareas del sprint que estan terminadas, de 0 a 100. */
  progress: number
}

interface Plan {
  sprints: Sprint[]
  /** Tareas sin fecha limite: no caen en ningun sprint. */
  sinFecha: SprintTask[]
  start: Date
  end: Date
  /** Semanas que abarca el trabajo, redondeadas hacia arriba. */
  weeks: number
}

const DIA = 86_400_000

/**
 * Fecha como «2026-11-03» a partir de las partes locales.
 *
 * No vale toISOString(): convierte la medianoche local a UTC y en Espana
 * devuelve el dia anterior, que es como la ventana salia corrida un dia.
 */
export function isoDate(date: Date): string {
  const mes = String(date.getMonth() + 1).padStart(2, '0')
  const dia = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${mes}-${dia}`
}

/** El dia, a medianoche local. Una fecha que no se entiende cuenta como hoy. */
function soloFecha(value: string | Date): Date {
  const d = typeof value === 'string' ? toDate(value) ?? new Date() : new Date(value)
  d.setHours(0, 0, 0, 0)
  return d
}

/**
 * Suma dias de calendario, no de 24 horas.
 *
 * En la ultima semana de octubre se atrasa el reloj: 21 dias de 86.400.000 ms
 * desde el 13 de octubre no caen el 3 de noviembre a las 00:00 sino el 2 a las
 * 23:00. Con eso los sprints salian corridos un dia y, peor, quedaba un hueco de
 * una hora entre el final de uno y el principio del siguiente donde las tareas de
 * ese dia desaparecian del plan. El constructor por partes respeta el cambio de
 * hora porque cuenta dias del calendario.
 */
function sumarDias(date: Date, dias: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + dias)
}

/** Dias de calendario entre dos fechas; redondea porque un dia con cambio de hora dura 23 o 25 horas. */
function diasEntre(desde: Date, hasta: Date): number {
  return Math.round((hasta.getTime() - desde.getTime()) / DIA)
}

/**
 * En que punto esta la tarea.
 *
 * Se deduce de la columna y no de un campo propio: la primera columna es lo que
 * no se ha empezado y la ultima lo terminado, se llamen como se llamen. Asi el
 * plan sigue siendo correcto si el grupo renombra o anade columnas.
 */
function stateOf(task: TaskResponse, columns: ColumnResponse[]): TaskState {
  if (columns.length === 0) return 'en-curso'
  if (task.columnId === columns[0].id) return 'sin-empezar'
  if (task.columnId === columns[columns.length - 1].id) return 'terminada'
  return 'en-curso'
}

/**
 * Reparte las tareas del tablero en sprints de la duracion pedida.
 *
 * La ventana del trabajo sale de las fechas del workspace; si faltan, se deduce
 * de las propias tareas, porque es preferible ensenar un plan aproximado a no
 * ensenar nada. Cada tarea cae en el sprint que contiene su fecha limite.
 */
export function buildPlan(
  tasks: TaskResponse[],
  columns: ColumnResponse[],
  options: { start?: string | null; end?: string | null; sprintDays: number },
): Plan {
  const conFecha = tasks.filter((task) => task.dueDate)
  const fechas = conFecha.map((task) => soloFecha(task.dueDate!).getTime()).sort((a, b) => a - b)
  const hoy = soloFecha(new Date())

  const start = options.start
    ? soloFecha(options.start)
    : fechas.length > 0
      ? soloFecha(new Date(fechas[0]))
      : hoy
  const finPorTareas = fechas.length > 0 ? soloFecha(new Date(fechas[fechas.length - 1])) : hoy
  let end = options.end ? soloFecha(options.end) : finPorTareas
  // Una entrega anterior a la ultima tarea dejaria tareas fuera del plan.
  if (end < finPorTareas) end = finPorTareas
  if (end < start) end = start

  const dias = Math.max(1, options.sprintDays)
  const total = diasEntre(start, end) + 1
  let cuantos = Math.max(1, Math.ceil(total / dias))
  // Un ultimo sprint de dos dias sueltos no es un sprint: se suma al anterior.
  const sobra = total - (cuantos - 1) * dias
  if (cuantos > 1 && sobra <= dias / 2) cuantos -= 1

  const sprints: Sprint[] = []
  for (let i = 0; i < cuantos; i++) {
    const inicio = sumarDias(start, i * dias)
    const ultimo = sumarDias(inicio, dias - 1)
    // El ultimo sprint llega siempre hasta el final de la ventana: si se recorta
    // o se alarga por el resto de la division, ninguna tarea puede quedar fuera.
    const fin = i === cuantos - 1 ? end : ultimo

    const dentro = conFecha
      .filter((task) => {
        const d = soloFecha(task.dueDate!)
        return d >= inicio && d <= fin
      })
      .map((task) => ({ task, state: stateOf(task, columns) }))
      .sort((a, b) => (a.task.dueDate! < b.task.dueDate! ? -1 : 1))

    const terminadas = dentro.filter((item) => item.state === 'terminada')
    sprints.push({
      number: i + 1,
      start: inicio,
      end: fin,
      current: hoy >= inicio && hoy <= fin,
      past: fin < hoy,
      tasks: dentro,
      sinEmpezar: dentro.filter((item) => item.state === 'sin-empezar'),
      enCurso: dentro.filter((item) => item.state === 'en-curso'),
      terminadas,
      progress: dentro.length === 0 ? 0 : Math.round((terminadas.length / dentro.length) * 100),
    })
  }

  return {
    sprints,
    sinFecha: tasks
      .filter((task) => !task.dueDate)
      .map((task) => ({ task, state: stateOf(task, columns) })),
    start,
    end,
    weeks: Math.ceil((total) / 7),
  }
}

/** «16 sept – 29 sept», sin repetir el mes cuando es el mismo. */
export function sprintRange(sprint: Sprint): string {
  const corto = new Intl.DateTimeFormat('es-ES', { day: 'numeric', month: 'short' })
  const soloDia = new Intl.DateTimeFormat('es-ES', { day: 'numeric' })
  const mismoMes = sprint.start.getMonth() === sprint.end.getMonth()
  return `${mismoMes ? soloDia.format(sprint.start) : corto.format(sprint.start)} – ${corto.format(sprint.end)}`
}
