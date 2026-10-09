import type { ReactNode } from 'react'
import styles from './ProgressDashboard.module.css'
import { PROGRESS_STATE_COLORS } from '@/lib/planStatus'

/**
 * Piezas comunes de los paneles de la app (el del docente y el panel de seguimiento, y el resumen
 * del equipo en el plan): asi todos se ven igual, con las mismas tarjetas de cifra
 * y los mismos paneles con titulo.
 */

/** Tarjeta de una cifra: titulo, valor grande y su unidad, con un icono en un circulo de color. */
export function KpiCard({
  label, icon, tone, value, unit, children,
}: {
  label: string
  icon: ReactNode
  tone: 'blue' | 'green' | 'amber' | 'red' | 'grey'
  value: string
  unit?: string
  /** Lo que va debajo, a todo el ancho (una barra, una variacion…). */
  children?: ReactNode
}) {
  return (
    <article className={styles.kpi}>
      <div className={styles.kpiMain}>
        <span className={styles.kpiLabel}>{label}</span>
        <span className={styles.kpiValue}>{value}</span>
        {unit && <span className={styles.kpiUnit}>{unit}</span>}
      </div>
      <span className={[styles.kpiIcon, styles[tone]].join(' ')} aria-hidden="true">{icon}</span>
      {children && <div className={styles.kpiExtra}>{children}</div>}
    </article>
  )
}

/** Panel blanco con su titulo y una linea que explica que se ve. */
export function DashPanel({
  title, subtitle, tools, children, className,
}: {
  title: string
  subtitle?: string
  /** Controles a la derecha del titulo (un selector, una leyenda…). */
  tools?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={[styles.panel, className].filter(Boolean).join(' ')}>
      <header className={styles.chartHead}>
        <div className={styles.panelHead}>
          <h3 className={styles.panelTitle}>{title}</h3>
          {subtitle && <p className={styles.panelSubtitle}>{subtitle}</p>}
        </div>
        {tools && <div className={styles.chartTools}>{tools}</div>}
      </header>
      {children}
    </section>
  )
}

/**
 * «Estado de las tareas»: la rueda con lo que queda por hacer, en progreso y
 * finalizado, en su panel. La usan el resumen del equipo y el panel del docente.
 */
export function StatusWheelPanel({ todo, doing, done }: { todo: number; doing: number; done: number }) {
  const total = todo + doing + done
  return (
    <DashPanel title="Estado de las tareas" className={styles.wheelPanel}>
      <StatusWheel
        avance={total > 0 ? Math.round((done / total) * 100) : 0}
        total={total}
        segments={[
          { label: 'Por hacer', value: todo, color: PROGRESS_STATE_COLORS.todo },
          { label: 'En progreso', value: doing, color: PROGRESS_STATE_COLORS.doing },
          { label: 'Finalizadas', value: done, color: PROGRESS_STATE_COLORS.done },
        ]}
      />
    </DashPanel>
  )
}

/**
 * Rueda del estado de las tareas: un anillo partido en tres tramos, cada uno
 * del tamaño de su parte, con el porcentaje terminado en el centro y la leyenda
 * con las cifras al lado (el color solo no basta para leerlo).
 */
function StatusWheel({
  segments, avance, total,
}: {
  segments: { label: string; value: number; color: string }[]
  avance: number
  total: number
}) {
  const radio = 42
  const vuelta = 2 * Math.PI * radio
  // Un hueco entre tramos para que se distingan aunque dos colores se parezcan.
  const hueco = segments.filter((segment) => segment.value > 0).length > 1 ? 2 : 0
  let recorrido = 0

  return (
    <div className={styles.wheel}>
        <svg viewBox="0 0 100 100" className={styles.wheelSvg} role="img"
          aria-label={segments.map((segment) => `${segment.label}: ${segment.value}`).join(', ')}>
          <circle cx="50" cy="50" r={radio} fill="none" stroke="var(--color-neutral-200)" strokeWidth="11" />
          {total > 0 && segments.map((segment) => {
            if (segment.value === 0) return null
            const largo = (segment.value / total) * vuelta
            const tramo = (
              <circle
                key={segment.label}
                cx="50" cy="50" r={radio}
                fill="none"
                stroke={segment.color}
                strokeWidth="11"
                strokeDasharray={`${Math.max(0, largo - hueco)} ${vuelta}`}
                strokeDashoffset={-recorrido}
                transform="rotate(-90 50 50)"
              />
            )
            recorrido += largo
            return tramo
          })}
          <text x="50" y="49" textAnchor="middle" className={styles.wheelValue}>{avance} %</text>
          <text x="50" y="62" textAnchor="middle" className={styles.wheelCaption}>finalizado</text>
        </svg>
        <ul className={styles.legend}>
          {segments.map((segment) => (
            <li key={segment.label}>
              <span className={styles.legendDot} style={{ background: segment.color }} aria-hidden="true" />
              <span className={styles.legendLabel}>{segment.label}</span>
              <span className={styles.legendValue}>{segment.value}</span>
            </li>
          ))}
          <li className={styles.legendTotal}>
            <span className={styles.legendLabel}>Total</span>
            <span className={styles.legendValue}>{total}</span>
          </li>
        </ul>
    </div>
  )
}
