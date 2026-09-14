import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { ListChecks, LogOut } from 'lucide-react'
import { Avatar } from '@/components/ui/Avatar'
import { Tooltip } from '@/components/ui/Controls'
import styles from './HomeShell.module.css'
import { ROUTES } from '@/app/routes'
import { useAuth } from '@/state/context'

/**
 * Marco de la pantalla inicial. No lleva barra lateral a proposito: en el nivel
 * de «mis workspaces» no hay nada que navegar todavia, solo elegir uno.
 */
export function HomeShell({ children }: { children: ReactNode }) {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()

  return (
    <div className={styles.shell}>
      <header className={styles.top}>
        <button className={styles.brand} onClick={() => navigate(ROUTES.workspaces)}>
          <span className={styles.mark}>
            <ListChecks size={15} strokeWidth={2.5} />
          </span>
          <span className={styles.brandName}>PlanifIA</span>
        </button>

        <div className={styles.actions}>
          <button className={styles.profile} onClick={() => navigate(ROUTES.configuracion)}>
            <Avatar user={user} size={30} />
            <span className={styles.profileName}>{user?.fullName}</span>
          </button>
          <Tooltip label="Cerrar sesion">
            <button className={styles.icon} onClick={signOut} aria-label="Cerrar sesion">
              <LogOut size={16} strokeWidth={2.75} />
            </button>
          </Tooltip>
        </div>
      </header>

      <main className={styles.content}>{children}</main>
    </div>
  )
}
