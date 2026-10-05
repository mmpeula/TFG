import { Check, UserRound } from 'lucide-react'
import { Avatar } from '@/components/ui/Avatar'
import { Dropdown, DropdownItem } from '@/components/ui/Controls'
import styles from './PeopleFilter.module.css'
import { SIN_ASIGNAR } from '@/lib/board'
import type { TeamMemberResponse } from '@/types/api'

/** Cuantas caras caben en la fila antes de agrupar el resto en «+N». */
const VISIBLES = 6

/**
 * Filtro del tablero por persona, como en Jira: una fila de fotos de perfil
 * superpuestas. Pulsar una cara ensena solo sus tareas; se pueden marcar varias,
 * y la marcada lleva un aro. La primera, gris, es «Sin asignar». Sin nadie
 * marcado no filtra.
 *
 * Tu cara va la primera de las personas: filtrar por lo propio es lo mas comun.
 * Los docentes no salen: no llevan tareas. Si no caben todos, el resto se elige
 * desde «+N», que se marca si alguna de las escondidas esta seleccionada.
 */
export function PeopleFilter({
  members,
  selected,
  currentUserId,
  onChange,
}: {
  members: TeamMemberResponse[]
  /** Ids marcados; {@link SIN_ASIGNAR} para las tareas sin responsable. */
  selected: number[]
  currentUserId?: number
  onChange: (ids: number[]) => void
}) {
  const people = members
    .filter((member) => member.role !== 'DOCENTE')
    .sort((a, b) => Number(b.user.id === currentUserId) - Number(a.user.id === currentUserId))
  const visibles = people.slice(0, VISIBLES)
  const resto = people.slice(VISIBLES)

  const toggle = (id: number) =>
    onChange(selected.includes(id) ? selected.filter((item) => item !== id) : [...selected, id])
  const nombre = (member: TeamMemberResponse) =>
    member.user.id === currentUserId ? `${member.user.fullName} (tú)` : member.user.fullName

  return (
    <div className={styles.stack} role="group" aria-label="Filtrar por persona">
      <button
        type="button"
        className={[styles.face, selected.includes(SIN_ASIGNAR) && styles.faceOn].filter(Boolean).join(' ')}
        onClick={() => toggle(SIN_ASIGNAR)}
        aria-pressed={selected.includes(SIN_ASIGNAR)}
        title="Sin asignar"
      >
        <span className={styles.unassigned}>
          <UserRound size={17} strokeWidth={2} aria-hidden="true" />
        </span>
      </button>

      {visibles.map((member) => {
        const on = selected.includes(member.user.id)
        return (
          <button
            key={member.user.id}
            type="button"
            className={[styles.face, on && styles.faceOn].filter(Boolean).join(' ')}
            onClick={() => toggle(member.user.id)}
            aria-pressed={on}
            title={nombre(member)}
          >
            <Avatar user={member.user} avatarUrl={member.user.avatarUrl} size={30} title={nombre(member)} />
          </button>
        )
      })}

      {resto.length > 0 && (
        <Dropdown
          trigger={({ toggle: open }) => {
            const alguno = resto.some((member) => selected.includes(member.user.id))
            return (
              <button
                type="button"
                className={[styles.face, styles.more, alguno && styles.faceOn].filter(Boolean).join(' ')}
                onClick={open}
                title={`${resto.length} personas más`}
              >
                +{resto.length}
              </button>
            )
          }}
        >
          {() => (
            <>
              {resto.map((member) => {
                const on = selected.includes(member.user.id)
                return (
                  <DropdownItem key={member.user.id} active={on} onClick={() => toggle(member.user.id)}>
                    <span className={[styles.check, on && styles.checkOn].filter(Boolean).join(' ')} aria-hidden="true">
                      {on && <Check size={11} strokeWidth={3} />}
                    </span>
                    <Avatar user={member.user} avatarUrl={member.user.avatarUrl} size={20} />
                    {nombre(member)}
                  </DropdownItem>
                )
              })}
            </>
          )}
        </Dropdown>
      )}
    </div>
  )
}
