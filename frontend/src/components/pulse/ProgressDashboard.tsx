import type { ReactNode } from 'react'
import { CalendarDays, ChevronDown, Clock3, FileText, TrendingDown, TrendingUp, Zap } from 'lucide-react'
import { Dropdown, DropdownItem } from '@/components/ui/Controls'
import { DashPanel, KpiCard, StatusWheelPanel } from './DashboardParts'
import styles from './ProgressDashboard.module.css'
import { decimal } from '@/lib/format'
import { formatCycle, SEMANAS_POSIBLES, type StatusKind } from '@/lib/metrics'
import type { derivePulse } from '@/lib/metrics'

type Pulse = ReturnType<typeof derivePulse>

/** Tareas en las columnas de ese estado (por hacer, en curso o hechas). */
function countOf(pulse: Pulse, kind: StatusKind): number {
  return pulse.byStatus.filter((row) => row.kind === kind).reduce((sum, row) => sum + row.value, 0)
}

/**
 * Como va un espacio de trabajo, de un vistazo: lo que ve el docente al entrar (y
 * cualquiera en Pulso). Todo sale del tablero real (lib/metrics.ts).
 *
 * Arriba, la rueda del estado de las tareas (la misma del resumen del equipo) y
 * las cifras: velocidad y tiempo medio con su variacion frente a la semana
 * anterior, y cuantas tareas quedan pendientes. Las que tienen aviso las
 * cuenta el insight «Avisos», que ademas dice cuales son. Debajo, la
 * evolucion semanal de creadas frente a completadas. Las
 * variaciones van en verde si mejoran y en rojo si empeoran (mas
 * velocidad es mejor; mas tiempo por tarea, peor).
 */
export function ProgressDashboard({
  pulse,
  weeks,
  onWeeksChange,
  hideCycleTime,
  children,
}: {
  pulse: Pulse
  weeks: number
  onWeeksChange: (weeks: number) => void
  /** Sin la tarjeta de tiempo medio por tarea: la fila de arriba queda con una tarjeta menos. */
  hideCycleTime?: boolean
  /** Lo que va debajo del panel (los insights en Pulso, por ejemplo). */
  children?: ReactNode
}) {
  return (
    <div className={styles.dashboard}>
      <div className={[styles.kpisTop, hideCycleTime && styles.kpisTopThree].filter(Boolean).join(' ')}>
        {/* La misma rueda que el resumen del equipo del responsable: ya dice el porcentaje
            finalizado, asi que no hace falta otra tarjeta de progreso. */}
        <StatusWheelPanel
          todo={countOf(pulse, 'todo')}
          doing={countOf(pulse, 'doing')}
          done={countOf(pulse, 'done')}
        />
        <KpiCard label="Velocidad" icon={<Zap />} tone="green" value={decimal(pulse.velocity)} unit="tareas / semana">
          <Delta value={pulse.velocityDelta} higherIsBetter format={(n) => decimal(n)} />
        </KpiCard>
        {!hideCycleTime && (
          <KpiCard
            label="Tiempo medio"
            icon={<Clock3 />}
            tone="amber"
            value={formatCycle(pulse.cycleDays, pulse.cycleSamples)}
            unit="por tarea"
          >
            <Delta value={pulse.cycleDelta} higherIsBetter={false} format={(n) => `${decimal(n)} d`} />
          </KpiCard>
        )}
        <KpiCard label="Pendientes" icon={<FileText />} tone="grey" value={String(pulse.pending)} unit="tareas sin terminar" />
      </div>

      <WeeklyChart series={pulse.weekly} weeks={weeks} onWeeksChange={onWeeksChange} />

      {children}
    </div>
  )
}

/** «↗ +0,8 vs. semana anterior», en verde si mejora y en rojo si empeora. */
function Delta({
  value, higherIsBetter, format,
}: {
  value: number | null
  higherIsBetter: boolean
  format: (n: number) => string
}) {
  if (value === null || Math.abs(value) < 0.05) {
    return <span className={styles.delta}><span className={styles.deltaFlat}>{value === null ? 'sin datos de la semana anterior' : 'igual que la semana anterior'}</span></span>
  }
  const mejora = higherIsBetter ? value > 0 : value < 0
  const Icon = value > 0 ? TrendingUp : TrendingDown
  return (
    <span className={styles.delta}>
      <span className={[styles.deltaValue, mejora ? styles.good : styles.bad].join(' ')}>
        <Icon size={15} strokeWidth={2.5} aria-hidden="true" />
        {value > 0 ? '+' : '−'}{format(Math.abs(value))}
      </span>
      <span className={styles.deltaLabel}>vs. semana anterior</span>
    </span>
  )
}

/** Escala del eje: un paso «redondo» (1, 2, 5, 10…) que deje unas tres lineas. */
function niceStep(max: number): number {
  const raw = Math.max(1, max / 3)
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const steps = [1, 2, 5, 10].map((n) => n * magnitude)
  return steps.find((step) => step >= raw) ?? 10 * magnitude
}

function WeeklyChart({
  series, weeks, onWeeksChange,
}: {
  series: Pulse['weekly']
  weeks: number
  onWeeksChange: (weeks: number) => void
}) {
  const max = Math.max(1, ...series.flatMap((item) => [item.done, item.created]))
  const step = niceStep(max)
  const top = Math.ceil(max / step) * step
  const ticks = Array.from({ length: top / step + 1 }, (_, i) => top - i * step)

  return (
    <DashPanel
      title="Evolución temporal"
      subtitle="Tareas creadas y completadas por semana"
      tools={
        <>
          <Dropdown
            trigger={({ toggle }) => (
              <button type="button" className={styles.range} onClick={toggle}>
                <CalendarDays size={14} strokeWidth={2} aria-hidden="true" />
                Últimas {weeks} semanas
                <ChevronDown size={14} strokeWidth={2.5} aria-hidden="true" />
              </button>
            )}
          >
            {(close) => (
              <>
                {SEMANAS_POSIBLES.map((n) => (
                  <DropdownItem key={n} active={n === weeks} onClick={() => { close(); onWeeksChange(n) }}>
                    Últimas {n} semanas
                  </DropdownItem>
                ))}
              </>
            )}
          </Dropdown>
          <span className={styles.legendItem}><span className={styles.dot} style={{ background: 'var(--color-accent-500)' }} />Completadas</span>
          <span className={styles.legendItem}><span className={styles.dot} style={{ background: 'var(--color-accent-2-400)' }} />Creadas</span>
        </>
      }
    >
      <div className={styles.chart}>
        <div className={styles.axis} aria-hidden="true">
          {ticks.map((tick) => <span key={tick}>{tick}</span>)}
        </div>
        <div className={styles.plot}>
          <div className={styles.gridLines} aria-hidden="true">
            {ticks.map((tick) => <span key={tick} />)}
          </div>
          <div className={styles.columns}>
            {series.map((item) => (
              <div key={item.week} className={styles.column}>
                <div className={styles.bars}>
                  <span className={styles.bar} style={{ height: `${(item.done / top) * 100}%`, background: 'var(--color-accent-500)' }}
                    title={`${item.done} completadas en ${item.week}`} />
                  <span className={styles.bar} style={{ height: `${(item.created / top) * 100}%`, background: 'var(--color-accent-2-400)' }}
                    title={`${item.created} creadas en ${item.week}`} />
                </div>
                <span className={[styles.week, item.current && styles.weekNow].filter(Boolean).join(' ')}>{item.week}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </DashPanel>
  )
}
