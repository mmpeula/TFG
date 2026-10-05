import { AlertTriangle } from 'lucide-react'
import styles from './SignalBadge.module.css'
import type { TaskWarning } from '@/lib/signals'

/**
 * Aviso sobre la tarjeta: «venció hace 2 d», «5 d sin avance», «sin responsable».
 * El ⚠ dice que es un aviso, no un dato; al pasar el raton se lee por que salta y
 * que hacer (lo mismo que dice el panel de la tarea al abrirla).
 */
export function SignalBadge({ warning }: { warning: TaskWarning }) {
  return (
    <span className={styles.badge} title={`${warning.detail} ${warning.action}`}>
      <AlertTriangle size={10} strokeWidth={2.5} aria-hidden="true" />
      {warning.short}
      <span className={styles.srOnly}>. {warning.detail} {warning.action}</span>
    </span>
  )
}
