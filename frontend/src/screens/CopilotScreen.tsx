import { useState, type ReactNode } from 'react'
import { useParams } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { MessageCircle, Sparkles } from 'lucide-react'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { Chat, type ChatMessage } from '@/components/copilot/Chat'
import {
  AiErrorCard, AiWorkbench, AppliedTasksSummary, PrivacyNote, ProposalPanel, SuggestedScheduleCard,
  WorkloadSummary,
} from '@/components/copilot/Copilot'
import { Skeleton } from '@/components/ui/Skeleton'
import { Tag } from '@/components/ui/Tag'
import styles from './CopilotScreen.module.css'
import { projects as projectsApi, workspaces as workspacesApi } from '@/api/endpoints'
import {
  keys, useProject, useProjectBoards, useTeamMembers, useWorkspace, useWorkspaceMutations,
} from '@/hooks/useData'
import { useCopilotJob, useCopilotJobs, useUi } from '@/state/context'
import { toErrorMessage } from '@/lib/api-client'
import type { ChatTurn } from '@/types/api'

type CopilotTab = 'planificar' | 'hablar'

const ICON = { size: 15, strokeWidth: 2.75 } as const
const TABS: { value: CopilotTab; label: string; icon: ReactNode }[] = [
  { value: 'planificar', label: 'Planificar', icon: <Sparkles {...ICON} /> },
  { value: 'hablar', label: 'Hablar', icon: <MessageCircle {...ICON} /> },
]

/** Igual que las pestanas de Plan/Tablero/Copiloto de arriba, no un control de formulario. */
function CopilotTabs({ value, onChange }: { value: CopilotTab; onChange: (value: CopilotTab) => void }) {
  return (
    <div className={styles.tabs} role="tablist" aria-label="Que quieres hacer">
      {TABS.map((item) => (
        <button
          key={item.value}
          role="tab"
          aria-selected={item.value === value}
          className={[styles.tab, item.value === value && styles.tabActive].filter(Boolean).join(' ')}
          onClick={() => onChange(item.value)}
        >
          {item.icon}
          {item.label}
        </button>
      ))}
    </div>
  )
}

/**
 * El copiloto del workspace.
 *
 * Dos apartados elegibles con una pestana, no dos cosas apiladas: conviven dos
 * herramientas de naturaleza distinta —<b>preguntar</b>, que no toca nada, y
 * <b>planificar</b>, que devuelve una propuesta que hay que aceptar— y cada una
 * se elige aparte en vez de compartir la misma pantalla larga. Dentro de cada
 * una, nada de submenus: la caja de planificar (documento o descripcion, nunca
 * hace falta elegir un modo antes) y el chat estan siempre a la vista en cuanto
 * se entra en su pestana. El modelo corre en local y nunca escribe en el
 * tablero por su cuenta.
 *
 * Generar tarda: la peticion en si la lanza {@link useCopilotJobs}, anclada por
 * encima de las rutas, asi que sigue en marcha aunque el usuario cambie de
 * pestana (Hablar) o se vaya a mirar el tablero o los miembros. Esta pantalla
 * solo lee su resultado.
 */
