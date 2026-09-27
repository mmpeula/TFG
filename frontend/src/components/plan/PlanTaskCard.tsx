import { useState } from 'react'
import { CalendarDays, MessageSquare, MoreHorizontal, Paperclip, Sparkles, Trash2 } from 'lucide-react'
import { Avatar } from '@/components/ui/Avatar'
import { Dropdown, DropdownItem } from '@/components/ui/Controls'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import styles from './PlanTaskCard.module.css'
import { formatDate, daysUntil } from '@/lib/format'
import { typeOf } from '@/lib/taskTypes'
import { PRIORITY_LABELS } from '@/lib/roles'
import type { TaskResponse } from '@/types/api'

interface Props {
  task: TaskResponse
  done?: boolean
  /** Estado con su color y su texto: por hacer / en curso / terminada. */
  state: { label: string; color: string }
  canWrite?: boolean
  onOpen: (task: TaskResponse) => void
  onDelete: (task: TaskResponse) => void | Promise<unknown>
}

/**
 * Tarjeta de tarea dentro de un sprint.
 *
 * Ensena de un vistazo lo que hace falta para no tener que abrirla: de que va
 * (la franja de color y su etiqueta), como va (la insignia de estado, con su
 * propio color y su texto), quien la lleva, para cuando y si urge. No hay
 * columnas ni bandas separadas por estado: todas las tareas del sprint estan
 * juntas y la diferencia entre "por hacer", "en curso" y "terminada" se ve
 * solo por esa insignia, nunca por estar en un sitio distinto de la pantalla.
 * Cambiar el estado se hace abriendo la tarjeta, igual que en el tablero.
 */
export function PlanTaskCard({ task, done, state, canWrite = true, onOpen, onDelete }: Props) {
  const tipo = typeOf(task.type)
  const quedan = task.dueDate ? daysUntil(task.dueDate) : null
  const urge = !done && quedan !== null && quedan <= 3
  const alta = task.priority === 'ALTA' || task.priority === 'CRITICA'
  const [confirmOpen, setConfirmOpen] = useState(false)

  return (
    <article
      className={[styles.card, done && styles.done].filter(Boolean).join(' ')}
      style={{ '--tipo': tipo.color } as React.CSSProperties}
    >
      {canWrite && (
        <div className={styles.menuRow}>
          <Dropdown
            trigger={({ toggle }) => (
              <button className={styles.menu} onClick={toggle} aria-label={`Acciones de ${task.title}`}>
                <MoreHorizontal size={13} strokeWidth={2.75} />
              </button>
            )}
          >
            {(close) => (
              <>
                <DropdownItem onClick={() => { close(); onOpen(task) }}>Abrir la tarea</DropdownItem>
                <DropdownItem onClick={() => { close(); setConfirmOpen(true) }}>
                  <Trash2 size={13} strokeWidth={2.75} /> Borrar
                </DropdownItem>
              </>
            )}
          </Dropdown>
        </div>
      )}

      <button className={styles.hit} onClick={() => onOpen(task)} aria-label={`Abrir ${task.title}`}>
        <span className={styles.title}>{task.title}</span>

        <span className={styles.badges}>
          <span className={styles.type}>
            <span className={styles.dot} aria-hidden="true" />
            {tipo.label}
          </span>

          <span className={styles.stateBadge} style={{ '--estado': state.color } as React.CSSProperties}>
            <span className={styles.stateDot} aria-hidden="true" />
            {state.label}
          </span>
        </span>

        <span className={styles.foot}>
          {task.assignees.length > 0 ? (
            task.assignees.map((assignee) => <Avatar key={assignee.id} user={assignee} size={20} />)
          ) : (
            <span className={styles.unassigned}>Sin asignar</span>
          )}

          <span className={styles.spacer} />

          {alta && <span className={styles.priority}>{PRIORITY_LABELS[task.priority].toLowerCase()}</span>}
          {task.commentCount > 0 && (
            <span className={styles.stat}>
              <MessageSquare size={11} strokeWidth={2} />
              {task.commentCount}
            </span>
          )}
          {task.attachmentCount > 0 && (
            <span className={styles.stat}>
              <Paperclip size={11} strokeWidth={2} />
              {task.attachmentCount}
            </span>
          )}
          {task.aiGenerated && (
            <span className={styles.stat} title="Propuesta por la IA">
              <Sparkles size={11} strokeWidth={2} />
            </span>
          )}
          {task.dueDate && (
            <span className={[styles.due, urge && styles.dueSoon].filter(Boolean).join(' ')}>
              <CalendarDays size={11} strokeWidth={2} />
              {formatDate(task.dueDate)}
            </span>
          )}
        </span>
      </button>

      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={`Borrar «${task.title}»`}
        description="Se borraran tambien sus comentarios y adjuntos."
        onConfirm={() => onDelete(task)}
      />
    </article>
  )
}
