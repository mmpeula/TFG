import { Navigate, useParams } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import {
  AiErrorCard, AiWorkbench, AppliedTasksSummary, PrivacyNote, ProposalPanel, SuggestedScheduleCard,
  WorkloadSummary,
} from '@/components/generator/PlanGenerator'
import { Skeleton } from '@/components/ui/Skeleton'
import { Tag } from '@/components/ui/Tag'
import styles from './GeneratePlanScreen.module.css'
import { projects as projectsApi } from '@/api/endpoints'
import {
  keys, useProject, useProjectBoards, useTeamMembers, useWorkspace, useWorkspaceMutations,
} from '@/hooks/useData'
import { usePlanDrafts, usePlanJob, usePlanJobs, useUi } from '@/state/context'
import { toErrorMessage } from '@/lib/api-client'
import { canManage } from '@/lib/roles'
import { ROUTES } from '@/app/routes'

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

  const { data: workspace } = useWorkspace(workspaceId)
  const { data: project, isPending: loadingProject } = useProject(workspaceId)
  const { data: boards } = useProjectBoards(workspaceId)
  const { data: members } = useTeamMembers(workspace?.teamId)
  const { pushToast } = useUi()
  const queryClient = useQueryClient()
  const workspaceMutations = useWorkspaceMutations(workspaceId)
  const planJobs = usePlanJobs()
  const job = usePlanJob(workspaceId)

  // Nada de estado local aqui: el borrador (documento, descripcion,
  // indicaciones, personas y lo que quede marcado de la propuesta) vive por
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

  // El docente solo mira: no es alumno, no cuenta para el reparto.
  const teamSize = members?.filter((member) => member.role !== 'DOCENTE').length ?? 0
  const people = draft.people ?? (teamSize || 1)

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
      people,
      indications: indicaciones.trim() || undefined,
    })
  }

  /**
   * Aplica al workspace solo las fechas/duracion que el documento ha propuesto,
   * conservando lo que ya hubiera en lo demas: aplicar la sugerencia no debe
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
        people={people}
        maxPeople={teamSize || undefined}
        onPeople={(next) => set({ people: next })}
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

            {plan.excludedMembers && plan.excludedMembers.length > 0 && (
              <p className={styles.nothing}>
                Sin tareas para {plan.excludedMembers.join(', ')}: lo pedian tus indicaciones.
              </p>
            )}

            {plan.tasks.length === 0 ? (
              <p className={styles.nothing}>{plan.message}</p>
            ) : (
              <>
                <WorkloadSummary workload={plan.workload} />
                <ProposalPanel
                  tasks={plan.tasks}
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
