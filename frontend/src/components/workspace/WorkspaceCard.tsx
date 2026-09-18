import type { ReactNode } from 'react'
import { CheckCircle2, Clock, Users } from 'lucide-react'
import { ProgressBar } from '@/components/ui/ProgressBar'
import styles from './WorkspaceCard.module.css'
import { colorOf, iconOf, lastActivityLabel } from '@/lib/workspace'
import type { WorkspaceResponse } from '@/types/api'

/**
 * Tarjeta de la pantalla inicial. Lleva solo lo que ayuda a decidir en cual
 * entrar: cuanto queda, cuanto se ha avanzado y si el grupo se ha movido.
 *
 * `alert` es el unico hueco pensado para otra pantalla (el panel docente):
 * el resto de usos no lo pasan y la tarjeta queda igual que siempre.
 */
export function WorkspaceCard({
  workspace,
  onOpen,
  alert,
}: {
  workspace: WorkspaceResponse
  onOpen: (workspace: WorkspaceResponse) => void
  alert?: ReactNode
}) {
  const color = colorOf(workspace)
  const vacio = workspace.totalTasks === 0
  const terminado = !vacio && workspace.pendingTasks === 0

  return (
    <button className={styles.card} onClick={() => onOpen(workspace)}>
      <span className={styles.rail} style={{ background: color }} aria-hidden="true" />

      <div className={styles.head}>
        <span className={styles.badge} style={{ background: color }} aria-hidden="true">
          {iconOf(workspace)}
        </span>
        <span className={styles.title}>{workspace.name}</span>
      </div>

      {workspace.description && <p className={styles.description}>{workspace.description}</p>}

      <div className={styles.status}>
        {vacio ? (
          <span className={styles.pending}>Sin tareas todavia</span>
        ) : terminado ? (
          <span className={styles.done}>
            <CheckCircle2 size={13} strokeWidth={2.75} />
            Todo hecho
          </span>
        ) : (
          <span className={styles.pending}>
            {workspace.pendingTasks} {workspace.pendingTasks === 1 ? 'tarea pendiente' : 'tareas pendientes'}
          </span>
        )}
        {!vacio && <span className={styles.percent}>{workspace.progress}%</span>}
      </div>

      <ProgressBar value={workspace.progress} height={6} color={color} label={`Progreso de ${workspace.name}`} />

      <div className={styles.foot}>
        <span className={styles.meta}>
          <Users size={12} strokeWidth={2.75} />
          {workspace.memberCount}
        </span>
        <span className={styles.meta}>
          <Clock size={12} strokeWidth={2.75} />
          {lastActivityLabel(workspace.lastActivityAt)}
        </span>
        {alert && <span className={styles.footAlert}>{alert}</span>}
      </div>
    </button>
  )
}
