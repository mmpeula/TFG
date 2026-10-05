import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Check } from 'lucide-react'
import styles from './ProcessingSteps.module.css'
import { projects as projectsApi } from '@/api/endpoints'
import type { PlanStage } from '@/types/api'

/** Las fases en el orden en que ocurren. Despues de la ultima, la propia respuesta dice que ha terminado. */
const STAGES: { stage: PlanStage; label: string; note?: string }[] = [
  { stage: 'RECEIVED', label: 'Documento recibido' },
  { stage: 'ANALYZING', label: 'Analizando documento' },
  { stage: 'EXTRACTING', label: 'Extrayendo contenido' },
  { stage: 'VISUAL', label: 'Analizando contenido visual', note: 'solo si hace falta' },
  { stage: 'PLANNING', label: 'Generando planificación' },
]

const VISUAL_INDEX = STAGES.findIndex(({ stage }) => stage === 'VISUAL')
const POLL_MS = 1200

/**
 * Por donde va la lectura del documento mientras se genera el plan.
 *
 * La generacion es una unica peticion que tarda, asi que la fase la pregunta
 * este componente al servidor cada poco, solo mientras esta en pantalla. La fase
 * del contenido visual solo existe si el documento la necesita (un escaneado, una
 * imagen con informacion): si el servidor pasa de largo, se ensena como que no
 * hizo falta en vez de darla por hecha.
 */
export function ProcessingSteps({ workspaceId, sourceLabel }: { workspaceId: number; sourceLabel: string }) {
  const { data } = useQuery({
    queryKey: ['plan-progress', workspaceId],
    queryFn: () => projectsApi.aiPlanProgress(workspaceId),
    refetchInterval: POLL_MS,
    retry: false,
    // Sin cache: la siguiente generacion no debe arrancar ensenando la ultima fase de la anterior.
    gcTime: 0,
  })
  const [sawVisual, setSawVisual] = useState(false)

  // Antes de la primera respuesta, o si el servidor aun no ha apuntado la fase, se esta recibiendo.
  const current = Math.max(0, STAGES.findIndex(({ stage }) => stage === data?.stage))
  if (data?.stage === 'VISUAL' && !sawVisual) setSawVisual(true)

  return (
    <div className={styles.steps} aria-live="polite" aria-busy="true">
      <p className={styles.title}>Leyendo {sourceLabel} y repartiendo el trabajo…</p>
      <ol className={styles.list}>
        {STAGES.map(({ stage, label, note }, index) => {
          const skipped = index === VISUAL_INDEX && current > VISUAL_INDEX && !sawVisual
          const state = skipped ? 'skipped' : index < current ? 'done' : index === current ? 'active' : 'waiting'
          return (
            <li key={stage} className={[styles.step, styles[state]].join(' ')} aria-current={state === 'active' ? 'step' : undefined}>
              <span className={styles.marker} aria-hidden="true">
                {state === 'done' && <Check size={11} strokeWidth={3.5} />}
              </span>
              <span>
                {skipped ? 'Sin contenido visual que analizar' : label}
                {note && state !== 'done' && !skipped && <span className={styles.note}> · {note}</span>}
              </span>
            </li>
          )
        })}
        <li className={[styles.step, styles.waiting].join(' ')}>
          <span className={styles.marker} aria-hidden="true" />
          <span>Planificación completada</span>
        </li>
      </ol>
    </div>
  )
}
