import { useRef, useState, type ReactNode } from 'react'
import {
  AlertCircle, AlertTriangle, CalendarDays, Check, CheckCircle2, ChevronDown, FileText, Plus, Upload, Users, X,
} from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { Tag } from '@/components/ui/Tag'
import styles from './PlanGenerator.module.css'
import type { MemberWorkload, PlannedSprint, ProposedTask, SuggestedSchedule, TeamMemberResponse } from '@/types/api'
import type { PlanTeamDraft } from '@/state/context'
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
 * Todo lo que hace falta para pedirle un plan a la IA, en una sola ventana
 * siempre visible: documento o descripcion escrita (una de las dos, no hace falta
 * abrir nada para elegir), el calendario, quien entra en el reparto y con que
 * datos, y el boton de generar. El resultado se pinta debajo, como children.
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
  startDate,
  onStartDate,
  workspaceStartDate,
  onGoToSettings,
  team,
  members,
  onTeam,
  onSaveNotes,
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
  /** Fecha de inicio con la que se va a generar: la del workspace, o la elegida a mano. */
  startDate: string
  onStartDate: (startDate: string) => void
  /**
   * Fecha de inicio del workspace, si la tiene. Con ella, el calendario entero
   * (inicio y duracion del sprint) es automatico y no se pregunta nada; sin
   * ella, hace falta un inicio a mano porque no hay de donde sacarlo.
   */
  workspaceStartDate?: string | null
  onGoToSettings: () => void
  /** Todas las personas del equipo, incluidas o no en este reparto. undefined mientras carga. */
  team?: PlanTeamDraft[]
  /** Para comparar contra lo guardado y saber si las preferencias de alguien han cambiado. */
  members?: TeamMemberResponse[]
  onTeam: (team: PlanTeamDraft[]) => void
  /** Guarda las preferencias de una persona en su ficha: valdran para todos los proximos planes. */
  onSaveNotes: (userId: number, preferences: string) => Promise<unknown>
  onRun: () => void
  running: boolean
  children?: ReactNode
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragOver, setDragOver] = useState(false)
  const sinFuente = !file && !text.trim() && !briefing.trim()
  const incluidos = team?.filter((person) => person.included) ?? []
  const sinEquipo = incluidos.length === 0

  // Sin titulo propio: la pestana de arriba ya dice «Generar plan» y repetirlo
  // dentro de la tarjeta solo gasta la primera pantalla sin decir nada nuevo.
  return (
    <div className={styles.workbench}>
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

        {/* El calendario es automatico siempre que se pueda: la fecha de inicio y la
            duracion del sprint son las que ya tiene el workspace, sin preguntar nada.
            Solo hace falta un dato a mano cuando de verdad falta —el workspace no
            tiene fecha de inicio—, y basta con esa: cuantos sprints hacen falta y
            donde acaba cada tarea lo reparte el modelo solo. */}
        {workspaceStartDate ? (
          <p className={styles.autoNote}>
            <CalendarDays size={13} strokeWidth={2.5} />
            Calendario automatico: empieza el {formatDate(workspaceStartDate)}, con la duracion de
            sprint del workspace.
            <button type="button" className={styles.autoChange} onClick={onGoToSettings}>
              Cambiar en Ajustes
            </button>
          </p>
        ) : (
          <label className={styles.label}>
            Fecha de inicio <span className={styles.optional}>este workspace no tiene una fijada</span>
            <input
              type="date"
              className={styles.input}
              value={startDate}
              onChange={(event) => event.target.value && onStartDate(event.target.value)}
            />
          </label>
        )}

        <TeamEditor team={team} members={members} onTeam={onTeam} onSaveNotes={onSaveNotes} />

        <label className={styles.label}>
          Indicaciones para este reparto <span className={styles.optional}>opcional</span>
          <input
            className={styles.input}
            value={indications}
            placeholder="P. ej.: que Ana lleve menos carga esta semana, o que Luis se centre en el backend"
            onChange={(event) => onIndications(event.target.value)}
          />
          {/* Lo dice aqui porque es justo la duda que produce tener dos campos de texto
              parecidos uno encima del otro: este se olvida al generar, la ficha de
              arriba se queda. */}
          <span className={styles.optional}>
            Solo valen para esta vez. Lo que sea siempre cierto de alguien va en su ficha, arriba.
          </span>
        </label>

        <div className={styles.contextFoot}>
          <span className={styles.note}>
            {team === undefined
              ? 'Cargando el equipo…'
              : sinEquipo
                ? 'Incluye al menos a una persona en el reparto'
                : `Se repartira entre ${incluidos.length} ${incluidos.length === 1 ? 'persona' : 'personas'}`}
          </span>
          <span className={styles.spacer} />
          <Button onClick={onRun} disabled={running || loading || sinFuente || sinEquipo}>
            {running ? 'Generando…' : 'Generar y repartir'}
          </Button>
        </div>
      </div>

      {children}
    </div>
  )
}

