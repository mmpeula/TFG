import { Avatar } from '@/components/ui/Avatar'
import { ProgressBar } from '@/components/ui/ProgressBar'
import styles from './TeamDashboard.module.css'
import { daysUntil, formatDate } from '@/lib/format'
import { isoDate, type Sprint } from '@/lib/sprints'
import { overdueDays, stalledDays, unassignedInProgress } from '@/lib/signals'
import { STATE_COLORS } from '@/lib/planStatus'
import type { ColumnResponse, TaskResponse, TeamMemberResponse, UserSummary } from '@/types/api'

interface PersonStats {
  user: UserSummary | null
  total: number
  pendientes: number
  enCurso: number
  hechas: number
  atrasadas: number
}

/**
 * Como va el equipo, de un vistazo: lo que ve el responsable (o el docente) al
 * elegir «Todo el equipo» en el plan, encima de los sprints.
 *
 * Arriba, la rueda del estado de todas las tareas (por hacer, en progreso,
 * finalizadas) y tres cifras: como va el sprint en curso, cuantas tareas van
 * tarde y cuantas estan atascadas. Debajo, una fila por
 * persona con su avance y sus tareas por estado, ordenadas para que lo que necesita
 * atencion (quien acumula retrasos) salga primero.
 *
 * Todo sale de las mismas tareas que se pintan debajo, asi que las cifras no
 * pueden discrepar de lo que se ve. Los colores siguen su trabajo fijo: verde lo
 * terminado o al dia, rojo lo que va tarde, ambar lo que pide atencion.
 */
