import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  DndContext, DragOverlay, KeyboardSensor, PointerSensor, closestCorners, useSensor, useSensors,
  type DragEndEvent, type DragOverEvent, type DragStartEvent,
} from '@dnd-kit/core'
import { sortableKeyboardCoordinates } from '@dnd-kit/sortable'
import { AlertTriangle, Bot, Columns3, Plus, Search, X } from 'lucide-react'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { BoardColumn } from '@/components/board/BoardColumn'
import { TaskCard } from '@/components/board/TaskCard'
import { NewTaskDialog } from '@/components/board/NewTaskDialog'
import { PeopleFilter } from '@/components/board/PeopleFilter'
import { TaskPanel } from '@/components/task/TaskPanel'
import { Button } from '@/components/ui/Button'
import { Chip, Segmented } from '@/components/ui/Controls'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import styles from './BoardScreen.module.css'
import {
  useBoard, useProjectBoards, useTaskMutations, useTeamMembers, useWorkspace,
} from '@/hooks/useData'
import { useAssistant, useAuth, useUi } from '@/state/context'
import { useMediaQuery } from '@/hooks/useMediaQuery'
import { canWrite } from '@/lib/roles'
import { deriveSignals, taskBadge } from '@/lib/signals'
import { dropPosition, matchesBoardFilters } from '@/lib/board'
import { ROUTES } from '@/app/routes'
import type { TaskResponse } from '@/types/api'

