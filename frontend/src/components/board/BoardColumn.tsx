import { useState } from 'react'
import { useDroppable } from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { MoreVertical, Pencil, Plus, Trash2 } from 'lucide-react'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { Button } from '@/components/ui/Button'
import { Dropdown, DropdownItem } from '@/components/ui/Controls'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { TaskCard } from './TaskCard'
import { DropZone } from './DropZone'
import { TaskComposer } from './TaskComposer'
import { ColumnFormDialog } from './ColumnFormDialog'
import styles from './BoardColumn.module.css'
import { taskBadge } from '@/lib/signals'
import type { ColumnResponse, TaskResponse } from '@/types/api'

const VISIBLE = 4

interface Props {
  column: ColumnResponse
  columns: ColumnResponse[]
  tasks: TaskResponse[]
  isOver: boolean
  isLast: boolean
  highlighted: number | null
  canWrite: boolean
  canManage: boolean
  draggable?: boolean
  onOpenTask: (task: TaskResponse) => void
  onMoveTask: (task: TaskResponse, columnId: number) => void
  onDeleteTask: (task: TaskResponse) => void | Promise<unknown>
  onCreateTask: (columnId: number, title: string) => void
  onUpdateColumn: (columnId: number, payload: { name: string; wipLimit?: number | null }) => Promise<unknown>
  onDeleteColumn: (columnId: number) => Promise<unknown>
}

/**
 * Una columna muestra presion, no solo tarjetas: contador, limite de trabajo en
 * curso y barra de presion.
 */
export function BoardColumn({
  column, columns, tasks, isOver, isLast, highlighted, canWrite, canManage,
  draggable = true, onOpenTask, onMoveTask, onDeleteTask, onCreateTask, onUpdateColumn, onDeleteColumn,
}: Props) {
  const { setNodeRef } = useDroppable({ id: `column-${column.id}`, data: { columnId: column.id } })
  const [expanded, setExpanded] = useState(false)
  const [composing, setComposing] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)

  const overLimit = column.wipLimit != null && tasks.length > column.wipLimit
  const pressure = column.wipLimit
    ? Math.min(100, Math.round((tasks.length / column.wipLimit) * 100))
    : Math.min(100, tasks.length * 20)

  const visible = expanded ? tasks : tasks.slice(0, VISIBLE)
  const hidden = tasks.length - visible.length

  return (
    <section className={styles.column} ref={setNodeRef} aria-label={column.name}>
      <header className={styles.head}>
        <h2 className={styles.name}>{column.name}</h2>
        <span className={[styles.count, overLimit && styles.countOver].filter(Boolean).join(' ')}>
          {column.wipLimit ? `${tasks.length} / ${column.wipLimit}` : tasks.length}
        </span>
        {canWrite && (
          <button className={styles.add} onClick={() => setComposing(true)} aria-label={`Anadir tarea a ${column.name}`}>
            <Plus size={13} strokeWidth={2.75} />
          </button>
        )}
        {canManage && (
          <Dropdown
            trigger={({ toggle }) => (
              <button className={styles.add} style={{ border: 'none' }} onClick={toggle} aria-label={`Acciones de ${column.name}`}>
                <MoreVertical size={13} strokeWidth={2.75} />
              </button>
            )}
          >
            {(close) => (
              <>
                <DropdownItem onClick={() => { close(); setEditOpen(true) }}>
                  <Pencil size={14} strokeWidth={2.75} /> Editar
                </DropdownItem>
                <DropdownItem onClick={() => { close(); setDeleteOpen(true) }}>
                  <Trash2 size={14} strokeWidth={2.75} /> Borrar
                </DropdownItem>
              </>
            )}
          </Dropdown>
        )}
      </header>

      {/* Solo se ve cuando la columna se pasa del limite: si va bien, no hay nada que mirar. */}
      <ProgressBar
        value={pressure}
        height={2}
        color={overLimit ? '#b42318' : 'var(--color-neutral-300)'}
        label={`Presion de ${column.name}`}
      />

      {overLimit && <div className={styles.warning}>Limite de trabajo en curso superado</div>}

      {isOver && <DropZone columnName={column.name} />}

      {composing && (
        <TaskComposer onCreate={(title) => onCreateTask(column.id, title)} onClose={() => setComposing(false)} />
      )}

      <div className={styles.cards}>
        <SortableContext items={visible.map((task) => task.id)} strategy={verticalListSortingStrategy}>
          {visible.map((task) => (
            <TaskCard
              key={task.id}
              task={task}
              columns={columns}
              badge={taskBadge(task, columns)}
              done={isLast}
              canWrite={canWrite}
              draggable={draggable}
              highlighted={highlighted === task.id}
              onOpen={onOpenTask}
              onMove={onMoveTask}
              onDelete={onDeleteTask}
            />
          ))}
        </SortableContext>
      </div>

      {tasks.length === 0 && !composing && (
        <p style={{ fontSize: 12, color: 'var(--color-neutral-600)', padding: 'var(--space-2)' }}>
          Sin tareas
        </p>
      )}

      {hidden > 0 && (
        <button className={styles.more} onClick={() => setExpanded(true)}>+ {hidden} mas</button>
      )}
      {expanded && tasks.length > VISIBLE && (
        <button className={styles.more} onClick={() => setExpanded(false)}>Ver menos</button>
      )}

      <ColumnFormDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        column={column}
        onSubmit={(payload) => onUpdateColumn(column.id, payload)}
      />
      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title={`Borrar la columna «${column.name}»`}
        description="Solo se puede borrar si esta vacia y no es la ultima columna del tablero."
        onConfirm={() => onDeleteColumn(column.id)}
      />

      {canWrite && !composing && (
        <footer className={styles.footer}>
          <Button variant="ghost" small block onClick={() => setComposing(true)}>
            <Plus size={14} strokeWidth={2.75} />
            Anadir tarea
          </Button>
        </footer>
      )}
    </section>
  )
}
