import { Avatar } from '@/components/ui/Avatar'
import styles from './ActivityFeed.module.css'
import { relativeTime } from '@/lib/format'
import type { ActivityLogResponse } from '@/types/api'

export function ActivityFeed({ items }: { items: ActivityLogResponse[] }) {
  return (
    <div className={styles.feed}>
      {items.map((item) => (
        <article key={item.id} className={styles.item}>
          <Avatar user={item.user} size={26} />
          <div>
            <p className={styles.text}>
              <strong>{item.user?.fullName ?? 'PlanifIA'}</strong>{' '}
              {(item.details ?? '').replace(/^[A-Z]\w+o?\s/, (match) => match.toLowerCase())}
            </p>
            <div className={styles.time}>{relativeTime(item.occurredAt)}</div>
          </div>
        </article>
      ))}
    </div>
  )
}
