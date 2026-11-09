import { describe, expect, it } from 'vitest'
import { task, user } from '@/test/factories'
import { taskPayload, withChanges } from './tasks'

describe('taskPayload', () => {
  it('copia todos los campos para que el PUT no borre nada', () => {
    const t = task({ title: 'Memoria', dueDate: '2026-10-20', estimatedHours: 3, assignees: [user(4, 'Ana'), user(7, 'Pablo')] })
    expect(taskPayload(t)).toEqual({
      title: 'Memoria',
      description: undefined,
      type: 'TAREA',
      priority: 'MEDIA',
      dueDate: '2026-10-20',
      estimatedHours: 3,
      actualHours: null,
      assigneeIds: [4, 7],
    })
  })

  it('aplica los cambios encima', () => {
    expect(taskPayload(task(), { priority: 'ALTA', dueDate: null })).toMatchObject({ priority: 'ALTA', dueDate: null })
  })
})

describe('withChanges', () => {
  it('aplica los cambios y resuelve los responsables con la gente del equipo', () => {
    const t = task({ title: 'Memoria', assignees: [user(4, 'Ana')] })
    const next = withChanges(t, { priority: 'ALTA', assigneeIds: [4, 7] }, [user(7, 'Pablo')])
    expect(next.priority).toBe('ALTA')
    expect(next.title).toBe('Memoria')
    expect(next.assignees.map((person) => person.fullName)).toEqual(['Ana', 'Pablo'])
  })

  it('un null del formulario borra el campo; lo que no se toca se queda', () => {
    const t = task({ dueDate: '2026-10-20', estimatedHours: 3, actualHours: 2 })
    const next = withChanges(t, { dueDate: null, estimatedHours: null, actualHours: 5 }, [])
    expect(next.dueDate).toBeUndefined()
    expect(next.estimatedHours).toBeUndefined()
    expect(next.actualHours).toBe(5)
    expect(withChanges(t, { title: 'Otro' }, []).dueDate).toBe('2026-10-20')
  })

  it('ignora los ids que no conoce en vez de pintar un hueco', () => {
    expect(withChanges(task(), { assigneeIds: [99] }, []).assignees).toEqual([])
  })
})
