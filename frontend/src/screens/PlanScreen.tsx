import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { CalendarRange, Plus, Users } from 'lucide-react'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { PlanTaskCard } from '@/components/plan/PlanTaskCard'
import { TaskPanel } from '@/components/task/TaskPanel'
import { Button } from '@/components/ui/Button'
import { Segmented } from '@/components/ui/Controls'
import { EmptyState } from '@/components/ui/EmptyState'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { Skeleton } from '@/components/ui/Skeleton'
import styles from './PlanScreen.module.css'
import { useBoard, useProjectBoards, useTeamMembers, useWorkspace } from '@/hooks/useData'
import { buildPlan, DEFAULT_SPRINT_DAYS, isoDate, sprintRange, type Sprint } from '@/lib/sprints'
import { STATE_COLORS } from '@/lib/planStatus'
import { canWrite } from '@/lib/roles'
import { formatDate } from '@/lib/format'
import { useAuth } from '@/state/context'
import { useMediaQuery } from '@/hooks/useMediaQuery'
import { ROUTES } from '@/app/routes'
import type { ColumnResponse, TaskResponse } from '@/types/api'

/** Quien vale para ver: por defecto, solo lo tuyo. */
const ALCANCES = [
  { value: 'mine', label: 'Mis tareas' },
  { value: 'team', label: 'Todo el equipo' },
]

const GUARDADO_ALCANCE = 'planifia.planScope'

function leerGuardado(clave: string, porDefecto: string): string {
  try {
    return localStorage.getItem(clave) ?? porDefecto
  } catch {
    return porDefecto
  }
}

function guardar(clave: string, valor: string) {
  try {
    localStorage.setItem(clave, valor)
  } catch {
    // Sin almacenamiento (ventana privada) se pierde la preferencia y ya esta.
  }
}

/**
 * El trabajo repartido en el tiempo.
 *
 * El tablero responde a «que estoy haciendo ahora»; esta pantalla responde a
 * «llegamos». Cada sprint es una columna con sus tareas en una unica lista,
 * ordenada por fecha: no hay bandas ni columnas separadas por estado, que se ve
 * solo en la insignia de cada tarjeta (color y texto). Por defecto solo se ve
 * lo propio, que es la pregunta que un estudiante se hace de verdad al abrir
 * esto.
 *
 * La duracion del sprint no se elige aqui: se fija una vez al crear el
 * workspace (y se puede cambiar en sus ajustes), para que todos los sprints
 * del plan midan lo mismo en vez de recalcularse cada vez que alguien entra.
 */
