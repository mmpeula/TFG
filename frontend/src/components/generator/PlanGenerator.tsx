import { useRef, useState, type ReactNode } from 'react'
import {
  AlertCircle, AlertTriangle, CalendarDays, Check, CheckCircle2, ChevronDown, FileText, Plus, Settings,
  Sparkles, Upload, X,
} from 'lucide-react'
import { DateInput } from '@/components/ui/DateInput'
import { Avatar } from '@/components/ui/Avatar'
import { BreakableText } from '@/components/ui/BreakableText'
import { Button } from '@/components/ui/Button'
import { Card, CardGroup, InfoNote } from '@/components/ui/Card'
import { Tag } from '@/components/ui/Tag'
import styles from './PlanGenerator.module.css'
import type { MemberWorkload, PlannedSprint, ProposedTask, TeamMemberResponse } from '@/types/api'
import type { PlanTeamDraft } from '@/state/context'
import { PRIORITY_LABELS } from '@/lib/roles'
import { typeOf } from '@/lib/taskTypes'
import { formatDate } from '@/lib/format'
import { formatHours } from '@/lib/hours'

/** Que sale de la app al generar: la misma nota con «i» que el resto, en azul porque es de la IA. */
export function PrivacyNote() {
  return (
    <InfoNote tone="accent">
      El enunciado, el equipo y las tareas se envían a DeepSeek para generar el plan. Nada se escribe en
      el tablero sin que lo confirmes.
    </InfoNote>
  )
}

/**
 * Todo lo que hace falta para pedirle un plan a la IA, una tarjeta por cosa y
 * todas a la vista: documento o descripcion escrita (una de las dos, no hace
 * falta abrir nada para elegir), el calendario, quien entra en el reparto y
 * con que datos, y el boton de generar. El resultado se pinta debajo, como
 * children. Las tarjetas van en azul porque toda la pantalla es de la IA.
 */
