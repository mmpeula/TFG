import { useState, type ReactNode } from 'react'
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
import { useEscape } from '@/hooks/useEscape'
import { useFinishPrompt } from '@/hooks/useFinishPrompt'
import { taskPayload } from '@/lib/tasks'
import { dropPosition } from '@/lib/board'
import type { TaskPayload } from '@/api/endpoints'
import { useAuth } from '@/state/context'
import { canManage, canWrite as canWriteRole, PRIORITY_LABELS } from '@/lib/roles'
import { TASK_TYPE_IDS, typeOf } from '@/lib/taskTypes'
import { taskWarning, type TaskWarningKind } from '@/lib/signals'
import { formatDate, formatDateTime, relativeTime } from '@/lib/format'
import { formatHours, hoursNumber, parseHours } from '@/lib/hours'
import type { ColumnResponse, CommentResponse, Role, TaskPriority, TaskResponse, TaskType, TeamMemberResponse } from '@/types/api'

const PRIORITIES: TaskPriority[] = ['BAJA', 'MEDIA', 'ALTA', 'CRITICA']

/**
 * Horas escritas en un campo («1,5» es hora y media): null si se ha vaciado,
 * undefined si no son un numero valido, para no mandar algo que no vale.
 */
function readHours(raw: string): number | null | undefined {
  if (raw.trim() === '') return null
  return parseHours(raw) ?? undefined
}

/** Titular del aviso de la tarea, segun su motivo. */
const WARNING_TITLES: Record<TaskWarningKind, string> = {
  fecha: 'Esta tarea va tarde',
  parada: 'Esta tarea está parada',
  'sin-responsable': 'Esta tarea no tiene responsable',
}

/** El punto de color y el nombre de un tipo de tarea, como se ve en el panel y en su menu. */
function TypeLabel({ type }: { type: TaskType }) {
  const { label, color } = typeOf(type)
  return (
    <>
      <span className={styles.typeDot} style={{ background: color }} />
      {label}
    </>
  )
}

/**
 * Una propiedad del panel que se elige de una lista (estado, tipo, prioridad): el valor
 * actual y, si se puede editar, su menu. Las tres se ven y se comportan igual.
 */
