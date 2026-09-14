import type { ReactNode } from 'react'
import styles from './Section.module.css'

interface Props {
  title: string
  subtitle?: string
  count?: number
  action?: ReactNode
  children: ReactNode
}

export function Section({ title, subtitle, count, action, children }: Props) {
  return (
    <section className={styles.section}>
      <header className={styles.head}>
        <h2 className={styles.title}>
          {title} {count !== undefined && <span className={styles.count}>{count}</span>}
        </h2>
        {subtitle && <span className={styles.subtitle}>{subtitle}</span>}
        {action}
      </header>
      {children}
    </section>
  )
}
