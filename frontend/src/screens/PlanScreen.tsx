import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { CalendarRange, Plus, Users } from 'lucide-react'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { TaskCard } from '@/components/board/TaskCard'
import { TaskPanel } from '@/components/task/TaskPanel'
import { TeamDashboard } from '@/components/plan/TeamDashboard'
import { ProjectTracking } from '@/components/tracking/ProjectTracking'
import { Button } from '@/components/ui/Button'
import { Segmented } from '@/components/ui/Controls'
import { EmptyState } from '@/components/ui/EmptyState'
import { ProgressBar } from '@/components/ui/ProgressBar'
import { Skeleton } from '@/components/ui/Skeleton'
import styles from './PlanScreen.module.css'
import { useBoard, useProject, useProjectBoards, useTaskMutations, useTeamMembers, useWorkspace } from '@/hooks/useData'
import { buildPlan, DEFAULT_SPRINT_DAYS, isoDate, sprintRange, type Sprint } from '@/lib/sprints'
import { STATE_COLORS } from '@/lib/planStatus'
import { canManage, canWrite, isTeacher } from '@/lib/roles'
import { formatDate } from '@/lib/format'
import { useAuth } from '@/state/context'
import { useMediaQuery } from '@/hooks/useMediaQuery'
import { ROUTES } from '@/app/routes'
import type { ColumnResponse, TaskResponse } from '@/types/api'