function PropertyMenu<T extends string | number>({
  value,
  options,
  onSelect,
  readOnly,
}: {
  value: ReactNode
  options: { id: T; label: ReactNode; active: boolean }[]
  onSelect: (id: T) => void
  readOnly: boolean
}) {
  if (readOnly) return <span className={styles.propValue}>{value}</span>
  return (
    <Dropdown
      align="left"
      trigger={({ toggle }) => (
        <button className={styles.propValue} onClick={toggle}>
          {value}
          <ChevronDown size={14} strokeWidth={2.75} aria-hidden="true" />
        </button>
      )}
    >
      {(close) => (
        <>
          {options.map((option) => (
            <DropdownItem
              key={option.id}
              active={option.active}
              onClick={() => {
                close()
                onSelect(option.id)
              }}
            >
              {option.label}
            </DropdownItem>
          ))}
        </>
      )}
    </Dropdown>
  )
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
  const finish = useFinishPrompt(boardId, projectId)
  const [comment, setComment] = useState('')
  const [borrando, setBorrando] = useState<CommentResponse | null>(null)

  const taskId = task?.id
  const { data: comments, isPending: loadingComments } = useComments(taskId)
  const commentMutations = useCommentMutations(taskId ?? 0, boardId, projectId)

  // Esc cierra el panel; si hay algo abierto encima (un menu, una confirmacion, la
  // pregunta de las horas), cierra eso primero.
  useEscape(onClose, task !== null)

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
    ? Math.round((task.actualHours - task.estimatedHours) * 100) / 100
    : null
  const deltaClass = delta == null ? undefined : delta <= 0 ? styles.deltaGood : styles.deltaOver
  const deltaText = delta == null ? '' : delta === 0
    ? 'justo lo estimado'
    : `${delta < 0 ? '↓' : '↑'} ${formatHours(Math.abs(delta))} vs. estimado`

  function patch(changes: Partial<TaskPayload>) {
    taskMutations.update.mutate({ taskId: task!.id, payload: taskPayload(task!, changes) })
  }

  /** Al final de la columna de destino, como el «Mover a» de la tarjeta. */
  function moveTo(columnId: number) {
    const current = task!
    if (columnId === current.columnId) return
    const terminada = columnId === columns[columns.length - 1]?.id
    const destino = columns.find((column) => column.id === columnId)?.tasks ?? []
    taskMutations.move.mutate(
      { taskId: current.id, columnId, position: dropPosition(destino, current.id) },
      { onSuccess: () => { if (terminada) finish.ask(current) } },
    )
  }

  function comentar() {
    if (!comment.trim() || commentMutations.create.isPending) return
    commentMutations.create.mutate(comment.trim())
    setComment('')
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
                const title = event.target.value.trim()
                // Un titulo vacio no se guarda: el servidor lo rechazaria. Se vuelve al que habia.
                if (!title) event.target.value = task.title
                else if (writable && title !== task.title) patch({ title })
              }}
            />
          </div>
          <button className={styles.close} onClick={onClose} aria-label="Cerrar el panel">
            <X size={16} strokeWidth={2.75} />
          </button>
        </header>

        <div className={styles.body}>
          {warning && (
            // Por que esta tarea sale en «Avisos» y que hacer para que deje de salir. En rojo, como
            // todos los avisos de la app.
            <div className={styles.warning} role="status">
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
            <PropertyMenu
              readOnly={!writable}
              value={task.columnName}
              options={columns.map((column) => ({ id: column.id, label: column.name, active: column.id === task.columnId }))}
              onSelect={moveTo}
            />

            <span className={styles.propLabel}>Tipo</span>
            <PropertyMenu
              readOnly={!writable}
              value={<TypeLabel type={task.type} />}
              options={TASK_TYPE_IDS.map((id) => ({ id, label: <TypeLabel type={id} />, active: id === task.type }))}
              onSelect={(type) => patch({ type })}
            />

            <span className={styles.propLabel}>Prioridad</span>
            <PropertyMenu
              readOnly={!writable}
              value={PRIORITY_LABELS[task.priority]}
              options={PRIORITIES.map((priority) => ({
                id: priority, label: PRIORITY_LABELS[priority], active: priority === task.priority,
              }))}
              onSelect={(priority) => patch({ priority })}
            />

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

            {/* La estimacion es la mitad de lo que la IA necesita para aprender el ritmo de cada
                persona (la otra son las horas reales al terminar): sin ella, la tarea no cuenta. */}
            <span className={styles.propLabel}>Estimación</span>
            {writable ? (
              <span className={styles.measuredInput}>
                <input
                  key={`estimated-${task.id}-${task.estimatedHours ?? ''}`}
                  className={[styles.propInput, styles.measured].join(' ')}
                  defaultValue={task.estimatedHours != null ? hoursNumber(task.estimatedHours) : ''}
                  inputMode="decimal"
                  placeholder="Horas, por ejemplo 1,5"
                  aria-label="Horas estimadas de esta tarea"
                  onBlur={(event) => {
                    const value = readHours(event.target.value)
                    if (value === undefined) {
                      event.target.value = task.estimatedHours != null ? hoursNumber(task.estimatedHours) : ''
                    } else if (value !== (task.estimatedHours ?? null)) patch({ estimatedHours: value })
                  }}
                />
              </span>
            ) : (
              <span className={[styles.propValue, styles.measured].join(' ')}>
                {task.estimatedHours != null ? formatHours(task.estimatedHours) : '—'}
              </span>
            )}

            {done && task.completedAt && (
              <>
                {/* La apunta el servidor al pasarla a «Hecho»: no se edita, se ve. */}
                <span className={styles.propLabel}>Terminada</span>
                <span className={[styles.propValue, styles.measured].join(' ')}>{formatDate(task.completedAt)}</span>
              </>
            )}

            {done && (
              <>
                <span className={styles.propLabel}>Horas reales</span>
                {writable ? (
                  <span className={styles.measuredInput}>
                    <input
                      // Con la clave, el campo se pone al dia si las horas se apuntan desde
                      // la pregunta que sale al terminar la tarea (ActualHoursDialog).
                      key={`actual-${task.id}-${task.actualHours ?? ''}`}
                      className={[styles.propInput, styles.measured].join(' ')}
                      defaultValue={task.actualHours != null ? hoursNumber(task.actualHours) : ''}
                      inputMode="decimal"
                      placeholder="Horas, por ejemplo 1,5"
                      aria-label="Horas reales dedicadas a esta tarea"
                      onBlur={(event) => {
                        const value = readHours(event.target.value)
                        if (value === undefined) {
                          event.target.value = task.actualHours != null ? hoursNumber(task.actualHours) : ''
                        } else if (value !== (task.actualHours ?? null)) patch({ actualHours: value })
                      }}
                    />
                    {delta != null && (
                      <span className={[styles.deltaNote, deltaClass].filter(Boolean).join(' ')}>
                        {deltaText}
                      </span>
                    )}
                  </span>
                ) : (
                  <span
                    className={[styles.propValue, styles.measured, deltaClass].filter(Boolean).join(' ')}
                  >
                    {task.actualHours != null ? formatHours(task.actualHours) : '—'}
                    {delta != null && (
                      <span className={styles.deltaNote}>
                        {deltaText}
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
                    {writable && (item.author.id === user?.id || responsable) && (
                      <div>
                        <Button variant="ghost" small onClick={() => setBorrando(item)}>
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
                />
                <div className={styles.composerFoot}>
                  <Button small disabled={!comment.trim() || commentMutations.create.isPending} onClick={comentar}>
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

      <ConfirmDialog
        open={borrando !== null}
        onOpenChange={(abierto) => { if (!abierto) setBorrando(null) }}
        title="Borrar el comentario"
        description="No se puede deshacer."
        isPending={commentMutations.remove.isPending}
        onConfirm={async () => {
          if (borrando) await commentMutations.remove.mutateAsync(borrando.id)
        }}
      />
      {finish.dialog}
    </>
  )
}
