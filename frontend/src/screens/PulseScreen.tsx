import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { WorkspaceShell } from '@/components/shell/WorkspaceShell'
import { BarChart, DistributionBars, InsightCard, KpiCard, PriorityBar } from '@/components/pulse/Pulse'
import { EmptyState } from '@/components/ui/EmptyState'
import { Skeleton } from '@/components/ui/Skeleton'
import { Segmented } from '@/components/ui/Controls'
import { Section } from './Section'
import styles from './PulseScreen.module.css'
import { useBoard, useProject, useProjectBoards, useWorkspace } from '@/hooks/useData'
import { derivePulse } from '@/lib/metrics'
import { ROUTES } from '@/app/routes'

/**
 * El backend no expone metricas: todo lo que se ve aqui se calcula en el cliente
 * a partir del tablero real (ver lib/metrics.ts). Cada insight cita sus cifras.
 */
export function PulseScreen() {
  const { workspaceId: workspaceIdParam } = useParams()
  const projectId = Number(workspaceIdParam)
  const navigate = useNavigate()

  const { data: workspace } = useWorkspace(projectId)
  const { data: project } = useProject(projectId)
  const { data: boards } = useProjectBoards(projectId)
  const [boardIndex, setBoardIndex] = useState(0)
  const board = boards?.[boardIndex]
  const { data: detail, isPending } = useBoard(board?.id)

  const pulse = useMemo(() => derivePulse(detail, project), [detail, project])
  const hasTasks = pulse.totalCount > 0

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

          <div className={styles.kpis}>
            {pulse.kpis.map((kpi) => (
              <KpiCard
                key={kpi.id}
                label={kpi.label}
                value={kpi.value}
                delta={kpi.delta}
                better={kpi.better}
                unit={kpi.unit}
                alert={kpi.alert}
              />
            ))}
          </div>

          <div className={styles.split}>
            <BarChart series={pulse.weekly} title="Evolución temporal" />
            <div className={styles.side}>
              <DistributionBars title="Por estado" rows={pulse.byStatus} />
              {pulse.byPriority.length > 0 && (
                <PriorityBar title="Por prioridad" segments={pulse.byPriority} />
              )}
            </div>
          </div>

          <Section
            plain
            title="Insights inteligentes"
            subtitle="calculados a partir de la actividad real del tablero"
          >
            <div className={styles.insights}>
              {pulse.insights.map((insight) => (
                <InsightCard
                  key={insight.id}
                  variant={insight.variant}
                  kicker={insight.kicker}
                  title={insight.title}
                  body={insight.body}
                  actions={insight.actions}
                  onAction={() => navigate(ROUTES.tableroById(projectId))}
                />
              ))}
            </div>
          </Section>
        </>
      )}
    </WorkspaceShell>
  )
}
