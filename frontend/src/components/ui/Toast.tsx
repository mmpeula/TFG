import styles from './Toast.module.css'
import { useUi } from '@/state/context'

/** Pila de avisos abajo a la izquierda. 4 s, con un boton de accion cuando aplica. */
export function ToastStack() {
  const { toasts, dismissToast } = useUi()
  if (toasts.length === 0) return null

  return (
    <div className={styles.stack} role="status" aria-live="polite">
      {toasts.map((toast) => (
        <div key={toast.id} className={styles.toast}>
          <span>{toast.text}</span>
          {toast.action && (
            <button
              className={styles.action}
              onClick={() => {
                toast.action?.onClick()
                dismissToast(toast.id)
              }}
            >
              {toast.action.label}
            </button>
          )}
        </div>
      ))}
    </div>
  )
}
