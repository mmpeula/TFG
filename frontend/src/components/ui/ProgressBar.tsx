import styles from './ProgressBar.module.css'

interface Props {
  value: number
  height?: number
  color?: string
  label?: string
}

/** Las barras de porcentaje de toda la app van en el mismo azul; color solo para casos que no son un avance. */
export function ProgressBar({ value, height = 6, color = 'var(--color-accent-600)', label }: Props) {
  const clamped = Math.max(0, Math.min(100, value))
  return (
    <div
      className={styles.track}
      style={{ height }}
      role="progressbar"
      aria-valuenow={clamped}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={label}
    >
      <div className={styles.fill} style={{ width: `${clamped}%`, background: color }} />
    </div>
  )
}
