import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import {
  AiErrorCard, AiWorkbench, AppliedTasksSummary, PlanWarnings, PrivacyNote, ProposalPanel, WorkloadSummary,
} from '@/components/generator/PlanGenerator'
import { ProcessingSteps } from '@/components/generator/ProcessingSteps'
import { Skeleton } from '@/components/ui/Skeleton'
import { Tag } from '@/components/ui/Tag'
import styles from './GeneratePlanScreen.module.css'
import { projects as projectsApi, workspaces as workspacesApi } from '@/api/endpoints'
import {
  keys, useMemberMutations, useProject, useProjectBoards, useTeamMembers, useWorkspace,
} from '@/hooks/useData'
import { usePlanDrafts, usePlanJob, usePlanJobs, useUi, type PlanTeamDraft } from '@/state/context'
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
  const navigate = useNavigate()

  const { data: workspace } = useWorkspace(workspaceId)
  const { data: project, isPending: loadingProject } = useProject(workspaceId)
  const { data: boards } = useProjectBoards(workspaceId)
  const { data: members } = useTeamMembers(workspace?.teamId)
  const { pushToast } = useUi()
  const queryClient = useQueryClient()
  const memberMutations = useMemberMutations(workspace?.teamId)
  const planJobs = usePlanJobs()
  const job = usePlanJob(workspaceId)

  // Nada de estado local aqui: el borrador (documento, descripcion, calendario,
  // equipo y lo que quede marcado de la propuesta) vive por
  // encima de las rutas, para que irse un momento al tablero no obligue a
  // volver a subir el enunciado.
  const { draftOf, patch } = usePlanDrafts()
  const draft = draftOf(workspaceId)
  const { file, text: descripcion, appliedTitles } = draft
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
      preferences: member.planNotes ?? '',
      included: true,
    })
  // Automatico siempre que se pueda: si el workspace ya tiene ambas fechas,
  // esas mandan (ni tampoco se pregunta la duracion del sprint, que el servidor
  // toma directamente de sus propios ajustes). Si falta alguna, hay que
  // resolverla antes de generar nada: vacia hasta entonces, nunca "hoy" por
  // defecto, para no acabar generando con una fecha adivinada y tener que
  // repetir la llamada al modelo si resultaba estar mal.
  const effectiveStartDate = workspace?.startDate ?? draft.startDate ?? ''
  const effectiveDeadline = workspace?.deadline ?? draft.deadline ?? ''
  const fechasResueltas = Boolean(effectiveStartDate && effectiveDeadline)

  const apply = useMutation({
    mutationFn: async () => {
      const boardId = boards?.[0]?.id
      if (!boardId) throw new Error('Este espacio de trabajo no tiene tablero al que añadir las tareas')
      await saveCalendarOfThePlan()
      const tasks = (plan?.tasks ?? []).filter((_, index) => selected.includes(index))
      return projectsApi.applyAiPlan(workspaceId, boardId, tasks)
    },
    onSuccess: (created) => {
      // Reinicia la parte de "Planificar": si la propuesta se quedara ahi tal
      // cual, parece que no ha pasado nada (o que se ha quedado atascada) en
      // vez de reflejar que ya esta hecho. El resumen plegable de abajo es la
      // confirmacion de que salio bien.
      // El documento o la descripcion ya han servido: se quitan para poder dar el siguiente.
      set({ appliedTitles: created.map((task) => task.title), selected: null, text: '', file: null })
      planJobs.clear(workspaceId)

      const boardId = boards?.[0]?.id
      if (boardId) queryClient.invalidateQueries({ queryKey: keys.board(boardId) })
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      queryClient.invalidateQueries({ queryKey: keys.workspaces })
      queryClient.invalidateQueries({ queryKey: keys.workspace(workspaceId) })
      queryClient.invalidateQueries({ queryKey: keys.project(workspaceId) })
      queryClient.invalidateQueries({ queryKey: keys.activity(workspaceId) })
      pushToast(`${created.length} ${created.length === 1 ? 'tarea añadida' : 'tareas añadidas'} al tablero`)
    },
    onError: (err) => pushToast(toErrorMessage(err)),
  })

  /**
   * Al confirmar el plan, el espacio se queda con el calendario con el que se genero, si aun
   * no lo tenia: asi la pestana Plan cuadra con las tareas que se acaban de crear y la proxima
   * generacion no vuelve a pedir las fechas. Antes de confirmar no se guarda nada: generar y
   * descartar la propuesta no debe cambiar el espacio. Las fechas que ya tenia no se tocan
   * (esas solo se cambian en Ajustes).
   */
  async function saveCalendarOfThePlan() {
    const usadas = job?.options
    if (!workspace || !usadas) return
    const startDate = workspace.startDate ?? usadas.startDate ?? null
    const deadline = workspace.deadline ?? usadas.deadline ?? null
    if (startDate === (workspace.startDate ?? null) && deadline === (workspace.deadline ?? null)) return
    await workspacesApi.update(workspaceId, {
      name: workspace.name,
      description: workspace.description,
      color: workspace.color,
      icon: workspace.icon,
      startDate,
      deadline,
      sprintDays: workspace.sprintDays,
    })
  }

  /**
   * Documento si lo hay, si no la descripcion, si tampoco el guion ya guardado
   * del proyecto. Las fechas ya estan resueltas para cuando esto se puede
   * llamar (ver `sinFechas` en `AiWorkbench`); si no estaban en el espacio, la
   * propuesta se genera con las del formulario y solo se guardan al confirmarla.
   */
  function runGenerate() {
    if (!fechasResueltas) return
    set({ appliedTitles: null, selected: null })

    planJobs.start({
      workspaceId,
      workspaceName: workspace?.name ?? 'el espacio de trabajo',
      file,
      text: descripcion,
      options: {
        startDate: effectiveStartDate,
        deadline: effectiveDeadline,
        // La duracion del sprint no se pregunta: el servidor usa la del
        // workspace, o dos semanas si tampoco la tiene.
        team: (team ?? [])
          .filter((person) => person.included)
          .map(({ userId, preferences }) => ({
            userId, preferences: preferences.trim(),
          })),
      },
    })
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
          // Deteccion por texto, no llamada al modelo: se puede pedir de una vez,
          // al subir el documento, sin esperar a generar para saber si trae fecha.
          if (next && !workspace?.startDate && !draft.startDate) {
            projectsApi.detectStartDate(workspaceId, next).then((result) => {
              if (result.startDate) set({ startDate: result.startDate })
            }).catch(() => {})
          }
        }}
        text={descripcion}
        onText={(next) => {
          set({ text: next, appliedTitles: null })
          if (job?.status !== 'pending') planJobs.clear(workspaceId)
        }}
        startDate={effectiveStartDate}
        deadline={effectiveDeadline}
        onStartDate={(next) => set({ startDate: next || null })}
        onDeadline={(next) => set({ deadline: next || null })}
        workspaceStartDate={workspace?.startDate}
        workspaceDeadline={workspace?.deadline}
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

        {proponiendo && job?.withDocument && (
          <ProcessingSteps workspaceId={workspaceId} sourceLabel={job.sourceLabel} />
        )}

        {proponiendo && !job?.withDocument && (
          <Skeleton label={`Leyendo ${job?.sourceLabel ?? 'el enunciado'} y repartiendo el trabajo…`} />
        )}

        {!proponiendo && error && <AiErrorCard message={error} onRetry={runGenerate} />}

        {!proponiendo && !error && plan && (
          <>
            {plan.tasks.length === 0 ? (
              <>
                <PlanWarnings warnings={plan.warnings ?? []} />
                <p className={styles.nothing}>{plan.message}</p>
              </>
            ) : (
              <>
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
                >
                  <div className={styles.source}>
                    <Tag variant="outline">{plan.source}</Tag>
                  </div>
                  <PlanWarnings warnings={plan.warnings ?? []} />
                  <WorkloadSummary workload={plan.workload} />
                </ProposalPanel>
              </>
            )}
          </>
        )}
      </AiWorkbench>

      <PrivacyNote />
    </WorkspaceShell>
  )
}
