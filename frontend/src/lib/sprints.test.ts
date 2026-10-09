import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { columns, task } from '@/test/factories'
import { buildPlan, isoDate } from './sprints'

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-07T12:00:00'))
})
afterEach(() => vi.useRealTimers())

describe('isoDate', () => {
  it('usa la fecha local, no UTC', () => {
    expect(isoDate(new Date(2026, 9, 26, 0, 0))).toBe('2026-10-26')
  })
})

describe('buildPlan', () => {
  it('reparte las tareas en el sprint de su fecha limite', () => {
    const a = task({ id: 1, dueDate: '2026-10-08' })
    const b = task({ id: 2, dueDate: '2026-10-20', columnId: 40 })
    const plan = buildPlan([a, b], columns([a, b]), { start: '2026-10-05', end: '2026-10-25', sprintDays: 7 })

    expect(plan.sprints).toHaveLength(3)
    expect(plan.sprints[0].tasks.map((item) => item.task.id)).toEqual([1])
    expect(plan.sprints[2].tasks.map((item) => item.task.id)).toEqual([2])
    expect(plan.sprints[2].progress).toBe(100)
    expect(plan.sprints[0].current).toBe(true)
  })

  it('las tareas sin fecha quedan aparte', () => {
    const t = task({ id: 3 })
    const plan = buildPlan([t], columns([t]), { start: '2026-10-05', end: '2026-10-18', sprintDays: 14 })
    expect(plan.sinFecha.map((item) => item.task.id)).toEqual([3])
  })

  it('un ultimo sprint de pocos dias se suma al anterior', () => {
    const plan = buildPlan([], columns(), { start: '2026-10-05', end: '2026-10-20', sprintDays: 7 })
    expect(plan.sprints).toHaveLength(2)
    expect(isoDate(plan.sprints[1].end)).toBe('2026-10-20')
  })

  it('una entrega anterior a la ultima tarea se alarga para no dejarla fuera', () => {
    const t = task({ dueDate: '2026-11-02' })
    const plan = buildPlan([t], columns([t]), { start: '2026-10-05', end: '2026-10-18', sprintDays: 14 })
    expect(isoDate(plan.end)).toBe('2026-11-02')
    expect(plan.sprints.flatMap((sprint) => sprint.tasks)).toHaveLength(1)
  })

  it('el cambio de hora de octubre no corre los sprints ni deja huecos', () => {
    // El 25 de octubre de 2026 se atrasa el reloj en Espana.
    const t = task({ dueDate: '2026-11-02' })
    const plan = buildPlan([t], columns([t]), { start: '2026-10-13', end: '2026-11-30', sprintDays: 21 })
    expect(isoDate(plan.sprints[1].start)).toBe('2026-11-03')
    expect(plan.sprints[0].tasks).toHaveLength(1)
  })

  it('sin fechas del workspace las deduce de las tareas', () => {
    const a = task({ id: 1, dueDate: '2026-10-10' })
    const b = task({ id: 2, dueDate: '2026-10-30' })
    const plan = buildPlan([a, b], columns([a, b]), { sprintDays: 14 })
    expect(isoDate(plan.start)).toBe('2026-10-10')
    expect(isoDate(plan.end)).toBe('2026-10-30')
  })

  it('el estado sale de la columna', () => {
    const porHacer = task({ id: 1, dueDate: '2026-10-08', columnId: 10 })
    const enCurso = task({ id: 2, dueDate: '2026-10-08', columnId: 30 })
    const plan = buildPlan([porHacer, enCurso], columns([porHacer, enCurso]), { start: '2026-10-05', end: '2026-10-11', sprintDays: 7 })
    expect(plan.sprints[0].sinEmpezar).toHaveLength(1)
    expect(plan.sprints[0].enCurso).toHaveLength(1)
  })
})
