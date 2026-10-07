import { useParams } from 'react-router-dom'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { ProjectPulse } from '@/components/pulse/ProjectPulse'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import { useBoard, useProject, useProjectBoards, useWorkspace } from '@/hooks/useData'

/**
 * El backend no expone metricas: todo lo que se ve aqui se calcula en el cliente
 * a partir del tablero real (ver lib/metrics.ts). Cada insight cita sus cifras.
 */
export function PulseScreen() {
  const { workspaceId: workspaceIdParam } = useParams()
  const projectId = Number(workspaceIdParam)

  const { data: workspace } = useWorkspace(projectId)
  const { data: project } = useProject(projectId)
  const { data: boards } = useProjectBoards(projectId)
  // Cada workspace tiene un unico tablero: nace con el y no se crean otros.
  const { data: detail, isPending } = useBoard(boards?.[0]?.id)

  const hasTasks = (detail?.columns ?? []).some((column) => (column.tasks?.length ?? column.taskCount) > 0)

  return (
    <WorkspaceShell workspace={workspace} workspaceId={projectId}>
      {isPending ? (
        <Skeleton label="Calculando el pulso del proyecto…" lines={4} />
      ) : !hasTasks ? (
        <EmptyState
          title="Todavía no hay datos que medir"
          body="En cuanto el tablero tenga tareas, aquí aparecerán el progreso, la velocidad, el tiempo de ciclo y los insights."
        />
      ) : (
        <ProjectPulse projectId={projectId} board={detail} project={project} />
      )}
    </WorkspaceShell>
  )
}
