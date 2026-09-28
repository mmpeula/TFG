import { useEffect, useState } from 'react'
import { Loader2, MessageSquare, Paperclip, Send, Sparkles, Trash2, Upload, X } from 'lucide-react'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { Tag } from '@/components/ui/Tag'
import { Dropdown, DropdownItem } from '@/components/ui/Controls'
import { AssigneePicker } from './AssigneePicker'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import styles from './TaskPanel.module.css'
import {
  useAttachmentMutations, useAttachments, useCommentMutations, useComments, useTaskMutations,
} from '@/hooks/useData'
import { useAuth } from '@/state/context'
import { useUi } from '@/state/context'
import { canManage, canWrite as canWriteRole, PRIORITY_LABELS } from '@/lib/roles'
import { TASK_TYPES, TASK_TYPE_IDS, typeOf } from '@/lib/taskTypes'
import { formatDate, formatDateTime, formatFileSize, relativeTime } from '@/lib/format'
import type { ColumnResponse, Role, TaskPriority, TaskResponse, TeamMemberResponse } from '@/types/api'

const PRIORITIES: TaskPriority[] = ['BAJA', 'MEDIA', 'ALTA', 'CRITICA']

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
  const { pushToast } = useUi()
  const taskMutations = useTaskMutations(boardId, projectId)
  const [comment, setComment] = useState('')
  const [dragOver, setDragOver] = useState(false)

  const taskId = task?.id
  const { data: comments, isPending: loadingComments } = useComments(taskId)
  const { data: attachments } = useAttachments(taskId)
  const commentMutations = useCommentMutations(taskId ?? 0)
  const attachmentMutations = useAttachmentMutations(taskId ?? 0)

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
  const responsable = canManage(role)

  function patch(values: Partial<Pick<TaskResponse, 'title' | 'description' | 'type' | 'priority' | 'columnId'>> & {
    assigneeIds?: number[]
  }) {
    taskMutations.update.mutate({
      taskId: task!.id,
      payload: {
        title: values.title ?? task!.title,
        description: values.description ?? task!.description,
        type: values.type ?? task!.type,
        priority: values.priority ?? task!.priority,
        dueDate: task!.dueDate ?? null,
        estimatedHours: task!.estimatedHours ?? null,
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
              aria-label="Titulo de la tarea"
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
          <textarea
            key={`desc-${task.id}`}
            className={styles.description}
            defaultValue={task.description ?? ''}
            placeholder="Sin descripcion. Escribe aqui el contexto de la tarea."
            readOnly={!writable}
            aria-label="Descripcion"
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
                  <button className={styles.propValue} onClick={toggle}>{task.columnName}</button>
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

            <span className={styles.propLabel}>Fecha</span>
            <span className={styles.propValue}>{formatDate(task.dueDate)}</span>

            <span className={styles.propLabel}>Estimacion</span>
            <span className={styles.propValue}>
              {task.estimatedHours != null ? `${task.estimatedHours} h` : '—'}
            </span>
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
                description="Se borraran tambien sus comentarios y adjuntos."
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
              {comments?.content.length === 0 && <p className={styles.hint}>Todavia no hay comentarios.</p>}
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
                  <span className={styles.hint}>⌘+Enter envia</span>
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

          <section>
            <h3 className={styles.sectionTitle}>
              <Paperclip size={14} strokeWidth={2.75} style={{ verticalAlign: -2, marginRight: 6 }} />
              Adjuntos ({attachments?.length ?? task.attachmentCount})
            </h3>

            {writable && (
              <div
                className={[styles.drop, dragOver && styles.dropActive].filter(Boolean).join(' ')}
                style={{ marginTop: 'var(--space-2)' }}
                onDragOver={(event) => { event.preventDefault(); setDragOver(true) }}
                onDragLeave={() => setDragOver(false)}
                onDrop={(event) => {
                  event.preventDefault()
                  setDragOver(false)
                  const file = event.dataTransfer.files[0]
                  if (file) attachmentMutations.upload.mutate(file)
                }}
              >
                {attachmentMutations.upload.isPending ? (
                  <span><Loader2 size={13} className="spin" strokeWidth={2.75} /> Subiendo…</span>
                ) : dragOver ? (
                  'Suelta el fichero aqui'
                ) : (
                  <>
                    <Upload size={13} strokeWidth={2.75} /> Arrastra un fichero aqui (maximo 10 MB)
                  </>
                )}
              </div>
            )}

            {attachments?.map((item) => (
              <div key={item.id} className={styles.file}>
                <Paperclip size={12} strokeWidth={2.75} />
                <span style={{ flex: 1, minWidth: 0 }}>
                  {item.filename}
                  <span className={styles.commentMeta}> · {formatFileSize(item.sizeBytes)} · {item.uploadedBy.fullName}</span>
                </span>
                <Button variant="ghost" small onClick={() => attachmentMutations.download.mutate(item)}>
                  Descargar
                </Button>
                {(item.uploadedBy.id === user?.id || responsable) && (
                  <Button variant="ghost" small onClick={() => attachmentMutations.remove.mutate(item.id)}>
                    Borrar
                  </Button>
                )}
              </div>
            ))}
          </section>

          <div className={styles.ai}>
            <span className={styles.aiKicker}>
              <Sparkles size={12} strokeWidth={2.75} />
              PlanifIA puede ayudarte
            </span>
            <div className={styles.aiActions}>
              <Button small variant="secondary" onClick={() => pushToast('La ayuda con IA estara disponible proximamente')}>
                Desglosar en subtareas
              </Button>
              <Button small variant="secondary" onClick={() => pushToast('La ayuda con IA estara disponible proximamente')}>
                Resumir la discusion
              </Button>
            </div>
          </div>

          <section className={styles.history}>
            <h3 className={styles.sectionTitle}>Historial</h3>
            <span>Creada por {task.createdBy?.fullName ?? 'desconocido'} · {formatDateTime(task.createdAt)}</span>
            {task.updatedAt && <span>Ultimo cambio · {formatDateTime(task.updatedAt)}</span>}
            {task.aiGenerated && <Tag variant="accent" small>Generada por IA</Tag>}
          </section>
        </div>
      </aside>
    </>
  )
}
