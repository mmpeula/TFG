import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { Modal } from '@/components/ui/Modal'
import styles from './ActualHoursDialog.module.css'
import { hoursNumber, parseHours } from '@/lib/hours'
import type { TaskResponse } from '@/types/api'

/**
 * Lo que se pregunta al terminar una tarea: cuanto ha costado, mas o menos.
 *
 * Es el dato con el que PlanifIA aprende el ritmo de cada persona (horas reales
 * frente a estimadas, ver AiPlanService.historicalPace). Tiene que costar casi nada,
 * o nadie lo apunta: un solo campo en horas («1,5» es hora y media), ya relleno con
 * la estimacion. No hace falta ser exacto. Se puede dejar para luego: el campo
 * sigue en el panel de la tarea.
 */
export function ActualHoursDialog({
  task,
  saving,
  onSave,
  onClose,
}: {
  task: TaskResponse | null
  saving: boolean
  onSave: (hours: number) => void
  onClose: () => void
}) {
  if (!task) return null
  // Solo existe mientras esta abierto: cada tarea empieza con su propia estimacion.
  return <ActualHoursForm key={task.id} task={task} saving={saving} onSave={onSave} onClose={onClose} />
}

function ActualHoursForm({
  task, saving, onSave, onClose,
}: {
  task: TaskResponse
  saving: boolean
  onSave: (hours: number) => void
  onClose: () => void
}) {
  // Ya relleno con lo estimado: si fue asi, basta con pulsar Guardar.
  const [text, setText] = useState(task.estimatedHours != null ? hoursNumber(task.estimatedHours) : '')
  const hours = parseHours(text)

  function guardar() {
    if (hours !== null && !saving) onSave(hours)
  }

  return (
    <Modal
      open
      title="Tarea terminada"
      onClose={onClose}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Ahora no
          </Button>
          <Button onClick={guardar} disabled={hours === null || saving}>
            {saving ? 'Guardando…' : 'Guardar'}
          </Button>
        </>
      }
    >
      <label className={styles.body}>
        ¿Cuántas horas te ha llevado?
        <span className={styles.field}>
          <input
            className={styles.input}
            value={text}
            inputMode="decimal"
            autoFocus
            onFocus={(event) => event.target.select()}
            placeholder="Por ejemplo: 1,5"
            onChange={(event) => setText(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && guardar()}
          />
          <span className={styles.unit}>horas</span>
        </span>
        {text.trim() !== '' && hours === null && (
          <span className={styles.error}>Escribe las horas con un número, por ejemplo 1,5.</span>
        )}
      </label>
    </Modal>
  )
}
