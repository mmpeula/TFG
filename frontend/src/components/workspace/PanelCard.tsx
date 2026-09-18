import type { ReactNode } from 'react'
// Mismo cascaron que WorkspaceCard (tarjeta, cabecera, insignia, descripcion):
// esta tarjeta no es un workspace, pero vive en la misma cuadricula y tiene
// que leerse como una mas, no como un aparte.
import styles from './WorkspaceCard.module.css'

/**
 * Tarjeta de acceso a un panel de la aplicacion (docente, administracion...),
 * mezclada en la misma cuadricula que "Mis workspaces" en vez de escondida en
 * un boton de la cabecera. La insignia va siempre en el mismo gris, nunca en
 * uno de los colores de workspace: as si se distingue de un vistazo que no es
 * una asignatura.
 */
export function PanelCard({
  icon,
  title,
  description,
  onOpen,
}: {
  icon: ReactNode
  title: string
  description: string
  onOpen: () => void
}) {
  return (
    <button className={styles.card} onClick={onOpen}>
      <div className={styles.head}>
        <span className={styles.badge} style={{ background: 'var(--color-neutral-800)' }} aria-hidden="true">
          {icon}
        </span>
        <span className={styles.title}>{title}</span>
      </div>
      <p className={styles.description}>{description}</p>
    </button>
  )
}