export function AiWorkbench({
  briefing,
  loading,
  file,
  onFile,
  text,
  onText,
  startDate,
  deadline,
  onStartDate,
  onDeadline,
  workspaceStartDate,
  workspaceDeadline,
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
  /** Fecha de inicio efectiva (del workspace o elegida a mano). Vacia si falta por rellenar. */
  startDate: string
  /** Fecha de fin efectiva. Vacia si falta por rellenar. */
  deadline: string
  onStartDate: (value: string) => void
  onDeadline: (value: string) => void
  /**
   * Fechas ya fijadas en el workspace. Con ambas, el calendario entero es
   * automatico y no se pregunta nada; si falta alguna, se piden las dos aqui
   * mismo, antes de poder generar (ver {@link DateFields}).
   */
  workspaceStartDate?: string | null
  workspaceDeadline?: string | null
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
  const sinFechas = !startDate || !deadline
  const calendarioAutomatico = Boolean(workspaceStartDate && workspaceDeadline)

  // Sin titulo propio: la pestana de arriba ya dice «Generar plan» y repetirlo
  // solo gasta la primera pantalla sin decir nada nuevo.
  return (
    <div className={styles.workbench}>
      <input
        ref={inputRef}
        type="file"
        accept=".pdf,.docx,.txt,.md,.csv"
        className={styles.hiddenInput}
        onChange={(event) => onFile(event.target.files?.[0] ?? null)}
      />

      <CardGroup>
        <Card
          title="Documento de la actividad"
          description="Sube el enunciado o documento para que se genere el plan."
          aside={
            <>
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
                    <FileText size={15} strokeWidth={2} />
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
                    <Upload size={18} strokeWidth={2} />
                    <span className={styles.dropText}>
                      Arrastra aquí el documento o pulsa para elegirlo
                      <span className={styles.dropHint}>PDF, Word (.docx) o texto</span>
                    </span>
                  </>
                )}
              </div>
              <Button variant="secondary" className={styles.pickButton} onClick={() => inputRef.current?.click()}>
                <Upload size={14} strokeWidth={2.5} />
                {file ? 'Cambiar archivo' : 'Seleccionar archivo'}
              </Button>
            </>
          }
        />

        {/* Alternativa al documento, no las dos a la vez: con archivo puesto no hace
            falta tambien escribir, y asi queda claro cual de las dos manda. */}
        {!file && (
          <Card
            title="Descripción del trabajo"
            description={
              text.trim()
                ? 'Cuéntanos qué tienes que hacer.'
                : loading
                  ? 'Cargando el guion guardado en el proyecto…'
                  : briefing.trim()
                    ? 'Si lo dejas vacío, se usará el guion ya guardado en el proyecto.'
                    : 'Cuéntanos qué tienes que hacer, o sube un documento.'
            }
            aside={
              <div className={styles.textareaWrap}>
                <textarea
                  className={styles.textarea}
                  rows={2}
                  value={text}
                  placeholder="Tenemos que hacer una práctica de bases de datos: modelo E/R, script SQL y memoria de 10 páginas."
                  aria-label="Descripción del trabajo"
                  onChange={(event) => onText(event.target.value)}
                />
                <span className={styles.counter}>{text.length} caracteres</span>
              </div>
            }
          />
        )}

        {/* Las fechas son lo primero que hace falta resolver, antes de generar nada:
            con las dos ya fijadas en el workspace el calendario es automatico; si
            falta alguna, se piden aqui mismo y no se genera hasta tenerlas. */}
        <Card
          title={calendarioAutomatico ? 'Calendario automático' : 'Calendario del plan'}
          description={
            calendarioAutomatico
              ? 'Se generará según la duración del sprint del espacio de trabajo.'
              : 'El espacio de trabajo aún no tiene fechas: indica entre cuándo y cuándo repartir las tareas.'
          }
          aside={
            calendarioAutomatico ? (
              <>
                <span className={styles.calendarRange}>
                  <CalendarDays size={16} strokeWidth={2} />
                  De {formatDate(workspaceStartDate)} a {formatDate(workspaceDeadline)}
                </span>
                <Button variant="secondary" onClick={onGoToSettings}>
                  <Settings size={14} strokeWidth={2.5} />
                  Cambiar en ajustes
                </Button>
              </>
            ) : (
              <DateFields startDate={startDate} deadline={deadline} onStartDate={onStartDate} onDeadline={onDeadline} />
            )
          }
        />

        <TeamEditor team={team} members={members} onTeam={onTeam} onSaveNotes={onSaveNotes}>
          <div className={styles.runFoot}>
            <span className={styles.note}>
              {team === undefined
                ? 'Cargando el equipo…'
                : sinEquipo
                  ? 'Incluye al menos a una persona en el reparto'
                  : `Se repartirá entre ${incluidos.length} ${incluidos.length === 1 ? 'persona' : 'personas'}`}
            </span>
            <Button variant="accent" onClick={onRun} disabled={running || loading || sinFuente || sinEquipo || sinFechas}>
              <Sparkles size={14} strokeWidth={2.5} />
              {running ? 'Generando…' : 'Generar y repartir'}
            </Button>
          </div>
        </TeamEditor>
      </CardGroup>

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
 * aparte para volver a incluirlo. Nada de esto se guarda en su ficha de Miembros
 * salvo que se pulse el check.
 */
function TeamEditor({
  team,
  members,
  onTeam,
  onSaveNotes,
  children,
}: {
  team?: PlanTeamDraft[]
  members?: TeamMemberResponse[]
  onTeam: (team: PlanTeamDraft[]) => void
  onSaveNotes: (userId: number, preferences: string) => Promise<unknown>
  /** Pie de la tarjeta: el recuento y el boton de generar. */
  children?: ReactNode
}) {
  const [guardandoId, setGuardandoId] = useState<number | null>(null)

  const cambiar = (userId: number, cambios: Partial<PlanTeamDraft>) =>
    onTeam((team ?? []).map((person) => (person.userId === userId ? { ...person, ...cambios } : person)))
  const incluidos = team?.filter((person) => person.included) ?? []
  const fuera = team?.filter((person) => !person.included) ?? []

  /** Lo que ya hay guardado en la ficha de esa persona, para saber si ha cambiado. */
  const fichaDe = (userId: number) => members?.find((member) => member.user.id === userId)?.planNotes ?? ''
  /** Para el avatar: la foto, si la tiene. */
  const avatarDe = (userId: number) => members?.find((member) => member.user.id === userId)?.user.avatarUrl ?? null

  async function guardarFicha(userId: number, preferences: string) {
    setGuardandoId(userId)
    try {
      await onSaveNotes(userId, preferences.trim())
    } finally {
      setGuardandoId(null)
    }
  }

  return (
    <Card
      title="Equipo para este reparto"
      description="Define las preferencias de cada miembro del equipo."
    >
      {team && incluidos.length > 0 && (
        <div className={styles.team}>
          <div className={styles.teamHead} aria-hidden="true">
            <span>Persona</span>
            <span>Preferencias y restricciones</span>
            <span />
          </div>

          {incluidos.map((person) => {
            const guardada = fichaDe(person.userId)
            const cambiado = person.preferences.trim() !== guardada.trim()
            return (
              <div key={person.userId} className={styles.teamRow}>
                <span className={styles.teamPerson}>
                  <Avatar
                    user={{ id: person.userId, fullName: person.name }}
                    avatarUrl={avatarDe(person.userId)}
                    size={28}
                  />
                  <span className={styles.teamName}>{person.name}</span>
                </span>
                <div className={styles.prefCell}>
                  <input
                    className={styles.input}
                    value={person.preferences}
                    placeholder="Prefiere backend; menos horas esta vez; vacaciones la semana 3…"
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
                      title="Guardar en su ficha: valdrá para los próximos planes"
                      onClick={() => guardarFicha(person.userId, person.preferences)}
                    >
                      <Check size={13} strokeWidth={2.75} />
                    </button>
                  )}
                </div>
                <button
                  type="button"
                  className={styles.teamRemove}
                  aria-label={`Quitar a ${person.name} de este reparto`}
                  title="Quitar de este reparto"
                  onClick={() => cambiar(person.userId, { included: false })}
                >
                  <X size={14} strokeWidth={2.5} />
                </button>
              </div>
            )
          })}
        </div>
      )}

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

      <InfoNote tone="accent">
        Las preferencias son la <b>ficha de cada persona</b> —el rol incluido, y si tiene menos o más
        tiempo que de costumbre esta vez—; pulsa <Check size={12} strokeWidth={2.75} /> para guardarlas y
        que valgan también en los próximos planes.
      </InfoNote>

      {children}
    </Card>
  )
}

