import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import {
  AiErrorCard, AiWorkbench, AppliedTasksSummary, PlanWarnings, PrivacyNote, ProposalPanel, SuggestedScheduleCard,
  WorkloadSummary,
} from '@/components/generator/PlanGenerator'
import { Skeleton } from '@/components/ui/Skeleton'
import { Tag } from '@/components/ui/Tag'
import styles from './GeneratePlanScreen.module.css'
import { projects as projectsApi } from '@/api/endpoints'
import {
  keys, useMemberMutations, useProject, useProjectBoards, useTeamMembers, useWorkspace, useWorkspaceMutations,
} from '@/hooks/useData'
import { usePlanDrafts, usePlanJob, usePlanJobs, useUi, type PlanTeamDraft } from '@/state/context'
import { toErrorMessage } from '@/lib/api-client'
import { canManage } from '@/lib/roles'
import { ROUTES } from '@/app/routes'
import { isoDate } from '@/lib/sprints'

/** Lo que se asume si no se dice: una asignatura normal, no dedicacion completa. */
const DEFAULT_HOURS_PER_WEEK = 10

/**
 * Generar plan: crear tareas con IA a partir de un documento o una
 * descripcion, nada mas. Preguntarle algo puntual al modelo es otra cosa —el
 * desplegable de ayuda de la cabecera (ver Assistant)— y esta abierto a
 * cualquiera; esta pantalla, en cambio, solo la ve quien administra el equipo,
 * porque lo que hace (proponer y aplicar tareas al tablero) tambien esta
 * restringido en el servidor a ese rol.
 *
 * Generar tarda: la peticion en si la lanza {@link usePlanJobs}, anclada por
 * encima de las rutas, asi que sigue en marcha aunque el usuario se vaya a
 * mirar el tablero o los miembros. Esta pantalla solo lee su resultado.
 */
