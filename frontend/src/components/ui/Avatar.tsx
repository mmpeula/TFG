import styles from './Avatar.module.css'
import { initials } from '@/lib/format'
import { useAvatarImage } from '@/hooks/useData'
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
  /** Si el usuario tiene foto de perfil, se muestra en vez de las iniciales. */
  avatarUrl?: string | null
  size?: number
  title?: string
}

export function Avatar({ user, avatarUrl, size = 22, title }: Props) {
  const { data: photoUrl } = useAvatarImage(avatarUrl)
  const label = title ?? user?.fullName ?? 'Sin asignar'

  if (photoUrl) {
    return (
      <img
        className={styles.avatar}
        style={{ width: size, height: size, objectFit: 'cover' }}
        src={photoUrl}
        alt={label}
        title={label}
      />
    )
  }

  return (
    <span
      className={styles.avatar}
      title={label}
      style={{ width: size, height: size, background: colorFor(user?.id), fontSize: Math.max(9, Math.round(size * 0.4)) }}
      aria-label={label}
    >
      {initials(user?.fullName)}
    </span>
  )
}
