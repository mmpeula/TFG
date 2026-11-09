import type { TaskPayload } from '@/api/endpoints'
import type { TaskResponse, UserSummary } from '@/types/api'

/** Lo mismo que admite el servidor (TaskRequest.MAX_DESCRIPTION). */
export const MAX_DESCRIPTION = 10_000

/**
 * Lo que hay que mandar para guardar una tarea con algun cambio. El servidor
 * reemplaza la tarea entera (PUT), asi que todo lo que no cambia se copia tal
 * cual: un campo olvidado aqui se borraria al guardar cualquier otro.
 */
export function taskPayload(task: TaskResponse, changes: Partial<TaskPayload> = {}): TaskPayload {
  return {
    title: task.title,
    description: task.description,
    type: task.type,
    priority: task.priority,
    dueDate: task.dueDate ?? null,
    estimatedHours: task.estimatedHours ?? null,
    actualHours: task.actualHours ?? null,
    assigneeIds: task.assignees.map((assignee) => assignee.id),
    ...changes,
  }
}

/**
 * La tarea tal y como quedara con esos cambios, para ensenarla antes de que conteste el
 * servidor. Los responsables llegan como ids: se buscan entre los que ya tenia y la gente
 * del equipo.
 */
export function withChanges(task: TaskResponse, changes: Partial<TaskPayload>, people: UserSummary[]): TaskResponse {
  const { assigneeIds, dueDate, estimatedHours, actualHours, ...rest } = changes
  const known = new Map([...people, ...task.assignees].map((person) => [person.id, person]))
  return {
    ...task,
    ...rest,
    ...(dueDate !== undefined && { dueDate: dueDate ?? undefined }),
    ...(estimatedHours !== undefined && { estimatedHours: estimatedHours ?? undefined }),
    ...(actualHours !== undefined && { actualHours: actualHours ?? undefined }),
    ...(assigneeIds !== undefined && {
      assignees: assigneeIds.map((id) => known.get(id)).filter((person): person is UserSummary => person !== undefined),
    }),
  }
}
