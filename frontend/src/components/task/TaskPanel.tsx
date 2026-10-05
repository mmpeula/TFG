import { useEffect, useState } from 'react'
import { AlertTriangle, ChevronDown, MessageSquare, Send, Trash2, X } from 'lucide-react'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { Tag } from '@/components/ui/Tag'
import { Dropdown, DropdownItem } from '@/components/ui/Controls'
import { AssigneePicker } from './AssigneePicker'
import { DateInput } from '@/components/ui/DateInput'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import styles from './TaskPanel.module.css'
import { useCommentMutations, useComments, useTaskMutations } from '@/hooks/useData'
import { useAuth } from '@/state/context'
import { canManage, canWrite as canWriteRole, PRIORITY_LABELS } from '@/lib/roles'
import { TASK_TYPES, TASK_TYPE_IDS, typeOf } from '@/lib/taskTypes'
import { taskWarning, type TaskWarningKind } from '@/lib/signals'
import { formatDate, formatDateTime, relativeTime } from '@/lib/format'
import type { ColumnResponse, Role, TaskPriority, TaskResponse, TeamMemberResponse } from '@/types/api'

const PRIORITIES: TaskPriority[] = ['BAJA', 'MEDIA', 'ALTA', 'CRITICA']

/** Titular del aviso de la tarea, segun su motivo. */
const WARNING_TITLES: Record<TaskWarningKind, string> = {
  fecha: 'Esta tarea va tarde',
  parada: 'Esta tarea está parada',
  'sin-responsable': 'Esta tarea no tiene responsable',
}

interface Props {
  task: TaskResponse | null
  columns: ColumnResponse[]
  members: TeamMemberResponse[]
  role: Role | null
  boardId?: number
  projectId?: number
  onClose: () => void
}

/**
 * Detalle de tarea en panel lateral de 560 px que entra desde la derecha.
 * Todo se edita en linea: no hay formulario con labels y boton Guardar.
 */
