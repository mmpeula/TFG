import { Modal } from '@/components/ui/Modal'
import styles from './ShortcutsDialog.module.css'

/**
 * Los atajos que funcionan aqui y ahora: «Nueva tarea» solo en el tablero y para quien
 * puede crear, y «Generar plan» solo para quien administra el espacio. Ensenar uno que
 * no hace nada al pulsarlo es peor que no ensenarlo.
 */
export function ShortcutsDialog({
  open,
  onClose,
  canCreateTask,
  canGeneratePlan,
}: {
  open: boolean
  onClose: () => void
  canCreateTask: boolean
  canGeneratePlan: boolean
}) {
  const shortcuts = [
    ...(canCreateTask ? [['Nueva tarea', 'N']] : []),
    ['Ir al plan', 'P'],
    ['Ir al tablero', 'B'],
    ...(canGeneratePlan ? [['Ir a generar plan', 'G']] : []),
    ['Ver el detalle del tablero', 'I'],
    ['Cerrar panel o modal', 'Esc'],
    ['Esta lista', '?'],
  ]

  return (
    <Modal open={open} title="Atajos de teclado" onClose={onClose}>
      <div className={styles.list}>
        {shortcuts.map(([label, key]) => (
          <div key={key} className={styles.row}>
            <span>{label}</span>
            <span className={styles.kbd}>{key}</span>
          </div>
        ))}
      </div>
    </Modal>
  )
}
