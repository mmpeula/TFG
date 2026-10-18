import type { ColumnResponse, TaskResponse, TeamMemberResponse, UserSummary } from '@/types/api'

/** Una tarea valida con lo minimo; cada test cambia solo lo que le importa. */
export function task(overrides: Partial<TaskResponse> = {}): TaskResponse {
  return {
    id: 1,
    title: 'Tarea de prueba',
    position: 0,
    type: 'TAREA',
    priority: 'MEDIA',
    aiGenerated: false,
    columnId: 10,
    columnName: 'Por hacer',
    boardId: 1,
    projectId: 1,
    assignees: [],
    commentCount: 0,
    createdAt: '2026-10-01T10:00:00',
    ...overrides,
  }
}

/** Tablero de cuatro columnas como el que se crea por defecto. */
export function columns(tasks: TaskResponse[] = []): ColumnResponse[] {
  const base = [
    { id: 10, name: 'Por hacer' },
    { id: 20, name: 'En progreso' },
    { id: 30, name: 'En revisión' },
    { id: 40, name: 'Hecho' },
  ]
  return base.map((column, position) => {
    const own = tasks.filter((item) => item.columnId === column.id)
    return { ...column, position, taskCount: own.length, tasks: own }
  })
}

export function user(id: number, fullName: string): UserSummary {
  return { id, fullName, email: `${id}@uni.es`, avatarUrl: null }
}

export function member(
  id: number,
  fullName: string,
  role: TeamMemberResponse['role'] = 'MIEMBRO',
  enabled = true,
): TeamMemberResponse {
  return { id, user: user(id, fullName), role, joinedAt: '2026-09-01T10:00:00', enabled }
}