export function TeamDashboard({
  tasks,
  columns,
  members,
  sprints,
}: {
  tasks: TaskResponse[]
  columns: ColumnResponse[]
  members: TeamMemberResponse[]
  sprints: Sprint[]
}) {
  const ultima = columns[columns.length - 1]?.id
  const primera = columns[0]?.id
  const hecha = (task: TaskResponse) => task.columnId === ultima
  // Las mismas reglas que los avisos de las tarjetas (lib/signals). «Tarde» es lo ya vencido;
  // «Atascada» se mira aparte, asi que una tarea vencida y ademas parada cuenta en las dos.
  const atrasada = (task: TaskResponse) => (overdueDays(task, columns) ?? 0) < 0
  const atascada = (task: TaskResponse) =>
    stalledDays(task, columns) !== null || unassignedInProgress(task, columns)

  const total = tasks.length
  const hechas = tasks.filter(hecha).length
  const porHacer = tasks.filter((task) => task.columnId === primera).length
  const enProgreso = total - hechas - porHacer
  const avance = total > 0 ? Math.round((hechas / total) * 100) : 0
  const atrasadas = tasks.filter(atrasada).length
  const atascadas = tasks.filter(atascada).length

  const actual = sprints.find((sprint) => sprint.current)
  const quedan = actual ? daysUntil(isoDate(actual.end)) : null

  function statsOf(user: UserSummary | null, suyas: TaskResponse[]): PersonStats {
    return {
      user,
      total: suyas.length,
      pendientes: suyas.filter((task) => task.columnId === primera).length,
      enCurso: suyas.filter((task) => task.columnId !== primera && !hecha(task)).length,
      hechas: suyas.filter(hecha).length,
      atrasadas: suyas.filter(atrasada).length,
    }
  }

  const personas = members
    .filter((member) => member.role !== 'DOCENTE')
    .map((member) => statsOf(member.user, tasks.filter((task) => task.assignees.some((a) => a.id === member.user.id))))
  const sinAsignar = tasks.filter((task) => task.assignees.length === 0)
  const filas = [
    ...personas.sort((a, b) =>
      b.atrasadas - a.atrasadas || (b.total - b.hechas) - (a.total - a.hechas)),
    ...(sinAsignar.length > 0 ? [statsOf(null, sinAsignar)] : []),
  ]

  return (
    <section className={styles.dashboard} aria-label="Cómo va el equipo">
      <div className={styles.kpis}>
        <StatusWheel
          avance={avance}
          total={total}
          segments={[
            { label: 'Por hacer', value: porHacer, color: STATE_COLORS['sin-empezar'].color },
            { label: 'En progreso', value: enProgreso, color: STATE_COLORS['en-curso'].color },
            { label: 'Finalizadas', value: hechas, color: STATE_COLORS.terminada.color },
          ]}
        />
        <Kpi
          label={actual ? `Sprint ${actual.number}, en curso` : 'Sprint en curso'}
          value={actual ? `${actual.terminadas.length}/${actual.tasks.length}` : '—'}
          detail={
            actual
              ? quedan === 0 ? 'termina hoy' : `quedan ${quedan} ${quedan === 1 ? 'día' : 'días'} · hasta ${formatDate(isoDate(actual.end))}`
              : 'hoy no cae en ningún sprint'
          }
          progress={actual?.progress}
        />
        <Kpi
          label="Van tarde"
          value={String(atrasadas)}
          detail={atrasadas === 0 ? 'todo dentro de plazo' : 'fecha límite ya pasada'}
          tone={atrasadas > 0 ? 'bad' : 'good'}
        />
        <Kpi
          label="Atascadas"
          value={String(atascadas)}
          detail={atascadas === 0 ? 'nada parado' : 'sin avance o sin responsable'}
          tone={atascadas > 0 ? 'warn' : 'good'}
        />
      </div>

      <div className={styles.people}>
        <div className={styles.peopleHead} aria-hidden="true">
          <span>Persona</span>
          <span>Avance</span>
          <span className={styles.num}>Por hacer</span>
          <span className={styles.num}>En curso</span>
          <span className={styles.num}>Hechas</span>
          <span className={styles.num}>Tarde</span>
        </div>
        {filas.map((fila) => {
          const pct = fila.total > 0 ? Math.round((fila.hechas / fila.total) * 100) : 0
          return (
            <div key={fila.user?.id ?? 'sin-asignar'} className={styles.row}>
              <span className={styles.person}>
                <Avatar user={fila.user} avatarUrl={fila.user?.avatarUrl} size={26} />
                <span className={styles.name}>{fila.user?.fullName ?? 'Sin asignar'}</span>
              </span>
              <span className={styles.progress}>
                {fila.total === 0 ? (
                  <span className={styles.none}>sin tareas</span>
                ) : (
                  <>
                    <ProgressBar
                      value={pct}
                      height={4}
                      color={pct === 100 ? 'var(--color-accent-2-500)' : 'var(--color-neutral-700)'}
                      label={`Avance de ${fila.user?.fullName ?? 'las tareas sin asignar'}`}
                    />
                    <span className={styles.pct}>{pct} %</span>
                  </>
                )}
              </span>
              <span className={styles.num} data-label="Por hacer">{fila.pendientes}</span>
              <span className={styles.num} data-label="En curso">{fila.enCurso}</span>
              <span className={styles.num} data-label="Hechas">{fila.hechas}</span>
              <span className={[styles.num, fila.atrasadas > 0 && styles.bad].filter(Boolean).join(' ')} data-label="Tarde">
                {fila.atrasadas}
              </span>
            </div>
          )
        })}
      </div>
    </section>
  )
}

function Kpi({
  label, value, detail, tone, progress,
}: {
  label: string
  value: string
  detail: string
  tone?: 'good' | 'bad' | 'warn'
  progress?: number
}) {
  return (
    <div className={styles.kpi}>
      <span className={styles.kpiLabel}>{label}</span>
      <span className={[styles.kpiValue, tone && styles[tone]].filter(Boolean).join(' ')}>{value}</span>
      {progress !== undefined && (
        <ProgressBar
          value={progress}
          height={3}
          color={progress === 100 ? 'var(--color-accent-2-500)' : 'var(--color-neutral-700)'}
          label={label}
        />
      )}
      <span className={styles.kpiDetail}>{detail}</span>
    </div>
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
    <div className={[styles.kpi, styles.wheelCard].join(' ')}>
      <span className={styles.kpiLabel}>Estado de las tareas</span>
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
    </div>
  )
}
