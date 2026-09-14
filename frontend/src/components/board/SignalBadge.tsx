import styles from './SignalBadge.module.css'

/** Micro-badge de problema sobre la tarjeta: "6 d sin avance", "vence hoy"… */
export function SignalBadge({ text }: { text: string }) {
  return <span className={styles.badge}>{text}</span>
}
