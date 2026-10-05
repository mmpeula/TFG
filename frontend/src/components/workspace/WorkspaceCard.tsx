import type { ReactNode } from 'react'
import { CheckCircle2, Clock, PauseCircle, Users } from 'lucide-react'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { Tag } from '@/components/ui/Tag'
import { WorkspaceBadge } from './WorkspaceBadge'
import styles from './WorkspaceCard.module.css'
import { colorOf, lastActivityLabel } from '@/lib/workspace'
import type { WorkspaceResponse } from '@/types/api'

/**
 * Tarjeta de la pantalla inicial. Lleva solo lo que ayuda a decidir en cual
 * entrar: cuanto queda, cuanto se ha avanzado y si el grupo se ha movido.
 *
 * `alert` y `actions` son los dos huecos para las otras pantallas (el panel
 * docente y el menu de borrar/salir): quien no los pasa ve la tarjeta de
 * siempre. `actions` va fuera del boton y no dentro porque un boton no puede
 * contener otro.
 */
export function WorkspaceCard({
  workspace,
  onOpen,
  alert,
  actions,
}: {
  workspace: WorkspaceResponse
  onOpen: (workspace: WorkspaceResponse) => void
  alert?: ReactNode
  actions?: ReactNode
}) {
  const color = colorOf(workspace)
  const vacio = workspace.totalTasks === 0
  const terminado = !vacio && workspace.pendingTasks === 0

  return (
    <div className={styles.wrap}>
      {actions && <div className={styles.actions}>{actions}</div>}
      <button
        className={styles.card}
        onClick={() => onOpen(workspace)}
        disabled={workspace.enabled === false}
      >
        <span className={styles.rail} style={{ background: color }} aria-hidden="true" />

        <div className={styles.head}>
          <WorkspaceBadge workspace={workspace} className={styles.badge} />
          <span className={styles.title}>{workspace.name}</span>
        </div>

        {workspace.enabled === false && (
          <span className={styles.suspended}>
            <Tag variant="neutral" small>
              <PauseCircle size={11} strokeWidth={2.75} />
              Suspendido
            </Tag>
            Lo ha parado quien administra la plataforma. No se ha perdido nada.
          </span>
        )}

        {workspace.description && <p className={styles.description}>{workspace.description}</p>}

        <div className={styles.status}>
          {vacio ? (
            <span className={styles.pending}>Sin tareas todavía</span>
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
    </div>
  )
}