/**
 * Fecha de inicio y de fin del plan, lo primero que hace falta resolver antes
 * de poder generar nada: sin las dos, no se sabe entre que ventana repartir
 * las tareas y habria que adivinar (y luego regenerar si la adivinanza
 * fallaba, una segunda llamada entera al modelo). Siempre editables, aunque
 * vengan prerellenas de lo detectado en el documento.
 */
function DateFields({
  startDate,
  deadline,
  onStartDate,
  onDeadline,
}: {
  startDate: string
  deadline: string
  onStartDate: (value: string) => void
  onDeadline: (value: string) => void
}) {
  return (
    <div className={styles.dates}>
      <label className={styles.label}>
        Fecha de inicio
        <DateInput
          className={styles.input}
          value={startDate}
          onChange={onStartDate}
        />
        {!startDate && <span className={styles.dateMissing}>Falta la fecha de inicio</span>}
      </label>
      <label className={styles.label}>
        Fecha de fin
        <DateInput
          className={styles.input}
          value={deadline}
          onChange={onDeadline}
        />
        {!deadline && <span className={styles.dateMissing}>Falta la fecha de fin</span>}
      </label>
    </div>
  )
}

/**
 * Reparto resultante: cuantas tareas y horas le tocan a cada persona. La barra
 * es relativa a quien mas horas lleva del propio equipo, no contra una
 * capacidad fija: no hay horas por semana con las que compararla.
 */
