import type { ReactNode } from 'react'
import { Card } from '@/components/ui/Card'
import styles from './Section.module.css'

interface Props {
  title: string
  subtitle?: string
  count?: number
  action?: ReactNode
  /**
   * Sin tarjeta, solo la cabecera: para cuando lo de dentro ya son tarjetas
   * (los insights del pulso) y meterlas en otra solo apilaria bordes.
   */
  plain?: boolean
  children: ReactNode
}

export function Section({ title, subtitle, count, action, plain, children }: Props) {
  const heading = (
    <>
      {title} {count !== undefined && <span className={styles.count}>{count}</span>}
    </>
  )

  if (plain) {
    return (
      <section className={styles.section}>
        <header className={styles.head}>
          <h2 className={styles.title}>{heading}</h2>
          {subtitle && <span className={styles.subtitle}>{subtitle}</span>}
          {action}
        </header>
        {children}
      </section>
    )
  }

  return (
    <Card title={heading} description={subtitle} aside={action}>
      {children}
    </Card>
  )
}
