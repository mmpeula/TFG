import { describe, expect, it } from 'vitest'
import { columns, task, user } from '@/test/factories'
import type { BoardResponse, ProjectResponse, TaskResponse } from '@/types/api'
import { deriveTracking, formatCycle } from './metrics'

// «Hoy» es el 7 de octubre de 2026 a mediodia (ver test/setup.ts).

const board = (tasks: TaskResponse[]): BoardResponse => ({
  id: 1, name: 'Tablero', projectId: 1, projectName: 'P', myRole: 'RESPONSABLE', columns: columns(tasks), createdAt: '2026-09-01T10:00:00',
})
const project = (deadline?: string): ProjectResponse => ({
  id: 1, name: 'P', status: 'EN_CURSO', team: { id: 1, name: 'P' }, boardCount: 1, myRole: 'RESPONSABLE',
  createdAt: '2026-09-01T10:00:00', deadline,
})
const ana = user(1, 'Ana')

/** Una tarea terminada hace `dias` dias, que se creo `ciclo` dias antes de terminarse. */
function hecha(id: number, dias: number, ciclo = 2): TaskResponse {
  const fin = new Date(Date.now() - dias * 86_400_000)
  const inicio = new Date(fin.getTime() - ciclo * 86_400_000)
  return task({ id, columnId: 40, completedAt: fin.toISOString(), createdAt: inicio.toISOString(), assignees: [ana] })
}
const pendiente = (id: number, extra: Partial<TaskResponse> = {}) =>
  task({ id, columnId: 20, assignees: [ana], updatedAt: '2026-10-07T09:00:00', ...extra })

describe('deriveTracking: cifras', () => {
  it('sin tablero todo a cero y sin insights', () => {
    const tracking = deriveTracking(undefined, undefined)
    expect(tracking).toMatchObject({ velocity: 0, pending: 0, cycleSamples: 0, velocityDelta: null, cycleDelta: null, insights: [] })
    expect(tracking.weekly).toHaveLength(10)
  })

  it('velocidad: terminadas por semana en las ultimas cuatro, y su variacion', () => {
    const tracking = deriveTracking(board([hecha(1, 1), hecha(2, 3), hecha(3, 10), hecha(4, 40)]), project())
    expect(tracking.velocity).toBe(0.75)
    // Hace una semana, en sus cuatro semanas anteriores, solo cabia la de hace 10 dias.
    expect(tracking.velocityDelta).toBe(0.5)
  })

  it('tiempo de ciclo medio y su variacion frente a hace una semana', () => {
    const tracking = deriveTracking(board([hecha(1, 1, 2), hecha(2, 10, 4)]), project())
    expect(tracking.cycleDays).toBe(3)
    expect(tracking.cycleSamples).toBe(2)
    expect(tracking.cycleDelta).toBe(-1)
  })

  it('cuenta las tareas por columna y las pendientes', () => {
    const tracking = deriveTracking(board([task({ id: 1 }), pendiente(2), hecha(3, 1)]), project())
    expect(tracking.byStatus.map((row) => [row.label, row.value, row.kind])).toEqual([
      ['Por hacer', 1, 'todo'], ['En progreso', 1, 'doing'], ['En revisión', 0, 'doing'], ['Hecho', 1, 'done'],
    ])
    expect(tracking.pending).toBe(2)
  })

  it('la serie semanal cuenta creadas y terminadas en cada semana', () => {
    const tracking = deriveTracking(board([hecha(1, 1), task({ id: 2, createdAt: '2026-10-06T10:00:00' })]), project(), 4)
    expect(tracking.weekly.map((week) => week.week)).toEqual(['S1', 'S2', 'S3', 'S4'])
    // La terminada hace un dia se creo dos dias antes: tambien cuenta como creada esta semana.
    expect(tracking.weekly[3]).toMatchObject({ done: 1, created: 2, current: true })
    expect(tracking.weekly[0]).toMatchObject({ done: 0, created: 0, current: false })
  })

  it('sin fecha de fin ni ultimo cambio, la tarea no cuenta para el ciclo', () => {
    const rara = task({ id: 1, columnId: 40, completedAt: undefined, updatedAt: undefined })
    expect(deriveTracking(board([rara]), project()).cycleSamples).toBe(0)
  })
})

