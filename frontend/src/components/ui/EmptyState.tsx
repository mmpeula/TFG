import type { ReactNode } from 'react'
import { Inbox } from 'lucide-react'
import styles from './EmptyState.module.css'

interface Props {
  title: string
  body?: string
  icon?: ReactNode
  action?: ReactNode
}

export function EmptyState({ title, body, icon, action }: Props) {
  return (
    <div className={styles.empty}>
      <span className={styles.icon}>{icon ?? <Inbox size={22} strokeWidth={2.75} />}</span>
      <h3 className={styles.title}>{title}</h3>
      {body && <p className={styles.body}>{body}</p>}
      {action}
    </div>
  )
}
