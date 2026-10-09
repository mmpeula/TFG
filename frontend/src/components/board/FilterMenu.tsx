import { useState, type ReactNode } from 'react'
import { AlertTriangle, Check, ChevronDown, ListFilter, Search, UserRound } from 'lucide-react'
import { Avatar } from '@/components/ui/Avatar'
import { Chip, Dropdown } from '@/components/ui/Controls'
import { SIN_ASIGNAR } from '@/lib/board'
import { plainText } from '@/lib/format'
import { TASK_TYPES, TASK_TYPE_IDS } from '@/lib/taskTypes'
import type { ColumnResponse, TaskType, TeamMemberResponse } from '@/types/api'
import styles from './FilterMenu.module.css'

type Field = 'people' | 'status' | 'type' | 'signals'

const FIELDS: { id: Field; label: string }[] = [
  { id: 'people', label: 'Persona' },
  { id: 'status', label: 'Estado' },
  { id: 'type', label: 'Tipo de tarea' },
  { id: 'signals', label: 'Avisos' },
]

interface Option {
  key: string
  label: string
  on: boolean
  count: number
  toggle: () => void
  icon?: ReactNode
}

/**
 * Boton «Filtros» del tablero. Abre un panel de dos columnas, como el de Jira: a la
 * izquierda el campo por el que filtrar (persona, estado, tipo de tarea, avisos) y a la
 * derecha sus opciones con casillas, que se pueden marcar varias. Sin nada marcado un
 * campo no filtra. Los filtros se aplican al instante y el panel se queda abierto.
 */
export function FilterMenu({
  members,
  currentUserId,
  columns,
  people,
  onPeople,
  statuses,
  onStatuses,
  types,
  onTypes,
  onlySignals,
  onOnlySignals,
  signalCount,
}: {
  members: TeamMemberResponse[]
  currentUserId?: number
  columns: ColumnResponse[]
  people: number[]
  onPeople: (ids: number[]) => void
  /** Ids de columna marcadas. */
  statuses: number[]
  onStatuses: (ids: number[]) => void
  types: TaskType[]
  onTypes: (types: TaskType[]) => void
  onlySignals: boolean
  onOnlySignals: (on: boolean) => void
  signalCount: number
}) {
  const [field, setField] = useState<Field>('people')
  const [query, setQuery] = useState('')

  const tasks = columns.flatMap((column) => column.tasks ?? [])
  const flip = <T,>(list: T[], item: T, set: (next: T[]) => void) =>
    set(list.includes(item) ? list.filter((value) => value !== item) : [...list, item])

  const peopleOptions: Option[] = [
    {
      key: 'none',
      label: 'Sin asignar',
      on: people.includes(SIN_ASIGNAR),
      count: tasks.filter((task) => task.assignees.length === 0).length,
      toggle: () => flip(people, SIN_ASIGNAR, onPeople),
      icon: <span className={styles.unassigned}><UserRound size={12} aria-hidden="true" /></span>,
    },
    ...members
      .filter((member) => member.role !== 'DOCENTE')
      .sort((a, b) => Number(b.user.id === currentUserId) - Number(a.user.id === currentUserId))
      .map((member) => ({
        key: String(member.user.id),
        label: member.user.id === currentUserId ? `${member.user.fullName} (tú)` : member.user.fullName,
        on: people.includes(member.user.id),
        count: tasks.filter((task) => task.assignees.some((assignee) => assignee.id === member.user.id)).length,
        toggle: () => flip(people, member.user.id, onPeople),
        icon: <Avatar user={member.user} avatarUrl={member.user.avatarUrl} size={20} />,
      })),
  ]
  const statusOptions: Option[] = columns.map((column) => ({
    key: String(column.id),
    label: column.name,
    on: statuses.includes(column.id),
    count: column.tasks?.length ?? 0,
    toggle: () => flip(statuses, column.id, onStatuses),
  }))
  const typeOptions: Option[] = TASK_TYPE_IDS.map((id) => ({
    key: id,
    label: TASK_TYPES[id].label,
    on: types.includes(id),
    count: tasks.filter((task) => (task.type ?? 'TAREA') === id).length,
    toggle: () => flip(types, id, onTypes),
    icon: <span className={styles.dot} style={{ background: TASK_TYPES[id].color }} />,
  }))
  const signalOptions: Option[] = [
    {
      key: 'signals',
      label: 'Con aviso',
      on: onlySignals,
      count: signalCount,
      toggle: () => onOnlySignals(!onlySignals),
      icon: <AlertTriangle size={14} strokeWidth={2.5} className={styles.warn} aria-hidden="true" />,
    },
  ]

  const optionsOf: Record<Field, Option[]> = {
    people: peopleOptions,
    status: statusOptions,
    type: typeOptions,
    signals: signalOptions,
  }
  const marked: Record<Field, number> = {
    people: people.length,
    status: statuses.length,
    type: types.length,
    signals: onlySignals ? 1 : 0,
  }
  const total = Object.values(marked).reduce((sum, value) => sum + value, 0)

  const searchable = field !== 'signals'
  const needle = plainText(query.trim())
  const visible = optionsOf[field].filter((option) => !needle || plainText(option.label).includes(needle))

  return (
    <Dropdown
      align="left"
      trigger={({ toggle }) => (
        <Chip active={total > 0} onClick={toggle} title="Filtrar las tareas del tablero">
          <ListFilter size={13} strokeWidth={2.5} />
          Filtros
          {total > 0 && <span className={styles.badge}>{total}</span>}
          <ChevronDown size={13} strokeWidth={2.5} aria-hidden="true" />
        </Chip>
      )}
    >
      {() => (
        <div className={styles.panel}>
          <nav className={styles.fields} aria-label="Campos del filtro">
            {FIELDS.map((item) => (
              <button
                key={item.id}
                type="button"
                className={[styles.field, field === item.id && styles.fieldOn].filter(Boolean).join(' ')}
                onClick={() => { setField(item.id); setQuery('') }}
                aria-current={field === item.id}
              >
                {item.label}
                {marked[item.id] > 0 && <span className={styles.badge}>{marked[item.id]}</span>}
              </button>
            ))}
          </nav>

          <div className={styles.options}>
            {searchable && (
              <label className={styles.search}>
                <Search size={14} strokeWidth={2.5} aria-hidden="true" />
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder={`Buscar ${FIELDS.find((item) => item.id === field)?.label.toLowerCase()}`}
                  aria-label="Buscar en el filtro"
                />
              </label>
            )}
            <ul className={styles.list}>
              {visible.map((option) => (
                <li key={option.key}>
                  <button type="button" className={styles.option} onClick={option.toggle} aria-pressed={option.on}>
                    <span className={styles.checkbox} data-on={option.on}>
                      {option.on && <Check size={11} strokeWidth={3} aria-hidden="true" />}
                    </span>
                    {option.icon}
                    <span className={styles.label}>{option.label}</span>
                    <span className={styles.count}>{option.count}</span>
                  </button>
                </li>
              ))}
              {visible.length === 0 && <li className={styles.empty}>Nada que coincida</li>}
            </ul>
          </div>
        </div>
      )}
    </Dropdown>
  )
}
