import { useState } from 'react'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import {
  Check, ChevronDown, ChevronsUp, ChevronUp, Equal, MessageSquare, MoreHorizontal, Sparkles, Trash2,
} from 'lucide-react'
import { BreakableText } from '@/components/ui/BreakableText'
import { Avatar } from '@/components/ui/Avatar'
import { Tag } from '@/components/ui/Tag'
import { Dropdown, DropdownItem } from '@/components/ui/Controls'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { SignalBadge } from './SignalBadge'
import type { TaskWarning } from '@/lib/signals'
import styles from './TaskCard.module.css'
import { formatDate, daysUntil } from '@/lib/format'
import { formatHours } from '@/lib/hours'
import { PRIORITY_COLOR, PRIORITY_LABELS } from '@/lib/roles'
import type { ColumnResponse, TaskPriority, TaskResponse } from '@/types/api'

/*
 * La prioridad, a la vista: la franja de la izquierda ya la pinta, pero un color
 * solo no dice cual es. La etiqueta lleva el nombre, una flecha que sube o baja
 * (se lee aunque no se distingan los colores) y el mismo tono que la franja:
 * rojo lo critico, ambar lo alto, gris lo que puede esperar.
 */
const PRIORITY_ICON = { CRITICA: ChevronsUp, ALTA: ChevronUp, MEDIA: Equal, BAJA: ChevronDown } as const
const PRIORITY_TONE: Record<TaskPriority, { fg: string; bg: string }> = {
  CRITICA: { fg: 'var(--color-danger-700)', bg: 'var(--color-danger-100)' },
  ALTA: { fg: 'var(--color-warning-700)', bg: 'var(--color-warning-100)' },
  MEDIA: { fg: 'var(--color-neutral-700)', bg: 'var(--color-neutral-200)' },
  BAJA: { fg: 'var(--color-neutral-600)', bg: 'var(--color-neutral-100)' },
}

/** Sin columnas no hay a donde mover: el menu solo abre y borra. Una sola lista para todas las tarjetas. */
const SIN_COLUMNAS: ColumnResponse[] = []

function PriorityTag({ priority }: { priority: TaskPriority }) {
  const Icon = PRIORITY_ICON[priority]
  const tone = PRIORITY_TONE[priority]
  return (
    <span
      className={styles.priority}
      style={{ '--p-fg': tone.fg, '--p-bg': tone.bg } as React.CSSProperties}
      title={`Prioridad ${PRIORITY_LABELS[priority].toLowerCase()}`}
    >
      <Icon size={12} strokeWidth={2.75} aria-hidden="true" />
      {PRIORITY_LABELS[priority]}
    </span>
  )
}

interface Props {
  task: TaskResponse
  /** Columnas a las que se puede mover desde el menu; sin ellas, el menu solo abre y borra. */
  columns?: ColumnResponse[]
  badge?: TaskWarning
  done?: boolean
  highlighted?: boolean
  canWrite?: boolean
  /**
   * Estado de la tarea con su color (por hacer / en curso / terminada). En el tablero no hace
   * falta, lo dice la columna; en el plan las tareas van por sprint y no por columna.
   */
  status?: { label: string; color: string }
  onOpen: (task: TaskResponse) => void
  onMove?: (task: TaskResponse, columnId: number) => void
  onDelete: (task: TaskResponse) => void | Promise<unknown>
  draggable?: boolean
  overlay?: boolean
}

/**
 * La tarjeta de una tarea: la misma en el tablero y en el plan, para que una tarea se vea
 * igual este donde este. En el plan no se arrastra (draggable={false}) y lleva su estado.
 */
