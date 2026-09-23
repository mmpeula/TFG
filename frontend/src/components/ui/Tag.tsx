import type { ReactNode } from 'react'
import styles from './Tag.module.css'

interface Props {
  variant?: 'accent' | 'accent2' | 'neutral' | 'outline'
  small?: boolean
  children: ReactNode
  className?: string
  /** Texto que se ve al posar el raton, para el detalle que no cabe en la etiqueta. */
  title?: string
}

export function Tag({ variant = 'neutral', small, children, className, title }: Props) {
  const map = { accent: styles.accent, accent2: styles.accent2, neutral: styles.neutral, outline: styles.outline }
  return (
    <span
      className={[styles.tag, map[variant], small && styles.small, className].filter(Boolean).join(' ')}
      title={title}
    >
      {children}
    </span>
  )
}
