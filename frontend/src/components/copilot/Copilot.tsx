import { useRef, useState, type ReactNode } from 'react'
import {
  AlertCircle, CalendarDays, Check, CheckCircle2, ChevronDown, FileText, Sparkles, Upload, Users, X,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Tag } from '@/components/ui/Tag'
import styles from './Copilot.module.css'
import type { MemberWorkload, ProposedTask, SuggestedSchedule } from '@/types/api'
import { PRIORITY_LABELS } from '@/lib/roles'
import { typeOf } from '@/lib/taskTypes'
import { formatDate } from '@/lib/format'
import { SPRINT_DURATIONS } from '@/lib/sprints'

export function PrivacyNote() {
  return (
    <p className={styles.privacy}>
      El modelo corre en tu maquina: el enunciado, las tareas y las fechas de este workspace no salen
      de ella y no se usa ningun servicio externo. Nada se escribe en el tablero sin que lo confirmes.
    </p>
  )
}

/**
 * Todo lo que hace falta para pedirle una planificacion a la IA, en una sola
 * ventana siempre visible: documento o descripcion escrita (una de las dos, no
 * hace falta abrir nada para elegir), a cuantos repartir y el boton de generar.
 * El resultado se pinta debajo, como children.
 */
export function AiWorkbench({
  briefing,
  loading,
  file,
  onFile,
  text,
  onText,
  indications,
  onIndications,
  people,
  maxPeople,
  onPeople,
  onRun,
  running,
  children,
}: {
  /** Guion ya guardado en el proyecto; se usa si no se sube documento ni se escribe nada. */
  briefing: string
  loading?: boolean
  file: File | null
  onFile: (file: File | null) => void
  /** Descripcion escrita a mano, alternativa al documento. */
  text: string
  onText: (text: string) => void
  /** Indicaciones aparte de la fuente (documento, descripcion o guion guardado): a tener en cuenta, no a reemplazarla. */
  indications: string
  onIndications: (indications: string) => void
  people: number
  /** Tamano del equipo. undefined mientras se esta cargando. */
  maxPeople?: number
  onPeople: (people: number) => void
  onRun: () => void
  running: boolean
  children?: ReactNode
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragOver, setDragOver] = useState(false)
  const sinFuente = !file && !text.trim() && !briefing.trim()
  const equipoSinCargar = !maxPeople

  return (
    <div className={styles.workbench}>
      <header className={styles.head}>
        <span className={styles.icon}>
          <Sparkles size={15} strokeWidth={2.75} />
        </span>
        <h2 className={styles.title}>Generar planificacion</h2>
      </header>

      <div className={styles.context}>
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.docx,.txt,.md,.csv"
          className={styles.hiddenInput}
          onChange={(event) => onFile(event.target.files?.[0] ?? null)}
        />

        <div
          className={[styles.drop, dragOver && styles.dropActive, file && styles.dropFilled]
            .filter(Boolean)
            .join(' ')}
          role="button"
          tabIndex={0}
          onClick={() => inputRef.current?.click()}
          onKeyDown={(event) => event.key === 'Enter' && inputRef.current?.click()}
          onDragOver={(event) => {
            event.preventDefault()
            setDragOver(true)
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(event) => {
            event.preventDefault()
            setDragOver(false)
            const dropped = event.dataTransfer.files[0]
            if (dropped) onFile(dropped)
          }}
        >
          {file ? (
            <>
              <FileText size={15} strokeWidth={2.75} />
              <span className={styles.dropName}>{file.name}</span>
              <button
                className={styles.dropClear}
                onClick={(event) => {
                  event.stopPropagation()
                  onFile(null)
                  if (inputRef.current) inputRef.current.value = ''
                }}
                aria-label="Quitar el documento"
              >
                <X size={13} strokeWidth={2.75} />
              </button>
            </>
          ) : (
            <>
              <Upload size={15} strokeWidth={2.75} />
              <span>
                Arrastra aqui el documento de la actividad o pulsa para elegirlo
                <span className={styles.dropHint}> · PDF, Word (.docx) o texto</span>
              </span>
            </>
          )}
        </div>

        {/* Alternativa al documento, no las dos a la vez: con archivo puesto no hace
            falta tambien escribir, y asi queda claro cual de las dos manda. */}
        {!file && (
          <>
            <p className={styles.orDivider}>o describe el trabajo a mano</p>
            <textarea
              className={styles.textarea}
              rows={3}
              value={text}
              placeholder="Tenemos que hacer una practica de bases de datos: modelo E/R, script SQL y memoria de 10 paginas."
              onChange={(event) => onText(event.target.value)}
            />
          </>
        )}

        {!file && !text.trim() && (
          <p className={styles.sourceNote}>
            {loading
              ? 'Cargando el guion guardado en el proyecto…'
              : briefing.trim()
                ? 'Sin documento ni descripcion se usara el guion ya guardado en el proyecto.'
                : 'Este proyecto no tiene guion guardado: sube un documento o describe el trabajo.'}
          </p>
        )}

        <label className={styles.label}>
          Indicaciones para la IA <span className={styles.optional}>opcional</span>
          <input
            className={styles.input}
            value={indications}
            placeholder="P. ej.: que Ana lleve menos carga esta semana, o prioriza el backend"
            onChange={(event) => onIndications(event.target.value)}
          />
        </label>

        <div className={styles.contextFoot}>
          <label className={styles.people}>
            <Users size={14} strokeWidth={2.75} />
            Repartir entre
            <input
              type="number"
              min={1}
              max={maxPeople ?? 1}
              value={maxPeople ? people : ''}
              disabled={!maxPeople}
              onChange={(event) => onPeople(Number(event.target.value))}
              className={styles.peopleInput}
              aria-label="Numero de personas entre las que repartir"
            />
            de {maxPeople ?? '—'}
          </label>
          <span className={styles.spacer} />
          <Button onClick={onRun} disabled={running || loading || sinFuente || equipoSinCargar}>
            {running ? 'Generando…' : 'Generar y repartir'}
          </Button>
        </div>
      </div>

      {children}
    </div>
  )
}

/** Reparto resultante: cuantas tareas y cuantas horas le tocan a cada persona. */
export function WorkloadSummary({ workload }: { workload: MemberWorkload[] }) {
  if (workload.length === 0) return null
  const max = Math.max(...workload.map((item) => item.hours), 1)
  // Si salen menos tareas que personas, alguien se queda a cero: mejor decirlo que
  // dejar una barra vacia sin explicacion.
  const sinTareas = workload.filter((item) => item.tasks === 0).length
  const total = workload.reduce((sum, item) => sum + item.tasks, 0)

  return (
    <div className={styles.workload}>
      <span className={styles.workloadTitle}>Reparto del trabajo</span>
      {workload.map((item) => (
        <div key={item.memberId} className={styles.workloadRow}>
          <span className={styles.workloadName}>{item.memberName}</span>
          <span className={styles.workloadBar}>
            <span className={styles.workloadFill} style={{ width: `${(item.hours / max) * 100}%` }} />
          </span>
          <span className={styles.workloadValue}>
            {item.tasks} {item.tasks === 1 ? 'tarea' : 'tareas'} · {item.hours} h
          </span>
        </div>
      ))}
      {sinTareas > 0 && (
        <span className={styles.workloadNote}>
          {total < workload.length
            ? `Han salido ${total} ${total === 1 ? 'tarea' : 'tareas'} para ${workload.length} personas: no alcanzan para todos.`
            : 'Alguien se queda sin tarea porque las propuestas no se pueden dividir mas.'}
        </span>
      )}
    </div>
  )
}

/** «2 semanas» si encaja con las duraciones habituales; si no, los dias tal cual. */
function sprintDaysLabel(days: number) {
  return SPRINT_DURATIONS.find((option) => option.value === String(days))?.label ?? `${days} dias`
}

/**
 * Fechas y duracion de sprint que el documento menciona explicitamente. Solo
 * aparece cuando hay algo que proponer (el padre ya filtra los null), y solo
 * anuncia los campos que de verdad ha encontrado: si el documento solo dice la
 * duracion del sprint, no se inventa que tambien dijo las fechas.
 */
export function SuggestedScheduleCard({
  schedule,
  onApply,
  applying,
  applied,
}: {
  schedule: SuggestedSchedule
  onApply: () => void
  applying: boolean
  applied: boolean
}) {
  const partes: string[] = []
  if (schedule.startDate) partes.push(`empieza el ${formatDate(schedule.startDate)}`)
  if (schedule.deadline) partes.push(`entrega el ${formatDate(schedule.deadline)}`)
  if (schedule.sprintDays) partes.push(`sprints de ${sprintDaysLabel(schedule.sprintDays)}`)
  if (partes.length === 0) return null

  return (
    <div className={styles.schedule}>
      <span className={styles.scheduleIcon}>
        <CalendarDays size={14} strokeWidth={2.75} />
      </span>
      <span className={styles.scheduleText}>
        El documento dice que {partes.join(', ')}.
      </span>
      <Button variant="secondary" small disabled={applying || applied} onClick={onApply}>
        {applied ? 'Aplicado' : applying ? 'Aplicando…' : 'Usar estos datos'}
      </Button>
    </div>
  )
}

/** Una tarea propuesta: se marca o se desmarca antes de llevarla al tablero. */
function ProposedTaskRow({
  task,
  selected,
  onToggle,
}: {
  task: ProposedTask
  selected: boolean
  onToggle: () => void
}) {
  const tipo = typeOf(task.type)

  return (
    <button
      className={[styles.row, selected ? styles.rowOn : styles.rowOff].join(' ')}
      onClick={onToggle}
      aria-pressed={selected}
    >
      <span className={[styles.check, selected && styles.checkOn].filter(Boolean).join(' ')}>
        <Check size={13} strokeWidth={2.75} />
      </span>
      <span>
        <span className={styles.rowTitle}>{task.title}</span>
        <span className={styles.rowType} style={{ '--tipo': tipo.color } as React.CSSProperties}>
          <span className={styles.rowTypeDot} aria-hidden="true" />
          {tipo.label}
        </span>
        {task.reason && <span className={styles.rowReason}>{task.reason}</span>}
      </span>
      {task.assigneeName && (
        <Tag small variant={selected ? 'accent2' : 'neutral'}>
          {task.assigneeName}
        </Tag>
      )}
      <span className={styles.estimate}>
        {task.estimatedHours ? `${task.estimatedHours} h` : ''}
        {task.priority ? ` · ${PRIORITY_LABELS[task.priority].toLowerCase()}` : ''}
        {task.dueDate && (
          <span className={styles.rowDue}>
            <CalendarDays size={11} strokeWidth={2} />
            {formatDate(task.dueDate)}
          </span>
        )}
      </span>
    </button>
  )
}

/** Propuesta completa: nada llega al tablero hasta que se pulsa «Anadir». */
export function ProposalPanel({
  tasks,
  selected,
  onToggle,
  onApply,
  onRegenerate,
  applying,
  model,
}: {
  tasks: ProposedTask[]
  selected: number[]
  onToggle: (index: number) => void
  onApply: () => void
  onRegenerate: () => void
  applying: boolean
  model: string
}) {
  return (
    <div className={styles.proposal}>
      <header className={styles.proposalHead}>
        <h3 className={styles.proposalTitle}>Propuesta: {tasks.length} tareas</h3>
        <span className={styles.note}>
          {selected.length} seleccionadas · modelo {model} en local
        </span>
      </header>

      <div className={styles.proposalRows}>
        {tasks.map((task, index) => (
          <ProposedTaskRow
            key={`${task.title}-${index}`}
            task={task}
            selected={selected.includes(index)}
            onToggle={() => onToggle(index)}
          />
        ))}
      </div>

      <div className={styles.proposalFoot}>
        <Button onClick={onApply} disabled={selected.length === 0 || applying}>
          {applying ? 'Anadiendo…' : 'Anadir al tablero'}
        </Button>
        <Button variant="secondary" onClick={onRegenerate} disabled={applying}>
          Regenerar
        </Button>
        <span className={styles.note}>Se crearan en la primera columna, cada una con su responsable</span>
      </div>
    </div>
  )
}

/**
 * Confirmacion de que las tareas ya estan en el tablero, en vez de dejar la
 * propuesta tal cual estaba (parece que no ha pasado nada, o que se ha
 * quedado atascada). Plegada por defecto: el titulo con el numero ya basta,
 * la lista de que se ha anadido esta a un clic si hace falta comprobarlo.
 */
export function AppliedTasksSummary({ titles }: { titles: string[] }) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div className={styles.applied}>
      <button
        type="button"
        className={styles.appliedHead}
        onClick={() => setExpanded((current) => !current)}
        aria-expanded={expanded}
      >
        <CheckCircle2 size={16} strokeWidth={2.75} />
        <span>
          {titles.length} {titles.length === 1 ? 'tarea anadida' : 'tareas anadidas'} al tablero
        </span>
        <ChevronDown className={[styles.appliedChevron, expanded && styles.appliedChevronOpen]
          .filter(Boolean).join(' ')} size={14} strokeWidth={2.75} />
      </button>
      {expanded && (
        <ul className={styles.appliedList}>
          {titles.map((title, index) => (
            <li key={index}>{title}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** Error del modulo de IA con su causa y la accion de reintentar. */
export function AiErrorCard({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className={styles.error}>
      <span className={styles.errorHead}>
        <AlertCircle size={14} strokeWidth={2.75} />
        No se ha podido generar la planificacion
      </span>
      <p className={styles.errorBody}>{message}</p>
      <Button variant="secondary" small onClick={onRetry}>
        Reintentar
      </Button>
    </div>
  )
}

