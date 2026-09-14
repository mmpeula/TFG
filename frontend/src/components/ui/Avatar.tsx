import styles from './Avatar.module.css'
import { initials } from '@/lib/format'
import type { UserSummary } from '@/types/api'

/*
 * Grises, no colores. Un avatar solo tiene que distinguir a una persona de otra,
 * y para eso bastan las iniciales; pintarlos de colores metia ruido en cada
 * tarjeta del tablero.
 */
const PALETTE = [
  'var(--color-neutral-500)',
  'var(--color-neutral-600)',
  'var(--color-neutral-700)',
  'var(--color-neutral-800)',
]

/** El tono se deriva del id, asi cada persona tiene siempre el mismo. */
function colorFor(id?: number): string {
  return PALETTE[(id ?? 0) % PALETTE.length]
}

interface Props {
  user?: Pick<UserSummary, 'id' | 'fullName'> | null
  size?: number
  title?: string
}

export function Avatar({ user, size = 22, title }: Props) {
  return (
    <span
      className={styles.avatar}
      title={title ?? user?.fullName ?? 'Sin asignar'}
      style={{ width: size, height: size, background: colorFor(user?.id), fontSize: Math.max(9, Math.round(size * 0.4)) }}
      aria-label={user?.fullName ?? 'Sin asignar'}
    >
      {initials(user?.fullName)}
    </span>
  )
}
