import styles from './DropZone.module.css'

export function DropZone({ columnName }: { columnName: string }) {
  return <div className={styles.zone}>Suelta aqui para mover a {columnName}</div>
}
