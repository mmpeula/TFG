import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Info } from 'lucide-react'
import { Dropdown } from '@/components/ui/Controls'
import { InsightCard } from './Pulse'
import { ProgressDashboard } from './ProgressDashboard'
import styles from './ProgressDashboard.module.css'
import { derivePulse, SEMANAS_POR_DEFECTO } from '@/lib/metrics'
import { ROUTES } from '@/app/routes'
import type { BoardResponse, ProjectResponse } from '@/types/api'

/**
 * Panel de progreso de un espacio de trabajo con sus insights debajo: lo que ve
 * el docente al entrar en un espacio (pestaña Plan) y cualquiera en Pulso. Un
 * solo componente para que las dos pantallas no puedan contar cosas distintas.
 */
export function ProjectPulse({
  projectId,
  board,
  project,
  hideCycleTime,
}: {
  projectId: number
  board?: BoardResponse
  project?: ProjectResponse
  /** Sin la cifra de tiempo medio por tarea (el plan del docente no la ensena). */
  hideCycleTime?: boolean
}) {
  const navigate = useNavigate()
  const [weeks, setWeeks] = useState<number>(SEMANAS_POR_DEFECTO)
  const pulse = useMemo(() => derivePulse(board, project, weeks), [board, project, weeks])

  return (
    <ProgressDashboard pulse={pulse} weeks={weeks} onWeeksChange={setWeeks} hideCycleTime={hideCycleTime}>
      {pulse.insights.length > 0 && (
        <section className={styles.insightsSection}>
          <header className={styles.insightsHead}>
            <div className={styles.insightsTitle}>
              <h3>Insights inteligentes</h3>
              {/* Al pulsar se abre la explicacion (no un title del navegador, que solo sale
                  dejando el raton quieto y no se puede pulsar). */}
              <Dropdown
                align="left"
                trigger={({ toggle, open }) => (
                  <button
                    type="button"
                    className={styles.insightsInfo}
                    onClick={toggle}
                    aria-expanded={open}
                    aria-label="Cómo se calculan los insights"
                  >
                    <Info size={16} strokeWidth={2} />
                  </button>
                )}
              >
                {() => (
                  <div className={styles.insightsHelp}>
                    <p>Se calculan en el momento con las tareas del tablero; no los inventa la IA.</p>
                    <p>
                      <b>Riesgo de entrega:</b> compara las tareas que quedan con el ritmo de las últimas cuatro
                      semanas y los días que faltan para la entrega.
                    </p>
                    <p>
                      <b>Avisos:</b> tareas vencidas, paradas cuatro días o más, o en curso sin responsable. Son las
                      mismas que muestra el filtro «Avisos» del tablero.
                    </p>
                  </div>
                )}
              </Dropdown>
            </div>
            <span className={styles.panelSubtitle}>Calculados a partir de la actividad real del tablero</span>
          </header>
          <div className={styles.insights}>
            {pulse.insights.map((insight) => (
              <InsightCard
                key={insight.id}
                insight={insight}
                // Al llegar al tablero se abre la tarea o se pone el filtro «Avisos» (ver BoardScreen).
                onAction={(action) =>
                  navigate(ROUTES.tableroById(projectId), {
                    state: { openTaskId: action.taskId, onlyWarnings: action.onlyWarnings },
                  })
                }
              />
            ))}
          </div>
        </section>
      )}
    </ProgressDashboard>
  )
}
