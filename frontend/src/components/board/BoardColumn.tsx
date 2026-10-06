import { useState } from 'react'
import { Plus } from 'lucide-react'
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
  /** Crear una tarea rapida en esta columna: solo el titulo, sin responsable y prioridad media. */
  onQuickCreate: (columnId: number, title: string) => void
}

/**
 * Una columna muestra presion, no solo tarjetas: contador y barra de presion.
 *
 * Las columnas son fijas (Por hacer, En progreso, Hecho): no tienen menu para
 * renombrarlas ni borrarlas, y el servidor tampoco lo permite.
 *
 * El «+ Añadir tarea» del pie de cada columna crea una tarea rapida en ella: solo el titulo, sin
 * responsable y con prioridad media. Lo demas se completa abriendola, o se usa el
 * boton «Añadir tarea» de arriba, que pide todos los campos.
 */
export function BoardColumn({
  column, columns, tasks, isOver, isLast, highlighted, canWrite,
  draggable = true, onOpenTask, onMoveTask, onDeleteTask, onQuickCreate,
}: Props) {
  const { setNodeRef } = useDroppable({ id: `column-${column.id}`, data: { columnId: column.id } })
  const [expanded, setExpanded] = useState(false)
  const [creating, setCreating] = useState(false)
  const [title, setTitle] = useState('')

  function create() {
    if (!title.trim()) return
    onQuickCreate(column.id, title.trim())
    setTitle('')
  }

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
      {canWrite && (creating ? (
        <div className={styles.quick}>
          <textarea
            className={styles.quickInput}
            rows={2}
            value={title}
            autoFocus
            placeholder="Título de la tarea"
            aria-label={`Título de la nueva tarea en ${column.name}`}
            onChange={(event) => setTitle(event.target.value)}
            onBlur={() => { if (!title.trim()) setCreating(false) }}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); create() }
              if (event.key === 'Escape') { setTitle(''); setCreating(false) }
            }}
          />
          <span className={styles.quickHint}>Enter crea · Esc cancela · sin responsable, prioridad media</span>
        </div>
      ) : (
        <button
          type="button"
          className={styles.add}
          onClick={() => setCreating(true)}
          title="Sin responsable y con prioridad media"
        >
          <Plus size={14} strokeWidth={2.5} /> Añadir tarea
        </button>
      ))}
    </section>
  )
}
