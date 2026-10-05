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

/** Marca de «sin responsable» en el filtro por persona: no es el id de nadie. */
export const SIN_ASIGNAR = 0

/** Minusculas y sin tildes: «Diseño» encuentra «diseno» y al reves. */
function plain(text: string): string {
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
}

/**
 * Si una tarea pasa los filtros del tablero.
 *
 * @param search texto del buscador: busca en el titulo, o por numero («#13302» o «13302»)
 * @param people ids de persona marcados ({@link SIN_ASIGNAR} para las que no tienen
 *               responsable); vacio no filtra
 */
export function matchesBoardFilters(task: TaskResponse, search: string, people: number[]): boolean {
  const query = plain(search.trim())
  if (query) {
    const numero = query.replace(/^#/, '')
    const porNumero = /^\d+$/.test(numero) && String(task.id).startsWith(numero)
    if (!porNumero && !plain(task.title).includes(query)) return false
  }
  if (people.length > 0) {
    const suya = task.assignees.some((assignee) => people.includes(assignee.id))
    const sinNadie = task.assignees.length === 0 && people.includes(SIN_ASIGNAR)
    if (!suya && !sinNadie) return false
  }
  return true
}
