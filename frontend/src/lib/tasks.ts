import type { TaskPayload } from '@/api/endpoints'
import type { TaskResponse } from '@/types/api'

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
