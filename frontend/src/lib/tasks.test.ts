import { describe, expect, it } from 'vitest'
import { task, user } from '@/test/factories'
import { taskPayload } from './tasks'

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