export function GeneratePlanScreen() {
  const { workspaceId: param } = useParams()
  const workspaceId = Number(param)
  const navigate = useNavigate()

  const { data: workspace } = useWorkspace(workspaceId)
  const { data: project, isPending: loadingProject } = useProject(workspaceId)
  const { data: boards } = useProjectBoards(workspaceId)
  const { data: members } = useTeamMembers(workspace?.teamId)
  const { pushToast } = useUi()
  const queryClient = useQueryClient()
  const workspaceMutations = useWorkspaceMutations(workspaceId)
  const memberMutations = useMemberMutations(workspace?.teamId)
  const planJobs = usePlanJobs()
  const job = usePlanJob(workspaceId)

  // Nada de estado local aqui: el borrador (documento, descripcion, calendario,
  // equipo, indicaciones y lo que quede marcado de la propuesta) vive por
  // encima de las rutas, para que irse un momento al tablero no obligue a
  // volver a subir el enunciado ni a reescribir las indicaciones.
  const { draftOf, patch } = usePlanDrafts()
  const draft = draftOf(workspaceId)
  const { file, text: descripcion, indications: indicaciones, appliedTitles, scheduleApplied } = draft
  const set = (cambios: Partial<typeof draft>) => patch(workspaceId, cambios)

  const plan = job?.status === 'done' ? job.plan : undefined
  const error = job?.status === 'error' ? job.error : undefined
  const proponiendo = job?.status === 'pending'

  // Al generar se marca todo, que es lo que se espera; en cuanto el usuario
  // desmarca algo a mano se guarda su eleccion. Basta con volver a null al
  // lanzar una generacion o al descartar la propuesta, que son los dos unicos
  // momentos en los que el plan deja de ser el mismo.
  const selected = draft.selected ?? (plan?.tasks.map((_, index) => index) ?? [])

  // El docente solo mira: no es alumno, no entra en el reparto. Lo que se haya
  // tocado de cada persona se conserva; quien entre o salga del equipo despues
  // aparece o desaparece sin tener que rehacer el formulario.
  const team: PlanTeamDraft[] | undefined = members
    ?.filter((member) => member.role !== 'DOCENTE')
    .map((member) => draft.team?.find((person) => person.userId === member.user.id) ?? {
      userId: member.user.id,
      name: member.user.fullName,
      hoursPerWeek: DEFAULT_HOURS_PER_WEEK,
      preferences: member.planNotes ?? '',
      included: true,
    })
  // Automatico siempre que se pueda: si el workspace ya tiene fecha de inicio,
  // esa manda y no se pregunta nada (ni tampoco la duracion del sprint, que el
  // servidor toma directamente de sus propios ajustes). Solo cuando el
  // workspace no tiene fecha de inicio hace falta una a mano.
  const startDate = workspace?.startDate ?? draft.startDate ?? isoDate(new Date())

  const apply = useMutation({
    mutationFn: () => {
      const boardId = boards?.[0]?.id
      if (!boardId) throw new Error('Este workspace no tiene tablero al que anadir las tareas')
      const tasks = (plan?.tasks ?? []).filter((_, index) => selected.includes(index))
      return projectsApi.applyAiPlan(workspaceId, boardId, tasks)
    },
    onSuccess: (created) => {
      // Reinicia la parte de "Planificar": si la propuesta se quedara ahi tal
      // cual, parece que no ha pasado nada (o que se ha quedado atascada) en
      // vez de reflejar que ya esta hecho. El resumen plegable de abajo es la
      // confirmacion de que salio bien.
      set({ appliedTitles: created.map((task) => task.title), selected: null })
      planJobs.clear(workspaceId)

      const boardId = boards?.[0]?.id
      if (boardId) queryClient.invalidateQueries({ queryKey: keys.board(boardId) })
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      queryClient.invalidateQueries({ queryKey: keys.workspaces })
      queryClient.invalidateQueries({ queryKey: keys.activity(workspaceId) })
      pushToast(`${created.length} ${created.length === 1 ? 'tarea anadida' : 'tareas anadidas'} al tablero`)
    },
    onError: (err) => pushToast(toErrorMessage(err)),
  })

  /** Documento si lo hay, si no la descripcion, si tampoco el guion ya guardado del proyecto. */
  function runGenerate() {
    set({ appliedTitles: null, scheduleApplied: false, selected: null })
    planJobs.start({
      workspaceId,
      workspaceName: workspace?.name ?? 'el workspace',
      file,
      text: descripcion,
      options: {
        startDate,
        // La duracion del sprint no se pregunta: el servidor usa la del
        // workspace, o dos semanas si tampoco la tiene.
        indications: indicaciones.trim() || undefined,
        team: (team ?? [])
          .filter((person) => person.included)
          .map(({ userId, hoursPerWeek, preferences }) => ({
            userId, hoursPerWeek, preferences: preferences.trim(),
          })),
      },
    })
  }

  /**
   * Guarda en el workspace las fechas con las que se ha hecho el plan, para que la
   * pestana Plan pinte los mismos sprints. Solo toca lo que difiere: no debe
   * borrar una descripcion, un color o una fecha que el usuario ya puso a mano.
   */
  function applySchedule() {
    if (!workspace || !plan?.schedule) return
    workspaceMutations.update.mutate(
      {
        name: workspace.name,
        description: workspace.description,
        color: workspace.color,
        icon: workspace.icon,
        startDate: plan.schedule.startDate ?? workspace.startDate ?? null,
        deadline: plan.schedule.deadline ?? workspace.deadline ?? null,
        sprintDays: plan.schedule.sprintDays ?? workspace.sprintDays,
      },
      {
        onSuccess: () => {
          set({ scheduleApplied: true })
          queryClient.invalidateQueries({ queryKey: keys.project(workspaceId) })
        },
      },
    )
  }

  if (workspace && !canManage(workspace.myRole)) {
    return <Navigate to={ROUTES.planById(workspaceId)} replace />
  }

  return (
    <WorkspaceShell workspace={workspace} workspaceId={workspaceId}>
      <AiWorkbench
        briefing={project?.briefing ?? ''}
        loading={loadingProject}
        file={file}
        onFile={(next) => {
          set({ file: next, appliedTitles: null })
          // Si hay una generacion en marcha, tocar el documento no debe hacer
          // que su "generando…" desaparezca: sigue corriendo igual, solo se
          // descarta un resultado o error ya terminado, que ahora si quedaria
          // desactualizado.
          if (job?.status !== 'pending') planJobs.clear(workspaceId)
        }}
        text={descripcion}
        onText={(next) => {
          set({ text: next, appliedTitles: null })
          if (job?.status !== 'pending') planJobs.clear(workspaceId)
        }}
        indications={indicaciones}
        onIndications={(next) => set({ indications: next })}
        startDate={startDate}
        onStartDate={(next) => set({ startDate: next })}
        workspaceStartDate={workspace?.startDate}
        onGoToSettings={() => navigate(ROUTES.ajustesById(workspaceId))}
        team={team}
        members={members}
        onTeam={(next) => set({ team: next })}
        onSaveNotes={(userId, preferences) =>
          memberMutations.updateNotes.mutateAsync({ userId, planNotes: preferences })
        }
        onRun={runGenerate}
        running={proponiendo}
      >
        {!proponiendo && !error && !plan && appliedTitles && (
          <AppliedTasksSummary titles={appliedTitles} />
        )}

        {proponiendo && (
          <Skeleton label={`Leyendo ${job?.sourceLabel ?? 'el enunciado'} y repartiendo el trabajo…`} />
        )}

        {!proponiendo && error && <AiErrorCard message={error} onRetry={runGenerate} />}

        {!proponiendo && !error && plan && (
          <>
            <div className={styles.source}>
              <Tag variant="outline">{plan.source}</Tag>
              <Tag variant="outline">modelo {plan.model}</Tag>
            </div>

            {plan.schedule && (
              <SuggestedScheduleCard
                schedule={plan.schedule}
                applying={workspaceMutations.update.isPending}
                applied={scheduleApplied}
                onApply={applySchedule}
              />
            )}

            <PlanWarnings warnings={plan.warnings ?? []} />

            {plan.tasks.length === 0 ? (
              <p className={styles.nothing}>{plan.message}</p>
            ) : (
              <>
                <WorkloadSummary workload={plan.workload} />
                <ProposalPanel
                  tasks={plan.tasks}
                  sprints={plan.sprints ?? []}
                  selected={selected}
                  model={plan.model}
                  applying={apply.isPending}
                  onToggle={(index) =>
                    set({
                      selected: selected.includes(index)
                        ? selected.filter((item) => item !== index)
                        : [...selected, index],
                    })
                  }
                  onApply={() => apply.mutate()}
                  onRegenerate={runGenerate}
                />
              </>
            )}
          </>
        )}
      </AiWorkbench>

      <PrivacyNote />
    </WorkspaceShell>
  )
}
