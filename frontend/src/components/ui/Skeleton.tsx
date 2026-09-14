import styles from './Skeleton.module.css'

const WIDTHS = ['70%', '92%', '48%']

interface Props {
  label?: string
  lines?: number
}

/** Esqueleto de carga: tres barras y un punto terracota que late. */
export function Skeleton({ label, lines = 3 }: Props) {
  return (
    <div className={styles.block} aria-live="polite" aria-busy="true">
      {label && (
        <div className={styles.row}>
          <span className={styles.dot} />
          <span className={styles.label}>{label}</span>
        </div>
      )}
      {Array.from({ length: lines }).map((_, index) => (
        <div key={index} className={styles.bar} style={{ width: WIDTHS[index % WIDTHS.length] }} />
      ))}
    </div>
  )
}
