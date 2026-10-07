import { Avatar } from '@/components/ui/Avatar'
import styles from './ActivityFeed.module.css'
import { relativeTime } from '@/lib/format'
import type { ActivityLogResponse } from '@/types/api'

/**
 * El servidor escribe cada entrada como una frase suelta («Creó la tarea…»), y aqui va
 * detras del nombre de quien lo hizo: «Ana creó la tarea…». Se pasa a minuscula la
 * primera palabra, con o sin tilde.
 */
function afterName(details?: string): string {
  return (details ?? '').replace(/^\p{Lu}\p{Ll}*\s/u, (word) => word.toLowerCase())
}

export function ActivityFeed({ items }: { items: ActivityLogResponse[] }) {
  return (
    <div className={styles.feed}>
      {items.map((item) => (
        <article key={item.id} className={styles.item}>
          <Avatar user={item.user} avatarUrl={item.user?.avatarUrl} size={26} />
          <div>
            <p className={styles.text}>
              <strong>{item.user?.fullName ?? 'PlanifIA'}</strong> {afterName(item.details)}
            </p>
            <div className={styles.time}>{relativeTime(item.occurredAt)}</div>
          </div>
        </article>
      ))}
    </div>
  )
}
