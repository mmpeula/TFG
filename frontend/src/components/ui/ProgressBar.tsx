import styles from './ProgressBar.module.css'

interface Props {
  value: number
  height?: number
  color?: string
  label?: string
}

export function ProgressBar({ value, height = 6, color = 'var(--color-accent-500)', label }: Props) {
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
      <div className={styles.fill} style={{ transform: `scaleX(${clamped / 100})`, background: color }} />
    </div>
  )
}
