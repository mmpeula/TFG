import { useState } from 'react'
import { DateInput } from '@/components/ui/DateInput'
import { parseHours } from '@/lib/hours'
import { MAX_DESCRIPTION } from '@/lib/tasks'
import { Modal } from '@/components/ui/Modal'
import { DialogActions } from '@/components/ui/DialogActions'
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
 * Alta de tarea con todo lo que se suele saber al crearla: titulo, tipo, que hay que
 * hacer, fecha, horas, responsables y columna. Solo el titulo es obligatorio. La
 * prioridad (media por defecto) y los comentarios se tocan despues, en el panel de la
 * tarea; para crear una al vuelo, solo con el titulo, esta el «+ Añadir tarea» de cada
 * columna.
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
  const [estimated, setEstimated] = useState('')
  const [assigneeIds, setAssigneeIds] = useState<number[]>([])
  const [columnId, setColumnId] = useState(String(columns[0]?.id ?? ''))

  // Siempre en horas: «1,5» es hora y media. Vacio, sin estimacion.
  const horas = estimated.trim() === '' ? null : parseHours(estimated)
  const horasValidas = estimated.trim() === '' || horas !== null
  const valido = title.trim().length > 0 && columnId !== '' && horasValidas

  async function submit() {
    if (!valido || isPending) return
    try {
      await onSubmit(Number(columnId), {
        title: title.trim(),
        type,
        description: description.trim() || undefined,
        dueDate: dueDate || null,
        estimatedHours: horas,
        assigneeIds: assigneeIds.length > 0 ? assigneeIds : undefined,
      })
      onClose()
    } catch {
      // El motivo ya sale en el aviso de la mutacion; el dialogo sigue abierto con lo escrito.
    }
  }

  return (
    <Modal
      open
      title="Nueva tarea"
      onClose={onClose}
      footer={
        <DialogActions
          onCancel={onClose}
          onConfirm={submit}
          label="Crear tarea"
          pendingLabel="Creando…"
          pending={isPending}
          disabled={!valido}
        />
      }
    >
      <div className={styles.form}>
        <label className={styles.label}>
          Título
          <input
            className={styles.input}
            value={title}
            autoFocus
            placeholder="Modelar la base de datos"
            onChange={(event) => setTitle(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && submit()}
          />
        </label>

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
          Descripción <span className={styles.optional}>opcional</span>
          <textarea
            className={[styles.input, styles.area].join(' ')}
            rows={3}
            value={description}
            placeholder="Qué hay que hacer exactamente"
            maxLength={MAX_DESCRIPTION}
            onChange={(event) => setDescription(event.target.value)}
          />
        </label>

        <div className={styles.row}>
          <label className={styles.label}>
            Fecha límite <span className={styles.optional}>opcional</span>
            <DateInput
              className={styles.input}
              value={dueDate}
              onChange={setDueDate}
            />
          </label>

          {/* Sin estimacion la tarea no le sirve a la IA para aprender el ritmo de nadie:
              al terminarla, sus horas reales no tendrian con que compararse. */}
          <label className={styles.label}>
            Horas estimadas <span className={styles.optional}>opcional</span>
            <input
              className={styles.input}
              value={estimated}
              inputMode="decimal"
              placeholder="Por ejemplo: 1,5"
              onChange={(event) => setEstimated(event.target.value)}
            />
            {!horasValidas && <span className={styles.optional}>Escribe las horas con un número, por ejemplo 1,5.</span>}
          </label>
        </div>

        <label className={styles.label}>
          Responsables <span className={styles.optional}>opcional</span>
          <AssigneePicker
            members={members}
            selected={members.filter((member) => assigneeIds.includes(member.user.id)).map((member) => member.user)}
            onChange={setAssigneeIds}
            className={styles.input}
          />
        </label>

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
