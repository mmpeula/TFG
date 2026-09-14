import { useEffect, useRef, useState } from 'react'

import styles from './TaskComposer.module.css'

/**
 * Composer en linea dentro de la columna (no un modal): Enter crea y deja el
 * composer abierto para la siguiente, Esc cierra.
 */
export function TaskComposer({
  onCreate,
  onClose,
}: {
  onCreate: (title: string) => void
  onClose: () => void
}) {
  const [value, setValue] = useState('')
  const ref = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    ref.current?.focus()
  }, [])

  return (
    <div className={styles.composer}>
      <textarea
        ref={ref}
        className={styles.input}
        rows={2}
        value={value}
        placeholder="Titulo de la tarea"
        onChange={(event) => setValue(event.target.value)}
        onBlur={() => !value && onClose()}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey) {
            event.preventDefault()
            if (value.trim()) {
              onCreate(value.trim())
              setValue('')
            }
          }
          if (event.key === 'Escape') {
            event.preventDefault()
            onClose()
          }
        }}
      />
      <span className={styles.hint}>Enter crea y sigue · Esc cierra</span>
    </div>
  )
}