export function TaskPanel({ task, columns, members, role, boardId, projectId, onClose }: Props) {
  const { user } = useAuth()
  const taskMutations = useTaskMutations(boardId, projectId)
  const [comment, setComment] = useState('')

  const taskId = task?.id
  const { data: comments, isPending: loadingComments } = useComments(taskId)
  const commentMutations = useCommentMutations(taskId ?? 0)

  useEffect(() => {
    if (!task) return
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [task, onClose])

  if (!task) return null

  const writable = canWriteRole(role)
  const warning = taskWarning(task, columns)
  const responsable = canManage(role)

  // Hecha, para PlanifIA, es estar en la ultima columna del tablero: no hay otro
  // campo que lo diga. Solo ahi tiene sentido preguntar cuanto ha costado de verdad.
  const done = columns.length > 0 && task.columnId === columns[columns.length - 1].id

  // Diferencia entre lo real y lo estimado, igual que un diff: por debajo es
  // verde, por encima es rojo. Sin las dos cifras no hay nada que comparar.
  const delta = task.actualHours != null && task.estimatedHours != null
    ? Math.round((task.actualHours - task.estimatedHours) * 10) / 10
    : null
  const deltaClass = delta == null ? undefined : delta <= 0 ? styles.deltaGood : styles.deltaOver

  function patch(values: Partial<Pick<TaskResponse, 'title' | 'description' | 'type' | 'priority' | 'columnId'>> & {
    assigneeIds?: number[]
    actualHours?: number | null
    dueDate?: string | null
  }) {
    taskMutations.update.mutate({
      taskId: task!.id,
      payload: {
        title: values.title ?? task!.title,
        description: values.description ?? task!.description,
        type: values.type ?? task!.type,
        priority: values.priority ?? task!.priority,
        dueDate: 'dueDate' in values ? (values.dueDate ?? null) : (task!.dueDate ?? null),
        estimatedHours: task!.estimatedHours ?? null,
        actualHours: 'actualHours' in values ? (values.actualHours ?? null) : (task!.actualHours ?? null),
        assigneeIds: values.assigneeIds ?? task!.assignees.map((assignee) => assignee.id),
      },
    })
  }

  return (
    <>
      <div className={styles.scrim} onClick={onClose} role="presentation" />
      <aside className={styles.panel} role="dialog" aria-label={task.title}>
        <header className={styles.head}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className={styles.code}>Tarea #{task.id} · {task.columnName}</div>
            <textarea
              key={task.id}
              className={styles.title}
              rows={1}
              defaultValue={task.title}
              readOnly={!writable}
              aria-label="Título de la tarea"
              ref={(node) => {
                if (node) {
                  node.style.height = 'auto'
                  node.style.height = `${node.scrollHeight}px`
                }
              }}
              onInput={(event) => {
                const node = event.currentTarget
                node.style.height = 'auto'
                node.style.height = `${node.scrollHeight}px`
              }}
              onBlur={(event) => {
                if (writable && event.target.value !== task.title) patch({ title: event.target.value })
              }}
            />
          </div>
          <button className={styles.close} onClick={onClose} aria-label="Cerrar el panel">
            <X size={16} strokeWidth={2.75} />
          </button>
        </header>

        <div className={styles.body}>
          {warning && (
            // Por que esta tarea sale en «Avisos» y que hacer para que deje de salir. Rojo si va
            // tarde, ambar si solo pide atencion (parada o sin responsable): el color tiene ese trabajo.
            <div
              className={[styles.warning, warning.kind === 'fecha' ? styles.warningLate : styles.warningAttention].join(' ')}
              role="status"
            >
              <AlertTriangle size={15} strokeWidth={2.5} aria-hidden="true" />
              <div>
                <p className={styles.warningTitle}>{WARNING_TITLES[warning.kind]}</p>
                <p className={styles.warningText}>{warning.detail} {warning.action}</p>
              </div>
            </div>
          )}

          <textarea
            key={`desc-${task.id}`}
            className={styles.description}
            defaultValue={task.description ?? ''}
            placeholder="Sin descripción. Escribe aquí el contexto de la tarea."
            readOnly={!writable}
            aria-label="Descripción"
            onBlur={(event) => {
              if (writable && event.target.value !== (task.description ?? '')) {
                patch({ description: event.target.value })
              }
            }}
          />

          <div className={styles.props}>
            <span className={styles.propLabel}>Responsables</span>
            {writable ? (
              <AssigneePicker
                members={members}
                selected={task.assignees}
                onChange={(assigneeIds) => patch({ assigneeIds })}
                align="left"
                className={styles.propValue}
              />
            ) : (
              <span className={styles.propValue}>
                {task.assignees.length === 0
                  ? <Avatar size={20} />
                  : task.assignees.map((assignee) => (
                      <Avatar key={assignee.id} user={assignee} avatarUrl={assignee.avatarUrl} size={20} />
                    ))}
                {task.assignees.length === 0
                  ? 'Sin asignar'
                  : task.assignees.map((assignee) => assignee.fullName).join(', ')}
              </span>
            )}

            <span className={styles.propLabel}>Estado</span>
            {writable ? (
              <Dropdown
                align="left"
                trigger={({ toggle }) => (
                  <button className={styles.propValue} onClick={toggle}>
                    {task.columnName}
                    <ChevronDown size={14} strokeWidth={2.75} aria-hidden="true" />
                  </button>
                )}
              >
                {(close) => (
                  <>
                    {columns.map((column) => (
                      <DropdownItem
                        key={column.id}
                        active={column.id === task.columnId}
                        onClick={() => {
                          close()
                          taskMutations.move.mutate({ taskId: task.id, columnId: column.id, position: 0 })
                        }}
                      >
                        {column.name}
                      </DropdownItem>
                    ))}
                  </>
                )}
              </Dropdown>
            ) : (
              <span className={styles.propValue}>{task.columnName}</span>
            )}

            <span className={styles.propLabel}>Tipo</span>
            {writable ? (
              <Dropdown
                align="left"
                trigger={({ toggle }) => (
                  <button className={styles.propValue} onClick={toggle}>
                    <span className={styles.typeDot} style={{ background: typeOf(task.type).color }} />
                    {typeOf(task.type).label}
                    <ChevronDown size={14} strokeWidth={2.75} aria-hidden="true" />
                  </button>
                )}
              >
                {(close) => (
                  <>
                    {TASK_TYPE_IDS.map((id) => (
                      <DropdownItem
                        key={id}
                        active={id === task.type}
                        onClick={() => { close(); patch({ type: id }) }}
                      >
                        <span className={styles.typeDot} style={{ background: TASK_TYPES[id].color }} />
                        {TASK_TYPES[id].label}
                      </DropdownItem>
                    ))}
                  </>
                )}
              </Dropdown>
            ) : (
              <span className={styles.propValue}>
                <span className={styles.typeDot} style={{ background: typeOf(task.type).color }} />
                {typeOf(task.type).label}
              </span>
            )}

            <span className={styles.propLabel}>Prioridad</span>
            {writable ? (
              <Dropdown
                align="left"
                trigger={({ toggle }) => (
                  <button className={styles.propValue} onClick={toggle}>
                    {PRIORITY_LABELS[task.priority]}
                    <ChevronDown size={14} strokeWidth={2.75} aria-hidden="true" />
                  </button>
                )}
              >
                {(close) => (
                  <>
                    {PRIORITIES.map((priority) => (
                      <DropdownItem
                        key={priority}
                        active={priority === task.priority}
                        onClick={() => { close(); patch({ priority }) }}
                      >
                        {PRIORITY_LABELS[priority]}
                      </DropdownItem>
                    ))}
                  </>
                )}
              </Dropdown>
            ) : (
              <span className={styles.propValue}>{PRIORITY_LABELS[task.priority]}</span>
            )}

            {/* La fecha limite decide en que sprint cae la tarea en el plan: cambiarla es
                tambien la forma de moverla de sprint, o de sacarla de «Sin fecha». */}
            <span className={styles.propLabel}>Fecha límite</span>
            {writable ? (
              <DateInput
                key={`due-${task.id}`}
                className={[styles.propInput, styles.measured].join(' ')}
                value={task.dueDate ?? ''}
                aria-label="Fecha límite de la tarea"
                onChange={(iso) => {
                  if (iso !== (task.dueDate ?? '')) patch({ dueDate: iso || null })
                }}
              />
            ) : (
              <span className={[styles.propValue, styles.measured].join(' ')}>{formatDate(task.dueDate)}</span>
            )}

            <span className={styles.propLabel}>Estimación</span>
            <span className={[styles.propValue, styles.measured].join(' ')}>
              {task.estimatedHours != null ? `${task.estimatedHours} h` : '—'}
            </span>

            {done && (
              <>
                <span className={styles.propLabel}>Horas reales</span>
                {writable ? (
                  <span className={styles.measuredInput}>
                    <input
                      type="number"
                      min={0}
                      step={0.5}
                      className={[styles.propInput, styles.measured].join(' ')}
                      defaultValue={task.actualHours ?? ''}
                      placeholder="Cuánto ha costado de verdad"
                      aria-label="Horas reales dedicadas a esta tarea"
                      onBlur={(event) => {
                        const raw = event.target.value.trim()
                        const value = raw === '' ? null : Number(raw)
                        if (value !== (task.actualHours ?? null)) patch({ actualHours: value })
                      }}
                    />
                    {delta != null && (
                      <span className={[styles.deltaNote, deltaClass].filter(Boolean).join(' ')}>
                        {delta <= 0 ? '↓' : '↑'} {Math.abs(delta)} h vs. estimado
                      </span>
                    )}
                  </span>
                ) : (
                  <span
                    className={[styles.propValue, styles.measured, deltaClass].filter(Boolean).join(' ')}
                  >
                    {task.actualHours != null ? `${task.actualHours} h` : '—'}
                    {delta != null && (
                      <span className={styles.deltaNote}>
                        {delta <= 0 ? '↓' : '↑'} {Math.abs(delta)} h vs. estimado
                      </span>
                    )}
                  </span>
                )}
              </>
            )}
          </div>

          {writable && (
            <div>
              <ConfirmDialog
                trigger={
                  <Button variant="secondary" small>
                    <Trash2 size={13} strokeWidth={2.75} />
                    Borrar la tarea
                  </Button>
                }
                title={`Borrar «${task.title}»`}
                description="Se borrarán también sus comentarios. No se puede deshacer."
                onConfirm={async () => {
                  await taskMutations.remove.mutateAsync(task.id)
                  onClose()
                }}
              />
            </div>
          )}

          <section>
            <h3 className={styles.sectionTitle}>
              <MessageSquare size={14} strokeWidth={2.75} style={{ verticalAlign: -2, marginRight: 6 }} />
              Comentarios ({comments?.totalElements ?? task.commentCount})
            </h3>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', margin: 'var(--space-3) 0' }}>
              {loadingComments && <p className={styles.hint}>Cargando comentarios…</p>}
              {comments?.content.length === 0 && <p className={styles.hint}>Todavía no hay comentarios.</p>}
              {comments?.content.map((item) => (
                <div key={item.id} className={styles.comment}>
                  <Avatar user={item.author} avatarUrl={item.author.avatarUrl} size={26} />
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <p className={styles.commentBody}>
                      <strong>{item.author.fullName}</strong> {item.content}
                    </p>
                    <span className={styles.commentMeta}>
                      {relativeTime(item.createdAt)}
                      {item.edited && ' · editado'}
                    </span>
                    {(item.author.id === user?.id || responsable) && (
                      <div>
                        <Button
                          variant="ghost"
                          small
                          onClick={() => commentMutations.remove.mutate(item.id)}
                        >
                          Borrar
                        </Button>
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>

            {writable ? (
              <div className={styles.composer}>
                <textarea
                  className={styles.composerInput}
                  value={comment}
                  placeholder="Escribe un comentario…"
                  onChange={(event) => setComment(event.target.value)}
                  onKeyDown={(event) => {
                    if ((event.metaKey || event.ctrlKey) && event.key === 'Enter' && comment.trim()) {
                      event.preventDefault()
                      commentMutations.create.mutate(comment.trim())
                      setComment('')
                    }
                  }}
                />
                <div className={styles.composerFoot}>
                  <span className={styles.hint}>⌘+Enter envía</span>
                  <Button
                    small
                    disabled={!comment.trim() || commentMutations.create.isPending}
                    onClick={() => {
                      commentMutations.create.mutate(comment.trim())
                      setComment('')
                    }}
                  >
                    <Send size={13} strokeWidth={2.75} />
                    Comentar
                  </Button>
                </div>
              </div>
            ) : (
              <p className={styles.hint}>Tu rol en este equipo es de solo lectura.</p>
            )}
          </section>

          <section className={styles.history}>
            <h3 className={styles.sectionTitle}>Historial</h3>
            <span>Creada por {task.createdBy?.fullName ?? 'desconocido'} · {formatDateTime(task.createdAt)}</span>
            {task.updatedAt && <span>Último cambio · {formatDateTime(task.updatedAt)}</span>}
            {task.aiGenerated && <Tag variant="accent" small>Generada por IA</Tag>}
          </section>
        </div>
      </aside>
    </>
  )
}
