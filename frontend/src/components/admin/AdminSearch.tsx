import { Search } from 'lucide-react'
import styles from './AdminPanel.module.css'

/** El buscador de los dos paneles de administracion: cuentas y espacios de trabajo. */
export function AdminSearch({
  value,
  onChange,
  placeholder,
  label,
}: {
  value: string
  onChange: (value: string) => void
  placeholder: string
  label: string
}) {
  return (
    <div className={styles.searchBox}>
      <Search size={14} strokeWidth={2.75} aria-hidden="true" />
      <input
        className={styles.searchInput}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        aria-label={label}
      />
    </div>
  )
}
