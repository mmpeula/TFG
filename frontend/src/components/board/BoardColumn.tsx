import { useState } from 'react'
import { useDroppable } from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { TaskCard } from './TaskCard'
import { DropZone } from './DropZone'
import styles from './BoardColumn.module.css'
import { taskWarning } from '@/lib/signals'
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
  draggable?: boolean
  onOpenTask: (task: TaskResponse) => void
  onMoveTask: (task: TaskResponse, columnId: number) => void
  onDeleteTask: (task: TaskResponse) => void | Promise<unknown>
}

/**
 * Una columna muestra presion, no solo tarjetas: contador y barra de presion.
 *
 * Las columnas son fijas (Por hacer, En progreso, Hecho): no tienen menu para
 * renombrarlas ni borrarlas, y el servidor tampoco lo permite. Tampoco crean
 * tareas: eso se hace solo desde el boton «Añadir tarea» de arriba, un unico
 * sitio con todos los campos (columna, responsables, fecha…).
 */
export function BoardColumn({
  column, columns, tasks, isOver, isLast, highlighted, canWrite,
  draggable = true, onOpenTask, onMoveTask, onDeleteTask,
}: Props) {
  const { setNodeRef } = useDroppable({ id: `column-${column.id}`, data: { columnId: column.id } })
  const [expanded, setExpanded] = useState(false)

  // Cuanto se ha llenado la columna, a ojo: cada tarea suma un quinto de la barra.
  const pressure = Math.min(100, tasks.length * 20)

  const visible = expanded ? tasks : tasks.slice(0, VISIBLE)
  const hidden = tasks.length - visible.length

  return (
    <section className={styles.column} ref={setNodeRef} aria-label={column.name}>
      <header className={styles.head}>
        <h2 className={styles.name}>{column.name}</h2>
        <span className={styles.count}>{tasks.length}</span>
      </header>

      <ProgressBar
        value={pressure}
        height={2}
        color="var(--color-neutral-300)"
        label={`Presión de ${column.name}`}
      />

      {isOver && <DropZone columnName={column.name} />}

      <div className={styles.cards}>
        <SortableContext items={visible.map((task) => task.id)} strategy={verticalListSortingStrategy}>
          {visible.map((task) => (
            <TaskCard
              key={task.id}
              task={task}
              columns={columns}
              badge={taskWarning(task, columns)}
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

      {tasks.length === 0 && (
        <p style={{ fontSize: 12, color: 'var(--color-neutral-600)', padding: 'var(--space-2)' }}>
          Sin tareas
        </p>
      )}

      {hidden > 0 && (
        <button className={styles.more} onClick={() => setExpanded(true)}>+ {hidden} más</button>
      )}
      {expanded && tasks.length > VISIBLE && (
        <button className={styles.more} onClick={() => setExpanded(false)}>Ver menos</button>
      )}
    </section>
  )
}
