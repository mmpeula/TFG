import { useState } from 'react'
import { Modal } from '@/components/ui/Modal'
import { Button } from '@/components/ui/Button'
import { AssigneePicker } from '@/components/task/AssigneePicker'
import styles from './NewTaskDialog.module.css'
import type { TaskPayload } from '@/api/endpoints'
import { TASK_TYPES, TASK_TYPE_IDS } from '@/lib/taskTypes'
import type { ColumnResponse, TaskType, TeamMemberResponse } from '@/types/api'

interface Props {
  open: boolean
  columns: ColumnResponse[]
  members: TeamMemberResponse[]
  isPending?: boolean
  onClose: () => void
  onSubmit: (columnId: number, payload: TaskPayload) => Promise<unknown>
}

/**
 * Alta de tarea con los cuatro campos que de verdad se usan al crearla. Todo lo
 * demas (prioridad, horas, comentarios, adjuntos) vive en el panel de detalle:
 * pedirlo aqui solo conseguiria que nadie creara tareas.
 */
export function NewTaskDialog({ open, columns, members, isPending, onClose, onSubmit }: Props) {
  // El formulario nace vacio cada vez que se abre porque solo se monta entonces.
  if (!open) return null
  return (
    <NewTaskForm
      columns={columns}
      members={members}
      isPending={isPending}
      onClose={onClose}
      onSubmit={onSubmit}
    />
  )
}

function NewTaskForm({ columns, members, isPending, onClose, onSubmit }: Omit<Props, 'open'>) {
  const [title, setTitle] = useState('')
  const [type, setType] = useState<TaskType>('TAREA')
  const [description, setDescription] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [assigneeIds, setAssigneeIds] = useState<number[]>([])
  const [columnId, setColumnId] = useState(String(columns[0]?.id ?? ''))

  const valido = title.trim().length > 0 && columnId !== ''

  async function submit() {
    if (!valido || isPending) return
    await onSubmit(Number(columnId), {
      title: title.trim(),
      type,
      description: description.trim() || undefined,
      dueDate: dueDate || null,
      assigneeIds: assigneeIds.length > 0 ? assigneeIds : undefined,
    })
    onClose()
  }

  return (
    <Modal
      open
      title="Nueva tarea"
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={submit} disabled={!valido || isPending}>
            {isPending ? 'Creando…' : 'Crear tarea'}
          </Button>
        </>
      }
    >
      <div className={styles.form}>
        <label className={styles.label}>
          Titulo
          <input
            className={styles.input}
            value={title}
            autoFocus
            placeholder="Modelar la base de datos"
            onChange={(event) => setTitle(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && submit()}
          />
        </label>

        {/* El tipo define el color de la tarjeta en el plan; por eso va arriba. */}
        <div className={styles.label}>
          Tipo
          <div className={styles.types} role="radiogroup" aria-label="Tipo de tarea">
            {TASK_TYPE_IDS.map((id) => (
              <button
                key={id}
                type="button"
                role="radio"
                aria-checked={type === id}
                className={[styles.type, type === id && styles.typeOn].filter(Boolean).join(' ')}
                onClick={() => setType(id)}
              >
                <span className={styles.dot} style={{ background: TASK_TYPES[id].color }} />
                {TASK_TYPES[id].label}
              </button>
            ))}
          </div>
        </div>

        <label className={styles.label}>
          Descripcion <span className={styles.optional}>opcional</span>
          <textarea
            className={[styles.input, styles.area].join(' ')}
            rows={3}
            value={description}
            placeholder="Que hay que hacer exactamente"
            onChange={(event) => setDescription(event.target.value)}
          />
        </label>

        <div className={styles.row}>
          <label className={styles.label}>
            Fecha limite <span className={styles.optional}>opcional</span>
            <input
              type="date"
              className={styles.input}
              value={dueDate}
              onChange={(event) => setDueDate(event.target.value)}
            />
          </label>

          <label className={styles.label}>
            Responsables <span className={styles.optional}>opcional</span>
            <AssigneePicker
              members={members}
              selected={members.filter((member) => assigneeIds.includes(member.user.id)).map((member) => member.user)}
              onChange={setAssigneeIds}
              className={styles.input}
            />
          </label>
        </div>

        {columns.length > 1 && (
          <label className={styles.label}>
            Columna
            <select
              className={styles.input}
              value={columnId}
              onChange={(event) => setColumnId(event.target.value)}
            >
              {columns.map((column) => (
                <option key={column.id} value={column.id}>
                  {column.name}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
    </Modal>
  )
}
