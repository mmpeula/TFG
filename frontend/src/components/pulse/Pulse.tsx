import { AlertTriangle, ArrowRight, CalendarDays, Clock3, PauseCircle, TrendingUp, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import styles from './Pulse.module.css'
import type { Insight, InsightAction, InsightTask } from '@/lib/metrics'

/** Icono de cada motivo de aviso en la lista de tareas afectadas. */
const TASK_ICON: Record<InsightTask['kind'], typeof Clock3> = {
  fecha: Clock3,
  parada: PauseCircle,
  'sin-responsable': UserRound,
}

/**
 * Un insight a todo lo ancho: a la izquierda el icono, que pasa, por que y el
 * boton para ir a resolverlo; a la derecha, separado por una linea, el resumen en
 * cifras (riesgo de entrega) o las tareas a las que se refiere (avisos).
 *
 * Blanco como el resto de tarjetas del panel; el color va en el icono, la etiqueta
 * y el boton: rojo si no se llega a la entrega, ambar los avisos, verde si el ritmo
 * llega a tiempo.
 */
export function InsightCard({
  insight,
  onAction,
}: {
  insight: Insight
  onAction?: (action: InsightAction) => void
}) {
  const { variant } = insight
  return (
    <article className={[styles.insight, styles[variant]].join(' ')}>
      <span className={styles.icon} aria-hidden="true">
        {variant === 'ok' ? <TrendingUp /> : <AlertTriangle />}
      </span>

      <div className={styles.main}>
        <span className={styles.kicker}>{insight.kicker}</span>
        <h3 className={styles.title}>{insight.title}</h3>
        <p className={styles.body}>{insight.body}</p>
        <div className={styles.actions}>
          {insight.actions.map((action) => (
            <Button
              key={action.label}
              variant={variant === 'risk' && action.primary ? 'destructive' : 'secondary'}
              onClick={() => onAction?.(action)}
            >
              {action.label}
              <ArrowRight size={15} strokeWidth={2.25} aria-hidden="true" />
            </Button>
          ))}
        </div>
      </div>

      {(insight.facts || insight.tasks) && (
        <aside className={styles.side}>
          {insight.recommended && <span className={styles.recommended}>Acción recomendada</span>}

          {insight.facts && (
            <>
              <span className={styles.sideTitle}>Resumen</span>
              <ul className={styles.facts}>
                {insight.facts.map((fact) => {
                  const Icon = fact.icon === 'calendar' ? CalendarDays : TrendingUp
                  return (
                    <li key={fact.text}>
                      <Icon size={17} strokeWidth={2} aria-hidden="true" />
                      <span>
                        {fact.text}{' '}
                        <b className={fact.bad ? styles.bad : undefined}>{fact.value}</b>
                      </span>
                    </li>
                  )
                })}
              </ul>
            </>
          )}

          {insight.tasks && (
            <>
              <span className={styles.sideTitle}>Tareas afectadas</span>
              <ul className={styles.tasks}>
                {insight.tasks.map((task) => {
                  const Icon = TASK_ICON[task.kind]
                  return (
                    <li key={task.taskId}>
                      <span className={[styles.taskIcon, styles[`task_${task.kind}`]].join(' ')} aria-hidden="true">
                        <Icon size={16} strokeWidth={2.25} />
                      </span>
                      <span className={styles.taskText}>
                        <span className={styles.taskTitle}>{task.title}</span>
                        <span className={styles.taskDetail}>{task.detail}</span>
                      </span>
                    </li>
                  )
                })}
              </ul>
              {(insight.moreTasks ?? 0) > 0 && <span className={styles.more}>y {insight.moreTasks} más</span>}
            </>
          )}
        </aside>
      )}
    </article>
  )
}