/** Que ve el responsable: lo suyo o el equipo entero (con su resumen). */
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
 * solo en la insignia de cada tarjeta (color y texto).
 *
 * Que se ve depende del rol en este espacio. Un alumno ve solo sus tareas, sin
 * nada que elegir: es la pregunta que se hace de verdad al abrir esto. Quien es
 * responsable elige entre lo suyo y «Todo el equipo», que cambia los sprints
 * por un resumen de como va el equipo y cada persona (ver TeamDashboard). Un
 * docente, que solo observa, ve el panel de progreso del proyecto (el mismo de
 * el panel de seguimiento, ver ProjectTracking).
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
  // Sin tablero la consulta del tablero no se lanza y se quedaria «pendiente» para siempre.
  const cargando = loadingBoards || (boards?.[0] !== undefined && isPending)
  const { data: members } = useTeamMembers(workspace?.teamId)
  const { data: project } = useProject(workspaceId)
  const taskMutations = useTaskMutations(boards?.[0]?.id, workspaceId)

  // El alcance elegido se recuerda: nadie quiere volver a fijarlo cada vez. Lo guardado se
  // comprueba: un valor raro (de otra version, o tocado a mano) no puede dejar la vista sin ninguno.
  const [chosenScope, setScope] = useState<'mine' | 'team'>(
    () => (leerGuardado(GUARDADO_ALCANCE, 'mine') === 'team' ? 'team' : 'mine'),
  )
  // Lo decide el rol en ESTE espacio, no el de la cuenta. Un docente no tiene tareas suyas: siempre
  // ve el equipo. Un alumno, siempre lo suyo. Solo quien es responsable puede elegir.
  const soloObserva = isTeacher(workspace?.myRole)
  const responsable = canManage(workspace?.myRole)
  const scope = soloObserva ? 'team' : responsable ? chosenScope : 'mine'
  const [mobileSprint, setMobileSprint] = useState<number | null>(null)
  const [openTask, setOpenTask] = useState<TaskResponse | null>(null)

  const columns = useMemo(() => columnsOf(detail?.columns), [detail])
  const tasks = useMemo(() => columns.flatMap((column) => column.tasks ?? []), [columns])
  const visibleTasks = useMemo(
    () => (scope === 'mine' ? tasks.filter((task) => task.assignees.some((assignee) => assignee.id === user?.id)) : tasks),
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
      {cargando ? (
        <Skeleton label="Repartiendo el trabajo en sprints…" lines={4} />
      ) : sinTareasEnAbsoluto ? (
        <EmptyState
          icon={<CalendarRange size={22} strokeWidth={2} />}
          title="Todavía no hay nada que planificar"
          body="En cuanto haya tareas con fecha límite, aparecerán aquí repartidas por sprints."
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
              {/* Cambiarlo es cosa de quien administra el espacio: al resto se le dice de donde
                  sale, sin ofrecerle un enlace a unos ajustes que no puede tocar. */}
              {!workspace?.startDate && (
                <p className={styles.note}>
                  El inicio sale de la primera tarea.{' '}
                  {responsable && (
                    <button className={styles.link} onClick={() => navigate(ROUTES.ajustesById(workspaceId))}>
                      Fijar la fecha de inicio
                    </button>
                  )}
                </p>
              )}
              {!workspace?.sprintDays && (
                <p className={styles.note}>
                  Sprints de {DEFAULT_SPRINT_DAYS / 7} semanas, por defecto.{' '}
                  {responsable && (
                    <button className={styles.link} onClick={() => navigate(ROUTES.ajustesById(workspaceId))}>
                      Cambiar la duración
                    </button>
                  )}
                </p>
              )}
            </div>
            <div className={styles.headActions}>
              {responsable && (
                <Segmented options={ALCANCES} value={scope} onChange={elegirAlcance} ariaLabel="Qué tareas ver" />
              )}
            </div>
          </header>

          {/* El docente entra a ver como va el equipo: el panel de progreso (el mismo del panel de seguimiento, sin el
              tiempo medio por tarea). El responsable, en «Todo el equipo», tiene su resumen propio. */}
          {soloObserva ? (
            <ProjectTracking projectId={workspaceId} board={detail} project={project} hideCycleTime />
          ) : scope === 'team' && (
            <TeamDashboard tasks={tasks} columns={columns} members={members ?? []} sprints={plan.sprints} />
          )}

          {scope === 'mine' && isMobile && plan.sprints.length > 1 && (
            <div className={styles.mobileTabs}>
              <Segmented
                options={plan.sprints.map((sprint, index) => ({ value: String(index), label: `Sprint ${sprint.number}` }))}
                value={String(indiceMovil)}
                onChange={(value) => setMobileSprint(Number(value))}
                ariaLabel="Sprint"
              />
            </div>
          )}

          {scope === 'team' ? null : sinTareasPropias ? (
            <EmptyState
              icon={<Users size={22} strokeWidth={2} />}
              title="No tienes tareas asignadas aquí"
              body={
                responsable
                  ? 'Este espacio de trabajo tiene trabajo, pero ninguna tarea es tuya todavía.'
                  : 'Cuando te asignen alguna tarea, aparecerá aquí en su sprint.'
              }
              action={
                responsable ? (
                  <Button variant="secondary" onClick={() => elegirAlcance('team')}>
                    Ver todo el equipo
                  </Button>
                ) : undefined
              }
            />
          ) : (
            <div className={styles.sprints}>
              {sprintsVisibles.map((sprint) => (
                <SprintColumn
                  key={sprint.number}
                  sprint={sprint}
                  writable={writable}
                  onOpen={setOpenTask}
                  onDelete={(task) => taskMutations.remove.mutate(task.id)}
                />
              ))}
            </div>
          )}

          {scope === 'mine' && plan.sinFecha.length > 0 && (
            <section className={styles.loose} aria-label="Tareas sin fecha">
              <h2 className={styles.looseTitle}>
                Sin fecha <span className={styles.dim}>· {plan.sinFecha.length}</span>
              </h2>
              <p className={styles.note}>
                No entran en ningún sprint. Ábrelas y ponles una fecha límite: entrarán en el sprint que contenga esa fecha.
              </p>
              <div className={styles.looseCards}>
                {plan.sinFecha.map((item) => (
                  <TaskCard
                    key={item.task.id}
                    task={item.task}
                    draggable={false}
                    done={item.state === 'terminada'}
                    status={STATE_COLORS[item.state]}
                    canWrite={writable}
                    onOpen={setOpenTask}
                    onDelete={(task) => taskMutations.remove.mutate(task.id)}
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
function SprintColumn({
  sprint, writable, onOpen, onDelete,
}: {
  sprint: Sprint
  writable: boolean
  onOpen: (task: TaskResponse) => void
  onDelete: (task: TaskResponse) => void
}) {
  return (
    <section className={styles.sprint} aria-label={`Sprint ${sprint.number}`}>
      <header className={styles.sprintHead}>
        <div className={styles.sprintTitleRow}>
          <h2 className={styles.sprintName}>
            Sprint {sprint.number}
            {sprint.current && <span className={styles.now}>ahora</span>}
          </h2>
          <span className={styles.count}>
            {sprint.tasks.length === 0 ? '0' : `${sprint.terminadas.length}/${sprint.tasks.length}`}
          </span>
        </div>
        <span className={styles.range}>{sprintRange(sprint)}</span>
        <ProgressBar
          value={sprint.progress}
          height={2}
          label={`Avance del sprint ${sprint.number}: ${sprint.terminadas.length} de ${sprint.tasks.length} completadas`}
        />
      </header>

      {sprint.tasks.length === 0 ? (
        <p className={styles.sprintEmpty}>Sin tareas en estas fechas.</p>
      ) : (
        <div className={styles.cards}>
          {sprint.tasks.map((item) => (
            <TaskCard
              key={item.task.id}
              task={item.task}
              draggable={false}
              done={item.state === 'terminada'}
              status={STATE_COLORS[item.state]}
              canWrite={writable}
              onOpen={onOpen}
              onDelete={onDelete}
            />
          ))}
        </div>
      )}
    </section>
  )
}