export function PlanScreen() {
  const { workspaceId: param } = useParams()
  const workspaceId = Number(param)
  const navigate = useNavigate()
  const { user } = useAuth()
  const isMobile = useMediaQuery('(max-width: 767px)')

  const { data: workspace } = useWorkspace(workspaceId)
  const { data: boards, isPending: loadingBoards } = useProjectBoards(workspaceId)
  const { data: detail, isPending } = useBoard(boards?.[0]?.id)
  const { data: members } = useTeamMembers(workspace?.teamId)

  // El alcance elegido se recuerda: nadie quiere volver a fijarlo cada vez.
  const [scope, setScope] = useState<'mine' | 'team'>(
    () => (leerGuardado(GUARDADO_ALCANCE, 'mine') as 'mine' | 'team') ?? 'mine',
  )
  const [mobileSprint, setMobileSprint] = useState<number | null>(null)
  const [openTask, setOpenTask] = useState<TaskResponse | null>(null)

  const columns = useMemo(() => columnsOf(detail?.columns), [detail])
  const tasks = useMemo(() => columns.flatMap((column) => column.tasks ?? []), [columns])
  const visibleTasks = useMemo(
    () => (scope === 'mine' ? tasks.filter((task) => task.assignee?.id === user?.id) : tasks),
    [tasks, scope, user?.id],
  )
  const writable = canWrite(detail?.myRole ?? null)

  const plan = useMemo(
    () =>
      buildPlan(visibleTasks, columns, {
        start: workspace?.startDate,
        end: workspace?.deadline,
        sprintDays: workspace?.sprintDays ?? DEFAULT_SPRINT_DAYS,
      }),
    [visibleTasks, columns, workspace?.startDate, workspace?.deadline, workspace?.sprintDays],
  )

  function elegirAlcance(value: string) {
    setScope(value as 'mine' | 'team')
    guardar(GUARDADO_ALCANCE, value)
    setMobileSprint(null)
  }

  // En movil se ve un sprint cada vez; por defecto, el que esta en marcha ahora.
  const sprintActualIndex = plan.sprints.findIndex((sprint) => sprint.current)
  const indiceMovil = Math.min(
    mobileSprint ?? (sprintActualIndex >= 0 ? sprintActualIndex : 0),
    Math.max(0, plan.sprints.length - 1),
  )
  const sprintsVisibles = isMobile ? plan.sprints.filter((_, index) => index === indiceMovil) : plan.sprints

  const sinTareasEnAbsoluto = tasks.length === 0
  const sinTareasPropias = !sinTareasEnAbsoluto && scope === 'mine' && visibleTasks.length === 0

  return (
    <WorkspaceShell workspace={workspace} workspaceId={workspaceId}>
      {loadingBoards || isPending ? (
        <Skeleton label="Repartiendo el trabajo en sprints…" lines={4} />
      ) : sinTareasEnAbsoluto ? (
        <EmptyState
          icon={<CalendarRange size={22} strokeWidth={2} />}
          title="Todavia no hay nada que planificar"
          body="En cuanto haya tareas con fecha limite, apareceran aqui repartidas por sprints."
          action={
            writable ? (
              <Button onClick={() => navigate(ROUTES.tableroById(workspaceId))}>
                <Plus size={15} strokeWidth={2.5} />
                Ir al tablero
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <header className={styles.head}>
            <div>
              <p className={styles.window}>
                {formatDate(isoDate(plan.start))} – {formatDate(isoDate(plan.end))}
                <span className={styles.dim}>
                  {' · '}
                  {plan.weeks} {plan.weeks === 1 ? 'semana' : 'semanas'} en {plan.sprints.length}{' '}
                  {plan.sprints.length === 1 ? 'sprint' : 'sprints'}
                </span>
              </p>
              {!workspace?.startDate && (
                <p className={styles.note}>
                  El inicio sale de la primera tarea.{' '}
                  <button className={styles.link} onClick={() => navigate(ROUTES.ajustesById(workspaceId))}>
                    Fijar la fecha de inicio
                  </button>
                </p>
              )}
              {!workspace?.sprintDays && (
                <p className={styles.note}>
                  Sprints de {DEFAULT_SPRINT_DAYS / 7} semanas, por defecto.{' '}
                  <button className={styles.link} onClick={() => navigate(ROUTES.ajustesById(workspaceId))}>
                    Cambiar la duracion
                  </button>
                </p>
              )}
            </div>
            <div className={styles.headActions}>
              <Segmented options={ALCANCES} value={scope} onChange={elegirAlcance} ariaLabel="Que tareas ver" />
            </div>
          </header>

          {isMobile && plan.sprints.length > 1 && (
            <div className={styles.mobileTabs}>
              <Segmented
                options={plan.sprints.map((sprint, index) => ({ value: String(index), label: `Sprint ${sprint.number}` }))}
                value={String(indiceMovil)}
                onChange={(value) => setMobileSprint(Number(value))}
                ariaLabel="Sprint"
              />
            </div>
          )}

          {sinTareasPropias ? (
            <EmptyState
              icon={<Users size={22} strokeWidth={2} />}
              title="No tienes tareas asignadas aqui"
              body="Este workspace tiene trabajo, pero ninguna tarea es tuya todavia."
              action={
                <Button variant="secondary" onClick={() => elegirAlcance('team')}>
                  Ver todo el equipo
                </Button>
              }
            />
          ) : (
            <div className={styles.sprints}>
              {sprintsVisibles.map((sprint) => (
                <SprintColumn key={sprint.number} sprint={sprint} onOpen={setOpenTask} />
              ))}
            </div>
          )}

          {plan.sinFecha.length > 0 && (
            <section className={styles.loose} aria-label="Tareas sin fecha">
              <h2 className={styles.looseTitle}>
                Sin fecha <span className={styles.dim}>· {plan.sinFecha.length}</span>
              </h2>
              <p className={styles.note}>
                No entran en ningun sprint. Ponles fecha limite desde el tablero para que cuenten.
              </p>
              <div className={styles.looseCards}>
                {plan.sinFecha.map((item) => (
                  <PlanTaskCard
                    key={item.task.id}
                    task={item.task}
                    done={item.state === 'terminada'}
                    state={STATE_COLORS[item.state]}
                    onOpen={setOpenTask}
                  />
                ))}
              </div>
            </section>
          )}
        </>
      )}

      <TaskPanel
        task={openTask ? tasks.find((t) => t.id === openTask.id) ?? openTask : null}
        columns={columns}
        members={members ?? []}
        role={detail?.myRole ?? null}
        boardId={boards?.[0]?.id}
        projectId={workspaceId}
        onClose={() => setOpenTask(null)}
      />
    </WorkspaceShell>
  )
}

/** Columnas del tablero, ya ordenadas: el plan depende de ese orden. */
function columnsOf(columns?: ColumnResponse[]): ColumnResponse[] {
  return [...(columns ?? [])].sort((a, b) => a.position - b.position)
}

/**
 * Un sprint como columna: cabecera con sus fechas y su avance, y debajo sus
 * tareas en una sola lista ordenada por fecha, cada una con su insignia de
 * estado. Los contadores salen de las mismas listas que se pintan, asi que no
 * pueden discrepar de lo que se ve.
 */
function SprintColumn({ sprint, onOpen }: { sprint: Sprint; onOpen: (task: TaskResponse) => void }) {
  return (
    <section
      className={[styles.sprint, sprint.current && styles.currentSprint].filter(Boolean).join(' ')}
      aria-label={`Sprint ${sprint.number}`}
    >
      <header className={styles.sprintHead}>
        <h2 className={styles.sprintName}>
          Sprint {sprint.number}
          {sprint.current && <span className={styles.now}>ahora</span>}
        </h2>
        <span className={styles.range}>{sprintRange(sprint)}</span>
        <ProgressBar
          value={sprint.progress}
          height={3}
          color={sprint.progress === 100 ? 'var(--color-accent-2-500)' : 'var(--color-neutral-700)'}
          label={`Avance del sprint ${sprint.number}`}
        />
        <span className={styles.count}>
          {sprint.tasks.length === 0
            ? 'vacio'
            : `${sprint.terminadas.length}/${sprint.tasks.length} completadas`}
        </span>
      </header>

      {sprint.tasks.length === 0 ? (
        <p className={styles.sprintEmpty}>Sin tareas en estas fechas.</p>
      ) : (
        <div className={styles.cards}>
          {sprint.tasks.map((item) => (
            <PlanTaskCard
              key={item.task.id}
              task={item.task}
              done={item.state === 'terminada'}
              state={STATE_COLORS[item.state]}
              onOpen={onOpen}
            />
          ))}
        </div>
      )}
    </section>
  )
}
