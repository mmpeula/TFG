import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  DndContext, DragOverlay, KeyboardSensor, PointerSensor, closestCorners, useSensor, useSensors,
  type DragEndEvent, type DragOverEvent, type DragStartEvent,
} from '@dnd-kit/core'
import { sortableKeyboardCoordinates } from '@dnd-kit/sortable'
import { Bot, Columns3, Plus, Sparkles } from 'lucide-react'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { BoardColumn } from '@/components/board/BoardColumn'
import { TaskCard } from '@/components/board/TaskCard'
import { ColumnFormDialog } from '@/components/board/ColumnFormDialog'
import { NewTaskDialog } from '@/components/board/NewTaskDialog'
import { TaskPanel } from '@/components/task/TaskPanel'
import { Button } from '@/components/ui/Button'
import { Chip, Segmented } from '@/components/ui/Controls'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import styles from './BoardScreen.module.css'
import {
  useBoard, useBoardMutations, useProjectBoards, useTaskMutations, useTeamMembers, useWorkspace,
} from '@/hooks/useData'
import { useUi } from '@/state/context'
import { useMediaQuery } from '@/hooks/useMediaQuery'
import { canManage, canWrite } from '@/lib/roles'
import { deriveSignals, taskBadge } from '@/lib/signals'
import { dropPosition } from '@/lib/board'
import { ROUTES } from '@/app/routes'
import type { TaskResponse } from '@/types/api'

export function BoardScreen() {
  const { workspaceId: workspaceIdParam } = useParams()
  const projectId = Number(workspaceIdParam)
  const navigate = useNavigate()
  const { highlighted, pushToast } = useUi()

  const { data: workspace } = useWorkspace(projectId)
  const { data: boards, isPending: loadingBoards } = useProjectBoards(projectId)
  const [boardIndex, setBoardIndex] = useState(0)
  const board = boards?.[boardIndex]
  const { data: detail, isPending } = useBoard(board?.id)
  const { data: members } = useTeamMembers(workspace?.teamId)

  const boardMutations = useBoardMutations(board?.id, projectId)
  const taskMutations = useTaskMutations(board?.id, projectId)

  const [onlySignals, setOnlySignals] = useState(false)
  const [openTask, setOpenTask] = useState<TaskResponse | null>(null)
  const [dragging, setDragging] = useState<TaskResponse | null>(null)
  const [overColumn, setOverColumn] = useState<number | null>(null)
  const [mobileColumn, setMobileColumn] = useState<number | null>(null)
  const [newTaskOpen, setNewTaskOpen] = useState(false)
  const isMobile = useMediaQuery('(max-width: 767px)')

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const columns = detail?.columns ?? []
  const role = detail?.myRole ?? null
  const writable = canWrite(role)
  const manageable = canManage(role)

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

  /** Lo que se pinta, que puede estar filtrado. Nunca sirve para calcular posiciones. */
  function tasksOf(columnId: number): TaskResponse[] {
    const tasks = allTasksOf(columnId)
    return onlySignals ? tasks.filter((task) => taskBadge(task, columns)) : tasks
  }

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
            Anadir tarea
          </Button>
        ) : undefined
      }
    >
      {loadingBoards || isPending ? (
        <Skeleton label="Cargando el tablero…" lines={4} />
      ) : !board ? (
        <EmptyState
          title="Este workspace todavia no tiene tablero"
          body="Es un workspace de antes de que el tablero se creara solo al empezar. Contacta con el responsable."
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
            ) : (
              <span />
            )}

            <div className={styles.filters}>
              <Chip active={onlySignals} onClick={() => setOnlySignals((value) => !value)}>
                <Sparkles size={12} strokeWidth={2.75} />
                Solo con senales · {signalCount}
              </Chip>
              {manageable && (
                <ColumnFormDialog
                  trigger={
                    <Button variant="secondary" small>
                      <Plus size={13} strokeWidth={2.75} />
                      Nueva columna
                    </Button>
                  }
                  onSubmit={(payload) => boardMutations.createColumn.mutateAsync(payload)}
                  isPending={boardMutations.createColumn.isPending}
                />
              )}
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
              title="Este workspace esta vacio"
              body={
                writable
                  ? 'Empieza creando tu primera tarea. Tambien puedes generar el plan con IA a partir del enunciado.'
                  : 'Todavia no hay ninguna tarea que ver.'
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
                  canManage={manageable}
                  draggable={!isMobile}
                  onOpenTask={setOpenTask}
                  onMoveTask={(task, columnId) => move(task, columnId, dropPosition(allTasksOf(columnId), task.id))}
                  onCreateTask={(columnId, title) =>
                    taskMutations.create.mutate({ columnId, payload: { title } })
                  }
                  onUpdateColumn={(columnId, payload) =>
                    boardMutations.updateColumn.mutateAsync({ columnId, payload })
                  }
                  onDeleteColumn={(columnId) => boardMutations.removeColumn.mutateAsync(columnId)}
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
                />
              )}
            </DragOverlay>
          </DndContext>

          {boardSignals.length > 0 && (
            <div className={styles.boardSignal}>
              <span className={styles.boardSignalKicker}>
                <Sparkles size={12} strokeWidth={2.75} />
                Senal del tablero
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
