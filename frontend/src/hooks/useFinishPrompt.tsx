import { useCallback, useState } from 'react'
import { ActualHoursDialog } from '@/components/task/ActualHoursDialog'
import { useTaskMutations } from '@/hooks/useData'
import { taskPayload } from '@/lib/tasks'
import type { TaskResponse } from '@/types/api'

/**
 * Pregunta las horas reales al terminar una tarea (ver ActualHoursDialog).
 *
 * Quien mueve una tarea a la ultima columna llama a `ask(task)`; el dialogo se
 * pinta donde se ponga `dialog`. Si la tarea ya tenia horas reales apuntadas no
 * se vuelve a preguntar: moverla de nuevo a «Hecho» no cambia lo que costo.
 */
export function useFinishPrompt(boardId?: number, projectId?: number) {
  const [task, setTask] = useState<TaskResponse | null>(null)
  const taskMutations = useTaskMutations(boardId, projectId)

  const ask = useCallback((finished: TaskResponse) => {
    if (finished.actualHours == null) setTask(finished)
  }, [])

  const dialog = (
    <ActualHoursDialog
      task={task}
      saving={taskMutations.update.isPending}
      onClose={() => setTask(null)}
      onSave={(hours) => {
        if (!task) return
        taskMutations.update.mutate(
          { taskId: task.id, payload: taskPayload(task, { actualHours: hours }) },
          { onSuccess: () => setTask(null) },
        )
      }}
    />
  )

  return { ask, dialog, open: task !== null }
}