export function TaskCard({
  task, columns = SIN_COLUMNAS, badge, done, highlighted, canWrite = true, status,
  onOpen, onMove, onDelete, draggable = true, overlay,
}: Props) {
  const [confirmOpen, setConfirmOpen] = useState(false)
  const sortable = useSortable({
    id: task.id,
    data: { columnId: task.columnId },
    disabled: !draggable || !canWrite,
  })
  const remaining = task.dueDate ? daysUntil(task.dueDate) : null

  const className = [
    styles.card,
    badge && styles.flagged,
    done && styles.done,
    sortable.isDragging && styles.dragging,
    overlay && styles.overlay,
    highlighted && styles.highlight,
  ].filter(Boolean).join(' ')

  return (
    <article
      ref={overlay ? undefined : sortable.setNodeRef}
      className={className}
      style={{
        '--prioridad': PRIORITY_COLOR[task.priority],
        ...(overlay ? {} : { transform: CSS.Translate.toString(sortable.transform), transition: sortable.transition }),
      } as React.CSSProperties}
    >

      {draggable && canWrite && !overlay && (
        <span
          className={styles.grip}
          {...sortable.attributes}
          {...sortable.listeners}
          // Un clic sin mover la tarjeta la abre, se pulse donde se pulse: si solo abriera el
          // titulo, una tarea con un titulo corto apenas tendria donde pulsar. Tras arrastrar,
          // dnd-kit se come el clic, asi que soltarla no la abre.
          onClick={() => onOpen(task)}
          role="button"
          aria-label={`Arrastrar ${task.title}`}
        />
      )}

      <div className={styles.content}>
        <header className={styles.head}>
          <span className={styles.code}>
            #{task.id}{task.estimatedHours != null && <span className={styles.codeHours}> · {formatHours(task.estimatedHours)}</span>}
          </span>
          {/* Hueco flexible: menu, aviso y estado quedan juntos a la derecha. El menu va antes
              del aviso porque solo se ve al pasar el raton: asi el aviso queda pegado al borde. */}
          <span className={styles.headSpacer} />
          {canWrite && (
            <Dropdown
              trigger={({ toggle }) => (
                <button className={styles.menu} onClick={toggle} aria-label={`Acciones de ${task.title}`}>
                  <MoreHorizontal size={14} strokeWidth={2.75} />
                </button>
              )}
            >
              {(close) => (
                <>
                  <DropdownItem onClick={() => { close(); onOpen(task) }}>Abrir la tarea</DropdownItem>
                  {onMove && columns
                    .filter((column) => column.id !== task.columnId)
                    .map((column) => (
                      <DropdownItem key={column.id} onClick={() => { close(); onMove(task, column.id) }}>
                        Mover a {column.name}
                      </DropdownItem>
                    ))}
                  <DropdownItem onClick={() => { close(); setConfirmOpen(true) }}>
                    <Trash2 size={13} strokeWidth={2.75} /> Borrar
                  </DropdownItem>
                </>
              )}
            </Dropdown>
          )}
          {badge && <SignalBadge warning={badge} />}
          {/* Lo ultimo de la cabecera, pegado al borde: el boton de acciones queda a su izquierda. */}
          {status && (
            <span className={styles.status} style={{ '--estado': status.color } as React.CSSProperties}>
              <span className={styles.statusDot} aria-hidden="true" />
              {status.label}
            </span>
          )}
        </header>

        <button className={styles.title} onClick={() => onOpen(task)}>
          <span className={done ? styles.doneTitle : undefined}>
            {done && (
              <span className={styles.check}>
                <Check size={13} strokeWidth={2.75} />
              </span>
            )}
            <BreakableText>{task.title}</BreakableText>
          </span>
        </button>

        <div className={styles.tags}>
          <PriorityTag priority={task.priority} />
          {task.aiGenerated && (
            <Tag small variant="accent">
              <Sparkles size={10} strokeWidth={2.75} /> IA
            </Tag>
          )}
        </div>

        <footer className={styles.foot}>
          {task.assignees.length === 0
            ? <Avatar size={22} />
            : task.assignees.map((assignee) => (
                <Avatar key={assignee.id} user={assignee} avatarUrl={assignee.avatarUrl} size={22} />
              ))}
          {task.commentCount > 0 && (
            <span className={styles.stat}>
              <MessageSquare size={12} strokeWidth={2.75} />
              {task.commentCount}
            </span>
          )}
          <span className={[styles.due, remaining !== null && remaining <= 0 && styles.dueSoon].filter(Boolean).join(' ')}>
            {task.dueDate ? formatDate(task.dueDate) : ''}
          </span>
        </footer>
      </div>

      {!overlay && (
        <ConfirmDialog
          open={confirmOpen}
          onOpenChange={setConfirmOpen}
          title={`Borrar «${task.title}»`}
          description="Se borrarán también sus comentarios. No se puede deshacer."
          onConfirm={() => onDelete(task)}
        />
      )}
    </article>
  )
}