export function WorkloadSummary({ workload }: { workload: MemberWorkload[] }) {
  if (workload.length === 0) return null
  // Si salen menos tareas que personas, alguien se queda a cero: mejor decirlo que
  // dejar una barra vacia sin explicacion.
  const sinTareas = workload.filter((item) => item.tasks === 0).length
  const total = workload.reduce((sum, item) => sum + item.tasks, 0)
  const maxHoras = Math.max(0, ...workload.map((item) => item.hours))

  return (
    <div className={styles.workload}>
      <span className={styles.workloadTitle}>Reparto del trabajo</span>
      {workload.map((item) => {
        const uso = maxHoras > 0 ? item.hours / maxHoras : 0
        return (
          <div key={item.memberId} className={styles.workloadRow}>
            <span className={styles.workloadName}>{item.memberName}</span>
            <span className={styles.workloadBar}>
              <span className={styles.workloadFill} style={{ width: `${uso * 100}%` }} />
            </span>
            <span className={styles.workloadValue}>
              {item.tasks} {item.tasks === 1 ? 'tarea' : 'tareas'} · {formatHours(item.hours)}
            </span>
          </div>
        )
      })}
      {sinTareas > 0 && (
        <span className={styles.workloadNote}>
          {total < workload.length
            ? `Han salido ${total} ${total === 1 ? 'tarea' : 'tareas'} para ${workload.length} personas: no alcanzan para todos.`
            : 'Alguien se queda sin tarea porque las propuestas no se pueden dividir más.'}
        </span>
      )}
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
        <span className={styles.rowTitle}><BreakableText>{task.title}</BreakableText></span>
        <span className={styles.rowType} style={{ '--tipo': tipo.color } as React.CSSProperties}>
          <span className={styles.rowTypeDot} aria-hidden="true" />
          {tipo.label}
          {task.epic && <span className={styles.rowEpic}>{task.epic}</span>}
        </span>
        {task.reason && <span className={styles.rowReason}>{task.reason}</span>}
      </span>
      {task.assigneeNames.map((name) => (
        <Tag key={name} small variant={selected ? 'accent2' : 'neutral'}>
          {name}
        </Tag>
      ))}
      <span className={styles.estimate}>
        {task.estimatedHours ? formatHours(task.estimatedHours) : ''}
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
  children,
}: {
  tasks: ProposedTask[]
  sprints: PlannedSprint[]
  selected: number[]
  onToggle: (index: number) => void
  onApply: () => void
  onRegenerate: () => void
  applying: boolean
  model: string
  /** Lo que se lee antes de las tareas: de donde sale el plan, avisos y reparto. */
  children?: ReactNode
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
    <CardGroup>
      <Card
        title={`Propuesta: ${tasks.length} tareas en ${sprints.length} ${sprints.length === 1 ? 'sprint' : 'sprints'}`}
        description={`${selected.length} seleccionadas · modelo ${model}. Marca o desmarca antes de llevarlas al tablero.`}
      >
        {children}

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
            {applying ? 'Añadiendo…' : 'Añadir al tablero'}
          </Button>
          <Button variant="secondary" onClick={onRegenerate} disabled={applying}>
            Regenerar
          </Button>
          <span className={styles.note}>Se crearán en la primera columna, cada una con su responsable</span>
        </div>
      </Card>
    </CardGroup>
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
          {titles.length} {titles.length === 1 ? 'tarea añadida' : 'tareas añadidas'} al tablero
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
        No se ha podido generar la planificación
      </span>
      <p className={styles.errorBody}>{message}</p>
      <Button variant="secondary" small onClick={onRetry}>
        Reintentar
      </Button>
    </div>
  )
}

