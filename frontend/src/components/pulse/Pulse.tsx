import { Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ProgressBar } from '@/components/ui/ProgressBar'
import styles from './Pulse.module.css'

/** Tarjeta de KPI: cifra grande, variacion y borde de alerta opcional. */
export function KpiCard({
  label,
  value,
  delta,
  better,
  unit,
  alert,
}: {
  label: string
  value: string
  delta?: string
  better?: boolean
  unit?: string
  alert?: boolean
}) {
  return (
    <article className={[styles.kpi, alert && styles.kpiAlert].filter(Boolean).join(' ')}>
      <span className={styles.kpiLabel}>{label}</span>
      <span className={styles.kpiValue}>{value}</span>
      {delta && (
        <span className={[styles.kpiDelta, better ? styles.better : styles.worse].join(' ')}>{delta}</span>
      )}
      {unit && <span className={styles.kpiUnit}>{unit}</span>}
    </article>
  )
}

interface Serie {
  week: string
  done: number
  created: number
  current?: boolean
}

export function BarChart({ series, title }: { series: Serie[]; title: string }) {
  const max = Math.max(...series.flatMap((item) => [item.done, item.created]))

  return (
    <div className={styles.chart}>
      <header className={styles.chartHead}>
        <h3 className={styles.panelTitle}>{title}</h3>
        <div className={styles.legend}>
          <span className={styles.legendItem}>
            <span className={styles.swatch} style={{ background: 'var(--color-accent-500)' }} />
            Completadas
          </span>
          <span className={styles.legendItem}>
            <span className={styles.swatch} style={{ background: 'var(--color-accent-2-400)' }} />
            Creadas
          </span>
        </div>
      </header>

      <div className={styles.grid}>
        {series.map((item) => (
          <div key={item.week} className={[styles.col, item.current && styles.current].filter(Boolean).join(' ')}>
            <div className={styles.bars}>
              <span
                className={styles.bar}
                style={{ height: `${(item.done / max) * 100}%`, background: 'var(--color-accent-500)' }}
                title={`${item.done} completadas en ${item.week}`}
              />
              <span
                className={styles.bar}
                style={{ height: `${(item.created / max) * 100}%`, background: 'var(--color-accent-2-400)' }}
                title={`${item.created} creadas en ${item.week}`}
              />
            </div>
            <span className={styles.week}>{item.week}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

export function DistributionBars({
  title,
  rows,
}: {
  title: string
  rows: { label: string; value: number; max: number }[]
}) {
  return (
    <div className={styles.panel}>
      <h3 className={styles.panelTitle}>{title}</h3>
      {rows.map((row) => (
        <div key={row.label} className={styles.distRow}>
          <span className={styles.distLabel}>{row.label}</span>
          <span className={styles.distValue}>{row.value}</span>
          <span className={styles.distBar}>
            <ProgressBar value={(row.value / row.max) * 100} height={6} />
          </span>
        </div>
      ))}
    </div>
  )
}

export function PriorityBar({
  title,
  segments,
}: {
  title: string
  segments: { label: string; value: number; color: string }[]
}) {
  const total = segments.reduce((sum, segment) => sum + segment.value, 0)

  return (
    <div className={styles.panel}>
      <h3 className={styles.panelTitle}>{title}</h3>
      <div className={styles.segments}>
        {segments.map((segment) => (
          <span
            key={segment.label}
            className={styles.segment}
            style={{ width: `${(segment.value / total) * 100}%`, background: segment.color }}
            title={`${segment.label}: ${segment.value}`}
          />
        ))}
      </div>
      <div className={styles.priorityLegend}>
        {segments.map((segment) => (
          <span key={segment.label} className={styles.priorityItem}>
            <span className={styles.priorityDot} style={{ background: segment.color }} />
            {segment.label} · {segment.value}
          </span>
        ))}
      </div>
    </div>
  )
}

export function InsightCard({
  variant,
  kicker,
  title,
  body,
  actions,
  onAction,
}: {
  variant: 'risk' | 'block' | 'flow'
  kicker: string
  title: string
  body: string
  actions: { label: string; primary: boolean }[]
  onAction?: (label: string) => void
}) {
  return (
    <article className={[styles.insight, styles[variant]].join(' ')}>
      <span className={styles.insightKicker}>
        <Sparkles size={12} strokeWidth={2.75} />
        {kicker}
      </span>
      <h3 className={styles.insightTitle}>{title}</h3>
      <p className={styles.insightBody}>{body}</p>
      <div className={styles.insightActions}>
        {actions.map((action) => (
          <Button
            key={action.label}
            small
            onClick={() => onAction?.(action.label)}
            variant={
              variant === 'risk'
                ? action.primary
                  ? 'onDark'
                  : 'onDarkGhost'
                : action.primary
                  ? 'primary'
                  : 'secondary'
            }
          >
            {action.label}
          </Button>
        ))}
      </div>
    </article>
  )
}
