import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { ProjectPulse } from '@/components/pulse/ProjectPulse'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import { Segmented } from '@/components/ui/Controls'
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
  const [boardIndex, setBoardIndex] = useState(0)
  const board = boards?.[boardIndex]
  const { data: detail, isPending } = useBoard(board?.id)

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
        <>
          {boards && boards.length > 1 && (
            <Segmented
              options={boards.map((item, index) => ({ value: String(index), label: item.name }))}
              value={String(boardIndex)}
              onChange={(value) => setBoardIndex(Number(value))}
              ariaLabel="Tablero"
            />
          )}

          <ProjectPulse projectId={projectId} board={detail} project={project} />
        </>
      )}
    </WorkspaceShell>
  )
}
