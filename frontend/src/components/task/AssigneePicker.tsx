import { ChevronDown } from 'lucide-react'
import { Avatar } from '@/components/ui/Avatar'
import { Dropdown, DropdownItem } from '@/components/ui/Controls'
import styles from './AssigneePicker.module.css'
import type { TeamMemberResponse, UserSummary } from '@/types/api'

interface Props {
  members: TeamMemberResponse[]
  selected: UserSummary[]
  onChange: (ids: number[]) => void
  align?: 'left' | 'right'
  className?: string
}

/**
 * Selector de responsables de una tarea. A diferencia de un desplegable
 * normal, marcar a alguien no cierra el menu: asi se puede marcar a varios
 * seguidos sin reabrirlo cada vez. "Sin asignar" es la unica opcion que vacia
 * la seleccion entera y cierra.
 */
export function AssigneePicker({ members, selected, onChange, align = 'left', className }: Props) {
  const selectedIds = selected.map((user) => user.id)
  // Los docentes siguen el trabajo, no lo hacen: no se les asignan tareas (el filtro por persona y
  // el resumen del equipo tampoco los cuentan). Una cuenta dada de baja tampoco puede hacer nada.
  // Solo sale alguno de ellos si ya lo estaba, para poder quitarlo.
  const elegibles = members.filter(
    (member) => (member.role !== 'DOCENTE' && member.enabled) || selectedIds.includes(member.user.id),
  )

  function toggle(userId: number) {
    onChange(
      selectedIds.includes(userId)
        ? selectedIds.filter((id) => id !== userId)
        : [...selectedIds, userId],
    )
  }

  return (
    <Dropdown
      align={align}
      trigger={({ toggle: open }) => (
        <button
          type="button"
          className={[styles.trigger, className].filter(Boolean).join(' ')}
          onClick={open}
        >
          <span className={styles.avatars}>
            {selected.length === 0
              ? <Avatar size={20} />
              : selected.map((user) => <Avatar key={user.id} user={user} avatarUrl={user.avatarUrl} size={20} />)}
          </span>
          {selected.length === 0 ? 'Sin asignar' : selected.map((user) => user.fullName).join(', ')}
          <ChevronDown size={14} strokeWidth={2.75} aria-hidden="true" />
        </button>
      )}
    >
      {(close) => (
        <>
          <DropdownItem onClick={() => { close(); onChange([]) }}>
            Sin asignar
          </DropdownItem>
          {elegibles.map((member) => (
            <DropdownItem
              key={member.user.id}
              active={selectedIds.includes(member.user.id)}
              onClick={() => toggle(member.user.id)}
            >
              <Avatar user={member.user} avatarUrl={member.user.avatarUrl} size={20} />
              {member.user.fullName}
            </DropdownItem>
          ))}
        </>
      )}
    </Dropdown>
  )
}