/**
 * Quien entra en este reparto y con que datos.
 *
 * Salen siempre del equipo real del workspace: aqui no se puede meter a nadie de
 * fuera, porque una tarea para alguien que no es miembro llegaria al tablero sin
 * responsable. Quitar a alguien no borra lo que se habia escrito de el: queda
 * aparte para volver a incluirlo. Nada de esto se guarda en su ficha de Miembros.
 */
function TeamEditor({
  team,
  members,
  onTeam,
  onSaveNotes,
}: {
  team?: PlanTeamDraft[]
  members?: TeamMemberResponse[]
  onTeam: (team: PlanTeamDraft[]) => void
  onSaveNotes: (userId: number, preferences: string) => Promise<unknown>
}) {
  const [guardandoId, setGuardandoId] = useState<number | null>(null)
  if (!team) return null

  const cambiar = (userId: number, cambios: Partial<PlanTeamDraft>) =>
    onTeam(team.map((person) => (person.userId === userId ? { ...person, ...cambios } : person)))
  const incluidos = team.filter((person) => person.included)
  const fuera = team.filter((person) => !person.included)

  /** Lo que ya hay guardado en la ficha de esa persona, para saber si ha cambiado. */
  const fichaDe = (userId: number) => members?.find((member) => member.user.id === userId)?.planNotes ?? ''

  async function guardarFicha(userId: number, preferences: string) {
    setGuardandoId(userId)
    try {
      await onSaveNotes(userId, preferences.trim())
    } finally {
      setGuardandoId(null)
    }
  }

  return (
    <div className={styles.team}>
      <span className={styles.teamTitle}>
        <Users size={13} strokeWidth={2.75} />
        Equipo para este reparto
      </span>

      {incluidos.length > 0 && (
        <div className={styles.teamHead} aria-hidden="true">
          <span>Persona</span>
          <span>Preferencias y restricciones</span>
          <span>h/semana</span>
          <span />
        </div>
      )}

      {incluidos.map((person) => {
        const guardada = fichaDe(person.userId)
        const cambiado = person.preferences.trim() !== guardada.trim()
        return (
          <div key={person.userId} className={styles.teamRow}>
            <span className={styles.teamName}>{person.name}</span>
            <div className={styles.prefCell}>
              <input
                className={styles.input}
                value={person.preferences}
                placeholder="Prefiere backend; vacaciones la semana 3…"
                aria-label={`Preferencias de ${person.name}`}
                maxLength={600}
                onChange={(event) => cambiar(person.userId, { preferences: event.target.value })}
              />
              {cambiado && (
                <button
                  type="button"
                  className={styles.prefSave}
                  disabled={guardandoId === person.userId}
                  aria-label={`Guardar las preferencias de ${person.name} en su ficha`}
                  title="Guardar en su ficha: valdra para los proximos planes"
                  onClick={() => guardarFicha(person.userId, person.preferences)}
                >
                  <Check size={13} strokeWidth={2.75} />
                </button>
              )}
            </div>
            <input
              type="number"
              min={0}
              max={60}
              className={styles.input}
              value={person.hoursPerWeek}
              aria-label={`Horas por semana de ${person.name}`}
              onChange={(event) =>
                cambiar(person.userId, { hoursPerWeek: Math.max(0, Math.min(60, Number(event.target.value) || 0)) })
              }
            />
            <button
              type="button"
              className={styles.teamRemove}
              aria-label={`Quitar a ${person.name} de este reparto`}
              onClick={() => cambiar(person.userId, { included: false })}
            >
              <X size={13} strokeWidth={2.75} />
            </button>
          </div>
        )
      })}

      {fuera.length > 0 && (
        <div className={styles.teamOut}>
          {fuera.map((person) => (
            <button
              key={person.userId}
              type="button"
              className={styles.teamAdd}
              onClick={() => cambiar(person.userId, { included: true })}
            >
              <Plus size={12} strokeWidth={2.75} />
              Volver a incluir a {person.name}
            </button>
          ))}
        </div>
      )}

      <span className={styles.optional}>
        Las horas por semana son solo para este reparto: con ellas se calcula cuanto le cabe en
        cada sprint. Las preferencias son la ficha de cada persona —el rol incluido, si hace
        falta decirlo— : pulsa <Check size={11} strokeWidth={2.75} /> para guardarlas y que
        valgan tambien en los proximos planes.
      </span>
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
 * Las fechas con las que se ha hecho el plan que no coinciden con las guardadas
 * en el workspace. Guardarlas hace que la pestana Plan pinte exactamente estos
 * sprints; si no, las tareas caerian entre dos.
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
  if (schedule.deadline) partes.push(`termina el ${formatDate(schedule.deadline)}`)
  if (schedule.sprintDays) partes.push(`usa sprints de ${sprintDaysLabel(schedule.sprintDays)}`)
  if (partes.length === 0) return null

  return (
    <div className={styles.schedule}>
      <span className={styles.scheduleIcon}>
        <CalendarDays size={14} strokeWidth={2.75} />
      </span>
      <span className={styles.scheduleText}>
        Este plan {partes.join(', ')}. Guardalo en el workspace para que la pestana Plan muestre los
        mismos sprints.
      </span>
      <Button variant="secondary" small disabled={applying || applied} onClick={onApply}>
        {applied ? 'Guardado' : applying ? 'Guardando…' : 'Guardar en el workspace'}
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
          {task.epic && <span className={styles.rowEpic}>{task.epic}</span>}
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

/** «5 oct – 16 oct». */
function sprintDates(sprint: PlannedSprint) {
  return `${formatDate(sprint.start)} – ${formatDate(sprint.end)}`
}

/**
 * Propuesta completa, agrupada por sprint: nada llega al tablero hasta que se
 * pulsa «Anadir». Cada tarea conserva su indice en la lista original, que es con
 * lo que se marca y se desmarca, asi que agrupar no cambia la seleccion.
 */
export function ProposalPanel({
  tasks,
  sprints,
  selected,
  onToggle,
  onApply,
  onRegenerate,
  applying,
  model,
}: {
  tasks: ProposedTask[]
  sprints: PlannedSprint[]
  selected: number[]
  onToggle: (index: number) => void
  onApply: () => void
  onRegenerate: () => void
  applying: boolean
  model: string
}) {
  const conIndice = tasks.map((task, index) => ({ task, index }))
  const numeros = new Set(sprints.map((sprint) => sprint.number))
  // Una tarea con un sprint que no existe no se pierde: va al final, aparte.
  const sueltas = conIndice.filter(({ task }) => !task.sprint || !numeros.has(task.sprint))

  const fila = ({ task, index }: { task: ProposedTask; index: number }) => (
    <ProposedTaskRow
      key={`${task.title}-${index}`}
      task={task}
      selected={selected.includes(index)}
      onToggle={() => onToggle(index)}
    />
  )

  return (
    <div className={styles.proposal}>
      <header className={styles.proposalHead}>
        <h3 className={styles.proposalTitle}>
          Propuesta: {tasks.length} tareas en {sprints.length} {sprints.length === 1 ? 'sprint' : 'sprints'}
        </h3>
        <span className={styles.note}>
          {selected.length} seleccionadas · modelo {model} en local
        </span>
      </header>

      {sprints.map((sprint) => {
        const suyas = conIndice.filter(({ task }) => task.sprint === sprint.number)
        return (
          <section key={sprint.number} className={styles.sprint}>
            <header className={styles.sprintHead}>
              <span className={styles.sprintTitle}>Sprint {sprint.number}</span>
              <span className={styles.sprintDates}>
                <CalendarDays size={11} strokeWidth={2} />
                {sprintDates(sprint)}
              </span>
              <span className={styles.note}>{suyas.length} {suyas.length === 1 ? 'tarea' : 'tareas'}</span>
            </header>
            {sprint.goal && <p className={styles.sprintGoal}>{sprint.goal}</p>}
            <div className={styles.proposalRows}>
              {suyas.length === 0
                ? <span className={styles.note}>Sin tareas en este sprint.</span>
                : suyas.map(fila)}
            </div>
          </section>
        )
      })}

      {sueltas.length > 0 && (
        <section className={styles.sprint}>
          <header className={styles.sprintHead}>
            <span className={styles.sprintTitle}>Sin sprint</span>
          </header>
          <div className={styles.proposalRows}>{sueltas.map(fila)}</div>
        </section>
      )}

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
 * Lo que el plan no ha podido resolver solo: los avisos del propio modelo y las
 * reglas que siguen sin cumplirse tras pedirle que corrija. Se ensena encima de
 * la propuesta para que se lea antes de aplicarla, no despues.
 */
export function PlanWarnings({ warnings }: { warnings: string[] }) {
  if (warnings.length === 0) return null
  return (
    <div className={styles.warnings}>
      <span className={styles.warningsHead}>
        <AlertTriangle size={14} strokeWidth={2.75} />
        {warnings.length === 1 ? 'Un aviso' : `${warnings.length} avisos`} sobre este plan
      </span>
      <ul className={styles.warningsList}>
        {warnings.map((warning, index) => (
          <li key={index}>{warning}</li>
        ))}
      </ul>
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

