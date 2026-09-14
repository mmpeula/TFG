import type { TaskResponse } from '@/types/api'

/**
 * Posicion en la que debe insertarse una tarea al soltarla.
 *
 * Se calcula SIEMPRE sobre la lista completa de la columna, nunca sobre la
 * filtrada que se esta pintando: con el filtro «solo con senales» activo, una
 * columna de 8 tareas puede mostrar 2, y soltar al final tiene que enviar la
 * posicion 8, no la 2.
 *
 * @param columnTasks todas las tareas de la columna destino, sin filtrar
 * @param draggedId   la tarea que se arrastra (se excluye del calculo)
 * @param overTaskId  la tarjeta sobre la que se ha soltado, si la hay
 */
export function dropPosition(
  columnTasks: TaskResponse[],
  draggedId: number,
  overTaskId?: number,
): number {
  const siblings = columnTasks.filter((task) => task.id !== draggedId)
  if (overTaskId === undefined) return siblings.length

  const index = siblings.findIndex((task) => task.id === overTaskId)
  return index < 0 ? siblings.length : index
}
