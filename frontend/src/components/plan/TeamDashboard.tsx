import { CalendarRange, Clock3, PauseCircle } from 'lucide-react'
import { Avatar } from '@/components/ui/Avatar'
import { DashPanel, KpiCard, StatusWheelPanel } from '@/components/pulse/DashboardParts'
import { ProgressBar } from '@/components/ui/ProgressBar'
import styles from './TeamDashboard.module.css'
import { daysUntil, formatDate } from '@/lib/format'
import { isoDate, type Sprint } from '@/lib/sprints'
import { overdueDays, stalledDays, unassignedInProgress } from '@/lib/signals'
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
 * Con las mismas piezas que el panel del docente (DashboardParts), para que el
 * plan se vea igual para todos. Arriba, tres cifras: como va el sprint en curso,
 * cuantas tareas van tarde y cuantas estan atascadas. Debajo, la rueda del estado
 * de las tareas y una fila por persona con su avance, ordenadas para que lo que
 * necesita atencion (quien acumula retrasos) salga primero.
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
  const atrasadas = tasks.filter(atrasada).length
  const atascadas = tasks.filter(atascada).length

  const actual = sprints.find((sprint) => sprint.current)
  const quedan = actual ? daysUntil(isoDate(actual.end)) : null

  return (
    <section className={styles.dashboard} aria-label="Cómo va el equipo">
      {/* Una sola fila arriba: el estado de las tareas y las tres cifras, todas de la misma
          altura; debajo, la tabla por persona a todo el ancho. Asi no quedan huecos. */}
      <div className={styles.top}>
        <StatusWheelPanel todo={porHacer} doing={enProgreso} done={hechas} />
        <KpiCard
          label={actual ? `Sprint ${actual.number}, en curso` : 'Sprint en curso'}
          icon={<CalendarRange />}
          tone="blue"
          value={actual ? `${actual.terminadas.length}/${actual.tasks.length}` : '—'}
          unit={actual ? 'tareas terminadas' : 'hoy no cae en ningún sprint'}
        >
          {actual && (
            <>
              <ProgressBar value={actual.progress} height={8} label="Avance del sprint en curso" />
              <span className={styles.kpiNote}>
                {quedan === 0 ? 'termina hoy' : `quedan ${quedan} ${quedan === 1 ? 'día' : 'días'}`} · hasta {formatDate(isoDate(actual.end))}
              </span>
            </>
          )}
        </KpiCard>
        <KpiCard
          label="Van tarde"
          icon={<Clock3 />}
          tone="red"
          value={String(atrasadas)}
          unit={atrasadas === 0 ? 'todo dentro de plazo' : 'con la fecha límite ya pasada'}
        />
        <KpiCard
          label="Atascadas"
          icon={<PauseCircle />}
          tone="amber"
          value={String(atascadas)}
          unit={atascadas === 0 ? 'nada parado' : 'sin avance o sin responsable'}
        />
      </div>

      <DashPanel title="Por persona" subtitle="Avance y tareas de cada miembro del equipo">
        <TeamPeopleTable tasks={tasks} columns={columns} members={members} />
      </DashPanel>
    </section>
  )
}


/**
 * Una fila por persona con su avance y sus tareas por estado, ordenadas para que
 * salga primero quien acumula retrasos; al final, las tareas sin responsable.
 */
function TeamPeopleTable({
  tasks, columns, members,
}: {
  tasks: TaskResponse[]
  columns: ColumnResponse[]
  members: TeamMemberResponse[]
}) {
  const ultima = columns[columns.length - 1]?.id
  const primera = columns[0]?.id
  const hecha = (task: TaskResponse) => task.columnId === ultima
  const atrasada = (task: TaskResponse) => (overdueDays(task, columns) ?? 0) < 0

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
  )
}
