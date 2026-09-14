import { Modal } from '@/components/ui/Modal'
import styles from './ShortcutsDialog.module.css'

const SHORTCUTS = [
  ['Nueva tarea', 'N'],
  ['Ir al plan', 'P'],
  ['Ir al tablero', 'B'],
  ['Ir al copiloto', 'C'],
  ['Ver el detalle del tablero', 'I'],
  ['Cerrar panel o modal', 'Esc'],
  ['Esta lista', '?'],
]

export function ShortcutsDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Modal open={open} title="Atajos de teclado" onClose={onClose}>
      <div className={styles.list}>
        {SHORTCUTS.map(([label, key]) => (
          <div key={key} className={styles.row}>
            <span>{label}</span>
            <span className={styles.kbd}>{key}</span>
          </div>
        ))}
      </div>
    </Modal>
  )
}