export function CopilotScreen() {
  const { workspaceId: param } = useParams()
  const workspaceId = Number(param)

  const { data: workspace } = useWorkspace(workspaceId)
  const { data: project, isPending: loadingProject } = useProject(workspaceId)
  const { data: boards } = useProjectBoards(workspaceId)
  const { data: members } = useTeamMembers(workspace?.teamId)
  const { pushToast } = useUi()
  const queryClient = useQueryClient()
  const workspaceMutations = useWorkspaceMutations(workspaceId)
  const copilotJobs = useCopilotJobs()
  const job = useCopilotJob(workspaceId)

  const [tab, setTab] = useState<CopilotTab>('planificar')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [file, setFile] = useState<File | null>(null)
  const [descripcion, setDescripcion] = useState('')
  const [indicaciones, setIndicaciones] = useState('')
  const [chosenPeople, setChosenPeople] = useState<number | null>(null)
  // Titulos de la ultima tanda anadida al tablero, para el resumen plegable de
  // confirmacion. null cuando no hay nada reciente que mostrar.
  const [appliedTitles, setAppliedTitles] = useState<string[] | null>(null)
  const [scheduleApplied, setScheduleApplied] = useState(false)

  const plan = job?.status === 'done' ? job.plan : undefined
  const error = job?.status === 'error' ? job.error : undefined
  const proponiendo = job?.status === 'pending'

  // Cada vez que llega una propuesta nueva (o al montar, si ya habia una
  // terminada de antes), se marca todo: es lo que se espera al generar. Si el
  // usuario desmarca algo a mano, se conserva mientras siga siendo la misma
  // propuesta (aunque navegue fuera y vuelva). Ajustado durante el render en
  // vez de en un efecto: no hace falta un renderizado extra para algo que ya
  // se sabe en cuanto cambia "plan".
  const [selected, setSelected] = useState<number[]>(() => plan?.tasks.map((_, index) => index) ?? [])
  const [previousPlan, setPreviousPlan] = useState(plan)
  if (plan !== previousPlan) {
    setPreviousPlan(plan)
    setSelected(plan ? plan.tasks.map((_, index) => index) : [])
  }

  // El profesor (OBSERVADOR) solo mira: no es alumno, no cuenta para el reparto.
  const teamSize = members?.filter((member) => member.role !== 'OBSERVADOR').length ?? 0
  const people = chosenPeople ?? (teamSize || 1)

  /** Mensaje de error util cuando el modelo local no responde. */
  function describe(err: unknown): string {
    const status = axios.isAxiosError(err) ? err.response?.status : undefined
    return status === 503
      ? `${toErrorMessage(err)} El modelo corre en tu maquina: comprueba que Ollama esta arrancado.`
      : toErrorMessage(err)
  }

  const ask = useMutation({
    mutationFn: (message: string) => {
      const history: ChatTurn[] = messages.map(({ role, content }) => ({ role, content }))
      return workspacesApi.ask(workspaceId, message, history)
    },
    onSuccess: (response) => {
      setMessages((current) => [
        ...current,
        { role: 'assistant', content: response.answer, context: response.contextSummary },
      ])
    },
    onError: (err) => {
      setMessages((current) => [...current, { role: 'assistant', content: describe(err), error: true }])
    },
  })

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
      setAppliedTitles(created.map((task) => task.title))
      copilotJobs.clear(workspaceId)

      const boardId = boards?.[0]?.id
      if (boardId) queryClient.invalidateQueries({ queryKey: keys.board(boardId) })
      queryClient.invalidateQueries({ queryKey: ['tasks'] })
      queryClient.invalidateQueries({ queryKey: keys.workspaces })
      queryClient.invalidateQueries({ queryKey: keys.activity(workspaceId) })
      pushToast(`${created.length} ${created.length === 1 ? 'tarea anadida' : 'tareas anadidas'} al tablero`)
    },
    onError: (err) => pushToast(toErrorMessage(err)),
  })

  function send(message: string) {
    setMessages((current) => [...current, { role: 'user', content: message }])
    ask.mutate(message)
  }

  /** Documento si lo hay, si no la descripcion, si tampoco el guion ya guardado del proyecto. */
  function runGenerate() {
    setAppliedTitles(null)
    setScheduleApplied(false)
    copilotJobs.start({
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
          setScheduleApplied(true)
          queryClient.invalidateQueries({ queryKey: keys.project(workspaceId) })
        },
      },
    )
  }

  return (
    <WorkspaceShell workspace={workspace} workspaceId={workspaceId}>
      <CopilotTabs value={tab} onChange={setTab} />

      {tab === 'planificar' && (
      <AiWorkbench
        briefing={project?.briefing ?? ''}
        loading={loadingProject}
        file={file}
        onFile={(next) => {
          setFile(next)
          // Si hay una generacion en marcha, tocar el documento no debe hacer
          // que su "generando…" desaparezca: sigue corriendo igual, solo se
          // descarta un resultado o error ya terminado, que ahora si quedaria
          // desactualizado.
          if (job?.status !== 'pending') copilotJobs.clear(workspaceId)
          setAppliedTitles(null)
        }}
        text={descripcion}
        onText={(next) => {
          setDescripcion(next)
          if (job?.status !== 'pending') copilotJobs.clear(workspaceId)
          setAppliedTitles(null)
        }}
        indications={indicaciones}
        onIndications={setIndicaciones}
        people={people}
        maxPeople={teamSize || undefined}
        onPeople={setChosenPeople}
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
                    setSelected((current) =>
                      current.includes(index)
                        ? current.filter((item) => item !== index)
                        : [...current, index],
                    )
                  }
                  onApply={() => apply.mutate()}
                  onRegenerate={runGenerate}
                />
              </>
            )}
          </>
        )}
      </AiWorkbench>
      )}

      {tab === 'hablar' && (
        <Chat
          messages={messages}
          pending={ask.isPending}
          onSend={send}
          onReset={() => setMessages([])}
        />
      )}

      <PrivacyNote />
    </WorkspaceShell>
  )
}
