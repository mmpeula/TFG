import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { columns, task, user } from '@/test/factories'
import { boardWarningText, overdueDays, stalledDays, taskWarning, unassignedInProgress } from './signals'

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-07T12:00:00'))
})
afterEach(() => vi.useRealTimers())

const ana = user(1, 'Ana')
const reciente = '2026-10-07T09:00:00'

describe('overdueDays', () => {
  it('vencida y sin terminar: dias negativos', () => {
    const t = task({ dueDate: '2026-10-04', updatedAt: reciente })
    expect(overdueDays(t, columns([t]))).toBe(-3)
  })

  it('vence hoy: 0', () => {
    const t = task({ dueDate: '2026-10-07' })
    expect(overdueDays(t, columns([t]))).toBe(0)
  })

  it('terminada, futura o sin fecha: null', () => {
    const hecha = task({ dueDate: '2026-10-01', columnId: 40 })
    const futura = task({ dueDate: '2026-10-20' })
    expect(overdueDays(hecha, columns([hecha]))).toBeNull()
    expect(overdueDays(futura, columns([futura]))).toBeNull()
    expect(overdueDays(task(), columns())).toBeNull()
  })
})

describe('stalledDays y unassignedInProgress', () => {
  it('parada solo en columnas de trabajo y a partir de 4 dias', () => {
    const parada = task({ columnId: 20, updatedAt: '2026-10-02T10:00:00', assignees: [ana] })
    const porHacer = task({ columnId: 10, updatedAt: '2026-09-01T10:00:00' })
    const tres = task({ columnId: 20, updatedAt: '2026-10-04T10:00:00' })
    const cols = columns([parada, porHacer, tres])
    expect(stalledDays(parada, cols)).toBe(5)
    expect(stalledDays(porHacer, cols)).toBeNull()
    expect(stalledDays(tres, cols)).toBeNull()
  })

  it('sin responsable solo cuenta en columnas de trabajo', () => {
    const enCurso = task({ columnId: 30 })
    const porHacer = task({ columnId: 10 })
    const cols = columns([enCurso, porHacer])
    expect(unassignedInProgress(enCurso, cols)).toBe(true)
    expect(unassignedInProgress(porHacer, cols)).toBe(false)
  })
})

describe('taskWarning', () => {
  it('la fecha manda sobre lo demas', () => {
    const t = task({ columnId: 20, dueDate: '2026-10-05', updatedAt: '2026-09-01T10:00:00' })
    expect(taskWarning(t, columns([t]))).toMatchObject({ kind: 'fecha', short: 'venció hace 2 d' })
  })

  it('vence hoy', () => {
    const t = task({ dueDate: '2026-10-07' })
    expect(taskWarning(t, columns([t]))?.short).toBe('vence hoy')
  })

  it('parada antes que sin responsable', () => {
    const t = task({ columnId: 20, updatedAt: '2026-10-01T10:00:00' })
    expect(taskWarning(t, columns([t]))).toMatchObject({ kind: 'parada', short: '6 d sin avance' })
  })

  it('en curso sin nadie', () => {
    const t = task({ columnId: 20, updatedAt: reciente })
    expect(taskWarning(t, columns([t]))?.kind).toBe('sin-responsable')
  })

  it('una tarea al dia no lleva aviso', () => {
    const t = task({ columnId: 20, updatedAt: reciente, assignees: [ana] })
    expect(taskWarning(t, columns([t]))).toBeUndefined()
  })
})

describe('boardWarningText', () => {
  it('elige el aviso mas grave del tablero', () => {
    const sinNadie = task({ id: 1, title: 'Sin nadie', columnId: 20, columnName: 'En progreso', updatedAt: reciente })
    const vencida = task({ id: 2, title: 'Memoria', dueDate: '2026-10-04', columnName: 'Por hacer' })
    expect(boardWarningText(columns([sinNadie, vencida]))).toBe('«Memoria» venció hace 3 d y sigue en Por hacer.')
  })

  it('sin avisos devuelve null', () => {
    const t = task({ columnId: 40 })
    expect(boardWarningText(columns([t]))).toBeNull()
  })
})
