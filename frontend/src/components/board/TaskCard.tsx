import { useState } from 'react'
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { Check, MessageSquare, MoreHorizontal, Paperclip, Sparkles, Trash2 } from 'lucide-react'
import { Avatar } from '@/components/ui/Avatar'
import { Tag } from '@/components/ui/Tag'
import { Dropdown, DropdownItem } from '@/components/ui/Controls'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { SignalBadge } from './SignalBadge'
import styles from './TaskCard.module.css'
import { formatDate, daysUntil } from '@/lib/format'
import { PRIORITY_COLOR, PRIORITY_LABELS } from '@/lib/roles'
import type { ColumnResponse, TaskResponse } from '@/types/api'

interface Props {
  task: TaskResponse
  columns: ColumnResponse[]
  badge?: string
  done?: boolean
  highlighted?: boolean
  canWrite?: boolean
  onOpen: (task: TaskResponse) => void
  onMove: (task: TaskResponse, columnId: number) => void
  onDelete: (task: TaskResponse) => void | Promise<unknown>
  draggable?: boolean
  overlay?: boolean
}

export function TaskCard({
  task, columns, badge, done, highlighted, canWrite = true,
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
      style={
        overlay
          ? undefined
          : { transform: CSS.Translate.toString(sortable.transform), transition: sortable.transition }
      }
    >
      {badge && <SignalBadge text={badge} />}

      {draggable && canWrite && !overlay && (
        <span
          className={styles.grip}
          {...sortable.attributes}
          {...sortable.listeners}
          role="button"
          aria-label={`Arrastrar ${task.title}`}
        />
      )}

      <div className={styles.content}>
        <header className={styles.head}>
          <span className={styles.code}>#{task.id}</span>
          <span
            className={styles.priority}
            style={{ background: PRIORITY_COLOR[task.priority] }}
            title={`Prioridad ${PRIORITY_LABELS[task.priority].toLowerCase()}`}
          />
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
                  {columns
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
        </header>

        <button className={styles.title} onClick={() => onOpen(task)}>
          <span className={done ? styles.doneTitle : undefined}>
            {done && (
              <span className={styles.check}>
                <Check size={13} strokeWidth={2.75} />
              </span>
            )}
            {task.title}
          </span>
        </button>

        {task.aiGenerated && (
          <div className={styles.tags}>
            <Tag small variant="accent">
              <Sparkles size={10} strokeWidth={2.75} /> IA
            </Tag>
          </div>
        )}

        <footer className={styles.foot}>
          <Avatar user={task.assignee} size={22} />
          {task.commentCount > 0 && (
            <span className={styles.stat}>
              <MessageSquare size={12} strokeWidth={2.75} />
              {task.commentCount}
            </span>
          )}
          {task.attachmentCount > 0 && (
            <span className={styles.stat}>
              <Paperclip size={12} strokeWidth={2.75} />
              {task.attachmentCount}
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
          description="Se borraran tambien sus comentarios y adjuntos."
          onConfirm={() => onDelete(task)}
        />
      )}
    </article>
  )
}
