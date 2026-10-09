import { useParams } from 'react-router-dom'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { ProjectTracking } from '@/components/tracking/ProjectTracking'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import { useBoard, useProject, useProjectBoards, useWorkspace } from '@/hooks/useData'

/**
 * El backend no expone metricas: todo lo que se ve aqui se calcula en el cliente
 * a partir del tablero real (ver lib/metrics.ts). Cada insight cita sus cifras.
 */
export function TrackingScreen() {
  const { workspaceId: workspaceIdParam } = useParams()
  const projectId = Number(workspaceIdParam)

  const { data: workspace } = useWorkspace(projectId)
  const { data: project } = useProject(projectId)
  const { data: boards, isPending: loadingBoards } = useProjectBoards(projectId)
  // Cada workspace tiene un unico tablero: nace con el y no se crean otros.
  const { data: detail, isPending } = useBoard(boards?.[0]?.id)
  // Sin tablero la consulta del tablero no se lanza y se quedaria «pendiente» para siempre.
  const cargando = loadingBoards || (boards?.[0] !== undefined && isPending)

  const hasTasks = (detail?.columns ?? []).some((column) => (column.tasks?.length ?? column.taskCount) > 0)

  return (
    <WorkspaceShell workspace={workspace} workspaceId={projectId}>
      {cargando ? (
        <Skeleton label="Calculando el seguimiento del proyecto…" lines={4} />
      ) : !hasTasks ? (
        <EmptyState
          title="Todavía no hay datos que medir"
          body="En cuanto el tablero tenga tareas, aquí aparecerán el progreso, la velocidad, el tiempo de ciclo y los insights."
        />
      ) : (
        <ProjectTracking projectId={projectId} board={detail} project={project} />
      )}
    </WorkspaceShell>
  )
}