describe('deriveTracking: insight de entrega', () => {
  const riesgo = (tasks: TaskResponse[], deadline?: string) =>
    deriveTracking(board(tasks), project(deadline)).insights.find((insight) => insight.id === 'riesgo')

  it('a buen ritmo llega a tiempo', () => {
    const insight = riesgo([hecha(1, 1), hecha(2, 2), hecha(3, 3), hecha(4, 4), pendiente(5)], '2026-11-30')
    expect(insight).toMatchObject({ variant: 'ok', title: 'Quedan 1 tareas por cerrar', recommended: false })
    expect(insight?.body).toContain('el ritmo actual llega a tiempo')
  })

  it('si el ritmo no llega, avisa del riesgo y recomienda actuar', () => {
    const muchas = Array.from({ length: 10 }, (_, i) => pendiente(10 + i))
    const insight = riesgo([hecha(1, 1), ...muchas], '2026-10-10')
    expect(insight).toMatchObject({ variant: 'risk', kicker: 'Riesgo de entrega', recommended: true })
    expect(insight?.facts?.[0]).toMatchObject({ text: 'Entrega en', value: '3 días', bad: true })
    expect(insight?.facts?.[1].value).toBe('40,0 semanas')
  })

  it('con la entrega pasada lo dice, haya ritmo o no', () => {
    const insight = riesgo([pendiente(1)], '2026-10-06')
    expect(insight).toMatchObject({ variant: 'risk', title: 'La fecha de entrega ya ha pasado' })
    expect(insight?.body).toBe('La entrega era hace 1 día y todavía quedan 1 tareas sin cerrar.')
    expect(insight?.facts?.[0].value).toBe('vencida hace 1 día')
  })

  it('sin ritmo medido no estima', () => {
    const insight = riesgo([pendiente(1)], '2026-12-01')
    expect(insight?.body).toContain('no hay ritmo con el que estimar')
    expect(insight?.facts?.[1].value).toBe('sin datos todavía')
  })

  it('sin fecha de entrega estima las semanas de trabajo', () => {
    const insight = riesgo([hecha(1, 1), pendiente(2)])
    expect(insight?.body).toContain('El proyecto no tiene fecha de entrega fijada.')
    expect(insight?.facts?.[0].value).toBe('sin fecha fijada')
  })

  it('sin nada pendiente no hay insight de entrega', () => {
    expect(riesgo([hecha(1, 1)], '2026-12-01')).toBeUndefined()
  })
})

describe('deriveTracking: insight de avisos', () => {
  const avisos = (tasks: TaskResponse[]) =>
    deriveTracking(board(tasks), project('2026-12-01')).insights.find((insight) => insight.id === 'avisos')

  it('una sola tarea con aviso se abre directamente', () => {
    const insight = avisos([pendiente(1, { title: 'Memoria', dueDate: '2026-10-06' })])
    expect(insight?.title).toBe('Hay una tarea con aviso')
    expect(insight?.body).toBe('Es «Memoria» (venció hace 1 d). Resolverlas es lo que más ayuda a llegar a la entrega.')
    expect(insight?.tasks?.[0]).toMatchObject({ kind: 'fecha', detail: 'Venció hace 1 día' })
    expect(insight?.actions[0]).toMatchObject({ label: 'Abrir la tarea', taskId: 1 })
  })

  it('varias se nombran, se explican y se ven filtradas en el tablero', () => {
    const insight = avisos([
      pendiente(1, { title: 'A', dueDate: '2026-10-07' }),
      pendiente(2, { title: 'B', updatedAt: '2026-10-01T10:00:00' }),
      pendiente(3, { title: 'C', assignees: [] }),
      pendiente(4, { title: 'D', dueDate: '2026-10-01' }),
      pendiente(5, { title: 'E', dueDate: '2026-10-02' }),
    ])
    expect(insight?.title).toBe('Hay 5 tareas con aviso')
    expect(insight?.body).toContain('Son «A» (vence hoy), «B» (6 d sin avance) y «C» (sin responsable) y 2 más.')
    expect(insight?.tasks?.map((item) => item.detail)).toEqual(['Vence hoy', '6 días sin avance', 'Sin responsable', 'Venció hace 6 días'])
    expect(insight?.moreTasks).toBe(1)
    expect(insight?.actions[0]).toMatchObject({ label: 'Verlas en el tablero', onlyWarnings: true })
  })

  it('sin avisos no hay insight', () => {
    expect(avisos([pendiente(1)])).toBeUndefined()
  })
})

describe('formatCycle', () => {
  it('distingue no tener datos de un ciclo de menos de un dia', () => {
    expect(formatCycle(0, 0)).toBe('—')
    expect(formatCycle(0.3, 2)).toBe('<1 d')
    expect(formatCycle(2.5, 2)).toBe('2,5 d')
  })
})