export function BoardScreen() {
  const { workspaceId: workspaceIdParam } = useParams()
  const projectId = Number(workspaceIdParam)
  const navigate = useNavigate()
  const { highlighted, pushToast } = useUi()
  const { setActiveTask } = useAssistant()

  const { data: workspace } = useWorkspace(projectId)
  const { data: boards, isPending: loadingBoards } = useProjectBoards(projectId)
  const [boardIndex, setBoardIndex] = useState(0)
  const board = boards?.[boardIndex]
  const { data: detail, isPending } = useBoard(board?.id)
  const { data: members } = useTeamMembers(workspace?.teamId)

  const taskMutations = useTaskMutations(board?.id, projectId)

  const { user } = useAuth()
  const [onlySignals, setOnlySignals] = useState(false)
  // Filtros de encima del tablero: buscar por titulo o numero, y por persona.
  const [search, setSearch] = useState('')
  const [people, setPeople] = useState<number[]>([])
  const filtering = onlySignals || search.trim() !== '' || people.length > 0
  const [openTask, setOpenTask] = useState<TaskResponse | null>(null)
  // El asistente sabe de que tarea hablar mientras el panel esta abierto, sin que
  // haya que repetirsela en cada pregunta; se limpia sola al cerrar el panel o al
  // salir de esta pantalla.
  useEffect(() => {
    setActiveTask(openTask ? { id: openTask.id, title: openTask.title } : null)
    return () => setActiveTask(null)
  }, [openTask, setActiveTask])
  const [dragging, setDragging] = useState<TaskResponse | null>(null)
  const [overColumn, setOverColumn] = useState<number | null>(null)
  const [mobileColumn, setMobileColumn] = useState<number | null>(null)
  const [newTaskOpen, setNewTaskOpen] = useState(false)
  const isMobile = useMediaQuery('(max-width: 767px)')

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const columns = useMemo(() => detail?.columns ?? [], [detail])
  const role = detail?.myRole ?? null
  const writable = canWrite(role)

  const signalCount = useMemo(
    () => {
      const all = detail?.columns ?? []
      return all.flatMap((column) => column.tasks ?? []).filter((task) => taskBadge(task, all)).length
    },
    [detail],
  )
  const boardSignals = useMemo(() => deriveSignals(detail), [detail])

  /** Todas las tareas de la columna. Es la lista con la que se calculan posiciones. */
  function allTasksOf(columnId: number): TaskResponse[] {
    return columns.find((item) => item.id === columnId)?.tasks ?? []
  }

  /**
   * Lo que se pinta de cada columna, ya filtrado. Se calcula una vez por cambio de
   * tablero o de filtros, no en cada render ni por cada columna. Nunca sirve para
   * calcular posiciones: eso va siempre sobre la lista completa (allTasksOf).
   */
  const visibleByColumn = useMemo(
    () => new Map(columns.map((column) => [
      column.id,
      (column.tasks ?? []).filter((task) =>
        (!onlySignals || taskBadge(task, columns)) && matchesBoardFilters(task, search, people)),
    ])),
    [columns, onlySignals, search, people],
  )
  const tasksOf = (columnId: number): TaskResponse[] => visibleByColumn.get(columnId) ?? []
  const sinResultados = [...visibleByColumn.values()].every((list) => list.length === 0)

  function move(task: TaskResponse, columnId: number, position = 0) {
    if (task.columnId === columnId && task.position === position) return
    const target = columns.find((column) => column.id === columnId)
    taskMutations.move.mutate(
      { taskId: task.id, columnId, position },
      {
        onSuccess: () => {
          if (task.columnId !== columnId) {
            pushToast(`Movida a ${target?.name ?? 'otra columna'}`, {
              label: 'Deshacer',
              onClick: () =>
                taskMutations.move.mutate({ taskId: task.id, columnId: task.columnId, position: task.position }),
            })
          }
        },
      },
    )
  }

  function onDragStart(event: DragStartEvent) {
    const id = Number(event.active.id)
    setDragging(columns.flatMap((column) => column.tasks ?? []).find((task) => task.id === id) ?? null)
  }

  function onDragOver(event: DragOverEvent) {
    const overId = String(event.over?.id ?? '')
    if (!overId) return setOverColumn(null)
    if (overId.startsWith('column-')) return setOverColumn(Number(overId.replace('column-', '')))
    const overTask = columns.flatMap((column) => column.tasks ?? []).find((task) => task.id === Number(overId))
    setOverColumn(overTask ? overTask.columnId : null)
  }

  function onDragEnd(event: DragEndEvent) {
    const task = dragging
    setDragging(null)
    setOverColumn(null)
    if (!task || !event.over) return

    const overId = String(event.over.id)
    if (overId.startsWith('column-')) {
      const columnId = Number(overId.replace('column-', ''))
      move(task, columnId, dropPosition(allTasksOf(columnId), task.id))
      return
    }
    const overTask = columns.flatMap((column) => column.tasks ?? []).find((item) => item.id === Number(overId))
    if (!overTask) return
    move(task, overTask.columnId, dropPosition(allTasksOf(overTask.columnId), task.id, overTask.id))
  }

  if (!Number.isFinite(projectId)) return null

  const visibleColumns = isMobile
    ? columns.filter((column) => column.id === (mobileColumn ?? columns[0]?.id))
    : columns
  const sinTareas = columns.length > 0 && columns.every((column) => (column.tasks ?? []).length === 0)

  return (
    <WorkspaceShell
      workspace={workspace}
      workspaceId={projectId}
      action={
        writable && columns.length > 0 ? (
          <Button onClick={() => setNewTaskOpen(true)}>
            <Plus size={15} strokeWidth={2.75} />
            Añadir tarea
          </Button>
        ) : undefined
      }
    >
      {loadingBoards || isPending ? (
        <Skeleton label="Cargando el tablero…" lines={4} />
      ) : !board ? (
        <EmptyState
          title="Este espacio de trabajo todavía no tiene tablero"
          body="Es un espacio de trabajo de antes de que el tablero se creara solo al empezar. Contacta con el responsable."
        />
      ) : (
        <>
          <div className={styles.toolbar}>
            {boards && boards.length > 1 ? (
              <Segmented
                options={boards.map((item, index) => ({ value: String(index), label: item.name }))}
                value={String(boardIndex)}
                onChange={(value) => setBoardIndex(Number(value))}
                ariaLabel="Tablero"
              />
            ) : null}

            <label className={styles.search}>
              <Search size={14} strokeWidth={2.5} aria-hidden="true" />
              <input
                className={styles.searchInput}
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                onKeyDown={(event) => event.key === 'Escape' && setSearch('')}
                placeholder="Buscar tareas"
                title="Busca por título o por número (#13302)"
                aria-label="Buscar tarea"
              />
              {search && (
                <button type="button" className={styles.searchClear} onClick={() => setSearch('')} aria-label="Borrar la búsqueda">
                  <X size={13} strokeWidth={2.5} />
                </button>
              )}
            </label>

            <PeopleFilter members={members ?? []} selected={people} currentUserId={user?.id} onChange={setPeople} />

            <div className={styles.filters}>
              {/* Filtra el tablero a las tareas que llevan un aviso en la tarjeta («venció hace…»,
                  «… sin avance», «sin responsable»). Cuantas hay se ve al pasar el raton. */}
              <Chip
                active={onlySignals}
                onClick={() => setOnlySignals((value) => !value)}
                title={`Ver solo las tareas con aviso: vencidas, paradas o sin responsable (${signalCount})`}
              >
                <AlertTriangle size={13} strokeWidth={2.5} />
                Avisos
              </Chip>
            </div>
          </div>

          {isMobile && columns.length > 0 && (
            <div className={styles.mobileTabs}>
              <Segmented
                options={columns.map((column) => ({ value: String(column.id), label: column.name }))}
                value={String(mobileColumn ?? columns[0].id)}
                onChange={(value) => setMobileColumn(Number(value))}
                ariaLabel="Estado"
              />
            </div>
          )}

          {sinTareas ? (
            <EmptyState
              icon={<Columns3 size={22} strokeWidth={2.75} />}
              title="Este espacio de trabajo está vacío"
              body={
                writable
                  ? 'Empieza creando tu primera tarea. También puedes generar el plan con IA a partir del enunciado.'
                  : 'Todavía no hay ninguna tarea que ver.'
              }
              action={
                writable ? (
                  <div className={styles.emptyActions}>
                    <Button onClick={() => setNewTaskOpen(true)}>
                      <Plus size={15} strokeWidth={2.75} />
                      Crear tarea
                    </Button>
                    <Button variant="secondary" onClick={() => navigate(ROUTES.generarPlanById(projectId))}>
                      <Bot size={15} strokeWidth={2.75} />
                      Generar plan
                    </Button>
                  </div>
                ) : undefined
              }
            />
          ) : null}

          {!sinTareas && filtering && sinResultados && (
            <div className={styles.noResults}>
              <span>Ninguna tarea coincide con estos filtros.</span>
              <Button
                variant="ghost"
                small
                onClick={() => { setSearch(''); setPeople([]); setOnlySignals(false) }}
              >
                Quitar los filtros
              </Button>
            </div>
          )}

          <DndContext
            sensors={sensors}
            collisionDetection={closestCorners}
            onDragStart={onDragStart}
            onDragOver={onDragOver}
            onDragEnd={onDragEnd}
            onDragCancel={() => { setDragging(null); setOverColumn(null) }}
          >
            <div
              className={[styles.board, sinTareas && styles.boardQuiet].filter(Boolean).join(' ')}
              style={{ gridTemplateColumns: `repeat(${Math.max(1, visibleColumns.length)}, minmax(0, 1fr))` }}
            >
              {visibleColumns.map((column, index) => (
                <BoardColumn
                  key={column.id}
                  column={column}
                  columns={columns}
                  tasks={tasksOf(column.id)}
                  isOver={overColumn === column.id && dragging?.columnId !== column.id}
                  isLast={!isMobile && index === visibleColumns.length - 1}
                  highlighted={highlighted}
                  canWrite={writable}
                  draggable={!isMobile}
                  onOpenTask={setOpenTask}
                  onMoveTask={(task, columnId) => move(task, columnId, dropPosition(allTasksOf(columnId), task.id))}
                  onDeleteTask={(task) => taskMutations.remove.mutateAsync(task.id)}
                />
              ))}
            </div>

            <DragOverlay>
              {dragging && (
                <TaskCard
                  task={dragging}
                  columns={columns}
                  overlay
                  draggable={false}
                  onOpen={() => undefined}
                  onMove={() => undefined}
                  onDelete={() => undefined}
                />
              )}
            </DragOverlay>
          </DndContext>

          {boardSignals.length > 0 && (
            <div className={styles.boardSignal}>
              <span className={styles.boardSignalKicker}>
                <AlertTriangle size={12} strokeWidth={2.75} />
                Aviso del tablero
              </span>
              <p className={styles.boardSignalText}>{boardSignals[0].text}</p>
              <Button variant="ghost" small onClick={() => navigate(ROUTES.pulsoById(projectId))}>
                Ver el detalle
              </Button>
            </div>
          )}
        </>
      )}

      <TaskPanel
        task={openTask ? columns.flatMap((c) => c.tasks ?? []).find((t) => t.id === openTask.id) ?? openTask : null}
        columns={columns}
        members={members ?? []}
        role={role}
        boardId={board?.id}
        projectId={projectId}
        onClose={() => setOpenTask(null)}
      />

      <NewTaskDialog
        open={newTaskOpen}
        columns={columns}
        members={members ?? []}
        isPending={taskMutations.create.isPending}
        onClose={() => setNewTaskOpen(false)}
        onSubmit={(columnId, payload) => taskMutations.create.mutateAsync({ columnId, payload })}
      />
    </WorkspaceShell>
  )
}
